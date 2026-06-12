package com.vunbo.watchtogether.feature.watchroom

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vunbo.watchtogether.ui.theme.DarkCard
import com.vunbo.watchtogether.ui.theme.GoldStar
import com.vunbo.watchtogether.ui.theme.RoomConnected
import com.vunbo.watchtogether.ui.theme.Secondary
import com.vunbo.watchtogether.ui.theme.SecondaryMuted
import com.vunbo.watchtogether.ui.theme.TextPrimary
import com.vunbo.watchtogether.ui.theme.TextSecondary
import com.vunbo.watchtogether.ui.theme.TextTertiary

@Composable
fun WatchTogetherOverlay(
    roomState: RoomState,
    messages: List<ChatMessage>,
    onSendMessage: (String) -> Unit,
    onSyncHost: () -> Unit,
    onCollapse: () -> Unit,
    onLeaveRoom: () -> Unit,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    var initialScrollDone by remember(roomState.roomCode) { mutableStateOf(false) }

    LaunchedEffect(roomState.roomCode, messages.size, messages.lastOrNull()?.timestamp) {
        if (messages.isNotEmpty()) {
            val targetIndex = ROOM_INFO_ITEM_COUNT + messages.lastIndex
            if (initialScrollDone) {
                listState.animateScrollToItem(targetIndex)
            } else {
                listState.scrollToItem(targetIndex)
                initialScrollDone = true
            }
        }
    }

    Surface(
        modifier = modifier,
        color = Color.Transparent,
        shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xC60A1018), Color(0xF0152434))
                    )
                )
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RoomHeader(
                roomState = roomState,
                onCopyRoom = { context.copyPlainText("房间号", roomState.roomCode) },
                onCollapse = onCollapse,
                onLeaveRoom = onLeaveRoom
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                state = listState,
                contentPadding = PaddingValues(vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    SyncStatusCard(roomState, onSyncHost)
                }
                item {
                    MemberStrip(
                        members = roomState.members,
                        currentUserId = roomState.userId
                    )
                }
                if (messages.isEmpty()) {
                    item { EmptyChatHint() }
                }
                items(messages) { message ->
                    ChatBubble(message)
                }
            }

            ChatInput(
                value = inputText,
                onValueChange = { inputText = it },
                onSend = {
                    val clean = inputText.trim()
                    if (clean.isNotBlank()) {
                        onSendMessage(clean)
                        inputText = ""
                    }
                }
            )
        }
    }
}

private const val ROOM_INFO_ITEM_COUNT = 2

private fun Context.copyPlainText(label: String, text: String) {
    val manager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    manager.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(this, "已复制房间号", Toast.LENGTH_SHORT).show()
}

@Composable
private fun RoomHeader(
    roomState: RoomState,
    onCopyRoom: () -> Unit,
    onCollapse: () -> Unit,
    onLeaveRoom: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(18.dp),
            color = Color.White.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(shape = CircleShape, color = SecondaryMuted) {
                    Icon(
                        imageVector = Icons.Filled.Groups,
                        contentDescription = null,
                        tint = Secondary,
                        modifier = Modifier.padding(7.dp).size(17.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (roomState.isHost) "你是房主" else "正在跟随房主",
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = roomState.roomCode.ifBlank { "未连接" },
                        color = TextPrimary,
                        fontSize = 18.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip
                    )
                }
                IconButton(onClick = onCopyRoom, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "复制房间号", tint = Secondary)
                }
            }
        }
        IconButton(onClick = onCollapse, modifier = Modifier.size(34.dp)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "收起", tint = TextSecondary)
        }
        IconButton(onClick = onLeaveRoom, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "退出", tint = TextSecondary)
        }
    }
}

@Composable
private fun SyncStatusCard(roomState: RoomState, onSyncHost: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF102338).copy(alpha = 0.92f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (roomState.isHost) Icons.Filled.VerifiedUser else Icons.Filled.Sync,
                contentDescription = null,
                tint = if (roomState.isHost) GoldStar else RoomConnected,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (roomState.isHost) "房主控制播放" else "实时同步房主进度",
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = roomState.mediaState?.videoTitle?.takeIf { it.isNotBlank() }
                        ?: "播放、进度和倍速会自动同步",
                    color = TextTertiary,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (roomState.isHost) {
                roomState.mediaState?.let {
                    Text(
                        text = "${String.format("%.1f", it.temporarySpeed ?: it.speed)}x",
                        color = Secondary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Secondary.copy(alpha = 0.18f),
                    modifier = Modifier.clickable(onClick = onSyncHost)
                ) {
                    Text(
                        text = "同步房主",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        color = Secondary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun MemberStrip(members: List<RoomMember>, currentUserId: String) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(members) { member ->
            val isMe = member.userId.isNotBlank() && member.userId == currentUserId
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = when {
                    member.isHost -> Color(0xFF453A19)
                    isMe -> Secondary.copy(alpha = 0.14f)
                    !member.connected -> Color.White.copy(alpha = 0.035f)
                    else -> Color.White.copy(alpha = 0.07f)
                },
                border = BorderStroke(
                    1.dp,
                    when {
                        member.isHost -> GoldStar.copy(alpha = 0.35f)
                        isMe -> Secondary.copy(alpha = 0.28f)
                        else -> Color.White.copy(alpha = 0.06f)
                    }
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (isMe) {
                        Text("我", color = Secondary, style = MaterialTheme.typography.labelSmall)
                    }
                    if (member.isHost) {
                        Text("房主", color = GoldStar, style = MaterialTheme.typography.labelSmall)
                    }
                    if (!member.connected) {
                        Text("离线", color = TextTertiary, style = MaterialTheme.typography.labelSmall)
                    }
                    Text(
                        text = member.userName,
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyChatHint() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 28.dp),
        color = Color.Transparent
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.PlayCircle,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(34.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("房间聊天会显示在这里", color = TextTertiary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun ChatBubble(msg: ChatMessage) {
    val isSystem = msg.isSystem || msg.userId == "system"
    if (isSystem) {
        Text(
            text = msg.message,
            modifier = Modifier.fillMaxWidth(),
            color = TextTertiary,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center
        )
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (msg.isSelf) Alignment.End else Alignment.Start
    ) {
        Text(
            text = msg.userName,
            color = if (msg.isSelf) Secondary else TextSecondary,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
        Surface(
            shape = RoundedCornerShape(
                topStart = if (msg.isSelf) 16.dp else 5.dp,
                topEnd = if (msg.isSelf) 5.dp else 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            ),
            color = if (msg.isSelf) Secondary.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
        ) {
            Text(
                text = msg.message,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                color = TextPrimary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ChatInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = DarkCard.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f))
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = { onValueChange(it.take(300)) },
                modifier = Modifier.weight(1f),
                placeholder = { Text("说点什么...", color = TextTertiary, style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = Secondary
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() })
            )
            IconButton(
                onClick = onSend,
                enabled = value.isNotBlank(),
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "发送",
                    tint = if (value.isNotBlank()) Secondary else TextTertiary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
