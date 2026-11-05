package app.vicsmusic.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vicsmusic.core.model.Playlist
import app.vicsmusic.core.model.Track
import app.vicsmusic.core.ui.R
import app.vicsmusic.core.ui.component.EmptyContent
import app.vicsmusic.core.ui.component.ErrorContent
import app.vicsmusic.core.ui.component.ShimmerList
import app.vicsmusic.core.ui.component.TrackRow

@Composable
fun LibraryRoute(
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
    onNavigateToPlayer: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LibraryScreen(
        uiState = uiState,
        onTabSelect = viewModel::selectTab,
        onTrackClick = { track, list ->
            viewModel.playTrack(track, list)
            onNavigateToPlayer()
        },
        onScanClick = viewModel::syncTracks,
        modifier = modifier
    )
}

@Composable
fun LibraryScreen(
    uiState: LibraryUiState,
    onTabSelect: (LibraryTab) -> Unit,
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
                .padding(top = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.library_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(onClick = onScanClick) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.library_scan_tracks),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Tabs
        val tabs = listOf(
            LibraryTab.TRACKS to stringResource(R.string.library_tab_tracks),
            LibraryTab.FAVORITES to stringResource(R.string.library_tab_favorites),
            LibraryTab.PLAYLISTS to stringResource(R.string.library_tab_playlists)
        )

        TabRow(
            selectedTabIndex = uiState.selectedTab.ordinal,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            tabs.forEachIndexed { _, (tab, title) ->
                Tab(
                    selected = uiState.selectedTab == tab,
                    onClick = { onTabSelect(tab) },
                    text = {
                        Text(
                            text = title,
                            fontWeight = if (uiState.selectedTab == tab) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        // Tab Content
        when {
            uiState.isLoading && uiState.tracks.isEmpty() -> {
                ShimmerList(count = 6, modifier = Modifier.fillMaxSize())
            }
            uiState.errorMessage != null -> {
                ErrorContent(
                    message = uiState.errorMessage,
                    onRetry = onScanClick,
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                when (uiState.selectedTab) {
                    LibraryTab.TRACKS -> {
                        if (uiState.tracks.isEmpty()) {
                            EmptyContent(
                                title = stringResource(R.string.home_empty_title),
                                message = stringResource(R.string.home_empty_description),
                                actionLabel = stringResource(R.string.library_scan_tracks),
                                onActionClick = onScanClick,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(bottom = 120.dp)
                            ) {
                                item {
                                    Text(
                                        text = stringResource(R.string.library_track_count, uiState.tracks.size),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                }
                                items(
                                    items = uiState.tracks,
                                    key = { it.id }
                                ) { track ->
                                    TrackRow(
                                        track = track,
                                        onClick = { onTrackClick(track, uiState.tracks) }
                                    )
                                }
                            }
                        }
                    }
                    LibraryTab.FAVORITES -> {
                        if (uiState.favorites.isEmpty()) {
                            EmptyContent(
                                title = stringResource(R.string.library_empty_favorites),
                                message = "Marca canciones con el icono de corazón para tenerlas siempre a mano.",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(bottom = 120.dp)
                            ) {
                                items(
                                    items = uiState.favorites,
                                    key = { "fav_${it.id}" }
                                ) { track ->
                                    TrackRow(
                                        track = track,
                                        onClick = { onTrackClick(track, uiState.favorites) }
                                    )
                                }
                            }
                        }
                    }
                    LibraryTab.PLAYLISTS -> {
                        if (uiState.playlists.isEmpty()) {
                            EmptyContent(
                                title = stringResource(R.string.library_empty_playlists),
                                message = "Tus listas de reproducción creadas aparecerán aquí.",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(bottom = 120.dp)
                            ) {
                                items(
                                    items = uiState.playlists,
                                    key = { it.id }
                                ) { playlist ->
                                    PlaylistRow(playlist = playlist, onClick = {})
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistRow(
    playlist: Playlist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.QueueMusic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${playlist.trackCount} canciones",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
