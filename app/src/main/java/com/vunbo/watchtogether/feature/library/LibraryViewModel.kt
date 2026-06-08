package com.vunbo.watchtogether.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vunbo.watchtogether.app.WatchTogetherApp
import com.vunbo.watchtogether.data.local.RoomDataManager
import com.vunbo.watchtogether.data.local.VodCollect
import com.vunbo.watchtogether.data.model.VodInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LibraryViewModel : ViewModel() {
    private val roomDataManager = RoomDataManager(WatchTogetherApp.instance)

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _history = MutableStateFlow<List<VodInfo>>(emptyList())
    val history: StateFlow<List<VodInfo>> = _history.asStateFlow()

    private val _favorites = MutableStateFlow<List<VodCollect>>(emptyList())
    val favorites: StateFlow<List<VodCollect>> = _favorites.asStateFlow()

    private val _deleteMode = MutableStateFlow(false)
    val deleteMode: StateFlow<Boolean> = _deleteMode.asStateFlow()

    fun loadData() {
        viewModelScope.launch {
            _history.value = roomDataManager.getAllVodRecord(100)
            _favorites.value = roomDataManager.getAllVodCollect()
        }
    }

    fun selectTab(index: Int) {
        _selectedTab.value = index
        _deleteMode.value = false
    }

    fun toggleDeleteMode() {
        _deleteMode.value = !_deleteMode.value
        if (!_deleteMode.value) loadData() // Refresh
    }

    fun deleteHistory(vodInfo: VodInfo) {
        viewModelScope.launch {
            roomDataManager.deleteVodRecord(vodInfo.sourceKey ?: "", vodInfo)
            _history.value = roomDataManager.getAllVodRecord(100)
        }
    }

    fun deleteFavorite(collect: VodCollect) {
        viewModelScope.launch {
            roomDataManager.deleteVodCollect(collect.sourceKey, collect.vodId)
            _favorites.value = roomDataManager.getAllVodCollect()
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            if (_selectedTab.value == 0) {
                roomDataManager.deleteAllVodRecords()
                _history.value = emptyList()
            } else {
                roomDataManager.deleteAllVodCollects()
                _favorites.value = emptyList()
            }
        }
    }
}
