package com.vunbo.watchtogether.feature.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vunbo.watchtogether.ui.component.ErrorView
import com.vunbo.watchtogether.ui.component.LoadingIndicator
import com.vunbo.watchtogether.ui.theme.Secondary
import com.vunbo.watchtogether.ui.theme.TextPrimary
import com.vunbo.watchtogether.ui.theme.TextSecondary
import com.vunbo.watchtogether.ui.theme.TextTertiary
import com.vunbo.watchtogether.feature.search.model.SearchSourceOption
import com.vunbo.watchtogether.feature.search.model.SearchState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onVideoClick: (sourceKey: String, vodId: String) -> Unit,
    onBack: () -> Unit,
    initialQuery: String = "",
    viewModel: SearchViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val results by viewModel.results.collectAsState()
    val query by viewModel.query.collectAsState()
    val sourceGroups by viewModel.sourceGroups.collectAsState()
    val selectedSourceKey by viewModel.selectedSourceKey.collectAsState()
    val totalSourceCount by viewModel.totalSourceCount.collectAsState()
    val searchSourceOptions by viewModel.searchSourceOptions.collectAsState()
    val history by viewModel.history.collectAsState()
    val discovery by viewModel.discovery.collectAsState()
    val loadingState = state as? SearchState.Loading

    var showSourceSheet by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank() && initialQuery != query) {
            viewModel.setQueryAndSearch(initialQuery)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07111F))
    ) {
        SearchTopBar(
            query = query,
            onBack = onBack,
            onQueryChange = viewModel::onQueryChanged,
            onSearch = viewModel::search,
            onClearQuery = viewModel::clearQuery,
            onFilterClick = {
                viewModel.ensureSearchSourceOptionsLoaded()
                showSourceSheet = true
            }
        )

        when (state) {
            SearchState.Idle -> {
                if (query.isBlank()) {
                    SearchDiscoveryContent(
                        hotWords = discovery.hotWords,
                        history = history.items,
                        onWordClick = viewModel::setQueryAndSearch,
                        onClearHistory = viewModel::clearHistory
                    )
                } else {
                    SearchSuggestionContent(
                        suggestions = discovery.suggestions,
                        isLoading = discovery.isSuggestionLoading,
                        onSuggestionClick = viewModel::setQueryAndSearch
                    )
                }
            }

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
                        statusText = "搜索 ${loadingState?.completedSources ?: 0}/$totalSourceCount",
                        emptyMessage = "正在扩展搜索结果..."
                    )
                } else {
                    LoadingIndicator()
                }
            }

            is SearchState.Error -> {
                if (query.isNotBlank() && sourceGroups.isNotEmpty()) {
                    AggregatedSearchContent(
                        results = results,
                        sourceGroups = sourceGroups,
                        selectedSourceKey = selectedSourceKey,
                        onSourceSelect = viewModel::selectSource,
                        onVideoClick = onVideoClick,
                        query = query,
                        totalSourceCount = totalSourceCount,
                        emptyMessage = "没有找到相关资源"
                    )
                } else {
                    ErrorView(
                        message = (state as SearchState.Error).msg,
                        onRetry = { viewModel.search() }
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
                    totalSourceCount = totalSourceCount,
                    emptyMessage = "没有找到相关资源"
                )
            }
        }
    }

    if (showSourceSheet) {
        SearchSourceSheet(
            options = searchSourceOptions,
            onDismiss = {
                showSourceSheet = false
                viewModel.applySearchSourceSelectionAndRefresh()
            },
            onSelectAll = viewModel::selectAllSearchSources,
            onClearAll = viewModel::clearAllSearchSources,
            onToggle = viewModel::toggleSearchSource
        )
    }

}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchDiscoveryContent(
    hotWords: List<String>,
    history: List<String>,
    onWordClick: (String) -> Unit,
    onClearHistory: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 22.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp)
    ) {
        item {
            Text(
                text = "最近热搜",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(178.dp),
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF101F35),
                tonalElevation = 0.dp
            ) {
                FlowRow(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    hotWords.take(12).forEach { word ->
                        SearchWordChip(word = word, onClick = { onWordClick(word) })
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0xFFD553F1)
                    ) {
                        Box(
                            modifier = Modifier.size(28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Filled.MusicNote,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                    Text(
                        text = "搜索历史",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (history.isNotEmpty()) {
                    Text(
                        text = "清空",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                        modifier = Modifier.clickable(onClick = onClearHistory)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (history.isEmpty()) {
                Text(
                    text = "暂无搜索历史",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTertiary,
                    modifier = Modifier.padding(start = 2.dp)
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    history.forEach { word ->
                        SearchWordChip(word = word, onClick = { onWordClick(word) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSuggestionContent(
    suggestions: List<String>,
    isLoading: Boolean,
    onSuggestionClick: (String) -> Unit
) {
    if (suggestions.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isLoading) "正在获取搜索建议..." else "暂无搜索建议",
                style = MaterialTheme.typography.bodyMedium,
                color = TextTertiary
            )
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        itemsIndexed(suggestions, key = { index, word -> "${index}_$word" }) { _, word ->
            SearchSuggestionRow(
                word = word,
                onClick = { onSuggestionClick(word) }
            )
        }
    }
}

@Composable
private fun SearchSuggestionRow(
    word: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        color = Color.Transparent,
        onClick = onClick
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = word,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.10f))
            )
        }
    }
}

@Composable
private fun SearchWordChip(
    word: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(7.dp),
        color = Color(0xFF132A44),
        tonalElevation = 0.dp
    ) {
        Text(
            text = word,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SearchTopBar(
    query: String,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClearQuery: () -> Unit,
    onFilterClick: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        val horizontalPadding = (maxWidth * 0.032f).coerceIn(12.dp, 20.dp)
        val barHeight = (maxWidth * 0.118f).coerceIn(50.dp, 62.dp)
        val backSize = (barHeight * 0.78f).coerceIn(38.dp, 46.dp)
        val actionSize = (barHeight * 0.94f).coerceIn(46.dp, 58.dp)
        val itemGap = (maxWidth * 0.02f).coerceIn(8.dp, 12.dp)
        val innerPadding = (barHeight * 0.30f).coerceIn(14.dp, 18.dp)
        val clearSize = (barHeight * 0.50f).coerceIn(26.dp, 32.dp)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(itemGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onBack,
                shape = RoundedCornerShape(backSize / 2),
                color = Color.Transparent
            ) {
                Box(
                    modifier = Modifier.size(backSize),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = TextPrimary,
                        modifier = Modifier.size((backSize * 0.56f).coerceIn(20.dp, 24.dp))
                    )
                }
            }

            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(barHeight / 2),
                color = Color(0xFF14233A),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(barHeight)
                        .padding(start = innerPadding, end = innerPadding),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = TextPrimary,
                            fontWeight = FontWeight.Medium
                        ),
                        cursorBrush = SolidColor(Secondary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                        decorationBox = { innerTextField ->
                            if (query.isBlank()) {
                                Text(
                                    text = "搜索影片、剧集、演员、站点",
                                    color = TextTertiary,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            innerTextField()
                        }
                    )
                    if (query.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            onClick = onClearQuery,
                            shape = RoundedCornerShape(clearSize / 2),
                            color = Color(0xFFC6D0DD),
                            tonalElevation = 0.dp
                        ) {
                            Box(
                                modifier = Modifier.size(clearSize),
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.material3.Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "清空输入",
                                    tint = Color(0xFF263345),
                                    modifier = Modifier.size((clearSize * 0.58f).coerceIn(15.dp, 19.dp))
                                )
                            }
                        }
                    }
                }
            }

            ToolbarIconChip(
                icon = Icons.Filled.FilterList,
                contentDescription = "搜索源筛选",
                size = actionSize,
                onClick = onFilterClick
            )
            ToolbarIconChip(
                icon = Icons.Filled.Search,
                contentDescription = "搜索",
                size = actionSize,
                emphasized = true,
                onClick = onSearch
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchSourceSheet(
    options: List<SearchSourceOption>,
    onDismiss: () -> Unit,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit,
    onToggle: (String) -> Unit
) {
    val configuration = LocalConfiguration.current
    val maxSheetHeight = configuration.screenHeightDp.dp * 0.70f
    val enabledCount = options.count { it.enabled }
    var locateQuery by rememberSaveable { mutableStateOf("") }
    val filteredOptions = options.filter { option ->
        val keyword = locateQuery.trim()
        keyword.isBlank() || option.sourceName.contains(keyword, ignoreCase = true) ||
            option.sourceKey.contains(keyword, ignoreCase = true)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF152338),
        tonalElevation = 0.dp,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
        ) {
            val horizontalPadding = (maxWidth * 0.052f).coerceIn(16.dp, 24.dp)
            val verticalGap = (maxWidth * 0.030f).coerceIn(12.dp, 18.dp)
            val buttonHeight = (maxWidth * 0.135f).coerceIn(48.dp, 56.dp)
            val cardHeight = (maxWidth * 0.162f).coerceIn(58.dp, 72.dp)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding, vertical = 10.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color.White.copy(alpha = 0.18f)
                    ) {
                        Box(
                            modifier = Modifier
                                .width((this@BoxWithConstraints.maxWidth * 0.16f).coerceIn(54.dp, 80.dp))
                                .height(4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(verticalGap))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "指定搜索源",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "已启用 $enabledCount / ${options.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF233750)
                    ) {
                        Box(
                            modifier = Modifier
                                .size((buttonHeight * 0.92f).coerceIn(42.dp, 50.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = null,
                                tint = TextPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(verticalGap))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF1C2A3F),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        BasicTextField(
                            value = locateQuery,
                            onValueChange = { locateQuery = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = TextPrimary,
                                fontWeight = FontWeight.Medium
                            ),
                            cursorBrush = SolidColor(Secondary),
                            decorationBox = { innerTextField ->
                                if (locateQuery.isBlank()) {
                                    Text(
                                        text = "快速定位站点",
                                        color = TextTertiary,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(verticalGap))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SheetActionButton(
                        text = "全选",
                        height = buttonHeight,
                        modifier = Modifier.weight(1f),
                        onClick = onSelectAll
                    )
                    SheetActionButton(
                        text = "清空",
                        height = buttonHeight,
                        modifier = Modifier.weight(1f),
                        onClick = onClearAll
                    )
                }

                Spacer(modifier = Modifier.height(verticalGap))

                if (filteredOptions.isEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        color = Color(0xFF1C2A3F)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "没有匹配到站点",
                                color = TextTertiary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(filteredOptions, key = { index, option -> "${index}_${option.sourceKey}" }) { _, option ->
                            SearchSourceToggleCard(
                                option = option,
                                height = cardHeight,
                                onToggle = { onToggle(option.sourceKey) }
                            )
                        }
                    }
                }

            }
        }
    }
}

@Composable
private fun SheetActionButton(
    text: String,
    height: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.height(height),
        onClick = onClick,
        shape = RoundedCornerShape(height * 0.32f),
        color = Color(0xFF22384F),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SearchSourceToggleCard(
    option: SearchSourceOption,
    height: Dp,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        onClick = onToggle,
        shape = RoundedCornerShape(height * 0.28f),
        color = if (option.enabled) Color(0xFF27405A) else Color(0xFF1C2A3B),
        border = BorderStroke(
            1.dp,
            if (option.enabled) Secondary.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.05f)
        ),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = option.sourceName,
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (option.enabled) "已启用" else "已停用",
                    color = if (option.enabled) Secondary else TextTertiary,
                    style = MaterialTheme.typography.labelMedium
                )
            }

        }
    }
}

@Composable
private fun ToolbarIconChip(
    icon: ImageVector,
    contentDescription: String,
    size: Dp,
    emphasized: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(size * 0.34f),
        color = if (emphasized) Color(0xFF1B3359) else Color(0xFF14233A),
        border = BorderStroke(
            1.dp,
            if (emphasized) Color(0xFF2D558F) else Color.White.copy(alpha = 0.05f)
        ),
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier.size(size),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = TextPrimary,
                modifier = Modifier.size((size * 0.46f).coerceIn(18.dp, 24.dp))
            )
        }
    }
}
