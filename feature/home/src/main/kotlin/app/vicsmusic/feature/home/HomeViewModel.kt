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
                val effectiveTracks = allTracks.ifEmpty { STARTER_TRACKS }

                val recommendations = recommendationEngine.computeRecommendations(
                    allAvailableTracks = effectiveTracks,
                    listeningHistory = history
                )

                // 6 tracks for Spotify 2x3 quick access grid
                val quickAccess = recents.take(6).ifEmpty { effectiveTracks.take(6) }

                // Hero featured banner (first recommendation or top track)
                val hero = recommendations.dailyMix.firstOrNull() ?: effectiveTracks.firstOrNull()

                val activeSources = if (prefs.activeSourceIds.isNotEmpty()) {
                    prefs.activeSourceIds
                } else {
                    setOf("ytmusic-spotiflac", "spotify-web", "soundcloud")
                }

                val currentTrending = if (_uiState.value.trendingTracks.isNotEmpty()) {
                    _uiState.value.trendingTracks
                } else {
                    STARTER_TRACKS
                }

                _uiState.value.copy(
                    isLoading = false,
                    quickAccessTracks = quickAccess,
                    featuredRelease = hero,
                    dailyMix = recommendations.dailyMix.ifEmpty { effectiveTracks.drop(1).take(5) },
                    recommendedTracks = recommendations.recommendedForYou.ifEmpty { effectiveTracks.takeLast(4) },
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
            val trending = musicRepository.searchTracks("Top Global").getOrNull()
                ?: musicRepository.searchTracks("Hits").getOrDefault(emptyList())
            if (trending.isNotEmpty()) {
                _uiState.update { it.copy(trendingTracks = trending) }
            }
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
            Track(
                id = "yt_kJQP7kiw5Fk",
                sourceId = "ytmusic",
                title = "Music!",
                artistId = "dargz",
                artistName = "DARGZ",
                albumId = "friends_and_family",
                albumName = "Friends & Family",
                durationMs = 281000L,
                artworkUrl = "https://i.ytimg.com/vi/kJQP7kiw5Fk/hqdefault.jpg"
            ),
            Track(
                id = "yt_kPa7bsKwL-8",
                sourceId = "ytmusic",
                title = "Die With A Smile",
                artistId = "bruno_mars",
                artistName = "Lady Gaga & Bruno Mars",
                albumId = "die_with_a_smile",
                albumName = "Die With A Smile",
                durationMs = 251000L,
                artworkUrl = "https://i.ytimg.com/vi/kPa7bsKwL-8/hqdefault.jpg"
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
                id = "yt_d5gf9dXHevw",
                sourceId = "ytmusic",
                title = "BIRDS OF A FEATHER",
                artistId = "billie_eilish",
                artistName = "Billie Eilish",
                albumId = "hit_me_hard_and_soft",
                albumName = "HIT ME HARD AND SOFT",
                durationMs = 196000L,
                artworkUrl = "https://i.ytimg.com/vi/d5gf9dXHevw/hqdefault.jpg"
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
                id = "yt_T6eK-2OQtew",
                sourceId = "ytmusic",
                title = "Not Like Us",
                artistId = "kendrick_lamar",
                artistName = "Kendrick Lamar",
                albumId = "not_like_us",
                albumName = "Not Like Us",
                durationMs = 274000L,
                artworkUrl = "https://i.ytimg.com/vi/T6eK-2OQtew/hqdefault.jpg"
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
            )
        )
    }
}
