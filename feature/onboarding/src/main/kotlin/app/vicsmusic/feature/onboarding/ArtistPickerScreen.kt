package app.vicsmusic.feature.onboarding

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
private val CardBackground = Color(0xFF1E1E1E)

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
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onContinueClick = { viewModel.completeOnboarding(onOnboardingFinished) },
        modifier = modifier
    )
}

@Composable
fun ArtistPickerScreen(
    uiState: OnboardingUiState,
    onArtistToggled: (String) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onContinueClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val query = uiState.searchQuery.trim().lowercase()
    val filteredArtists = if (query.isEmpty()) {
        ArtistCatalog.ALL_ARTISTS
    } else {
        ArtistCatalog.ALL_ARTISTS.filter {
            it.name.lowercase().contains(query) || it.genre.lowercase().contains(query)
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = DarkBackground
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Title and instructions
                item(span = { GridItemSpan(3) }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Text(
                            text = "Elige 3 o más artistas que te gusten",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 26.sp
                            ),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Personalizaremos tu música y catálogo según tus artistas favoritos.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.LightGray
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // Search Box
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = onSearchQueryChanged,
                            placeholder = { Text("Buscar artistas o géneros (Cumbia, Rock...)") },
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
                    }
                }

                // Dynamic Suggested Similar Artists Section (if user has made selections)
                if (uiState.suggestedArtists.isNotEmpty() && query.isEmpty()) {
                    item(span = { GridItemSpan(3) }) {
                        Text(
                            text = "Artistas similares que podrían gustarte",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = SpotifyGreen,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }
                    items(uiState.suggestedArtists, key = { "suggested_${it.id}" }) { artist ->
                        ArtistBubble(
                            artist = artist,
                            isSelected = uiState.selectedArtistIds.contains(artist.id),
                            onClick = { onArtistToggled(artist.id) }
                        )
                    }
                    item(span = { GridItemSpan(3) }) {
                        Text(
                            text = "Todos los artistas",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                        )
                    }
                }

                // Main Artists Grid
                items(filteredArtists, key = { it.id }) { artist ->
                    ArtistBubble(
                        artist = artist,
                        isSelected = uiState.selectedArtistIds.contains(artist.id),
                        onClick = { onArtistToggled(artist.id) }
                    )
                }
            }

            // Floating Bottom "Continuar" Bar
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Button(
                    onClick = onContinueClick,
                    enabled = uiState.canContinue && !uiState.isSaving,
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SpotifyGreen,
                        contentColor = Color.Black,
                        disabledContainerColor = Color.DarkGray,
                        disabledContentColor = Color.LightGray
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            color = Color.Black,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = if (uiState.canContinue) "Continuar" else "Elige al menos 3 artistas (${uiState.selectionCountText})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistBubble(
    artist: ArtistItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            ArtworkImage(
                artworkUrl = artist.imageUrl,
                contentDescription = artist.name,
                size = 88.dp,
                cornerRadius = 44.dp,
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
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(SpotifyGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Seleccionado",
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            ),
            color = if (isSelected) SpotifyGreen else Color.White,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = artist.genre,
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
    }
}
