package com.vunbo.watchtogether.feature.subscription

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vunbo.watchtogether.data.source.ApiConfig
import com.vunbo.watchtogether.data.live.LiveRepository
import com.vunbo.watchtogether.data.subscription.SubscriptionGroup
import com.vunbo.watchtogether.data.subscription.SubscriptionRepository
import com.vunbo.watchtogether.data.subscription.SubscriptionSelection
import com.vunbo.watchtogether.data.subscription.SubscriptionSummary
import com.vunbo.watchtogether.data.subscription.SubscriptionType
import com.vunbo.watchtogether.data.subscription.SubscriptionValidationResult
import com.vunbo.watchtogether.core.event.AppEvent
import com.vunbo.watchtogether.core.event.AppEventBus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SubscriptionManagerViewModel : ViewModel() {
    private val apiConfig = ApiConfig.get()
    private val repository = SubscriptionRepository(apiConfig, LiveRepository(apiConfig))
    private val mutex = Mutex()

    private val _vodGroups = MutableStateFlow<List<SubscriptionGroup>>(emptyList())
    val vodGroups: StateFlow<List<SubscriptionGroup>> = _vodGroups.asStateFlow()

    private val _vodSelection = MutableStateFlow(SubscriptionSelection())
    val vodSelection: StateFlow<SubscriptionSelection> = _vodSelection.asStateFlow()

    private val _vodSummary = MutableStateFlow(SubscriptionSummary())
    val vodSummary: StateFlow<SubscriptionSummary> = _vodSummary.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _loadingGroupId = MutableStateFlow("")
    val loadingGroupId: StateFlow<String> = _loadingGroupId.asStateFlow()

    private val _loadingStoreId = MutableStateFlow("")
    val loadingStoreId: StateFlow<String> = _loadingStoreId.asStateFlow()

    private val _validationMessage = MutableStateFlow<String?>(null)
    val validationMessage: StateFlow<String?> = _validationMessage.asStateFlow()

    private val _operationMessage = MutableStateFlow<String?>(null)
    val operationMessage: StateFlow<String?> = _operationMessage.asStateFlow()

    init {
        refreshVodState()
        viewModelScope.launch {
            AppEventBus.events.collect { event ->
                if (
                    event is AppEvent.SubscriptionChanged &&
                    event.typeName == SubscriptionType.Vod.name
                ) {
                    refreshVodState()
                }
            }
        }
    }

    fun addVodSubscription(name: String, url: String, onSuccess: () -> Unit = {}) {
        if (_isSaving.value) return
        _isSaving.value = true
        _validationMessage.value = null
        viewModelScope.launch {
            mutex.withLock {
                try {
                    when (val result = repository.addSubscription(SubscriptionType.Vod, name, url)) {
                        is SubscriptionValidationResult.Success -> {
                            refreshVodState()
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

    fun selectVod(groupId: String, storeId: String) {
        if (_isSaving.value) return
        _isSaving.value = true
        _loadingGroupId.value = groupId
        _loadingStoreId.value = storeId
        viewModelScope.launch {
            mutex.withLock {
                runCatching {
                    repository.selectStore(SubscriptionType.Vod, groupId, storeId)
                }
                refreshVodState()
                _isSaving.value = false
                _loadingGroupId.value = ""
                _loadingStoreId.value = ""
            }
        }
    }

    fun deleteVodGroup(groupId: String) {
        _loadingGroupId.value = groupId
        _loadingStoreId.value = ""
        viewModelScope.launch {
            mutex.withLock {
                repository.deleteGroup(SubscriptionType.Vod, groupId)
                refreshVodState()
                _loadingGroupId.value = ""
            }
        }
    }

    fun deleteVodStore(groupId: String, storeId: String) {
        _loadingGroupId.value = groupId
        _loadingStoreId.value = storeId
        viewModelScope.launch {
            mutex.withLock {
                repository.deleteStore(SubscriptionType.Vod, groupId, storeId)
                refreshVodState()
                _loadingGroupId.value = ""
                _loadingStoreId.value = ""
            }
        }
    }

    fun refreshVod() {
        if (_isSaving.value) return
        _isSaving.value = true
        _loadingGroupId.value = _vodSelection.value.groupId
        _loadingStoreId.value = _vodSelection.value.storeId
        viewModelScope.launch {
            mutex.withLock {
                try {
                    val result = repository.refreshSelected(SubscriptionType.Vod)
                    refreshVodState()
                    _operationMessage.value = result.message.ifBlank {
                        if (result.success) "影视订阅刷新完成" else "影视订阅刷新失败"
                    }
                } catch (e: Exception) {
                    _operationMessage.value = "影视订阅刷新失败：${e.message ?: "未知错误"}"
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

    fun clearOperationMessage() {
        _operationMessage.value = null
    }

    private fun refreshVodState() {
        _vodGroups.value = repository.getGroups(SubscriptionType.Vod)
        _vodSelection.value = repository.getSelection(SubscriptionType.Vod)
        _vodSummary.value = repository.getSummary(SubscriptionType.Vod)
    }
}
