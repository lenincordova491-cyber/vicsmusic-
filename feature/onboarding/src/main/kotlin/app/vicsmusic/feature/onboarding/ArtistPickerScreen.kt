package app.vicsmusic.feature.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vicsmusic.core.ui.component.ArtworkImage

private val SpotifyGreen = Color(0xFF1DB954)
private val DarkBackground = Color(0xFF121212)
private val CardBackground = Color(0xFF242424)
private val ChipBackground = Color(0xFF2A2A2A)

@Composable
fun ArtistPickerRoute(
    onOnboardingFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ArtistPickerScreen(
        uiState = uiState,
        onArtistToggled = viewModel::onArtistToggled,
        onArtistItemToggled = viewModel::onArtistItemToggled,
        onGenreSelected = viewModel::onGenreSelected,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onContinueClick = { viewModel.completeOnboarding(onOnboardingFinished) },
        modifier = modifier
    )
}

@Composable
fun ArtistPickerScreen(
    uiState: OnboardingUiState,
    onArtistToggled: (String) -> Unit,
    onArtistItemToggled: (ArtistItem) -> Unit,
    onGenreSelected: (String) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onContinueClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val query = uiState.searchQuery.trim().lowercase()
    val isSearching = query.isNotEmpty()

    val baseCatalog = if (uiState.selectedGenre == ArtistGenres.ALL) {
        ArtistCatalog.ALL_ARTISTS
    } else {
        ArtistCatalog.ALL_ARTISTS.filter { it.genre.equals(uiState.selectedGenre, ignoreCase = true) }
    }

    val filteredCatalog = if (isSearching) {
        ArtistCatalog.ALL_ARTISTS.filter {
            it.name.lowercase().contains(query) || it.genre.lowercase().contains(query)
        }
    } else {
        baseCatalog
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = DarkBackground
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 105.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Header section spanning full width
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Text(
                            text = "Elige 3 o más artistas que te gusten",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp
                            ),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Construiremos tu feed, mixes personalizados y radio según tus gustos musicales.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.LightGray
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        // Search Bar
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = onSearchQueryChanged,
                            placeholder = { Text("Buscar artistas, grupos o géneros...", color = Color.Gray) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Buscar",
                                    tint = Color.LightGray
                                )
                            },
                            trailingIcon = {
                                if (uiState.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { onSearchQueryChanged("") }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Limpiar búsqueda",
                                            tint = Color.LightGray
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = CardBackground,
                                unfocusedContainerColor = CardBackground,
                                focusedBorderColor = SpotifyGreen,
                                unfocusedBorderColor = Color.Transparent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Genre chips row (only visible when not searching)
                        if (!isSearching) {
                            Spacer(modifier = Modifier.height(14.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(ArtistGenres.GENRES) { genre ->
                                    val isSelected = uiState.selectedGenre == genre
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { onGenreSelected(genre) },
                                        label = {
                                            Text(
                                                text = genre,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 13.sp
                                            )
                                        },
                                        shape = RoundedCornerShape(20.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            containerColor = ChipBackground,
                                            labelColor = Color.White,
                                            selectedContainerColor = SpotifyGreen,
                                            selectedLabelColor = Color.Black
                                        ),
                                        border = null
                                    )
                                }
                            }
                        }

                        // Selected Artists Carousel (Visual preview with quick remove)
                        if (uiState.selectedArtists.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Artistas seleccionados (${uiState.selectedArtists.size})",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = SpotifyGreen
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(uiState.selectedArtists, key = { "selected_${it.id}" }) { artist ->
                                    SelectedArtistItem(
                                        artist = artist,
                                        onRemove = { onArtistToggled(artist.id) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Dynamic Suggested Similar Artists Section (if user made selections and not searching)
                if (uiState.suggestedArtists.isNotEmpty() && !isSearching) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = "Artistas recomendados para ti",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = SpotifyGreen,
                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                        )
                    }
                    items(uiState.suggestedArtists, key = { "suggested_${it.id}" }) { artist ->
                        ArtistBubble(
                            artist = artist,
                            isSelected = uiState.selectedArtistIds.contains(artist.id),
                            onClick = { onArtistItemToggled(artist) }
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = if (uiState.selectedGenre == ArtistGenres.ALL) "Explorar artistas" else uiState.selectedGenre,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                        )
                    }
                }

                // Main Artists Grid
                items(filteredCatalog, key = { it.id }) { artist ->
                    ArtistBubble(
                        artist = artist,
                        isSelected = uiState.selectedArtistIds.contains(artist.id),
                        onClick = { onArtistItemToggled(artist) }
                    )
                }

                // Remote Search Results from YouTube Music / InnerTube
                if (isSearching) {
                    if (uiState.isSearchingRemote) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = SpotifyGreen,
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Buscando más artistas en línea...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.LightGray
                                )
                            }
                        }
                    }

                    if (uiState.remoteArtists.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = "Encontrados en línea",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = SpotifyGreen,
                                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                            )
                        }
                        items(uiState.remoteArtists, key = { it.id }) { artist ->
                            ArtistBubble(
                                artist = artist,
                                isSelected = uiState.selectedArtistIds.contains(artist.id),
                                onClick = { onArtistItemToggled(artist) }
                            )
                        }
                    }
                }
            }

            // Floating Bottom Bar
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f),
                                Color.Black
                            )
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Button(
                    onClick = onContinueClick,
                    enabled = uiState.canContinue && !uiState.isSaving,
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SpotifyGreen,
                        contentColor = Color.Black,
                        disabledContainerColor = Color(0xFF333333),
                        disabledContentColor = Color.Gray
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            color = Color.Black,
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = if (uiState.canContinue) {
                                "Continuar (${uiState.selectedArtistIds.size} seleccionados)"
                            } else {
                                "Elige al menos 3 artistas (${uiState.selectionCountText})"
                            },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedArtistItem(
    artist: ArtistItem,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.width(64.dp)
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            ArtworkImage(
                artworkUrl = artist.imageUrl,
                contentDescription = artist.name,
                size = 54.dp,
                cornerRadius = 27.dp,
                modifier = Modifier.border(2.dp, SpotifyGreen, CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color.Red.copy(alpha = 0.9f))
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Quitar",
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ArtistBubble(
    artist: ArtistItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 0.94f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "artist_bubble_scale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            ArtworkImage(
                artworkUrl = artist.imageUrl,
                contentDescription = artist.name,
                size = 84.dp,
                cornerRadius = 42.dp,
                modifier = Modifier.border(
                    width = if (isSelected) 3.dp else 0.dp,
                    color = if (isSelected) SpotifyGreen else Color.Transparent,
                    shape = CircleShape
                )
            )

            // Spotify Selection Badge
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(SpotifyGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Seleccionado",
                        tint = Color.Black,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 13.sp
            ),
            color = if (isSelected) SpotifyGreen else Color.White,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = artist.genre,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = Color.Gray,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}
