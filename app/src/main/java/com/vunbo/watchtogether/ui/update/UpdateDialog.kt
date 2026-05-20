package com.vunbo.watchtogether.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vunbo.watchtogether.config.AppInfo
import com.vunbo.watchtogether.data.update.UpdateInfo
import com.vunbo.watchtogether.ui.theme.DarkCard
import com.vunbo.watchtogether.ui.theme.Secondary
import com.vunbo.watchtogether.ui.theme.TextPrimary
import com.vunbo.watchtogether.ui.theme.TextSecondary

@Composable
fun UpdateDialog(
    state: UpdateUiState,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit
) {
    val info = state.updateInfo ?: return
    val forceUpdate = info.isForceFor(AppInfo.versionCode)
    AlertDialog(
        onDismissRequest = {
            if (!forceUpdate && !state.downloading) onDismiss()
        },
        containerColor = DarkCard,
        title = {
            Text(
                text = if (forceUpdate) "发现必要更新 ${info.safeVersionName}" else "发现新版本 ${info.safeVersionName}",
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            UpdateDialogContent(
                info = info,
                state = state
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (state.hasDownloadedApk) onInstall() else onDownload()
                },
                enabled = !state.downloading
            ) {
                Text(
                    text = when {
                        state.downloading -> "下载中..."
                        state.hasDownloadedApk -> "安装更新"
                        else -> "下载更新"
                    }
                )
            }
        },
        dismissButton = {
            if (!forceUpdate) {
                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !state.downloading
                ) {
                    Text("稍后", color = TextSecondary)
                }
            }
        }
    )
}

@Composable
private fun UpdateDialogContent(
    info: UpdateInfo,
    state: UpdateUiState
) {
    Column(
        modifier = Modifier
            .widthIn(max = 420.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "当前版本 ${AppInfo.versionName}，最新版本 ${info.safeVersionName}",
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "安装包大小：${info.formattedSize()}",
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
        if (info.notes.isNotEmpty()) {
            Text(
                text = "更新内容",
                color = TextPrimary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                info.notes.forEach { note ->
                    Text(
                        text = "• $note",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        if (state.downloading) {
            Spacer(modifier = Modifier.height(2.dp))
            LinearProgressIndicator(
                progress = { state.downloadProgress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = Secondary
            )
            Text(
                text = "已下载 ${(state.downloadProgress * 100).toInt()}%",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun UpdateMessageDialog(
    message: String?,
    onDismiss: () -> Unit
) {
    if (message.isNullOrBlank()) return
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = {
            Text(
                text = "更新提示",
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = message,
                modifier = Modifier.padding(top = 4.dp),
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("知道了", color = Secondary)
            }
        }
    )
}
