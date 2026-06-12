package com.vunbo.watchtogether.feature.live

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.vunbo.watchtogether.data.model.Epginfo
import com.vunbo.watchtogether.data.model.LiveSource
import com.vunbo.watchtogether.data.live.LiveEpgResolver
import com.vunbo.watchtogether.core.event.AppEvent
import com.vunbo.watchtogether.core.event.AppEventBus
import com.vunbo.watchtogether.core.player.PlayerHelper
import com.vunbo.watchtogether.feature.live.model.LiveChannelRef
import com.vunbo.watchtogether.feature.live.model.LiveDisplayGroup
import com.vunbo.watchtogether.feature.live.model.LivePlaybackMode
import com.vunbo.watchtogether.feature.live.model.LiveUiState
import com.vunbo.watchtogether.ui.theme.DarkBackground
import com.vunbo.watchtogether.ui.theme.DarkCard
import com.vunbo.watchtogether.ui.theme.DarkSurface
import com.vunbo.watchtogether.ui.theme.DarkSurfaceVariant
import com.vunbo.watchtogether.ui.theme.Primary
import com.vunbo.watchtogether.ui.theme.PrimaryMuted
import com.vunbo.watchtogether.ui.theme.Secondary
import com.vunbo.watchtogether.ui.theme.TextPrimary
import com.vunbo.watchtogether.ui.theme.TextSecondary
import com.vunbo.watchtogether.ui.theme.TextTertiary

private val LiveBackground = DarkBackground
private val LivePanel = DarkSurface
private val LivePanelLight = DarkSurfaceVariant
private val LiveItem = DarkCard
private val LiveSelected = PrimaryMuted
private val LiveAccent = Secondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveScreen(viewModel: LiveViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var fullscreen by remember { mutableStateOf(false) }
    var sourceSheetVisible by remember { mutableStateOf(false) }
    var menuSheetVisible by remember { mutableStateOf(false) }
    var epgSheetVisible by remember { mutableStateOf(false) }
    var pageActive by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val activity = context.findActivity()
    val lifecycleOwner = LocalLifecycleOwner.current
    val playerView = remember(viewModel) { createLivePlayerView(context, viewModel) }

    LaunchedEffect(Unit) {
        pageActive = true
        viewModel.setPageVisible(true)
        viewModel.loadData()
    }

    LaunchedEffect(playerView) {
                AppEventBus.events.collect { event ->
            if (event is AppEvent.LivePageExit) {
                pageActive = false
                fullscreen = false
                sourceSheetVisible = false
                menuSheetVisible = false
                epgSheetVisible = false
                hideLivePlayerView(playerView)
                viewModel.setPageVisible(false)
            }
        }
    }

    DisposableEffect(lifecycleOwner, playerView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    pageActive = true
                    viewModel.setPageVisible(true)
                    viewModel.loadData()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    pageActive = false
                    fullscreen = false
                    sourceSheetVisible = false
                    menuSheetVisible = false
                    epgSheetVisible = false
                    hideLivePlayerView(playerView)
                    viewModel.setPageVisible(false)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            hideLivePlayerView(playerView)
            viewModel.setPageVisible(false)
        }
    }

    val keepScreenOn = uiState.currentUrl != null && (uiState.isPlaying || uiState.isBuffering)
    DisposableEffect(activity, keepScreenOn) {
        activity?.setLiveKeepScreenOn(keepScreenOn)
        onDispose {
            if (keepScreenOn) activity?.setLiveKeepScreenOn(false)
        }
    }

    BackHandler(fullscreen) {
        fullscreen = false
    }

    if (fullscreen) {
        LiveFullscreenDialog(
            viewModel = viewModel,
            uiState = uiState,
            playerView = playerView,
            activity = activity,
            onExit = { fullscreen = false }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LiveBackground)
    ) {
        LivePlayerPane(
            viewModel = viewModel,
            uiState = uiState,
            playerView = playerView,
            active = pageActive,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
            fullscreen = false,
            onFullscreen = { fullscreen = true },
            onMenu = { menuSheetVisible = true }
        )

        LiveInfoPane(
            uiState = uiState,
            onProgramClick = { epgSheetVisible = true },
            onSourceClick = { sourceSheetVisible = true },
            onMenuClick = { menuSheetVisible = true },
            onFavoriteClick = viewModel::toggleCurrentFavorite,
            onReturnToLive = viewModel::returnToLive
        )

        LiveChannelMenu(
            uiState = uiState,
            onGroupSelect = viewModel::selectDisplayGroup,
            onChannelSelect = viewModel::selectDisplayChannel,
            onLineSelect = viewModel::selectLine,
            onSearchChange = viewModel::updateSearchQuery,
            onClearSearch = viewModel::clearSearchQuery,
            modifier = Modifier.weight(1f)
        )
    }

    uiState.userMessage?.let { message ->
        LiveToastMessage(text = message)
    }

    if (sourceSheetVisible) {
        LiveSourceSheet(
            sources = uiState.sources,
            selectedIndex = uiState.selectedSourceIndex,
            onSelect = {
                sourceSheetVisible = false
                viewModel.selectLiveSource(it)
            },
            onDismiss = { sourceSheetVisible = false }
        )
    }

    if (menuSheetVisible) {
        LivePlayerMenuSheet(
            uiState = uiState,
            onDismiss = { menuSheetVisible = false },
            onReplay = viewModel::replayCurrent,
            onScaleSelect = viewModel::setScaleType,
            onTimeoutSelect = viewModel::setSwitchTimeout
        )
    }

    if (epgSheetVisible) {
        LiveEpgSheet(
            uiState = uiState,
            onDismiss = { epgSheetVisible = false },
            onCatchup = {
                epgSheetVisible = false
                viewModel.playCatchup(it)
            }
        )
    }
}

