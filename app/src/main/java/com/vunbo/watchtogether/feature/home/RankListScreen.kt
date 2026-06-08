package com.vunbo.watchtogether.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.vunbo.watchtogether.data.model.Movie
import com.vunbo.watchtogether.ui.component.ErrorView
import com.vunbo.watchtogether.ui.component.LoadingIndicator
import com.vunbo.watchtogether.ui.theme.TextPrimary
import com.vunbo.watchtogether.ui.theme.TextSecondary
import com.vunbo.watchtogether.ui.theme.TextTertiary

@Composable
fun RankListScreen(
    onBack: () -> Unit,
    onVideoClick: (String) -> Unit,
    viewModel: RankListViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val videos by viewModel.videos.collectAsState()

    LaunchedEffect(viewModel.rankKey) {
        viewModel.load()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF071A2E))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 36.dp, start = 6.dp, end = 16.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = TextPrimary
                )
            }
            Text(
                text = viewModel.title,
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        }

        when (state) {
            RankListState.Loading -> LoadingIndicator()
            is RankListState.Error -> ErrorView(
                message = (state as RankListState.Error).msg,
                onRetry = { viewModel.load() }
            )
            RankListState.Success -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 14.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp)
                ) {
                    itemsIndexed(videos, key = { index, video -> "${video.name}_$index" }) { _, video ->
                        RankListItem(
                            video = video,
                            onClick = { video.name?.trim()?.takeIf { it.isNotBlank() }?.let(onVideoClick) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RankListItem(
    video: Movie.Video,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        AsyncImage(
            model = video.pic,
            contentDescription = video.name,
            modifier = Modifier
                .width(94.dp)
                .height(134.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF142A45)),
            contentScale = ContentScale.Crop
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .height(134.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = video.name.orEmpty(),
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            video.note?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            video.type?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = "类型：$it",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            video.state?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val intro = video.des?.takeIf { it.isNotBlank() }
            if (intro != null) {
                Text(
                    text = "简介：$intro",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}
