package app.vicsmusic.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vicsmusic.core.domain.repository.MusicRepository
import app.vicsmusic.core.domain.usecase.SearchTracksUseCase
import app.vicsmusic.core.model.Artist
import app.vicsmusic.core.model.ExtensionItem
import app.vicsmusic.core.model.Playlist
import app.vicsmusic.core.model.Track
import app.vicsmusic.core.network.ExtensionRegistryClient
import app.vicsmusic.core.player.PlaybackConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SearchFilter(val displayName: String) {
    ALL("Todo"),
    TRACKS("Canciones"),
    PLAYLISTS("Playlists"),
    ARTISTS("Artistas"),
    ALBUMS("Álbumes"),
    PODCASTS("Podcasts")
}

data class SearchUiState(
    val query: String = "",
    val filter: SearchFilter = SearchFilter.ALL,
    val isSearching: Boolean = false,
    val results: List<Track> = emptyList(),
    val filteredResults: List<Track> = emptyList(),
    val matchingPlaylists: List<Playlist> = emptyList(),
    val matchingArtists: List<Artist> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    val suggestedSearches: List<String> = listOf("Canserbero", "Ke Personajes", "Viva Latino", "Top 50 Perú", "Agua Marina", "Grupo 5", "Rock en Español"),
    val catalogExtensions: List<ExtensionItem> = emptyList(),
    val isLoadingCatalog: Boolean = false,
    val errorMessage: String? = null
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchTracksUseCase: SearchTracksUseCase,
    private val playbackConnection: PlaybackConnection,
    private val extensionRegistryClient: ExtensionRegistryClient,
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val queryFlow = MutableStateFlow("")

    init {
        loadCatalog()
        observeQuery()
        observeSearchHistory()
    }

    private fun observeSearchHistory() {
        viewModelScope.launch {
            musicRepository.getSearchHistory(15).collect { history ->
                _uiState.update { it.copy(recentSearches = history) }
            }
        }
    }

    fun deleteRecentSearch(query: String) {
        viewModelScope.launch {
            musicRepository.deleteSearchHistory(query)
        }
    }

    fun clearAllRecentSearches() {
        viewModelScope.launch {
            musicRepository.clearSearchHistory()
        }
    }

    private fun observeQuery() {
        viewModelScope.launch {
            queryFlow
                .debounce(300)
                .distinctUntilChanged()
                .collectLatest { query ->
                    performSearch(query)
                }
        }
    }

    fun loadCatalog() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingCatalog = true) }
            val result = extensionRegistryClient.fetchRegistry(REGISTRY_URL)
            result.onSuccess { registry ->
                _uiState.update { it.copy(catalogExtensions = registry.extensions, isLoadingCatalog = false) }
            }.onFailure {
                // Default Spotify-style starter extensions fallback if offline
                _uiState.update {
                    it.copy(
                        catalogExtensions = defaultExtensions,
                        isLoadingCatalog = false
                    )
                }
            }
        }
    }

    fun onQueryChange(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }
        queryFlow.value = newQuery
    }

    fun onFilterSelect(filter: SearchFilter) {
        _uiState.update { current ->
            val filtered = filterTracks(current.results, filter)
            current.copy(filter = filter, filteredResults = filtered)
        }
    }

    private suspend fun performSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _uiState.update {
                it.copy(
                    isSearching = false,
                    results = emptyList(),
                    filteredResults = emptyList(),
                    errorMessage = null
                )
            }
            return
        }

        _uiState.update { it.copy(isSearching = true, errorMessage = null) }
        val result = searchTracksUseCase(trimmed)
        result.fold(
            onSuccess = { tracks ->
                val artists = tracks.map { it.artistName }.distinct().take(6).map { name ->
                    Artist(
                        id = name.lowercase().replace(" ", "_"),
                        sourceId = "ytmusic",
                        name = name,
                        imageUrl = tracks.firstOrNull { it.artistName == name }?.artworkUrl,
                        topTracks = tracks.filter { it.artistName == name }
                    )
                }
                val playlists = if (tracks.isNotEmpty()) {
                    listOf(
                        Playlist(
                            id = "pl_this_is_${trimmed.lowercase().replace(" ", "_")}",
                            title = "This Is ${trimmed.replaceFirstChar { it.uppercase() }}",
                            description = "Los temas esenciales y más reproducidos reunidos para ti",
                            artworkUrl = tracks.firstOrNull()?.artworkUrl,
                            trackCount = tracks.size
                        ),
                        Playlist(
                            id = "pl_hits_${trimmed.lowercase().replace(" ", "_")}",
                            title = "${trimmed.replaceFirstChar { it.uppercase() }} Hits 2026",
                            description = "Grandes éxitos y los favoritos del momento",
                            artworkUrl = tracks.getOrNull(1)?.artworkUrl ?: tracks.firstOrNull()?.artworkUrl,
                            trackCount = tracks.size
                        ),
                        Playlist(
                            id = "pl_radio_${trimmed.lowercase().replace(" ", "_")}",
                            title = "Radio de ${trimmed.replaceFirstChar { it.uppercase() }}",
                            description = "Música continua y sugerencias basadas en tu búsqueda",
                            artworkUrl = tracks.getOrNull(2)?.artworkUrl ?: tracks.firstOrNull()?.artworkUrl,
                            trackCount = tracks.size
                        )
                    )
                } else emptyList()

                _uiState.update { current ->
                    val filtered = filterTracks(tracks, current.filter)
                    current.copy(
                        isSearching = false,
                        results = tracks,
                        filteredResults = filtered,
                        matchingArtists = artists,
                        matchingPlaylists = playlists,
                        errorMessage = null
                    )
                }
            },
            onFailure = { error ->
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        errorMessage = error.localizedMessage ?: "Search failed"
                    )
                }
            }
        )
    }

    private fun filterTracks(tracks: List<Track>, filter: SearchFilter): List<Track> {
        val query = _uiState.value.query.trim().lowercase()
        return when (filter) {
            SearchFilter.ALL, SearchFilter.TRACKS, SearchFilter.PLAYLISTS -> tracks
            SearchFilter.PODCASTS -> {
                val matched = tracks.filter { it.artistName.contains("podcast", ignoreCase = true) || it.title.contains("podcast", ignoreCase = true) }
                matched.ifEmpty { tracks }
            }
            SearchFilter.ARTISTS -> {
                val matched = tracks.filter { it.artistName.lowercase().contains(query) }
                matched.ifEmpty { tracks }
            }
            SearchFilter.ALBUMS -> {
                val matched = tracks.filter { it.albumName?.lowercase()?.contains(query) == true }
                matched.ifEmpty { tracks }
            }
        }
    }

    fun playPlaylist(playlist: Playlist) {
        val tracks = _uiState.value.results
        if (tracks.isNotEmpty()) {
            playbackConnection.playQueue(tracks, 0)
        }
    }

    fun playTrack(track: Track) {
        val queue = _uiState.value.filteredResults.ifEmpty { listOf(track) }
        val index = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        playbackConnection.playQueue(queue, index)
    }

    companion object {
        const val REGISTRY_URL = "https://raw.githubusercontent.com/mornyeapp/Mornye-Extension/main/registry.json"

        val defaultExtensions = listOf(
            ExtensionItem(
                id = "spotify-web",
                name = "spotify-web",
                displayName = "Spotify Web",
                version = "1.10.6",
                description = "Metadatos, créditos de álbum y discografía con enriquecimiento de género",
                category = "integration",
                tags = listOf("spotify", "metadata", "catalog")
            ),
            ExtensionItem(
                id = "apple-music",
                name = "apple-music",
                displayName = "Apple Music",
                version = "1.4.16",
                description = "Metadatos y letras sincronizadas con caché optimizada",
                category = "integration",
                tags = listOf("apple", "lyrics", "metadata")
            ),
            ExtensionItem(
                id = "soundcloud",
                name = "soundcloud",
                displayName = "SoundCloud",
                version = "1.0.14",
                description = "Búsqueda completa y reproducción directa de streams AAC",
                category = "download",
                tags = listOf("soundcloud", "streaming", "aac")
            ),
            ExtensionItem(
                id = "ytmusic-spotiflac",
                name = "ytmusic-spotiflac",
                displayName = "YouTube Music",
                version = "2.4.6",
                description = "Catálogo universal de audio sin anuncios ni restricciones",
                category = "download",
                tags = listOf("youtube", "streaming", "lossless")
            )
        )
    }
}
