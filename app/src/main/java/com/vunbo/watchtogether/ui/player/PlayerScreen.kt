package com.vunbo.watchtogether.ui.player

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.vunbo.watchtogether.MainActivity
import com.vunbo.watchtogether.data.util.PlayerHelper
import com.vunbo.watchtogether.ui.theme.DarkBackground
import com.vunbo.watchtogether.ui.theme.DarkCard
import com.vunbo.watchtogether.ui.theme.DarkSurface
import com.vunbo.watchtogether.ui.theme.DarkSurfaceVariant
import com.vunbo.watchtogether.ui.theme.Primary
import com.vunbo.watchtogether.ui.theme.RoomConnected
import com.vunbo.watchtogether.ui.theme.Secondary
import com.vunbo.watchtogether.ui.theme.SecondaryMuted
import com.vunbo.watchtogether.ui.theme.TextOnPrimary
import com.vunbo.watchtogether.ui.theme.TextPrimary
import com.vunbo.watchtogether.ui.theme.TextSecondary
import com.vunbo.watchtogether.ui.theme.TextTertiary
import com.vunbo.watchtogether.ui.watchtogether.WatchTogetherOverlay
import com.vunbo.watchtogether.ui.watchtogether.WatchTogetherUiState
import com.vunbo.watchtogether.ui.watchtogether.WatchTogetherNoticeLevel
import com.vunbo.watchtogether.ui.watchtogether.WatchTogetherNoticeState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class PlayerUiMode {
    Embedded,
    Fullscreen
}

