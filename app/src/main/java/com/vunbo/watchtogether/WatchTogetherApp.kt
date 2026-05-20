package com.vunbo.watchtogether

import android.app.Application
import android.os.Looper
import android.util.Log
import com.vunbo.watchtogether.data.api.ApiConfig
import com.vunbo.watchtogether.data.util.PrefsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WatchTogetherApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        PrefsManager.init(this)
        installSpiderInitCrashGuard()

        // 启动时自动加载已保存的配置
        applicationScope.launch {
            val apiUrl = PrefsManager.getString(
                com.vunbo.watchtogether.data.util.HawkConfig.API_URL
            )
            if (apiUrl.isNotEmpty()) {
                ApiConfig.get().loadConfig()
            }
        }
    }

    private fun installSpiderInitCrashGuard() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (thread != Looper.getMainLooper().thread && throwable.isCatVodSpiderInitFailure()) {
                Log.e("SpiderGuard", "外部蜘蛛初始化线程异常，已阻止应用闪退: ${thread.name}", throwable)
            } else {
                previousHandler?.uncaughtException(thread, throwable) ?: throw throwable
            }
        }
    }

    private fun Throwable.isCatVodSpiderInitFailure(): Boolean {
        return generateSequence(this) { it.cause }.any { error ->
            error.stackTrace.any { frame ->
                frame.className == "com.github.catvod.spider.Init"
            }
        }
    }

    companion object {
        lateinit var instance: WatchTogetherApp
            private set
    }
}
