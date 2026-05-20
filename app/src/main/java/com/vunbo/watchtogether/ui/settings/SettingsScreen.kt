package com.vunbo.watchtogether.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vunbo.watchtogether.AppInfo
import com.vunbo.watchtogether.data.api.ApiConfig.ApiStore
import com.vunbo.watchtogether.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val apiUrl by viewModel.apiUrl.collectAsState()
    val playType by viewModel.playType.collectAsState()
    val homeRec by viewModel.homeRec.collectAsState()
    val m3u8Purify by viewModel.m3u8Purify.collectAsState()
    val apiStores by viewModel.apiStores.collectAsState()
    val selectedStoreUrl by viewModel.selectedStoreUrl.collectAsState()
    val saveResult by viewModel.saveResult.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val operationResult by viewModel.operationResult.collectAsState()
    var showStoreDialog by remember { mutableStateOf(false) }

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
                OutlinedTextField(
                    value = apiUrl,
                    onValueChange = { viewModel.updateApiUrl(it) },
                    label = { Text("API 地址") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = Secondary,
                        unfocusedBorderColor = DarkSurfaceVariant,
                        cursorColor = Secondary,
                        focusedLabelColor = Secondary,
                        unfocusedLabelColor = TextTertiary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.saveAndReload() },
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("加载中...")
                    } else {
                        Icon(
                            Icons.Filled.CloudSync,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("保存并加载配置")
                    }
                }
                if (apiStores.size > 1) {
                    val selectedStore = apiStores.firstOrNull { it.url == selectedStoreUrl }
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsItem(
                        title = "切换仓库",
                        subtitle = selectedStore?.name?.ifBlank { selectedStore.url } ?: "请选择仓库",
                        onClick = { showStoreDialog = true }
                    )
                }
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
                    title = "播放器类型",
                    subtitle = viewModel.getPlayerTypeName(playType),
                    onClick = { viewModel.cyclePlayerType() }
                )
                SettingsItem(
                    title = "首页推荐",
                    subtitle = viewModel.getHomeRecName(homeRec),
                    onClick = { viewModel.cycleHomeRec() }
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

    if (showStoreDialog) {
        ApiStoreDialog(
            stores = apiStores,
            selectedUrl = selectedStoreUrl,
            isSaving = isSaving,
            onSelect = { store ->
                showStoreDialog = false
                viewModel.selectApiStore(store)
            },
            onDismiss = { showStoreDialog = false }
        )
    }
}

@Composable
private fun ApiStoreDialog(
    stores: List<ApiStore>,
    selectedUrl: String,
    isSaving: Boolean,
    onSelect: (ApiStore) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "切换仓库",
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(stores) { store ->
                    ApiStoreRow(
                        store = store,
                        selected = store.url == selectedUrl,
                        enabled = !isSaving,
                        onClick = { onSelect(store) }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextTertiary)
            }
        },
        containerColor = DarkCard,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary
    )
}

@Composable
private fun ApiStoreRow(
    store: ApiStore,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) SecondaryMuted else DarkSurfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = store.name.ifBlank { store.url },
                color = if (selected) Secondary else TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = store.url,
                color = TextTertiary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
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
