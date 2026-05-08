package com.vunbo.watchtogether.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.vunbo.watchtogether.data.model.Movie
import com.vunbo.watchtogether.ui.theme.DarkCard
import com.vunbo.watchtogether.ui.theme.Primary
import com.vunbo.watchtogether.ui.theme.PrimaryMuted
import com.vunbo.watchtogether.ui.theme.Secondary
import com.vunbo.watchtogether.ui.theme.TextOnPrimary
import com.vunbo.watchtogether.ui.theme.TextPrimary
import com.vunbo.watchtogether.ui.theme.TextSecondary
import com.vunbo.watchtogether.ui.theme.TextTertiary

@Composable
fun VideoCard(
    video: Movie.Video,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.04f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth()
        ) {
            val titlePanelHeight = (maxWidth * 0.34f).coerceIn(64.dp, 86.dp)
            val cardPadding = (maxWidth * 0.050f).coerceIn(10.dp, 14.dp)
            val badgePadding = (maxWidth * 0.036f).coerceIn(8.dp, 10.dp)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f)
            ) {
                SubcomposeAsyncImage(
                    model = video.pic,
                    contentDescription = video.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(20.dp)),
                    contentScale = ContentScale.Crop,
                    loading = {
                        PosterPlaceholder(
                            title = video.name.orEmpty(),
                            modifier = Modifier.fillMaxSize()
                        )
                    },
                    error = {
                        PosterPlaceholder(
                            title = video.name.orEmpty(),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.14f),
                                    Color.Black.copy(alpha = 0.72f)
                                )
                            )
                        )
                )

                video.note?.takeIf { it.isNotBlank() }?.let { note ->
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(badgePadding),
                        shape = RoundedCornerShape(12.dp),
                        color = Primary.copy(alpha = 0.90f)
                    ) {
                        Text(
                            text = note,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextOnPrimary,
                            maxLines = 1
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(titlePanelHeight)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.12f),
                                    Color(0xFF171A26).copy(alpha = 0.94f)
                                )
                            )
                        )
                        .padding(horizontal = cardPadding, vertical = cardPadding),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text(
                        text = video.name.orEmpty().ifBlank { "未命名资源" },
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(
                        text = buildVideoMeta(video),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun PosterPlaceholder(
    title: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF1B2640),
                        Color(0xFF202C4B),
                        Color(0xFF161B2B)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.2.dp,
                color = Secondary
            )
            Text(
                text = title.take(10).ifBlank { "VIDEO" },
                style = MaterialTheme.typography.labelLarge,
                color = TextTertiary
            )
        }
    }
}

private fun buildVideoMeta(video: Movie.Video): String {
    val meta = listOfNotNull(
        video.type?.takeIf { it.isNotBlank() },
        video.year?.takeIf { it.isNotBlank() },
        video.area?.takeIf { it.isNotBlank() },
        video.lang?.takeIf { it.isNotBlank() }
    ).joinToString(" · ")
    return if (meta.isNotBlank()) meta else "继续查看详情"
}

@Composable
fun LoadingIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            color = Primary,
            modifier = Modifier.size(36.dp)
        )
    }
}

@Composable
fun ErrorView(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(16.dp))
        androidx.compose.material3.FilledTonalButton(
            onClick = onRetry,
            colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                containerColor = PrimaryMuted
            )
        ) {
            Text("重试")
        }
    }
}