@Composable
private fun LivePlayerPane(
    viewModel: LiveViewModel,
    uiState: LiveUiState,
    playerView: PlayerView,
    active: Boolean,
    modifier: Modifier,
    fullscreen: Boolean,
    onFullscreen: () -> Unit,
    onMenu: () -> Unit,
    onChannelPanel: () -> Unit = {}
) {
    var controlsVisible by remember { mutableStateOf(false) }
    var lastClickAt by remember { mutableLongStateOf(0L) }
    val scaleMode = remember(uiState.scaleType) { LiveScaleMode.fromScaleType(uiState.scaleType) }

    key(fullscreen) {
        LaunchedEffect(controlsVisible, fullscreen) {
            if (controlsVisible && fullscreen) {
                kotlinx.coroutines.delay(3000)
                controlsVisible = false
            }
        }
        LaunchedEffect(active) {
            if (!active) hideLivePlayerView(playerView)
        }
        Box(
            modifier = modifier
                .background(Color.Black)
                .clickable {
                    val now = System.currentTimeMillis()
                    if (now - lastClickAt < 280) {
                        viewModel.togglePlay()
                    } else {
                        controlsVisible = !controlsVisible
                    }
                    lastClickAt = now
                },
            contentAlignment = Alignment.Center
        ) {
            if (active) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = {
                        (playerView.parent as? ViewGroup)?.removeView(playerView)
                        playerView.apply {
                            alpha = 1f
                            visibility = View.VISIBLE
                            player = viewModel.exoPlayer
                            useController = false
                            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                            setBackgroundColor(android.graphics.Color.BLACK)
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            applyLiveScaleMode(scaleMode, viewModel.currentVideoAspectRatio())
                        }
                    },
                    update = { playerView ->
                        playerView.alpha = 1f
                        playerView.visibility = View.VISIBLE
                        playerView.player = viewModel.exoPlayer
                        playerView.applyLiveScaleMode(scaleMode, viewModel.currentVideoAspectRatio())
                        playerView.post {
                            playerView.applyLiveScaleMode(scaleMode, viewModel.currentVideoAspectRatio())
                        }
                    }
                )
            } else {
                Box(Modifier.fillMaxSize().background(Color.Black))
            }

            if (uiState.loading || uiState.isBuffering) {
                CircularProgressIndicator(color = Secondary, modifier = Modifier.size(34.dp))
            }

            uiState.error?.let { error ->
                Surface(
                    color = Color.Black.copy(alpha = 0.62f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = error,
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            if (controlsVisible) {
                LivePlayerControls(
                    uiState = uiState,
                    fullscreen = fullscreen,
                    onTogglePlay = {
                        viewModel.togglePlay()
                        if (fullscreen) controlsVisible = false
                    },
                    onReplay = {
                        viewModel.replayCurrent()
                        if (fullscreen) controlsVisible = false
                    },
                    onFullscreen = {
                        controlsVisible = false
                        onFullscreen()
                    },
                    onMenu = {
                        controlsVisible = false
                        onMenu()
                    },
                    onChannelPanel = {
                        controlsVisible = false
                        onChannelPanel()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun LivePlayerControls(
    uiState: LiveUiState,
    fullscreen: Boolean,
    onTogglePlay: () -> Unit,
    onReplay: () -> Unit,
    onFullscreen: () -> Unit,
    onMenu: () -> Unit,
    onChannelPanel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                listOf(
                    Color.Black.copy(alpha = 0.54f),
                    Color.Black.copy(alpha = 0.12f),
                    Color.Black.copy(alpha = 0.62f)
                )
            )
        )
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(if (fullscreen) 20.dp else 10.dp)
                .height(34.dp)
                .background(Color.Black.copy(alpha = 0.42f), RoundedCornerShape(18.dp))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.LiveTv, contentDescription = null, tint = Secondary, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(7.dp))
            Text(
                text = uiState.currentChannel?.name ?: "直播",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(
            onClick = onTogglePlay,
            modifier = Modifier
                .align(Alignment.Center)
                .size(if (fullscreen) 64.dp else 54.dp)
                .background(Color.Black.copy(alpha = 0.38f), CircleShape)
        ) {
            Icon(
                imageVector = if (uiState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(if (fullscreen) 42.dp else 36.dp)
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(if (fullscreen) 22.dp else 9.dp),
            horizontalArrangement = Arrangement.spacedBy(if (fullscreen) 12.dp else 7.dp)
        ) {
            LiveControlButton(Icons.Filled.Refresh, "刷新", onReplay)
            if (fullscreen) {
                LiveControlButton(Icons.Filled.Tune, "换台", onChannelPanel)
                LiveControlButton(Icons.Filled.MoreVert, "菜单", onMenu)
            }
            LiveControlButton(
                imageVector = if (fullscreen) Icons.Filled.CloseFullscreen else Icons.Filled.Fullscreen,
                label = if (fullscreen) "退出全屏" else "全屏",
                onClick = onFullscreen
            )
        }
    }
}

@Composable
private fun LiveControlButton(
    imageVector: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(38.dp)
            .background(Color.Black.copy(alpha = 0.36f), CircleShape)
    ) {
        Icon(imageVector, contentDescription = label, tint = Color.White, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun LiveInfoPane(
    uiState: LiveUiState,
    onProgramClick: () -> Unit,
    onSourceClick: () -> Unit,
    onMenuClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onReturnToLive: () -> Unit
) {
    val channelName = uiState.currentChannel?.name ?: "请选择频道"
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = LiveBackground
    ) {
        Box {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(58.dp),
                    color = Primary.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Tv,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(33.dp)
                        )
                    }
                }
                Spacer(Modifier.width(11.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onProgramClick),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = channelName,
                        color = TextPrimary,
                        fontSize = 17.sp,
                        lineHeight = 21.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    ProgramLine(uiState.currentProgram)
                    ProgramLine(uiState.nextProgram)
                }
                Spacer(Modifier.width(8.dp))
                SourceChip(
                    text = uiState.currentSource?.name ?: "直播源",
                    onClick = onSourceClick
                )
                IconButton(onClick = onFavoriteClick, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = if (uiState.isCurrentFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = if (uiState.isCurrentFavorite) "取消收藏" else "收藏频道",
                        tint = if (uiState.isCurrentFavorite) LiveAccent else TextSecondary
                    )
                }
                IconButton(onClick = onMenuClick, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "播放器菜单", tint = TextSecondary)
                }
            }
            if (uiState.playbackMode == LivePlaybackMode.Catchup) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LiveSmallActionButton(Icons.Filled.LiveTv, "返回直播", onReturnToLive)
                }
            }
        }
    }
}

