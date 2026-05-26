package com.vunbo.watchtogether.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vunbo.watchtogether.config.AppInfo
import com.vunbo.watchtogether.data.subscription.SubscriptionType
import com.vunbo.watchtogether.ui.subscription.SubscriptionManagerSheet
import com.vunbo.watchtogether.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onCheckUpdate: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val m3u8Purify by viewModel.m3u8Purify.collectAsState()
    val vodGroups by viewModel.vodGroups.collectAsState()
    val liveGroups by viewModel.liveGroups.collectAsState()
    val vodSelection by viewModel.vodSelection.collectAsState()
    val liveSelection by viewModel.liveSelection.collectAsState()
    val vodSummary by viewModel.vodSummary.collectAsState()
    val liveSummary by viewModel.liveSummary.collectAsState()
    val saveResult by viewModel.saveResult.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val loadingGroupId by viewModel.loadingGroupId.collectAsState()
    val loadingStoreId by viewModel.loadingStoreId.collectAsState()
    val validationMessage by viewModel.validationMessage.collectAsState()
    val operationResult by viewModel.operationResult.collectAsState()
    var showVodSheet by remember { mutableStateOf(false) }
    var showLiveSheet by remember { mutableStateOf(false) }

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
                    text = "设置",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Data Source Section
            SettingsSection(title = "数据源配置") {
                SettingsItem(
                    title = "影视订阅管理",
                    subtitle = vodSummary.subtitle,
                    onClick = { showVodSheet = true }
                )
                SettingsItem(
                    title = "直播订阅管理",
                    subtitle = liveSummary.subtitle,
                    onClick = { showLiveSheet = true }
                )
                saveResult?.let { msg ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (msg.contains("成功")) SuccessGreen else if (msg.contains("失败") || msg.contains("出错")) ErrorRed else TextSecondary
                    )
                }
            }

            // Player Settings
            SettingsSection(title = "播放设置") {
                SettingsItem(
                    title = "播放内核",
                    subtitle = "Exo播放器"
                )
                SettingsToggleItem(
                    title = "M3U8 净化",
                    subtitle = "移除M3U8视频流中的广告片段",
                    checked = m3u8Purify,
                    onToggle = { viewModel.toggleM3u8Purify() }
                )
            }

            // Data Management
            SettingsSection(title = "数据管理") {
                SettingsItem(
                    title = "清除播放历史",
                    subtitle = "删除所有观看记录",
                    onClick = { viewModel.clearHistory() }
                )
                SettingsItem(
                    title = "清除收藏",
                    subtitle = "删除所有收藏内容",
                    onClick = { viewModel.clearFavorites() }
                )
                SettingsItem(
                    title = "清除缓存",
                    subtitle = "清除应用缓存数据",
                    onClick = { viewModel.clearCache() }
                )
            }

            // About
            SettingsSection(title = "关于") {
                SettingsItem(
                    title = AppInfo.APP_NAME,
                    subtitle = "Version ${AppInfo.versionName}"
                )
                SettingsItem(
                    title = "检查更新",
                    subtitle = "当前版本 ${AppInfo.versionName}",
                    onClick = onCheckUpdate
                )
            }
        }
    }

    operationResult?.let { message ->
        val failed = message.contains("失败") || message.contains("出错")
        AlertDialog(
            onDismissRequest = { viewModel.dismissOperationResult() },
            title = {
                Text(
                    text = if (failed) "操作失败" else "操作完成",
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    text = message,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissOperationResult() }) {
                    Text("知道了", color = Secondary)
                }
            },
            containerColor = DarkCard,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary
        )
    }

    if (showVodSheet) {
        SubscriptionManagerSheet(
            type = SubscriptionType.Vod,
            title = "影视订阅管理",
            groups = vodGroups,
            selection = vodSelection,
            isSaving = isSaving,
            loadingGroupId = loadingGroupId,
            loadingStoreId = loadingStoreId,
            summary = vodSummary,
            validationMessage = validationMessage,
            onDismiss = {
                showVodSheet = false
                viewModel.clearValidationMessage()
            },
            onRefresh = { viewModel.refreshSubscriptions(SubscriptionType.Vod) },
            onAdd = { name, url, onSuccess ->
                viewModel.addSubscription(SubscriptionType.Vod, name, url, onSuccess)
            },
            onSelect = { groupId, storeId ->
                viewModel.selectSubscription(SubscriptionType.Vod, groupId, storeId)
            },
            onDeleteGroup = { groupId -> viewModel.deleteSubscriptionGroup(SubscriptionType.Vod, groupId) },
            onDeleteStore = { groupId, storeId ->
                viewModel.deleteSubscriptionStore(SubscriptionType.Vod, groupId, storeId)
            },
            onClearValidationMessage = { viewModel.clearValidationMessage() }
        )
    }

    if (showLiveSheet) {
        SubscriptionManagerSheet(
            type = SubscriptionType.Live,
            title = "直播订阅管理",
            groups = liveGroups,
            selection = liveSelection,
            isSaving = isSaving,
            loadingGroupId = loadingGroupId,
            loadingStoreId = loadingStoreId,
            summary = liveSummary,
            validationMessage = validationMessage,
            onDismiss = {
                showLiveSheet = false
                viewModel.clearValidationMessage()
            },
            onRefresh = { viewModel.refreshSubscriptions(SubscriptionType.Live) },
            onAdd = { name, url, onSuccess ->
                viewModel.addSubscription(SubscriptionType.Live, name, url, onSuccess)
            },
            onSelect = { groupId, storeId ->
                viewModel.selectSubscription(SubscriptionType.Live, groupId, storeId)
            },
            onDeleteGroup = { groupId -> viewModel.deleteSubscriptionGroup(SubscriptionType.Live, groupId) },
            onDeleteStore = { groupId, storeId ->
                viewModel.deleteSubscriptionStore(SubscriptionType.Live, groupId, storeId)
            },
            onClearValidationMessage = { viewModel.clearValidationMessage() }
        )
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = Secondary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCard)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

@Composable
fun SettingsItem(
    title: String,
    subtitle: String = "",
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }
        }
        if (onClick != null) {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun SettingsToggleItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Secondary,
                checkedTrackColor = SecondaryMuted
            )
        )
    }
}
