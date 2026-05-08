package com.vunbo.watchtogether.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vunbo.watchtogether.data.model.LiveChannelGroup
import com.vunbo.watchtogether.data.model.LiveChannelItem
import com.vunbo.watchtogether.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveScreen(viewModel: LiveViewModel = viewModel()) {
    val groups by viewModel.groups.collectAsState()
    val selectedGroupIndex by viewModel.selectedGroupIndex.collectAsState()
    val channels by viewModel.channels.collectAsState()
    val currentChannel by viewModel.currentChannel.collectAsState()

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
                    text = "直播",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
        )

        if (groups.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无直播源\n请在设置中配置直播API",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextTertiary
                )
            }
            return
        }

        // Channel grouping tabs
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(groups.size) { index ->
                val group = groups[index]
                val selected = index == selectedGroupIndex
                Surface(
                    modifier = Modifier.clickable { viewModel.selectGroup(index) },
                    shape = RoundedCornerShape(20.dp),
                    color = if (selected) Secondary else DarkSurfaceVariant
                ) {
                    Text(
                        text = group.name,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) DarkBackground else TextSecondary,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }

        // Channel list
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(channels.size) { index ->
                val channel = channels[index]
                val isActive = currentChannel == channel
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.playChannel(channel) },
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isActive) PrimaryMuted.copy(alpha = 0.5f) else DarkCard
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isActive) Secondary else TextTertiary,
                            modifier = Modifier.width(36.dp)
                        )
                        Text(
                            text = channel.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isActive) TextPrimary else TextSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        if (isActive) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Secondary.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "播放中",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Secondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
