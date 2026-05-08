package com.vunbo.watchtogether.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenSearchDesktop
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.vunbo.watchtogether.data.model.VodInfo
import com.vunbo.watchtogether.data.model.VodSeries
import com.vunbo.watchtogether.data.util.PlayerHelper
import com.vunbo.watchtogether.ui.components.ErrorView
import com.vunbo.watchtogether.ui.components.LoadingIndicator
import com.vunbo.watchtogether.ui.player.EmbeddedPlayerSurface
import com.vunbo.watchtogether.ui.player.PlayerState
import com.vunbo.watchtogether.ui.player.PlayerSurface
import com.vunbo.watchtogether.ui.player.PlayerUiMode
import com.vunbo.watchtogether.ui.player.PlayerViewModel
import com.vunbo.watchtogether.ui.player.WatchTogetherDialog
import com.vunbo.watchtogether.ui.theme.DarkBackground
import com.vunbo.watchtogether.ui.theme.Primary
import com.vunbo.watchtogether.ui.theme.Secondary
import com.vunbo.watchtogether.ui.theme.TextOnPrimary
import com.vunbo.watchtogether.ui.theme.TextPrimary
import com.vunbo.watchtogether.ui.theme.TextSecondary
import com.vunbo.watchtogether.ui.theme.TextTertiary
import com.vunbo.watchtogether.ui.watchtogether.WatchTogetherOverlay
import com.vunbo.watchtogether.ui.watchtogether.WatchTogetherNoticeLevel
import com.vunbo.watchtogether.ui.watchtogether.WatchTogetherNoticeState
import kotlin.math.ceil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    sourceKey: String,
    vodId: String,
    onBack: () -> Unit,
    onQuickSearch: ((String) -> Unit)? = null,
    viewModel: DetailViewModel = viewModel(),
    playerViewModel: PlayerViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val vodInfo by viewModel.vodInfo.collectAsState()
    val selectedFlag by viewModel.selectedFlag.collectAsState()
    val visibleEpisodes by viewModel.visibleEpisodes.collectAsState()
    val episodePages by viewModel.episodePages.collectAsState()
    val selectedEpisodePage by viewModel.selectedEpisodePage.collectAsState()
    val isFavorite by viewModel.isFavorite.collectAsState()
    val playerState by playerViewModel.playerState.collectAsState()
    val showWatchTogether by playerViewModel.showWatchTogether.collectAsState()
    val roomState by playerViewModel.roomState.collectAsState()
    val chatMessages by playerViewModel.chatMessages.collectAsState()
    val watchTogetherUiState by playerViewModel.watchTogetherUiState.collectAsState()
    val watchTogetherNoticeState by playerViewModel.watchTogetherNoticeState.collectAsState()
    val remoteNavigationTarget by playerViewModel.remoteNavigationTarget.collectAsState()
    var fullscreenVisible by rememberSaveable { mutableStateOf(false) }
    var showSettingsSheet by rememberSaveable { mutableStateOf(false) }
    var showExitTogetherDialog by rememberSaveable { mutableStateOf(false) }

    fun requestExitPage() {
        if (playerViewModel.hasActiveTogetherRoom()) {
            showExitTogetherDialog = true
        } else {
            playerViewModel.stopPlaybackAfterLeavingPage()
            onBack()
        }
    }

    LaunchedEffect(sourceKey, vodId) {
        viewModel.loadDetail(sourceKey, vodId)
    }

    LaunchedEffect(remoteNavigationTarget) {
        val target = remoteNavigationTarget ?: return@LaunchedEffect
        if (target.sourceKey == sourceKey && target.vodId == vodId) {
            viewModel.loadDetail(target.sourceKey, target.vodId)
            viewModel.selectFlag(target.playFlag)
            viewModel.markPlayed(target.playFlag, target.playIndex)
            playerViewModel.consumeRemoteNavigationTarget(target)
            playerViewModel.startPlay(target.sourceKey, target.vodId, target.playFlag, target.playIndex)
        }
    }

    LaunchedEffect(
        playerState.currentSourceKey,
        playerState.currentVodId,
        playerState.currentFlag,
        playerState.currentEpisodeIndex
    ) {
        if (
            playerState.currentSourceKey == sourceKey &&
            playerState.currentVodId == vodId &&
            playerState.currentFlag.isNotBlank()
        ) {
            viewModel.syncPlaybackSelection(playerState.currentFlag, playerState.currentEpisodeIndex)
        }
    }

    BackHandler {
        if (fullscreenVisible) {
            fullscreenVisible = false
        } else {
            requestExitPage()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (!playerViewModel.hasActiveTogetherRoom()) {
                playerViewModel.stopPlaybackAfterLeavingPage()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        when (state) {
            DetailState.Loading -> LoadingIndicator()
            is DetailState.Error -> ErrorView(
                message = (state as DetailState.Error).msg,
                onRetry = { viewModel.loadDetail(sourceKey, vodId) }
            )

            DetailState.Success -> {
                val info = vodInfo
                if (info != null) {
                    DetailPlaybackLayout(
                        info = info,
                        sourceKey = sourceKey,
                        vodId = vodId,
                        selectedFlag = selectedFlag,
                        visibleEpisodes = visibleEpisodes,
                        episodePages = episodePages,
                        selectedEpisodePage = selectedEpisodePage,
                        isFavorite = isFavorite,
                        currentEpisodeIndex = viewModel.getCurrentEpisodeIndex(),
                        playerState = playerState,
                        watchTogetherNoticeState = watchTogetherNoticeState,
                        playerViewModel = playerViewModel,
                        suppressInlinePlayer = fullscreenVisible,
                        onBack = ::requestExitPage,
                        onFavoriteClick = viewModel::toggleFavorite,
                        onFlagSelect = viewModel::selectFlag,
                        onEpisodePageSelect = viewModel::selectEpisodePage,
                        onEnterFullscreen = { fullscreenVisible = true },
                        onEpisodeClick = { episode ->
                            val index = viewModel.getEpisodeAbsoluteIndex(episode)
                            viewModel.markPlayed(selectedFlag, index)
                            playerViewModel.startPlay(sourceKey, vodId, selectedFlag, index)
                        },
                        onQuickSearch = {
                            val query = cleanText(info.name, "")
                            if (query.isNotBlank()) {
                                playerViewModel.stopPlayback()
                                onQuickSearch?.invoke(query)
                            }
                        },
                        onTogglePlayer = { playerViewModel.togglePreferredPlayer() },
                        onOpenSettings = { showSettingsSheet = true },
                        onOpenWatchTogether = { playerViewModel.toggleWatchTogether() }
                    )
                }
            }
        }

        if (fullscreenVisible) {
            PlayerSurface(
                modifier = Modifier.fillMaxSize(),
                mode = PlayerUiMode.Fullscreen,
                onBack = { fullscreenVisible = false },
                onRequestFullscreen = {},
                viewModel = playerViewModel,
                forceFullscreen = true
            )
        }

        if (!fullscreenVisible && showWatchTogether && roomState != null) {
            WatchTogetherOverlay(
                roomState = roomState!!,
                messages = chatMessages,
                onSendMessage = { playerViewModel.sendChatMessage(it) },
                onSyncHost = { playerViewModel.requestHostSync() },
                onCollapse = { playerViewModel.dismissWatchTogetherPanel() },
                onLeaveRoom = { playerViewModel.leaveRoom() },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .fillMaxWidth(0.82f)
            )
        }
    }

    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            containerColor = Color(0xFF1C2B3E),
            tonalElevation = 0.dp,
            dragHandle = null
        ) {
            DetailSettingsSheet(
                playerState = playerState,
                onSelectPlayerType = { playerViewModel.setPreferredPlayerType(it) },
                onSelectScale = { playerViewModel.setScaleType(it) },
                onSelectSpeed = { playerViewModel.setPlaybackSpeed(it) },
                onMarkIntro = { playerViewModel.markIntroPosition() },
                onMarkOutro = { playerViewModel.markOutroPosition() },
                onResetIntro = { playerViewModel.resetIntroPosition() },
                onResetOutro = { playerViewModel.resetOutroPosition() },
                onReplay = { playerViewModel.replayPlayback() },
                onRefresh = { playerViewModel.refreshPlayback() }
            )
        }
    }

    if (!fullscreenVisible && showWatchTogether && roomState == null) {
        WatchTogetherDialog(
            defaultServerUrl = playerViewModel.getDefaultTogetherServerUrl(),
            uiState = watchTogetherUiState,
            onCreateRoom = { serverUrl -> playerViewModel.createRoom(serverUrl) },
            onJoinRoom = { serverUrl, code -> playerViewModel.joinRoom(serverUrl, code) },
            onClearError = { playerViewModel.clearTogetherError() },
            onDismiss = { playerViewModel.dismissWatchTogetherPanel() }
        )
    }

    if (showExitTogetherDialog) {
        ExitTogetherConfirmDialog(
            isHost = playerViewModel.isTogetherHost(),
            onDismiss = { showExitTogetherDialog = false },
            onConfirmExit = {
                showExitTogetherDialog = false
                playerViewModel.leaveRoom()
                playerViewModel.stopPlayback()
                onBack()
            }
        )
    }
}

