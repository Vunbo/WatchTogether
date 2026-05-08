package com.vunbo.watchtogether.data.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object AppEventBus {
    private val _events = MutableSharedFlow<AppEvent>(replay = 0, extraBufferCapacity = 64)
    val events = _events.asSharedFlow()

    fun post(event: AppEvent) {
        _events.tryEmit(event)
    }
}

sealed class AppEvent {
    data class RefreshPlayIndex(val index: Int) : AppEvent()
    data class RefreshPlayUrl(val url: String) : AppEvent()
    data class RefreshPlayerCfg(val cfg: String) : AppEvent()
    data class HistoryRefresh(val unused: Unit = Unit) : AppEvent()
    data class FilterChange(val count: Int) : AppEvent()
    data class SubtitleSizeChange(val size: Int) : AppEvent()
    data class SearchResult(val data: Any?) : AppEvent()
    data class QuickSearchResult(val data: Any?) : AppEvent()
    data class PushUrl(val url: String) : AppEvent()
    data class ApiUrlChange(val url: String) : AppEvent()
    data class HomeRecommendChange(val mode: Int) : AppEvent()
}
