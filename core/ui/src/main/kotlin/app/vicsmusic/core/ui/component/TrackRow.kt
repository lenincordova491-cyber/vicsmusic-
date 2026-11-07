package app.vicsmusic.core.ui.component

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.vicsmusic.core.designsystem.theme.VicsCardBorderDark
import app.vicsmusic.core.designsystem.theme.VicsPrimary
import app.vicsmusic.core.designsystem.theme.VicsSurfaceContainerDark
import app.vicsmusic.core.designsystem.theme.VicsSurfaceDark
import app.vicsmusic.core.designsystem.theme.VicsTextPrimaryDark
import app.vicsmusic.core.designsystem.theme.VicsTextSecondaryDark
import app.vicsmusic.core.model.Track

@Composable
fun TrackRow(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    onMenuClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = if (isPlaying) 1.dp else 0.5.dp,
                color = if (isPlaying) VicsPrimary else VicsCardBorderDark,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) VicsSurfaceContainerDark else VicsSurfaceDark
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArtworkImage(
                artworkUrl = track.artworkUrl,
                contentDescription = track.title,
                size = 44.dp,
                cornerRadius = 8.dp
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (isPlaying) VicsPrimary else VicsTextPrimaryDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${track.artistName} • ${formatDuration(track.durationMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = VicsTextSecondaryDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (track.isLiked) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Favorito",
                    tint = Color(0xFFEF4444),
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(18.dp)
                )
            }

            if (onMenuClick != null) {
                IconButton(onClick = onMenuClick) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Opciones",
                        tint = VicsTextSecondaryDark,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
