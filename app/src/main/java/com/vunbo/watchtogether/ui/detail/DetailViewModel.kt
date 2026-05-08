package com.vunbo.watchtogether.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vunbo.watchtogether.WatchTogetherApp
import com.vunbo.watchtogether.data.api.ApiConfig
import com.vunbo.watchtogether.data.local.RoomDataManager
import com.vunbo.watchtogether.data.model.VodInfo
import com.vunbo.watchtogether.data.model.VodSeries
import com.vunbo.watchtogether.data.repository.SourceRepository
import com.vunbo.watchtogether.data.util.SourceRanker
import com.vunbo.watchtogether.data.util.SourceReputationStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.min

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

class DetailViewModel : ViewModel() {
    private val repository = SourceRepository(ApiConfig.get())
    private val roomDataManager = RoomDataManager(WatchTogetherApp.instance)

    private val _state = MutableStateFlow<DetailState>(DetailState.Loading)
    val state: StateFlow<DetailState> = _state.asStateFlow()

    private val _vodInfo = MutableStateFlow<VodInfo?>(null)
    val vodInfo: StateFlow<VodInfo?> = _vodInfo.asStateFlow()

    private val _selectedFlag = MutableStateFlow("")
    val selectedFlag: StateFlow<String> = _selectedFlag.asStateFlow()

    private val _episodes = MutableStateFlow<List<VodSeries>>(emptyList())
    val episodes: StateFlow<List<VodSeries>> = _episodes.asStateFlow()

    private val _visibleEpisodes = MutableStateFlow<List<VodSeries>>(emptyList())
    val visibleEpisodes: StateFlow<List<VodSeries>> = _visibleEpisodes.asStateFlow()

    private val _episodePages = MutableStateFlow<List<EpisodePage>>(emptyList())
    val episodePages: StateFlow<List<EpisodePage>> = _episodePages.asStateFlow()

    private val _selectedEpisodePage = MutableStateFlow(0)
    val selectedEpisodePage: StateFlow<Int> = _selectedEpisodePage.asStateFlow()

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    private var currentSourceKey: String = ""
    private var currentVodId: String = ""

    fun loadDetail(sourceKey: String, vodId: String) {
        currentSourceKey = sourceKey
        currentVodId = vodId

        viewModelScope.launch {
            _state.value = DetailState.Loading

            val cached = roomDataManager.getVodInfo(sourceKey, vodId)
            if (cached != null) {
                _vodInfo.value = cached
                onVodInfoReady(cached)
                _state.value = DetailState.Success
                return@launch
            }

            try {
                repository.getDetail(sourceKey, vodId)
                val video = repository.detailResult.value?.movie?.videoList?.firstOrNull()
                if (video == null) {
                    SourceReputationStore.recordDetailFailure(sourceKey)
                    _state.value = DetailState.Error("无法加载视频详情")
                    return@launch
                }
                val info = VodInfo().apply {
                    setVideo(video)
                    this.sourceKey = sourceKey
                }
                _vodInfo.value = info
                onVodInfoReady(info)
                roomDataManager.insertVodRecord(sourceKey, info)
                _state.value = DetailState.Success
            } catch (e: Exception) {
                SourceReputationStore.recordDetailFailure(sourceKey)
                _state.value = DetailState.Error(e.message ?: "无法加载视频详情")
            }
        }
    }

    private fun onVodInfoReady(info: VodInfo) {
        _isFavorite.value = roomDataManager.isVodCollect(currentSourceKey, currentVodId)
        sortPlayableFlags(info)
        val defaultFlag = info.playFlag
            ?.takeIf { it.isNotBlank() && info.seriesMap.containsKey(it) }
            ?: info.seriesFlags.firstOrNull()?.name
            .orEmpty()
        selectFlag(defaultFlag)
    }

    fun selectFlag(flag: String) {
        if (flag.isBlank()) {
            _selectedFlag.value = ""
            _episodes.value = emptyList()
            _episodePages.value = emptyList()
            _visibleEpisodes.value = emptyList()
            _selectedEpisodePage.value = 0
            return
        }
        _selectedFlag.value = flag
        val list = _vodInfo.value?.seriesMap?.get(flag).orEmpty()
        _episodes.value = list
        val pages = buildEpisodePages(list.size)
        _episodePages.value = pages
        val targetPage = when {
            list.isEmpty() -> 0
            _vodInfo.value?.playFlag == flag -> (_vodInfo.value?.playIndex ?: 0) / 40
            else -> 0
        }.coerceIn(0, (pages.lastIndex).coerceAtLeast(0))
        selectEpisodePage(targetPage)
    }

    fun selectEpisodePage(pageIndex: Int) {
        val pages = _episodePages.value
        if (pages.isEmpty()) {
            _selectedEpisodePage.value = 0
            _visibleEpisodes.value = emptyList()
            return
        }
        val validIndex = pageIndex.coerceIn(0, pages.lastIndex)
        _selectedEpisodePage.value = validIndex
        val page = pages[validIndex]
        _visibleEpisodes.value = _episodes.value.subList(page.startIndex, page.endIndex)
    }

    fun getEpisodeAbsoluteIndex(episode: VodSeries): Int {
        return _episodes.value.indexOfFirst { it.name == episode.name && it.url == episode.url }.coerceAtLeast(0)
    }

    fun getCurrentEpisodeIndex(): Int {
        val info = _vodInfo.value ?: return 0
        if (info.playFlag == _selectedFlag.value) {
            return info.playIndex.coerceAtLeast(0)
        }
        return 0
    }

    fun markPlayed(flag: String, playIndex: Int) {
        val info = _vodInfo.value ?: return
        info.playFlag = flag
        info.playIndex = playIndex
        _vodInfo.value = info.copy()
        roomDataManager.insertVodRecord(currentSourceKey, info)
    }

    fun toggleFavorite() {
        val info = _vodInfo.value ?: return
        if (_isFavorite.value) {
            roomDataManager.deleteVodCollect(currentSourceKey, info)
        } else {
            roomDataManager.insertVodCollect(currentSourceKey, info)
        }
        _isFavorite.value = !_isFavorite.value
    }

    private fun buildEpisodePages(total: Int, pageSize: Int = 40): List<EpisodePage> {
        if (total <= 0) {
            return emptyList()
        }
        val result = mutableListOf<EpisodePage>()
        var start = 0
        while (start < total) {
            val end = min(start + pageSize, total)
            result += EpisodePage(
                startIndex = start,
                endIndex = end,
                label = "${start + 1}-${end}"
            )
            start = end
        }
        return result
    }

    private fun sortPlayableFlags(info: VodInfo) {
        if (info.seriesFlags.size <= 1) return
        val sortedFlags = SourceRanker.sortFlags(
            sourceKey = currentSourceKey,
            flags = info.seriesFlags,
            reputation = SourceReputationStore.load()[currentSourceKey]
        )
        val sortedMap = linkedMapOf<String, MutableList<VodSeries>>()
        sortedFlags.forEach { flag ->
            info.seriesMap[flag.name]?.let { sortedMap[flag.name] = it }
        }
        info.seriesFlags.clear()
        info.seriesFlags.addAll(sortedFlags)
        info.seriesMap.clear()
        info.seriesMap.putAll(sortedMap)
    }
}