@Composable
private fun ProgramLine(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .height(1.dp)
                .widthIn(min = 28.dp, max = 54.dp)
                .background(Color.White.copy(alpha = 0.10f))
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = text,
            color = TextSecondary,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SourceChip(text: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .height(30.dp)
            .widthIn(min = 50.dp, max = 84.dp)
            .clip(RoundedCornerShape(15.dp))
            .clickable(onClick = onClick),
        color = LivePanelLight,
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = LiveAccent,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 9.dp)
            )
        }
    }
}

@Composable
private fun LiveChannelMenu(
    uiState: LiveUiState,
    onGroupSelect: (Int) -> Unit,
    onChannelSelect: (Int) -> Unit,
    onLineSelect: (Int) -> Unit,
    onSearchChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (uiState.loading && uiState.sources.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Secondary)
        }
        return
    }

    if (uiState.groups.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = uiState.error ?: "暂无直播源\n请检查订阅 lives 或设置页直播源地址",
                color = TextTertiary,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(LiveBackground)
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        val selectedDisplayChannelIndex = uiState.selectedDisplayChannelIndex
        val showCurrentChannelControls = selectedDisplayChannelIndex >= 0
        LiveSearchBar(
            query = uiState.searchQuery,
            resultCount = uiState.displayChannels.size,
            onQueryChange = onSearchChange,
            onClear = onClearSearch
        )
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.weight(1f)) {
            LiveGroupColumn(
                groups = uiState.displayGroups,
                selectedIndex = uiState.selectedDisplayGroupIndex,
                onSelect = onGroupSelect,
                modifier = Modifier.weight(0.31f)
            )
            LiveChannelColumn(
                channels = uiState.displayChannels,
                selectedIndex = selectedDisplayChannelIndex,
                onSelect = onChannelSelect,
                modifier = Modifier.weight(0.47f)
            )
            LiveLineColumn(
                lineCount = if (showCurrentChannelControls) uiState.currentChannel?.urls?.size ?: 0 else 0,
                selectedIndex = if (showCurrentChannelControls) uiState.selectedLineIndex else -1,
                onSelect = onLineSelect,
                modifier = Modifier.weight(0.22f)
            )
        }
    }
}

