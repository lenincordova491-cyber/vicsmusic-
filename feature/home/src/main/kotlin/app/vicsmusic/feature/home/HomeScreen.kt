package app.vicsmusic.feature.home

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownloadDone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vicsmusic.core.model.Artist
import app.vicsmusic.core.model.ExtensionItem
import app.vicsmusic.core.model.Track
import app.vicsmusic.core.ui.component.ArtworkImage
import app.vicsmusic.core.ui.component.EmptyContent
import app.vicsmusic.core.ui.component.ShimmerList
import app.vicsmusic.core.ui.component.TrackRow
import coil3.compose.AsyncImage
import kotlin.math.abs

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
    var selectedChart by remember { mutableStateOf<HomeChart?>(null) }
    var selectedArtist by remember { mutableStateOf<Artist?>(null) }

    BackHandler(enabled = selectedChart != null || selectedArtist != null) {
        selectedChart = null
        selectedArtist = null
    }

    if (selectedChart != null) {
        ChartDetailScreen(
            chart = selectedChart!!,
            onBack = { selectedChart = null },
            onPlayTrack = { track, list ->
                viewModel.playTrack(track, list)
                onNavigateToPlayer()
            },
            onDownloadTrack = viewModel::downloadTrack,
            modifier = modifier
        )
    } else if (selectedArtist != null) {
        ArtistDetailScreen(
            artist = selectedArtist!!,
            onBack = { selectedArtist = null },
            onPlayTrack = { track, list ->
                viewModel.playTrack(track, list)
                onNavigateToPlayer()
            },
            onDownloadTrack = viewModel::downloadTrack,
            modifier = modifier
        )
    } else {
        HomeScreen(
            uiState = uiState,
            onTrackClick = { track, list ->
                viewModel.playTrack(track, list)
                onNavigateToPlayer()
            },
            onArtistClick = { artist ->
                selectedArtist = artist
            },
            onChartClick = { chart ->
                selectedChart = chart
            },
            onRefresh = viewModel::refreshCatalog,
            onFilterSelected = viewModel::onFilterSelected,
            onSettingsClick = { viewModel.setSettingsOpen(true) },
            onDismissSettings = { viewModel.setSettingsOpen(false) },
            onToggleExtension = viewModel::toggleExtension,
            onDownloadTrack = viewModel::downloadTrack,
            onResetArtistPreferences = viewModel::resetArtistPreferences,
            onToggleAutoplay = viewModel::toggleAutoplay,
            onOpenLogin = { viewModel.setLoginDialogOpen(true) },
            onDismissLogin = { viewModel.setLoginDialogOpen(false) },
            onLoginSuccess = viewModel::onYouTubeLoginSuccess,
            onSyncYouTube = viewModel::syncYouTubeAccount,
            onLogoutYouTube = viewModel::logoutYouTube,
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onTrackClick: (Track, List<Track>) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onChartClick: (HomeChart) -> Unit,
    onRefresh: () -> Unit,
    onFilterSelected: (HomeCategoryFilter) -> Unit,
    onSettingsClick: () -> Unit,
    onDismissSettings: () -> Unit,
    onToggleExtension: (String) -> Unit,
    onDownloadTrack: (Track) -> Unit,
    onResetArtistPreferences: () -> Unit = {},
    onToggleAutoplay: (Boolean) -> Unit = {},
    onOpenLogin: () -> Unit = {},
    onDismissLogin: () -> Unit = {},
    onLoginSuccess: (String, String?) -> Unit = { _, _ -> },
    onSyncYouTube: () -> Unit = {},
    onLogoutYouTube: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Spotify Top Bar: Greeting + Settings Gear (Tuerca de Ajustes)
            item(key = "top_bar") {
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

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (uiState.isYtmLoggedIn) {
                                    SpotifyGreen.copy(alpha = 0.2f)
                                } else {
                                    Color(0xFFE11D48).copy(alpha = 0.2f)
                                }
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .clickable {
                                    if (uiState.isYtmLoggedIn) {
                                        onSyncYouTube()
                                    } else {
                                        onOpenLogin()
                                    }
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                if (uiState.isSyncingYtm) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        color = SpotifyGreen,
                                        strokeWidth = 2.dp
                                    )
                                } else if (uiState.isYtmLoggedIn) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = SpotifyGreen,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Text(
                                    text = when {
                                        uiState.isSyncingYtm -> "Sincronizando..."
                                        uiState.isYtmLoggedIn -> "YT Music activo"
                                        else -> "Conectar YT Music"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (uiState.isYtmLoggedIn) SpotifyGreen else Color(0xFFFF4D6D)
                                )
                            }
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
            }

            // 2. Spotify Category Filter Chips ("Todos", "Música", "Descargas", "Podcasts")
            item(key = "category_filters") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item(key = "chip_all") {
                        SpotifyFilterChip(
                            label = "Todos",
                            selected = uiState.selectedFilter == HomeCategoryFilter.ALL,
                            onClick = { onFilterSelected(HomeCategoryFilter.ALL) }
                        )
                    }
                    item(key = "chip_music") {
                        SpotifyFilterChip(
                            label = "Música",
                            selected = uiState.selectedFilter == HomeCategoryFilter.MUSIC,
                            onClick = { onFilterSelected(HomeCategoryFilter.MUSIC) }
                        )
                    }
                    item(key = "chip_downloads") {
                        SpotifyFilterChip(
                            label = "Descargas (Offline)",
                            selected = uiState.selectedFilter == HomeCategoryFilter.DOWNLOADS,
                            onClick = { onFilterSelected(HomeCategoryFilter.DOWNLOADS) }
                        )
                    }
                    item(key = "chip_podcasts") {
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
                    item(key = "quick_access_grid") {
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

            // Tus Artistas Favoritos (Carrusel horizontal estilo Spotify con fotos circulares)
            if (uiState.favoriteArtists.isNotEmpty() && (uiState.selectedFilter == HomeCategoryFilter.ALL || uiState.selectedFilter == HomeCategoryFilter.MUSIC)) {
                item(key = "favorite_artists_carousel") {
                    SectionHeader(title = "Tus artistas favoritos")
                    Text(
                        text = "Basado en tus preferencias y reproducciones",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 4.dp)
                    ) {
                        items(uiState.favoriteArtists, key = { "fav_art_${it.id}" }) { artist ->
                            ArtistCircleCard(
                                artist = artist,
                                onClick = { onArtistClick(artist) }
                            )
                        }
                    }
                }
            }

            // Listas de Éxitos y Rankings (Top 50 Perú, Top 50 Global, Virales)
            if (uiState.topCharts.isNotEmpty() && (uiState.selectedFilter == HomeCategoryFilter.ALL || uiState.selectedFilter == HomeCategoryFilter.MUSIC)) {
                item(key = "top_charts_carousel") {
                    SectionHeader(title = "Listas de éxitos y rankings")
                    Text(
                        text = "Las canciones más escuchadas en Perú y el mundo",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 4.dp)
                    ) {
                        items(uiState.topCharts, key = { "top_chart_${it.id}" }) { chart ->
                            ChartCard(
                                chart = chart,
                                onClick = { onChartClick(chart) }
                            )
                        }
                    }
                }
            }

            // 4. Hero "Nuevo lanzamiento para ti" (Featured Release Banner)
            if (uiState.featuredRelease != null && (uiState.selectedFilter == HomeCategoryFilter.ALL || uiState.selectedFilter == HomeCategoryFilter.MUSIC)) {
                item(key = "featured_release_hero") {
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
                item(key = "daily_mix_carousel") {
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
                item(key = "recommended_discovery_carousel") {
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
                item(key = "trending_hits_carousel") {
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
                item(key = "cumbia_carousel") {
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
                item(key = "rock_pop_carousel") {
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
                item(key = "urban_carousel") {
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
                item(key = "salsa_carousel") {
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
                item(key = "podcasts_empty_view") {
                    EmptyContent(
                        title = "Podcasts próximamente",
                        message = "Explora tus canciones y artistas favoritos en las pestañas Todos, Música o Descargas.",
                        modifier = Modifier.fillMaxWidth().height(240.dp)
                    )
                }
            }

            // 8. Descargas (100% Offline Songs stored on device)
            if (uiState.selectedFilter == HomeCategoryFilter.DOWNLOADS || (uiState.selectedFilter == HomeCategoryFilter.ALL && uiState.downloadedTracks.isNotEmpty())) {
                item(key = "downloads_section_header") {
                    SectionHeader(title = "Descargas locales (100% Offline)")
                }
                if (uiState.downloadedTracks.isEmpty()) {
                    item(key = "downloads_empty_view") {
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
            item(key = "active_sources_section") {
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
                isYtmLoggedIn = uiState.isYtmLoggedIn,
                isSyncingYtm = uiState.isSyncingYtm,
                onToggle = onToggleExtension,
                onToggleAutoplay = onToggleAutoplay,
                onResetArtistPreferences = onResetArtistPreferences,
                onOpenLogin = {
                    onDismissSettings()
                    onOpenLogin()
                },
                onSyncYouTube = onSyncYouTube,
                onLogoutYouTube = onLogoutYouTube,
                onDismiss = onDismissSettings
            )
        }

        if (uiState.isLoginDialogOpen) {
            YouTubeLoginDialog(
                onLoginSuccess = onLoginSuccess,
                onDismiss = onDismissLogin
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
private fun ArtistCircleCard(
    artist: Artist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
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
        modifier = modifier
            .width(108.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(gradientColors)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.9f)
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
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "Artista",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ChartDetailScreen(
    chart: HomeChart,
    onBack: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onDownloadTrack: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    val gradientColors = when (chart.id) {
        "top_50_peru" -> listOf(Color(0xFF004D40), Color(0xFF1DB954))
        "top_50_global" -> listOf(Color(0xFF4A148C), Color(0xFF7C3AED))
        "virales_peru" -> listOf(Color(0xFF991B1B), Color(0xFFEA580C))
        "viva_latino" -> listOf(Color(0xFF9D174D), Color(0xFFE11D48))
        else -> listOf(Color(0xFF1E3A8A), Color(0xFF2563EB))
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121212)),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        item(key = "chart_top_nav") {
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
                    text = chart.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        item(key = "chart_header") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.verticalGradient(gradientColors))
                    .padding(20.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!chart.coverUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = chart.coverUrl,
                                    contentDescription = chart.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(
                                    text = chart.emoji,
                                    fontSize = 44.sp
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "PLAYLIST & RANKING",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = chart.title,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${chart.tracks.size} canciones • Actualizado hoy",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = chart.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        IconButton(
                            onClick = {
                                if (chart.tracks.isNotEmpty()) {
                                    onPlayTrack(chart.tracks.first(), chart.tracks)
                                }
                            },
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(SpotifyGreen)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Reproducir todo",
                                tint = Color.Black,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                if (chart.tracks.isNotEmpty()) {
                                    val shuffled = chart.tracks.shuffled()
                                    onPlayTrack(shuffled.first(), shuffled)
                                }
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = "Modo aleatorio",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }

        item(key = "chart_list_header") {
            Text(
                text = "Lista de canciones",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
            )
        }

        itemsIndexed(chart.tracks, key = { index, track -> "chart_item_${track.id}_$index" }) { index, track ->
            val rank = index + 1
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPlayTrack(track, chart.tracks) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "$rank",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (rank <= 3) FontWeight.Bold else FontWeight.Normal,
                    color = if (rank <= 3) SpotifyGreen else Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(28.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                ArtworkImage(
                    artworkUrl = track.artworkUrl ?: chart.coverUrl,
                    contentDescription = track.title,
                    size = 48.dp,
                    cornerRadius = 8.dp
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artistName,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = { onDownloadTrack(track) }) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Descargar",
                        tint = Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ArtistDetailScreen(
    artist: Artist,
    onBack: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onDownloadTrack: (Track) -> Unit,
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
                    text = "Artista verificado • Oyentes mensuales en aumento",
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
                            .background(Color.White.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Modo aleatorio",
                            tint = Color.White,
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
                modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp)
            )
        }

        if (artist.topTracks.isEmpty()) {
            item(key = "no_artist_tracks") {
                Text(
                    text = "Próximamente más temas de ${artist.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        } else {
            itemsIndexed(artist.topTracks, key = { idx, t -> "art_tr_${t.id}_$idx" }) { index, track ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPlayTrack(track, artist.topTracks) }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (index < 3) FontWeight.Bold else FontWeight.Normal,
                        color = if (index < 3) SpotifyGreen else Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(28.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    ArtworkImage(
                        artworkUrl = track.artworkUrl,
                        contentDescription = track.title,
                        size = 48.dp,
                        cornerRadius = 8.dp
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track.albumName ?: artist.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(onClick = { onDownloadTrack(track) }) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Descargar",
                            tint = Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartCard(
    chart: HomeChart,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gradientColors = when (chart.id) {
        "top_50_peru" -> listOf(Color(0xFF004D40), Color(0xFF1DB954))
        "top_50_global" -> listOf(Color(0xFF4A148C), Color(0xFF7C3AED))
        "virales_peru" -> listOf(Color(0xFF991B1B), Color(0xFFEA580C))
        "viva_latino" -> listOf(Color(0xFF9D174D), Color(0xFFE11D48))
        else -> listOf(Color(0xFF1E3A8A), Color(0xFF2563EB))
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        modifier = modifier
            .width(156.dp)
            .height(180.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!chart.coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = chart.coverUrl,
                    contentDescription = chart.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.25f),
                                    Color.Black.copy(alpha = 0.55f),
                                    Color.Black.copy(alpha = 0.92f)
                                )
                            )
                        )
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(gradientColors))
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = chart.emoji,
                        fontSize = 28.sp
                    )
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(SpotifyGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Reproducir",
                            tint = Color.Black,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = chart.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = chart.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
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
        Box(modifier = Modifier.size(136.dp)) {
            ArtworkImage(
                artworkUrl = track.artworkUrl,
                contentDescription = track.title,
                size = 136.dp,
                cornerRadius = 8.dp
            )

            // Botón flotante para descargar en el dispositivo
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.75f))
                    .clickable { onDownloadClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Descargar en el móvil",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
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
    isYtmLoggedIn: Boolean = false,
    isSyncingYtm: Boolean = false,
    onToggle: (String) -> Unit,
    onToggleAutoplay: (Boolean) -> Unit,
    onResetArtistPreferences: () -> Unit,
    onOpenLogin: () -> Unit = {},
    onSyncYouTube: () -> Unit = {},
    onLogoutYouTube: () -> Unit = {},
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
                modifier = Modifier.fillMaxWidth().height(420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isYtmLoggedIn) SpotifyGreen.copy(alpha = 0.18f)
                                else Color(0xFFE11D48).copy(alpha = 0.18f)
                            )
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isYtmLoggedIn) Icons.Default.Check else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = if (isYtmLoggedIn) SpotifyGreen else Color(0xFFFF4D6D)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isYtmLoggedIn) "YouTube Music Conectado" else "Conectar cuenta de YouTube Music",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = if (isYtmLoggedIn) {
                                        "Tus gustos, favoritos, historial y búsquedas están sincronizados"
                                    } else {
                                        "Inicia sesión con Google para extraer tus gustos, mixes y búsquedas personalizadas"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isYtmLoggedIn) {
                                TextButton(onClick = onSyncYouTube, enabled = !isSyncingYtm) {
                                    Text(
                                        text = if (isSyncingYtm) "Sincronizando..." else "Sincronizar ahora",
                                        color = SpotifyGreen,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                TextButton(onClick = onLogoutYouTube) {
                                    Text(
                                        text = "Cerrar sesión",
                                        color = Color(0xFFFF4D6D),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                TextButton(onClick = onOpenLogin) {
                                    Text(
                                        text = "Iniciar sesión con Google",
                                        color = SpotifyGreen,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

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