@Composable
private fun ExitTogetherConfirmDialog(
    isHost: Boolean,
    onDismiss: () -> Unit,
    onConfirmExit: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C2B3E),
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

@Composable
private fun DetailPlaybackLayout(
    info: VodInfo,
    sourceKey: String,
    vodId: String,
    selectedFlag: String,
    visibleEpisodes: List<VodSeries>,
    episodePages: List<EpisodePage>,
    selectedEpisodePage: Int,
    isFavorite: Boolean,
    currentEpisodeIndex: Int,
    playerState: PlayerState,
    watchTogetherNoticeState: WatchTogetherNoticeState,
    playerViewModel: PlayerViewModel,
    suppressInlinePlayer: Boolean,
    onBack: () -> Unit,
    onFavoriteClick: () -> Unit,
    onFlagSelect: (String) -> Unit,
    onEpisodePageSelect: (Int) -> Unit,
    onEnterFullscreen: () -> Unit,
    onEpisodeClick: (VodSeries) -> Unit,
    onQuickSearch: () -> Unit,
    onTogglePlayer: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWatchTogether: () -> Unit
) {
    val shouldShowPlayer = !suppressInlinePlayer &&
        playerState.hasActiveMedia &&
        playerState.currentSourceKey == sourceKey &&
        playerState.currentVodId == vodId
    val firstPlayableFlag = remember(info.seriesFlags, info.seriesMap) {
        info.seriesFlags.firstOrNull()?.name ?: info.seriesMap.keys.firstOrNull().orEmpty()
    }
    val firstPlayableEpisode = remember(firstPlayableFlag, info.seriesMap) {
        info.seriesMap[firstPlayableFlag]?.firstOrNull()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (shouldShowPlayer && selectedFlag.isNotBlank()) {
            EmbeddedPlayerSurface(
                sourceKey = sourceKey,
                vodId = vodId,
                playFlag = playerState.currentFlag.ifBlank { selectedFlag },
                playIndex = playerState.currentEpisodeIndex,
                onRequestFullscreen = onEnterFullscreen,
                modifier = Modifier.fillMaxWidth(),
                viewModel = playerViewModel
            )
        } else {
            DetailHeroHeader(
                info = info,
                onBack = onBack,
                onPlayClick = {
                    when {
                        visibleEpisodes.isNotEmpty() -> onEpisodeClick(visibleEpisodes.first())
                        firstPlayableEpisode != null -> onEpisodeClick(firstPlayableEpisode)
                    }
                }
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .offset(y = if (shouldShowPlayer) (-6).dp else 0.dp)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF6A6B81),
                            Color(0xFF5C5E73),
                            Color(0xFF404554)
                        )
                    )
                )
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 14.dp, bottom = 26.dp)
            ) {
                item {
                    DetailHeaderPanel(
                        info = info,
                        sourceKey = sourceKey,
                        isFavorite = isFavorite,
                        onFavoriteClick = onFavoriteClick
                    )
                }

                item {
                    DetailToolRow(
                        currentPlayerLabel = if (playerState.currentPlayerType == PlayerHelper.PLAYER_TYPE_IJK) "IJK" else "Exo",
                        onQuickSearch = onQuickSearch,
                        onTogglePlayer = onTogglePlayer,
                        onOpenSettings = onOpenSettings,
                        onOpenWatchTogether = onOpenWatchTogether,
                        watchTogetherNoticeState = watchTogetherNoticeState
                    )
                }

                if (info.seriesFlags.isNotEmpty()) {
                    item { SectionTitle(title = "线路") }
                    item {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(info.seriesFlags) { flag ->
                                val selected = flag.name == selectedFlag
                                Surface(
                                    modifier = Modifier.clickable { onFlagSelect(flag.name) },
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (selected) Color(0xFF4C5165) else Color(0xFF50556A),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (selected) Secondary.copy(alpha = 0.32f) else Color.Transparent
                                    )
                                ) {
                                    Text(
                                        text = cleanText(flag.name, "默认线路"),
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (selected) Secondary else TextPrimary,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }

                if (episodePages.isNotEmpty()) {
                    item { SectionTitle(title = "选集") }
                    item {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(episodePages.size) { index ->
                                val page = episodePages[index]
                                EpisodePageTab(
                                    text = page.label,
                                    selected = index == selectedEpisodePage,
                                    onClick = { onEpisodePageSelect(index) }
                                )
                            }
                        }
                    }
                }

                if (visibleEpisodes.isNotEmpty()) {
                    item {
                        EpisodeGridSection(
                            episodes = visibleEpisodes,
                            selectedFlag = selectedFlag,
                            info = info,
                            currentEpisodeIndex = currentEpisodeIndex,
                            onEpisodeClick = onEpisodeClick
                        )
                    }
                }

                item {
                    DetailSynopsisSection(text = sanitizeDetailDescription(info.des))
                }
            }
        }
    }
}