@Composable
private fun LiveSearchBar(
    query: String,
    resultCount: Int,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp),
        color = LivePanel,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(17.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = TextPrimary,
                    fontSize = 13.sp,
                    lineHeight = 17.sp
                ),
                cursorBrush = Brush.verticalGradient(listOf(LiveAccent, LiveAccent)),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isBlank()) {
                            Text("搜索频道", color = TextTertiary, fontSize = 12.sp, lineHeight = 16.sp)
                        }
                        innerTextField()
                    }
                }
            )
            if (query.isNotBlank()) {
                Text(
                    text = "$resultCount",
                    color = TextTertiary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(end = 4.dp)
                )
                IconButton(onClick = onClear, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "清空", tint = TextSecondary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun LiveGroupColumn(
    groups: List<LiveDisplayGroup>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(10.dp))
            .background(LivePanel),
        contentPadding = PaddingValues(vertical = 5.dp)
    ) {
        itemsIndexed(groups) { index, group ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(43.dp)
                    .background(if (selected) LivePanel else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 9.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = group.name,
                    color = if (selected) LiveAccent else TextPrimary,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun LiveChannelColumn(
    channels: List<LiveChannelRef>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxHeight()
            .padding(horizontal = 6.dp),
        contentPadding = PaddingValues(vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        itemsIndexed(channels) { index, channel ->
            LiveMenuButton(
                text = channel.channel.name,
                selected = index == selectedIndex,
                onClick = { onSelect(index) }
            )
        }
    }
}

@Composable
private fun LiveLineColumn(
    lineCount: Int,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxHeight(),
        contentPadding = PaddingValues(vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        items(lineCount) { index ->
            LiveMenuButton(
                text = "源${index + 1}",
                selected = index == selectedIndex,
                onClick = { if (index < lineCount) onSelect(index) }
            )
        }
    }
}

@Composable
private fun LiveMenuButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        color = if (selected) LiveSelected else LiveItem,
        shape = RoundedCornerShape(8.dp),
        border = if (selected) BorderStroke(1.dp, LiveAccent.copy(alpha = 0.42f)) else null
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = if (selected) LiveAccent else TextPrimary,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
        }
    }
}

@Composable
private fun LiveToastMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            color = Color.Black.copy(alpha = 0.72f),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                text = text,
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiveSourceSheet(
    sources: List<LiveSource>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = LivePanel,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "切换直播源",
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(sources) { index, source ->
                    val selected = index == selectedIndex
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(index) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) LiveSelected else LiveItem,
                        border = if (selected) BorderStroke(1.dp, LiveAccent.copy(alpha = 0.5f)) else null
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    text = source.name,
                                    color = if (selected) LiveAccent else TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${source.groups.size} 个分组 · " +
                                        source.groups.sumOf { it.channels.size }.let { "$it 个频道" },
                                    color = TextTertiary,
                                    fontSize = 11.sp
                                )
                            }
                            if (selected) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = LiveAccent)
                            }
                        }
                    }
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("收起", color = TextSecondary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiveEpgSheet(
    uiState: LiveUiState,
    onDismiss: () -> Unit,
    onCatchup: (Epginfo) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = LivePanel,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Event, contentDescription = null, tint = LiveAccent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = uiState.currentChannel?.name ?: "节目单",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (uiState.canCatchup) "支持回看" else "当前频道未提供回看",
                        color = TextTertiary,
                        fontSize = 11.sp
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text("收起", color = TextSecondary, fontSize = 12.sp)
                }
            }

            if (uiState.programList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暂无节目单", color = TextTertiary, fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.programList) { program ->
                        LiveProgramRow(
                            program = program,
                            current = uiState.currentProgram.contains(program.title),
                            canCatchup = uiState.canCatchup && LiveEpgResolver.isPast(program),
                            onCatchup = { onCatchup(program) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveProgramRow(
    program: Epginfo,
    current: Boolean,
    canCatchup: Boolean,
    onCatchup: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (current) LiveSelected else LiveItem,
        shape = RoundedCornerShape(10.dp),
        border = if (current) BorderStroke(1.dp, LiveAccent.copy(alpha = 0.45f)) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = program.title,
                    color = if (current) LiveAccent else TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = program.timeRange.ifBlank { "时间未知" },
                    color = TextTertiary,
                    fontSize = 11.sp
                )
            }
            if (canCatchup) {
                Button(
                    onClick = onCatchup,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LiveSelected),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Filled.History, contentDescription = null, tint = LiveAccent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("回看", color = LiveAccent, fontSize = 11.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LivePlayerMenuSheet(
    uiState: LiveUiState,
    onDismiss: () -> Unit,
    onReplay: () -> Unit,
    onScaleSelect: (Int) -> Unit,
    onTimeoutSelect: (Int) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = LivePanel,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "播放器菜单",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                LiveSmallActionButton(Icons.Filled.Refresh, "刷新", onReplay)
            }

            LivePlayerMenuContent(
                uiState = uiState,
                onScaleSelect = onScaleSelect,
                onTimeoutSelect = onTimeoutSelect
            )

        }
    }
}

@Composable
private fun LiveSmallActionButton(
    imageVector: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
        colors = ButtonDefaults.buttonColors(containerColor = LiveSelected),
        shape = RoundedCornerShape(18.dp)
    ) {
        Icon(imageVector, contentDescription = null, modifier = Modifier.size(16.dp), tint = LiveAccent)
        Spacer(Modifier.width(5.dp))
        Text(label, color = TextPrimary, fontSize = 12.sp)
    }
}

