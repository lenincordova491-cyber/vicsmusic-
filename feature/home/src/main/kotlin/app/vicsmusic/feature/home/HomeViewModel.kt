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

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        updateGreeting()
        loadExtensions()
        observeData()
        fetchOnlineCatalog()
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
                val recommendations = recommendationEngine.computeRecommendations(
                    allAvailableTracks = allTracks,
                    listeningHistory = history
                )

                // 6 tracks for Spotify 2x3 quick access grid
                val quickAccess = (recents.take(6).ifEmpty { allTracks.take(6) })

                // Hero featured banner (first recommendation or top track)
                val hero = recommendations.dailyMix.firstOrNull() ?: allTracks.firstOrNull()

                val activeSources = if (prefs.activeSourceIds.isNotEmpty()) {
                    prefs.activeSourceIds
                } else {
                    setOf("ytmusic-spotiflac", "spotify-web", "soundcloud")
                }

                _uiState.value.copy(
                    isLoading = false,
                    quickAccessTracks = quickAccess,
                    featuredRelease = hero,
                    dailyMix = recommendations.dailyMix,
                    recommendedTracks = recommendations.recommendedForYou,
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
            // Fetch online trending songs if database is fresh
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
}