@Composable
private fun DetailHeroHeader(
    info: VodInfo,
    onBack: () -> Unit,
    onPlayClick: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
    ) {
        val heroHeight = (maxWidth * 0.60f).coerceIn(220.dp, 320.dp)
        val headerPadding = (maxWidth * 0.034f).coerceIn(10.dp, 18.dp)
        val playButtonSize = (maxWidth * 0.18f).coerceIn(62.dp, 82.dp)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heroHeight)
        ) {
            AsyncImage(
                model = info.pic,
                contentDescription = info.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.38f))
            )

            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = headerPadding, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onBack,
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.34f)
                ) {
                    Box(
                        modifier = Modifier.size((playButtonSize * 0.48f).coerceIn(42.dp, 48.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = TextPrimary
                        )
                    }
                }
            }

            Button(
                onClick = onPlayClick,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(playButtonSize),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Primary.copy(alpha = 0.92f)),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "播放",
                    tint = TextOnPrimary,
                    modifier = Modifier.size((playButtonSize * 0.42f).coerceIn(24.dp, 34.dp))
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailHeaderPanel(
    info: VodInfo,
    sourceKey: String,
    isFavorite: Boolean,
    onFavoriteClick: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
    ) {
        val outerGap = (maxWidth * 0.022f).coerceIn(7.dp, 9.dp)
        val posterWidth = (maxWidth * 0.225f).coerceIn(80.dp, 94.dp)
        val actionWidth = (maxWidth * 0.278f).coerceIn(102.dp, 128.dp)

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(outerGap),
                verticalAlignment = Alignment.Top
            ) {
                AsyncImage(
                    model = info.pic,
                    contentDescription = info.name,
                    modifier = Modifier
                        .width(posterWidth)
                        .aspectRatio(0.72f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF4A5064)),
                    contentScale = ContentScale.Crop
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = cleanText(info.name, "未命名资源"),
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        buildPrimaryMetaLines(info).forEach { line ->
                            Text(
                                text = line,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DetailHighlightPill(
                            text = cleanText(sourceKey, "站点"),
                            emphasized = false
                        )
                        Text(
                            text = cleanText(info.note, "热播影视"),
                            style = MaterialTheme.typography.labelSmall,
                            color = Secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Text(
                        text = sanitizeDetailDescription(info.des),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Column(
                    modifier = Modifier.widthIn(max = actionWidth),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LargeStatusBlock(
                        primary = cleanText(info.note, "热播"),
                        secondary = "更新状态"
                    )
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onFavoriteClick),
                        shape = RoundedCornerShape(16.dp),
                        color = if (isFavorite) Color(0xFFF0CF62) else Color(0xFF33384D),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isFavorite) Color.Transparent else Color.White.copy(alpha = 0.08f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = null,
                                tint = if (isFavorite) DarkBackground else TextPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isFavorite) "已收藏" else "加入收藏",
                                color = if (isFavorite) DarkBackground else TextPrimary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                buildTagItems(info).forEach { meta ->
                    DetailTag(text = meta)
                }
            }
        }
    }
}

