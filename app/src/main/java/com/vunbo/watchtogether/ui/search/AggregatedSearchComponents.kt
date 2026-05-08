package com.vunbo.watchtogether.ui.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vunbo.watchtogether.data.model.Movie
import com.vunbo.watchtogether.ui.theme.DarkBackground
import com.vunbo.watchtogether.ui.theme.Secondary
import com.vunbo.watchtogether.ui.theme.TextPrimary
import com.vunbo.watchtogether.ui.theme.TextSecondary
import com.vunbo.watchtogether.ui.theme.TextTertiary

@Composable
fun AggregatedSearchContent(
    results: List<Movie.Video>,
    sourceGroups: List<SearchSourceGroup>,
    selectedSourceKey: String,
    onSourceSelect: (String) -> Unit,
    onVideoClick: (sourceKey: String, vodId: String) -> Unit,
    statusText: String? = null,
    totalSourceCount: Int = sourceGroups.size,
    query: String = "",
    emptyMessage: String = "当前没有资源",
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
    ) {
        val gap = (maxWidth * 0.014f).coerceIn(6.dp, 9.dp)
        val sidebarWidth = (maxWidth * 0.365f).coerceIn(128.dp, 190.dp)

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(gap)
        ) {
            AggregatedSearchSidebar(
                sourceGroups = sourceGroups,
                selectedSourceKey = selectedSourceKey,
                totalResultCount = sourceGroups.count { it.results.isNotEmpty() },
                totalSourceCount = totalSourceCount,
                onSourceSelect = onSourceSelect,
                statusText = statusText,
                modifier = Modifier.width(sidebarWidth)
            )
            AggregatedResultList(
                results = results,
                query = query,
                onVideoClick = onVideoClick,
                emptyMessage = emptyMessage,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AggregatedSearchSidebar(
    sourceGroups: List<SearchSourceGroup>,
    selectedSourceKey: String,
    totalResultCount: Int,
    totalSourceCount: Int,
    onSourceSelect: (String) -> Unit,
    statusText: String?,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF0F1826),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 7.dp, vertical = 12.dp)
        ) {
            Text(
                text = "站点",
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "$totalResultCount / $totalSourceCount",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (!statusText.isNullOrBlank()) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = Secondary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxHeight(),
                contentPadding = PaddingValues(top = 12.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    SidebarSourceRow(
                        label = "全部",
                        count = totalResultCount,
                        selected = selectedSourceKey.isBlank(),
                        onClick = { onSourceSelect("") }
                    )
                }
                itemsIndexed(sourceGroups, key = { index, group -> "${index}_${group.sourceKey}" }) { _, group ->
                    SidebarSourceRow(
                        label = group.sourceName,
                        count = group.results.size,
                        selected = group.sourceKey == selectedSourceKey,
                        onClick = { onSourceSelect(group.sourceKey) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SidebarSourceRow(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) Color(0xFF1B2A42) else Color(0xFF141D2A),
        border = BorderStroke(
            1.dp,
            if (selected) Secondary.copy(alpha = 0.28f) else Color.Transparent
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = normalizeSourceLabel(label),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) TextPrimary else TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) Secondary else TextTertiary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AggregatedResultList(
    results: List<Movie.Video>,
    query: String,
    onVideoClick: (sourceKey: String, vodId: String) -> Unit,
    emptyMessage: String,
    modifier: Modifier = Modifier
) {
    if (results.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxHeight()
                .background(DarkBackground),
            contentAlignment = Alignment.Center
        ) {
            Text(emptyMessage, color = TextTertiary)
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxHeight(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(
            results,
            key = { index, video ->
                "${index}_${video.sourceKey.orEmpty()}_${video.id.orEmpty()}_${video.name.orEmpty()}"
            }
        ) { _, video ->
            AggregatedResultCard(
                video = video,
                query = query,
                onClick = {
                    val sourceKey = video.sourceKey.orEmpty()
                    val vodId = video.id.orEmpty()
                    if (sourceKey.isNotBlank() && vodId.isNotBlank()) {
                        onVideoClick(sourceKey, vodId)
                    }
                }
            )
        }
    }
}

@Composable
fun AggregatedResultCard(
    video: Movie.Video,
    query: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF0F1826),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val cardPadding = (maxWidth * 0.028f).coerceIn(9.dp, 12.dp)
            val posterWidth = (maxWidth * 0.172f).coerceIn(68.dp, 80.dp)
            val contentGap = (maxWidth * 0.022f).coerceIn(8.dp, 10.dp)
            val badgeHeight = (maxWidth * 0.060f).coerceIn(22.dp, 26.dp)
            val badgeMaxWidth = (maxWidth * 0.27f).coerceIn(72.dp, 92.dp)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(cardPadding),
                horizontalArrangement = Arrangement.spacedBy(contentGap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = video.pic,
                    contentDescription = video.name,
                    modifier = Modifier
                        .width(posterWidth)
                        .aspectRatio(0.72f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1C2332)),
                    contentScale = ContentScale.Crop
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 92.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = video.name.orEmpty().ifBlank { query.ifBlank { "未命名资源" } },
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.SemiBold
                        )

                        SourceBadge(
                            text = video.sourceKey.orEmpty(),
                            height = badgeHeight,
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .widthIn(max = badgeMaxWidth)
                        )
                    }

                    Text(
                        text = video.note?.takeIf { it.isNotBlank() } ?: "暂无更新信息",
                        style = MaterialTheme.typography.labelSmall,
                        color = Secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = buildMeta(video),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = buildFooter(video),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceBadge(
    text: String,
    height: Dp,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(height / 2),
        color = Color(0xFF1B2434),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Box(
            modifier = Modifier
                .height(height)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = normalizeSourceLabel(text).ifBlank { "站点" },
                style = MaterialTheme.typography.labelSmall,
                color = Secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun buildMeta(video: Movie.Video): String {
    return listOfNotNull(
        video.type?.takeIf { it.isNotBlank() },
        video.year?.takeIf { it.isNotBlank() },
        video.area?.takeIf { it.isNotBlank() },
        video.lang?.takeIf { it.isNotBlank() }
    ).joinToString("  ").ifBlank { "影视资源" }
}

private fun buildFooter(video: Movie.Video): String {
    return listOfNotNull(
        video.actor?.takeIf { it.isNotBlank() }?.take(18),
        video.director?.takeIf { it.isNotBlank() }?.take(14)
    ).joinToString(" / ").ifBlank { "点击查看详情" }
}

private fun normalizeSourceLabel(value: String): String {
    val cleaned = value
        .replace(Regex("^[^\\p{IsHan}\\p{L}\\p{N}]+"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
    return cleaned
        .removePrefix("csp_")
        .removePrefix("CSP_")
        .removePrefix("spider_")
        .removePrefix("Spider_")
        .ifBlank { value.trim() }
}
