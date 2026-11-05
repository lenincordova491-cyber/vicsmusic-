package app.vicsmusic.core.player.queue

import app.vicsmusic.core.database.dao.QueueDao
import app.vicsmusic.core.database.dao.TrackDao
import app.vicsmusic.core.database.entity.QueueEntity
import app.vicsmusic.core.database.model.asExternalModel
import app.vicsmusic.core.model.RepeatMode
import app.vicsmusic.core.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.Random
import javax.inject.Inject
import javax.inject.Singleton

data class QueueState(
    val items: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF
) {
    val currentTrack: Track?
        get() = if (currentIndex in items.indices) items[currentIndex] else null

    val hasNext: Boolean
        get() = when (repeatMode) {
            RepeatMode.ONE, RepeatMode.ALL -> items.isNotEmpty()
            RepeatMode.OFF -> currentIndex + 1 < items.size
        }

    val hasPrevious: Boolean
        get() = when (repeatMode) {
            RepeatMode.ONE, RepeatMode.ALL -> items.isNotEmpty()
            RepeatMode.OFF -> currentIndex - 1 >= 0
        }
}

@Singleton
class QueueManager @Inject constructor(
    private val queueDao: QueueDao,
    private val trackDao: TrackDao
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val _state = MutableStateFlow(QueueState())
    val state: StateFlow<QueueState> = _state.asStateFlow()

    private var originalQueue: List<Track> = emptyList()

    init {
        restoreQueue()
    }

    fun setQueue(tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty()) {
            clear()
            return
        }
        val safeIndex = startIndex.coerceIn(0, tracks.lastIndex)
        originalQueue = tracks.toList()

        val activeList = if (_state.value.isShuffleEnabled) {
            val shuffled = tracks.toMutableList()
            val current = tracks[safeIndex]
            shuffled.removeAt(safeIndex)
            shuffled.shuffle(Random(System.currentTimeMillis()))
            shuffled.add(0, current)
            shuffled
        } else {
            tracks
        }

        _state.value = _state.value.copy(
            items = activeList,
            currentIndex = if (_state.value.isShuffleEnabled) 0 else safeIndex
        )
        persistQueue()
    }

    fun playNext(track: Track) {
        val currentItems = _state.value.items.toMutableList()
        val insertPos = (_state.value.currentIndex + 1).coerceAtMost(currentItems.size)
        currentItems.add(insertPos, track)
        _state.value = _state.value.copy(items = currentItems)
        persistQueue()
    }

    fun addToQueue(track: Track) {
        val currentItems = _state.value.items.toMutableList()
        currentItems.add(track)
        _state.value = _state.value.copy(items = currentItems)
        persistQueue()
    }

    fun removeAt(index: Int) {
        val currentItems = _state.value.items.toMutableList()
        if (index !in currentItems.indices) return

        currentItems.removeAt(index)
        var newIndex = _state.value.currentIndex
        if (index < newIndex) {
            newIndex--
        } else if (newIndex >= currentItems.size) {
            newIndex = currentItems.lastIndex
        }

        _state.value = _state.value.copy(
            items = currentItems,
            currentIndex = newIndex
        )
        persistQueue()
    }

    fun moveItem(fromIndex: Int, toIndex: Int) {
        val currentItems = _state.value.items.toMutableList()
        if (fromIndex !in currentItems.indices || toIndex !in currentItems.indices) return

        val currentTrack = _state.value.currentTrack
        Collections.swap(currentItems, fromIndex, toIndex)
        val newIndex = currentItems.indexOf(currentTrack)

        _state.value = _state.value.copy(
            items = currentItems,
            currentIndex = newIndex
        )
        persistQueue()
    }

    fun skipToNext(): Track? {
        val current = _state.value
        if (current.items.isEmpty()) return null

        if (current.repeatMode == RepeatMode.ONE) {
            return current.currentTrack
        }

        val nextIndex = current.currentIndex + 1
        if (nextIndex < current.items.size) {
            _state.value = current.copy(currentIndex = nextIndex)
            persistQueue()
            return _state.value.currentTrack
        } else if (current.repeatMode == RepeatMode.ALL) {
            _state.value = current.copy(currentIndex = 0)
            persistQueue()
            return _state.value.currentTrack
        }
        return null
    }

    fun skipToPrevious(): Track? {
        val current = _state.value
        if (current.items.isEmpty()) return null

        if (current.repeatMode == RepeatMode.ONE) {
            return current.currentTrack
        }

        val prevIndex = current.currentIndex - 1
        if (prevIndex >= 0) {
            _state.value = current.copy(currentIndex = prevIndex)
            persistQueue()
            return _state.value.currentTrack
        } else if (current.repeatMode == RepeatMode.ALL) {
            _state.value = current.copy(currentIndex = current.items.lastIndex)
            persistQueue()
            return _state.value.currentTrack
        }
        return null
    }

    fun skipToIndex(index: Int): Track? {
        if (index in _state.value.items.indices) {
            _state.value = _state.value.copy(currentIndex = index)
            persistQueue()
            return _state.value.currentTrack
        }
        return null
    }

    fun toggleShuffle(): Boolean {
        val nextShuffle = !_state.value.isShuffleEnabled
        val currentTrack = _state.value.currentTrack

        val newItems = if (nextShuffle) {
            val list = (_state.value.items.ifEmpty { originalQueue }).toMutableList()
            if (currentTrack != null) {
                list.remove(currentTrack)
                list.shuffle(Random(42)) // Deterministic shuffle
                list.add(0, currentTrack)
            } else {
                list.shuffle(Random(42))
            }
            list
        } else {
            originalQueue.ifEmpty { _state.value.items }
        }

        val newIndex = if (currentTrack != null) newItems.indexOf(currentTrack).coerceAtLeast(0) else 0

        _state.value = _state.value.copy(
            items = newItems,
            currentIndex = newIndex,
            isShuffleEnabled = nextShuffle
        )
        persistQueue()
        return nextShuffle
    }

    fun toggleRepeat(): RepeatMode {
        val nextMode = when (_state.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _state.value = _state.value.copy(repeatMode = nextMode)
        return nextMode
    }

    fun clear() {
        originalQueue = emptyList()
        _state.value = QueueState()
        scope.launch {
            queueDao.clearQueue()
        }
    }

    private fun persistQueue() {
        val currentItems = _state.value.items
        scope.launch {
            queueDao.clearQueue()
            if (currentItems.isNotEmpty()) {
                val entities = currentItems.mapIndexed { index, track ->
                    QueueEntity(
                        position = index,
                        trackId = track.id,
                        addedAt = System.currentTimeMillis()
                    )
                }
                queueDao.insertQueue(entities)
            }
        }
    }

    private fun restoreQueue() {
        scope.launch {
            val queueEntities = queueDao.getQueue().firstOrNull() ?: emptyList()
            if (queueEntities.isNotEmpty()) {
                val trackIds = queueEntities.map { it.trackId }
                val tracks = trackDao.getTracks(trackIds).firstOrNull() ?: emptyList()
                val trackMap = tracks.associateBy { it.id }
                val restoredTracks = trackIds.mapNotNull { trackMap[it]?.asExternalModel() }

                if (restoredTracks.isNotEmpty()) {
                    originalQueue = restoredTracks
                    _state.value = _state.value.copy(
                        items = restoredTracks,
                        currentIndex = 0
                    )
                }
            }
        }
    }
}