@Composable
private fun DetailHighlightPill(text: String, emphasized: Boolean) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (emphasized) Color.Black.copy(alpha = 0.36f) else Color.White.copy(alpha = 0.12f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            color = if (emphasized) Secondary else TextPrimary,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun LargeStatusBlock(primary: String, secondary: String) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = primary,
            color = TextPrimary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = secondary,
            color = TextTertiary,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun DetailToolRow(
    currentPlayerLabel: String,
    onQuickSearch: () -> Unit,
    onTogglePlayer: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWatchTogether: () -> Unit,
    watchTogetherNoticeState: WatchTogetherNoticeState
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
    ) {
        val horizontalPadding = (maxWidth * 0.046f).coerceIn(16.dp, 20.dp)
        val itemGap = (maxWidth * 0.012f).coerceIn(5.dp, 8.dp)
        val iconBoxSize = (maxWidth * 0.102f).coerceIn(42.dp, 50.dp)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(itemGap)
        ) {
            val items = listOf(
                DetailToolSpec(Icons.Filled.Download, "下载", {}),
                DetailToolSpec(Icons.Filled.ScreenSearchDesktop, "快搜", onQuickSearch),
                DetailToolSpec(Icons.Filled.HighQuality, currentPlayerLabel, onTogglePlayer),
                DetailToolSpec(Icons.Filled.Groups, "一起看", onOpenWatchTogether),
                DetailToolSpec(Icons.Filled.Settings, "设置", onOpenSettings)
            )
            items.forEach { item ->
                DetailToolItem(
                    icon = item.icon,
                    label = item.label,
                    iconBoxSize = iconBoxSize,
                    onClick = item.onClick,
                    badgeCount = if (item.label == "一起看") watchTogetherNoticeState.unreadCount else 0,
                    warningBadge = item.label == "一起看" && watchTogetherNoticeState.level == WatchTogetherNoticeLevel.Warning,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private data class DetailToolSpec(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit
)

@Composable
private fun DetailToolItem(
    icon: ImageVector,
    label: String,
    iconBoxSize: Dp,
    onClick: () -> Unit,
    badgeCount: Int = 0,
    warningBadge: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            modifier = Modifier.size(iconBoxSize),
            shape = RoundedCornerShape(iconBoxSize * 0.24f),
            color = Color(0xFF2A2E45)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = TextPrimary,
                    modifier = Modifier.size((iconBoxSize * 0.34f).coerceIn(17.dp, 20.dp))
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
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextPrimary,
            maxLines = 1
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
        style = MaterialTheme.typography.titleSmall,
        color = TextPrimary,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun EpisodePageTab(text: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = text,
            color = if (selected) Color(0xFFFFD166) else TextPrimary,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
        Box(
            modifier = Modifier
                .width(if (selected) 34.dp else 0.dp)
                .height(2.dp)
                .background(if (selected) Color(0xFFFFD166) else Color.Transparent)
        )
    }
}

@Composable
private fun EpisodeGridSection(
    episodes: List<VodSeries>,
    selectedFlag: String,
    info: VodInfo,
    currentEpisodeIndex: Int,
    onEpisodeClick: (VodSeries) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        val columns = 4
        val spacing = 6.dp
        val itemHeight = (maxWidth * 0.120f).coerceIn(38.dp, 44.dp)
        val rows = ceil(episodes.size / columns.toFloat()).toInt().coerceAtLeast(1)
        val gridHeight = itemHeight * rows + spacing * (rows - 1)

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = gridHeight, max = gridHeight),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
            userScrollEnabled = false
        ) {
            itemsIndexed(episodes) { _, episode ->
                val selected = currentEpisodeIndex == info.seriesMap[selectedFlag]
                    ?.indexOfFirst { it.name == episode.name && it.url == episode.url }
                EpisodeChip(
                    episode = episode,
                    selected = selected == true,
                    itemHeight = itemHeight,
                    onClick = { onEpisodeClick(episode) }
                )
            }
        }
    }
}

@Composable
private fun DetailSynopsisSection(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "简介",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0x33212634)
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary.copy(alpha = 0.88f)
            )
        }
    }
}