private enum class VideoScaleMode(
    val label: String,
    val scaleType: Int,
    val resizeMode: Int
) {
    Default("默认", 0, AspectRatioFrameLayout.RESIZE_MODE_FIT),
    Fit16x9("16:9", 1, AspectRatioFrameLayout.RESIZE_MODE_FIT),
    Fit4x3("4:3", 2, AspectRatioFrameLayout.RESIZE_MODE_FIT),
    Fill("填充", 3, AspectRatioFrameLayout.RESIZE_MODE_FILL),
    Original("原始", 4, AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    Crop("裁剪", 5, AspectRatioFrameLayout.RESIZE_MODE_ZOOM);

    companion object {
        fun fromScaleType(scaleType: Int): VideoScaleMode {
            return entries.firstOrNull { it.scaleType == scaleType } ?: Default
        }
    }
}

@Composable
fun PlayerScreen(
    sourceKey: String,
    vodId: String,
    playFlag: String,
    playIndex: Int,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = viewModel()
) {
    val roomState by viewModel.roomState.collectAsState()
    var showExitTogetherDialog by remember { mutableStateOf(false) }

    LaunchedEffect(sourceKey, vodId, playFlag, playIndex) {
        viewModel.startPlay(sourceKey, vodId, playFlag, playIndex)
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopPlaybackAfterLeavingPage()
        }
    }

    fun requestBack() {
        if (roomState != null) {
            showExitTogetherDialog = true
        } else {
            viewModel.stopPlaybackAfterLeavingPage()
            onBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        PlayerSurface(
            modifier = Modifier.fillMaxSize(),
            mode = PlayerUiMode.Fullscreen,
            onBack = ::requestBack,
            onRequestFullscreen = {},
            viewModel = viewModel,
            forceFullscreen = true
        )
    }

    if (showExitTogetherDialog) {
        ExitTogetherConfirmDialog(
            isHost = viewModel.isTogetherHost(),
            onDismiss = { showExitTogetherDialog = false },
            onConfirmExit = {
                showExitTogetherDialog = false
                viewModel.leaveRoom()
                viewModel.stopPlayback()
                onBack()
            }
        )
    }
}

@Composable
fun EmbeddedPlayerSurface(
    sourceKey: String,
    vodId: String,
    playFlag: String,
    playIndex: Int,
    onRequestFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = viewModel()
) {
    LaunchedEffect(sourceKey, vodId, playFlag, playIndex) {
        viewModel.startPlay(sourceKey, vodId, playFlag, playIndex)
    }

    PlayerSurface(
        modifier = modifier,
        mode = PlayerUiMode.Embedded,
        onBack = {},
        onRequestFullscreen = onRequestFullscreen,
        viewModel = viewModel
    )
}

@Composable
private fun ExitTogetherConfirmDialog(
    isHost: Boolean,
    onDismiss: () -> Unit,
    onConfirmExit: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Text(
                text = "还在一起看房间中",
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = if (isHost) {
                    "退出后房间将结束，其他成员会停止同步。"
                } else {
                    "退出后将离开房间，不再同步房主播放。"
                },
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirmExit,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Secondary)
            ) {
                Text("退出房间", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("继续观看", color = TextTertiary)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSurface(
    modifier: Modifier = Modifier,
    mode: PlayerUiMode,
    onBack: () -> Unit,
    onRequestFullscreen: () -> Unit,
    viewModel: PlayerViewModel = viewModel(),
    forceFullscreen: Boolean = false
) {
    val playerState by viewModel.playerState.collectAsState()
    val showWatchTogether by viewModel.showWatchTogether.collectAsState()
    val roomState by viewModel.roomState.collectAsState()
    val chatMessages by viewModel.chatMessages.collectAsState()
    val watchTogetherUiState by viewModel.watchTogetherUiState.collectAsState()
    val watchTogetherNoticeState by viewModel.watchTogetherNoticeState.collectAsState()
    val context = LocalContext.current
    val activity = context.findActivity()
    val pipMode by ((activity as? MainActivity)?.pictureInPictureMode
        ?: kotlinx.coroutines.flow.MutableStateFlow(false)).collectAsState()
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableFloatStateOf(0f) }
    val scaleMode = remember(playerState.currentScaleType) {
        VideoScaleMode.fromScaleType(playerState.currentScaleType)
    }
    val durationValue = playerState.duration.coerceAtLeast(0L).toFloat().coerceAtLeast(1f)
    val effectiveMode = if (mode == PlayerUiMode.Fullscreen || forceFullscreen) {
        PlayerUiMode.Fullscreen
    } else {
        PlayerUiMode.Embedded
    }

    DisposableEffect(activity, effectiveMode) {
        if (effectiveMode == PlayerUiMode.Fullscreen) {
            activity?.setPlayerFullscreen(true)
        }
        onDispose {
            activity?.setPlayerFullscreen(false)
            viewModel.setPictureInPictureMode(activity?.isInPictureInPictureMode == true)
        }
    }

    LaunchedEffect(pipMode) {
        viewModel.setPictureInPictureMode(pipMode)
    }

    LaunchedEffect(watchTogetherNoticeState.timestamp) {
        if (watchTogetherNoticeState.message != null) {
            delay(2400L)
            viewModel.clearWatchTogetherNoticeMessage()
        }
    }

    BackHandler(enabled = effectiveMode == PlayerUiMode.Fullscreen) {
        if (playerState.settingsPanelVisible) {
            viewModel.dismissSettingsPanel()
        } else {
            onBack()
        }
    }

    LaunchedEffect(playerState.currentPosition, playerState.duration, isScrubbing) {
        if (!isScrubbing) {
            scrubPosition = playerState.currentPosition.toFloat().coerceIn(0f, durationValue)
        }
    }

    key(effectiveMode) {
        Box(
        modifier = modifier
            .background(Color.Black)
            .then(
                if (effectiveMode == PlayerUiMode.Embedded) {
                    Modifier.aspectRatio(16f / 9f)
                } else {
                    Modifier.fillMaxSize()
                }
            )
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                PlayerView(context).apply {
                    player = viewModel.exoPlayer
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    setBackgroundColor(android.graphics.Color.BLACK)
                    resizeMode = scaleMode.resizeMode
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { playerView ->
                playerView.player = viewModel.exoPlayer
                playerView.resizeMode = scaleMode.resizeMode
            }
        )

        PlayerGestureLayer(
            enabled = effectiveMode == PlayerUiMode.Fullscreen,
            onTapCenter = { viewModel.toggleControls() },
            onBeginDrag = { viewModel.beginGestureSeek() },
            onHorizontalDrag = { ratio -> viewModel.updateGestureSeek(ratio) },
            onEndDrag = { viewModel.commitGestureSeek() },
            onCancelDrag = { viewModel.cancelGestureSeek() },
            onLongPressSpeedStart = { viewModel.startTemporarySpeed() },
            onLongPressSpeedStop = { viewModel.stopTemporarySpeed() }
        )

        if (playerState.controlsVisible) {
            when (effectiveMode) {
                PlayerUiMode.Embedded -> EmbeddedPlayerControls(
                    state = playerState,
                    scrubPosition = scrubPosition,
                    durationValue = durationValue,
                    isScrubbing = isScrubbing,
                    onScrubStart = {
                        isScrubbing = true
                        viewModel.showControls()
                    },
                    onScrub = { scrubPosition = it },
                    onScrubFinished = {
                        isScrubbing = false
                        viewModel.seekTo(scrubPosition.toLong())
                    },
                    onPlayPause = { viewModel.togglePlay() },
                    onFullscreenClick = onRequestFullscreen,
                    modifier = Modifier.fillMaxSize()
                )

                PlayerUiMode.Fullscreen -> FullscreenPlayerControls(
                    state = playerState,
                    scrubPosition = scrubPosition,
                    durationValue = durationValue,
                    isScrubbing = isScrubbing,
                    showWatchTogether = showWatchTogether,
                    watchTogetherNoticeState = watchTogetherNoticeState,
                    onToggleLock = { viewModel.toggleControlsLock() },
                    onPictureInPicture = {
                        viewModel.beginPictureInPictureRequest()
                        val entered = (activity as? MainActivity)?.enterPlayerPictureInPicture() == true
                        viewModel.finishPictureInPictureRequest(entered)
                        if (!entered) {
                            viewModel.showUserMessage("无法进入小窗播放，请检查系统是否允许画中画")
                        }
                    },
                    onScrubStart = {
                        if (!playerState.controlsLocked) {
                            isScrubbing = true
                            viewModel.showControls()
                        }
                    },
                    onScrub = {
                        if (!playerState.controlsLocked) {
                            scrubPosition = it
                        }
                    },
                    onScrubFinished = {
                        if (!playerState.controlsLocked) {
                            isScrubbing = false
                            viewModel.seekTo(scrubPosition.toLong())
                        }
                    },
                    onBack = onBack,
                    onPlayPause = { viewModel.togglePlay() },
                    onPreviousEpisode = { viewModel.playPreviousEpisode() },
                    onNextEpisode = { viewModel.playNextEpisode() },
                    onReplay = { viewModel.replayPlayback() },
                    onRefresh = { viewModel.refreshPlayback() },
                    onEpisodeClick = { viewModel.openEpisodeSheet() },
                    onWatchTogetherClick = { viewModel.toggleWatchTogether() },
                    onSettingsClick = { viewModel.toggleSettingsPanel() },
                    onTogglePlayerType = { viewModel.togglePreferredPlayer() },
                    onMarkIntro = { viewModel.markIntroPosition() },
                    onMarkOutro = { viewModel.markOutroPosition() },
                    onResetIntro = { viewModel.resetIntroPosition() },
                    onResetOutro = { viewModel.resetOutroPosition() },
                    onFullscreenClick = onBack,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        if (playerState.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Secondary
            )
        }

        playerState.error?.let { error ->
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.72f)
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        playerState.userMessage?.let { message ->
            PlayerToastMessage(
                text = message,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = if (effectiveMode == PlayerUiMode.Fullscreen) 112.dp else 52.dp)
            )
        }

        val gestureTarget = playerState.gestureSeekPosition
        if (gestureTarget != null && effectiveMode == PlayerUiMode.Fullscreen) {
            GestureSeekOverlay(
                deltaMs = playerState.gestureSeekOffsetMs,
                targetPosition = gestureTarget,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        if (playerState.temporarySpeedActive && effectiveMode == PlayerUiMode.Fullscreen) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 160.dp),
                shape = RoundedCornerShape(14.dp),
                color = Color.Black.copy(alpha = 0.66f)
            ) {
                Text(
                    text = "${playerState.temporarySpeed}x 倍速播放中",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        if (
            effectiveMode == PlayerUiMode.Fullscreen &&
            roomState != null &&
            !playerState.controlsVisible &&
            !showWatchTogether
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PlayerRoundButton(
                    icon = Icons.Filled.Groups,
                    onClick = { viewModel.toggleWatchTogether() },
                    contentDescription = "一起看",
                    size = 48.dp,
                    badgeCount = watchTogetherNoticeState.unreadCount,
                    warningBadge = watchTogetherNoticeState.level == WatchTogetherNoticeLevel.Warning
                )
                WatchTogetherFloatingNotice(
                    notice = watchTogetherNoticeState,
                    modifier = Modifier.widthIn(max = 220.dp)
                )
            }
        }

        if (effectiveMode == PlayerUiMode.Fullscreen && playerState.settingsPanelVisible) {
            FullscreenSettingsPanel(
                currentPlayerType = playerState.currentPlayerType,
                scaleMode = scaleMode,
                currentSpeed = playerState.currentPlaybackSpeed,
                skipIntroPosition = playerState.skipIntroPosition,
                skipOutroPosition = playerState.skipOutroPosition,
                isFavorite = playerState.isFavorite,
                resolvedUrl = playerState.resolvedUrl,
                parseOptions = playerState.parseLineOptions,
                selectedParseLine = playerState.selectedParseLine,
                activeParseLine = playerState.activeParseLine,
                ijkCodecOptions = playerState.ijkCodecOptions,
                selectedIjkCodec = playerState.selectedIjkCodec,
                onPlayerTypeSelect = { viewModel.setPreferredPlayerType(it) },
                onScaleModeSelect = { viewModel.setScaleType(it.scaleType) },
                onSpeedSelect = { viewModel.setPlaybackSpeed(it) },
                onParseLineSelect = { viewModel.selectParseLine(it) },
                onIjkCodecSelect = { viewModel.setIjkCodec(it) },
                onMarkIntro = { viewModel.markIntroPosition() },
                onMarkOutro = { viewModel.markOutroPosition() },
                onFavoriteClick = { viewModel.toggleFavorite() },
                onCopyUrl = {
                    val url = playerState.resolvedUrl
                    if (url.isNotBlank()) {
                        context.copyPlainText("播放链接", url)
                        viewModel.showUserMessage("已复制播放链接")
                    } else {
                        viewModel.showUserMessage("暂无可复制的播放链接")
                    }
                },
                onPictureInPicture = {
                    viewModel.beginPictureInPictureRequest()
                    val entered = (activity as? MainActivity)?.enterPlayerPictureInPicture() == true
                    viewModel.finishPictureInPictureRequest(entered)
                    if (!entered) {
                        viewModel.showUserMessage("无法进入小窗播放，请检查系统是否允许画中画")
                    }
                },
                onWatchTogether = { viewModel.toggleWatchTogether() },
                onResetIntro = { viewModel.resetIntroPosition() },
                onResetOutro = { viewModel.resetOutroPosition() },
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }

        if (effectiveMode == PlayerUiMode.Fullscreen && showWatchTogether && roomState != null) {
            WatchTogetherOverlay(
                roomState = roomState!!,
                messages = chatMessages,
                onSendMessage = { viewModel.sendChatMessage(it) },
                onSyncHost = { viewModel.requestHostSync() },
                onCollapse = { viewModel.dismissWatchTogetherPanel() },
                onLeaveRoom = { viewModel.leaveRoom() },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .fillMaxWidth(0.45f)
            )
        }
        }
    }

    if (effectiveMode == PlayerUiMode.Fullscreen && playerState.episodeSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.closeEpisodeSheet() },
            containerColor = DarkSurface,
            tonalElevation = 0.dp
        ) {
            EpisodeSheetContent(
                state = playerState,
                onEpisodeClick = { index -> viewModel.playEpisode(index) }
            )
        }
    }

    if (effectiveMode == PlayerUiMode.Fullscreen && showWatchTogether && roomState == null) {
        WatchTogetherDialog(
            defaultServerUrl = viewModel.getDefaultTogetherServerUrl(),
            uiState = watchTogetherUiState,
            onCreateRoom = { serverUrl -> viewModel.createRoom(serverUrl) },
            onJoinRoom = { serverUrl, code -> viewModel.joinRoom(serverUrl, code) },
            onClearError = { viewModel.clearTogetherError() },
            onDismiss = { viewModel.dismissWatchTogetherPanel() }
        )
    }
}

@Composable
private fun PlayerGestureLayer(
    enabled: Boolean,
    onTapCenter: () -> Unit,
    onBeginDrag: () -> Unit,
    onHorizontalDrag: (Float) -> Unit,
    onEndDrag: () -> Unit,
    onCancelDrag: () -> Unit,
    onLongPressSpeedStart: () -> Unit,
    onLongPressSpeedStop: () -> Unit
) {
    if (!enabled) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onTapCenter() })
                }
        )
        return
    }

    Row(modifier = Modifier.fillMaxSize()) {
        SpeedPressZone(
            modifier = Modifier.weight(1f),
            enabled = true,
            onLongPressSpeedStart = onLongPressSpeedStart,
            onLongPressSpeedStop = onLongPressSpeedStop
        )
        Box(
            modifier = Modifier
                .weight(2f)
                .fillMaxHeight()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { onBeginDrag() },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val width = size.width.coerceAtLeast(1)
                            onHorizontalDrag(dragAmount / width.toFloat())
                        },
                        onDragEnd = { onEndDrag() },
                        onDragCancel = { onCancelDrag() }
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onTapCenter() })
                }
        )
        SpeedPressZone(
            modifier = Modifier.weight(1f),
            enabled = true,
            onLongPressSpeedStart = onLongPressSpeedStart,
            onLongPressSpeedStop = onLongPressSpeedStop
        )
    }
}

