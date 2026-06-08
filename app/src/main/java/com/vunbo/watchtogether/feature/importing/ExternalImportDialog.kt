package com.vunbo.watchtogether.feature.importing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vunbo.watchtogether.data.importing.ExternalImportRequest
import com.vunbo.watchtogether.ui.theme.DarkCard
import com.vunbo.watchtogether.ui.theme.ErrorRed
import com.vunbo.watchtogether.ui.theme.Secondary
import com.vunbo.watchtogether.ui.theme.TextPrimary
import com.vunbo.watchtogether.ui.theme.TextSecondary
import com.vunbo.watchtogether.ui.theme.TextTertiary

@Composable
fun ExternalImportDialog(
    request: ExternalImportRequest,
    loading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = {
            Text(
                text = "导入${request.typeLabel}",
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ImportField(label = "配置名称", value = request.name)
                ImportField(label = "配置地址", value = request.url)
                Text(
                    text = "导入前会先验证配置可用性，验证失败不会保存。",
                    color = TextTertiary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !loading) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Secondary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text("验证并导入", color = Secondary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !loading) {
                Text("取消", color = TextTertiary)
            }
        },
        containerColor = DarkCard,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary
    )
}

@Composable
fun ExternalImportMessageDialog(
    message: String,
    onDismiss: () -> Unit
) {
    val failed = message.contains("失败") || message.contains("缺少") || message.contains("不支持")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (failed) "导入失败" else "导入完成",
                color = TextPrimary
            )
        },
        text = {
            Text(
                text = message,
                color = if (failed) ErrorRed else TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("知道了", color = Secondary)
            }
        },
        containerColor = DarkCard,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary
    )
}

@Composable
private fun ImportField(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = TextTertiary,
            style = MaterialTheme.typography.labelSmall
        )
        Text(
            text = value,
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}
