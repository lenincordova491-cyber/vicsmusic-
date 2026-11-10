package app.vicsmusic.feature.search

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vicsmusic.core.model.Artist
import app.vicsmusic.core.model.Playlist
import app.vicsmusic.core.model.Track
import app.vicsmusic.core.ui.R
import app.vicsmusic.core.ui.component.ArtworkImage
import app.vicsmusic.core.ui.component.EmptyContent
import app.vicsmusic.core.ui.component.ErrorContent
import app.vicsmusic.core.ui.component.ExtensionCard
import app.vicsmusic.core.ui.component.ShimmerList
import app.vicsmusic.core.ui.component.TrackRow

private val SpotifyGreen = Color(0xFF1DB954)

@Composable
fun SearchRoute(
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
    onNavigateToPlayer: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Intercept Back press if query is not empty to clear query instead of popping to Home!
    BackHandler(enabled = uiState.query.isNotEmpty()) {
        viewModel.onQueryChange("")
    }

    SearchScreen(
        uiState = uiState,
        onQueryChange = viewModel::onQueryChange,
        onFilterSelect = viewModel::onFilterSelect,
        onDeleteRecentSearch = viewModel::deleteRecentSearch,
        onClearHistory = viewModel::clearAllRecentSearches,
        onTrackClick = { track ->
            viewModel.playTrack(track)
            onNavigateToPlayer()
        },
        onPlaylistClick = { playlist ->
            viewModel.playPlaylist(playlist)
            onNavigateToPlayer()
        },
        onArtistClick = { artist ->
            if (artist.topTracks.isNotEmpty()) {
                viewModel.playTrack(artist.topTracks.first())
                onNavigateToPlayer()
            } else {
                viewModel.onQueryChange(artist.name)
            }
        },
        modifier = modifier
    )
}

