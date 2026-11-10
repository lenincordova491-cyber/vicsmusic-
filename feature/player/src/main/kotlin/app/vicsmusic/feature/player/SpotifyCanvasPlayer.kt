package app.vicsmusic.feature.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay

/**
 * Modos de ahorro de datos y batería para Spotify Canvas.
 */
enum class CanvasDataMode(
    val title: String,
    val subtitle: String
) {
    ALWAYS(
        title = "Siempre activado",
        subtitle = "Reproduce bucles Canvas en Wi-Fi y datos móviles"
    ),
    WIFI_ONLY(
        title = "Solo en Wi-Fi",
        subtitle = "Ahorra datos móviles cargando Canvas únicamente en Wi-Fi"
    ),
    DISABLED(
        title = "Desactivado (Ahorro de batería)",
        subtitle = "Muestra siempre la carátula estática sin decodificar video"
    )
}

object CanvasPreferences {
    private const val PREFS_NAME = "vicsmusic_canvas_prefs"
    private const val KEY_MODE = "canvas_data_mode"

    fun getMode(context: Context): CanvasDataMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_MODE, CanvasDataMode.ALWAYS.name)
        return CanvasDataMode.entries.firstOrNull { it.name == raw } ?: CanvasDataMode.ALWAYS
    }

    fun setMode(context: Context, mode: CanvasDataMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, mode.name)
            .apply()
    }

    fun shouldPlayCanvas(context: Context, mode: CanvasDataMode): Boolean {
        return when (mode) {
            CanvasDataMode.ALWAYS -> true
            CanvasDataMode.DISABLED -> false
            CanvasDataMode.WIFI_ONLY -> isOnUnmeteredWifi(context)
        }
    }

    private fun isOnUnmeteredWifi(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }
}

/**
 * Reproductor de Canvas de video vertical 9:16 optimizado contra consumo de batería.
 *
 * Optimizaciones aplicadas:
 * 1. Pista de audio deshabilitada en hardware (C.TRACK_TYPE_AUDIO apagado).
 * 2. Pausa automática si la app va a segundo plano o se apaga la pantalla.
 * 3. Transición suave de fundido cruzado desde la portada estática hacia el video una vez listo.
 * 4. Bucle infinito en memoria sin peticiones de red repetitivas (recorte de 7s para videos musicales).
 */
@OptIn(UnstableApi::class)
@Composable
fun SpotifyCanvasContainer(
    staticArtworkUrl: String?,
    canvasVideoUrl: String?,
    isCanvasEnabledInSettings: Boolean = true,
    isNowPlayingExpanded: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isVideoReady by remember(canvasVideoUrl) { mutableStateOf(false) }

    // Instancia liviana de ExoPlayer dedicada exclusivamente al Canvas con User-Agent oficial de Android YouTube
    val canvasPlayer = remember(canvasVideoUrl, isCanvasEnabledInSettings) {
        if (!canvasVideoUrl.isNullOrBlank() && isCanvasEnabledInSettings) {
            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("com.google.android.youtube/20.10.38 (Linux; U; Android 13; en_US)")
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(10_000)
                .setReadTimeoutMs(10_000)

            ExoPlayer.Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(httpDataSourceFactory))
                .build()
                .apply {
                    repeatMode = Player.REPEAT_MODE_ONE
                    volume = 0f // Mute absoluto
                    // Optimización clave: Desactivar completamente el decodificador de audio y limitar resolución a 480p/30fps
                    trackSelectionParameters = trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                        .setMaxVideoSize(854, 480)
                        .setMaxVideoFrameRate(30)
                        .build()

                    setMediaItem(MediaItem.fromUri(canvasVideoUrl))
                    prepare()
                    playWhenReady = isNowPlayingExpanded
                }
        } else null
    }

    // Listener para mostrar el video únicamente cuando el primer frame esté listo y posicionar en una escena dinámica
    DisposableEffect(canvasPlayer) {
        var didInitialSeek = false
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && !didInitialSeek) {
                    val dur = canvasPlayer?.duration ?: 0L
                    if (dur > 40_000L) {
                        didInitialSeek = true
                        canvasPlayer?.seekTo(22_000L)
                    }
                }
            }

            override fun onRenderedFirstFrame() {
                isVideoReady = true
            }

            override fun onPlayerError(error: PlaybackException) {
                android.util.Log.w("SpotifyCanvasPlayer", "Error reproduciendo Canvas: ${error.message}")
                isVideoReady = false
            }
        }
        canvasPlayer?.addListener(listener)
        onDispose {
            isVideoReady = false
            canvasPlayer?.removeListener(listener)
            canvasPlayer?.release()
        }
    }

    // Bucle visual de 8 segundos (22s -> 30s) para videos musicales largos sin usar ClippingMediaSource que falla en HTTP
    LaunchedEffect(canvasPlayer, isVideoReady, isNowPlayingExpanded) {
        while (canvasPlayer != null && isVideoReady && isNowPlayingExpanded) {
            val dur = canvasPlayer.duration
            val pos = canvasPlayer.currentPosition
            if (dur > 40_000L && pos >= 30_000L) {
                canvasPlayer.seekTo(22_000L)
            }
            delay(400L)
        }
    }

    // Pausar y reanudar según visibilidad de la pantalla (ahorro de batería)
    DisposableEffect(lifecycleOwner, isNowPlayingExpanded, canvasPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (isNowPlayingExpanded) canvasPlayer?.play()
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    canvasPlayer?.pause()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Si el usuario minimiza el reproductor a la barra inferior, pausar de inmediato
    LaunchedEffect(isNowPlayingExpanded, canvasPlayer) {
        if (isNowPlayingExpanded) {
            canvasPlayer?.play()
        } else {
            canvasPlayer?.pause()
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF181818))
    ) {
        // 1. Portada estática como respaldo permanente y mientras carga el video
        AsyncImage(
            model = staticArtworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Video vertical en loop con fundido suave al cargar
        AnimatedVisibility(
            visible = isVideoReady && isCanvasEnabledInSettings && canvasPlayer != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = canvasPlayer
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { view ->
                    if (view.player !== canvasPlayer) {
                        view.player = canvasPlayer
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 3. Degradado vertical sutil para mantener legibles los títulos y controles inferiores
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.20f),
                            Color.Black.copy(alpha = 0.55f)
                        )
                    )
                )
        )
    }
}
