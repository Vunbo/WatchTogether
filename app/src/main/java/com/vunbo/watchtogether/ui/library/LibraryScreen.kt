package com.vunbo.watchtogether.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.draw.clip
import com.vunbo.watchtogether.data.local.VodCollect
import com.vunbo.watchtogether.data.model.VodInfo
import com.vunbo.watchtogether.ui.theme.*
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onVideoClick: (sourceKey: String, vodId: String) -> Unit,
    viewModel: LibraryViewModel = viewModel()
) {
    val selectedTab by viewModel.selectedTab.collectAsState()
    val history by viewModel.history.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val deleteMode by viewModel.deleteMode.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadData()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        TopAppBar(
            modifier = Modifier.padding(top = 8.dp),
            windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            title = {
                Text(
                    text = "历史/收藏",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            actions = {
                if (deleteMode) {
                    TextButton(onClick = { viewModel.toggleDeleteMode() }) {
                        Text("完成", color = Secondary)
                    }
                    TextButton(onClick = { viewModel.clearAll() }) {
                        Text("清空", color = Tertiary)
                    }
                } else {
                    TextButton(onClick = { viewModel.toggleDeleteMode() }) {
                        Text("编辑", color = Secondary)
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
        )

        // Tab row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            listOf("播放历史" to 0, "我的收藏" to 1).forEach { (label, index) ->
                val selected = selectedTab == index
                TextButton(onClick = { viewModel.selectTab(index) }) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (selected) Secondary else TextTertiary,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }

        // Content
        when (selectedTab) {
            0 -> HistoryList(
                history = history,
                deleteMode = deleteMode,
                onItemClick = { vodInfo ->
                    if (deleteMode) {
                        viewModel.deleteHistory(vodInfo)
                    } else {
                        vodInfo.id?.let { id ->
                            onVideoClick(vodInfo.sourceKey ?: "", id)
                        }
                    }
                }
            )
            1 -> FavoritesList(
                favorites = favorites,
                deleteMode = deleteMode,
                onItemClick = { collect ->
                    if (deleteMode) {
                        viewModel.deleteFavorite(collect)
                    } else {
                        onVideoClick(collect.sourceKey, collect.vodId)
                    }
                }
            )
        }
    }
}

@Composable
fun HistoryList(
    history: List<VodInfo>,
    deleteMode: Boolean,
    onItemClick: (VodInfo) -> Unit
) {
    if (history.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无播放历史", color = TextTertiary)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(history) { vodInfo ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onItemClick(vodInfo) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = vodInfo.pic,
                        contentDescription = null,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkSurfaceVariant)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = vodInfo.name ?: "未知",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        vodInfo.playNote?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall,
                                color = Secondary
                            )
                        }
                    }
                    if (deleteMode) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "删除",
                            tint = Tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FavoritesList(
    favorites: List<VodCollect>,
    deleteMode: Boolean,
    onItemClick: (VodCollect) -> Unit
) {
    if (favorites.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无收藏", color = TextTertiary)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(favorites) { collect ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onItemClick(collect) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = collect.pic,
                        contentDescription = null,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkSurfaceVariant)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = collect.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (deleteMode) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "删除",
                            tint = Tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
