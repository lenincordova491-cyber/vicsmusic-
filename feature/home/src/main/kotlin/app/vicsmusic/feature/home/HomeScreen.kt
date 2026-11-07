package app.vicsmusic.feature.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownloadDone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vicsmusic.core.model.ExtensionItem
import app.vicsmusic.core.model.Track
import app.vicsmusic.core.ui.component.ArtworkImage
import app.vicsmusic.core.ui.component.EmptyContent
import app.vicsmusic.core.ui.component.ShimmerList
import app.vicsmusic.core.ui.component.TrackRow

private val SpotifyGreen = Color(0xFF1DB954)
private val SpotifyDarkCard = Color(0xFF242424)
private val SpotifyCardHover = Color(0xFF2A2A2A)

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
        onFilterSelected = viewModel::onFilterSelected,
        onSettingsClick = { viewModel.setSettingsOpen(true) },
        onDismissSettings = { viewModel.setSettingsOpen(false) },
        onToggleExtension = viewModel::toggleExtension,
        onDownloadTrack = viewModel::downloadTrack,
        onResetArtistPreferences = viewModel::resetArtistPreferences,
        onToggleAutoplay = viewModel::toggleAutoplay,
        modifier = modifier
    )
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onTrackClick: (Track, List<Track>) -> Unit,
    onFilterSelected: (HomeCategoryFilter) -> Unit,
    onSettingsClick: () -> Unit,
    onDismissSettings: () -> Unit,
    onToggleExtension: (String) -> Unit,
    onDownloadTrack: (Track) -> Unit,
    onResetArtistPreferences: () -> Unit = {},
    onToggleAutoplay: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Spotify Top Bar: Greeting + Settings Gear (Tuerca de Ajustes)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // User Avatar
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(SpotifyGreen),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "V",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }

                        Text(
                            text = uiState.greeting,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Settings Gear Button (Tuerca de ajustes para configurar extensiones)
                    IconButton(
                        onClick = onSettingsClick,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(SpotifyDarkCard)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Ajustes y Extensiones",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // 2. Spotify Category Filter Chips ("Todos", "Música", "Descargas", "Podcasts")
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        SpotifyFilterChip(
                            label = "Todos",
                            selected = uiState.selectedFilter == HomeCategoryFilter.ALL,
                            onClick = { onFilterSelected(HomeCategoryFilter.ALL) }
                        )
                    }
                    item {
                        SpotifyFilterChip(
                            label = "Música",
                            selected = uiState.selectedFilter == HomeCategoryFilter.MUSIC,
                            onClick = { onFilterSelected(HomeCategoryFilter.MUSIC) }
                        )
                    }
                    item {
                        SpotifyFilterChip(
                            label = "Descargas (Offline)",
                            selected = uiState.selectedFilter == HomeCategoryFilter.DOWNLOADS,
                            onClick = { onFilterSelected(HomeCategoryFilter.DOWNLOADS) }
                        )
                    }
                    item {
                        SpotifyFilterChip(
                            label = "Podcasts",
                            selected = uiState.selectedFilter == HomeCategoryFilter.PODCASTS,
                            onClick = { onFilterSelected(HomeCategoryFilter.PODCASTS) }
                        )
                    }
                }
            }

            // 3. Quick Access Grid (2x3 Spotify style)
            if (uiState.selectedFilter == HomeCategoryFilter.ALL || uiState.selectedFilter == HomeCategoryFilter.MUSIC) {
                if (uiState.quickAccessTracks.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val chunked = uiState.quickAccessTracks.chunked(2)
                            chunked.forEach { pair ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    QuickAccessCard(
                                        track = pair[0],
                                        onClick = { onTrackClick(pair[0], uiState.quickAccessTracks) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (pair.size > 1) {
                                        QuickAccessCard(
                                            track = pair[1],
                                            onClick = { onTrackClick(pair[1], uiState.quickAccessTracks) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Hero "Nuevo lanzamiento para ti" (Featured Release Banner)
            if (uiState.featuredRelease != null && (uiState.selectedFilter == HomeCategoryFilter.ALL || uiState.selectedFilter == HomeCategoryFilter.MUSIC)) {
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text(
                            text = "Nuevo lanzamiento para ti",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        HeroReleaseCard(
                            track = uiState.featuredRelease,
                            onPlayClick = { onTrackClick(uiState.featuredRelease, listOf(uiState.featuredRelease)) },
                            onDownloadClick = { onDownloadTrack(uiState.featuredRelease) },
                            isDownloading = uiState.downloadingTrackId == uiState.featuredRelease.id
                        )
                    }
                }
            }

            // 5. "Tu Mix Diario" + "Descubrimiento según tu gusto" (Adaptive Taste Carousels)
            if (uiState.dailyMix.isNotEmpty() && (uiState.selectedFilter == HomeCategoryFilter.ALL || uiState.selectedFilter == HomeCategoryFilter.MUSIC)) {
                item {
                    SectionHeader(title = "Recomendado para ti")
                    Text(
                        text = uiState.tasteSummaryLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = SpotifyGreen,
                        modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 4.dp)
                    ) {
                        items(uiState.dailyMix, key = { "daily_${it.id}" }) { track ->
                            MediaCard(
                                track = track,
                                onClick = { onTrackClick(track, uiState.dailyMix) },
                                onDownloadClick = { onDownloadTrack(track) }
                            )
                        }
                    }
                }
            }

            if (uiState.recommendedTracks.isNotEmpty() && (uiState.selectedFilter == HomeCategoryFilter.ALL || uiState.selectedFilter == HomeCategoryFilter.MUSIC)) {
                item {
                    SectionHeader(title = "Descubrimiento según tu gusto")
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 4.dp)
                    ) {
                        items(uiState.recommendedTracks, key = { "rec_${it.id}" }) { track ->
                            MediaCard(
                                track = track,
                                onClick = { onTrackClick(track, uiState.recommendedTracks) },
                                onDownloadClick = { onDownloadTrack(track) }
                            )
                        }
                    }
                }
            }

            // 6. "Top Tendencias Globales" (Trending hits carousel)
            if (uiState.trendingTracks.isNotEmpty() && (uiState.selectedFilter == HomeCategoryFilter.ALL || uiState.selectedFilter == HomeCategoryFilter.MUSIC)) {
                item {
                    SectionHeader(title = "Top Éxitos Globales")
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 4.dp)
                    ) {
                        items(uiState.trendingTracks, key = { "trend_${it.id}" }) { track ->
                            MediaCard(
                                track = track,
                                onClick = { onTrackClick(track, uiState.trendingTracks) },
                                onDownloadClick = { onDownloadTrack(track) }
                            )
                        }
                    }
                }
            }

            // 7. "Cumbia y Fiesta" Carousel
            if (uiState.cumbiaTracks.isNotEmpty() && (uiState.selectedFilter == HomeCategoryFilter.ALL || uiState.selectedFilter == HomeCategoryFilter.MUSIC)) {
                item {
                    SectionHeader(title = "Cumbia y Fiesta")
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 4.dp)
                    ) {
                        items(uiState.cumbiaTracks, key = { "cumbia_${it.id}" }) { track ->
                            MediaCard(
                                track = track,
                                onClick = { onTrackClick(track, uiState.cumbiaTracks) },
                                onDownloadClick = { onDownloadTrack(track) }
                            )
                        }
                    }
                }
            }

            // 8. "Rock & Pop Clásicos" Carousel
            if (uiState.rockPopTracks.isNotEmpty() && (uiState.selectedFilter == HomeCategoryFilter.ALL || uiState.selectedFilter == HomeCategoryFilter.MUSIC)) {
                item {
                    SectionHeader(title = "Rock y Pop Clásicos")
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 4.dp)
                    ) {
                        items(uiState.rockPopTracks, key = { "rock_${it.id}" }) { track ->
                            MediaCard(
                                track = track,
                                onClick = { onTrackClick(track, uiState.rockPopTracks) },
                                onDownloadClick = { onDownloadTrack(track) }
                            )
                        }
                    }
                }
            }

            // 9. "Urbano & Pop Latino" Carousel
            if (uiState.urbanTracks.isNotEmpty() && (uiState.selectedFilter == HomeCategoryFilter.ALL || uiState.selectedFilter == HomeCategoryFilter.MUSIC)) {
                item {
                    SectionHeader(title = "Urbano y Tendencias")
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 4.dp)
                    ) {
                        items(uiState.urbanTracks, key = { "urban_${it.id}" }) { track ->
                            MediaCard(
                                track = track,
                                onClick = { onTrackClick(track, uiState.urbanTracks) },
                                onDownloadClick = { onDownloadTrack(track) }
                            )
                        }
                    }
                }
            }

            // 9b. "Salsa Clásica y Bailable" Carousel
            if (uiState.salsaTracks.isNotEmpty() && (uiState.selectedFilter == HomeCategoryFilter.ALL || uiState.selectedFilter == HomeCategoryFilter.MUSIC)) {
                item {
                    SectionHeader(title = "Salsa Clásica y Bailable")
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 4.dp)
                    ) {
                        items(uiState.salsaTracks, key = { "salsa_${it.id}" }) { track ->
                            MediaCard(
                                track = track,
                                onClick = { onTrackClick(track, uiState.salsaTracks) },
                                onDownloadClick = { onDownloadTrack(track) }
                            )
                        }
                    }
                }
            }

            // 10. Podcasts Empty State
            if (uiState.selectedFilter == HomeCategoryFilter.PODCASTS) {
                item {
                    EmptyContent(
                        title = "Podcasts próximamente",
                        message = "Explora tus canciones y artistas favoritos en las pestañas Todos, Música o Descargas.",
                        modifier = Modifier.fillMaxWidth().height(240.dp)
                    )
                }
            }

            // 8. Descargas (100% Offline Songs stored on device)
            if (uiState.selectedFilter == HomeCategoryFilter.DOWNLOADS || (uiState.selectedFilter == HomeCategoryFilter.ALL && uiState.downloadedTracks.isNotEmpty())) {
                item {
                    SectionHeader(title = "Descargas locales (100% Offline)")
                }
                if (uiState.downloadedTracks.isEmpty()) {
                    item {
                        EmptyContent(
                            title = "Sin canciones descargadas",
                            message = "Descarga música para escucharla sin conexión a internet en cualquier momento.",
                            modifier = Modifier.fillMaxWidth().height(180.dp)
                        )
                    }
                } else {
                    items(uiState.downloadedTracks, key = { "dl_${it.id}" }) { track ->
                        TrackRow(
                            track = track,
                            onClick = { onTrackClick(track, uiState.downloadedTracks) }
                        )
                    }
                }
            }

            // 8. Extensiones y Fuentes Activas
            item {
                SectionHeader(title = "Fuentes de música activas")
                Text(
                    text = "Configuradas desde el icono de tuerca para streaming sin anuncios",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(uiState.availableExtensions, key = { it.id }) { ext ->
                        val isActive = uiState.activeExtensionIds.contains(ext.id)
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isActive) SpotifyCardHover else SpotifyDarkCard.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.width(160.dp).clickable { onSettingsClick() }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = ext.displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (isActive) SpotifyGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (isActive) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Activa",
                                            tint = SpotifyGreen,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = ext.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // Settings Dialog (Tuerca de Ajustes de Extensiones)
        if (uiState.isSettingsOpen) {
            SettingsExtensionsDialog(
                extensions = uiState.availableExtensions,
                activeIds = uiState.activeExtensionIds,
                isAutoplayEnabled = uiState.isAutoplayEnabled,
                onToggle = onToggleExtension,
                onToggleAutoplay = onToggleAutoplay,
                onResetArtistPreferences = onResetArtistPreferences,
                onDismiss = onDismissSettings
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun SpotifyFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        },
        shape = RoundedCornerShape(16.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = SpotifyGreen,
            selectedLabelColor = Color.Black,
            containerColor = SpotifyDarkCard,
            labelColor = Color.White
        )
    )
}

@Composable
private fun QuickAccessCard(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = SpotifyDarkCard)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize()
        ) {
            ArtworkImage(
                artworkUrl = track.artworkUrl,
                contentDescription = track.title,
                size = 56.dp,
                cornerRadius = 6.dp
            )
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )
        }
    }
}

