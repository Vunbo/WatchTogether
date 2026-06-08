package com.vunbo.watchtogether.feature.live

import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.vunbo.watchtogether.app.WatchTogetherApp
import com.vunbo.watchtogether.data.live.LiveEpgResolver
import com.vunbo.watchtogether.data.live.LiveFavoriteStore
import com.vunbo.watchtogether.data.live.LiveRepository
import com.vunbo.watchtogether.data.model.Epginfo
import com.vunbo.watchtogether.data.model.LiveChannelItem
import com.vunbo.watchtogether.data.model.LiveSource
import com.vunbo.watchtogether.feature.live.model.LiveChannelRef
import com.vunbo.watchtogether.feature.live.model.LivePlaybackMode
import com.vunbo.watchtogether.feature.live.model.LiveUiState
import com.vunbo.watchtogether.core.event.AppEvent
import com.vunbo.watchtogether.core.event.AppEventBus
import com.vunbo.watchtogether.core.storage.HawkConfig
import com.vunbo.watchtogether.core.player.PlayerHelper
import com.vunbo.watchtogether.core.storage.PrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder

class LiveViewModel : ViewModel() {
    private val repository = LiveRepository()
    val exoPlayer: ExoPlayer = ExoPlayer.Builder(WatchTogetherApp.instance).build()

    private val _uiState = MutableStateFlow(
        LiveUiState(
            favorites = LiveFavoriteStore.load(),
            playerType = normalizePlayerType(PrefsManager.getInt(HawkConfig.PLAY_TYPE, PlayerHelper.PLAYER_TYPE_EXO)),
            scaleType = PrefsManager.getInt(HawkConfig.PLAY_SCALE, PlayerHelper.SCALE_DEFAULT),
            switchTimeoutSeconds = PrefsManager.getInt(HawkConfig.LIVE_CONNECT_TIMEOUT, 10).coerceIn(5, 30)
        )
    )
    val uiState: StateFlow<LiveUiState> = _uiState.asStateFlow()

