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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
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
import app.vicsmusic.core.model.Track
import app.vicsmusic.core.ui.R
import app.vicsmusic.core.ui.component.EmptyContent
import app.vicsmusic.core.ui.component.ErrorContent
import app.vicsmusic.core.ui.component.ExtensionCard
import app.vicsmusic.core.ui.component.ShimmerList
import app.vicsmusic.core.ui.component.TrackRow

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
        onTrackClick = { track ->
            viewModel.playTrack(track)
            onNavigateToPlayer()
        },
        modifier = modifier
    )
}

@Composable
fun SearchScreen(
    uiState: SearchUiState,
    onQueryChange: (String) -> Unit,
    onFilterSelect: (SearchFilter) -> Unit,
    onTrackClick: (Track) -> Unit,
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
                .padding(top = 16.dp, bottom = 12.dp)
        )

        // Quick Filter Chips Row (Música, Podcasts, Audiolibros, etc.)
        val quickFilters = listOf(
            SearchFilter.ALL,
            SearchFilter.TRACKS,
            SearchFilter.PODCASTS,
            SearchFilter.AUDIOBOOKS,
            SearchFilter.ARTISTS
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
            uiState.filteredResults.isEmpty() -> {
                EmptyContent(
                    title = stringResource(R.string.search_no_results),
                    message = stringResource(R.string.search_no_results_desc),
                    modifier = Modifier.fillMaxSize()
                )
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