@Composable
private fun LiveFullscreenMenuPanel(
    uiState: LiveUiState,
    onDismiss: () -> Unit,
    onReplay: () -> Unit,
    onScaleSelect: (Int) -> Unit,
    onTimeoutSelect: (Int) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.18f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.CenterEnd
    ) {
        Surface(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.46f)
                .clickable(onClick = {}),
            color = LivePanel.copy(alpha = 0.96f),
            shape = RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = null,
                        tint = LiveAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "播放器菜单",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    LiveSmallActionButton(Icons.Filled.Refresh, "刷新", onReplay)
                }

                LivePlayerMenuContent(
                    uiState = uiState,
                    onScaleSelect = onScaleSelect,
                    onTimeoutSelect = onTimeoutSelect
                )
            }
        }
    }
}

@Composable
private fun LivePlayerMenuContent(
    uiState: LiveUiState,
    onScaleSelect: (Int) -> Unit,
    onTimeoutSelect: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        LiveOptionSection(
            title = "播放器",
            options = listOf(
                LiveOption("EXO", PlayerHelper.PLAYER_TYPE_EXO)
            ),
            selectedValue = uiState.playerType,
            onSelect = {}
        )

        LiveOptionSection(
            title = "画面缩放",
            options = PlayerHelper.scaleNames.mapIndexed { index, label -> LiveOption(label, index) },
            selectedValue = uiState.scaleType,
            onSelect = onScaleSelect
        )

        LiveOptionSection(
            title = "超时换源",
            options = listOf(5, 10, 15, 20, 25, 30).map { LiveOption("${it}秒", it) },
            selectedValue = uiState.switchTimeoutSeconds,
            onSelect = onTimeoutSelect
        )
    }
}

