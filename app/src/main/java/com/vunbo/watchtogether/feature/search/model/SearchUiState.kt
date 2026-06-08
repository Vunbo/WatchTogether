package com.vunbo.watchtogether.feature.search.model

import com.vunbo.watchtogether.data.model.Movie

sealed class SearchState {
    data object Idle : SearchState()
    data class Loading(
        val completedSources: Int = 0,
        val totalSources: Int = 0
    ) : SearchState()

    data object Success : SearchState()
    data class Error(val msg: String) : SearchState()
}

data class SearchSourceGroup(
    val sourceKey: String,
    val sourceName: String,
    val results: List<Movie.Video>
)

data class SearchSourceOption(
    val sourceKey: String,
    val sourceName: String,
    val enabled: Boolean = true
)

data class SearchHistoryUiState(
    val items: List<String> = emptyList()
)

data class SearchDiscoveryUiState(
    val hotWords: List<String> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val isSuggestionLoading: Boolean = false
)
