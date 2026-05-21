package com.vunbo.watchtogether.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vunbo.watchtogether.WatchTogetherApp
import com.vunbo.watchtogether.data.api.ApiConfig
import com.vunbo.watchtogether.data.api.ApiConfig.ApiStore
import com.vunbo.watchtogether.data.local.CacheManager
import com.vunbo.watchtogether.data.local.RoomDataManager
import com.vunbo.watchtogether.data.util.AppEvent
import com.vunbo.watchtogether.data.util.AppEventBus
import com.vunbo.watchtogether.data.util.HawkConfig
import com.vunbo.watchtogether.data.util.PlayerHelper
import com.vunbo.watchtogether.data.util.PrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class SettingsViewModel : ViewModel() {
    private val apiConfig = ApiConfig.get()
    private val appContext = WatchTogetherApp.instance
    private val roomDataManager = RoomDataManager(appContext)
    private val cacheManager = CacheManager(appContext)

    private val _apiUrl = MutableStateFlow("")
    val apiUrl: StateFlow<String> = _apiUrl.asStateFlow()

    private val _playType = MutableStateFlow(1)
    val playType: StateFlow<Int> = _playType.asStateFlow()

    private val _m3u8Purify = MutableStateFlow(false)
    val m3u8Purify: StateFlow<Boolean> = _m3u8Purify.asStateFlow()

    private val _apiStores = MutableStateFlow<List<ApiStore>>(emptyList())
    val apiStores: StateFlow<List<ApiStore>> = _apiStores.asStateFlow()

    private val _selectedStoreUrl = MutableStateFlow("")
    val selectedStoreUrl: StateFlow<String> = _selectedStoreUrl.asStateFlow()

    private val _saveResult = MutableStateFlow<String?>(null)
    val saveResult: StateFlow<String?> = _saveResult.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _operationResult = MutableStateFlow<String?>(null)
    val operationResult: StateFlow<String?> = _operationResult.asStateFlow()

    private val saveMutex = Mutex()

    init {
        _apiUrl.value = PrefsManager.getString(HawkConfig.API_URL)
        _playType.value = PrefsManager.getInt(HawkConfig.PLAY_TYPE, 1)
        _m3u8Purify.value = PrefsManager.getBoolean(HawkConfig.M3U8_PURIFY)
        refreshStoreState()
    }

    fun updateApiUrl(url: String) {
        _apiUrl.value = url
    }

    fun saveAndReload() {
        if (_isSaving.value) return
        _isSaving.value = true
        viewModelScope.launch {
            saveMutex.withLock {
                val rawUrl = _apiUrl.value.trim()
                _apiUrl.value = rawUrl
                _saveResult.value = "正在加载配置..."
                try {
                    if (rawUrl.isBlank()) {
                        _saveResult.value = "请先填写 API 地址"
                        return@withLock
                    }
                    // 保存 API URL
                    PrefsManager.putString(HawkConfig.API_URL, rawUrl)
                    // 重新加载配置。loadConfig 内部会在网络失败时优先回退本地缓存。
                    val success = apiConfig.loadConfig(useCache = false, forceReload = true)
                    refreshStoreState()
                    if (success && apiConfig.homeSource != null) {
                        val storeName = apiConfig.selectedStore?.name
                            ?.takeIf { it.isNotBlank() }
                            ?.let { "，当前仓库：$it" }
                            .orEmpty()
                        _saveResult.value = "配置加载成功！已加载 ${apiConfig.sourceBeanList.size} 个站点$storeName"
                        // 通知首页刷新
                        AppEventBus.post(AppEvent.ApiUrlChange(rawUrl))
                    } else {
                        _saveResult.value = "配置加载失败，请检查 API 地址或稍后重试"
                    }
                } catch (e: Exception) {
                    _saveResult.value = "加载出错: ${e.message ?: "未知错误"}"
                } finally {
                    _isSaving.value = false
                }
            }
        }
    }

    fun selectApiStore(store: ApiStore) {
        if (_isSaving.value) return
        _isSaving.value = true
        viewModelScope.launch {
            saveMutex.withLock {
                _saveResult.value = "正在切换仓库..."
                try {
                    val success = apiConfig.switchApiStore(store)
                    refreshStoreState()
                    if (success && apiConfig.homeSource != null) {
                        _saveResult.value = "已切换到 ${store.name.ifBlank { store.url }}，加载 ${apiConfig.sourceBeanList.size} 个站点"
                        AppEventBus.post(AppEvent.ApiUrlChange(store.url))
                    } else {
                        _saveResult.value = "仓库切换失败，请检查该仓库地址"
                    }
                } catch (e: Exception) {
                    _saveResult.value = "切换仓库出错: ${e.message ?: "未知错误"}"
                } finally {
                    _isSaving.value = false
                }
            }
        }
    }

    fun cyclePlayerType() {
        val next = (_playType.value + 1) % 3
        _playType.value = next
        PrefsManager.putInt(HawkConfig.PLAY_TYPE, next)
    }

    fun getPlayerTypeName(type: Int): String = PlayerHelper.getPlayerName(type)

    fun toggleM3u8Purify() {
        _m3u8Purify.value = !_m3u8Purify.value
        PrefsManager.putBoolean(HawkConfig.M3U8_PURIFY, _m3u8Purify.value)
    }

    fun clearHistory() {
        viewModelScope.launch {
            runCatching {
                val before = roomDataManager.getAllVodRecord(100).size
                roomDataManager.deleteAllVodRecords()
                before
            }.onSuccess { before ->
                AppEventBus.post(AppEvent.HistoryRefresh())
                _operationResult.value = if (before > 0) {
                    "播放历史已清空，共删除 $before 条记录"
                } else {
                    "播放历史为空，无需清理"
                }
            }.onFailure { e ->
                _operationResult.value = "清除播放历史失败：${e.message ?: "未知错误"}"
            }
        }
    }

    fun clearFavorites() {
        viewModelScope.launch {
            runCatching {
                val before = roomDataManager.getAllVodCollect().size
                roomDataManager.deleteAllVodCollects()
                before
            }.onSuccess { before ->
                AppEventBus.post(AppEvent.HistoryRefresh())
                _operationResult.value = if (before > 0) {
                    "收藏已清空，共删除 $before 条内容"
                } else {
                    "收藏为空，无需清理"
                }
            }.onFailure { e ->
                _operationResult.value = "清除收藏失败：${e.message ?: "未知错误"}"
            }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val deletedCount = clearLocalCaches()
                    apiConfig.clearConfigState()
                    val reloadSuccess = apiConfig.loadConfig(useCache = false, forceReload = true)
                    refreshStoreState()
                    deletedCount to reloadSuccess
                }
            }.onSuccess { (deletedCount, reloadSuccess) ->
                if (reloadSuccess) {
                    AppEventBus.post(AppEvent.ApiUrlChange(_apiUrl.value))
                }
                _operationResult.value = buildString {
                    append("缓存清理完成，共清理 $deletedCount 项")
                    append(if (reloadSuccess) "\n配置已重新加载" else "\n配置重新加载失败，请检查数据源网络")
                }
            }.onFailure { e ->
                _operationResult.value = "清除缓存失败：${e.message ?: "未知错误"}"
            }
        }
    }

    fun dismissOperationResult() {
        _operationResult.value = null
    }

    private fun refreshStoreState() {
        _apiStores.value = apiConfig.getSavedApiStores()
        _selectedStoreUrl.value = apiConfig.getSelectedApiStoreUrl()
    }

    private fun clearLocalCaches(): Int {
        var count = 0
        cacheManager.clear()
        count++
        appContext.filesDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.startsWith("cache_")) {
                if (file.delete()) count++
            }
        }
        count += deleteChildren(File(appContext.filesDir, "csp"))
        count += deleteChildren(File(appContext.cacheDir, "catvod_csp"))
        count += deleteChildren(appContext.cacheDir)
        return count
    }

    private fun deleteChildren(dir: File): Int {
        if (!dir.exists() || !dir.isDirectory) return 0
        var count = 0
        dir.listFiles()?.forEach { child ->
            if (child.isDirectory) {
                count += deleteChildren(child)
            }
            if (child.delete()) count++
        }
        return count
    }
}
