package app.vicsmusic.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vicsmusic.core.domain.engine.AdaptiveRecommendationEngine
import app.vicsmusic.core.domain.engine.ListeningEvent
import app.vicsmusic.core.domain.repository.MusicRepository
import app.vicsmusic.core.domain.repository.UserPreferencesRepository
import app.vicsmusic.core.model.Artist
import app.vicsmusic.core.model.ExtensionItem
import app.vicsmusic.core.model.Track
import app.vicsmusic.core.player.PlaybackConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
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

data class HomeChart(
    val id: String,
    val title: String,
    val subtitle: String,
    val emoji: String,
    val tracks: List<Track>
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val greeting: String = "Buenas tardes",
    val tasteSummaryLabel: String = "Algoritmo adaptativo • Personalizado para ti",
    val selectedFilter: HomeCategoryFilter = HomeCategoryFilter.ALL,
    val quickAccessTracks: List<Track> = emptyList(),
    val featuredRelease: Track? = null,
    val favoriteArtists: List<Artist> = emptyList(),
    val topCharts: List<HomeChart> = emptyList(),
    val dailyMix: List<Track> = emptyList(),
    val recommendedTracks: List<Track> = emptyList(),
    val trendingTracks: List<Track> = emptyList(),
    val cumbiaTracks: List<Track> = emptyList(),
    val rockPopTracks: List<Track> = emptyList(),
    val urbanTracks: List<Track> = emptyList(),
    val salsaTracks: List<Track> = emptyList(),
    val downloadedTracks: List<Track> = emptyList(),
    val availableExtensions: List<ExtensionItem> = emptyList(),
    val activeExtensionIds: Set<String> = setOf("ytmusic-spotiflac", "spotify-web", "soundcloud"),
    val isSettingsOpen: Boolean = false,
    val isAutoplayEnabled: Boolean = true,
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
            favoriteArtists = CURATED_ARTISTS,
            topCharts = TOP_CHARTS,
            dailyMix = STARTER_TRACKS.drop(1).take(10),
            recommendedTracks = STARTER_TRACKS.takeLast(10),
            trendingTracks = STARTER_TRACKS.take(15),
            cumbiaTracks = CUMBIA_TRACKS,
            rockPopTracks = ROCK_POP_TRACKS,
            urbanTracks = URBAN_TRACKS,
            salsaTracks = SALSA_TRACKS
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
                // Home screen exclusively shows online streaming & downloaded tracks (no local device files)
                val onlineTracks = allTracks.filter { it.sourceId != "local" && !it.artworkUrl.isNullOrBlank() }
                val effectiveTracks = (onlineTracks + STARTER_TRACKS).distinctBy { it.id }
                val likedTrackIds = allTracks.filter { it.isLiked }.map { it.id }.toSet()

                val recommendations = recommendationEngine.computeRecommendations(
                    allAvailableTracks = effectiveTracks,
                    listeningHistory = history,
                    favoriteTrackIds = likedTrackIds,
                    favoriteArtistNames = prefs.favoriteArtistIds
                )

                // 6 tracks for Spotify 2x3 quick access grid (online tracks with valid artwork)
                val cleanRecents = recents.filter { it.sourceId != "local" && !it.artworkUrl.isNullOrBlank() }
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
                val quickAccess = (cleanRecents + prioritized).distinctBy { it.id }.take(6)

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
                    (_uiState.value.trendingTracks.filter { !it.artworkUrl.isNullOrBlank() } + STARTER_TRACKS).distinctBy { it.id }.take(20)
                } else {
                    STARTER_TRACKS.take(20)
                }

                val cumbia = (effectiveTracks.filter { track ->
                    track.artistId in listOf("grupo_5", "ke_personajes", "angeles_azules", "agua_marina", "armonia_10", "corazon_serrano", "hijos_del_sol", "los_destellos") ||
                    track.artistName.contains("Grupo 5", ignoreCase = true) ||
                    track.artistName.contains("Ke Personajes", ignoreCase = true) ||
                    track.artistName.contains("Agua Marina", ignoreCase = true) ||
                    track.artistName.contains("Armonía 10", ignoreCase = true) ||
                    track.artistName.contains("Ángeles Azules", ignoreCase = true) ||
                    track.artistName.contains("Corazón Serrano", ignoreCase = true) ||
                    track.artistName.contains("Hijos del Sol", ignoreCase = true) ||
                    track.artistName.contains("Destellos", ignoreCase = true)
                } + CUMBIA_TRACKS).distinctBy { it.id }

                val rockPop = (effectiveTracks.filter { track ->
                    track.artistId in listOf("coldplay", "queen", "soda_stereo", "imagine_dragons", "the_weeknd", "bruno_mars", "dua_lipa", "ed_sheeran", "taylor_swift", "sabrina_carpenter", "enanitos_verdes", "mana", "los_prisioneros", "los_mojarras", "caifanes", "jarabe_de_palo", "billie_eilish", "miley_cyrus") ||
                    track.artistName.contains("Soda Stereo", ignoreCase = true) ||
                    track.artistName.contains("Queen", ignoreCase = true) ||
                    track.artistName.contains("Coldplay", ignoreCase = true) ||
                    track.artistName.contains("Maná", ignoreCase = true) ||
                    track.artistName.contains("Enanitos Verdes", ignoreCase = true) ||
                    track.artistName.contains("Imagine Dragons", ignoreCase = true) ||
                    track.artistName.contains("Prisioneros", ignoreCase = true) ||
                    track.artistName.contains("Caifanes", ignoreCase = true)
                } + ROCK_POP_TRACKS).distinctBy { it.id }

                val urban = (effectiveTracks.filter { track ->
                    track.artistId in listOf("bad_bunny", "karol_g", "feid", "bizarrap", "manuel_turizo", "quevedo", "rauw_alejandro", "rosalia", "maluma") ||
                    track.artistName.contains("Bad Bunny", ignoreCase = true) ||
                    track.artistName.contains("Karol G", ignoreCase = true) ||
                    track.artistName.contains("Feid", ignoreCase = true) ||
                    track.artistName.contains("Bizarrap", ignoreCase = true) ||
                    track.artistName.contains("Manuel Turizo", ignoreCase = true) ||
                    track.artistName.contains("Quevedo", ignoreCase = true) ||
                    track.artistName.contains("Rauw Alejandro", ignoreCase = true) ||
                    track.artistName.contains("Maluma", ignoreCase = true)
                } + URBAN_TRACKS).distinctBy { it.id }

                val salsa = (effectiveTracks.filter { track ->
                    track.artistId in listOf("marc_anthony", "oscar_dleon", "ruben_blades") ||
                    track.artistName.contains("Marc Anthony", ignoreCase = true) ||
                    track.artistName.contains("Oscar D'León", ignoreCase = true) ||
                    track.artistName.contains("Rubén Blades", ignoreCase = true)
                } + SALSA_TRACKS).distinctBy { it.id }

                _uiState.value.copy(
                    isLoading = false,
                    tasteSummaryLabel = recommendations.tasteProfile.tasteSummaryLabel,
                    quickAccessTracks = quickAccess,
                    featuredRelease = hero,
                    favoriteArtists = if (prefs.favoriteArtistIds.isNotEmpty()) {
                        CURATED_ARTISTS.sortedByDescending { artist ->
                            if (prefs.favoriteArtistIds.any { it.equals(artist.id, true) || it.equals(artist.name, true) }) 1 else 0
                        }
                    } else {
                        CURATED_ARTISTS
                    },
                    topCharts = TOP_CHARTS,
                    dailyMix = (recommendations.dailyMix.filter { !it.artworkUrl.isNullOrBlank() } + prioritized.drop(1)).distinctBy { it.id }.take(12),
                    recommendedTracks = (recommendations.recommendedForYou.filter { !it.artworkUrl.isNullOrBlank() } + prioritized.reversed()).distinctBy { it.id }.take(12),
                    trendingTracks = currentTrending,
                    cumbiaTracks = cumbia,
                    rockPopTracks = rockPop,
                    urbanTracks = urban,
                    salsaTracks = salsa,
                    downloadedTracks = downloaded,
                    activeExtensionIds = activeSources,
                    isAutoplayEnabled = prefs.isAutoplayEnabled
                )
            }
            .flowOn(Dispatchers.Default)
            .catch { throwable ->
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
            val prefs = userPreferencesRepository.userPreferences.first()
            if (prefs.favoriteArtistIds.isNotEmpty()) {
                for (artist in prefs.favoriteArtistIds.take(3)) {
                    musicRepository.searchTracks(artist)
                }
            }
            musicRepository.searchTracks("Cumbia Peruana Exitos")
            musicRepository.searchTracks("Rock en Espanol Clasicos")
            musicRepository.searchTracks("Top Urbano Latino")
            val trending = musicRepository.searchTracks("Top Hits Global").getOrNull()
                ?: musicRepository.searchTracks("Hits").getOrDefault(emptyList())
            if (trending.isNotEmpty()) {
                _uiState.update {
                    it.copy(trendingTracks = (trending.filter { t -> t.sourceId != "local" } + STARTER_TRACKS).distinctBy { t -> t.id }.take(25))
                }
            }
        }
    }

    fun refreshCatalog() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            val onlineQueries = listOf(
                "Top 50 Peru",
                "Virales Peru 2026",
                "Canserbero Exitos",
                "Viva Latino",
                "Ke Personajes En Vivo",
                "Rock Clasico en Espanol",
                "Feid Ferxxo",
                "Cumbia Sanjuanera",
                "Bad Bunny Hits"
            ).shuffled().take(4)
            for (query in onlineQueries) {
                musicRepository.searchTracks(query)
            }
            val all = musicRepository.getAllTracks().first()
            val online = all.filter { it.sourceId != "local" && !it.artworkUrl.isNullOrBlank() }
            val pool = (online + STARTER_TRACKS).distinctBy { it.id }.shuffled()
            _uiState.update { current ->
                current.copy(
                    isRefreshing = false,
                    quickAccessTracks = pool.take(6),
                    dailyMix = pool.drop(6).take(12),
                    recommendedTracks = pool.drop(18).take(12),
                    trendingTracks = pool.drop(30).take(15)
                )
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

    fun toggleAutoplay(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setAutoplayEnabled(enabled)
        }
    }

    fun playTrack(track: Track, fromQueue: List<Track> = emptyList()) {
        if (fromQueue.isNotEmpty()) {
            val remoteQueue = fromQueue.filter { it.sourceId != "local" }
            val fullQueue = if (remoteQueue.size < 20) {
                val more = STARTER_TRACKS.filter { cat -> remoteQueue.none { it.id == cat.id } }
                (remoteQueue + more).distinctBy { it.id }
            } else {
                remoteQueue
            }
            val index = fullQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
            playbackConnection.playQueue(fullQueue, index)
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
        val CUMBIA_TRACKS = listOf(
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
                id = "yt_gn9i0-vgSpw",
                sourceId = "ytmusic",
                title = "Apostemos Que Me Caso",
                artistId = "grupo_5",
                artistName = "Grupo 5",
                albumId = "apostemos",
                albumName = "Grandes Éxitos",
                durationMs = 220000L,
                artworkUrl = "https://i.ytimg.com/vi/gn9i0-vgSpw/hqdefault.jpg"
            ),
            Track(
                id = "yt_hutrK9e59UU",
                sourceId = "ytmusic",
                title = "Cambio Mi Corazón",
                artistId = "grupo_5",
                artistName = "Grupo 5",
                albumId = "grupo_5_alb",
                albumName = "Elmer Yaipén",
                durationMs = 201000L,
                artworkUrl = "https://i.ytimg.com/vi/hutrK9e59UU/hqdefault.jpg"
            ),
            Track(
                id = "yt__AcvLO-H1bU",
                sourceId = "ytmusic",
                title = "Parranda La Negrita",
                artistId = "grupo_5",
                artistName = "Grupo 5",
                albumId = "grupo_5_alb",
                albumName = "En Vivo",
                durationMs = 421000L,
                artworkUrl = "https://i.ytimg.com/vi/_AcvLO-H1bU/hqdefault.jpg"
            ),
            Track(
                id = "yt_o9yHXj7eIKU",
                sourceId = "ytmusic",
                title = "Un Finde",
                artistId = "ke_personajes",
                artistName = "Ke Personajes",
                albumId = "un_finde",
                albumName = "Un Finde (CROSSOVER #2)",
                durationMs = 170000L,
                artworkUrl = "https://i.ytimg.com/vi/o9yHXj7eIKU/hqdefault.jpg"
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
                id = "yt_nmYVkrLlF-I",
                sourceId = "ytmusic",
                title = "Adiós Amor / Oye Mujer",
                artistId = "ke_personajes",
                artistName = "Ke Personajes",
                albumId = "adios_amor",
                albumName = "En Vivo",
                durationMs = 278000L,
                artworkUrl = "https://i.ytimg.com/vi/nmYVkrLlF-I/hqdefault.jpg"
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
                id = "yt_KlWux4sIaFM",
                sourceId = "ytmusic",
                title = "Amor A Primera Vista",
                artistId = "angeles_azules",
                artistName = "Los Ángeles Azules",
                albumId = "amor_primera_vista",
                albumName = "De Buenos Aires Para El Mundo",
                durationMs = 221000L,
                artworkUrl = "https://i.ytimg.com/vi/KlWux4sIaFM/hqdefault.jpg"
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
            Track(
                id = "yt__Y2CN8uuhZk",
                sourceId = "ytmusic",
                title = "Toma Que Toma",
                artistId = "armonia_10",
                artistName = "Armonía 10",
                albumId = "armonia_10_alb",
                albumName = "Grandes Éxitos",
                durationMs = 495000L,
                artworkUrl = "https://i.ytimg.com/vi/_Y2CN8uuhZk/hqdefault.jpg"
            ),
            Track(
                id = "yt_vryeLk8K8sw",
                sourceId = "ytmusic",
                title = "Díganle",
                artistId = "corazon_serrano",
                artistName = "Corazón Serrano",
                albumId = "diganle",
                albumName = "Grandes Éxitos",
                durationMs = 235000L,
                artworkUrl = "https://i.ytimg.com/vi/vryeLk8K8sw/hqdefault.jpg"
            ),
            Track(
                id = "yt_A82Fp83x6gw",
                sourceId = "ytmusic",
                title = "Cariñito",
                artistId = "hijos_del_sol",
                artistName = "Los Hijos del Sol",
                albumId = "hijos_del_sol_alb",
                albumName = "Clásicos de la Cumbia",
                durationMs = 245000L,
                artworkUrl = "https://i.ytimg.com/vi/A82Fp83x6gw/hqdefault.jpg"
            ),
            Track(
                id = "yt_Veo7zVW9lEw",
                sourceId = "ytmusic",
                title = "Elsa",
                artistId = "los_destellos",
                artistName = "Los Destellos",
                albumId = "los_destellos_alb",
                albumName = "Cumbia Peruana",
                durationMs = 220000L,
                artworkUrl = "https://i.ytimg.com/vi/Veo7zVW9lEw/hqdefault.jpg"
            )
        )

        val ROCK_POP_TRACKS = listOf(
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
                id = "yt_Z3L5M7GHAkw",
                sourceId = "ytmusic",
                title = "Persiana Americana",
                artistId = "soda_stereo",
                artistName = "Soda Stereo",
                albumId = "soda_stereo_alb",
                albumName = "Signos",
                durationMs = 295000L,
                artworkUrl = "https://i.ytimg.com/vi/Z3L5M7GHAkw/hqdefault.jpg"
            ),
            Track(
                id = "yt_nUXZe6hOm8c",
                sourceId = "ytmusic",
                title = "Prófugos",
                artistId = "soda_stereo",
                artistName = "Soda Stereo",
                albumId = "soda_stereo_alb",
                albumName = "Signos",
                durationMs = 321000L,
                artworkUrl = "https://i.ytimg.com/vi/nUXZe6hOm8c/hqdefault.jpg"
            ),
            Track(
                id = "yt_BRuHGchMrD8",
                sourceId = "ytmusic",
                title = "Lamento Boliviano",
                artistId = "enanitos_verdes",
                artistName = "Los Enanitos Verdes",
                albumId = "big_bang",
                albumName = "Big Bang",
                durationMs = 224000L,
                artworkUrl = "https://i.ytimg.com/vi/BRuHGchMrD8/hqdefault.jpg"
            ),
            Track(
                id = "yt_iuiepR7HkM4",
                sourceId = "ytmusic",
                title = "Oye Mi Amor",
                artistId = "mana",
                artistName = "Maná",
                albumId = "donde_jugaran",
                albumName = "¿Dónde Jugarán Los Niños?",
                durationMs = 273000L,
                artworkUrl = "https://i.ytimg.com/vi/iuiepR7HkM4/hqdefault.jpg"
            ),
            Track(
                id = "yt_EDkQB9b3cBw",
                sourceId = "ytmusic",
                title = "Rayando el Sol",
                artistId = "mana",
                artistName = "Maná",
                albumId = "mana_alb",
                albumName = "Falta Amor",
                durationMs = 250000L,
                artworkUrl = "https://i.ytimg.com/vi/EDkQB9b3cBw/hqdefault.jpg"
            ),
            Track(
                id = "yt_NgEJ9gMxP90",
                sourceId = "ytmusic",
                title = "Tren Al Sur",
                artistId = "los_prisioneros",
                artistName = "Los Prisioneros",
                albumId = "los_prisioneros_alb",
                albumName = "Corazones",
                durationMs = 337000L,
                artworkUrl = "https://i.ytimg.com/vi/NgEJ9gMxP90/hqdefault.jpg"
            ),
            Track(
                id = "yt_Qq4vqDjHMAQ",
                sourceId = "ytmusic",
                title = "Triciclo Perú",
                artistId = "los_mojarras",
                artistName = "Los Mojarras",
                albumId = "los_mojarras_alb",
                albumName = "Sarita Colonia",
                durationMs = 219000L,
                artworkUrl = "https://i.ytimg.com/vi/Qq4vqDjHMAQ/hqdefault.jpg"
            ),
            Track(
                id = "yt_UamQYzPCwUg",
                sourceId = "ytmusic",
                title = "Afuera",
                artistId = "caifanes",
                artistName = "Caifanes",
                albumId = "caifanes_alb",
                albumName = "El Nervio del Volcán",
                durationMs = 289000L,
                artworkUrl = "https://i.ytimg.com/vi/UamQYzPCwUg/hqdefault.jpg"
            ),
            Track(
                id = "yt_YFQC3_fC6i4",
                sourceId = "ytmusic",
                title = "La Flaca",
                artistId = "jarabe_de_palo",
                artistName = "Jarabe de Palo",
                albumId = "jarabe_de_palo_alb",
                albumName = "La Flaca",
                durationMs = 261000L,
                artworkUrl = "https://i.ytimg.com/vi/YFQC3_fC6i4/hqdefault.jpg"
            ),
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
                id = "yt_HgzGwKwLmgM",
                sourceId = "ytmusic",
                title = "Don't Stop Me Now",
                artistId = "queen",
                artistName = "Queen",
                albumId = "jazz",
                albumName = "Jazz",
                durationMs = 209000L,
                artworkUrl = "https://i.ytimg.com/vi/HgzGwKwLmgM/hqdefault.jpg"
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
                id = "yt_3_g2un5M350",
                sourceId = "ytmusic",
                title = "Starboy",
                artistId = "the_weeknd",
                artistName = "The Weeknd",
                albumId = "the_weeknd_alb",
                albumName = "Starboy",
                durationMs = 230000L,
                artworkUrl = "https://i.ytimg.com/vi/3_g2un5M350/hqdefault.jpg"
            ),
            Track(
                id = "yt_RmYCOm4ehKs",
                sourceId = "ytmusic",
                title = "Save Your Tears",
                artistId = "the_weeknd",
                artistName = "The Weeknd",
                albumId = "the_weeknd_alb",
                albumName = "After Hours",
                durationMs = 216000L,
                artworkUrl = "https://i.ytimg.com/vi/RmYCOm4ehKs/hqdefault.jpg"
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
                id = "yt_DlFXDl_ROAM",
                sourceId = "ytmusic",
                title = "Die With A Smile",
                artistId = "bruno_mars",
                artistName = "Lady Gaga & Bruno Mars",
                albumId = "bruno_mars_alb",
                albumName = "Die With A Smile",
                durationMs = 252000L,
                artworkUrl = "https://i.ytimg.com/vi/DlFXDl_ROAM/hqdefault.jpg"
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
            Track(
                id = "yt_WKZO-CWeOVA",
                sourceId = "ytmusic",
                title = "BIRDS OF A FEATHER",
                artistId = "billie_eilish",
                artistName = "Billie Eilish",
                albumId = "billie_eilish_alb",
                albumName = "HIT ME HARD AND SOFT",
                durationMs = 210000L,
                artworkUrl = "https://i.ytimg.com/vi/WKZO-CWeOVA/hqdefault.jpg"
            ),
            Track(
                id = "yt_BkSSOGNeWTM",
                sourceId = "ytmusic",
                title = "Flowers",
                artistId = "miley_cyrus",
                artistName = "Miley Cyrus",
                albumId = "miley_cyrus_alb",
                albumName = "Endless Summer Vacation",
                durationMs = 200000L,
                artworkUrl = "https://i.ytimg.com/vi/BkSSOGNeWTM/hqdefault.jpg"
            )
        )

        val URBAN_TRACKS = listOf(
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
                id = "yt_saGYMhApaH8",
                sourceId = "ytmusic",
                title = "Me Porto Bonito",
                artistId = "bad_bunny",
                artistName = "Bad Bunny",
                albumId = "un_verano_sin_ti",
                albumName = "Un Verano Sin Ti",
                durationMs = 178000L,
                artworkUrl = "https://i.ytimg.com/vi/saGYMhApaH8/hqdefault.jpg"
            ),
            Track(
                id = "yt_J-7knuczw2E",
                sourceId = "ytmusic",
                title = "Monaco",
                artistId = "bad_bunny",
                artistName = "Bad Bunny",
                albumId = "bad_bunny_alb",
                albumName = "nadie sabe lo que va a pasar mañana",
                durationMs = 267000L,
                artworkUrl = "https://i.ytimg.com/vi/J-7knuczw2E/hqdefault.jpg"
            ),
            Track(
                id = "yt_HekrZ0QcI3g",
                sourceId = "ytmusic",
                title = "Perro Negro",
                artistId = "bad_bunny",
                artistName = "Bad Bunny",
                albumId = "bad_bunny_alb",
                albumName = "nadie sabe lo que va a pasar mañana",
                durationMs = 163000L,
                artworkUrl = "https://i.ytimg.com/vi/HekrZ0QcI3g/hqdefault.jpg"
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
                id = "yt_i9gxO_5vA-Q",
                sourceId = "ytmusic",
                title = "Provenza",
                artistId = "karol_g",
                artistName = "Karol G",
                albumId = "manana_sera_bonito",
                albumName = "Mañana Será Bonito",
                durationMs = 210000L,
                artworkUrl = "https://i.ytimg.com/vi/i9gxO_5vA-Q/hqdefault.jpg"
            ),
            Track(
                id = "yt_hWbKLIH3N8c",
                sourceId = "ytmusic",
                title = "QLONA",
                artistId = "karol_g",
                artistName = "Karol G",
                albumId = "karol_g_alb",
                albumName = "Mañana Será Bonito (Bichota Season)",
                durationMs = 173000L,
                artworkUrl = "https://i.ytimg.com/vi/hWbKLIH3N8c/hqdefault.jpg"
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
            ),
            Track(
                id = "yt_6f679w-UM2k",
                sourceId = "ytmusic",
                title = "Feliz Cumpleaños Ferxxo",
                artistId = "feid",
                artistName = "Feid",
                albumId = "feliz_cumpleanos",
                albumName = "Feliz Cumpleaños Ferxxo",
                durationMs = 156000L,
                artworkUrl = "https://i.ytimg.com/vi/6f679w-UM2k/hqdefault.jpg"
            ),
            Track(
                id = "yt_WFoMObvIQXk",
                sourceId = "ytmusic",
                title = "CLASSY 101",
                artistId = "feid",
                artistName = "Feid",
                albumId = "feid_alb",
                albumName = "CLASSY 101",
                durationMs = 196000L,
                artworkUrl = "https://i.ytimg.com/vi/WFoMObvIQXk/hqdefault.jpg"
            ),
            Track(
                id = "yt_bfH2AO-oek0",
                sourceId = "ytmusic",
                title = "Quédate (Bzrp Music Sessions #52)",
                artistId = "bizarrap",
                artistName = "Bizarrap & Quevedo",
                albumId = "bzrp_52",
                albumName = "Quédate",
                durationMs = 198000L,
                artworkUrl = "https://i.ytimg.com/vi/bfH2AO-oek0/hqdefault.jpg"
            ),
            Track(
                id = "yt_sBLM_LzfCz4",
                sourceId = "ytmusic",
                title = "La Bachata",
                artistId = "manuel_turizo",
                artistName = "Manuel Turizo",
                albumId = "2000",
                albumName = "2000",
                durationMs = 162000L,
                artworkUrl = "https://i.ytimg.com/vi/sBLM_LzfCz4/hqdefault.jpg"
            ),
            Track(
                id = "yt_dN3y8kVNplA",
                sourceId = "ytmusic",
                title = "Despechá",
                artistId = "rosalia",
                artistName = "Rosalía",
                albumId = "rosalia_alb",
                albumName = "MOTOMAMI",
                durationMs = 157000L,
                artworkUrl = "https://i.ytimg.com/vi/dN3y8kVNplA/hqdefault.jpg"
            ),
            Track(
                id = "yt_n_2ZYU72BYQ",
                sourceId = "ytmusic",
                title = "Hawái",
                artistId = "maluma",
                artistName = "Maluma",
                albumId = "maluma_alb",
                albumName = "Papi Juancho",
                durationMs = 199000L,
                artworkUrl = "https://i.ytimg.com/vi/n_2ZYU72BYQ/hqdefault.jpg"
            ),
            Track(
                id = "yt_jDVGSXE0fEA",
                sourceId = "ytmusic",
                title = "Todo De Ti",
                artistId = "rauw_alejandro",
                artistName = "Rauw Alejandro",
                albumId = "rauw_alejandro_alb",
                albumName = "Vice Versa",
                durationMs = 200000L,
                artworkUrl = "https://i.ytimg.com/vi/jDVGSXE0fEA/hqdefault.jpg"
            )
        )

        val SALSA_TRACKS = listOf(
            Track(
                id = "yt_79OxD7KNlYw",
                sourceId = "ytmusic",
                title = "Vivir Mi Vida",
                artistId = "marc_anthony",
                artistName = "Marc Anthony",
                albumId = "marc_anthony_alb",
                albumName = "3.0",
                durationMs = 252000L,
                artworkUrl = "https://i.ytimg.com/vi/79OxD7KNlYw/hqdefault.jpg"
            ),
            Track(
                id = "yt_VDFuS4KmkKE",
                sourceId = "ytmusic",
                title = "Flor Pálida",
                artistId = "marc_anthony",
                artistName = "Marc Anthony",
                albumId = "marc_anthony_alb",
                albumName = "3.0",
                durationMs = 280000L,
                artworkUrl = "https://i.ytimg.com/vi/VDFuS4KmkKE/hqdefault.jpg"
            ),
            Track(
                id = "yt_gxlB1B9emDc",
                sourceId = "ytmusic",
                title = "Llorarás",
                artistId = "oscar_dleon",
                artistName = "Oscar D'León",
                albumId = "oscar_dleon_alb",
                albumName = "Salsa Clásica",
                durationMs = 222000L,
                artworkUrl = "https://i.ytimg.com/vi/gxlB1B9emDc/hqdefault.jpg"
            ),
            Track(
                id = "yt_xe4jSbcKiUQ",
                sourceId = "ytmusic",
                title = "Pedro Navaja",
                artistId = "ruben_blades",
                artistName = "Rubén Blades",
                albumId = "ruben_blades_alb",
                albumName = "Siembra",
                durationMs = 442000L,
                artworkUrl = "https://i.ytimg.com/vi/xe4jSbcKiUQ/hqdefault.jpg"
            )
        )

        val CANSERBERO_TRACKS = listOf(
            Track(
                id = "yt_canserbero_pensando",
                sourceId = "ytmusic",
                title = "Pensando en Ti",
                artistId = "canserbero",
                artistName = "Canserbero",
                albumId = "muerte",
                albumName = "Muerte",
                durationMs = 260000L,
                artworkUrl = "https://i.ytimg.com/vi/W4qfK4o8aA8/hqdefault.jpg"
            ),
            Track(
                id = "yt_canserbero_jeremias",
                sourceId = "ytmusic",
                title = "Jeremías 17:5",
                artistId = "canserbero",
                artistName = "Canserbero",
                albumId = "muerte",
                albumName = "Muerte",
                durationMs = 310000L,
                artworkUrl = "https://i.ytimg.com/vi/a_6cWJ_WwzM/hqdefault.jpg"
            ),
            Track(
                id = "yt_canserbero_epico",
                sourceId = "ytmusic",
                title = "Es Épico",
                artistId = "canserbero",
                artistName = "Canserbero",
                albumId = "muerte",
                albumName = "Muerte",
                durationMs = 360000L,
                artworkUrl = "https://i.ytimg.com/vi/w6f3Pq0vH9M/hqdefault.jpg"
            ),
            Track(
                id = "yt_canserbero_querer",
                sourceId = "ytmusic",
                title = "Querer Querernos",
                artistId = "canserbero",
                artistName = "Canserbero",
                albumId = "vida",
                albumName = "Vida",
                durationMs = 245000L,
                artworkUrl = "https://i.ytimg.com/vi/8aL0F9L_Z8w/hqdefault.jpg"
            ),
            Track(
                id = "yt_canserbero_maquiavelico",
                sourceId = "ytmusic",
                title = "Maquiavélico",
                artistId = "canserbero",
                artistName = "Canserbero",
                albumId = "muerte",
                albumName = "Muerte",
                durationMs = 280000L,
                artworkUrl = "https://i.ytimg.com/vi/Yw_eP1WwQ1Q/hqdefault.jpg"
            )
        )

        val KE_PERSONAJES_TRACKS = listOf(
            Track(
                id = "yt_ke_un_finde",
                sourceId = "ytmusic",
                title = "Un Finde | CROSSOVER #2",
                artistId = "ke_personajes",
                artistName = "Ke Personajes, Big One, FMK",
                albumId = "un_finde",
                albumName = "Un Finde",
                durationMs = 170000L,
                artworkUrl = "https://i.ytimg.com/vi/M27u4C1g2vA/hqdefault.jpg"
            ),
            Track(
                id = "yt_ke_pobre_corazon",
                sourceId = "ytmusic",
                title = "Pobre Corazón",
                artistId = "ke_personajes",
                artistName = "Ke Personajes & Onda Sabanera",
                albumId = "pobre_corazon",
                albumName = "En Vivo",
                durationMs = 195000L,
                artworkUrl = "https://i.ytimg.com/vi/E0pB_8V9KzE/hqdefault.jpg"
            ),
            Track(
                id = "yt_ke_ojitos_lindos",
                sourceId = "ytmusic",
                title = "Ojitos Lindos (En Vivo)",
                artistId = "ke_personajes",
                artistName = "Ke Personajes",
                albumId = "ojitos_lindos",
                albumName = "En Vivo en el Luna Park",
                durationMs = 210000L,
                artworkUrl = "https://i.ytimg.com/vi/4eFvJk_s_uM/hqdefault.jpg"
            ),
            Track(
                id = "yt_ke_costumbres",
                sourceId = "ytmusic",
                title = "Costumbres",
                artistId = "ke_personajes",
                artistName = "Ke Personajes",
                albumId = "costumbres",
                albumName = "En Vivo",
                durationMs = 220000L,
                artworkUrl = "https://i.ytimg.com/vi/h7xYq2K23jU/hqdefault.jpg"
            )
        )

        val AGUA_MARINA_TRACKS = listOf(
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
                id = "yt_am_paloma_ajena",
                sourceId = "ytmusic",
                title = "Paloma Ajena",
                artistId = "agua_marina",
                artistName = "Agua Marina",
                albumId = "paloma_ajena",
                albumName = "Grandes Éxitos",
                durationMs = 240000L,
                artworkUrl = "https://i.ytimg.com/vi/MSFR0st6yJM/hqdefault.jpg"
            ),
            Track(
                id = "yt_am_asi_es_el_amor",
                sourceId = "ytmusic",
                title = "Así Es El Amor",
                artistId = "agua_marina",
                artistName = "Agua Marina",
                albumId = "asi_es_el_amor",
                albumName = "Éxitos de Oro",
                durationMs = 260000L,
                artworkUrl = "https://i.ytimg.com/vi/MSFR0st6yJM/hqdefault.jpg"
            )
        )

        val DADDY_YANKEE_TRACKS = listOf(
            Track(
                id = "yt_dy_gasolina",
                sourceId = "ytmusic",
                title = "Gasolina",
                artistId = "daddy_yankee",
                artistName = "Daddy Yankee",
                albumId = "barrio_fino",
                albumName = "Barrio Fino",
                durationMs = 192000L,
                artworkUrl = "https://i.ytimg.com/vi/yL37-2lYhXQ/hqdefault.jpg"
            ),
            Track(
                id = "yt_dy_con_calma",
                sourceId = "ytmusic",
                title = "Con Calma",
                artistId = "daddy_yankee",
                artistName = "Daddy Yankee & Snow",
                albumId = "con_calma_alb",
                albumName = "Con Calma",
                durationMs = 193000L,
                artworkUrl = "https://i.ytimg.com/vi/DiItGE3eAyQ/hqdefault.jpg"
            ),
            Track(
                id = "yt_dy_llamado_emergencia",
                sourceId = "ytmusic",
                title = "Llamado de Emergencia",
                artistId = "daddy_yankee",
                artistName = "Daddy Yankee",
                albumId = "talento_de_barrio",
                albumName = "Talento de Barrio",
                durationMs = 240000L,
                artworkUrl = "https://i.ytimg.com/vi/u9r5Zc_bB1E/hqdefault.jpg"
            )
        )

        val ANGELES_AZULES_TRACKS = listOf(
            Track(
                id = "yt_aa_nunca_es_suficiente",
                sourceId = "ytmusic",
                title = "Nunca Es Suficiente",
                artistId = "angeles_azules",
                artistName = "Los Ángeles Azules ft. Natalia Lafourcade",
                albumId = "esto_si_es_cumbia",
                albumName = "Esto Sí Es Cumbia",
                durationMs = 265000L,
                artworkUrl = "https://i.ytimg.com/vi/Veo7zVW9lEw/hqdefault.jpg"
            ),
            Track(
                id = "yt_aa_17_anos",
                sourceId = "ytmusic",
                title = "17 Años",
                artistId = "angeles_azules",
                artistName = "Los Ángeles Azules",
                albumId = "17_anos_alb",
                albumName = "Grandes Éxitos",
                durationMs = 215000L,
                artworkUrl = "https://i.ytimg.com/vi/Veo7zVW9lEw/hqdefault.jpg"
            )
        )

        val CURATED_ARTISTS = listOf(
            Artist(
                id = "canserbero",
                sourceId = "ytmusic",
                name = "Canserbero",
                imageUrl = "https://i.ytimg.com/vi/a_6cWJ_WwzM/hqdefault.jpg",
                bio = "Rap consciente / Leyenda indiscutible del rap latinoamericano",
                topTracks = CANSERBERO_TRACKS
            ),
            Artist(
                id = "ke_personajes",
                sourceId = "ytmusic",
                name = "Ke Personajes",
                imageUrl = "https://i.ytimg.com/vi/M27u4C1g2vA/hqdefault.jpg",
                bio = "Cumbia argentina y fenómeno vocal liderado por Emanuel Noir",
                topTracks = KE_PERSONAJES_TRACKS
            ),
            Artist(
                id = "agua_marina",
                sourceId = "ytmusic",
                name = "Agua Marina",
                imageUrl = "https://i.ytimg.com/vi/MSFR0st6yJM/hqdefault.jpg",
                bio = "Cumbia elegante de Sechura, Piura para todo el Perú",
                topTracks = AGUA_MARINA_TRACKS
            ),
            Artist(
                id = "grupo_5",
                sourceId = "ytmusic",
                name = "Grupo 5",
                imageUrl = "https://i.ytimg.com/vi/W4qfK4o8aA8/hqdefault.jpg",
                bio = "El Grupo de Oro del Perú y referente número uno de la cumbia",
                topTracks = CUMBIA_TRACKS.filter { it.artistId == "grupo_5" }
            ),
            Artist(
                id = "coldplay",
                sourceId = "ytmusic",
                name = "Coldplay",
                imageUrl = "https://i.ytimg.com/vi/RB-RcX5DS5A/hqdefault.jpg",
                bio = "Banda británica de rock pop e himnos generacionales",
                topTracks = ROCK_POP_TRACKS.filter { it.artistId == "coldplay" }
            ),
            Artist(
                id = "bad_bunny",
                sourceId = "ytmusic",
                name = "Bad Bunny",
                imageUrl = "https://i.ytimg.com/vi/QpKkNIT84GY/hqdefault.jpg",
                bio = "Trap, reggaetón y figura global de la música urbana",
                topTracks = URBAN_TRACKS.filter { it.artistId == "bad_bunny" }
            ),
            Artist(
                id = "feid",
                sourceId = "ytmusic",
                name = "Feid",
                imageUrl = "https://i.ytimg.com/vi/ZA7ZKB8Mo9k/hqdefault.jpg",
                bio = "Ferxxo / El sonido fresco y romántico del reggaetón de Medellín",
                topTracks = URBAN_TRACKS.filter { it.artistId == "feid" }
            ),
            Artist(
                id = "daddy_yankee",
                sourceId = "ytmusic",
                name = "Daddy Yankee",
                imageUrl = "https://i.ytimg.com/vi/yL37-2lYhXQ/hqdefault.jpg",
                bio = "El máximo exponente y Rey del Reggaetón mundial",
                topTracks = DADDY_YANKEE_TRACKS
            ),
            Artist(
                id = "soda_stereo",
                sourceId = "ytmusic",
                name = "Soda Stereo",
                imageUrl = "https://i.ytimg.com/vi/T_FkEw27XJ0/hqdefault.jpg",
                bio = "Pioneros absolutos del rock en español y leyendas de América Latina",
                topTracks = ROCK_POP_TRACKS.filter { it.artistId == "soda_stereo" }
            ),
            Artist(
                id = "angeles_azules",
                sourceId = "ytmusic",
                name = "Los Ángeles Azules",
                imageUrl = "https://i.ytimg.com/vi/Veo7zVW9lEw/hqdefault.jpg",
                bio = "Cumbia sonidera mexicana: De Iztapalapa para el mundo",
                topTracks = ANGELES_AZULES_TRACKS
            )
        )

        val TOP_CHARTS = listOf(
            HomeChart(
                id = "top_50_peru",
                title = "Top 50 - Perú",
                subtitle = "Tu actualización diaria de las canciones más sonadas en Perú",
                emoji = "🇵🇪",
                tracks = (CUMBIA_TRACKS + CANSERBERO_TRACKS + URBAN_TRACKS).shuffled()
            ),
            HomeChart(
                id = "top_50_global",
                title = "Top 50 - Global",
                subtitle = "Las canciones más escuchadas en el mundo ahora mismo",
                emoji = "🌍",
                tracks = (ROCK_POP_TRACKS + URBAN_TRACKS + CANSERBERO_TRACKS).shuffled()
            ),
            HomeChart(
                id = "virales_peru",
                title = "Virales Perú",
                subtitle = "Tendencias en redes sociales y streaming en el país",
                emoji = "🔥",
                tracks = (CUMBIA_TRACKS + URBAN_TRACKS).shuffled()
            ),
            HomeChart(
                id = "viva_latino",
                title = "Viva Latino",
                subtitle = "El epicentro del ritmo y los mayores hits de la música latina",
                emoji = "✨",
                tracks = (URBAN_TRACKS + SALSA_TRACKS + CUMBIA_TRACKS).shuffled()
            ),
            HomeChart(
                id = "los_90_peru",
                title = "Los 90s & Clásicos",
                subtitle = "Nostalgia, rock en español y cumbias doradas",
                emoji = "📻",
                tracks = (ROCK_POP_TRACKS + CUMBIA_TRACKS).shuffled()
            )
        )

        val STARTER_TRACKS = CUMBIA_TRACKS + ROCK_POP_TRACKS + URBAN_TRACKS + SALSA_TRACKS + CANSERBERO_TRACKS + KE_PERSONAJES_TRACKS + AGUA_MARINA_TRACKS + DADDY_YANKEE_TRACKS + ANGELES_AZULES_TRACKS
    }
}
