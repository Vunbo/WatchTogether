package com.vunbo.watchtogether.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CompactTopHeader(
    modifier: Modifier = Modifier,
    titleContent: @Composable RowScope.() -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        val horizontalPadding = (maxWidth * 0.032f).coerceIn(12.dp, 20.dp)
        val barHeight = (maxWidth * 0.118f).coerceIn(50.dp, 62.dp)
        val itemGap = (maxWidth * 0.018f).coerceIn(8.dp, 12.dp)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding, vertical = 6.dp)
                .heightIn(min = barHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(itemGap)
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                content = titleContent
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(itemGap),
                verticalAlignment = Alignment.CenterVertically,
                content = actions
            )
        }
    }
}
