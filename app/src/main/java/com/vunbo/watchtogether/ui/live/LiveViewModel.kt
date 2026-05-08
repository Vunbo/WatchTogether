package com.vunbo.watchtogether.ui.live

import androidx.lifecycle.ViewModel
import com.vunbo.watchtogether.data.api.ApiConfig
import com.vunbo.watchtogether.data.model.LiveChannelGroup
import com.vunbo.watchtogether.data.model.LiveChannelItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LiveViewModel : ViewModel() {
    private val apiConfig = ApiConfig.get()

    private val _groups = MutableStateFlow<List<LiveChannelGroup>>(emptyList())
    val groups: StateFlow<List<LiveChannelGroup>> = _groups.asStateFlow()

    private val _selectedGroupIndex = MutableStateFlow(0)
    val selectedGroupIndex: StateFlow<Int> = _selectedGroupIndex.asStateFlow()

    private val _channels = MutableStateFlow<List<LiveChannelItem>>(emptyList())
    val channels: StateFlow<List<LiveChannelItem>> = _channels.asStateFlow()

    private val _currentChannel = MutableStateFlow<LiveChannelItem?>(null)
    val currentChannel: StateFlow<LiveChannelItem?> = _currentChannel.asStateFlow()

    fun loadData() {
        _groups.value = apiConfig.liveChannelGroupList
        if (_groups.value.isNotEmpty()) {
            selectGroup(0)
        }
    }

    fun selectGroup(index: Int) {
        _selectedGroupIndex.value = index
        val group = _groups.value.getOrNull(index)
        _channels.value = group?.channels ?: emptyList()
    }

    fun playChannel(channel: LiveChannelItem) {
        _currentChannel.value = channel
    }
}
