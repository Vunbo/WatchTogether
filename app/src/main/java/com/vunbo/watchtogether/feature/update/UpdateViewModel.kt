package com.vunbo.watchtogether.feature.update

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vunbo.watchtogether.core.config.AppInfo
import com.vunbo.watchtogether.core.config.UpdateConfig
import com.vunbo.watchtogether.data.update.ApkDownloader
import com.vunbo.watchtogether.data.update.UpdateCheckResult
import com.vunbo.watchtogether.data.update.UpdateInfo
import com.vunbo.watchtogether.data.update.UpdateRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class UpdateUiState(
    val checking: Boolean = false,
    val updateInfo: UpdateInfo? = null,
    val downloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadedApkPath: String? = null,
    val message: String? = null
) {
    val hasDownloadedApk: Boolean
        get() = !downloadedApkPath.isNullOrBlank()
}

class UpdateViewModel : ViewModel() {
    private val repository = UpdateRepository()
    private val _uiState = MutableStateFlow(UpdateUiState())
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    fun checkForUpdates(auto: Boolean) {
        if (_uiState.value.checking || _uiState.value.downloading) return
        viewModelScope.launch {
            if (auto) delay(UpdateConfig.AUTO_CHECK_DELAY_MS)
            _uiState.update {
                it.copy(
                    checking = true,
                    message = if (auto) null else "正在检查更新..."
                )
            }
            when (val result = repository.checkForUpdate(auto = auto)) {
                is UpdateCheckResult.Available -> {
                    _uiState.update {
                        it.copy(
                            checking = false,
                            updateInfo = result.info,
                            downloadedApkPath = null,
                            downloadProgress = 0f,
                            message = null
                        )
                    }
                }

                UpdateCheckResult.NotAvailable -> {
                    _uiState.update {
                        it.copy(
                            checking = false,
                            message = if (auto) null else "当前已是最新版本 ${AppInfo.versionName}"
                        )
                    }
                }

                UpdateCheckResult.Skipped -> {
                    _uiState.update { it.copy(checking = false, message = null) }
                }

                is UpdateCheckResult.Failed -> {
                    _uiState.update {
                        it.copy(
                            checking = false,
                            message = if (auto) null else "检查更新失败：${result.message}"
                        )
                    }
                }
            }
        }
    }

    fun dismissUpdate() {
        val info = _uiState.value.updateInfo ?: return
        if (info.isForceFor(AppInfo.versionCode)) return
        repository.markDismissed(info)
        _uiState.update {
            it.copy(
                updateInfo = null,
                downloading = false,
                downloadProgress = 0f,
                downloadedApkPath = null
            )
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun downloadAndInstall(context: Context) {
        val info = _uiState.value.updateInfo ?: return
        if (_uiState.value.downloading) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    downloading = true,
                    downloadProgress = 0f,
                    downloadedApkPath = null,
                    message = null
                )
            }
            runCatching {
                ApkDownloader.download(context.applicationContext, info) { progress ->
                    _uiState.update { state ->
                        state.copy(downloadProgress = progress)
                    }
                }
            }.onSuccess { apk ->
                _uiState.update {
                    it.copy(
                        downloading = false,
                        downloadProgress = 1f,
                        downloadedApkPath = apk.absolutePath
                    )
                }
                installDownloadedApk(context)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        downloading = false,
                        downloadProgress = 0f,
                        message = "下载更新失败：${error.message ?: "未知错误"}"
                    )
                }
            }
        }
    }

    fun installDownloadedApk(context: Context) {
        val path = _uiState.value.downloadedApkPath
        val apk = path?.let { File(it) }
        if (apk == null || !apk.exists()) {
            _uiState.update { it.copy(message = "安装包不存在，请重新下载") }
            return
        }
        if (!ApkDownloader.canRequestPackageInstalls(context)) {
            ApkDownloader.openInstallPermissionSettings(context)
            _uiState.update {
                it.copy(message = "请允许本应用安装未知应用，返回后再次点击安装")
            }
            return
        }
        runCatching {
            ApkDownloader.installApk(context, apk)
        }.onFailure { error ->
            _uiState.update {
                it.copy(message = "打开安装器失败：${error.message ?: "未知错误"}")
            }
        }
    }
}