@Composable
private fun DetailTag(text: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color(0x33212634)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = TextPrimary
        )
    }
}

@Composable
private fun EpisodeChip(
    episode: VodSeries,
    selected: Boolean,
    itemHeight: Dp,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(itemHeight)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) Color(0xFF3B4052) else Color(0xFF51566B),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) Color(0xFFFFD166).copy(alpha = 0.32f) else Color.Transparent
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = cleanText(episode.name, "播放"),
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) Color(0xFFFFD166) else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DetailSettingsSheet(
    playerState: PlayerState,
    onSelectPlayerType: (Int) -> Unit,
    onSelectScale: (Int) -> Unit,
    onSelectSpeed: (Float) -> Unit,
    onMarkIntro: () -> Unit,
    onMarkOutro: () -> Unit,
    onResetIntro: () -> Unit,
    onResetOutro: () -> Unit,
    onReplay: () -> Unit,
    onRefresh: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 520.dp)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 18.dp)
    ) {
        item {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color.White.copy(alpha = 0.28f)
                ) {
                    Box(
                        modifier = Modifier
                            .width(48.dp)
                            .height(5.dp)
                    )
                }
            }
        }
        item {
            Text(
                text = "播放器",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
        }
        item {
            DetailSettingOptionRow(
                options = listOf("系统", "IJK硬解", "IJK软解", "EXO"),
                selected = when (playerState.currentPlayerType) {
                    PlayerHelper.PLAYER_TYPE_SYSTEM -> "系统"
                    PlayerHelper.PLAYER_TYPE_IJK -> "IJK硬解"
                    else -> "EXO"
                },
                onSelect = {
                    when (it) {
                        "系统" -> onSelectPlayerType(PlayerHelper.PLAYER_TYPE_SYSTEM)
                        "IJK硬解", "IJK软解" -> onSelectPlayerType(PlayerHelper.PLAYER_TYPE_IJK)
                        else -> onSelectPlayerType(PlayerHelper.PLAYER_TYPE_EXO)
                    }
                }
            )
        }
        item { DetailSettingSectionTitle("画面缩放") }
        item {
            DetailSettingOptionRow(
                options = listOf("默认", "16:9", "4:3", "填充", "原始", "裁剪"),
                selected = PlayerHelper.getScaleDisplay(playerState.currentScaleType),
                onSelect = { label ->
                    PlayerHelper.scaleNames.indexOf(label).takeIf { it >= 0 }?.let(onSelectScale)
                }
            )
        }
        item { DetailSettingSectionTitle("倍速播放") }
        item {
            DetailSettingOptionRow(
                options = listOf("0.5", "0.75", "1.0", "1.25", "1.5", "1.75", "2.0", "3.0"),
                selected = String.format("%.2f", playerState.speed).trimEnd('0').trimEnd('.'),
                onSelect = { onSelectSpeed(it.toFloat()) }
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DetailSettingActionChip(
                    label = if (playerState.skipIntroPosition > 0L) "片头 ${formatDurationForDetail(playerState.skipIntroPosition)}" else "片头",
                    onClick = onMarkIntro,
                    onLongClick = onResetIntro,
                    modifier = Modifier.weight(1f)
                )
                DetailSettingActionChip(
                    label = if (playerState.skipOutroPosition > 0L) "片尾 ${formatDurationForDetail(playerState.skipOutroPosition)}" else "片尾",
                    onClick = onMarkOutro,
                    onLongClick = onResetOutro,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DetailSettingActionChip("重播", onReplay, modifier = Modifier.weight(1f))
                DetailSettingActionChip("刷新", onRefresh, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DetailSettingSectionTitle(text: String
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = TextPrimary,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun DetailSettingOptionRow(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.12f)
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(options) { option ->
                Surface(
                    modifier = Modifier.clickable { onSelect(option) },
                    shape = RoundedCornerShape(999.dp),
                    color = if (option == selected) Color.White.copy(alpha = 0.18f) else Color.Transparent
                ) {
                    Text(
                        text = option,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        color = if (option == selected) Color(0xFFFFC95B) else TextPrimary,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (option == selected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailSettingActionChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier
            .height(54.dp)
            .pointerInput(onLongClick) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick?.invoke() }
                )
            },
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.12f)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = TextPrimary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

private fun buildPrimaryMetaLines(info: VodInfo): List<String> {
    return listOf(
        "地区: ${cleanText(info.area, "未知")}",
        "年份: ${cleanText(info.year, "未知")}",
        "导演: ${cleanText(info.director, "暂无")}",
        "演员: ${cleanText(info.actor, "暂无")}"
    )
}

private fun buildTagItems(info: VodInfo): List<String> {
    return listOf(
        "类型: ${cleanText(info.type, "暂无")}",
        "语言: ${cleanText(info.lang, "暂无")}",
        "地区: ${cleanText(info.area, "未知")}",
        "年份: ${cleanText(info.year, "未知")}"
    )
}

private fun sanitizeDetailDescription(value: String?): String {
    val cleaned = cleanText(value, "暂无简介")
        .replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .replace(Regex("\\s+"), " ")
        .trim()
    return if (cleaned.isBlank()) "暂无简介" else cleaned
}

private fun formatDurationForDetail(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

private fun cleanText(value: String?, fallback: String): String {
    val text = value
        ?.replace("\u0000", "")
        ?.replace("\r", " ")
        ?.replace("\n", " ")
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        .orEmpty()
    return if (text.isBlank()) fallback else text
}
