package app.vicsmusic.feature.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vicsmusic.core.model.Artist
import app.vicsmusic.core.model.Track
import app.vicsmusic.core.ui.component.TrackRow
import coil3.compose.AsyncImage
import kotlin.math.abs

private val SpotifyGreen = Color(0xFF1DB954)

@Composable
fun ArtistDetailScreen(
    artist: Artist,
    onBack: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onDownloadTrack: (Track) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isFollowing by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121212)),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        item(key = "artist_back_nav") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Volver",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        item(key = "artist_hero") {
            val initial = artist.name.firstOrNull()?.uppercaseChar()?.toString() ?: "A"
            val gradientColors = remember(artist.id) {
                when (abs(artist.id.hashCode()) % 5) {
                    0 -> listOf(Color(0xFFE91E63), Color(0xFF673AB7))
                    1 -> listOf(Color(0xFF2196F3), Color(0xFF009688))
                    2 -> listOf(Color(0xFFFF9800), Color(0xFFE91E63))
                    3 -> listOf(Color(0xFF4CAF50), Color(0xFF009688))
                    else -> listOf(Color(0xFF9C27B0), Color(0xFF3F51B5))
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(gradientColors)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initial,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                    if (!artist.imageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = artist.imageUrl,
                            contentDescription = artist.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = artist.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = "Verificado",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = "Artista verificado • Catálogo de audio completo",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )

                val bio = artist.bio
                if (!bio.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = bio,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isFollowing) SpotifyGreen else Color.Transparent
                        ),
                        modifier = Modifier
                            .clickable { isFollowing = !isFollowing }
                            .border(
                                width = 1.dp,
                                color = if (isFollowing) SpotifyGreen else Color.Gray,
                                shape = RoundedCornerShape(20.dp)
                            )
                    ) {
                        Text(
                            text = if (isFollowing) "Siguiendo" else "Seguir",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isFollowing) Color.Black else Color.White,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            if (artist.topTracks.isNotEmpty()) {
                                onPlayTrack(artist.topTracks.first(), artist.topTracks)
                            }
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(SpotifyGreen)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Reproducir artista",
                            tint = Color.Black,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            if (artist.topTracks.isNotEmpty()) {
                                val shuffled = artist.topTracks.shuffled()
                                onPlayTrack(shuffled.first(), shuffled)
                            }
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF282828))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Modo aleatorio",
                            tint = SpotifyGreen,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        item(key = "artist_popular_header") {
            Text(
                text = "Canciones populares",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)
            )
        }

        itemsIndexed(
            items = artist.topTracks,
            key = { index, track -> "artist_track_${track.id}_$index" }
        ) { index, track ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    modifier = Modifier.width(28.dp)
                )
                TrackRow(
                    track = track,
                    isPlaying = false,
                    onClick = { onPlayTrack(track, artist.topTracks) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
