package com.vunbo.watchtogether.data.update

import android.util.Log
import com.google.gson.Gson
import com.vunbo.watchtogether.core.config.AppInfo
import com.vunbo.watchtogether.core.config.UpdateConfig
import com.vunbo.watchtogether.core.network.OkHttpHelper
import com.vunbo.watchtogether.core.storage.HawkConfig
import com.vunbo.watchtogether.core.storage.PrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UpdateRepository {
    private val gson = Gson()

    suspend fun checkForUpdate(auto: Boolean): UpdateCheckResult {
        val now = System.currentTimeMillis()
        return withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "check update start auto=$auto current=${AppInfo.versionName}(${AppInfo.versionCode})")
                val body = OkHttpHelper.getBody(
                    UpdateConfig.UPDATE_JSON_URL,
                    mapOf("Cache-Control" to "no-cache")
                )
                if (body.isNullOrBlank()) {
                    Log.w(TAG, "check update failed: empty body")
                    return@withContext UpdateCheckResult.Failed("未获取到更新信息")
                }

                val info = gson.fromJson(body, UpdateInfo::class.java)
                val validationError = validate(info)
                if (validationError != null) {
                    Log.w(TAG, "check update invalid: $validationError")
                    return@withContext UpdateCheckResult.Failed(validationError)
                }

                PrefsManager.putLong(HawkConfig.UPDATE_LAST_AUTO_CHECK, now)
                Log.i(
                    TAG,
                    "remote update version=${info.safeVersionName}(${info.versionCode}) " +
                        "force=${info.forceUpdate} min=${info.minSupportedVersionCode}"
                )

                if (!info.isNewerThan(AppInfo.versionCode)) {
                    Log.i(TAG, "check update not available")
                    return@withContext UpdateCheckResult.NotAvailable
                }

                if (auto && isDismissedRecently(info, now) && !info.isForceFor(AppInfo.versionCode)) {
                    Log.i(TAG, "check update skipped: dismissed recently versionCode=${info.versionCode}")
                    return@withContext UpdateCheckResult.Skipped
                }

                Log.i(TAG, "check update available versionCode=${info.versionCode}")
                UpdateCheckResult.Available(info)
            } catch (e: Exception) {
                Log.e(TAG, "check update failed", e)
                UpdateCheckResult.Failed(e.message ?: "检查更新失败")
            }
        }
    }

    fun markDismissed(info: UpdateInfo) {
        PrefsManager.putInt(HawkConfig.UPDATE_DISMISSED_VERSION_CODE, info.versionCode)
        PrefsManager.putLong(HawkConfig.UPDATE_DISMISSED_AT, System.currentTimeMillis())
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

    companion object {
        private const val TAG = "UpdateRepository"
    }
}

sealed class UpdateCheckResult {
    data class Available(val info: UpdateInfo) : UpdateCheckResult()
    data object NotAvailable : UpdateCheckResult()
    data object Skipped : UpdateCheckResult()
    data class Failed(val message: String) : UpdateCheckResult()
}
