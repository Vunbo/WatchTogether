package com.vunbo.watchtogether.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonParser
import com.vunbo.watchtogether.data.source.ApiConfig
import com.vunbo.watchtogether.data.model.Movie
import com.vunbo.watchtogether.data.model.SourceBean
import com.vunbo.watchtogether.data.vod.SourceRepository
import com.vunbo.watchtogether.core.network.OkHttpHelper
import com.vunbo.watchtogether.core.storage.SearchHistoryStore
import com.vunbo.watchtogether.data.source.SourceRanker
import com.vunbo.watchtogether.data.source.SourceReputationStore
import com.vunbo.watchtogether.feature.search.model.SearchDiscoveryUiState
import com.vunbo.watchtogether.feature.search.model.SearchHistoryUiState
import com.vunbo.watchtogether.feature.search.model.SearchSourceGroup
import com.vunbo.watchtogether.feature.search.model.SearchSourceOption
import com.vunbo.watchtogether.feature.search.model.SearchState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicInteger

class SearchViewModel : ViewModel() {
    private val apiConfig = ApiConfig.get()
    private var activeSearchSessionId: Long = 0L
    private var searchJob: Job? = null
    private var suggestionJob: Job? = null
    private var hotWordsLoaded = false
    private var currentReputation = SourceReputationStore.load()

    private val _state = MutableStateFlow<SearchState>(SearchState.Idle)
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private val _results = MutableStateFlow<List<Movie.Video>>(emptyList())
    val results: StateFlow<List<Movie.Video>> = _results.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _sourceGroups = MutableStateFlow<List<SearchSourceGroup>>(emptyList())
    val sourceGroups: StateFlow<List<SearchSourceGroup>> = _sourceGroups.asStateFlow()

    private val _selectedSourceKey = MutableStateFlow("")
    val selectedSourceKey: StateFlow<String> = _selectedSourceKey.asStateFlow()

    private val _totalSourceCount = MutableStateFlow(0)
    val totalSourceCount: StateFlow<Int> = _totalSourceCount.asStateFlow()

    private val _searchSourceOptions = MutableStateFlow<List<SearchSourceOption>>(emptyList())
    val searchSourceOptions: StateFlow<List<SearchSourceOption>> = _searchSourceOptions.asStateFlow()

    private val _history = MutableStateFlow(SearchHistoryUiState(SearchHistoryStore.load()))
    val history: StateFlow<SearchHistoryUiState> = _history.asStateFlow()

    private val _discovery = MutableStateFlow(SearchDiscoveryUiState(hotWords = defaultHotWords))
    val discovery: StateFlow<SearchDiscoveryUiState> = _discovery.asStateFlow()

    init {
        ensureSearchSourceOptionsLoaded()
        loadHotWords()
    }

    fun ensureSearchSourceOptionsLoaded(refreshReputation: Boolean = true) {
        if (refreshReputation) {
            currentReputation = SourceReputationStore.load()
        }
        val sources = sortSearchSources(loadSearchableSources())
        val currentEnabled = _searchSourceOptions.value.associate { it.sourceKey to it.enabled }
        _searchSourceOptions.value = sources.map { source ->
            SearchSourceOption(
                sourceKey = source.key,
                sourceName = source.name.ifBlank { source.key },
                enabled = currentEnabled[source.key] ?: true
            )
        }
        _totalSourceCount.value = enabledSearchSourceCount()
    }

    fun onQueryChanged(q: String) {
        _query.value = q
        searchJob?.cancel()
        activeSearchSessionId++
        _results.value = emptyList()
        _sourceGroups.value = emptyList()
        _selectedSourceKey.value = ""
        _totalSourceCount.value = enabledSearchSourceCount()
        _state.value = SearchState.Idle
        updateSuggestions(q)
    }

    fun setQueryAndSearch(q: String) {
        val keyword = q.trim()
        _query.value = keyword
        searchJob?.cancel()
        if (keyword.length < 2) {
            clearQuery()
            return
        }
        searchJob = viewModelScope.launch {
            search(keyword)
        }
    }

    fun clearQuery() {
        searchJob?.cancel()
        suggestionJob?.cancel()
        activeSearchSessionId++
        _query.value = ""
        _results.value = emptyList()
        _sourceGroups.value = emptyList()
        _selectedSourceKey.value = ""
        _totalSourceCount.value = enabledSearchSourceCount()
        _state.value = SearchState.Idle
        _discovery.update { it.copy(suggestions = emptyList(), isSuggestionLoading = false) }
    }

    fun selectSource(sourceKey: String) {
        _selectedSourceKey.value = sourceKey
        applyFilter()
    }