    private var autoTriedLineIndex = -1
    private var messageJob: Job? = null
    private var currentPlayUrl: String? = null
    private var currentPlayMode: LiveMediaMode? = null
    private var progressiveFallbackUrl: String? = null
    private var stoppedByLeavingPage = false
    private var pageVisible = false
    private var pendingSourceRefresh = false

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _uiState.update { it.copy(isPlaying = isPlaying) }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _uiState.update {
                    it.copy(isBuffering = playbackState == Player.STATE_BUFFERING)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (stoppedByLeavingPage) return
                Log.e(TAG, "直播播放失败: mode=$currentPlayMode url=${currentPlayUrl.orEmpty()}", error)
                tryNextLineOrShowError()
            }
        })
        viewModelScope.launch {
            AppEventBus.events.collect { event ->
                if (event is AppEvent.LiveSourceChange || event is AppEvent.ApiUrlChange) {
                    handleSourceConfigChanged()
                }
            }
        }
    }

    fun setPageVisible(visible: Boolean) {
        pageVisible = visible
        if (!visible) {
            leavePage()
        }
    }

    fun loadData() {
        if (!pageVisible) return
        if (_uiState.value.loading) return
        if (pendingSourceRefresh) {
            pendingSourceRefresh = false
            refreshInternal(forceRefresh = false, autoPlay = true)
            return
        }
        if (_uiState.value.sources.isNotEmpty()) {
            if (stoppedByLeavingPage && _uiState.value.currentUrl != null) {
                stoppedByLeavingPage = false
                playCurrent()
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val sources = withContext(Dispatchers.IO) { repository.loadSources(forceRefresh = false) }
            if (sources.isEmpty()) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        error = "暂无可用直播源，请检查订阅 lives 或设置页直播源地址"
                    )
                }
                return@launch
            }
            val selectedSource = resolveSavedSourceIndex(sources)
            val groups = sources[selectedSource].groups
            val selectedGroup = PrefsManager.getInt(HawkConfig.LIVE_GROUP_INDEX, 0)
                .coerceIn(0, groups.lastIndex)
            val savedChannelKey = PrefsManager.getString(HawkConfig.LIVE_CHANNEL)
            val channels = groups[selectedGroup].channels
            val selectedChannel = channels.indexOfFirst { it.name == savedChannelKey }
                .takeIf { it >= 0 } ?: 0
            val selectedLine = PrefsManager.getInt(HawkConfig.LIVE_LINE_INDEX, 0)
                .coerceIn(0, channels.getOrNull(selectedChannel)?.urls?.lastIndex ?: 0)
            _uiState.update {
                it.copy(
                    loading = false,
                    sources = sources,
                    selectedSourceIndex = selectedSource,
                    selectedGroupIndex = selectedGroup,
                    selectedChannelIndex = selectedChannel,
                    selectedLineIndex = selectedLine,
                    selectedDisplayGroupSourceIndex = selectedGroup,
                    error = null,
                    currentProgram = LiveUiState.NO_PROGRAM_TEXT,
                    nextProgram = LiveUiState.NO_PROGRAM_TEXT
                )
            }
            saveLiveSelection()
            loadProgramForCurrent()
            playCurrent()
        }
    }

    fun refresh(forceRefresh: Boolean = true) {
        refreshInternal(forceRefresh = forceRefresh, autoPlay = pageVisible)
    }

    private fun handleSourceConfigChanged() {
        if (!pageVisible) {
            pendingSourceRefresh = true
            return
        }
        refreshInternal(forceRefresh = false, autoPlay = true)
    }

    private fun refreshInternal(forceRefresh: Boolean = true, autoPlay: Boolean) {
        if (!pageVisible && !autoPlay) {
            pendingSourceRefresh = true
            stopPlayback()
            return
        }
        exoPlayer.stop()
        val previous = _uiState.value
        _uiState.update { it.copy(loading = true, error = null, isPlaying = false, isBuffering = false) }
        viewModelScope.launch {
            val sources = withContext(Dispatchers.IO) { repository.loadSources(forceRefresh = forceRefresh) }
            if (sources.isEmpty()) {
                if (previous.sources.isNotEmpty()) {
                    _uiState.value = previous.copy(
                        loading = false,
                        isPlaying = false,
                        isBuffering = false,
                        error = "直播源刷新失败，已使用上次直播源"
                    )
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        loading = false,
                        error = "暂无可用直播源，请检查订阅 lives 或设置页直播源地址"
                    )
                }
                return@launch
            }
            _uiState.update { state ->
                state.copy(
                    loading = false,
                    sources = sources,
                    selectedSourceIndex = resolveSavedSourceIndex(sources),
                    selectedGroupIndex = 0,
                    selectedChannelIndex = 0,
                    selectedLineIndex = 0,
                    selectedDisplayGroupSourceIndex = 0,
                    error = null
                )
            }
            saveLiveSelection()
            loadProgramForCurrent()
            if (autoPlay) {
                playCurrent()
            } else {
                stopPlayback()
            }
        }
    }

    fun selectLiveSource(index: Int) {
        val sources = _uiState.value.sources
        if (index !in sources.indices) return
        _uiState.update {
            it.copy(
                selectedSourceIndex = index,
                selectedGroupIndex = 0,
                selectedChannelIndex = 0,
                selectedLineIndex = 0,
                selectedDisplayGroupSourceIndex = 0,
                error = null
            )
        }
        saveLiveSelection()
        loadProgramForCurrent()
        playCurrent()
    }

    fun selectGroup(index: Int) {
        val groups = _uiState.value.groups
        if (index !in groups.indices) return
        _uiState.update {
            it.copy(
                selectedGroupIndex = index,
                selectedChannelIndex = 0,
                selectedLineIndex = 0,
                selectedDisplayGroupSourceIndex = index,
                error = null
            )
        }
        saveLiveSelection()
        loadProgramForCurrent()
        playCurrent()
    }

    fun selectDisplayGroup(index: Int) {
        val group = _uiState.value.displayGroups.getOrNull(index) ?: return
        _uiState.update {
            it.copy(selectedDisplayGroupSourceIndex = group.sourceGroupIndex)
        }
    }

    fun selectChannel(index: Int) {
        if (index !in _uiState.value.channels.indices) return
        _uiState.update {
            it.copy(
                selectedChannelIndex = index,
                selectedLineIndex = 0,
                error = null
            )
        }
        saveLiveSelection()
        loadProgramForCurrent()
        playCurrent()
    }

    fun selectDisplayChannel(index: Int) {
        val ref = _uiState.value.displayChannels.getOrNull(index) ?: return
        selectChannelRef(ref)
    }

    private fun selectChannelRef(ref: LiveChannelRef) {
        _uiState.update {
            it.copy(
                selectedGroupIndex = ref.groupIndex,
                selectedChannelIndex = ref.channelIndex,
                selectedLineIndex = 0,
                selectedDisplayGroupSourceIndex = _uiState.value.selectedDisplayGroupSourceIndex,
                playbackMode = LivePlaybackMode.Live,
                catchupProgram = null,
                error = null
            )
        }
        saveLiveSelection()
        loadProgramForCurrent()
        playCurrent()
    }

    fun selectLine(index: Int) {
        val channel = _uiState.value.currentChannel ?: return
        if (index !in channel.urls.indices) return
        _uiState.update {
            it.copy(
                selectedLineIndex = index,
                playbackMode = LivePlaybackMode.Live,
                catchupProgram = null,
                error = null
            )
        }
        saveLiveSelection()
        playCurrent()
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query, selectedDisplayGroupSourceIndex = 0) }
    }

    fun clearSearchQuery() {
        _uiState.update { it.copy(searchQuery = "") }
    }

    fun togglePlay() {
        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
    }

    fun stopPlayback() {
        stoppedByLeavingPage = true
        exoPlayer.playWhenReady = false
        exoPlayer.pause()
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        currentPlayUrl = null
        currentPlayMode = null
        progressiveFallbackUrl = null
        _uiState.update { it.copy(isPlaying = false, isBuffering = false) }
    }

    fun leavePage() {
        stopPlayback()
        _uiState.update {
            it.copy(
                loading = false,
                sources = emptyList(),
                selectedSourceIndex = 0,
                selectedGroupIndex = 0,
                selectedChannelIndex = 0,
                selectedLineIndex = 0,
                selectedDisplayGroupSourceIndex = 0,
                currentProgram = LiveUiState.NO_PROGRAM_TEXT,
                nextProgram = LiveUiState.NO_PROGRAM_TEXT,
                programList = emptyList(),
                playbackMode = LivePlaybackMode.Live,
                catchupProgram = null,
                error = null
            )
        }
    }

    fun replayCurrent() {
        stoppedByLeavingPage = false
        if (_uiState.value.playbackMode == LivePlaybackMode.Catchup) {
            playCatchupCurrent()
        } else {
            playCurrent()
        }
    }

    fun toggleCurrentFavorite() {
        val state = _uiState.value
        val source = state.currentSource ?: return
        val groupName = state.groups.getOrNull(state.selectedGroupIndex)?.name ?: return
        val channel = state.currentChannel ?: return
        val favorite = LiveFavoriteStore.buildFavorite(source, groupName, channel)
        val next = if (state.favorites.any { it.key == favorite.key }) {
            showUserMessage("已取消收藏")
            state.favorites.filterNot { it.key == favorite.key }
        } else {
            showUserMessage("已加入收藏")
            state.favorites + favorite
        }
        LiveFavoriteStore.save(next)
        _uiState.update { it.copy(favorites = next) }
    }

    fun playCatchup(program: Epginfo) {
        if (!LiveEpgResolver.isPast(program)) {
            showUserMessage("节目尚未播出")
            return
        }
        _uiState.update {
            it.copy(
                playbackMode = LivePlaybackMode.Catchup,
                catchupProgram = program,
                error = null
            )
        }
        playCatchupCurrent()
    }

    fun returnToLive() {
        _uiState.update {
            it.copy(
                playbackMode = LivePlaybackMode.Live,
                catchupProgram = null,
                error = null
            )
        }
        playCurrent()
    }

    fun setScaleType(scaleType: Int) {
        PrefsManager.putInt(HawkConfig.PLAY_SCALE, scaleType)
        _uiState.update { it.copy(scaleType = scaleType) }
    }

    private fun normalizePlayerType(type: Int): Int {
        if (type != PlayerHelper.PLAYER_TYPE_EXO) {
            PrefsManager.putInt(HawkConfig.PLAY_TYPE, PlayerHelper.PLAYER_TYPE_EXO)
        }
        return PlayerHelper.PLAYER_TYPE_EXO
    }

    fun setSwitchTimeout(seconds: Int) {
        val value = seconds.coerceIn(5, 30)
        PrefsManager.putInt(HawkConfig.LIVE_CONNECT_TIMEOUT, value)
        _uiState.update { it.copy(switchTimeoutSeconds = value) }
    }

    fun currentVideoAspectRatio(): Float {
        val videoSize = exoPlayer.videoSize
        if (videoSize.width <= 0 || videoSize.height <= 0) return 0f
        return (videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height).takeIf { it > 0f } ?: 0f
    }

    private fun resolveSavedSourceIndex(sources: List<LiveSource>): Int {
        val savedId = PrefsManager.getString(HawkConfig.LIVE_SOURCE_ID)
        if (savedId.isNotBlank()) {
            val index = sources.indexOfFirst { it.id == savedId }
            if (index >= 0) return index
        }
        return PrefsManager.getInt(HawkConfig.LIVE_SOURCE_INDEX, 0).coerceIn(0, sources.lastIndex)
    }

    private fun saveLiveSelection() {
        val state = _uiState.value
        val source = state.currentSource
        source?.let { PrefsManager.putString(HawkConfig.LIVE_SOURCE_ID, it.id) }
        PrefsManager.putInt(HawkConfig.LIVE_SOURCE_INDEX, state.selectedSourceIndex)
        PrefsManager.putInt(HawkConfig.LIVE_GROUP_INDEX, state.selectedGroupIndex)
        PrefsManager.putInt(HawkConfig.LIVE_LINE_INDEX, state.selectedLineIndex)
        state.currentChannel?.let { PrefsManager.putString(HawkConfig.LIVE_CHANNEL, it.name) }
    }

    private fun loadProgramForCurrent() {
        _uiState.update {
            it.copy(
                currentProgram = LiveUiState.NO_PROGRAM_TEXT,
                nextProgram = LiveUiState.NO_PROGRAM_TEXT,
                programList = emptyList()
            )
        }
        val channel = _uiState.value.currentChannel ?: return
        val epgName = channel.epgName?.takeIf { it.isNotBlank() }
            ?: channel.tvgId?.takeIf { it.isNotBlank() }
            ?: channel.name
        viewModelScope.launch(Dispatchers.IO) {
            // First pass: keep EPG nonblocking and conservative. Unsupported sources
            // fall back to "暂无播放预告" instead of delaying live playback.
            val program = LiveEpgResolver.loadPrograms(epgName)
            if (program != null) {
                _uiState.update {
                    it.copy(
                        currentProgram = program.current,
                        nextProgram = program.next ?: LiveUiState.NO_PROGRAM_TEXT,
                        programList = program.programs
                    )
                }
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun playCurrent(forceProgressive: Boolean = false) {
        val state = _uiState.value
        val channel = state.currentChannel ?: return
        val rawUrl = state.currentUrl ?: return
        stoppedByLeavingPage = false
        _uiState.update {
            it.copy(playbackMode = LivePlaybackMode.Live, catchupProgram = null)
        }
        playUrl(rawUrl, channel, forceProgressive, LivePlaybackMode.Live)
    }

    @OptIn(UnstableApi::class)
    private fun playCatchupCurrent() {
        val state = _uiState.value
        val channel = state.currentChannel ?: return
        val rawLiveUrl = state.currentUrl ?: return
        val program = state.catchupProgram ?: return
        val catchupUrl = LiveEpgResolver.buildCatchupUrl(channel, program, rawLiveUrl)
        if (catchupUrl.isNullOrBlank()) {
            _uiState.update {
                it.copy(
                    playbackMode = LivePlaybackMode.Live,
                    catchupProgram = null,
                    error = "当前频道不支持回看",
                    isBuffering = false
                )
            }
            return
        }
        stoppedByLeavingPage = false
        playUrl(catchupUrl, channel, forceProgressive = false, playbackMode = LivePlaybackMode.Catchup)
    }

    @OptIn(UnstableApi::class)
    private fun playUrl(
        rawUrl: String,
        channel: LiveChannelItem,
        forceProgressive: Boolean,
        playbackMode: LivePlaybackMode
    ) {
        val state = _uiState.value
        val liveRequest = buildLiveRequest(rawUrl, channel)
        val url = liveRequest.url
        autoTriedLineIndex = state.selectedLineIndex
        _uiState.update { it.copy(error = null, isBuffering = true) }
        runCatching {
            val dataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(liveRequest.headers["User-Agent"] ?: DEFAULT_LIVE_USER_AGENT)
                .setAllowCrossProtocolRedirects(true)
                .setDefaultRequestProperties(liveRequest.headers)
            val mediaItem = MediaItem.fromUri(Uri.parse(url))
            val useHls = shouldUseHls(url) && !forceProgressive
            currentPlayUrl = url
            currentPlayMode = if (useHls) LiveMediaMode.Hls else LiveMediaMode.Progressive
            Log.i(
                TAG,
                "开始播放直播: mode=$playbackMode, source=${state.currentSource?.name.orEmpty()}, " +
                    "group=${state.groups.getOrNull(state.selectedGroupIndex)?.name.orEmpty()}, " +
                    "channel=${channel.name}, line=${state.selectedLineIndex + 1}/${channel.urls.size}, " +
                    "mode=$currentPlayMode, url=$url"
            )
            val mediaSource = if (useHls) {
                HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
            } else {
                ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
            }
            exoPlayer.stop()
            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            exoPlayer.playbackParameters = PlaybackParameters(1f)
        }.onFailure { error ->
            Log.e(TAG, "直播播放初始化失败: url=$url", error)
            _uiState.update {
                it.copy(error = "播放失败：${error.message ?: "未知错误"}", isBuffering = false)
            }
        }
    }

    private fun tryNextLineOrShowError() {
        if (stoppedByLeavingPage) return
        val state = _uiState.value
        if (state.playbackMode == LivePlaybackMode.Catchup) {
            _uiState.update {
                it.copy(error = "回看播放失败", isBuffering = false)
            }
            return
        }
        val channel = state.currentChannel ?: return
        val url = currentPlayUrl
        if (
            currentPlayMode == LiveMediaMode.Hls &&
            !url.isNullOrBlank() &&
            !isExplicitHls(url) &&
            progressiveFallbackUrl != url
        ) {
            progressiveFallbackUrl = url
            _uiState.update {
                it.copy(error = "当前线路播放失败，正在尝试兼容模式")
            }
            playCurrent(forceProgressive = true)
            return
        }
        progressiveFallbackUrl = null
        val next = state.selectedLineIndex + 1
        if (next in channel.urls.indices && autoTriedLineIndex != next) {
            _uiState.update {
                it.copy(selectedLineIndex = next, error = "当前线路不可用，正在切换源${next + 1}")
            }
            saveLiveSelection()
            playCurrent()
        } else {
            _uiState.update {
                it.copy(error = "当前频道暂不可用", isBuffering = false)
            }
        }
    }

    private fun showUserMessage(message: String) {
        messageJob?.cancel()
        _uiState.update { it.copy(userMessage = message) }
        messageJob = viewModelScope.launch {
            delay(1800)
            _uiState.update { it.copy(userMessage = null) }
        }
    }

    private fun shouldUseHls(url: String): Boolean {
        val lower = url.lowercase()
        if (isExplicitHls(lower)) {
            return true
        }
        val progressiveHints = listOf(".flv", ".mp4", ".ts", ".avi", ".mkv", ".mov", ".mp3")
        return progressiveHints.none { lower.contains(it) }
    }

    private fun isExplicitHls(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains(".m3u8") ||
            lower.contains("m3u8") ||
            lower.contains("/hls") ||
            lower.contains("playlist")
    }

    private fun buildLiveRequest(rawUrl: String, channel: LiveChannelItem): LiveRequest {
        val parsed = splitUrlHeaders(rawUrl)
        val headers = linkedMapOf(
            "User-Agent" to (channel.userAgent?.takeIf { it.isNotBlank() } ?: DEFAULT_LIVE_USER_AGENT),
            "Accept" to LIVE_ACCEPT,
            "Accept-Language" to "zh-CN,zh;q=0.9,en;q=0.8",
            "Connection" to "keep-alive",
            "Icy-MetaData" to "1"
        )
        parsed.headers.forEach { (key, value) ->
            if (key.equals("ua", ignoreCase = true)) {
                headers["User-Agent"] = value
            } else {
                headers[key] = value
            }
        }
        if (headers["Referer"].isNullOrBlank()) {
            refererFromUrl(parsed.url)?.let { headers["Referer"] = it }
        }
        if (headers["Origin"].isNullOrBlank()) {
            originFromUrl(parsed.url)?.let { headers["Origin"] = it }
        }
        return LiveRequest(parsed.url, headers)
    }

    private fun splitUrlHeaders(rawUrl: String): LiveRequest {
        val candidates = listOf("|", "$")
        val split = candidates
            .mapNotNull { separator ->
                val index = rawUrl.indexOf(separator)
                if (index > 0 && index < rawUrl.lastIndex) separator to index else null
            }
            .firstOrNull { (_, index) -> rawUrl.substring(index + 1).contains("=") }
            ?: return LiveRequest(rawUrl.trim(), emptyMap())

        val cleanUrl = rawUrl.substring(0, split.second).trim()
        val headerText = rawUrl.substring(split.second + 1).trim()
        val headers = headerText
            .split("&", ";")
            .mapNotNull { item ->
                val key = item.substringBefore("=", "").trim()
                val value = item.substringAfter("=", "").trim()
                if (key.isBlank() || value.isBlank()) {
                    null
                } else {
                    normalizeHeaderName(key) to decodeHeaderValue(value)
                }
            }
            .toMap()
        return LiveRequest(cleanUrl, headers)
    }

    private fun normalizeHeaderName(key: String): String {
        return when {
            key.equals("ua", ignoreCase = true) -> "User-Agent"
            key.equals("user_agent", ignoreCase = true) -> "User-Agent"
            key.equals("user-agent", ignoreCase = true) -> "User-Agent"
            key.equals("referer", ignoreCase = true) -> "Referer"
            key.equals("referrer", ignoreCase = true) -> "Referer"
            key.equals("origin", ignoreCase = true) -> "Origin"
            else -> key
        }
    }

    private fun decodeHeaderValue(value: String): String {
        return runCatching { URLDecoder.decode(value, Charsets.UTF_8.name()) }.getOrDefault(value)
    }

    private fun refererFromUrl(url: String): String? {
        return runCatching {
            Uri.parse(url).let { uri ->
                val scheme = uri.scheme ?: return null
                val host = uri.host ?: return null
                "$scheme://$host/"
            }
        }.getOrNull()
    }

    private fun originFromUrl(url: String): String? {
        return refererFromUrl(url)?.trimEnd('/')
    }

    override fun onCleared() {
        messageJob?.cancel()
        exoPlayer.stop()
        exoPlayer.release()
        super.onCleared()
    }

    private data class LiveRequest(
        val url: String,
        val headers: Map<String, String>
    )

    private enum class LiveMediaMode {
        Hls,
        Progressive
    }

    companion object {
        private const val TAG = "LiveViewModel"
        private const val DEFAULT_LIVE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 12; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
        private const val LIVE_ACCEPT = "*/*"
    }
}