@Composable
private fun SpeedPressZone(
    modifier: Modifier = Modifier,
    enabled: Boolean,
    onLongPressSpeedStart: () -> Unit,
    onLongPressSpeedStop: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .pointerInput(enabled) {
                detectTapGestures(
                    onPress = {
                        if (!enabled) {
                            tryAwaitRelease()
                            return@detectTapGestures
                        }
                        coroutineScope {
                            var activated = false
                            val job = launch {
                                delay(350L)
                                activated = true
                                onLongPressSpeedStart()
                            }
                            tryAwaitRelease()
                            job.cancel()
                            if (activated) {
                                onLongPressSpeedStop()
                            }
                        }
                    }
                )
            }
    )
}

@Composable
private fun EmbeddedPlayerControls(
    state: PlayerState,
    scrubPosition: Float,
    durationValue: Float,
    isScrubbing: Boolean,
    onScrubStart: () -> Unit,
    onScrub: (Float) -> Unit,
    onScrubFinished: () -> Unit,
    onPlayPause: () -> Unit,
    onFullscreenClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val horizontalPadding = (maxWidth * 0.040f).coerceIn(12.dp, 18.dp)
        val topPadding = (maxHeight * 0.040f).coerceIn(8.dp, 14.dp)
        val bottomPadding = (maxHeight * 0.045f).coerceIn(10.dp, 16.dp)
        val progressTrack = (maxHeight * 0.0075f).coerceIn(2.dp, 3.dp)
        val thumbRadius = (maxHeight * 0.018f).coerceIn(4.dp, 6.dp)
        val playButtonSize = (maxWidth * 0.094f).coerceIn(34.dp, 40.dp)
        val fullscreenButtonSize = (maxWidth * 0.088f).coerceIn(32.dp, 38.dp)

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.72f),
                            Color.Black.copy(alpha = 0.20f),
                            Color.Transparent
                        )
                    )
                )
                .padding(horizontal = horizontalPadding, vertical = topPadding),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = state.title,
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (state.currentEpisodeName.isNotBlank()) {
                Text(
                    text = state.currentEpisodeName,
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.18f),
                            Color.Black.copy(alpha = 0.82f)
                        )
                    )
                )
                .padding(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    top = 14.dp,
                    bottom = bottomPadding
                ),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PlayerCommandButton(
                    icon = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    onClick = onPlayPause,
                    contentDescription = if (state.isPlaying) "暂停" else "播放",
                    size = playButtonSize
                )
                Text(
                    text = formatDuration(scrubPosition.toLong()),
                    color = TextPrimary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
                ThinProgressSlider(
                    value = scrubPosition.coerceIn(0f, durationValue),
                    durationValue = durationValue,
                    isScrubbing = isScrubbing,
                    onScrubStart = onScrubStart,
                    onScrub = onScrub,
                    onScrubFinished = onScrubFinished,
                    trackHeight = progressTrack,
                    thumbRadius = thumbRadius,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatDuration(state.duration),
                    color = TextTertiary,
                    style = MaterialTheme.typography.labelSmall
                )
                PlayerCommandButton(
                    icon = Icons.Filled.Fullscreen,
                    onClick = onFullscreenClick,
                    contentDescription = "全屏",
                    size = fullscreenButtonSize
                )
            }
        }
    }
}
@Composable
private fun FullscreenPlayerControls(
    state: PlayerState,
    scrubPosition: Float,
    durationValue: Float,
    isScrubbing: Boolean,
    showWatchTogether: Boolean,
    watchTogetherNoticeState: WatchTogetherNoticeState,
    onToggleLock: () -> Unit,
    onPictureInPicture: () -> Unit,
    onScrubStart: () -> Unit,
    onScrub: (Float) -> Unit,
    onScrubFinished: () -> Unit,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onPreviousEpisode: () -> Unit,
    onNextEpisode: () -> Unit,
    onReplay: () -> Unit,
    onRefresh: () -> Unit,
    onEpisodeClick: () -> Unit,
    onWatchTogetherClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onTogglePlayerType: () -> Unit,
    onMarkIntro: () -> Unit,
    onMarkOutro: () -> Unit,
    onResetIntro: () -> Unit,
    onResetOutro: () -> Unit,
    onFullscreenClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val horizontalPadding = (maxWidth * 0.024f).coerceIn(18.dp, 28.dp)
        val topPadding = (maxHeight * 0.026f).coerceIn(10.dp, 18.dp)
        val bottomPadding = (maxHeight * 0.026f).coerceIn(10.dp, 20.dp)
        val sideButtonSize = (maxHeight * 0.096f).coerceIn(40.dp, 52.dp)
        val centerButtonSize = (maxHeight * 0.138f).coerceIn(52.dp, 70.dp)
        val progressTrack = (maxHeight * 0.0065f).coerceIn(3.dp, 4.dp)
        val thumbRadius = (maxHeight * 0.018f).coerceIn(7.dp, 9.dp)
        val hasPreviousEpisode = state.currentEpisodeIndex > 0
        val hasNextEpisode = state.episodes.isNotEmpty() && state.currentEpisodeIndex < state.episodes.lastIndex

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.38f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.46f)
                        )
                    )
                )
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(start = horizontalPadding, end = horizontalPadding, top = topPadding),
            verticalAlignment = Alignment.Top
        ) {
            PlayerTopIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                onClick = onBack,
                size = sideButtonSize
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp, top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (state.currentEpisodeName.isNotBlank()) {
                    Text(
                        text = state.currentEpisodeName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            PlayerBatteryStatus()
        }

        PlayerRoundButton(
            icon = if (state.controlsLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
            onClick = onToggleLock,
            contentDescription = if (state.controlsLocked) "解锁" else "锁定",
            size = sideButtonSize,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = horizontalPadding)
        )

        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = horizontalPadding),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PlayerRoundButton(
                icon = Icons.Filled.PictureInPictureAlt,
                onClick = onPictureInPicture,
                contentDescription = "小窗播放",
                size = sideButtonSize
            )
            PlayerRoundButton(
                icon = if (showWatchTogether) Icons.Filled.Groups else Icons.Filled.GroupAdd,
                onClick = onWatchTogetherClick,
                contentDescription = "一起看",
                size = sideButtonSize,
                badgeCount = watchTogetherNoticeState.unreadCount,
                warningBadge = watchTogetherNoticeState.level == WatchTogetherNoticeLevel.Warning
            )
        }

        WatchTogetherFloatingNotice(
            notice = watchTogetherNoticeState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = horizontalPadding + sideButtonSize + 10.dp)
        )

        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (maxHeight * 0.105f).coerceIn(42.dp, 70.dp)),
            horizontalArrangement = Arrangement.spacedBy(26.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayerCenterActionButton(
                icon = Icons.Filled.SkipPrevious,
                onClick = onPreviousEpisode,
                contentDescription = "上一集",
                enabled = hasPreviousEpisode,
                size = centerButtonSize
            )
            PlayerCenterActionButton(
                icon = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                onClick = onPlayPause,
                contentDescription = if (state.isPlaying) "暂停" else "播放",
                enabled = true,
                emphasized = true,
                size = centerButtonSize * 1.08f
            )
            PlayerCenterActionButton(
                icon = Icons.Filled.SkipNext,
                onClick = onNextEpisode,
                contentDescription = "下一集",
                enabled = hasNextEpisode,
                size = centerButtonSize
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    bottom = bottomPadding
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                PlayerBottomPlayButton(
                    icon = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    onClick = onPlayPause,
                    contentDescription = if (state.isPlaying) "暂停" else "播放"
                )
                Text(
                    text = formatDuration(scrubPosition.toLong()),
                    color = TextPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                ThinProgressSlider(
                    value = scrubPosition.coerceIn(0f, durationValue),
                    durationValue = durationValue,
                    isScrubbing = isScrubbing,
                    onScrubStart = onScrubStart,
                    onScrub = onScrub,
                    onScrubFinished = onScrubFinished,
                    trackHeight = progressTrack,
                    thumbRadius = thumbRadius,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatDuration(state.duration),
                    color = TextPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                PlayerRoundButton(
                    icon = Icons.Filled.FullscreenExit,
                    onClick = onFullscreenClick,
                    contentDescription = "退出全屏",
                    size = sideButtonSize * 0.94f
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PlayerBottomTextButton(
                    label = if (state.currentPlayerType == PlayerHelper.PLAYER_TYPE_IJK) "IJK" else "Exo",
                    onClick = onTogglePlayerType
                )
                PlayerBottomTextButton(label = "刷新", onClick = onRefresh)
                PlayerBottomTextButton(label = "重播", onClick = onReplay)
                PlayerBottomTextButton(
                    label = if (state.skipIntroPosition > 0L) "片头 ${formatDuration(state.skipIntroPosition)}" else "片头",
                    onClick = onMarkIntro,
                    onLongClick = onResetIntro
                )
                PlayerBottomTextButton(
                    label = if (state.skipOutroPosition > 0L) "片尾 ${formatDuration(state.skipOutroPosition)}" else "片尾",
                    onClick = onMarkOutro,
                    onLongClick = onResetOutro
                )
                PlayerBottomTextButton(label = "选集", onClick = onEpisodeClick)
                PlayerBottomTextButton(label = "设置", onClick = onSettingsClick)
            }
        }
    }
}
@Composable
private fun PlayerRoundButton(
    icon: ImageVector,
    onClick: () -> Unit,
    contentDescription: String,
    size: Dp = 52.dp,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    badgeCount: Int = 0,
    warningBadge: Boolean = false
) {
    Surface(
        modifier = modifier
            .size(size)
            .clickable(enabled = enabled, onClick = onClick),
        shape = CircleShape,
        color = Color.Black.copy(alpha = if (enabled) 0.42f else 0.24f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = if (enabled) 0.08f else 0.04f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) TextPrimary else TextTertiary,
                modifier = Modifier.size((size * 0.40f).coerceIn(18.dp, 24.dp))
            )
            if (badgeCount > 0) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 3.dp, end = 3.dp),
                    shape = CircleShape,
                    color = if (warningBadge) Color(0xFFFF6B6B) else Secondary
                ) {
                    Text(
                        text = if (badgeCount > 9) "9+" else badgeCount.toString(),
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                        color = Color.Black,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun WatchTogetherFloatingNotice(
    notice: WatchTogetherNoticeState,
    modifier: Modifier = Modifier
) {
    val text = notice.message ?: return
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = if (notice.level == WatchTogetherNoticeLevel.Warning) {
            Color(0xFFE95F5F).copy(alpha = 0.88f)
        } else {
            Color.Black.copy(alpha = 0.58f)
        },
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            color = TextPrimary,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PlayerToastMessage(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = Color.Black.copy(alpha = 0.68f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            color = TextPrimary,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PlayerCenterActionButton(
    icon: ImageVector,
    onClick: () -> Unit,
    contentDescription: String,
    enabled: Boolean,
    emphasized: Boolean = false,
    size: Dp = 58.dp
) {
    Surface(
        modifier = Modifier
            .size(size)
            .clickable(enabled = enabled, onClick = onClick),
        shape = CircleShape,
        color = when {
            !enabled -> Color.Black.copy(alpha = 0.16f)
            emphasized -> Color.Black.copy(alpha = 0.48f)
            else -> Color.Black.copy(alpha = 0.42f)
        },
        border = BorderStroke(
            1.dp,
            if (enabled) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.04f)
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) TextPrimary else TextTertiary,
                modifier = Modifier.size(
                    (size * if (emphasized) 0.40f else 0.36f).coerceIn(20.dp, 28.dp)
                )
            )
        }
    }
}

@Composable
private fun PlayerBottomPlayButton(
    icon: ImageVector,
    onClick: () -> Unit,
    contentDescription: String
) {
    Surface(
        modifier = Modifier
            .size(52.dp)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = Color.Transparent
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = TextPrimary,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}

@Composable
private fun PlayerBatteryStatus() {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color.Black.copy(alpha = 0.24f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.BatteryFull,
                contentDescription = "电量",
                tint = TextPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun PlayerBottomTextButton(
    label: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true
) {
    Text(
        text = label,
        modifier = Modifier
            .pointerInput(enabled, onLongClick) {
                detectTapGestures(
                    onTap = { if (enabled) onClick() },
                    onLongPress = { if (enabled) onLongClick?.invoke() }
                )
            }
            .padding(horizontal = 4.dp, vertical = 3.dp),
        color = if (enabled) TextPrimary else TextTertiary,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun PlayerSettingShortcut(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = RoundedCornerShape(12.dp),
            color = Color.White.copy(alpha = 0.08f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Text(
            text = label,
            color = TextPrimary,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}

@Composable
private fun PlayerSettingTitle(text: String) {
    Text(
        text = text,
        color = TextPrimary,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun PlayerSettingChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.pointerInput(onLongClick) {
            detectTapGestures(
                onTap = { onClick() },
                onLongPress = { onLongClick?.invoke() }
            )
        },
        shape = RoundedCornerShape(999.dp),
        color = if (selected) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.07f)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = if (selected) Color(0xFFFFC95B) else TextPrimary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun PlayerCommandButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    contentDescription: String,
    size: Dp
) {
    Surface(
        modifier = Modifier
            .size(size)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(size * 0.32f),
        color = Color.Black.copy(alpha = 0.30f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = TextPrimary,
                modifier = Modifier.size((size * 0.48f).coerceIn(18.dp, 24.dp))
            )
        }
    }
}

@Composable
private fun PlayerTopIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: Dp
) {
    Surface(
        modifier = Modifier
            .size(size)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.28f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = TextPrimary,
                modifier = Modifier.size((size * 0.44f).coerceIn(18.dp, 24.dp))
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThinProgressSlider(
    value: Float,
    durationValue: Float,
    isScrubbing: Boolean,
    onScrubStart: () -> Unit,
    onScrub: (Float) -> Unit,
    onScrubFinished: () -> Unit,
    trackHeight: Dp = 4.dp,
    thumbRadius: Dp = 8.dp,
    modifier: Modifier = Modifier
) {
    val safeDuration = durationValue.coerceAtLeast(1f)
    val currentValue = value.coerceIn(0f, safeDuration)
    val sliderColors = SliderDefaults.colors(
        thumbColor = Color.White,
        activeTrackColor = Color.White,
        inactiveTrackColor = Color.White.copy(alpha = 0.20f)
    )
    Slider(
        value = currentValue,
        onValueChange = {
            if (!isScrubbing) {
                onScrubStart()
            }
            onScrub(it.coerceIn(0f, safeDuration))
        },
        onValueChangeFinished = onScrubFinished,
        valueRange = 0f..safeDuration,
        modifier = modifier.height((thumbRadius * 2.8f).coerceAtLeast(26.dp)),
        colors = sliderColors,
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                modifier = Modifier.height(trackHeight),
                colors = sliderColors,
                thumbTrackGapSize = 0.dp,
                drawStopIndicator = null
            )
        }
    )
}

@Composable
private fun FullscreenSettingsPanel(
    currentPlayerType: Int,
    scaleMode: VideoScaleMode,
    currentSpeed: Float,
    skipIntroPosition: Long,
    skipOutroPosition: Long,
    isFavorite: Boolean,
    resolvedUrl: String,
    parseOptions: List<String>,
    selectedParseLine: String,
    activeParseLine: String,
    ijkCodecOptions: List<String>,
    selectedIjkCodec: String,
    onPlayerTypeSelect: (Int) -> Unit,
    onScaleModeSelect: (VideoScaleMode) -> Unit,
    onSpeedSelect: (Float) -> Unit,
    onParseLineSelect: (String) -> Unit,
    onIjkCodecSelect: (String) -> Unit,
    onMarkIntro: () -> Unit,
    onMarkOutro: () -> Unit,
    onFavoriteClick: () -> Unit,
    onCopyUrl: () -> Unit,
    onPictureInPicture: () -> Unit,
    onWatchTogether: () -> Unit,
    onResetIntro: () -> Unit,
    onResetOutro: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth(0.46f)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0x8A0B1117),
                        Color(0xD3182730)
                    )
                )
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    PlayerSettingShortcut(Icons.Filled.PictureInPictureAlt, "小窗", onPictureInPicture)
                    PlayerSettingShortcut(Icons.Filled.Groups, "一起看", onWatchTogether)
                    PlayerSettingShortcut(
                        if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        if (isFavorite) "已收藏" else "收藏",
                        onFavoriteClick
                    )
                    PlayerSettingShortcut(Icons.Filled.ContentCopy, "复制链接", onCopyUrl)
                }
            }
            item { HorizontalDivider(color = Color.White.copy(alpha = 0.10f)) }
            item {
                SettingOptionStrip(
                    title = "解析",
                    options = listOf("自动") + parseOptions,
                    selected = selectedParseLine.ifBlank { "自动" },
                    secondary = activeParseLine.takeIf { it.isNotBlank() }?.let { "当前 $it" },
                    onSelect = onParseLineSelect
                )
            }
            item {
                SettingOptionStrip(
                    title = "播放速度",
                    options = listOf("0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "1.75x", "2.0x", "3.0x"),
                    selected = "${String.format("%.2f", currentSpeed).trimEnd('0').trimEnd('.')}x",
                    onSelect = { onSpeedSelect(it.removeSuffix("x").toFloat()) }
                )
            }
            item {
                SettingOptionStrip(
                    title = "画面缩放",
                    options = VideoScaleMode.entries.map { it.label },
                    selected = scaleMode.label,
                    onSelect = { label ->
                        VideoScaleMode.entries.firstOrNull { it.label == label }?.let(onScaleModeSelect)
                    }
                )
            }
            item {
                SettingOptionStrip(
                    title = "解码方式",
                    options = listOf("Exo", "IJK"),
                    selected = if (currentPlayerType == PlayerHelper.PLAYER_TYPE_IJK) "IJK" else "Exo",
                    onSelect = {
                        onPlayerTypeSelect(
                            if (it == "IJK") PlayerHelper.PLAYER_TYPE_IJK else PlayerHelper.PLAYER_TYPE_EXO
                        )
                    }
                )
            }
            item {
                SettingOptionStrip(
                    title = "IJK解码",
                    options = ijkCodecOptions.ifEmpty { listOf("硬解码", "软解码") },
                    selected = selectedIjkCodec,
                    onSelect = onIjkCodecSelect
                )
            }
            item {
                SettingOptionStrip(
                    title = "跳过片头片尾",
                    options = listOf(
                        if (skipIntroPosition > 0L) "片头 ${formatDuration(skipIntroPosition)}" else "记录片头",
                        if (skipOutroPosition > 0L) "片尾 ${formatDuration(skipOutroPosition)}" else "记录片尾"
                    ),
                    selected = "",
                    onSelect = {
                        if (it.startsWith("片头") || it.startsWith("记录片头")) onMarkIntro() else onMarkOutro()
                    },
                    onLongSelect = {
                        if (it.startsWith("片头") || it.startsWith("记录片头")) onResetIntro() else onResetOutro()
                    }
                )
            }

            if (resolvedUrl.isNotBlank()) item {
                Text(
                    text = resolvedUrl,
                    modifier = Modifier.fillMaxWidth(),
                    color = TextTertiary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SettingOptionStrip(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onLongSelect: ((String) -> Unit)? = null,
    secondary: String? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayerSettingTitle(title)
            if (!secondary.isNullOrBlank()) {
                Text(
                    text = secondary,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                    color = TextTertiary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End
                )
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = Color.White.copy(alpha = 0.07f)
        ) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(options) { option ->
                    PlayerSettingChip(
                        label = option,
                        selected = option == selected,
                        onClick = { onSelect(option) },
                        onLongClick = onLongSelect?.let { longSelect -> { longSelect(option) } }
                    )
                }
            }
        }
    }
}

@Composable
private fun GestureSeekOverlay(
    deltaMs: Long,
    targetPosition: Long,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color.Black.copy(alpha = 0.72f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = if (deltaMs >= 0) "+${deltaMs / 1000}s" else "${deltaMs / 1000}s",
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = formatDuration(targetPosition),
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun EpisodeSheetContent(
    state: PlayerState,
    onEpisodeClick: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "选集",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = DarkSurfaceVariant
            ) {
                Text(
                    text = state.currentFlag.ifBlank { "默认线路" },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    color = Secondary,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Text(
                text = "共 ${state.episodes.size} 集",
                color = TextTertiary,
                style = MaterialTheme.typography.bodySmall
            )
        }
        HorizontalDivider(color = DarkSurfaceVariant)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 84.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(state.episodes) { index, episode ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (index == state.currentEpisodeIndex) Primary else DarkCard,
                    onClick = { onEpisodeClick(index) }
                ) {
                    Text(
                        text = episode.name.ifBlank { "第${index + 1}集" },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (index == state.currentEpisodeIndex) TextOnPrimary else TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun WatchTogetherDialog(
    defaultServerUrl: String,
    uiState: WatchTogetherUiState,
    onCreateRoom: (String) -> Unit,
    onJoinRoom: (String, String) -> Unit,
    onClearError: () -> Unit,
    onDismiss: () -> Unit
) {
    var roomCode by remember { mutableStateOf("") }
    var serverUrl by remember(defaultServerUrl) { mutableStateOf(defaultServerUrl) }
    var joinMode by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(shape = CircleShape, color = SecondaryMuted) {
                    Icon(
                        imageVector = Icons.Filled.Groups,
                        contentDescription = null,
                        tint = Secondary,
                        modifier = Modifier.padding(8.dp).size(20.dp)
                    )
                }
                Column {
                    Text("一起看", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        text = "创建房间或输入房间号加入",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = {
                        serverUrl = it
                        onClearError()
                    },
                    label = { Text("一起看服务地址") },
                    leadingIcon = {
                        Icon(Icons.Filled.Link, contentDescription = null, tint = TextTertiary)
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = Secondary,
                        unfocusedBorderColor = DarkSurfaceVariant,
                        cursorColor = Secondary
                    ),
                    shape = RoundedCornerShape(14.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TogetherModeChip(
                        label = "创建房间",
                        selected = !joinMode,
                        onClick = {
                            joinMode = false
                            onClearError()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    TogetherModeChip(
                        label = "加入房间",
                        selected = joinMode,
                        onClick = {
                            joinMode = true
                            onClearError()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (joinMode) {
                    OutlinedTextField(
                        value = roomCode,
                        onValueChange = {
                            roomCode = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(6)
                            onClearError()
                        },
                        label = { Text("房间号") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = Secondary,
                            unfocusedBorderColor = DarkSurfaceVariant,
                            cursorColor = Secondary
                        ),
                        shape = RoundedCornerShape(14.dp)
                    )
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.06f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
                    ) {
                        Text(
                            text = "创建后会生成 6 位房间号。复制给朋友后，对方输入同一服务地址和房间号即可加入。",
                            modifier = Modifier.padding(12.dp),
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                uiState.error?.let { error ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF4A1D27)
                    ) {
                        Text(
                            text = error,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            color = Color(0xFFFFB4C4),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (joinMode) {
                        onJoinRoom(serverUrl, roomCode)
                    } else {
                        onCreateRoom(serverUrl)
                    }
                },
                enabled = !uiState.connecting && serverUrl.isNotBlank() && (!joinMode || roomCode.length == 6),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Secondary)
            ) {
                Text(
                    text = if (uiState.connecting) "连接中..." else if (joinMode) "加入" else "创建",
                    color = DarkBackground,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", color = TextTertiary)
            }
        }
    )
}

@Composable
private fun TogetherModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) Secondary else Color.White.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, if (selected) Secondary else Color.White.copy(alpha = 0.08f))
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(vertical = 11.dp),
            color = if (selected) DarkBackground else TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
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

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) {
            return current
        }
        current = current.baseContext
    }
    return null
}

private fun Context.copyPlainText(label: String, text: String) {
    val manager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    manager.setPrimaryClip(ClipData.newPlainText(label, text))
}

private fun Activity.setPlayerFullscreen(fullscreen: Boolean) {
    requestedOrientation = if (fullscreen) {
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    } else {
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    WindowInsetsControllerCompat(window, window.decorView).apply {
        if (fullscreen) {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

