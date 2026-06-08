package com.vunbo.watchtogether.feature.detail.model

sealed class DetailState {
    data object Loading : DetailState()
    data object Success : DetailState()
    data class Error(val msg: String) : DetailState()
}

data class EpisodePage(
    val startIndex: Int,
    val endIndex: Int,
    val label: String
)