@Composable
private fun LiveOptionSection(
    title: String,
    options: List<LiveOption>,
    selectedValue: Int,
    onSelect: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(options) { option ->
                LiveTogglePill(
                    text = option.label,
                    selected = option.value == selectedValue,
                    onClick = { onSelect(option.value) }
                )
            }
        }
    }
}

@Composable
private fun LiveTogglePill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .clickable(onClick = onClick),
        color = if (selected) LiveSelected else LiveItem,
        shape = RoundedCornerShape(17.dp),
        border = if (selected) BorderStroke(1.dp, LiveAccent.copy(alpha = 0.48f)) else BorderStroke(
            1.dp,
            Color.White.copy(alpha = 0.06f)
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = if (selected) LiveAccent else TextSecondary,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 13.dp)
            )
        }
    }
}

@Composable
private fun LiveFullscreenDialog(
    viewModel: LiveViewModel,
    uiState: LiveUiState,
    playerView: PlayerView,
    activity: Activity?,
    onExit: () -> Unit
) {
    var menuSheetVisible by remember { mutableStateOf(false) }
    var channelPanelVisible by remember { mutableStateOf(false) }
    DisposableEffect(activity) {
        activity?.setLiveFullscreen(true)
        onDispose { activity?.setLiveFullscreen(false) }
    }
    LaunchedEffect(activity, menuSheetVisible, channelPanelVisible) {
        activity?.applyLiveImmersiveMode()
    }
    Dialog(
        onDismissRequest = onExit,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        DisposableEffect(dialogWindow) {
            dialogWindow?.applyLiveFullscreenWindow()
            onDispose {}
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            LivePlayerPane(
                viewModel = viewModel,
                uiState = uiState,
                playerView = playerView,
                active = true,
                modifier = Modifier.fillMaxSize(),
                fullscreen = true,
                onFullscreen = onExit,
                onMenu = { menuSheetVisible = true },
                onChannelPanel = { channelPanelVisible = true }
            )
            if (channelPanelVisible) {
                LiveFullscreenChannelPanel(
                    uiState = uiState,
                    onGroupSelect = viewModel::selectDisplayGroup,
                    onChannelSelect = viewModel::selectDisplayChannel,
                    onLineSelect = viewModel::selectLine,
                    onSearchChange = viewModel::updateSearchQuery,
                    onClearSearch = viewModel::clearSearchQuery,
                    onDismiss = { channelPanelVisible = false }
                )
            }
            if (menuSheetVisible) {
                LiveFullscreenMenuPanel(
                    uiState = uiState,
                    onDismiss = { menuSheetVisible = false },
                    onReplay = viewModel::replayCurrent,
                    onScaleSelect = viewModel::setScaleType,
                    onTimeoutSelect = viewModel::setSwitchTimeout
                )
            }
        }
    }
}

