package com.vunbo.watchtogether

import android.app.Application
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

    companion object {
        lateinit var instance: WatchTogetherApp
            private set
    }
}
