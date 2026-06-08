package com.vunbo.watchtogether.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vunbo.watchtogether.ui.components.ErrorView
import com.vunbo.watchtogether.ui.components.LoadingIndicator
import com.vunbo.watchtogether.ui.theme.DarkBackground
import com.vunbo.watchtogether.ui.theme.DarkCard
import com.vunbo.watchtogether.ui.theme.Secondary
import com.vunbo.watchtogether.ui.theme.TextPrimary
import com.vunbo.watchtogether.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResourcePickerScreen(
    query: String,
    onVideoClick: (sourceKey: String, vodId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: SearchViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val results by viewModel.results.collectAsState()
    val sourceGroups by viewModel.sourceGroups.collectAsState()
    val selectedSourceKey by viewModel.selectedSourceKey.collectAsState()
    val totalSourceCount by viewModel.totalSourceCount.collectAsState()
    val loadingState = state as? SearchState.Loading

    LaunchedEffect(query) {
        if (query.isNotBlank()) {
            viewModel.setQueryAndSearch(query)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "选择资源",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = TextPrimary)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCard)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.TravelExplore,
                    contentDescription = null,
                    tint = Secondary
                )
                Text(
                    text = query,
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "已聚合多个站点结果，先选资源，再进入详情页继续播放。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }

        when (state) {
            SearchState.Idle -> LoadingIndicator()

            is SearchState.Loading -> {
                if (sourceGroups.isNotEmpty() || (loadingState?.completedSources ?: 0) > 0) {
                    AggregatedSearchContent(
                        results = results,
                        sourceGroups = sourceGroups,
                        selectedSourceKey = selectedSourceKey,
                        onSourceSelect = viewModel::selectSource,
                        onVideoClick = onVideoClick,
                        query = query,
                        totalSourceCount = totalSourceCount,
                        statusText = "正在搜索 ${loadingState?.completedSources ?: 0}/$totalSourceCount 个站点",
                        emptyMessage = "正在搜索更多资源..."
                    )
                } else {
                    LoadingIndicator()
                }
            }

            is SearchState.Error -> {
                if (sourceGroups.isNotEmpty()) {
                    AggregatedSearchContent(
                        results = results,
                        sourceGroups = sourceGroups,
                        selectedSourceKey = selectedSourceKey,
                        onSourceSelect = viewModel::selectSource,
                        onVideoClick = onVideoClick,
                        query = query,
                        totalSourceCount = totalSourceCount
                    )
                } else {
                    ErrorView(
                        message = (state as SearchState.Error).msg,
                        onRetry = { viewModel.setQueryAndSearch(query) }
                    )
                }
            }

            SearchState.Success -> {
                AggregatedSearchContent(
                    results = results,
                    sourceGroups = sourceGroups,
                    selectedSourceKey = selectedSourceKey,
                    onSourceSelect = viewModel::selectSource,
                    onVideoClick = onVideoClick,
                    query = query,
                    totalSourceCount = totalSourceCount
                )
            }
        }
    }
}
