package com.vunbo.watchtogether.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.vunbo.watchtogether.data.model.Movie
import com.vunbo.watchtogether.data.model.MovieSort
import com.vunbo.watchtogether.data.model.SourceBean
import com.vunbo.watchtogether.ui.components.ErrorView
import com.vunbo.watchtogether.ui.components.LoadingIndicator
import com.vunbo.watchtogether.ui.components.CompactTopHeader
import com.vunbo.watchtogether.ui.components.VideoCard
import com.vunbo.watchtogether.ui.theme.DarkBackground
import com.vunbo.watchtogether.ui.theme.DarkCard
import com.vunbo.watchtogether.ui.theme.DarkSurface
import com.vunbo.watchtogether.ui.theme.DarkSurfaceVariant
import com.vunbo.watchtogether.ui.theme.Secondary
import com.vunbo.watchtogether.ui.theme.TextPrimary
import com.vunbo.watchtogether.ui.theme.TextSecondary
import com.vunbo.watchtogether.ui.theme.TextTertiary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onVideoClick: (query: String) -> Unit,
    onSearchClick: () -> Unit,
    onRankMoreClick: (rankKey: String) -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val videos by viewModel.videos.collectAsState()
    val homeSections by viewModel.homeSections.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val sourceName by viewModel.sourceName.collectAsState()
    val availableSources by viewModel.availableSources.collectAsState()
    var showSourceSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadData()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        CompactTopHeader(
            titleContent = {
                Row(
                    modifier = Modifier.clickable { showSourceSheet = true },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = sourceName.ifEmpty { "WatchTogether" },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "切换站点",
                        tint = TextPrimary
                    )
                }
            },
            actions = {
                IconButton(onClick = onSearchClick) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "搜索",
                        tint = Secondary
                    )
                }
            }
        )

        when (state) {
            HomeState.Loading -> LoadingIndicator()
            is HomeState.Error -> ErrorView(
                message = (state as HomeState.Error).msg,
                onRetry = { viewModel.loadData() }
            )
            HomeState.Success -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        CategoryRow(
                            categories = categories,
                            selectedId = selectedCategory,
                            onSelect = { viewModel.selectCategory(it) }
                        )
                    }

                    if (selectedCategory == HOME_TAB_ID && homeSections.isNotEmpty()) {
                        itemsIndexed(homeSections, key = { index, section -> "${section.key}_$index" }) { _, section ->
                            HomeRankSection(
                                section = section,
                                onMoreClick = { onRankMoreClick(section.key) },
                                onVideoClick = onVideoClick
                            )
                        }
                    } else {
                        items(videos.chunked(3)) { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                row.forEach { video ->
                                    VideoCard(
                                        video = video,
                                        onClick = {
                                            val query = video.name?.trim().orEmpty()
                                            if (query.isNotBlank()) {
                                                onVideoClick(query)
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                repeat(3 - row.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSourceSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSourceSheet = false },
            containerColor = DarkSurface,
            tonalElevation = 0.dp,
            dragHandle = null
        ) {
            SourceSelectorSheet(
                currentSourceName = sourceName,
                sources = availableSources,
                onSelect = { source ->
                    showSourceSheet = false
                    viewModel.switchSource(source.key)
                }
            )
        }
    }
}

@Composable
fun CategoryRow(
    categories: List<MovieSort.SortData>,
    selectedId: String,
    onSelect: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(categories) { cat ->
            val selected = cat.id == selectedId
            Surface(
                modifier = Modifier.clickable { onSelect(cat.id) },
                shape = RoundedCornerShape(18.dp),
                color = if (selected) Secondary else DarkSurfaceVariant
            ) {
                Text(
                    text = cat.name,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) Color(0xFF0D0D0D) else TextSecondary,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun HomeRankSection(
    section: HomeSection,
    onMoreClick: () -> Unit,
    onVideoClick: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = section.title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "更多 >",
                modifier = Modifier.clickable(onClick = onMoreClick),
                style = MaterialTheme.typography.bodyMedium,
                color = TextTertiary,
                fontWeight = FontWeight.SemiBold
            )
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val itemSpacing = 10.dp
            val itemWidth = (maxWidth - itemSpacing * 2) / 3
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(itemSpacing),
                contentPadding = PaddingValues(end = 8.dp)
            ) {
                itemsIndexed(section.videos.take(10), key = { index, video -> "${section.key}_${video.name}_$index" }) { _, video ->
                    if (section.key == "douban") {
                        HomePosterCard(
                            video = video,
                            itemWidth = itemWidth,
                            onClick = { video.name?.trim()?.takeIf { it.isNotBlank() }?.let(onVideoClick) }
                        )
                    } else {
                        HomeRankCard(
                            video = video,
                            itemWidth = itemWidth,
                            onClick = { video.name?.trim()?.takeIf { it.isNotBlank() }?.let(onVideoClick) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomePosterCard(
    video: Movie.Video,
    itemWidth: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .width(itemWidth)
            .aspectRatio(0.72f)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .background(DarkCard)
    ) {
        AsyncImage(
            model = remember(video.pic) {
                ImageRequest.Builder(context)
                    .data(video.pic)
                    .setHeader("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
                    .setHeader("Referer", "https://movie.douban.com/")
                    .crossfade(true)
                    .build()
            },
            contentDescription = video.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        video.note?.takeIf { it.isNotBlank() }?.let { note ->
            Text(
                text = note,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
        Text(
            text = video.name.orEmpty(),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.42f))
                .padding(horizontal = 8.dp, vertical = 7.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HomeRankCard(
    video: Movie.Video,
    itemWidth: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(itemWidth)
            .height(itemWidth * 1.36f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF142A45)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        AsyncImage(
            model = video.pic,
            contentDescription = video.name,
            modifier = Modifier
                .fillMaxWidth()
                .height(itemWidth * 0.62f)
                .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)),
            contentScale = ContentScale.Crop
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = video.name.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = video.note.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = video.state.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SourceSelectorSheet(
    currentSourceName: String,
    sources: List<SourceBean>,
    onSelect: (SourceBean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "选择站点",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "切换首页推荐和分类数据源",
            style = MaterialTheme.typography.bodySmall,
            color = TextTertiary,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(sources.chunked(2)) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    row.forEach { source ->
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSelect(source) },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (source.name == currentSourceName) DarkCard else DarkSurfaceVariant
                            )
                        ) {
                            Text(
                                text = source.name.ifBlank { source.key },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (source.name == currentSourceName) Secondary else TextPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (row.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private const val HOME_TAB_ID = "__home_rank__"
