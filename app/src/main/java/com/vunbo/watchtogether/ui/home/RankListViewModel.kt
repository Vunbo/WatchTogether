package com.vunbo.watchtogether.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vunbo.watchtogether.data.model.Movie
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class RankListState {
    data object Loading : RankListState()
    data object Success : RankListState()
    data class Error(val msg: String) : RankListState()
}

class RankListViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
    val rankKey: String = savedStateHandle["rankKey"] ?: ""
    val title: String = HomeRankDefinition.fromKey(rankKey)?.title ?: "榜单"

    private val _state = MutableStateFlow<RankListState>(RankListState.Loading)
    val state: StateFlow<RankListState> = _state.asStateFlow()

    private val _videos = MutableStateFlow<List<Movie.Video>>(emptyList())
    val videos: StateFlow<List<Movie.Video>> = _videos.asStateFlow()

    fun load() {
        val definition = HomeRankDefinition.fromKey(rankKey)
        if (definition == null) {
            _state.value = RankListState.Error("榜单不存在")
            return
        }
        viewModelScope.launch {
            _state.value = RankListState.Loading
            val list = HomeRankProvider.loadRank(definition)
            _videos.value = list
            _state.value = if (list.isEmpty()) RankListState.Error("暂无榜单数据") else RankListState.Success
        }
    }
}