@Composable
private fun HeroReleaseCard(
    track: Track,
    onPlayClick: () -> Unit,
    onDownloadClick: () -> Unit,
    isDownloading: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SpotifyDarkCard),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArtworkImage(
                artworkUrl = track.artworkUrl,
                contentDescription = track.title,
                size = 80.dp,
                cornerRadius = 8.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artistName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Streaming sin anuncios",
                    style = MaterialTheme.typography.labelSmall,
                    color = SpotifyGreen
                )
            }

            // Download Icon Button
            IconButton(onClick = onDownloadClick) {
                if (isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = SpotifyGreen,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Descargar en el móvil",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Spotify Round Green Play Button
            IconButton(
                onClick = onPlayClick,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(SpotifyGreen)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Reproducir",
                    tint = Color.Black,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun MediaCard(
    track: Track,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(136.dp)
            .clickable(onClick = onClick)
    ) {
        Box {
            ArtworkImage(
                artworkUrl = track.artworkUrl,
                contentDescription = track.title,
                size = 136.dp,
                cornerRadius = 8.dp
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = track.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = track.artistName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SettingsExtensionsDialog(
    extensions: List<ExtensionItem>,
    activeIds: Set<String>,
    isAutoplayEnabled: Boolean,
    onToggle: (String) -> Unit,
    onToggleAutoplay: (Boolean) -> Unit,
    onResetArtistPreferences: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = SpotifyGreen,
                    modifier = Modifier.size(24.dp).padding(end = 8.dp)
                )
                Text(
                    text = "Ajustes y Preferencias",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(380.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SpotifyGreen.copy(alpha = 0.15f))
                            .clickable {
                                onDismiss()
                                onResetArtistPreferences()
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = SpotifyGreen
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Personalizar artistas favoritos",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Elige tus artistas para adaptar tus recomendaciones",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SpotifyDarkCard)
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = "Reproducción automática (Autoplay)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Reproduce temas similares y adaptados a tus gustos sin detenerse",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isAutoplayEnabled,
                            onCheckedChange = onToggleAutoplay,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = SpotifyGreen,
                                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                uncheckedTrackColor = SpotifyCardHover
                            )
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Activa o desactiva fuentes para personalizar el catálogo y streaming en Inicio:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(extensions, key = { it.id }) { ext ->
                    val isChecked = activeIds.contains(ext.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SpotifyDarkCard)
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = ext.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = ext.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Switch(
                            checked = isChecked,
                            onCheckedChange = { onToggle(ext.id) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = SpotifyGreen,
                                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                uncheckedTrackColor = SpotifyCardHover
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cerrar", color = SpotifyGreen, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = Color(0xFF1E1E1E),
        titleContentColor = Color.White,
        textContentColor = Color.White
    )
}