@Composable
private fun LiveFullscreenChannelPanel(
    uiState: LiveUiState,
    onGroupSelect: (Int) -> Unit,
    onChannelSelect: (Int) -> Unit,
    onLineSelect: (Int) -> Unit,
    onSearchChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.18f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.CenterEnd
    ) {
        Surface(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.58f)
                .clickable(onClick = {}),
            color = LivePanel.copy(alpha = 0.96f),
            shape = RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = null,
                        tint = LiveAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "换台",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismiss) {
                        Text("收起", color = TextSecondary, fontSize = 12.sp)
                    }
                }

                LiveChannelMenu(
                    uiState = uiState,
                    onGroupSelect = onGroupSelect,
                    onChannelSelect = onChannelSelect,
                    onLineSelect = onLineSelect,
                    onSearchChange = onSearchChange,
                    onClearSearch = onClearSearch,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private data class LiveOption(
    val label: String,
    val value: Int
)

private enum class LiveScaleMode(
    val scaleType: Int,
    val resizeMode: Int,
    val targetAspectRatio: Float? = null
) {
    Default(0, AspectRatioFrameLayout.RESIZE_MODE_FIT),
    Fit16x9(1, AspectRatioFrameLayout.RESIZE_MODE_FIT, 16f / 9f),
    Fit4x3(2, AspectRatioFrameLayout.RESIZE_MODE_FIT, 4f / 3f),
    Fill(3, AspectRatioFrameLayout.RESIZE_MODE_FILL),
    Original(4, AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    Crop(5, AspectRatioFrameLayout.RESIZE_MODE_ZOOM);

    companion object {
        fun fromScaleType(scaleType: Int): LiveScaleMode {
            return entries.firstOrNull { it.scaleType == scaleType } ?: Default
        }
    }
}

private fun PlayerView.applyLiveScaleMode(scaleMode: LiveScaleMode, videoAspectRatio: Float) {
    resizeMode = scaleMode.resizeMode
    findViewById<AspectRatioFrameLayout>(androidx.media3.ui.R.id.exo_content_frame)?.apply {
        resizeMode = scaleMode.resizeMode
        setAspectRatio(scaleMode.targetAspectRatio ?: videoAspectRatio)
    }
}

private fun createLivePlayerView(context: Context, viewModel: LiveViewModel): PlayerView {
    return PlayerView(context).apply {
        player = viewModel.exoPlayer
        useController = false
        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
        setBackgroundColor(android.graphics.Color.BLACK)
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        applyLiveScaleMode(LiveScaleMode.Default, viewModel.currentVideoAspectRatio())
    }
}

private fun hideLivePlayerView(playerView: PlayerView) {
    playerView.alpha = 0f
    playerView.visibility = View.GONE
    playerView.player = null
    (playerView.parent as? ViewGroup)?.removeView(playerView)
}

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

private var previousLiveSystemUiVisibility: Int? = null
private var previousLiveCutoutMode: Int? = null
private var previousLiveFullscreenFlag: Boolean? = null
private var previousLiveNoLimitsFlag: Boolean? = null

@Suppress("DEPRECATION")
private fun Activity.setLiveFullscreen(fullscreen: Boolean) {
    requestedOrientation = if (fullscreen) {
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    } else {
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    if (fullscreen) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (previousLiveSystemUiVisibility == null) {
            previousLiveSystemUiVisibility = window.decorView.systemUiVisibility
        }
        if (previousLiveFullscreenFlag == null || previousLiveNoLimitsFlag == null) {
            val flags = window.attributes.flags
            previousLiveFullscreenFlag = flags and WindowManager.LayoutParams.FLAG_FULLSCREEN != 0
            previousLiveNoLimitsFlag = flags and WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS != 0
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        window.clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val attributes = window.attributes
            if (previousLiveCutoutMode == null) {
                previousLiveCutoutMode = attributes.layoutInDisplayCutoutMode
            }
            attributes.layoutInDisplayCutoutMode = liveCutoutMode()
            window.attributes = attributes
            window.decorView.requestApplyInsets()
        }
        applyLiveImmersiveMode()
    } else {
        if (previousLiveFullscreenFlag == false) window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        if (previousLiveNoLimitsFlag == false) window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        previousLiveFullscreenFlag = null
        previousLiveNoLimitsFlag = null
        previousLiveSystemUiVisibility?.let { visibility ->
            window.decorView.systemUiVisibility = visibility
            previousLiveSystemUiVisibility = null
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            previousLiveCutoutMode?.let { cutoutMode ->
                val attributes = window.attributes
                attributes.layoutInDisplayCutoutMode = cutoutMode
                window.attributes = attributes
                window.decorView.requestApplyInsets()
                previousLiveCutoutMode = null
            }
        }
    }

    if (fullscreen) applyLiveImmersiveMode() else WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
}

@Suppress("DEPRECATION")
private fun Activity.applyLiveImmersiveMode() {
    window.applyLiveImmersiveMode()
}

@Suppress("DEPRECATION")
private fun Window.applyLiveImmersiveMode() {
    WindowCompat.setDecorFitsSystemWindows(this, false)
    decorView.systemUiVisibility =
        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    WindowInsetsControllerCompat(this, decorView).apply {
        hide(WindowInsetsCompat.Type.systemBars())
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
    decorView.requestApplyInsets()
}

private fun Window.applyLiveFullscreenWindow() {
    setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    addFlags(
        WindowManager.LayoutParams.FLAG_FULLSCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
    )
    clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val attributes = attributes
        attributes.layoutInDisplayCutoutMode = liveCutoutMode()
        this.attributes = attributes
    }
    applyLiveImmersiveMode()
    decorView.post {
        applyLiveImmersiveMode()
    }
}

private fun liveCutoutMode(): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    } else {
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }
}

private fun Activity.setLiveKeepScreenOn(keepOn: Boolean) {
    if (keepOn) {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    } else {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

