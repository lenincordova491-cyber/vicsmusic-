package app.vicsmusic.feature.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FileDownloadDone
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vicsmusic.core.model.Lyrics
import app.vicsmusic.core.model.PlaybackState
import app.vicsmusic.core.model.PlaybackStatus
import app.vicsmusic.core.model.RepeatMode
import app.vicsmusic.core.model.Track
import app.vicsmusic.core.player.queue.QueueState
import app.vicsmusic.core.ui.R
import app.vicsmusic.core.ui.component.ArtworkImage
import app.vicsmusic.core.ui.component.TrackRow
import java.util.Locale

@Composable
fun NowPlayingRoute(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val queueState by viewModel.queueState.collectAsStateWithLifecycle()
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val canvasVideoUrl by viewModel.canvasVideoUrl.collectAsStateWithLifecycle()
    val artistTracks by viewModel.artistTracks.collectAsStateWithLifecycle()
    val similarTracks by viewModel.similarTracks.collectAsStateWithLifecycle()
    val downloadedTrackIds by viewModel.downloadedTrackIds.collectAsStateWithLifecycle()
    val downloadingTrackId by viewModel.downloadingTrackId.collectAsStateWithLifecycle()

    val currentTrack = playbackState.currentTrack
    val isDownloaded = currentTrack?.id != null && downloadedTrackIds.contains(currentTrack.id)
    val isDownloading = currentTrack?.id != null && downloadingTrackId == currentTrack.id

    NowPlayingScreen(
        playbackState = playbackState,
        queueState = queueState,
        lyrics = lyrics,
        canvasVideoUrl = canvasVideoUrl,
        artistTracks = artistTracks,
        similarTracks = similarTracks,
        onDismiss = onDismiss,
        onPlayPauseClick = viewModel::togglePlayPause,
        onSkipNextClick = viewModel::skipNext,
        onSkipPreviousClick = viewModel::skipPrevious,
        onSeekTo = viewModel::seekTo,
        onShuffleClick = viewModel::toggleShuffle,
        onRepeatClick = viewModel::toggleRepeat,
        onLikeClick = viewModel::toggleLike,
        onDownloadClick = { track -> viewModel.downloadTrack(track) },
        isDownloaded = isDownloaded,
        isDownloading = isDownloading,
        onPlayQueueIndex = viewModel::playTrackAtIndex,
        onRemoveQueueItem = viewModel::removeQueueItem,
        onTrackClick = viewModel::playTrack,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    playbackState: PlaybackState,
    queueState: QueueState,
    lyrics: app.vicsmusic.core.model.Lyrics? = null,
    canvasVideoUrl: String? = null,
    artistTracks: List<Track> = emptyList(),
    similarTracks: List<Track> = emptyList(),
    onDismiss: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onSkipNextClick: () -> Unit,
    onSkipPreviousClick: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onLikeClick: () -> Unit,
    onDownloadClick: (Track) -> Unit = {},
    isDownloaded: Boolean = false,
    isDownloading: Boolean = false,
    onPlayQueueIndex: (Int) -> Unit,
    onRemoveQueueItem: (Int) -> Unit = {},
    onTrackClick: (Track) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val track = playbackState.currentTrack
    var showQueueSheet by remember { mutableStateOf(false) }
    var showFullScreenLyrics by remember { mutableStateOf(false) }
    var selectedLanguage by remember { mutableStateOf(TranslationLanguage.ORIGINAL) }
    var showTranslationSheet by remember { mutableStateOf(false) }
    var canvasDataMode by remember { mutableStateOf(CanvasPreferences.getMode(context)) }
    var showCanvasModeSheet by remember { mutableStateOf(false) }
    val isCanvasAllowed = remember(canvasDataMode) {
        CanvasPreferences.shouldPlayCanvas(context, canvasDataMode)
    }

    BackHandler(enabled = true) {
        when {
            showFullScreenLyrics -> showFullScreenLyrics = false
            showTranslationSheet -> showTranslationSheet = false
            showCanvasModeSheet -> showCanvasModeSheet = false
            showQueueSheet -> showQueueSheet = false
            else -> onDismiss()
        }
    }

    val displayedLyrics by produceState<Lyrics?>(initialValue = lyrics, key1 = lyrics, key2 = selectedLanguage) {
        val currLyrics = lyrics
        if (currLyrics == null || selectedLanguage == TranslationLanguage.ORIGINAL) {
            value = currLyrics
        } else {
            value = LyricsTranslationEngine.translateLyrics(currLyrics, selectedLanguage, track?.title, track?.artistName)
        }
    }

    val hasLyrics = lyrics != null && lyrics.lines.isNotEmpty()

    val activeIndex by remember(lyrics, playbackState.positionMs) {
        derivedStateOf {
            val lines = lyrics?.lines.orEmpty()
            if (lines.isEmpty() || lyrics?.isSynced != true) 0
            else {
                val pos = playbackState.positionMs + 180L // 180ms visual anticipation
                val idx = lines.binarySearch { it.timeMs.compareTo(pos) }
                if (idx >= 0) idx else (-idx - 2).coerceAtLeast(0)
            }
        }
    }
    val cardScrollState = rememberScrollState()
    var lastUserScrollTimeMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(cardScrollState.isScrollInProgress) {
        if (cardScrollState.isScrollInProgress) {
            lastUserScrollTimeMs = System.currentTimeMillis()
        }
    }

    LaunchedEffect(activeIndex, track?.id, hasLyrics) {
        val userRecentlyScrolled = (System.currentTimeMillis() - lastUserScrollTimeMs) < 3_500L
        if (hasLyrics && lyrics != null && lyrics.isSynced && activeIndex >= 0 && !cardScrollState.isScrollInProgress && !userRecentlyScrolled) {
            cardScrollState.animateScrollTo((activeIndex * 90).coerceAtLeast(0))
        }
    }

    val lyricsCardColor = remember(track?.id) {
        val vibrantPalettes = listOf(
            Color(0xFF1D4ED8), // Vibrant Electric Blue
            Color(0xFF15803D), // Vibrant Spotify Emerald
            Color(0xFF6D28D9), // Vibrant Royal Purple
            Color(0xFFB91C1C), // Vibrant Crimson Red
            Color(0xFF0F766E), // Vibrant Deep Teal
            Color(0xFFB45309)  // Vibrant Warm Amber
        )
        val idx = ((track?.id?.hashCode() ?: 0) and 0x7FFFFFFF) % vibrantPalettes.size
        vibrantPalettes[idx]
    }

    val ambientColors = remember(track?.id) {
        val palettes = listOf(
            Color(0xFF1E3A5F), // Deep Indigo
            Color(0xFF14532D), // Deep Emerald
            Color(0xFF4C1D95), // Deep Violet
            Color(0xFF7F1D1D), // Deep Crimson
            Color(0xFF134E4A), // Deep Teal
            Color(0xFF78350F)  // Deep Amber
        )
        val idx = ((track?.id?.hashCode() ?: 0) and 0x7FFFFFFF) % palettes.size
        listOf(palettes[idx], Color(0xFF121212), Color(0xFF0A0A0A))
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color(0xFF121212)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(ambientColors))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Cerrar reproductor",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showCanvasModeSheet = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.now_playing).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Text(
                        text = when {
                            track?.sourceId == "local" -> "En este dispositivo"
                            canvasDataMode == CanvasDataMode.DISABLED -> "Canvas: Ahorro • Sin anuncios"
                            else -> "Canvas ${if (canvasDataMode == CanvasDataMode.WIFI_ONLY) "Wi-Fi" else "HD"} • Sin anuncios"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1DB954)
                    )
                }

                IconButton(onClick = { showQueueSheet = true }) {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = stringResource(R.string.queue),
                        tint = Color.White
                    )
                }
            }

            if (playbackState.status == PlaybackStatus.ERROR && !playbackState.errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF7F1D1D)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = playbackState.errorMessage ?: "Error de reproducción",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Siguiente",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1DB954),
                            modifier = Modifier.clickable { onSkipNextClick() }.padding(start = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Center Album Artwork / Spotify Canvas Loop
            SpotifyCanvasContainer(
                staticArtworkUrl = track?.artworkUrl,
                canvasVideoUrl = canvasVideoUrl,
                isCanvasEnabledInSettings = isCanvasAllowed,
                isNowPlayingExpanded = !showFullScreenLyrics && !showQueueSheet && !showTranslationSheet && !showCanvasModeSheet,
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .aspectRatio(if (!canvasVideoUrl.isNullOrBlank() && isCanvasAllowed) 0.82f else 1f)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Title & Artist with Like Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track?.title ?: stringResource(R.string.unknown_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = track?.artistName ?: stringResource(R.string.unknown_artist),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val isLiked = track?.isLiked == true
                val heartScale by animateFloatAsState(
                    targetValue = if (isLiked) 1.25f else 1.0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "heartScale"
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Botón de Descarga MP3
                    IconButton(
                        onClick = { track?.let { onDownloadClick(it) } }
                    ) {
                        if (isDownloading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color(0xFF1DB954),
                                strokeWidth = 2.5.dp
                            )
                        } else if (isDownloaded) {
                            Icon(
                                imageVector = Icons.Default.FileDownloadDone,
                                contentDescription = "Descargado en el dispositivo",
                                tint = Color(0xFF1DB954),
                                modifier = Modifier.size(26.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Descargar canción en MP3",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(onClick = onLikeClick) {
                        Icon(
                            imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorito",
                            tint = if (isLiked) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(30.dp)
                                .scale(heartScale)
                        )
                    }
                }
            }

            // Seekbar Slider with Timers
            var sliderPosition by remember { mutableFloatStateOf(-1f) }
            val currentPos = if (sliderPosition >= 0f) sliderPosition.toLong() else playbackState.positionMs
            val duration = playbackState.durationMs.coerceAtLeast(1L)

            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = currentPos.coerceIn(0L, duration).toFloat(),
                    onValueChange = { sliderPosition = it },
                    onValueChangeFinished = {
                        if (sliderPosition >= 0f) {
                            onSeekTo(sliderPosition.toLong())
                            sliderPosition = -1f
                        }
                    },
                    valueRange = 0f..duration.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDuration(currentPos),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatDuration(duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Controls Row (Shuffle, Prev, Play/Pause, Next, Repeat)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle
                IconButton(onClick = onShuffleClick) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = stringResource(R.string.shuffle),
                        tint = if (playbackState.isShuffleEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                // Previous
                IconButton(
                    onClick = onSkipPreviousClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = stringResource(R.string.previous),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Play / Pause FAB-style button
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onPlayPauseClick),
                    contentAlignment = Alignment.Center
                ) {
                    if (playbackState.status == PlaybackStatus.BUFFERING) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(34.dp)
                        )
                    } else {
                        Icon(
                            imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playbackState.isPlaying) stringResource(R.string.pause) else stringResource(R.string.play),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                // Next
                IconButton(
                    onClick = onSkipNextClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = stringResource(R.string.next),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Repeat Mode
                IconButton(onClick = onRepeatClick) {
                    val icon = when (playbackState.repeatMode) {
                        RepeatMode.ONE -> Icons.Default.RepeatOne
                        else -> Icons.Default.Repeat
                    }
                    val tint = when (playbackState.repeatMode) {
                        RepeatMode.OFF -> MaterialTheme.colorScheme.onSurfaceVariant
                        RepeatMode.ALL, RepeatMode.ONE -> MaterialTheme.colorScheme.primary
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = stringResource(R.string.repeat),
                        tint = tint
                    )
                }
            }

            // Bottom Lyrics Card
            if (hasLyrics && lyrics != null) {
                Spacer(modifier = Modifier.height(20.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = lyricsCardColor),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .padding(bottom = 24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        lyricsCardColor,
                                        lyricsCardColor.copy(alpha = 0.85f),
                                        Color(0xFF181818).copy(alpha = 0.45f)
                                    )
                                )
                            )
                            .padding(20.dp)
                    ) {
                        // Spotify Lyrics Card Header: "Letras" + Varita Mágica + Expand Icon Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Letras",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Varita mágica de traducción manual
                                IconButton(
                                    onClick = { showTranslationSheet = true },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (selectedLanguage != TranslationLanguage.ORIGINAL)
                                                Color(0xFF1DB954).copy(alpha = 0.35f)
                                            else Color.Black.copy(alpha = 0.35f)
                                        )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = "Traducir letra",
                                        tint = if (selectedLanguage != TranslationLanguage.ORIGINAL)
                                            Color(0xFF1DB954)
                                        else Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { showFullScreenLyrics = true },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.35f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Fullscreen,
                                        contentDescription = "Pantalla completa",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }

                        // Indicador de traducción activa
                        if (selectedLanguage != TranslationLanguage.ORIGINAL) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.12f))
                                    .clickable { showTranslationSheet = true }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = Color(0xFF1DB954),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Traducido al ${selectedLanguage.displayName}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                Text(
                                    text = "Cambiar",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1DB954)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(cardScrollState),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            (displayedLyrics ?: lyrics).lines.forEachIndexed { index, line ->
                                val isCurrent = !lyrics.isSynced || index == activeIndex
                                val isPast = lyrics.isSynced && index < activeIndex
                                Text(
                                    text = line.text,
                                    style = if (isCurrent) {
                                        MaterialTheme.typography.headlineSmall
                                    } else {
                                        MaterialTheme.typography.titleMedium
                                    },
                                    fontWeight = FontWeight.ExtraBold,
                                    color = when {
                                        isCurrent -> Color.White
                                        isPast -> Color.White.copy(alpha = 0.38f)
                                        else -> Color.White.copy(alpha = 0.58f)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable(enabled = lyrics.isSynced) { onSeekTo(line.timeMs) }
                                        .padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // -------------------------------------------------------------
            // ACERCA DEL ARTISTA (Spotify Artist Profile Card)
            // -------------------------------------------------------------
            val artistBio = remember(track?.artistName, track?.artworkUrl) {
                ArtistBioProvider.getArtistInfo(track?.artistName, track?.artworkUrl)
            }
            var isFollowingArtist by remember(track?.artistName) { mutableStateOf(false) }

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF242424)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Artist Banner Header with Dark Gradient Overlay
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(230.dp)
                    ) {
                        ArtworkImage(
                            artworkUrl = artistBio.bannerUrl,
                            contentDescription = artistBio.artistName,
                            size = 400.dp,
                            cornerRadius = 0.dp,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.Transparent,
                                            Color(0xFF242424).copy(alpha = 0.6f),
                                            Color(0xFF242424)
                                        )
                                    )
                                )
                        )

                        Text(
                            text = "Acerca del artista",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(16.dp)
                        )

                        // Bottom Overlay: Artist Name + Monthly Listeners + Follow Button
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = artistBio.artistName,
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = artistBio.monthlyListeners,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White.copy(alpha = 0.85f)
                                    )
                                }

                                // Follow Pill Button (Seguir / Siguiendo)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(if (isFollowingArtist) Color.White.copy(alpha = 0.2f) else Color.White)
                                        .clickable { isFollowingArtist = !isFollowingArtist }
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (isFollowingArtist) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Text(
                                            text = if (isFollowingArtist) "Siguiendo" else "Seguir",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isFollowingArtist) Color.White else Color.Black
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Biography & Origin Details
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = artistBio.bio,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.88f),
                            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.25f
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // Button: Explorar [Nombre del artista]
                        Button(
                            onClick = {
                                val targetTrack = artistTracks.firstOrNull { it.id != track?.id } ?: track
                                if (targetTrack != null) {
                                    onTrackClick(targetTrack)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1DB954),
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Explore,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Explorar ${artistBio.artistName}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // -------------------------------------------------------------
            // CANCIONES DE [ARTISTA] (Popular Tracks by Artist)
            // -------------------------------------------------------------
            if (artistTracks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(28.dp))

                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Canciones de ${artistBio.artistName}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(artistTracks, key = { "art_${it.id}" }) { artistTrack ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .width(136.dp)
                                    .clickable { onTrackClick(artistTrack) }
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Box {
                                        ArtworkImage(
                                            artworkUrl = artistTrack.artworkUrl,
                                            contentDescription = artistTrack.title,
                                            size = 116.dp,
                                            cornerRadius = 8.dp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = artistTrack.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = artistTrack.artistName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.65f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // -------------------------------------------------------------
            // SIMILAR A [ARTISTA] (Similar Artists & Tracks)
            // -------------------------------------------------------------
            if (similarTracks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(28.dp))

                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Similar a ${artistBio.artistName}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(similarTracks, key = { "sim_${it.id}" }) { simTrack ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .width(136.dp)
                                    .clickable { onTrackClick(simTrack) }
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Box {
                                        ArtworkImage(
                                            artworkUrl = simTrack.artworkUrl,
                                            contentDescription = simTrack.title,
                                            size = 116.dp,
                                            cornerRadius = 8.dp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = simTrack.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = simTrack.artistName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF1DB954),
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    // Full-Screen Spotify Karaoke Modal
    if (showFullScreenLyrics && lyrics != null && lyrics.lines.isNotEmpty()) {
        val fullSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val activeIdx = if (lyrics.isSynced) {
            lyrics.lines.indexOfLast { it.timeMs <= playbackState.positionMs }.coerceAtLeast(0)
        } else {
            0
        }
        val fullListState = rememberLazyListState()

        LaunchedEffect(activeIdx) {
            if (lyrics.isSynced && activeIdx >= 0) {
                fullListState.animateScrollToItem((activeIdx - 2).coerceAtLeast(0))
            }
        }

        ModalBottomSheet(
            onDismissRequest = { showFullScreenLyrics = false },
            sheetState = fullSheetState,
            containerColor = lyricsCardColor,
            dragHandle = null
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(lyricsCardColor, lyricsCardColor.copy(alpha = 0.9f), Color(0xFF121212))
                        )
                    )
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                // Top Header inside Full-Screen Lyrics
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track?.title ?: "Canción actual",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track?.artistName ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = { showTranslationSheet = true },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selectedLanguage != TranslationLanguage.ORIGINAL)
                                        Color(0xFF1DB954).copy(alpha = 0.35f)
                                    else Color.Black.copy(alpha = 0.3f)
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Traducir letra",
                                tint = if (selectedLanguage != TranslationLanguage.ORIGINAL)
                                    Color(0xFF1DB954)
                                else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = { showFullScreenLyrics = false },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.3f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar letras",
                                tint = Color.White
                            )
                        }
                    }
                }

                if (selectedLanguage != TranslationLanguage.ORIGINAL) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .clickable { showTranslationSheet = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFF1DB954),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Traducido al ${selectedLanguage.displayName}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Text(
                            text = "Cambiar",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1DB954)
                        )
                    }
                }

                // Full-screen scrolling Karaoke verses
                LazyColumn(
                    state = fullListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    itemsIndexed((displayedLyrics ?: lyrics).lines) { index, line ->
                        val isCurrent = !lyrics.isSynced || index == activeIdx
                        val isPast = lyrics.isSynced && index < activeIdx
                        Text(
                            text = line.text,
                            style = if (isCurrent) {
                                MaterialTheme.typography.headlineMedium
                            } else {
                                MaterialTheme.typography.titleLarge
                            },
                            fontWeight = FontWeight.ExtraBold,
                            color = when {
                                isCurrent -> Color.White
                                isPast -> Color.White.copy(alpha = 0.35f)
                                else -> Color.White.copy(alpha = 0.55f)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = lyrics.isSynced) { onSeekTo(line.timeMs) }
                                .padding(vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Mini Transport Bar at Bottom of Full-Screen Lyrics
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatDuration(playbackState.positionMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onSkipPreviousClick) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Anterior",
                                tint = Color.White,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .clickable(onClick = onPlayPauseClick),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.Black,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                        IconButton(onClick = onSkipNextClick) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Siguiente",
                                tint = Color.White,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                    Text(
                        text = formatDuration(playbackState.durationMs.coerceAtLeast(1L)),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }

    // Queue Bottom Sheet
    if (showQueueSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showQueueSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.queue),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${queueState.items.size} canciones",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "Cola inteligente • Adaptada al gusto del usuario y afinidad de género",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1DB954),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(
                        items = queueState.items,
                        key = { index, trackItem -> "${trackItem.id}_$index" }
                    ) { index, trackItem ->
                        val isCurrent = index == queueState.currentIndex
                        val isAutoplay = queueState.autoplayTrackIds.contains(trackItem.id)
                        val isFirstAutoplay = isAutoplay && (index == 0 || !queueState.autoplayTrackIds.contains(queueState.items[index - 1].id))

                        if (isFirstAutoplay) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Reproducción automática",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "· Similar según tus gustos",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TrackRow(
                                track = trackItem,
                                isPlaying = isCurrent && playbackState.isPlaying,
                                onClick = {
                                    onPlayQueueIndex(index)
                                    showQueueSheet = false
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .let { mod ->
                                        if (isCurrent) {
                                            mod.background(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                                RoundedCornerShape(12.dp)
                                            )
                                        } else mod
                                    }
                            )
                            if (!isCurrent) {
                                IconButton(
                                    onClick = { onRemoveQueueItem(index) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Quitar de la cola",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Translation Bottom Sheet
    if (showTranslationSheet) {
        val transSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showTranslationSheet = false },
            sheetState = transSheetState,
            containerColor = Color(0xFF1E1E1E),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.3f))
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1DB954).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color(0xFF1DB954),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Traducir letra",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Selecciona un idioma para ver la traducción manual",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(TranslationLanguage.entries) { lang ->
                        val isSelected = lang == selectedLanguage
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) Color(0xFF2E2E2E) else Color(0xFF181818)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedLanguage = lang
                                    showTranslationSheet = false
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = lang.flag,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = lang.displayName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color(0xFF1DB954) else Color.White
                                    )
                                }

                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Seleccionado",
                                        tint = Color(0xFF1DB954),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Canvas Data & Battery Saver Bottom Sheet
    if (showCanvasModeSheet) {
        val canvasSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showCanvasModeSheet = false },
            sheetState = canvasSheetState,
            containerColor = Color(0xFF1E1E1E)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = "Spotify Canvas (Videos en bucle)",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Decodificación H.264 sin pista de audio para proteger la batería",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                CanvasDataMode.entries.forEach { mode ->
                    val isSelected = mode == canvasDataMode
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) Color(0xFF2E2E2E) else Color(0xFF181818)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                canvasDataMode = mode
                                CanvasPreferences.setMode(context, mode)
                                showCanvasModeSheet = false
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = mode.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color(0xFF1DB954) else Color.White
                                )
                                Text(
                                    text = mode.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.65f)
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Seleccionado",
                                    tint = Color(0xFF1DB954),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}
