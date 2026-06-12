package com.vunbo.watchtogether.feature.player.model

import com.vunbo.watchtogether.core.player.PlayerHelper
import com.vunbo.watchtogether.data.model.VodSeries

data class PlayerState(
    val title: String = "正在播放",
    val currentEpisodeName: String = "",
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val speed: Float = 1.0f,
    val currentPlaybackSpeed: Float = 1.0f,
    val controlsVisible: Boolean = true,
    val isLoading: Boolean = true,
    val error: String? = null,
    val userMessage: String? = null,
    val userMessageTimestamp: Long = 0L,
    val resolvedUrl: String = "",
    val currentFlag: String = "",
    val episodes: List<VodSeries> = emptyList(),
    val currentEpisodeIndex: Int = 0,
    val currentSourceKey: String = "",
    val currentVodId: String = "",
    val hasActiveMedia: Boolean = false,
    val episodeSheetVisible: Boolean = false,
    val settingsPanelVisible: Boolean = false,
    val controlsLocked: Boolean = false,
    val isFavorite: Boolean = false,
    val currentPlayerType: Int = PlayerHelper.PLAYER_TYPE_EXO,
    val currentScaleType: Int = PlayerHelper.SCALE_DEFAULT,
    val selectedParseLine: String = "",
    val activeParseLine: String = "",
    val parseLineOptions: List<String> = emptyList(),
    val skipIntroPosition: Long = 0L,
    val skipOutroPosition: Long = 0L,
    val skipOutroOffset: Long = 0L,
    val gestureSeekPosition: Long? = null,
    val gestureSeekOffsetMs: Long = 0L,
    val temporarySpeedActive: Boolean = false,
    val temporarySpeed: Float = 2.0f
)

data class RemoteMediaTarget(
    val sourceKey: String,
    val vodId: String,
    val playFlag: String,
    val playIndex: Int
)
