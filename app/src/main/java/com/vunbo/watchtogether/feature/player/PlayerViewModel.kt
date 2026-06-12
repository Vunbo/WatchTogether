package com.vunbo.watchtogether.feature.player

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
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.vunbo.watchtogether.app.WatchTogetherApp
import com.vunbo.watchtogether.data.source.ApiConfig
import com.vunbo.watchtogether.data.local.RoomDataManager
import com.vunbo.watchtogether.data.model.VodInfo
import com.vunbo.watchtogether.data.vod.SourceRepository
import com.vunbo.watchtogether.core.util.DefaultConfig
import com.vunbo.watchtogether.core.storage.HawkConfig
import com.vunbo.watchtogether.core.player.PlayerHelper
import com.vunbo.watchtogether.core.storage.PrefsManager
import com.vunbo.watchtogether.data.source.SourceReputationStore
import com.vunbo.watchtogether.feature.player.model.PlayerState
import com.vunbo.watchtogether.feature.player.model.RemoteMediaTarget
import com.vunbo.watchtogether.feature.watchroom.ChatMessage
import com.vunbo.watchtogether.feature.watchroom.MediaSyncState
import com.vunbo.watchtogether.feature.watchroom.RoomState
import com.vunbo.watchtogether.feature.watchroom.WatchTogetherManager
import com.vunbo.watchtogether.feature.watchroom.WatchTogetherNoticeLevel
import com.vunbo.watchtogether.feature.watchroom.WatchTogetherNoticeState
import com.vunbo.watchtogether.feature.watchroom.WatchTogetherUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlayerViewModel : ViewModel() {
    companion object {
        private const val TAG = "PlayerViewModel"
        private const val AUTO_HIDE_DELAY_MS = 3000L
        private const val TEMP_SPEED = 2.0f
        private const val MIN_SKIP_MARKER_GAP_MS = 30_000L
        private const val PLAY_SUCCESS_THRESHOLD_MS = 15_000L
        private const val RECORD_PROGRESS_INTERVAL_MS = 5_000L
        private const val TOGETHER_SYNC_INTERVAL_MS = 10_000L
        private const val TOGETHER_SMALL_DRIFT_MS = 1_500L
        private const val TOGETHER_SEEK_DRIFT_MS = 8_000L
        private const val TOGETHER_MAX_CATCH_UP_SPEED = 2.5f
    }

    private val apiConfig = ApiConfig.get()
    private val repository = SourceRepository(apiConfig)
    private val roomDataManager = RoomDataManager(WatchTogetherApp.instance)
    private val watchTogetherManager = WatchTogetherManager()

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(WatchTogetherApp.instance).build()

    private val _playerState = MutableStateFlow(
        PlayerState(
            speed = PrefsManager.getFloat(HawkConfig.PLAY_SPEED, 1.0f),
            currentPlaybackSpeed = PrefsManager.getFloat(HawkConfig.PLAY_SPEED, 1.0f),
            currentPlayerType = normalizePlayerType(PrefsManager.getInt(HawkConfig.PLAY_TYPE, PlayerHelper.PLAYER_TYPE_EXO)),
            currentScaleType = PrefsManager.getInt(HawkConfig.PLAY_SCALE, PlayerHelper.SCALE_DEFAULT),
            selectedParseLine = PrefsManager.getString(HawkConfig.PARSE_DEFAULT)
        )
    )
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private val _showWatchTogether = MutableStateFlow(false)
    val showWatchTogether: StateFlow<Boolean> = _showWatchTogether.asStateFlow()

    private val _roomState = MutableStateFlow<RoomState?>(null)
    val roomState: StateFlow<RoomState?> = _roomState.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _watchTogetherUiState = MutableStateFlow(WatchTogetherUiState())
    val watchTogetherUiState: StateFlow<WatchTogetherUiState> = _watchTogetherUiState.asStateFlow()

    private val _watchTogetherNoticeState = MutableStateFlow(WatchTogetherNoticeState())
    val watchTogetherNoticeState: StateFlow<WatchTogetherNoticeState> = _watchTogetherNoticeState.asStateFlow()

    private val _remoteNavigationTarget = MutableStateFlow<RemoteMediaTarget?>(null)
    val remoteNavigationTarget: StateFlow<RemoteMediaTarget?> = _remoteNavigationTarget.asStateFlow()

    private var currentUrl: String = ""
    private var currentSourceKey: String = ""
    private var currentVodId: String = ""
    private var currentPlayFlag: String = ""
    private var currentPlayIndex: Int = 0
    private var currentVodInfo: VodInfo? = null
    private var gestureSeekStartPosition: Long = 0L
    private var gestureSeekAccumulatedRatio: Float = 0f
    private var progressJob: Job? = null
    private var autoHideJob: Job? = null
    private var userMessageJob: Job? = null
    private var playRequestJob: Job? = null
    private var activePlayRequestId = 0L
    private var started = false
    private var outroAutoAdvanceConsumed = false
    private var playSuccessRecorded = false
    private var autoNextConsumed = false
    private var lastProgressRecordAt = 0L
    private var allowRecordResumeForRequest = false
    private var pendingStartPosition: Long? = null
    private var applyingRemoteSync = false
    private var pendingRemoteMedia: MediaSyncState? = null
    private var guestLocallyPaused = false
    private var togetherAutoSyncJob: Job? = null
    private var manualHostSyncPending = false
    private var pendingHostStateRequestReason: HostStateRequestReason? = null
    private var enteringPictureInPicture = false
    private var inPictureInPicture = false

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val state = _playerState.value
                _playerState.value = state.copy(isPlaying = isPlaying)
                if (isPlaying) {
                    scheduleAutoHide()
                } else {
                    savePlaybackRecord()
                }
                if (!applyingRemoteSync) {
                    syncToRoom(if (isPlaying) "play" else "pause")
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val duration = exoPlayer.duration.coerceAtLeast(0L)
                refreshOutroPositionForDuration(duration)
                _playerState.value = _playerState.value.copy(
                    isLoading = playbackState == Player.STATE_BUFFERING,
                    duration = duration
                )
                if (playbackState == Player.STATE_READY) {
                    applyPendingStartPosition()
                    scheduleAutoHide()
                } else if (playbackState == Player.STATE_ENDED) {
                    savePlaybackRecord()
                    maybePlayNextAfterEnded()
                }
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                _playerState.value = _playerState.value.copy(
                    currentPlaybackSpeed = playbackParameters.speed
                )
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "Playback failed: $currentUrl", error)
                recordPlayFailure()
                cancelAutoHide()
                _playerState.value = _playerState.value.copy(
                    isLoading = false,
                    isPlaying = false,
                    controlsVisible = true,
                    error = error.message ?: "播放失败"
                )
            }
        })
    }

    fun startPlay(sourceKey: String, vodId: String, playFlag: String, playIndex: Int) {
        refreshPlaybackOptions()
        if (
            started &&
            currentSourceKey == sourceKey &&
            currentVodId == vodId &&
            currentPlayFlag == playFlag &&
            currentPlayIndex == playIndex
        ) {
            showControls()
            return
        }

        val requestId = beginPlayRequest()
        started = true
        currentSourceKey = sourceKey
        currentVodId = vodId
        currentPlayFlag = playFlag
        currentPlayIndex = playIndex
        currentVodInfo = null
        outroAutoAdvanceConsumed = false
        autoNextConsumed = false
        playSuccessRecorded = false
        lastProgressRecordAt = 0L
        allowRecordResumeForRequest = true
        val remoteStartPosition = pendingRemoteMedia
            ?.takeIf {
                it.sourceKey == sourceKey &&
                    it.vodId == vodId &&
                    it.playFlag == playFlag &&
                    it.playIndex == playIndex
            }
            ?.let(::calculateRemoteTargetPosition)
        _playerState.value = _playerState.value.copy(
            currentSourceKey = sourceKey,
            currentVodId = vodId,
            hasActiveMedia = true,
            isFavorite = roomDataManager.isVodCollect(sourceKey, vodId)
        )
        loadSkipMarkers()

        playRequestJob = viewModelScope.launch {
            resolveAndPlay(requestId = requestId, initialPosition = remoteStartPosition)
        }
    }

    fun togglePlay() {
        val guest = _roomState.value?.isHost == false
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
            if (guest) guestLocallyPaused = true
        } else {
            exoPlayer.play()
            if (guest) guestLocallyPaused = false
        }
        showControls()
    }

    fun replayPlayback() {
        if (!canHostControlPlayback()) return
        exoPlayer.seekTo(0L)
        exoPlayer.playWhenReady = true
        _playerState.value = _playerState.value.copy(error = null)
        showControls()
        syncToRoom("seek")
    }

    fun refreshPlayback() {
        if (!canHostControlPlayback()) return
        val resumePosition = maxOf(
            exoPlayer.currentPosition.coerceAtLeast(0L),
            _playerState.value.currentPosition.coerceAtLeast(0L)
        )
        val requestId = beginPlayRequest(stopCurrentMedia = false)
        playRequestJob = viewModelScope.launch {
            resolveAndPlay(requestId = requestId, initialPosition = resumePosition)
        }
        showControls()
    }

    fun seekTo(positionMs: Long) {
        if (!canHostControlPlayback()) return
        val duration = exoPlayer.duration.takeIf { it > 0 } ?: _playerState.value.duration
        val target = clampSeekTarget(positionMs, duration)
        exoPlayer.seekTo(target)
        _playerState.value = _playerState.value.copy(
            currentPosition = target,
            error = null
        )
        showControls()
        syncToRoom("seek")
    }

    fun setPlaybackSpeed(speed: Float) {
        if (!canHostControlPlayback()) return
        PrefsManager.putFloat(HawkConfig.PLAY_SPEED, speed)
        applyBaseSpeed(speed)
        showControls()
        syncToRoom("speed")
    }

    private fun normalizePlayerType(type: Int): Int {
        if (type != PlayerHelper.PLAYER_TYPE_EXO) {
            PrefsManager.putInt(HawkConfig.PLAY_TYPE, PlayerHelper.PLAYER_TYPE_EXO)
        }
        return PlayerHelper.PLAYER_TYPE_EXO
    }

    fun setScaleType(scaleType: Int) {
        PrefsManager.putInt(HawkConfig.PLAY_SCALE, scaleType)
        _playerState.value = _playerState.value.copy(currentScaleType = scaleType)
        showControls()
    }

    fun selectParseLine(name: String) {
        val parserName = name.takeIf { it != PARSE_AUTO_LABEL }.orEmpty()
        PrefsManager.putString(HawkConfig.PARSE_DEFAULT, parserName)
        _playerState.value = _playerState.value.copy(
            selectedParseLine = parserName,
            error = null
        )
        refreshPlayback()
    }

    fun toggleFavorite() {
        val info = currentVodInfo ?: return
        val nextFavorite = !_playerState.value.isFavorite
        if (nextFavorite) {
            roomDataManager.insertVodCollect(currentSourceKey, info)
        } else {
            roomDataManager.deleteVodCollect(currentSourceKey, info)
        }
        _playerState.value = _playerState.value.copy(
            isFavorite = nextFavorite,
            error = null
        )
        showUserMessage(if (nextFavorite) "已加入收藏" else "已取消收藏")
        showControls()
    }

    fun markIntroPosition() {
        if (currentSourceKey.isBlank() || currentVodId.isBlank()) return
        val position = exoPlayer.currentPosition.coerceAtLeast(0L)
        val outroPosition = _playerState.value.skipOutroPosition
        if (outroPosition > 0L && position >= outroPosition - MIN_SKIP_MARKER_GAP_MS) {
            _playerState.value = _playerState.value.copy(
                error = "片头必须早于片尾，且至少相隔30秒"
            )
            showControls()
            return
        }
        PrefsManager.putLong(skipIntroKey(), position)
        _playerState.value = _playerState.value.copy(
            skipIntroPosition = position,
            error = null
        )
        showControls()
    }

    fun resetIntroPosition() {
        if (currentSourceKey.isBlank() || currentVodId.isBlank()) return
        PrefsManager.remove(skipIntroKey())
        _playerState.value = _playerState.value.copy(
            skipIntroPosition = 0L,
            error = null
        )
        showUserMessage("已重置片头")
        showControls()
    }

    fun markOutroPosition() {
        if (currentSourceKey.isBlank() || currentVodId.isBlank()) return
        val position = exoPlayer.currentPosition.coerceAtLeast(0L)
        val duration = exoPlayer.duration.takeIf { it > 0 } ?: _playerState.value.duration
        if (duration <= 0L) {
            _playerState.value = _playerState.value.copy(
                error = "当前视频时长未获取，暂不能设置片尾"
            )
            showControls()
            return
        }
        val introPosition = _playerState.value.skipIntroPosition
        if (position < duration / 2L) {
            _playerState.value = _playerState.value.copy(
                error = "片尾只能在视频播放到50%以后设置"
            )
            showControls()
            return
        }
        if (position <= introPosition + MIN_SKIP_MARKER_GAP_MS) {
            _playerState.value = _playerState.value.copy(
                error = "片尾必须晚于片头，且至少相隔30秒"
            )
            showControls()
            return
        }
        val outroOffset = (duration - position).coerceAtLeast(0L)
        PrefsManager.putLong(skipOutroOffsetKey(), outroOffset)
        PrefsManager.remove(skipOutroKey())
        _playerState.value = _playerState.value.copy(
            skipOutroPosition = position,
            skipOutroOffset = outroOffset,
            error = null
        )
        outroAutoAdvanceConsumed = false
        showControls()
    }

    fun resetOutroPosition() {
        if (currentSourceKey.isBlank() || currentVodId.isBlank()) return
        PrefsManager.remove(skipOutroKey())
        PrefsManager.remove(skipOutroOffsetKey())
        _playerState.value = _playerState.value.copy(
            skipOutroPosition = 0L,
            skipOutroOffset = 0L,
            error = null
        )
        outroAutoAdvanceConsumed = false
        showUserMessage("已重置片尾")
        showControls()
    }

    fun showControls() {
        _playerState.value = _playerState.value.copy(controlsVisible = true)
        scheduleAutoHide()
    }

    fun toggleControls() {
        val current = _playerState.value
        if (current.controlsVisible) {
            cancelAutoHide()
            _playerState.value = current.copy(
                controlsVisible = false,
                settingsPanelVisible = false
            )
        } else {
            showControls()
        }
    }

    fun toggleControlsLock() {
        val locked = !_playerState.value.controlsLocked
        _playerState.value = _playerState.value.copy(
            controlsLocked = locked,
            controlsVisible = true,
            settingsPanelVisible = false
        )
        if (locked) {
            cancelAutoHide()
        } else {
            scheduleAutoHide()
        }
    }

    fun toggleSettingsPanel() {
        refreshPlaybackOptions()
        val visible = !_playerState.value.settingsPanelVisible
        _playerState.value = _playerState.value.copy(
            controlsVisible = true,
            settingsPanelVisible = visible
        )
        if (visible) {
            cancelAutoHide()
        } else {
            scheduleAutoHide()
        }
    }

    fun dismissSettingsPanel() {
        if (!_playerState.value.settingsPanelVisible) return
        _playerState.value = _playerState.value.copy(settingsPanelVisible = false)
        scheduleAutoHide()
    }

    fun showUserMessage(message: String) {
        userMessageJob?.cancel()
        _playerState.value = _playerState.value.copy(
            controlsVisible = true,
            userMessage = message,
            userMessageTimestamp = System.currentTimeMillis()
        )
        userMessageJob = viewModelScope.launch {
            delay(1800L)
            val current = _playerState.value
            if (current.userMessage == message) {
                _playerState.value = current.copy(userMessage = null)
            }
        }
        scheduleAutoHide()
    }

    fun openEpisodeSheet() {
        _playerState.value = _playerState.value.copy(
            controlsVisible = true,
            episodeSheetVisible = true,
            settingsPanelVisible = false
        )
        cancelAutoHide()
    }

    fun closeEpisodeSheet() {
        _playerState.value = _playerState.value.copy(episodeSheetVisible = false)
        scheduleAutoHide()
    }

    fun playEpisode(index: Int) {
        if (!canHostControlPlayback()) return
        val episodes = _playerState.value.episodes
        if (episodes.isEmpty()) return
        val targetIndex = index.coerceIn(0, episodes.lastIndex)
        val targetEpisode = episodes[targetIndex]
        savePlaybackRecord()
        val requestId = beginPlayRequest()
        outroAutoAdvanceConsumed = false
        autoNextConsumed = false
        playSuccessRecorded = false
        lastProgressRecordAt = 0L
        allowRecordResumeForRequest = false
        currentPlayIndex = targetIndex
        _playerState.value = _playerState.value.copy(
            currentEpisodeIndex = targetIndex,
            currentEpisodeName = targetEpisode.name.ifBlank { "第${targetIndex + 1}集" },
            currentFlag = currentPlayFlag,
            currentPosition = 0L,
            duration = 0L,
            resolvedUrl = "",
            isPlaying = false,
            isLoading = true,
            error = null,
            hasActiveMedia = true
        )
        closeEpisodeSheet()
        playRequestJob = viewModelScope.launch {
            resolveAndPlay(requestId = requestId, initialPosition = null)
        }
    }

    fun playPreviousEpisode() {
        val previousIndex = (_playerState.value.currentEpisodeIndex - 1).coerceAtLeast(0)
        if (previousIndex == _playerState.value.currentEpisodeIndex) return
        playEpisode(previousIndex)
    }

    fun playNextEpisode() {
        val nextIndex = (_playerState.value.currentEpisodeIndex + 1)
            .coerceAtMost(_playerState.value.episodes.lastIndex)
        if (nextIndex == _playerState.value.currentEpisodeIndex) return
        playEpisode(nextIndex)
    }

    fun beginGestureSeek() {
        if (!canHostControlPlayback()) return
        if (_playerState.value.controlsLocked) return
        gestureSeekStartPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
        gestureSeekAccumulatedRatio = 0f
        _playerState.value = _playerState.value.copy(
            gestureSeekPosition = gestureSeekStartPosition,
            gestureSeekOffsetMs = 0L
        )
        cancelAutoHide()
    }

    fun updateGestureSeek(progressRatio: Float) {
        if (!canHostControlPlayback()) return
        if (_playerState.value.controlsLocked) return
        gestureSeekAccumulatedRatio = (gestureSeekAccumulatedRatio + progressRatio).coerceIn(-1f, 1f)
        val duration = exoPlayer.duration.takeIf { it > 0 } ?: _playerState.value.duration
        val window = if (duration > 0L) {
            (duration / 4).coerceIn(60_000L, 600_000L)
        } else {
            180_000L
        }
        val offset = (window * gestureSeekAccumulatedRatio).toLong().coerceIn(-600_000L, 600_000L)
        val maxPosition = duration.takeIf { it > 0L } ?: Long.MAX_VALUE
        val target = clampSeekTarget(gestureSeekStartPosition + offset, maxPosition)
        _playerState.value = _playerState.value.copy(
            gestureSeekPosition = target,
            gestureSeekOffsetMs = offset
        )
    }

    fun commitGestureSeek() {
        if (!canHostControlPlayback()) return
        if (_playerState.value.controlsLocked) return
        val target = _playerState.value.gestureSeekPosition ?: return
        exoPlayer.seekTo(target)
        gestureSeekAccumulatedRatio = 0f
        _playerState.value = _playerState.value.copy(
            currentPosition = target,
            gestureSeekPosition = null,
            gestureSeekOffsetMs = 0L,
            error = null
        )
        if (_playerState.value.controlsVisible) {
            scheduleAutoHide()
        }
        savePlaybackRecord(target)
        syncToRoom("seek")
    }

    fun cancelGestureSeek() {
        gestureSeekAccumulatedRatio = 0f
        _playerState.value = _playerState.value.copy(
            gestureSeekPosition = null,
            gestureSeekOffsetMs = 0L
        )
        if (_playerState.value.controlsVisible) {
            scheduleAutoHide()
        }
    }

    fun startTemporarySpeed(speed: Float = TEMP_SPEED) {
        if (!canHostControlPlayback()) return
        if (_playerState.value.temporarySpeedActive) return
        exoPlayer.playbackParameters = PlaybackParameters(speed)
        _playerState.value = _playerState.value.copy(
            temporarySpeedActive = true,
            temporarySpeed = speed,
            currentPlaybackSpeed = speed
        )
        cancelAutoHide()
        syncToRoom("speed_boost_start")
    }

    fun stopTemporarySpeed() {
        val state = _playerState.value
        if (!state.temporarySpeedActive) return
        exoPlayer.playbackParameters = PlaybackParameters(state.speed)
        _playerState.value = state.copy(
            temporarySpeedActive = false,
            currentPlaybackSpeed = state.speed
        )
        scheduleAutoHide()
        syncToRoom("speed_boost_end")
    }

    fun stopPlayback() {
        invalidatePlayRequests()
        savePlaybackRecord()
        progressJob?.cancel()
        progressJob = null
        userMessageJob?.cancel()
        userMessageJob = null
        cancelAutoHide()
        exoPlayer.stop()
        started = false
        outroAutoAdvanceConsumed = false
        playSuccessRecorded = false
        _playerState.value = _playerState.value.copy(
            isPlaying = false,
            isLoading = false,
            hasActiveMedia = false,
            error = null,
            userMessage = null,
            settingsPanelVisible = false,
            controlsLocked = false,
            gestureSeekPosition = null,
            gestureSeekOffsetMs = 0L,
            temporarySpeedActive = false
        )
    }

    fun stopPlaybackAfterLeavingPage() {
        if (enteringPictureInPicture || inPictureInPicture) return
        stopPlayback()
    }

    fun hasActiveTogetherRoom(): Boolean = _roomState.value != null

    fun isTogetherHost(): Boolean = _roomState.value?.isHost == true

    fun beginPictureInPictureRequest() {
        enteringPictureInPicture = true
    }

    fun finishPictureInPictureRequest(entered: Boolean) {
        enteringPictureInPicture = false
        inPictureInPicture = entered
    }

    fun setPictureInPictureMode(active: Boolean) {
        enteringPictureInPicture = false
        inPictureInPicture = active
    }

    private fun clampSeekTarget(positionMs: Long, maxPosition: Long): Long {
        val state = _playerState.value
        val hardMax = maxPosition.coerceAtLeast(0L)
        var minAllowed = 0L
        var maxAllowed = hardMax

        if (state.skipIntroPosition > 0L) {
            minAllowed = state.skipIntroPosition
        }
        val outroPosition = resolveOutroPosition(hardMax, state.skipOutroOffset)
        if (outroPosition > 0L) {
            maxAllowed = minOf(maxAllowed, outroPosition)
        }
        if (maxAllowed < minAllowed) {
            maxAllowed = minAllowed
        }
        return positionMs.coerceIn(minAllowed, maxAllowed)
    }

    private fun clampStartPosition(positionMs: Long, duration: Long): Long {
        if (duration <= 0L) return positionMs.coerceAtLeast(0L)
        return clampSeekTarget(positionMs, duration)
    }

    private fun beginPlayRequest(stopCurrentMedia: Boolean = true): Long {
        playRequestJob?.cancel()
        activePlayRequestId += 1
        pendingStartPosition = null
        progressJob?.cancel()
        progressJob = null
        if (stopCurrentMedia) {
            exoPlayer.stop()
            currentUrl = ""
        }
        _playerState.value = _playerState.value.copy(
            isLoading = true,
            isPlaying = false,
            error = null,
            userMessage = null,
            controlsVisible = true
        )
        return activePlayRequestId
    }

    private fun invalidatePlayRequests() {
        activePlayRequestId += 1
        playRequestJob?.cancel()
        playRequestJob = null
        pendingStartPosition = null
    }

    private fun isActivePlayRequest(requestId: Long): Boolean {
        return requestId == activePlayRequestId && started
    }

    private suspend fun resolveAndPlay(requestId: Long, initialPosition: Long?) {
        if (!isActivePlayRequest(requestId)) return
        outroAutoAdvanceConsumed = false
        autoNextConsumed = false
        _playerState.value = _playerState.value.copy(
            isLoading = true,
            error = null,
            controlsVisible = true
        )

        val episode = findEpisode() ?: run {
            if (!isActivePlayRequest(requestId)) return
            _playerState.value = _playerState.value.copy(
                isLoading = false,
                isPlaying = false,
                error = "无法获取播放地址"
            )
            return
        }
        if (!isActivePlayRequest(requestId)) return

        val result = repository.resolvePlay(
            currentSourceKey,
            currentPlayFlag,
            episode.url,
            _playerState.value.selectedParseLine.takeIf { it.isNotBlank() }
        )
        if (!isActivePlayRequest(requestId)) return
        val resolved = resolvePlayableUrl(result, episode.url)
        if (resolved.isBlank() || !isPlayableUrl(resolved)) {
            if (!isActivePlayRequest(requestId)) return
            recordPlayFailure()
            val errorMessage = result?.get("error")?.asString
                ?: result?.get("errMsg")?.asString
                ?: result?.get("msg")?.asString
                ?: "未解析到可播放地址"
            _playerState.value = _playerState.value.copy(
                isLoading = false,
                isPlaying = false,
                error = errorMessage
            )
            return
        }

        currentUrl = resolved
        Log.d(
            TAG,
            "prepare playback source=$currentSourceKey flag=$currentPlayFlag index=$currentPlayIndex " +
                "hls=${resolved.lowercase().contains(".m3u8")} url=${maskUrl(resolved)}"
        )
        _playerState.value = _playerState.value.copy(
            activeParseLine = result?.let { DefaultConfig.safeJsonString(it, "jxFrom") }.orEmpty()
        )
        if (roomState.value?.isHost == true && !applyingRemoteSync) {
            syncToRoom("media_change", urlOverride = resolved)
        }
        if (!isActivePlayRequest(requestId)) return
        val startPosition = initialPosition
            ?: episode.recordStartPosition.takeIf { allowRecordResumeForRequest && it > 0L }
        allowRecordResumeForRequest = false
        preparePlayer(requestId, resolved, parseHeaders(result), startPosition)
    }

    private suspend fun findEpisode(): EpisodePlayInfo? {
        val info = ensureVodInfo() ?: return null
        val episodes = info.seriesMap[currentPlayFlag]
            ?: info.seriesMap.values.firstOrNull()
            ?: return null
        if (episodes.isEmpty()) return null

        currentPlayIndex = currentPlayIndex.coerceIn(0, episodes.lastIndex)
        val recordStartPosition = if (
            info.playFlag == currentPlayFlag &&
            info.playIndex == currentPlayIndex
        ) {
            info.playPosition
        } else {
            0L
        }
        val episode = episodes[currentPlayIndex]
        info.playFlag = currentPlayFlag
        info.playIndex = currentPlayIndex
        info.playUpdateTime = System.currentTimeMillis()
        currentVodInfo = info
        _playerState.value = _playerState.value.copy(
            title = info.name.orEmpty().ifBlank { "正在播放" },
            currentEpisodeName = episode.name.ifBlank { "第${currentPlayIndex + 1}集" },
            currentFlag = currentPlayFlag,
            episodes = episodes,
            currentEpisodeIndex = currentPlayIndex,
            currentSourceKey = currentSourceKey,
            currentVodId = currentVodId,
            isFavorite = roomDataManager.isVodCollect(currentSourceKey, currentVodId),
            hasActiveMedia = true
        )
        loadSkipMarkers()
        return EpisodePlayInfo(episode.name, episode.url, recordStartPosition)
    }

    private suspend fun ensureVodInfo(): VodInfo? {
        currentVodInfo?.let { info ->
            if (info.seriesMap.isNotEmpty()) {
                currentPlayFlag = resolvePlayFlag(info)
                return info
            }
        }

        var info = roomDataManager.getVodInfo(currentSourceKey, currentVodId)
        if (info?.seriesMap?.isEmpty() != false) {
            repository.getDetailOnce(currentSourceKey, currentVodId)
                ?.movie
                ?.videoList
                ?.firstOrNull()
                ?.let { video ->
                    info = VodInfo().apply {
                        setVideo(video)
                        sourceKey = currentSourceKey
                    }
                }
        }

        if (info == null) {
            SourceReputationStore.recordDetailFailure(currentSourceKey)
            return null
        }

        currentPlayFlag = resolvePlayFlag(info)
        currentVodInfo = info
        return info
    }

    private fun resolvePlayFlag(info: VodInfo): String {
        if (currentPlayFlag.isNotBlank() && info.seriesMap.containsKey(currentPlayFlag)) {
            return currentPlayFlag
        }
        val recordFlag = info.playFlag
        return when {
            !recordFlag.isNullOrBlank() && info.seriesMap.containsKey(recordFlag) -> recordFlag
            info.seriesFlags.isNotEmpty() -> info.seriesFlags.first().name
            else -> info.seriesMap.keys.firstOrNull().orEmpty()
        }
    }

    private fun resolvePlayableUrl(result: JsonObject?, fallbackUrl: String): String {
        if (result == null) return fallbackUrl

        val parse = result.get("parse")?.asInt ?: 0
        val url = result.get("url")?.asString.orEmpty()
        if (parse == 0 && url.isNotBlank()) {
            return url
        }

        val playUrl = result.get("playUrl")?.asString.orEmpty()
        if (playUrl.isNotBlank() && url.isNotBlank()) {
            return playUrl + url
        }

        return url
    }

    private fun isPlayableUrl(url: String): Boolean {
        if (url.isBlank()) return false
        val lower = url.lowercase()
        if (
            lower.contains("iqiyi.com/") ||
            lower.contains("v.qq.com/") ||
            lower.contains("youku.com/") ||
            lower.contains("mgtv.com/") ||
            lower.contains("bilibili.com/video") ||
            lower.contains("sohu.com/")
        ) {
            return false
        }
        return DefaultConfig.isVideoFormat(url)
    }

    private fun parseHeaders(result: JsonObject?): Map<String, String> {
        if (result == null) return emptyMap()
        val headers = mutableMapOf<String, String>()
        headers.putAll(parseHeaderElement(result.get("header")))
        headers.putAll(parseHeaderElement(result.get("headers")))
        result.get("user-agent")?.asStringOrNull()?.takeIf { it.isNotBlank() }?.let {
            headers["User-Agent"] = it.trim()
        }
        result.get("referer")?.asStringOrNull()?.takeIf { it.isNotBlank() }?.let {
            headers["Referer"] = it.trim()
        }
        return headers
    }

    private fun parseHeaderElement(element: JsonElement?): Map<String, String> {
        if (element == null || element.isJsonNull) return emptyMap()
        return when {
            element.isJsonObject -> element.asJsonObject.entrySet().mapNotNull { (key, value) ->
                value.asStringOrNull()?.takeIf { it.isNotBlank() }?.let { key to it.trim() }
            }.toMap()
            element.isJsonPrimitive && element.asJsonPrimitive.isString -> parseHeaderString(element.asString)
            else -> emptyMap()
        }
    }

    private fun parseHeaderString(raw: String): Map<String, String> {
        val text = raw.trim()
        if (text.isBlank()) return emptyMap()
        if (text.startsWith("{") && text.endsWith("}")) {
            return runCatching {
                parseHeaderElement(com.google.gson.JsonParser.parseString(text))
            }.getOrDefault(emptyMap())
        }
        return text.split("&", "\n", ";")
            .mapNotNull { item ->
                val index = item.indexOf('=').takeIf { it > 0 } ?: item.indexOf(':').takeIf { it > 0 }
                if (index == null) {
                    null
                } else {
                    val key = item.substring(0, index).trim()
                    val value = item.substring(index + 1).trim()
                    if (key.isNotBlank() && value.isNotBlank()) key to value else null
                }
            }
            .toMap()
    }

    private fun JsonElement.asStringOrNull(): String? {
        return runCatching {
            if (isJsonPrimitive) asString else null
        }.getOrNull()
    }

    private fun applyPendingStartPosition() {
        val target = pendingStartPosition ?: return
        if (target <= 0L) {
            pendingStartPosition = null
            return
        }
        val current = exoPlayer.currentPosition.coerceAtLeast(0L)
        if (kotlin.math.abs(current - target) > 1_500L) {
            val duration = exoPlayer.duration.takeIf { it > 0L } ?: _playerState.value.duration
            val safeTarget = clampStartPosition(target, duration)
            exoPlayer.seekTo(safeTarget)
            _playerState.value = _playerState.value.copy(currentPosition = safeTarget)
            if (!applyingRemoteSync) {
                syncToRoom("seek")
            }
        }
        pendingStartPosition = null
    }

    private fun applyBaseSpeed(speed: Float) {
        val actualSpeed = if (_playerState.value.temporarySpeedActive) {
            _playerState.value.temporarySpeed
        } else {
            speed
        }
        exoPlayer.playbackParameters = PlaybackParameters(actualSpeed)
        _playerState.value = _playerState.value.copy(
            speed = speed,
            currentPlaybackSpeed = actualSpeed
        )
    }

    private fun loadSkipMarkers() {
        if (currentSourceKey.isBlank() || currentVodId.isBlank()) {
            _playerState.value = _playerState.value.copy(
                skipIntroPosition = 0L,
                skipOutroPosition = 0L,
                skipOutroOffset = 0L
            )
            return
        }
        val duration = exoPlayer.duration.takeIf { it > 0L } ?: _playerState.value.duration
        val outroOffset = loadOutroOffset(duration)
        _playerState.value = _playerState.value.copy(
            skipIntroPosition = PrefsManager.getLong(skipIntroKey(), 0L),
            skipOutroPosition = resolveOutroPosition(duration, outroOffset),
            skipOutroOffset = outroOffset
        )
    }

    private fun refreshOutroPositionForDuration(duration: Long) {
        if (duration <= 0L || currentSourceKey.isBlank() || currentVodId.isBlank()) return
        val offset = loadOutroOffset(duration)
        val position = resolveOutroPosition(duration, offset)
        val state = _playerState.value
        if (state.skipOutroOffset != offset || state.skipOutroPosition != position) {
            _playerState.value = state.copy(
                skipOutroOffset = offset,
                skipOutroPosition = position
            )
        }
    }

    private fun refreshPlaybackOptions() {
        val parsers = apiConfig.parseBeanList
            .filter { it.url.isNotBlank() && it.url != "Web" && it.url != "Demo" }
            .map { it.name.ifBlank { it.url } }
            .distinct()
        _playerState.value = _playerState.value.copy(
            selectedParseLine = PrefsManager.getString(HawkConfig.PARSE_DEFAULT),
            parseLineOptions = parsers
        )
    }

    private fun skipIntroKey(): String = "skip_intro_${currentSourceKey}_${currentVodId}"

    private fun skipOutroKey(): String = "skip_outro_${currentSourceKey}_${currentVodId}"

    private fun skipOutroOffsetKey(): String = "skip_outro_offset_${currentSourceKey}_${currentVodId}"

    private fun loadOutroOffset(duration: Long): Long {
        val savedOffset = PrefsManager.getLong(skipOutroOffsetKey(), 0L)
        if (savedOffset > 0L) return savedOffset

        val legacyPosition = PrefsManager.getLong(skipOutroKey(), 0L)
        if (legacyPosition <= 0L || duration <= 0L || legacyPosition >= duration) return 0L

        val migratedOffset = (duration - legacyPosition).coerceAtLeast(0L)
        if (migratedOffset > 0L) {
            PrefsManager.putLong(skipOutroOffsetKey(), migratedOffset)
            PrefsManager.remove(skipOutroKey())
        }
        return migratedOffset
    }

    private fun resolveOutroPosition(duration: Long, offset: Long): Long {
        if (duration <= 0L || offset <= 0L || offset >= duration) return 0L
        return (duration - offset).coerceAtLeast(0L)
    }

    private fun maskUrl(url: String): String {
        return if (url.length > 220) url.take(180) + "..." else url
    }

    @OptIn(UnstableApi::class)
    private fun preparePlayer(requestId: Long, url: String, headers: Map<String, String>, initialPosition: Long?) {
        if (!isActivePlayRequest(requestId)) return
        try {
            val dataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(headers["User-Agent"] ?: headers["user-agent"] ?: "okhttp/3.15")
                .setAllowCrossProtocolRedirects(true)
                .setDefaultRequestProperties(headers)
            val mediaItem = MediaItem.fromUri(Uri.parse(url))
            val mediaSource = if (url.lowercase().contains(".m3u8")) {
                HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
            } else {
                ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
            }
            if (!isActivePlayRequest(requestId)) return
            exoPlayer.setMediaSource(mediaSource)
            val durationHint = exoPlayer.duration.takeIf { it > 0L } ?: _playerState.value.duration
            val startPosition = when {
                initialPosition != null && initialPosition > 0L -> clampStartPosition(initialPosition, durationHint)
                _playerState.value.skipIntroPosition > 0L -> clampStartPosition(_playerState.value.skipIntroPosition, durationHint)
                else -> null
            }
            pendingStartPosition = startPosition
            when {
                startPosition != null -> exoPlayer.seekTo(startPosition)
            }
            val remoteMedia = pendingRemoteMedia?.takeIf { it.url == url }
            if (!isActivePlayRequest(requestId)) return
            exoPlayer.prepare()
            exoPlayer.playWhenReady = remoteMedia?.isPlaying ?: true
            _playerState.value = _playerState.value.copy(
                isLoading = true,
                error = null,
                resolvedUrl = url,
                currentPlaybackSpeed = _playerState.value.speed,
                hasActiveMedia = true
            )
            applyBaseSpeed(_playerState.value.speed)
            remoteMedia?.let { remote ->
                pendingStartPosition = calculateRemoteTargetPosition(remote)
                applyBaseSpeed(remote.speed)
                remote.temporarySpeed?.let { temp ->
                    exoPlayer.playbackParameters = PlaybackParameters(temp)
                    _playerState.value = _playerState.value.copy(
                        temporarySpeedActive = true,
                        temporarySpeed = temp,
                        currentPlaybackSpeed = temp
                    )
                }
            }
            startProgressUpdates(requestId)
        } catch (e: Exception) {
            if (!isActivePlayRequest(requestId)) return
            recordPlayFailure()
            Log.e(
                TAG,
                "Player init failed source=$currentSourceKey flag=$currentPlayFlag index=$currentPlayIndex url=${maskUrl(url)}",
                e
            )
            _playerState.value = _playerState.value.copy(
                isLoading = false,
                isPlaying = false,
                error = e.message ?: "播放器初始化失败"
            )
        }
    }

    private fun startProgressUpdates(requestId: Long = activePlayRequestId) {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                if (!isActivePlayRequest(requestId)) return@launch
                val position = exoPlayer.currentPosition.coerceAtLeast(0L)
                val duration = exoPlayer.duration.coerceAtLeast(0L)
                refreshOutroPositionForDuration(duration)
                val state = _playerState.value.copy(
                    currentPosition = position,
                    duration = duration,
                    isPlaying = exoPlayer.isPlaying
                )
                _playerState.value = state
                maybeRecordPlaySuccess(state)
                maybeSavePlaybackRecord(state)
                maybeAdvanceAfterOutro(state)
                delay(500L)
            }
        }
    }

    private fun maybeSavePlaybackRecord(state: PlayerState) {
        if (!state.hasActiveMedia) return
        if (currentSourceKey.isBlank() || currentVodId.isBlank()) return
        val now = System.currentTimeMillis()
        if (now - lastProgressRecordAt < RECORD_PROGRESS_INTERVAL_MS) return
        lastProgressRecordAt = now
        savePlaybackRecord(state.currentPosition, state.duration)
    }

    private fun savePlaybackRecord(
        positionOverride: Long? = null,
        durationOverride: Long? = null
    ) {
        val info = currentVodInfo ?: return
        if (currentSourceKey.isBlank() || currentVodId.isBlank()) return
        val state = _playerState.value
        val position = positionOverride
            ?: exoPlayer.currentPosition.takeIf { it >= 0L }
            ?: state.currentPosition
        val duration = durationOverride
            ?: exoPlayer.duration.takeIf { it > 0L }
            ?: state.duration
        info.playFlag = currentPlayFlag
        info.playIndex = currentPlayIndex
        info.playPosition = position.coerceAtLeast(0L)
        info.playDuration = duration.coerceAtLeast(0L)
        info.playUpdateTime = System.currentTimeMillis()
        val episodeName = state.currentEpisodeName
        val progressText = formatRecordProgress(info.playPosition, info.playDuration)
        info.playNote = listOf(
            episodeName.takeIf { it.isNotBlank() },
            progressText.takeIf { it.isNotBlank() }
        ).filterNotNull().joinToString("  ")
        roomDataManager.insertVodRecord(currentSourceKey, info)
    }

    private fun maybeRecordPlaySuccess(state: PlayerState) {
        if (playSuccessRecorded) return
        if (!state.isPlaying) return
        if (state.currentPosition < PLAY_SUCCESS_THRESHOLD_MS) return
        playSuccessRecorded = true
        SourceReputationStore.recordPlaySuccess(currentSourceKey)
    }

    private fun recordPlayFailure() {
        if (playSuccessRecorded) return
        SourceReputationStore.recordPlayFailure(currentSourceKey)
    }

    private fun maybePlayNextAfterEnded() {
        if (autoNextConsumed || outroAutoAdvanceConsumed) return
        if (!canHostControlPlayback(showMessage = false)) return
        val state = _playerState.value
        if (state.episodes.isEmpty()) return
        if (state.currentEpisodeIndex >= state.episodes.lastIndex) return
        autoNextConsumed = true
        playEpisode(state.currentEpisodeIndex + 1)
    }

    private fun scheduleAutoHide() {
        cancelAutoHide()
        if (
            _playerState.value.episodeSheetVisible ||
            _playerState.value.settingsPanelVisible ||
            _playerState.value.controlsLocked ||
            _showWatchTogether.value
        ) return
        autoHideJob = viewModelScope.launch {
            delay(AUTO_HIDE_DELAY_MS)
            _playerState.value = _playerState.value.copy(controlsVisible = false)
        }
    }

    private fun cancelAutoHide() {
        autoHideJob?.cancel()
        autoHideJob = null
    }

    fun toggleWatchTogether() {
        val visible = !_showWatchTogether.value
        _showWatchTogether.value = visible
        if (visible) {
            clearWatchTogetherNotice()
            _playerState.value = _playerState.value.copy(
                controlsVisible = false,
                settingsPanelVisible = false
            )
            cancelAutoHide()
        } else {
            cancelAutoHide()
        }
    }

    fun dismissWatchTogetherPanel() {
        _showWatchTogether.value = false
        cancelAutoHide()
    }

    fun getDefaultTogetherServerUrl(): String = watchTogetherManager.getDefaultServerUrl()

    fun createRoom(serverUrl: String) {
        viewModelScope.launch {
            if (currentUrl.isBlank()) {
                _watchTogetherUiState.value = WatchTogetherUiState(error = "请先开始播放视频")
                return@launch
            }
            _watchTogetherUiState.value = WatchTogetherUiState(connecting = true)
            watchTogetherManager.createRoom(
                serverUrl = serverUrl,
                mediaState = buildCurrentMediaState("media_change"),
                onState = ::handleRoomState,
                onSyncMessage = { handleRemoteSync(it) },
                onChatMessage = ::appendChatMessage,
                onErrorMessage = ::handleTogetherError
            )
        }
    }

    fun joinRoom(serverUrl: String, code: String) {
        viewModelScope.launch {
            val cleanCode = code.trim()
            if (cleanCode.isBlank()) {
                _watchTogetherUiState.value = WatchTogetherUiState(error = "请输入房间号")
                return@launch
            }
            _watchTogetherUiState.value = WatchTogetherUiState(connecting = true)
            watchTogetherManager.joinRoom(
                serverUrl = serverUrl,
                roomCode = cleanCode,
                onState = ::handleRoomState,
                onSyncMessage = { handleRemoteSync(it) },
                onChatMessage = ::appendChatMessage,
                onErrorMessage = ::handleTogetherError
            )
        }
    }

    fun leaveRoom() {
        watchTogetherManager.leaveRoom()
        _roomState.value = null
        _chatMessages.value = emptyList()
        _showWatchTogether.value = false
        clearWatchTogetherNotice()
        guestLocallyPaused = false
        manualHostSyncPending = false
        pendingHostStateRequestReason = null
        stopTogetherAutoSync()
    }

    fun sendChatMessage(message: String) {
        watchTogetherManager.sendChat(message)
    }

    fun clearTogetherError() {
        _watchTogetherUiState.value = _watchTogetherUiState.value.copy(error = null)
    }

    private fun syncToRoom(action: String, urlOverride: String? = null) {
        if (_roomState.value?.isHost != true) return
        val state = _playerState.value
        watchTogetherManager.syncPlayback(buildCurrentMediaState(action, state, urlOverride))
    }

    private fun buildCurrentMediaState(
        action: String,
        state: PlayerState = _playerState.value,
        urlOverride: String? = null
    ): MediaSyncState {
        return MediaSyncState(
            url = urlOverride ?: currentUrl,
            videoTitle = state.title,
            sourceKey = currentSourceKey,
            vodId = currentVodId,
            playFlag = currentPlayFlag,
            playIndex = currentPlayIndex,
            episodeName = state.currentEpisodeName,
            position = exoPlayer.currentPosition.coerceAtLeast(0L),
            isPlaying = exoPlayer.isPlaying,
            speed = state.speed,
            temporarySpeed = state.temporarySpeed.takeIf { state.temporarySpeedActive },
            action = action
        )
    }

    fun requestHostSync() {
        if (_roomState.value?.isHost == true) return
        manualHostSyncPending = true
        pendingHostStateRequestReason = HostStateRequestReason.ManualSync
        guestLocallyPaused = false
        showUserMessage("正在同步")
        watchTogetherManager.requestRoomState()
    }

    fun consumeRemoteNavigationTarget(target: RemoteMediaTarget) {
        if (_remoteNavigationTarget.value == target) {
            _remoteNavigationTarget.value = null
        }
    }

    private fun startTogetherAutoSync() {
        if (togetherAutoSyncJob?.isActive == true) return
        togetherAutoSyncJob = viewModelScope.launch {
            while (isActive) {
                delay(TOGETHER_SYNC_INTERVAL_MS)
                if (_roomState.value?.isHost == false && !guestLocallyPaused) {
                    if (pendingHostStateRequestReason == null) {
                        pendingHostStateRequestReason = HostStateRequestReason.AutoSync
                    }
                    watchTogetherManager.requestRoomState()
                }
            }
        }
    }

    private fun stopTogetherAutoSync() {
        togetherAutoSyncJob?.cancel()
        togetherAutoSyncJob = null
    }

    private fun handleRoomState(state: RoomState) {
        val previous = _roomState.value
        val requestReason = pendingHostStateRequestReason
        val merged = state.copy(
            mediaState = state.mediaState ?: previous?.mediaState,
            members = if (state.members.isEmpty()) previous?.members.orEmpty() else state.members
        )
        _roomState.value = merged
        _watchTogetherUiState.value = WatchTogetherUiState(connecting = false)
        recordRoomNotice(previous, merged)
        if (merged.isHost) {
            stopTogetherAutoSync()
        } else {
            startTogetherAutoSync()
        }
        if (previous == null) {
            appendChatMessage(
                ChatMessage(
                    userId = "system",
                    userName = "系统",
                    message = if (merged.isHost) "房间已创建，邀请好友输入房间号加入" else "已加入房间，正在同步房主播放",
                    isSystem = true
                )
            )
        }
        if (!merged.isHost && !merged.hasFreshMediaState) {
            if (requestReason == HostStateRequestReason.ManualSync) {
                showUserMessage("房主暂无播放状态")
                manualHostSyncPending = false
            }
            pendingHostStateRequestReason = null
            return
        }
        if (!merged.isHost) {
            pendingHostStateRequestReason = null
            val forceReload = manualHostSyncPending && requestReason == HostStateRequestReason.ManualSync
            if (forceReload) {
                manualHostSyncPending = false
            }
            merged.mediaState?.let { mediaState ->
                handleRemoteSync(
                    mediaState = mediaState,
                    forceReloadIfNeeded = forceReload,
                    respectControlAction = false,
                    showSyncResult = requestReason == HostStateRequestReason.ManualSync
                )
            }
        }
    }

    private fun handleRemoteSync(
        mediaState: MediaSyncState,
        forceReloadIfNeeded: Boolean = false,
        respectControlAction: Boolean = true,
        showSyncResult: Boolean = false
    ) {
        if (_roomState.value?.isHost == true) return
        if (shouldNavigateToRemoteMedia(mediaState)) {
            pendingRemoteMedia = mediaState
            _remoteNavigationTarget.value = RemoteMediaTarget(
                sourceKey = mediaState.sourceKey,
                vodId = mediaState.vodId,
                playFlag = mediaState.playFlag,
                playIndex = mediaState.playIndex
            )
            if (showSyncResult) {
                showUserMessage("正在打开房主视频")
            }
            return
        }
        if (forceReloadIfNeeded && shouldReloadForRemoteSync(mediaState)) {
            reloadRemotePlayback(mediaState, showSyncResult = showSyncResult)
            return
        }
        if (mediaState.url.isNotBlank() && mediaState.url != currentUrl) {
            applyRemoteMediaUrl(mediaState)
            if (showSyncResult) {
                showUserMessage("已同步")
            }
            return
        }
        if (guestLocallyPaused) return
        applyRemotePlayback(mediaState, respectControlAction = respectControlAction)
        if (showSyncResult) {
            showUserMessage("已同步")
        }
    }

    private fun shouldReloadForRemoteSync(mediaState: MediaSyncState): Boolean {
        val state = _playerState.value
        return state.error != null ||
            exoPlayer.playbackState == Player.STATE_IDLE ||
            currentUrl.isBlank() ||
            (state.hasActiveMedia && state.resolvedUrl.isBlank())
    }

    private fun shouldNavigateToRemoteMedia(mediaState: MediaSyncState): Boolean {
        if (mediaState.sourceKey.isBlank() || mediaState.vodId.isBlank()) return false
        return currentSourceKey != mediaState.sourceKey ||
            currentVodId != mediaState.vodId ||
            currentPlayFlag != mediaState.playFlag ||
            currentPlayIndex != mediaState.playIndex
    }

    private fun applyRemoteMediaUrl(mediaState: MediaSyncState) {
        applyingRemoteSync = true
        pendingRemoteMedia = mediaState
        try {
            val requestId = beginPlayRequest()
            currentUrl = mediaState.url
            preparePlayer(requestId, mediaState.url, emptyMap(), mediaState.position.coerceAtLeast(0L))
            _playerState.value = _playerState.value.copy(
                title = mediaState.videoTitle.ifBlank { _playerState.value.title },
                currentEpisodeName = mediaState.episodeName.ifBlank { _playerState.value.currentEpisodeName },
                resolvedUrl = mediaState.url,
                speed = mediaState.speed,
                currentPlaybackSpeed = mediaState.temporarySpeed ?: mediaState.speed,
                currentPosition = mediaState.position,
                isPlaying = mediaState.isPlaying,
                hasActiveMedia = true,
                error = null
            )
        } finally {
            applyingRemoteSync = false
        }
    }

    private fun reloadRemotePlayback(mediaState: MediaSyncState, showSyncResult: Boolean = false) {
        pendingRemoteMedia = mediaState
        guestLocallyPaused = false
        val target = calculateRemoteTargetPosition(mediaState)
        if (mediaState.sourceKey.isBlank() || mediaState.vodId.isBlank()) {
            if (mediaState.url.isNotBlank()) {
                applyRemoteMediaUrl(mediaState.copy(position = target))
                if (showSyncResult) showUserMessage("已同步")
            } else {
                applyRemotePlayback(mediaState, respectControlAction = false)
                if (showSyncResult) showUserMessage("已同步")
            }
            return
        }
        val requestId = beginPlayRequest()
        playRequestJob = viewModelScope.launch {
            resolveAndPlay(requestId = requestId, initialPosition = target)
            if (isActivePlayRequest(requestId)) {
                if (_playerState.value.error == null) {
                    applyRemotePlayback(mediaState.copy(position = target), respectControlAction = false)
                    if (showSyncResult) showUserMessage("已同步")
                } else if (mediaState.url.isNotBlank()) {
                    applyRemoteMediaUrl(mediaState.copy(position = target))
                    if (showSyncResult) showUserMessage("已同步")
                } else if (showSyncResult) {
                    showUserMessage("同步失败，请稍后重试")
                }
            }
        }
    }

    private fun applyRemotePlayback(mediaState: MediaSyncState, respectControlAction: Boolean = true) {
        if (guestLocallyPaused) return
        applyingRemoteSync = true
        try {
            val target = calculateRemoteTargetPosition(mediaState)
            val current = exoPlayer.currentPosition.coerceAtLeast(0L)
            val drift = target - current
            val action = if (respectControlAction) mediaState.action else "sync"
            val shouldSeek = action == "seek" ||
                action == "media_change" ||
                kotlin.math.abs(drift) > TOGETHER_SEEK_DRIFT_MS
            if (shouldSeek) {
                val duration = exoPlayer.duration.takeIf { it > 0L } ?: _playerState.value.duration
                exoPlayer.seekTo(clampStartPosition(target, duration))
            }
            val effectiveSpeed = if (shouldSeek || kotlin.math.abs(drift) <= TOGETHER_SMALL_DRIFT_MS) {
                mediaState.temporarySpeed ?: mediaState.speed
            } else {
                calculateCatchUpSpeed(mediaState, drift)
            }
            if (kotlin.math.abs(exoPlayer.playbackParameters.speed - effectiveSpeed) > 0.01f) {
                exoPlayer.playbackParameters = PlaybackParameters(effectiveSpeed)
            }
            if (exoPlayer.playWhenReady != mediaState.isPlaying) {
                exoPlayer.playWhenReady = mediaState.isPlaying
            }
            _playerState.value = _playerState.value.copy(
                currentPosition = target,
                isPlaying = mediaState.isPlaying,
                speed = mediaState.speed,
                currentPlaybackSpeed = effectiveSpeed,
                temporarySpeedActive = mediaState.temporarySpeed != null,
                temporarySpeed = mediaState.temporarySpeed ?: _playerState.value.temporarySpeed,
                error = null
            )
        } finally {
            applyingRemoteSync = false
        }
    }

    private fun calculateRemoteTargetPosition(mediaState: MediaSyncState): Long {
        if (!mediaState.isPlaying) return mediaState.position.coerceAtLeast(0L)
        val elapsed = (System.currentTimeMillis() - mediaState.lastUpdateTime).coerceAtLeast(0L)
        val speed = mediaState.temporarySpeed ?: mediaState.speed
        return (mediaState.position + (elapsed * speed).toLong()).coerceAtLeast(0L)
    }

    private fun calculateCatchUpSpeed(mediaState: MediaSyncState, drift: Long): Float {
        val baseSpeed = mediaState.temporarySpeed ?: mediaState.speed
        return when {
            drift > TOGETHER_SMALL_DRIFT_MS -> (baseSpeed + (drift / TOGETHER_SEEK_DRIFT_MS.toFloat()) * 0.5f)
                .coerceIn(baseSpeed, TOGETHER_MAX_CATCH_UP_SPEED)
            drift < -TOGETHER_SMALL_DRIFT_MS -> (baseSpeed - 0.25f).coerceAtLeast(0.75f)
            else -> baseSpeed
        }
    }

    private fun appendChatMessage(message: ChatMessage) {
        _chatMessages.value = (_chatMessages.value + message).takeLast(120)
        if (!_showWatchTogether.value && !message.isSelf) {
            val text = if (message.isSystem) {
                message.message
            } else {
                "${message.userName}：${message.message}"
            }
            addWatchTogetherNotice(
                text = text,
                level = if (message.isSystem) WatchTogetherNoticeLevel.Warning else WatchTogetherNoticeLevel.Normal
            )
        }
    }

    private fun recordRoomNotice(previous: RoomState?, current: RoomState) {
        if (_showWatchTogether.value || previous == null) return
        val previousMembers = previous.members.associateBy { it.userId }
        current.members.forEach { member ->
            val old = previousMembers[member.userId]
            when {
                old == null && member.userId != current.userId -> {
                    addWatchTogetherNotice("${member.userName} 加入房间")
                    return
                }
                old != null && old.connected && !member.connected -> {
                    addWatchTogetherNotice(
                        text = if (member.isHost) "房主连接已断开，等待重连" else "${member.userName} 连接已断开",
                        level = if (member.isHost) WatchTogetherNoticeLevel.Warning else WatchTogetherNoticeLevel.Normal
                    )
                    return
                }
                old != null && !old.connected && member.connected -> {
                    addWatchTogetherNotice(
                        text = if (member.isHost) "房主已重新连接" else "${member.userName} 已重新连接",
                        level = if (member.isHost) WatchTogetherNoticeLevel.Warning else WatchTogetherNoticeLevel.Normal
                    )
                    return
                }
            }
        }
        val currentIds = current.members.map { it.userId }.toSet()
        previous.members.firstOrNull { it.userId !in currentIds && it.userId != current.userId }?.let { member ->
            addWatchTogetherNotice("${member.userName} 离开房间")
        }
    }

    private fun addWatchTogetherNotice(
        text: String,
        level: WatchTogetherNoticeLevel = WatchTogetherNoticeLevel.Normal
    ) {
        if (text.isBlank()) return
        val current = _watchTogetherNoticeState.value
        _watchTogetherNoticeState.value = current.copy(
            unreadCount = (current.unreadCount + 1).coerceAtMost(99),
            message = text.take(48),
            level = level,
            timestamp = System.currentTimeMillis()
        )
    }

    fun clearWatchTogetherNotice() {
        _watchTogetherNoticeState.value = WatchTogetherNoticeState()
    }

    fun clearWatchTogetherNoticeMessage() {
        val current = _watchTogetherNoticeState.value
        if (current.message != null) {
            _watchTogetherNoticeState.value = current.copy(message = null)
        }
    }

    private fun handleTogetherError(message: String) {
        _watchTogetherUiState.value = WatchTogetherUiState(connecting = false, error = message)
        appendChatMessage(
            ChatMessage(
                userId = "system",
                userName = "系统",
                message = message,
                isSystem = true
            )
        )
    }

    private fun canHostControlPlayback(showMessage: Boolean = true): Boolean {
        if (_roomState.value?.isHost == false) {
            if (showMessage) {
                showUserMessage("一起看房间内仅房主可控制此操作")
            }
            return false
        }
        return true
    }

    private fun maybeAdvanceAfterOutro(state: PlayerState) {
        if (outroAutoAdvanceConsumed) return
        if (!state.isPlaying) return
        val outro = resolveOutroPosition(state.duration, state.skipOutroOffset)
        if (outro <= 0L || state.currentPosition < outro) return
        if (state.currentEpisodeIndex >= state.episodes.lastIndex) return
        outroAutoAdvanceConsumed = true
        playEpisode(state.currentEpisodeIndex + 1)
    }

    private fun formatRecordProgress(position: Long, duration: Long): String {
        if (position <= 0L && duration <= 0L) return ""
        return if (duration > 0L) {
            "${formatDuration(position)} / ${formatDuration(duration)}"
        } else {
            formatDuration(position)
        }
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    override fun onCleared() {
        super.onCleared()
        savePlaybackRecord()
        progressJob?.cancel()
        stopTogetherAutoSync()
        cancelAutoHide()
        exoPlayer.release()
        watchTogetherManager.disconnect()
    }
}

private enum class HostStateRequestReason {
    AutoSync,
    ManualSync
}

private data class EpisodePlayInfo(
    val name: String,
    val url: String,
    val recordStartPosition: Long = 0L
)

private const val PARSE_AUTO_LABEL = "自动"
