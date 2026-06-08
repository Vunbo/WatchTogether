package com.vunbo.watchtogether.data.update

import com.google.gson.Gson
import com.vunbo.watchtogether.core.config.AppInfo
import com.vunbo.watchtogether.core.config.UpdateConfig
import com.vunbo.watchtogether.core.storage.HawkConfig
import com.vunbo.watchtogether.core.network.OkHttpHelper
import com.vunbo.watchtogether.core.storage.PrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UpdateRepository {
    private val gson = Gson()

    suspend fun checkForUpdate(auto: Boolean): UpdateCheckResult {
        val now = System.currentTimeMillis()
        if (auto && !canAutoCheck(now)) return UpdateCheckResult.Skipped

        if (auto) {
            PrefsManager.putLong(HawkConfig.UPDATE_LAST_AUTO_CHECK, now)
        }

        return withContext(Dispatchers.IO) {
            try {
                val body = OkHttpHelper.getBody(
                    UpdateConfig.UPDATE_JSON_URL,
                    mapOf("Cache-Control" to "no-cache")
                )
                if (body.isNullOrBlank()) {
                    return@withContext UpdateCheckResult.Failed("未获取到更新信息")
                }

                val info = gson.fromJson(body, UpdateInfo::class.java)
                val validationError = validate(info)
                if (validationError != null) {
                    return@withContext UpdateCheckResult.Failed(validationError)
                }

                if (!info.isNewerThan(AppInfo.versionCode)) {
                    return@withContext UpdateCheckResult.NotAvailable
                }

                if (auto && isDismissedRecently(info, now) && !info.isForceFor(AppInfo.versionCode)) {
                    return@withContext UpdateCheckResult.Skipped
                }

                UpdateCheckResult.Available(info)
            } catch (e: Exception) {
                UpdateCheckResult.Failed(e.message ?: "检查更新失败")
            }
        }
    }

    fun markDismissed(info: UpdateInfo) {
        PrefsManager.putInt(HawkConfig.UPDATE_DISMISSED_VERSION_CODE, info.versionCode)
        PrefsManager.putLong(HawkConfig.UPDATE_DISMISSED_AT, System.currentTimeMillis())
    }

    private fun canAutoCheck(now: Long): Boolean {
        val last = PrefsManager.getLong(HawkConfig.UPDATE_LAST_AUTO_CHECK)
        return last <= 0L || now - last >= UpdateConfig.AUTO_CHECK_INTERVAL_MS
    }

    private fun isDismissedRecently(info: UpdateInfo, now: Long): Boolean {
        val dismissedCode = PrefsManager.getInt(HawkConfig.UPDATE_DISMISSED_VERSION_CODE, -1)
        val dismissedAt = PrefsManager.getLong(HawkConfig.UPDATE_DISMISSED_AT)
        return dismissedCode == info.versionCode &&
            dismissedAt > 0L &&
            now - dismissedAt < UpdateConfig.DISMISSED_VERSION_SILENCE_MS
    }

    private fun validate(info: UpdateInfo?): String? {
        if (info == null) return "更新信息格式异常"
        if (info.versionCode <= 0) return "更新版本号无效"
        if (info.downloadUrl.isBlank()) return "安装包地址为空"
        return null
    }
}

sealed class UpdateCheckResult {
    data class Available(val info: UpdateInfo) : UpdateCheckResult()
    data object NotAvailable : UpdateCheckResult()
    data object Skipped : UpdateCheckResult()
    data class Failed(val message: String) : UpdateCheckResult()
}
