package app.vicsmusic.feature.player

import app.vicsmusic.core.domain.usecase.ToggleLikeUseCase
import app.vicsmusic.core.model.PlaybackState
import app.vicsmusic.core.player.PlaybackConnection
import app.vicsmusic.core.player.queue.QueueState
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val playbackConnection: PlaybackConnection = mockk(relaxed = true)
    private val musicRepository: app.vicsmusic.core.domain.repository.MusicRepository = mockk(relaxed = true)
    private val toggleLikeUseCase: ToggleLikeUseCase = mockk(relaxed = true)

    private val playbackStateFlow = MutableStateFlow(PlaybackState())
    private val queueStateFlow = MutableStateFlow(QueueState())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { playbackConnection.playbackState } returns playbackStateFlow
        every { playbackConnection.queueState } returns queueStateFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `togglePlayPause delegates to playbackConnection`() = runTest(testDispatcher) {
        val viewModel = PlayerViewModel(playbackConnection, musicRepository, toggleLikeUseCase)
        advanceUntilIdle()

        viewModel.togglePlayPause()
        verify { playbackConnection.togglePlayPause() }
    }

    @Test
    fun `skipNext delegates to playbackConnection`() = runTest(testDispatcher) {
        val viewModel = PlayerViewModel(playbackConnection, musicRepository, toggleLikeUseCase)
        advanceUntilIdle()

        viewModel.skipNext()
        verify { playbackConnection.skipNext() }
    }

    @Test
    fun `seekTo delegates to playbackConnection`() = runTest(testDispatcher) {
        val viewModel = PlayerViewModel(playbackConnection, musicRepository, toggleLikeUseCase)
        advanceUntilIdle()

        viewModel.seekTo(15_000L)
        verify { playbackConnection.seekTo(15_000L) }
    }
}
