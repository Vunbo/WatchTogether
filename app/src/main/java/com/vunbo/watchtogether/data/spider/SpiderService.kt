package com.vunbo.watchtogether.data.spider

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import com.github.catvod.crawler.SpiderNull
import com.vunbo.watchtogether.data.source.ApiConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SpiderService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val messenger by lazy { Messenger(IncomingHandler()) }

    override fun onBind(intent: Intent?): IBinder {
        return messenger.binder
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private inner class IncomingHandler : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what != SpiderIpc.MSG_REQUEST) {
                super.handleMessage(msg)
                return
            }
            val replyTo = msg.replyTo ?: return
            val data = Bundle(msg.data)
            scope.launch {
                val reply = handleSpiderRequest(data)
                runCatching {
                    replyTo.send(Message.obtain(null, SpiderIpc.MSG_RESPONSE).apply {
                        this.data = reply
                    })
                }.onFailure {
                    Log.e(TAG, "Failed to reply spider request", it)
                }
            }
        }
    }

    private suspend fun handleSpiderRequest(data: Bundle): Bundle {
        val action = data.getString(SpiderIpc.KEY_ACTION).orEmpty()
        val sourceKey = data.getString(SpiderIpc.KEY_SOURCE_KEY).orEmpty()
        val api = data.getString(SpiderIpc.KEY_API).orEmpty()
        val ext = data.getString(SpiderIpc.KEY_EXT)
        val jar = data.getString(SpiderIpc.KEY_JAR)

        return try {
            val apiConfig = ApiConfig.get()
            apiConfig.loadConfig(useCache = true, forceReload = false, loadSpider = false)
            if (jar.isNullOrBlank() && apiConfig.spiderUrl?.isNotBlank() == true) {
                apiConfig.loadMainJar()
            }
            val spider = apiConfig.getSpider(sourceKey, api, ext, jar)
            if (spider is SpiderNull) {
                buildReply(success = false, result = "", error = "Spider is not available")
            } else {
                val result = when (action) {
                    SpiderIpc.ACTION_DETAIL -> spider.detailContent(
                        data.getStringArrayList(SpiderIpc.KEY_IDS)?.toList() ?: emptyList()
                    )
                    SpiderIpc.ACTION_SEARCH -> spider.searchContent(
                        data.getString(SpiderIpc.KEY_KEYWORD).orEmpty(),
                        data.getBoolean(SpiderIpc.KEY_QUICK, false)
                    )
                    SpiderIpc.ACTION_PLAYER -> spider.playerContent(
                        data.getString(SpiderIpc.KEY_PLAY_FLAG).orEmpty(),
                        data.getString(SpiderIpc.KEY_URL).orEmpty(),
                        data.getStringArrayList(SpiderIpc.KEY_FLAGS)?.toList() ?: emptyList()
                    )
                    else -> ""
                }
                buildReply(success = true, result = result)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Spider request failed: $action/$sourceKey", e)
            buildReply(success = false, result = "", error = e.message.orEmpty())
        }
    }

    private fun buildReply(success: Boolean, result: String, error: String = ""): Bundle {
        return Bundle().apply {
            putBoolean(SpiderIpc.KEY_SUCCESS, success)
            putString(SpiderIpc.KEY_RESULT, result)
            putString(SpiderIpc.KEY_ERROR, error)
        }
    }

    companion object {
        private const val TAG = "SpiderService"
    }
}