@Composable
fun SearchScreen(
    uiState: SearchUiState,
    onQueryChange: (String) -> Unit,
    onFilterSelect: (SearchFilter) -> Unit,
    onDeleteRecentSearch: (String) -> Unit = {},
    onClearHistory: () -> Unit = {},
    onTrackClick: (Track) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onArtistClick: (Artist) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Search Input Bar: "¿Qué quieres escuchar?"
        OutlinedTextField(
            value = uiState.query,
            onValueChange = onQueryChange,
            placeholder = {
                Text(
                    text = "¿Qué quieres escuchar?",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(R.string.nav_search),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (uiState.query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Limpiar búsqueda"
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 8.dp)
        )

        // Barra de autocompletado en tiempo real (< 50 ms) mientras escribe el usuario
        if (uiState.query.isNotBlank() && (uiState.liveSuggestions.isNotEmpty() || uiState.directSuggestedTracks.isNotEmpty())) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                items(
                    items = uiState.directSuggestedTracks,
                    key = { "dir_sug_${it.id}" }
                ) { directTrack ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2A22)),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.clickable { onTrackClick(directTrack) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            ArtworkImage(
                                artworkUrl = directTrack.artworkUrl,
                                contentDescription = directTrack.title,
                                size = 22.dp,
                                cornerRadius = 4.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${directTrack.title} • ${directTrack.artistName}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = SpotifyGreen,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                items(
                    items = uiState.liveSuggestions,
                    key = { "txt_sug_$it" }
                ) { suggestion ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF242424)),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.clickable { onQueryChange(suggestion) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = SpotifyGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = suggestion,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // Quick Filter Chips Row (Todo, Canciones, Playlists, Artistas, Álbumes, Podcasts)
        val quickFilters = listOf(
            SearchFilter.ALL,
            SearchFilter.TRACKS,
            SearchFilter.PLAYLISTS,
            SearchFilter.ARTISTS,
            SearchFilter.ALBUMS,
            SearchFilter.PODCASTS
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            items(quickFilters) { filter ->
                FilterChip(
                    selected = uiState.filter == filter,
                    onClick = { onFilterSelect(filter) },
                    label = { Text(filter.displayName) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }

        // Search Content / Spotify Style Catalog
        when {
            uiState.isSearching -> {
                ShimmerList(count = 6, modifier = Modifier.fillMaxSize())
            }
            uiState.errorMessage != null -> {
                ErrorContent(
                    message = uiState.errorMessage,
                    onRetry = { onQueryChange(uiState.query) },
                    modifier = Modifier.fillMaxSize()
                )
            }
            uiState.query.trim().isEmpty() -> {
                // Spotify-styled Catalog View with Categories & Personalized Hashtags
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    // 0. Búsquedas recientes / Historial Spotify
                    if (uiState.recentSearches.isNotEmpty()) {
                        item(span = { GridItemSpan(2) }) {
                            Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Búsquedas recientes",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    TextButton(onClick = onClearHistory) {
                                        Text(
                                            text = "Borrar todo",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color.Gray
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    uiState.recentSearches.take(6).forEach { recent ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { onQueryChange(recent) }
                                                .padding(vertical = 8.dp, horizontal = 4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.History,
                                                contentDescription = null,
                                                tint = Color.Gray,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = recent,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(
                                                onClick = { onDeleteRecentSearch(recent) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Eliminar de historial",
                                                    tint = Color.Gray,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else if (uiState.suggestedSearches.isNotEmpty()) {
                        item(span = { GridItemSpan(2) }) {
                            Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)) {
                                Text(
                                    text = "Tendencias de búsqueda",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(uiState.suggestedSearches) { suggested ->
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFF262626)),
                                            shape = RoundedCornerShape(20.dp),
                                            modifier = Modifier.clickable { onQueryChange(suggested) }
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Search,
                                                    contentDescription = null,
                                                    tint = SpotifyGreen,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = suggested,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 1. Descubre algo más para ti (#Hashtags)
                    item(span = { GridItemSpan(2) }) {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp)) {
                            Text(
                                text = "Descubre algo más para ti",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Etiquetas personalizadas según tus hábitos de escucha",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(SearchCategoryProvider.personalizedHashtags) { hashtag ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF262626)),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.clickable {
                                            onQueryChange(hashtag.removePrefix("#"))
                                        }
                                    ) {
                                        Text(
                                            text = hashtag,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1DB954),
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 2. Descubre algo nuevo para ti (Categorías en cuadrados de colores)
                    item(span = { GridItemSpan(2) }) {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)) {
                            Text(
                                text = "Descubre algo nuevo para ti",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Explorar todo por categorías, géneros y colecciones",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    items(
                        items = SearchCategoryProvider.categories,
                        key = { it.id }
                    ) { category ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(104.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onQueryChange(category.searchQuery) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Color(category.startColor), Color(category.endColor))
                                        )
                                    )
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = category.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .fillMaxWidth(0.72f)
                                )

                                Text(
                                    text = category.iconEmoji,
                                    fontSize = 32.sp,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .graphicsLayer {
                                            rotationZ = -10f
                                        }
                                )
                            }
                        }
                    }

                    // 3. Extensiones y fuentes de catálogo
                    item(span = { GridItemSpan(2) }) {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 4.dp)) {
                            Text(
                                text = "Fuentes y extensiones",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Catálogo universal de streaming con metadatos y letras sincronizadas",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    items(
                        items = uiState.catalogExtensions,
                        key = { "ext_${it.id}" },
                        span = { GridItemSpan(2) }
                    ) { extension ->
                        ExtensionCard(
                            extension = extension,
                            onClick = { onQueryChange(extension.name) }
                        )
                    }
                }
            }
            uiState.filteredResults.isEmpty() && uiState.matchingPlaylists.isEmpty() -> {
                EmptyContent(
                    title = stringResource(R.string.search_no_results),
                    message = stringResource(R.string.search_no_results_desc),
                    modifier = Modifier.fillMaxSize()
                )
            }
            uiState.filter == SearchFilter.PLAYLISTS -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    items(uiState.matchingPlaylists, key = { it.id }) { playlist ->
                        SearchPlaylistCard(
                            playlist = playlist,
                            onClick = { onPlaylistClick(playlist) }
                        )
                    }
                }
            }
            uiState.filter == SearchFilter.ARTISTS -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    items(uiState.matchingArtists, key = { it.id }) { artist ->
                        SearchArtistRow(
                            artist = artist,
                            onClick = { onArtistClick(artist) }
                        )
                    }
                }
            }
            uiState.filter == SearchFilter.ALL -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    // 1. Mejor Resultado
                    val top = uiState.topResultTrack ?: uiState.filteredResults.firstOrNull()
                    if (top != null) {
                        item(key = "top_result_header") {
                            Text(
                                text = "Mejor resultado",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                            )
                            TopResultCard(
                                track = top,
                                onClick = { onTrackClick(top) }
                            )
                        }
                    }

                    // 2. Playlists Destacadas de la búsqueda
                    if (uiState.matchingPlaylists.isNotEmpty()) {
                        item(key = "playlists_shelf_all") {
                            Text(
                                text = "Playlists",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(uiState.matchingPlaylists, key = { "pl_row_${it.id}" }) { playlist ->
                                    SearchPlaylistCard(
                                        playlist = playlist,
                                        onClick = { onPlaylistClick(playlist) },
                                        modifier = Modifier.width(150.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 3. Artistas en círculos
                    if (uiState.matchingArtists.isNotEmpty()) {
                        item(key = "artists_shelf_all") {
                            Text(
                                text = "Artistas",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(uiState.matchingArtists, key = { "art_row_${it.id}" }) { artist ->
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .width(96.dp)
                                            .clickable { onArtistClick(artist) }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(80.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF262626))
                                        ) {
                                            ArtworkImage(
                                                artworkUrl = artist.imageUrl,
                                                contentDescription = artist.name,
                                                size = 80.dp,
                                                cornerRadius = 40.dp
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = artist.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Artista",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 4. Canciones
                    item(key = "songs_header_all") {
                        Text(
                            text = "Canciones",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }

                    items(
                        items = uiState.filteredResults,
                        key = { it.id }
                    ) { track ->
                        TrackRow(
                            track = track,
                            onClick = { onTrackClick(track) }
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    items(
                        items = uiState.filteredResults,
                        key = { it.id }
                    ) { track ->
                        TrackRow(
                            track = track,
                            onClick = { onTrackClick(track) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopResultCard(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArtworkImage(
                artworkUrl = track.artworkUrl,
                contentDescription = track.title,
                size = 76.dp,
                cornerRadius = 8.dp
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "Canción",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.LightGray,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = track.artistName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1DB954)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Reproducir",
                    tint = Color.Black,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

@Composable
private fun SearchPlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            ArtworkImage(
                artworkUrl = playlist.artworkUrl,
                contentDescription = playlist.title,
                size = 130.dp,
                cornerRadius = 8.dp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = playlist.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = playlist.description ?: "Playlist",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SearchArtistRow(
    artist: Artist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
    ) {
        ArtworkImage(
            artworkUrl = artist.imageUrl,
            contentDescription = artist.name,
            size = 56.dp,
            cornerRadius = 28.dp
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Artista",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