    fun toggleSearchSource(sourceKey: String) {
        _searchSourceOptions.update { options ->
            options.map { option ->
                if (option.sourceKey == sourceKey) option.copy(enabled = !option.enabled) else option
            }
        }
        _totalSourceCount.value = enabledSearchSourceCount()
    }

    fun selectAllSearchSources() {
        _searchSourceOptions.update { options -> options.map { it.copy(enabled = true) } }
        _totalSourceCount.value = enabledSearchSourceCount()
    }

    fun clearAllSearchSources() {
        _searchSourceOptions.update { options -> options.map { it.copy(enabled = false) } }
        _totalSourceCount.value = 0
    }

    fun applySearchSourceSelectionAndRefresh() {
        val keyword = _query.value.trim()
        if (keyword.length < 2) {
            clearQuery()
            return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            search(keyword)
        }
    }

    fun enabledSearchSourceCount(): Int {
        return _searchSourceOptions.value.count { it.enabled }
    }

    fun refreshHistory() {
        _history.value = SearchHistoryUiState(SearchHistoryStore.load())
        if (_query.value.isNotBlank()) {
            updateLocalSuggestions(_query.value)
        }
    }

    fun clearHistory() {
        SearchHistoryStore.clear()
        refreshHistory()
    }

    fun search() {
        val q = _query.value.trim()
        if (q.isBlank()) return
        searchJob?.cancel()
        suggestionJob?.cancel()
        _discovery.update { it.copy(isSuggestionLoading = false) }
        searchJob = viewModelScope.launch {
            search(q)
        }
    }

    private fun updateSuggestions(keyword: String) {
        val q = keyword.trim()
        suggestionJob?.cancel()
        if (q.isBlank()) {
            _discovery.update { it.copy(suggestions = emptyList(), isSuggestionLoading = false) }
            return
        }
        updateLocalSuggestions(q, loading = q.length >= 2)
        if (q.length < 2) return
        suggestionJob = viewModelScope.launch {
            delay(260)
            val remote = fetchSuggestions(q)
            if (_query.value.trim() != q) return@launch
            val merged = mergeWords(remote, localSuggestions(q))
            _discovery.update { it.copy(suggestions = merged, isSuggestionLoading = false) }
        }
    }

    private fun updateLocalSuggestions(keyword: String, loading: Boolean = false) {
        _discovery.update {
            it.copy(
                suggestions = localSuggestions(keyword),
                isSuggestionLoading = loading
            )
        }
    }

    private fun loadHotWords() {
        if (hotWordsLoaded) return
        hotWordsLoaded = true
        viewModelScope.launch {
            val remote = fetchHotWords()
            if (remote.isNotEmpty()) {
                _discovery.update { it.copy(hotWords = remote) }
            }
        }
    }

    private fun localSuggestions(keyword: String): List<String> {
        val q = keyword.trim()
        if (q.isBlank()) return emptyList()
        return mergeWords(
            SearchHistoryStore.load(),
            _discovery.value.hotWords,
            defaultHotWords
        ).filter { it.contains(q, ignoreCase = true) || q.contains(it, ignoreCase = true) }
            .filterNot { it.equals(q, ignoreCase = true) }
            .take(24)
    }

