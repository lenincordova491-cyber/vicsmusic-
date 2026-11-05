package app.vicsmusic.feature.home

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vicsmusic.core.model.Track
import app.vicsmusic.core.ui.R
import app.vicsmusic.core.ui.component.ArtworkImage
import app.vicsmusic.core.ui.component.EmptyContent
import app.vicsmusic.core.ui.component.ErrorContent
import app.vicsmusic.core.ui.component.ShimmerList
import app.vicsmusic.core.ui.component.TrackRow

@Composable
fun HomeRoute(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToPlayer: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    HomeScreen(
        uiState = uiState,
        onTrackClick = { track, list ->
            viewModel.playTrack(track, list)
            onNavigateToPlayer()
        },
        onScanClick = viewModel::syncLocalTracks,
        modifier = modifier
    )
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onTrackClick: (Track, List<Track>) -> Unit,
    onScanClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Vicsmusic",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.home_greeting),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onScanClick) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.home_scan_button),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        when {
            uiState.isLoading && uiState.localTracks.isEmpty() -> {
                ShimmerList(count = 6, modifier = Modifier.fillMaxSize())
            }
            uiState.errorMessage != null && uiState.localTracks.isEmpty() -> {
                ErrorContent(
                    message = uiState.errorMessage,
                    onRetry = onScanClick,
                    modifier = Modifier.fillMaxSize()
                )
            }
            uiState.localTracks.isEmpty() -> {
                EmptyContent(
                    title = stringResource(R.string.home_empty_title),
                    message = stringResource(R.string.home_empty_description),
                    actionLabel = stringResource(R.string.home_scan_button),
                    onActionClick = onScanClick,
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    if (uiState.recentTracks.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.home_recent),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(
                                    items = uiState.recentTracks,
                                    key = { "recent_${it.id}" }
                                ) { track ->
                                    RecentTrackCard(
                                        track = track,
                                        onClick = { onTrackClick(track, uiState.recentTracks) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    item {
                        Text(
                            text = stringResource(R.string.home_local_music),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }

                    items(
                        items = uiState.localTracks,
                        key = { it.id }
                    ) { track ->
                        TrackRow(
                            track = track,
                            onClick = { onTrackClick(track, uiState.localTracks) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RecentTrackCard(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(130.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            ArtworkImage(
                artworkUrl = track.artworkUrl,
                contentDescription = track.title,
                size = 114.dp,
                cornerRadius = 8.dp,
                modifier = Modifier
                    .size(114.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artistName.ifBlank { stringResource(R.string.unknown_artist) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
