package com.vunbo.watchtogether.feature.subscription

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.vunbo.watchtogether.data.subscription.SubscriptionGroup
import com.vunbo.watchtogether.data.subscription.SubscriptionSelection
import com.vunbo.watchtogether.data.subscription.SubscriptionStore
import com.vunbo.watchtogether.data.subscription.SubscriptionSummary
import com.vunbo.watchtogether.data.subscription.SubscriptionType
import com.vunbo.watchtogether.ui.theme.DarkCard
import com.vunbo.watchtogether.ui.theme.DarkInput
import com.vunbo.watchtogether.ui.theme.DarkSurface
import com.vunbo.watchtogether.ui.theme.DarkSurfaceVariant
import com.vunbo.watchtogether.ui.theme.ErrorRed
import com.vunbo.watchtogether.ui.theme.Primary
import com.vunbo.watchtogether.ui.theme.Secondary
import com.vunbo.watchtogether.ui.theme.SecondaryMuted
import com.vunbo.watchtogether.ui.theme.SuccessGreen
import com.vunbo.watchtogether.ui.theme.TextPrimary
import com.vunbo.watchtogether.ui.theme.TextSecondary
import com.vunbo.watchtogether.ui.theme.TextTertiary
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionManagerSheet(
    type: SubscriptionType,
    title: String,
    groups: List<SubscriptionGroup>,
    selection: SubscriptionSelection,
    isSaving: Boolean,
    loadingGroupId: String,
    loadingStoreId: String,
    summary: SubscriptionSummary,
    validationMessage: String?,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onAdd: (String, String, () -> Unit) -> Unit,
    onSelect: (String, String) -> Unit,
    onDeleteGroup: (String) -> Unit,
    onDeleteStore: (String, String) -> Unit,
    onClearValidationMessage: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        tonalElevation = 0.dp,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 420.dp, max = 620.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onRefresh, enabled = !isSaving) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("刷新")
                }
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                TextButton(onClick = { showAddDialog = true }, enabled = !isSaving) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("添加")
                }
            }

            Text(
                text = "${summary.lastRefreshText} · 左滑删除，长按复制链接",
                color = TextTertiary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
            )

            if (groups.isEmpty()) {
                EmptySubscriptionView(type = type)
            } else {
                SubscriptionTwoColumnList(
                    groups = groups,
                    selection = selection,
                    isSaving = isSaving,
                    loadingGroupId = loadingGroupId,
                    loadingStoreId = loadingStoreId,
                    onSelect = onSelect,
                    onDeleteGroup = onDeleteGroup,
                    onDeleteStore = onDeleteStore
                )
            }

            if (isSaving) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Secondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("正在验证/切换...", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    if (showAddDialog) {
        AddSubscriptionDialog(
            title = "添加$title",
            isSaving = isSaving,
            error = validationMessage,
            onDismiss = {
                showAddDialog = false
                onClearValidationMessage()
            },
            onConfirm = { name, url ->
                onAdd(name, url) {
                    showAddDialog = false
                    onClearValidationMessage()
                }
            }
        )
    }
}

