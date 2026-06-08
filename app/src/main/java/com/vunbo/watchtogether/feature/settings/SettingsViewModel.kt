package com.vunbo.watchtogether.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vunbo.watchtogether.app.WatchTogetherApp
import com.vunbo.watchtogether.data.source.ApiConfig
import com.vunbo.watchtogether.data.local.CacheManager
import com.vunbo.watchtogether.data.local.RoomDataManager
import com.vunbo.watchtogether.data.live.LiveRepository
import com.vunbo.watchtogether.data.subscription.SubscriptionGroup
import com.vunbo.watchtogether.data.subscription.SubscriptionRepository
import com.vunbo.watchtogether.data.subscription.SubscriptionSelection
import com.vunbo.watchtogether.data.subscription.SubscriptionSummary
import com.vunbo.watchtogether.data.subscription.SubscriptionType
import com.vunbo.watchtogether.data.subscription.SubscriptionValidationResult
import com.vunbo.watchtogether.core.event.AppEvent
import com.vunbo.watchtogether.core.event.AppEventBus
import com.vunbo.watchtogether.core.storage.HawkConfig
import com.vunbo.watchtogether.core.player.PlayerHelper
import com.vunbo.watchtogether.core.storage.PrefsManager
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
    private val liveRepository = LiveRepository(apiConfig)
    private val subscriptionRepository = SubscriptionRepository(apiConfig, liveRepository)

    private val _m3u8Purify = MutableStateFlow(false)
    val m3u8Purify: StateFlow<Boolean> = _m3u8Purify.asStateFlow()

    private val _vodGroups = MutableStateFlow<List<SubscriptionGroup>>(emptyList())
    val vodGroups: StateFlow<List<SubscriptionGroup>> = _vodGroups.asStateFlow()

    private val _liveGroups = MutableStateFlow<List<SubscriptionGroup>>(emptyList())
    val liveGroups: StateFlow<List<SubscriptionGroup>> = _liveGroups.asStateFlow()

    private val _vodSelection = MutableStateFlow(SubscriptionSelection())
    val vodSelection: StateFlow<SubscriptionSelection> = _vodSelection.asStateFlow()

    private val _liveSelection = MutableStateFlow(SubscriptionSelection())
    val liveSelection: StateFlow<SubscriptionSelection> = _liveSelection.asStateFlow()

    private val _vodSummary = MutableStateFlow(SubscriptionSummary())
    val vodSummary: StateFlow<SubscriptionSummary> = _vodSummary.asStateFlow()

    private val _liveSummary = MutableStateFlow(SubscriptionSummary())
    val liveSummary: StateFlow<SubscriptionSummary> = _liveSummary.asStateFlow()

    private val _saveResult = MutableStateFlow<String?>(null)
    val saveResult: StateFlow<String?> = _saveResult.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _loadingGroupId = MutableStateFlow("")
    val loadingGroupId: StateFlow<String> = _loadingGroupId.asStateFlow()

    private val _loadingStoreId = MutableStateFlow("")
    val loadingStoreId: StateFlow<String> = _loadingStoreId.asStateFlow()

    private val _validationMessage = MutableStateFlow<String?>(null)
    val validationMessage: StateFlow<String?> = _validationMessage.asStateFlow()

    private val _operationResult = MutableStateFlow<String?>(null)
    val operationResult: StateFlow<String?> = _operationResult.asStateFlow()

    private val saveMutex = Mutex()

    init {
        PrefsManager.putInt(HawkConfig.PLAY_TYPE, PlayerHelper.PLAYER_TYPE_EXO)
        _m3u8Purify.value = PrefsManager.getBoolean(HawkConfig.M3U8_PURIFY)
        refreshSubscriptionState()
        viewModelScope.launch {
            AppEventBus.events.collect { event ->
                if (event is AppEvent.SubscriptionChanged) {
                    refreshSubscriptionState()
                }
            }
        }
    }

    fun addSubscription(type: SubscriptionType, name: String, url: String, onSuccess: () -> Unit = {}) {
        if (_isSaving.value) return
        _isSaving.value = true
        _validationMessage.value = null
        viewModelScope.launch {
            saveMutex.withLock {
                try {
                    when (val result = subscriptionRepository.addSubscription(type, name, url)) {
                        is SubscriptionValidationResult.Success -> {
                            refreshSubscriptionState()
                            val selected = result.group.stores.firstOrNull()?.name.orEmpty()
                            _saveResult.value = when (type) {
                                SubscriptionType.Vod -> "影视订阅已添加：${selected.ifBlank { result.group.name }}，${result.message}"
                                SubscriptionType.Live -> "直播订阅已添加：${selected.ifBlank { result.group.name }}，${result.message}"
                            }
                            onSuccess()
                        }
                        is SubscriptionValidationResult.Failure -> {
                            _validationMessage.value = result.reason
                        }
                    }
                } catch (e: Exception) {
                    _validationMessage.value = "添加失败：${e.message ?: "未知错误"}"
                } finally {
                    _isSaving.value = false
                }
            }
        }
    }

    fun selectSubscription(type: SubscriptionType, groupId: String, storeId: String, onDone: (Boolean) -> Unit = {}) {
        if (_isSaving.value) return
        _isSaving.value = true
        _loadingGroupId.value = groupId
        _loadingStoreId.value = storeId
        viewModelScope.launch {
            saveMutex.withLock {
                try {
                    val result = subscriptionRepository.selectStore(type, groupId, storeId)
                    refreshSubscriptionState()
                    _saveResult.value = result.message.ifBlank {
                        if (result.success) "订阅已切换" else "订阅切换失败，请检查地址是否可用"
                    }
                    onDone(result.success)
                } catch (e: Exception) {
                    _saveResult.value = "订阅切换出错：${e.message ?: "未知错误"}"
                    onDone(false)
                } finally {
                    _isSaving.value = false
                    _loadingGroupId.value = ""
                    _loadingStoreId.value = ""
                }
            }
        }
    }

    fun deleteSubscriptionGroup(type: SubscriptionType, groupId: String) {
        _loadingGroupId.value = groupId
        _loadingStoreId.value = ""
        viewModelScope.launch {
            saveMutex.withLock {
                subscriptionRepository.deleteGroup(type, groupId)
                refreshSubscriptionState()
                _loadingGroupId.value = ""
            }
        }
    }

    fun deleteSubscriptionStore(type: SubscriptionType, groupId: String, storeId: String) {
        _loadingGroupId.value = groupId
        _loadingStoreId.value = storeId
        viewModelScope.launch {
            saveMutex.withLock {
                subscriptionRepository.deleteStore(type, groupId, storeId)
                refreshSubscriptionState()
                _loadingGroupId.value = ""
                _loadingStoreId.value = ""
            }
        }
    }

    fun refreshSubscriptions(type: SubscriptionType) {
        if (_isSaving.value) return
        _isSaving.value = true
        val selection = when (type) {
            SubscriptionType.Vod -> _vodSelection.value
            SubscriptionType.Live -> _liveSelection.value
        }
        _loadingGroupId.value = selection.groupId
        _loadingStoreId.value = selection.storeId
        viewModelScope.launch {
            saveMutex.withLock {
                try {
                    val result = subscriptionRepository.refreshSelected(type)
                    refreshSubscriptionState()
                    _operationResult.value = result.message.ifBlank {
                        if (result.success) "订阅刷新完成" else "订阅刷新失败"
                    }
                } catch (e: Exception) {
                    _operationResult.value = "订阅刷新失败：${e.message ?: "未知错误"}"
                } finally {
                    _isSaving.value = false
                    _loadingGroupId.value = ""
                    _loadingStoreId.value = ""
                }
            }
        }
    }

    fun clearValidationMessage() {
        _validationMessage.value = null
    }

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
                    refreshSubscriptionState()
                    deletedCount to reloadSuccess
                }
            }.onSuccess { (deletedCount, reloadSuccess) ->
                if (reloadSuccess) {
                    AppEventBus.post(AppEvent.ApiUrlChange(PrefsManager.getString(HawkConfig.API_URL)))
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

    private fun refreshSubscriptionState() {
        _vodGroups.value = subscriptionRepository.getGroups(SubscriptionType.Vod)
        _liveGroups.value = subscriptionRepository.getGroups(SubscriptionType.Live)
        _vodSelection.value = subscriptionRepository.getSelection(SubscriptionType.Vod)
        _liveSelection.value = subscriptionRepository.getSelection(SubscriptionType.Live)
        _vodSummary.value = subscriptionRepository.getSummary(SubscriptionType.Vod)
        _liveSummary.value = subscriptionRepository.getSummary(SubscriptionType.Live)
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
