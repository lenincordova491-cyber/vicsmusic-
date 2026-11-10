package app.vicsmusic.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vicsmusic.core.domain.repository.MusicRepository
import app.vicsmusic.core.domain.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val selectedArtistIds: Set<String> = emptySet(),
    val selectedArtists: List<ArtistItem> = emptyList(),
    val selectedGenre: String = "Todos",
    val searchQuery: String = "",
    val suggestedArtists: List<ArtistItem> = emptyList(),
    val remoteArtists: List<ArtistItem> = emptyList(),
    val isSearchingRemote: Boolean = false,
    val isSaving: Boolean = false
) {
    val canContinue: Boolean get() = selectedArtistIds.size >= 3
    val selectionCountText: String get() = "${selectedArtistIds.size}/3"
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val knownArtists: MutableMap<String, ArtistItem> =
        ArtistCatalog.ALL_ARTISTS.associateBy { it.id }.toMutableMap()

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        updateSuggestedArtists()
    }

    fun onArtistToggled(artistId: String) {
        val current = _uiState.value.selectedArtistIds.toMutableSet()
        if (current.contains(artistId)) {
            current.remove(artistId)
        } else {
            current.add(artistId)
        }
        val selectedList = current.mapNotNull { knownArtists[it] }
        _uiState.update {
            it.copy(
                selectedArtistIds = current,
                selectedArtists = selectedList
            )
        }
        updateSuggestedArtists()
    }

    fun onArtistItemToggled(artist: ArtistItem) {
        knownArtists[artist.id] = artist
        onArtistToggled(artist.id)
    }

    fun onGenreSelected(genre: String) {
        _uiState.update { it.copy(selectedGenre = genre) }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()

        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _uiState.update { it.copy(remoteArtists = emptyList(), isSearchingRemote = false) }
            return
        }

        searchJob = viewModelScope.launch {
            delay(350) // Debounce 350ms
            _uiState.update { it.copy(isSearchingRemote = true) }

            val result = musicRepository.searchTracks(trimmed)
            val tracks = result.getOrDefault(emptyList())
            val catalogNames = ArtistCatalog.ALL_ARTISTS.map { it.name.lowercase() }.toSet()

            val remotes = tracks
                .filter { it.artistName.isNotBlank() && !it.artistName.equals("Artista desconocido", ignoreCase = true) }
                .distinctBy { it.artistName.lowercase() }
                .filter { !catalogNames.contains(it.artistName.lowercase()) }
                .map { track ->
                    val safeId = "remote_${track.artistName.trim().lowercase().replace("[^a-zA-Z0-9]".toRegex(), "_")}"
                    ArtistItem(
                        id = safeId,
                        name = track.artistName.trim(),
                        genre = "En línea",
                        imageUrl = track.artworkUrl ?: "https://i.ytimg.com/vi/${track.id}/hqdefault.jpg"
                    )
                }
                .take(12)

            remotes.forEach { knownArtists[it.id] = it }

            _uiState.update {
                it.copy(
                    remoteArtists = remotes,
                    isSearchingRemote = false
                )
            }
        }
    }

    private fun updateSuggestedArtists() {
        val selected = _uiState.value.selectedArtistIds
        val allArtists = ArtistCatalog.ALL_ARTISTS

        // Calculate dynamic similar artist suggestions based on selected artists' similarity graph
        val similarIds = selected.flatMap { id ->
            knownArtists[id]?.similarArtistIds.orEmpty()
        }.toSet() - selected

        val suggested = allArtists.filter { similarIds.contains(it.id) }
        _uiState.update { it.copy(suggestedArtists = suggested) }
    }

    fun completeOnboarding(onSuccess: () -> Unit) {
        if (!_uiState.value.canContinue) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val selectedIds = _uiState.value.selectedArtistIds
            val selectedNames = selectedIds.mapNotNull { id ->
                knownArtists[id]?.name
            }.toSet()

            // Save both names and IDs to ensure robust matching across all modules
            userPreferencesRepository.setFavoriteArtists(selectedNames + selectedIds)
            userPreferencesRepository.setOnboardingCompleted(true)

            _uiState.update { it.copy(isSaving = false) }
            onSuccess()
        }
    }
}
