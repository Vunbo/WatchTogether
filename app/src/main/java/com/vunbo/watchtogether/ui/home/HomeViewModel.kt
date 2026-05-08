package com.vunbo.watchtogether.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vunbo.watchtogether.WatchTogetherApp
import com.vunbo.watchtogether.data.api.ApiConfig
import com.vunbo.watchtogether.data.local.RoomDataManager
import com.vunbo.watchtogether.data.model.Movie
import com.vunbo.watchtogether.data.model.MovieSort
import com.vunbo.watchtogether.data.model.SourceBean
import com.vunbo.watchtogether.data.model.VodInfo
import com.vunbo.watchtogether.data.repository.SourceRepository
import com.vunbo.watchtogether.data.util.AppEvent
import com.vunbo.watchtogether.data.util.AppEventBus
import com.vunbo.watchtogether.data.util.HawkConfig
import com.vunbo.watchtogether.data.util.PrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class HomeState {
    object Loading : HomeState()
    object Success : HomeState()
    data class Error(val msg: String) : HomeState()
}

class HomeViewModel : ViewModel() {
    private val apiConfig = ApiConfig.get()
    private val repository = SourceRepository(apiConfig)
    private val roomDataManager = RoomDataManager(WatchTogetherApp.instance)

    private val _state = MutableStateFlow<HomeState>(HomeState.Loading)
    val state: StateFlow<HomeState> = _state.asStateFlow()

    private val _categories = MutableStateFlow<List<MovieSort.SortData>>(emptyList())
    val categories: StateFlow<List<MovieSort.SortData>> = _categories.asStateFlow()

    private val _videos = MutableStateFlow<List<Movie.Video>>(emptyList())
    val videos: StateFlow<List<Movie.Video>> = _videos.asStateFlow()

    private val _homeSections = MutableStateFlow<List<HomeSection>>(emptyList())
    val homeSections: StateFlow<List<HomeSection>> = _homeSections.asStateFlow()

    private val _selectedCategory = MutableStateFlow("")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _sourceName = MutableStateFlow("")
    val sourceName: StateFlow<String> = _sourceName.asStateFlow()

    private val _availableSources = MutableStateFlow<List<SourceBean>>(emptyList())
    val availableSources: StateFlow<List<SourceBean>> = _availableSources.asStateFlow()

    init {
        // 监听配置变更事件，自动刷新
        viewModelScope.launch {
            AppEventBus.events.collect { event ->
                if (
                    event is AppEvent.ApiUrlChange ||
                    event is AppEvent.HomeRecommendChange ||
                    event is AppEvent.HistoryRefresh
                ) {
                    loadData()
                }
            }
        }
    }

    fun loadData() {
        viewModelScope.launch {
            _state.value = HomeState.Loading

            // 确保配置已加载
            val source = apiConfig.homeSource
            if (source == null) {
                // 尝试加载配置
                val loaded = apiConfig.loadConfig()
                if (!loaded || apiConfig.homeSource == null) {
                    _state.value = HomeState.Error("请先在设置中配置API地址")
                    return@launch
                }
            }

            val currentSource = apiConfig.homeSource ?: return@launch
            _availableSources.value = apiConfig.getAllSources()
            _sourceName.value = currentSource.name
            _videos.value = emptyList()
            _homeSections.value = emptyList()
            _categories.value = emptyList()
            _selectedCategory.value = ""

            try {
                val sections = HomeRankProvider.loadHomeSections()
                if (sections.isNotEmpty()) {
                    _homeSections.value = sections
                    loadSiteCategories(currentSource)
                } else {
                    when (PrefsManager.getInt(HawkConfig.HOME_REC, HOME_REC_DOUBAN)) {
                        HOME_REC_HISTORY -> loadHistoryRecommendation()
                        HOME_REC_SITE -> loadSiteRecommendation(currentSource)
                        else -> loadSiteRecommendation(currentSource)
                    }
                }
                _state.value = HomeState.Success
            } catch (e: Exception) {
                _state.value = HomeState.Error(e.message ?: "加载失败")
            }
        }
    }

    fun selectCategory(catId: String) {
        if (catId.isBlank()) return
        _selectedCategory.value = catId
        if (catId == HOME_TAB_ID) {
            _videos.value = emptyList()
            return
        }
        val cat = _categories.value.find { it.id == catId } ?: return
        loadCategory(cat)
    }

    private fun loadCategory(sortData: MovieSort.SortData) {
        if (sortData.id.isBlank()) return
        viewModelScope.launch {
            try {
                repository.getList(sortData, 1)
                // 直接读取当前值
                val listXml = repository.listResult.value
                listXml?.movie?.videoList?.let {
                    _videos.value = it
                }
            } catch (e: Exception) {
                // 非关键错误
            }
        }
    }

    fun getCurrentSourceKey(): String {
        return apiConfig.homeSource?.key ?: ""
    }

    fun switchSource(sourceKey: String) {
        val source = apiConfig.getSource(sourceKey) ?: return
        if (apiConfig.homeSource?.key == source.key) return
        apiConfig.setSourceBean(source)
        _sourceName.value = source.name
        loadData()
    }

    private suspend fun loadSiteRecommendation(currentSource: SourceBean) {
        repository.getSort(currentSource.key)
        val sortXml = repository.sortResult.value
        val recommendedVideos = sortXml?.videoList.orEmpty()
        sortXml?.classes?.let { classes ->
            val validCategories = classes.sortList.filter { it.id.isNotBlank() && it.name.isNotBlank() }
            _categories.value = validCategories
            if (validCategories.isNotEmpty() && recommendedVideos.isEmpty()) {
                val firstCat = validCategories.first()
                _selectedCategory.value = firstCat.id
                loadCategory(firstCat)
            }
        }
        recommendedVideos.takeIf { it.isNotEmpty() }?.let { vids ->
            _videos.value = vids.map { it.copy(sourceKey = currentSource.key) }.toMutableList()
        }
    }

    private suspend fun loadSiteCategories(currentSource: SourceBean) {
        repository.getSort(currentSource.key)
        val sortXml = repository.sortResult.value
        val validCategories = sortXml?.classes?.sortList.orEmpty()
            .filter { it.id.isNotBlank() && it.name.isNotBlank() }
        val fixedTabs = listOf(MovieSort.SortData(id = HOME_TAB_ID, name = "主页"))
        _categories.value = fixedTabs + validCategories
        _selectedCategory.value = HOME_TAB_ID
    }

    private suspend fun loadHistoryRecommendation() {
        val historyVideos = withContext(Dispatchers.IO) {
            roomDataManager.getAllVodRecord(60).mapNotNull { it.toHomeVideo() }
        }
        _categories.value = emptyList()
        _selectedCategory.value = ""
        _videos.value = historyVideos.toMutableList()
    }

    private fun VodInfo.toHomeVideo(): Movie.Video? {
        val title = name?.trim().orEmpty()
        if (title.isBlank()) return null
        return Movie.Video(
            id = id,
            tid = tid,
            name = title,
            type = type,
            pic = pic,
            lang = lang,
            area = area,
            year = year,
            state = state,
            note = playNote?.takeIf { it.isNotBlank() } ?: note,
            actor = actor,
            director = director,
            des = des,
            last = last,
            sourceKey = sourceKey,
            tag = "history"
        )
    }

    companion object {
        private const val HOME_REC_DOUBAN = 0
        private const val HOME_REC_SITE = 1
        private const val HOME_REC_HISTORY = 2
        private const val HOME_TAB_ID = "__home_rank__"
    }
}