@Composable
private fun EmptySubscriptionView(type: SubscriptionType) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "还没有订阅配置",
                color = TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (type == SubscriptionType.Vod) {
                    "添加影视订阅后，可在这里切换单仓或多仓线路。"
                } else {
                    "添加直播订阅后，可在直播页使用新的频道源。"
                },
                color = TextTertiary,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun SubscriptionTwoColumnList(
    groups: List<SubscriptionGroup>,
    selection: SubscriptionSelection,
    isSaving: Boolean,
    loadingGroupId: String,
    loadingStoreId: String,
    onSelect: (String, String) -> Unit,
    onDeleteGroup: (String) -> Unit,
    onDeleteStore: (String, String) -> Unit
) {
    var selectedGroupId by remember(groups, selection.groupId) {
        mutableStateOf(selection.groupId.takeIf { id -> groups.any { it.id == id } } ?: groups.firstOrNull()?.id.orEmpty())
    }
    val selectedGroup = groups.firstOrNull { it.id == selectedGroupId } ?: groups.firstOrNull()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 320.dp, max = 500.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        LazyColumn(
            modifier = Modifier.weight(0.42f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 18.dp)
        ) {
            items(groups, key = { it.id }) { group ->
                val selected = group.id == selectedGroup?.id
                SwipeDeleteRow(
                    deleteEnabled = !isSaving,
                    onDelete = { onDeleteGroup(group.id) }
                ) {
                    SubscriptionCell(
                        title = group.name,
                        subtitle = "${group.stores.size} 条",
                        selected = selected,
                        enabled = !isSaving,
                        loading = loadingGroupId == group.id && loadingStoreId.isBlank(),
                        copyUrl = group.sourceUrl,
                        onClick = { selectedGroupId = group.id }
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(0.58f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 18.dp)
        ) {
            items(selectedGroup?.stores.orEmpty(), key = { it.id }) { store ->
                val selected = selectedGroup?.id == selection.groupId && store.id == selection.storeId
                SwipeDeleteRow(
                    deleteEnabled = !isSaving,
                    onDelete = { selectedGroup?.let { onDeleteStore(it.id, store.id) } }
                ) {
                    SubscriptionCell(
                        title = store.name,
                        subtitle = store.url,
                        selected = selected,
                        enabled = !isSaving,
                        loading = selectedGroup?.id == loadingGroupId && store.id == loadingStoreId,
                        copyUrl = store.url,
                        onClick = {
                            selectedGroup?.let { group ->
                                onSelect(group.id, store.id)
                            }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SubscriptionCell(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    loading: Boolean,
    copyUrl: String,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("订阅地址", copyUrl))
                    Toast.makeText(context, "链接已复制", Toast.LENGTH_SHORT).show()
                }
            ),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) SecondaryMuted else DarkCard
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title.ifBlank { "未命名" },
                    modifier = Modifier.weight(1f),
                    color = if (selected) Secondary else TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = Secondary
                    )
                } else if (selected) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(SuccessGreen)
                    )
                }
            }
            Text(
                text = subtitle,
                color = TextTertiary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SwipeDeleteRow(
    deleteEnabled: Boolean,
    onDelete: () -> Unit,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val revealPx = with(density) { 76.dp.toPx() }
    val offset = remember { Animatable(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ErrorRed.copy(alpha = 0.95f))
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .height(54.dp)
                .width(76.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            IconButton(
                onClick = {
                    onDelete()
                },
                enabled = deleteEnabled
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除",
                    tint = Color.White
                )
            }
        }
        Box(
            modifier = Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .pointerInput(deleteEnabled) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { _, dragAmount ->
                            if (deleteEnabled) {
                                scope.launch {
                                    val next = (offset.value + dragAmount).coerceIn(-revealPx, 0f)
                                    offset.snapTo(next)
                                }
                            }
                        },
                        onDragEnd = {
                            if (deleteEnabled) {
                                scope.launch {
                                    val target = if (offset.value < -revealPx * 0.45f) -revealPx else 0f
                                    offset.animateTo(target, tween(160))
                                }
                            }
                        }
                    )
                }
        ) {
            content()
        }
    }
}

@Composable
private fun AddSubscriptionDialog(
    title: String,
    isSaving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        name = ""
        url = ""
    }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss, enabled = !isSaving) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭", tint = TextTertiary)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("配置名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = subscriptionTextFieldColors(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("配置地址") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = subscriptionTextFieldColors(),
                    shape = RoundedCornerShape(12.dp)
                )
                error?.let {
                    Text(
                        text = it,
                        color = ErrorRed,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, url) },
                enabled = !isSaving,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("验证中")
                } else {
                    Text("确认")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("取消", color = TextTertiary)
            }
        },
        containerColor = DarkCard,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary
    )
}

@Composable
private fun subscriptionTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedContainerColor = DarkInput,
    unfocusedContainerColor = DarkInput,
    focusedBorderColor = Secondary,
    unfocusedBorderColor = DarkSurfaceVariant,
    cursorColor = Secondary,
    focusedLabelColor = Secondary,
    unfocusedLabelColor = TextTertiary
)
