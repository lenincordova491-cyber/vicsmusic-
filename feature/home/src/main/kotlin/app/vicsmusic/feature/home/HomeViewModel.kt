package app.vicsmusic.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vicsmusic.core.domain.engine.AdaptiveRecommendationEngine
import app.vicsmusic.core.domain.engine.ListeningEvent
import app.vicsmusic.core.domain.repository.MusicRepository
import app.vicsmusic.core.domain.repository.UserPreferencesRepository
import app.vicsmusic.core.model.ExtensionItem
import app.vicsmusic.core.model.Track
import app.vicsmusic.core.player.PlaybackConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

enum class HomeCategoryFilter {
    ALL,
    MUSIC,
    PODCASTS,
    DOWNLOADS
}

data class HomeUiState(
    val isLoading: Boolean = true,
    val greeting: String = "Buenas tardes",
    val selectedFilter: HomeCategoryFilter = HomeCategoryFilter.ALL,
    val quickAccessTracks: List<Track> = emptyList(),
    val featuredRelease: Track? = null,
    val dailyMix: List<Track> = emptyList(),
    val recommendedTracks: List<Track> = emptyList(),
    val trendingTracks: List<Track> = emptyList(),
    val downloadedTracks: List<Track> = emptyList(),
    val availableExtensions: List<ExtensionItem> = emptyList(),
    val activeExtensionIds: Set<String> = setOf("ytmusic-spotiflac", "spotify-web", "soundcloud"),
    val isSettingsOpen: Boolean = false,
    val downloadingTrackId: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val playbackConnection: PlaybackConnection,
    private val recommendationEngine: AdaptiveRecommendationEngine = AdaptiveRecommendationEngine()
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        HomeUiState(
            isLoading = false,
            quickAccessTracks = STARTER_TRACKS.take(6),
            featuredRelease = STARTER_TRACKS.first(),
            dailyMix = STARTER_TRACKS.drop(1).take(5),
            recommendedTracks = STARTER_TRACKS.takeLast(4),
            trendingTracks = STARTER_TRACKS
        )
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        updateGreeting()
        loadExtensions()
        seedStarterTracks()
        observeData()
        fetchOnlineCatalog()
    }

    private fun seedStarterTracks() {
        viewModelScope.launch {
            musicRepository.seedCatalogTracks(STARTER_TRACKS)
        }
    }

    private fun updateGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when (hour) {
            in 5..11 -> "Buenos días"
            in 12..19 -> "Buenas tardes"
            else -> "Buenas noches"
        }
        _uiState.update { it.copy(greeting = greeting) }
    }

    private fun loadExtensions() {
        viewModelScope.launch {
            val extensions = musicRepository.getAvailableExtensions().getOrDefault(emptyList())
            _uiState.update { it.copy(availableExtensions = extensions) }
        }
    }

    private fun observeData() {
        viewModelScope.launch {
            combine(
                musicRepository.getAllTracks(),
                musicRepository.getDownloadedTracks(),
                musicRepository.getRecentTracks(20),
                musicRepository.getPlayHistory(50),
                userPreferencesRepository.userPreferences
            ) { allTracks, downloaded, recents, history, prefs ->
                val validTracks = allTracks.filter { it.sourceId == "local" || !it.artworkUrl.isNullOrBlank() }
                val effectiveTracks = validTracks.ifEmpty { STARTER_TRACKS }
                val likedTrackIds = allTracks.filter { it.isLiked }.map { it.id }.toSet()

                val recommendations = recommendationEngine.computeRecommendations(
                    allAvailableTracks = effectiveTracks,
                    listeningHistory = history,
                    favoriteTrackIds = likedTrackIds,
                    favoriteArtistNames = prefs.favoriteArtistIds
                )

                // 6 tracks for Spotify 2x3 quick access grid (local or valid artwork)
                val cleanRecents = recents.filter { it.sourceId == "local" || !it.artworkUrl.isNullOrBlank() }
                val prioritized = if (prefs.favoriteArtistIds.isNotEmpty()) {
                    effectiveTracks.sortedByDescending { track ->
                        val isFav = prefs.favoriteArtistIds.any { fav ->
                            track.artistId.equals(fav, ignoreCase = true) ||
                            track.artistName.equals(fav, ignoreCase = true) ||
                            fav.replace("_", " ").equals(track.artistName, ignoreCase = true)
                        }
                        if (isFav) 100f else 0f
                    }
                } else {
                    effectiveTracks
                }
                val quickAccess = cleanRecents.take(6).ifEmpty { prioritized.take(6) }

                // Hero featured banner (first recommendation or top track matching favorites)
                val hero = recommendations.dailyMix.firstOrNull { !it.artworkUrl.isNullOrBlank() }
                    ?: prioritized.firstOrNull { !it.artworkUrl.isNullOrBlank() }
                    ?: effectiveTracks.first()

                val activeSources = if (prefs.activeSourceIds.isNotEmpty()) {
                    prefs.activeSourceIds
                } else {
                    _uiState.value.availableExtensions.map { it.id }.toSet() + setOf(
                        "local", "ytmusic", "ytmusic-spotiflac", "spotify-web", "soundcloud"
                    )
                }

                val currentTrending = if (_uiState.value.trendingTracks.isNotEmpty()) {
                    _uiState.value.trendingTracks.filter { !it.artworkUrl.isNullOrBlank() }.ifEmpty { STARTER_TRACKS }
                } else {
                    STARTER_TRACKS
                }

                _uiState.value.copy(
                    isLoading = false,
                    quickAccessTracks = quickAccess,
                    featuredRelease = hero,
                    dailyMix = recommendations.dailyMix.filter { !it.artworkUrl.isNullOrBlank() }.ifEmpty { prioritized.drop(1).take(5) },
                    recommendedTracks = recommendations.recommendedForYou.filter { !it.artworkUrl.isNullOrBlank() }.ifEmpty { prioritized.takeLast(4) },
                    trendingTracks = currentTrending,
                    downloadedTracks = downloaded,
                    activeExtensionIds = activeSources
                )
            }.catch { throwable ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = throwable.localizedMessage ?: "Error cargando catálogo"
                    )
                }
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    private fun fetchOnlineCatalog() {
        viewModelScope.launch {
            // Also fetch online tracks for user's favorite artists if set
            val prefs = userPreferencesRepository.userPreferences.first()
            if (prefs.favoriteArtistIds.isNotEmpty()) {
                for (artist in prefs.favoriteArtistIds.take(3)) {
                    musicRepository.searchTracks(artist)
                }
            }
            val trending = musicRepository.searchTracks("Top Global").getOrNull()
                ?: musicRepository.searchTracks("Hits").getOrDefault(emptyList())
            if (trending.isNotEmpty()) {
                _uiState.update { it.copy(trendingTracks = trending) }
            }
        }
    }

    fun resetArtistPreferences() {
        viewModelScope.launch {
            userPreferencesRepository.setOnboardingCompleted(false)
        }
    }

    fun onFilterSelected(filter: HomeCategoryFilter) {
        _uiState.update { it.copy(selectedFilter = filter) }
    }

    fun setSettingsOpen(isOpen: Boolean) {
        _uiState.update { it.copy(isSettingsOpen = isOpen) }
    }

    fun toggleExtension(extensionId: String) {
        viewModelScope.launch {
            val current = _uiState.value.activeExtensionIds.toMutableSet()
            if (current.contains(extensionId)) {
                current.remove(extensionId)
            } else {
                current.add(extensionId)
            }
            _uiState.update { it.copy(activeExtensionIds = current) }
            userPreferencesRepository.setActiveSourceIds(current)
        }
    }

    fun playTrack(track: Track, fromQueue: List<Track> = emptyList()) {
        viewModelScope.launch {
            // Record listening event for adaptive engine
            musicRepository.recordPlayHistory(track.id, 0.8f)
        }
        if (fromQueue.isNotEmpty()) {
            val index = fromQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
            playbackConnection.playQueue(fromQueue, index)
        } else {
            playbackConnection.playTrack(track)
        }
    }

    fun downloadTrack(track: Track) {
        viewModelScope.launch {
            _uiState.update { it.copy(downloadingTrackId = track.id) }
            musicRepository.downloadTrack(track)
            _uiState.update { it.copy(downloadingTrackId = null) }
        }
    }

    fun deleteDownloadedTrack(trackId: String) {
        viewModelScope.launch {
            musicRepository.deleteDownloadedTrack(trackId)
        }
    }

    fun syncLocalTracks() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            musicRepository.syncLocalTracks()
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    companion object {
        val STARTER_TRACKS = listOf(
            // Cumbia
            Track(
                id = "yt_w_LWtxTDr44",
                sourceId = "ytmusic",
                title = "Motor y Motivo",
                artistId = "grupo_5",
                artistName = "Grupo 5",
                albumId = "motor_y_motivo",
                albumName = "Motor y Motivo",
                durationMs = 230000L,
                artworkUrl = "https://i.ytimg.com/vi/w_LWtxTDr44/hqdefault.jpg"
            ),
            Track(
                id = "yt_o1HSd7QldRs",
                sourceId = "ytmusic",
                title = "El Ritmo de Mi Corazón",
                artistId = "grupo_5",
                artistName = "Grupo 5",
                albumId = "el_ritmo",
                albumName = "El Ritmo de Mi Corazón",
                durationMs = 245000L,
                artworkUrl = "https://i.ytimg.com/vi/o1HSd7QldRs/hqdefault.jpg"
            ),
            Track(
                id = "yt_E7wJTI-1dvQ",
                sourceId = "ytmusic",
                title = "Un Finde",
                artistId = "ke_personajes",
                artistName = "Ke Personajes",
                albumId = "un_finde",
                albumName = "Un Finde (CROSSOVER #2)",
                durationMs = 170000L,
                artworkUrl = "https://i.ytimg.com/vi/E7wJTI-1dvQ/hqdefault.jpg"
            ),
            Track(
                id = "yt_UYk01v661zA",
                sourceId = "ytmusic",
                title = "Ojitos Rojos",
                artistId = "ke_personajes",
                artistName = "Ke Personajes",
                albumId = "ojitos_rojos",
                albumName = "Ojitos Rojos",
                durationMs = 210000L,
                artworkUrl = "https://i.ytimg.com/vi/UYk01v661zA/hqdefault.jpg"
            ),
            Track(
                id = "yt_k76BgIb89-s",
                sourceId = "ytmusic",
                title = "Nunca Es Suficiente",
                artistId = "angeles_azules",
                artistName = "Los Ángeles Azules",
                albumId = "esto_si_es_cumbia",
                albumName = "Esto Sí Es Cumbia",
                durationMs = 265000L,
                artworkUrl = "https://i.ytimg.com/vi/k76BgIb89-s/hqdefault.jpg"
            ),
            Track(
                id = "yt_MSFR0st6yJM",
                sourceId = "ytmusic",
                title = "Tu Traición Se Acabó",
                artistId = "agua_marina",
                artistName = "Agua Marina",
                albumId = "tu_traicion",
                albumName = "Tu Traición Se Acabó",
                durationMs = 250000L,
                artworkUrl = "https://i.ytimg.com/vi/MSFR0st6yJM/hqdefault.jpg"
            ),
            Track(
                id = "yt_ROA6lTFZTbA",
                sourceId = "ytmusic",
                title = "El Cervecero",
                artistId = "armonia_10",
                artistName = "Armonía 10",
                albumId = "el_cervecero",
                albumName = "El Cervecero",
                durationMs = 240000L,
                artworkUrl = "https://i.ytimg.com/vi/ROA6lTFZTbA/hqdefault.jpg"
            ),

            // Rock & Pop
            Track(
                id = "yt_dvgZkm1xWPE",
                sourceId = "ytmusic",
                title = "Viva La Vida",
                artistId = "coldplay",
                artistName = "Coldplay",
                albumId = "viva_la_vida",
                albumName = "Viva La Vida",
                durationMs = 242000L,
                artworkUrl = "https://i.ytimg.com/vi/dvgZkm1xWPE/hqdefault.jpg"
            ),
            Track(
                id = "yt_yKNxeF4KMsY",
                sourceId = "ytmusic",
                title = "Yellow",
                artistId = "coldplay",
                artistName = "Coldplay",
                albumId = "parachutes",
                albumName = "Parachutes",
                durationMs = 269000L,
                artworkUrl = "https://i.ytimg.com/vi/yKNxeF4KMsY/hqdefault.jpg"
            ),
            Track(
                id = "yt_fJ9rUzIMcZQ",
                sourceId = "ytmusic",
                title = "Bohemian Rhapsody",
                artistId = "queen",
                artistName = "Queen",
                albumId = "a_night_at_the_opera",
                albumName = "A Night at the Opera",
                durationMs = 354000L,
                artworkUrl = "https://i.ytimg.com/vi/fJ9rUzIMcZQ/hqdefault.jpg"
            ),
            Track(
                id = "yt_T_FkEw27XJ0",
                sourceId = "ytmusic",
                title = "De Música Ligera",
                artistId = "soda_stereo",
                artistName = "Soda Stereo",
                albumId = "cancion_animal",
                albumName = "Canción Animal",
                durationMs = 212000L,
                artworkUrl = "https://i.ytimg.com/vi/T_FkEw27XJ0/hqdefault.jpg"
            ),
            Track(
                id = "yt_7wtfhZwyrcc",
                sourceId = "ytmusic",
                title = "Believer",
                artistId = "imagine_dragons",
                artistName = "Imagine Dragons",
                albumId = "evolve",
                albumName = "Evolve",
                durationMs = 204000L,
                artworkUrl = "https://i.ytimg.com/vi/7wtfhZwyrcc/hqdefault.jpg"
            ),
            Track(
                id = "yt_4NRXx6U8ABQ",
                sourceId = "ytmusic",
                title = "Blinding Lights",
                artistId = "the_weeknd",
                artistName = "The Weeknd",
                albumId = "after_hours",
                albumName = "After Hours",
                durationMs = 200000L,
                artworkUrl = "https://i.ytimg.com/vi/4NRXx6U8ABQ/hqdefault.jpg"
            ),
            Track(
                id = "yt_UqyT8IEBkvY",
                sourceId = "ytmusic",
                title = "24K Magic",
                artistId = "bruno_mars",
                artistName = "Bruno Mars",
                albumId = "24k_magic",
                albumName = "24K Magic",
                durationMs = 226000L,
                artworkUrl = "https://i.ytimg.com/vi/UqyT8IEBkvY/hqdefault.jpg"
            ),
            Track(
                id = "yt_TUVcZfQe-Kw",
                sourceId = "ytmusic",
                title = "Levitating",
                artistId = "dua_lipa",
                artistName = "Dua Lipa",
                albumId = "future_nostalgia",
                albumName = "Future Nostalgia",
                durationMs = 203000L,
                artworkUrl = "https://i.ytimg.com/vi/TUVcZfQe-Kw/hqdefault.jpg"
            ),
            Track(
                id = "yt_JGwWNGJdvx8",
                sourceId = "ytmusic",
                title = "Shape of You",
                artistId = "ed_sheeran",
                artistName = "Ed Sheeran",
                albumId = "divide",
                albumName = "÷ (Divide)",
                durationMs = 233000L,
                artworkUrl = "https://i.ytimg.com/vi/JGwWNGJdvx8/hqdefault.jpg"
            ),
            Track(
                id = "yt_ic8j13piAhQ",
                sourceId = "ytmusic",
                title = "Cruel Summer",
                artistId = "taylor_swift",
                artistName = "Taylor Swift",
                albumId = "lover",
                albumName = "Lover",
                durationMs = 178000L,
                artworkUrl = "https://i.ytimg.com/vi/ic8j13piAhQ/hqdefault.jpg"
            ),
            Track(
                id = "yt_eVli-tstM5E",
                sourceId = "ytmusic",
                title = "Espresso",
                artistId = "sabrina_carpenter",
                artistName = "Sabrina Carpenter",
                albumId = "short_n_sweet",
                albumName = "Short n' Sweet",
                durationMs = 175000L,
                artworkUrl = "https://i.ytimg.com/vi/eVli-tstM5E/hqdefault.jpg"
            ),

            // Urbano
            Track(
                id = "yt_QpKkNIT84GY",
                sourceId = "ytmusic",
                title = "Tití Me Preguntó",
                artistId = "bad_bunny",
                artistName = "Bad Bunny",
                albumId = "un_verano_sin_ti",
                albumName = "Un Verano Sin Ti",
                durationMs = 243000L,
                artworkUrl = "https://i.ytimg.com/vi/QpKkNIT84GY/hqdefault.jpg"
            ),
            Track(
                id = "yt_QCZZwZQ4qNs",
                sourceId = "ytmusic",
                title = "Si Antes Te Hubiera Conocido",
                artistId = "karol_g",
                artistName = "Karol G",
                albumId = "si_antes",
                albumName = "Si Antes Te Hubiera Conocido",
                durationMs = 195000L,
                artworkUrl = "https://i.ytimg.com/vi/QCZZwZQ4qNs/hqdefault.jpg"
            ),
            Track(
                id = "yt_ZA7ZKB8Mo9k",
                sourceId = "ytmusic",
                title = "LUNA",
                artistId = "feid",
                artistName = "Feid",
                albumId = "ferxxocalipsis",
                albumName = "FERXXOCALIPSIS",
                durationMs = 196000L,
                artworkUrl = "https://i.ytimg.com/vi/ZA7ZKB8Mo9k/hqdefault.jpg"
            )
        )
    }
}
