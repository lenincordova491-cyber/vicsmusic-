package app.vicsmusic.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vicsmusic.core.domain.repository.MusicRepository
import app.vicsmusic.core.domain.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val selectedArtistIds: Set<String> = emptySet(),
    val searchQuery: String = "",
    val suggestedArtists: List<ArtistItem> = emptyList(),
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

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

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
        _uiState.update { it.copy(selectedArtistIds = current) }
        updateSuggestedArtists()
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    private fun updateSuggestedArtists() {
        val selected = _uiState.value.selectedArtistIds
        val allArtists = ArtistCatalog.ALL_ARTISTS

        // Calculate dynamic similar artist suggestions based on selected artists
        val similarIds = selected.flatMap { id ->
            allArtists.find { it.id == id }?.similarArtistIds.orEmpty()
        }.toSet() - selected

        val suggested = allArtists.filter { similarIds.contains(it.id) }
        _uiState.update { it.copy(suggestedArtists = suggested) }
    }

    fun completeOnboarding(onSuccess: () -> Unit) {
        if (!_uiState.value.canContinue) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val selectedIds = _uiState.value.selectedArtistIds
            val selectedArtists = ArtistCatalog.ALL_ARTISTS
                .filter { selectedIds.contains(it.id) }
                .map { it.name }
                .toSet()

            // Save both names and IDs to ensure robust matching across all modules
            userPreferencesRepository.setFavoriteArtists(selectedArtists + selectedIds)
            userPreferencesRepository.setOnboardingCompleted(true)

            _uiState.update { it.copy(isSaving = false) }
            onSuccess()
        }
    }
}
