package app.vicsmusic.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.vicsmusic.core.model.ExtensionItem

@Composable
fun ExtensionCard(
    extension: ExtensionItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gradientColors = getCategoryGradient(extension.category, extension.id)
    val categoryIcon = getCategoryIcon(extension.category, extension.id)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(gradientColors))
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Spotify-styled icon tile
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = categoryIcon,
                        contentDescription = extension.displayName,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = extension.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Category Badge
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = extension.category.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = extension.description.ifBlank { "Extensión de audio y catálogo de streaming" },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "v${extension.version} • ${extension.tags.take(3).joinToString(" #", prefix = "#")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

private fun getCategoryGradient(category: String, id: String): List<Color> {
    return when {
        id.contains("spotify") -> listOf(Color(0xFF1DB954).copy(alpha = 0.4f), Color(0xFF121212))
        id.contains("apple") -> listOf(Color(0xFFFA243C).copy(alpha = 0.4f), Color(0xFF1E1E24))
        id.contains("soundcloud") -> listOf(Color(0xFFFF5500).copy(alpha = 0.4f), Color(0xFF1A1A22))
        id.contains("amazon") -> listOf(Color(0xFF00A8E1).copy(alpha = 0.4f), Color(0xFF151820))
        id.contains("deezer") -> listOf(Color(0xFFA238FF).copy(alpha = 0.4f), Color(0xFF191426))
        id.contains("tidal") -> listOf(Color(0xFF00FFFF).copy(alpha = 0.3f), Color(0xFF11171E))
        category == "download" -> listOf(Color(0xFF8B5CF6).copy(alpha = 0.4f), Color(0xFF161525))
        else -> listOf(Color(0xFF3B82F6).copy(alpha = 0.4f), Color(0xFF181C28))
    }
}

private fun getCategoryIcon(category: String, id: String): ImageVector {
    return when {
        id.contains("soundcloud") || id.contains("ytmusic") -> Icons.Default.GraphicEq
        id.contains("spotify") || id.contains("apple") -> Icons.Default.MusicNote
        category == "download" -> Icons.Default.CloudDownload
        category == "integration" -> Icons.Default.Album
        else -> Icons.Default.Extension
    }
}
