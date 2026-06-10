package com.vunbo.watchtogether.data.spider

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import com.vunbo.watchtogether.app.WatchTogetherApp
import com.vunbo.watchtogether.data.model.SourceBean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class SpiderClient(
    private val context: Context = WatchTogetherApp.instance
) {

    suspend fun detailContent(source: SourceBean, ids: List<String>): String {
        return request(source, SpiderIpc.ACTION_DETAIL, timeoutMs = 10_000L, retryOnDisconnect = true) {
            putStringArrayList(SpiderIpc.KEY_IDS, ArrayList(ids))
        }
    }

    suspend fun searchContent(source: SourceBean, keyword: String, quick: Boolean): String {
        return request(source, SpiderIpc.ACTION_SEARCH, timeoutMs = 10_000L, retryOnDisconnect = true) {
            putString(SpiderIpc.KEY_KEYWORD, keyword)
            putBoolean(SpiderIpc.KEY_QUICK, quick)
        }
    }

    suspend fun playerContent(source: SourceBean, flag: String, url: String, flags: List<String>): String {
        return request(source, SpiderIpc.ACTION_PLAYER, timeoutMs = 10_000L, retryOnDisconnect = true) {
            putString(SpiderIpc.KEY_PLAY_FLAG, flag)
            putString(SpiderIpc.KEY_URL, url)
            putStringArrayList(SpiderIpc.KEY_FLAGS, ArrayList(flags))
        }
    }

    private suspend fun request(
        source: SourceBean,
        action: String,
        timeoutMs: Long,
        retryOnDisconnect: Boolean,
        extras: Bundle.() -> Unit
    ): String = withContext(Dispatchers.IO) {
        var attempt = 0
        while (true) {
            try {
                return@withContext requestOnce(source, action, timeoutMs, extras)
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "Spider timeout action=$action source=${source.key} attempt=${attempt + 1}")
            } catch (e: Throwable) {
                Log.e(TAG, "Spider process request failed action=$action source=${source.key} attempt=${attempt + 1}", e)
            }
            if (!retryOnDisconnect || attempt >= 1) {
                return@withContext ""
            }
            attempt++
            delay(RETRY_DELAY_MS)
        }
        ""
    }

    private suspend fun requestOnce(
        source: SourceBean,
        action: String,
        timeoutMs: Long,
        extras: Bundle.() -> Unit
    ): String {
        return withTimeout(timeoutMs + 2_000L) {
            val service = bindService()
            val reply = CompletableDeferred<Bundle>()
            val replyMessenger = Messenger(object : Handler(Looper.getMainLooper()) {
                override fun handleMessage(msg: Message) {
                    if (msg.what == SpiderIpc.MSG_RESPONSE && !reply.isCompleted) {
                        reply.complete(Bundle(msg.data))
                    }
                }
            })

            val requestData = Bundle().apply {
                putString(SpiderIpc.KEY_ACTION, action)
                putString(SpiderIpc.KEY_SOURCE_KEY, source.key)
                putString(SpiderIpc.KEY_API, source.api)
                putString(SpiderIpc.KEY_EXT, source.ext)
                putString(SpiderIpc.KEY_JAR, source.jar)
                extras()
            }
            val message = Message.obtain(null, SpiderIpc.MSG_REQUEST).apply {
                data = requestData
                replyTo = replyMessenger
            }
            val result = try {
                service.messenger.send(message)
                withTimeout(timeoutMs) {
                    select {
                        reply.onAwait { it }
                        service.disconnected.onAwait {
                            throw IllegalStateException("Spider service disconnected")
                        }
                    }
                }
            } finally {
                unbindService(service)
            }
            if (result.getBoolean(SpiderIpc.KEY_SUCCESS, false)) {
                result.getString(SpiderIpc.KEY_RESULT).orEmpty()
            } else {
                Log.w(TAG, "Spider failed action=$action source=${source.key}: ${result.getString(SpiderIpc.KEY_ERROR).orEmpty()}")
                ""
            }
        }
    }

    private suspend fun bindService(): BoundService {
        return withContext(Dispatchers.Main) {
            val deferred = CompletableDeferred<BoundService>()
            val disconnected = CompletableDeferred<Unit>()
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    if (service == null) {
                        deferred.completeExceptionally(IllegalStateException("Spider service binder is null"))
                    } else {
                        deferred.complete(BoundService(Messenger(service), this, disconnected))
                    }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    if (!deferred.isCompleted) {
                        deferred.completeExceptionally(IllegalStateException("Spider service disconnected"))
                    }
                    if (!disconnected.isCompleted) {
                        disconnected.complete(Unit)
                    }
                }
            }
            val ok = context.bindService(
                Intent(context, SpiderService::class.java),
                connection,
                Context.BIND_AUTO_CREATE
            )
            if (!ok) {
                deferred.completeExceptionally(IllegalStateException("Bind spider service failed"))
            }
            deferred.await()
        }
    }

    private suspend fun unbindService(bound: BoundService) {
        withContext(Dispatchers.Main) {
            runCatching { context.unbindService(bound.connection) }
        }
    }

    private data class BoundService(
        val messenger: Messenger,
        val connection: ServiceConnection,
        val disconnected: CompletableDeferred<Unit>
    )

    companion object {
        private const val TAG = "SpiderClient"
        private const val RETRY_DELAY_MS = 300L
    }
}
