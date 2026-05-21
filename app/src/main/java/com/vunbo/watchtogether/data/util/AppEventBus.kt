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
    data class HistoryRefresh(val unused: Unit = Unit) : AppEvent()
    data class ApiUrlChange(val url: String) : AppEvent()
}