    private suspend fun fetchSuggestions(keyword: String): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            val encoded = URLEncoder.encode(keyword, "UTF-8")
            val url = "https://tv.aiseet.atianqi.com/i-tvbin/qtv_video/search/get_search_smart_box" +
                "?format=json&page_num=0&page_size=30&key=$encoded"
            val body = OkHttpHelper.getBody(url) ?: return@withContext emptyList()
            val root = JsonParser.parseString(body).asJsonObject
            val groups = root.getAsJsonObject("data")
                ?.getAsJsonObject("search_data")
                ?.getAsJsonArray("vecGroupData")
                ?.firstOrNull()
                ?.asJsonObject
                ?.getAsJsonArray("group_data")
                ?: return@withContext emptyList()
            groups.mapNotNull { item ->
                runCatching {
                    item.asJsonObject
                        .getAsJsonObject("dtReportInfo")
                        .getAsJsonObject("reportData")
                        .get("keyword_txt")
                        .asString
                        .trim()
                        .takeIf { it.isNotBlank() }
                }.getOrNull()
            }.distinctBy { it.lowercase() }.take(30)
        }.getOrDefault(emptyList())
    }

    private suspend fun fetchHotWords(): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://node.video.qq.com/x/api/hot_search?channdlId=0&_=${System.currentTimeMillis()}"
            val body = OkHttpHelper.getBody(url) ?: return@withContext emptyList()
            val list = JsonParser.parseString(body).asJsonObject
                .getAsJsonObject("data")
                ?.getAsJsonObject("mapResult")
                ?.getAsJsonObject("0")
                ?.getAsJsonArray("listInfo")
                ?: return@withContext emptyList()
            list.mapNotNull { item ->
                runCatching {
                    item.asJsonObject.get("title").asString
                        .trim()
                        .replace(Regex("[<>《》-]"), "")
                        .split(" ")
                        .firstOrNull()
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                }.getOrNull()
            }.distinctBy { it.lowercase() }.take(20)
        }.getOrDefault(emptyList())
    }

    private suspend fun search(keyword: String) {
        SearchHistoryStore.save(keyword)
        refreshHistory()
        currentReputation = SourceReputationStore.load()
        ensureSearchSourceOptionsLoaded(refreshReputation = false)

        val enabledKeys = _searchSourceOptions.value.filter { it.enabled }.map { it.sourceKey }.toSet()
        val sources = sortSearchSources(loadSearchableSources().filter { it.key in enabledKeys })
        val sessionId = ++activeSearchSessionId

        _results.value = emptyList()
        _sourceGroups.value = emptyList()
        _selectedSourceKey.value = ""
        _totalSourceCount.value = sources.size

        if (sources.isEmpty()) {
            _state.value = SearchState.Error("请至少选择一个搜索源")
            return
        }

        _state.value = SearchState.Loading(0, sources.size)
        val sourceOrder = sources.mapIndexed { index, source -> source.key to index }.toMap()
        val groups = mutableListOf<SearchSourceGroup>()
        val completedSources = AtomicInteger(0)

        supervisorScope {
            sources.forEach { source ->
                launch {
                    val group = searchInSource(source, keyword)
                    if (sessionId != activeSearchSessionId) return@launch

                    if (group.results.isNotEmpty()) {
                        synchronized(groups) {
                            groups.removeAll { it.sourceKey == group.sourceKey }
                            groups.add(group)
                            groups.sortBy { sourceOrder[it.sourceKey] ?: Int.MAX_VALUE }
                            _sourceGroups.value = groups.toList()
                            applyFilter(sortResults = false)
                        }
                    }

                    _state.value = SearchState.Loading(completedSources.incrementAndGet(), sources.size)
                }
            }
        }

        if (sessionId != activeSearchSessionId) return

        _sourceGroups.value = groups.toList()
        applyFilter()
        _state.value = if (_results.value.isEmpty()) {
            SearchState.Error("没有找到相关资源")
        } else {
            SearchState.Success
        }
    }

    private suspend fun searchInSource(source: SourceBean, keyword: String): SearchSourceGroup {
        val repository = SourceRepository(apiConfig)
        return try {
            repository.getSearch(source.key, keyword)
            val videos = repository.searchResult.value?.movie?.videoList
                ?.map { it.copy(sourceKey = source.key) }
                .orEmpty()
            SearchSourceGroup(
                sourceKey = source.key,
                sourceName = source.name.ifBlank { source.key },
                results = videos
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            SearchSourceGroup(
                sourceKey = source.key,
                sourceName = source.name.ifBlank { source.key },
                results = emptyList()
            )
        }
    }

    private fun loadSearchableSources(): List<SourceBean> {
        return apiConfig.getSearchableSources().ifEmpty {
            apiConfig.getAllSources().filter { it.searchable == 1 || it.quickSearch == 1 }
        }
    }

    private fun sortSearchSources(sources: List<SourceBean>): List<SourceBean> {
        return SourceRanker.sortSources(sources, currentReputation)
    }

    private fun applyFilter(sortResults: Boolean = true) {
        val selected = _selectedSourceKey.value
        val groups = _sourceGroups.value
        val filtered = if (selected.isBlank()) {
            groups.flatMap { it.results }
        } else {
            groups.firstOrNull { it.sourceKey == selected }?.results.orEmpty()
        }
        if (!sortResults) {
            _results.value = filtered
            return
        }
        val sourceNames = groups.associate { it.sourceKey to it.sourceName }
        _results.value = SourceRanker.sortSearchResults(
            results = filtered,
            keyword = _query.value,
            sourceNames = sourceNames,
            reputation = currentReputation
        )
    }

    private fun mergeWords(vararg sources: List<String>): List<String> {
        return sources
            .flatMap { it }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
    }

    companion object {
        private val defaultHotWords = listOf(
            "吞噬星空",
            "凡人修仙传",
            "仙逆",
            "遮天",
            "斗破苍穹",
            "完美世界",
            "庆余年",
            "长安的荔枝",
            "藏海传",
            "临江仙",
            "折腰",
            "歌手2025"
        )
    }
}
