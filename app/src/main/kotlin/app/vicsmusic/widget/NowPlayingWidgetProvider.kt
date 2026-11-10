package app.vicsmusic.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.widget.RemoteViews
import app.vicsmusic.MainActivity
import app.vicsmusic.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URL

class NowPlayingWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return

        when (action) {
            ACTION_PLAYBACK_STATE_CHANGED -> {
                val title = intent.getStringExtra("title") ?: "Vicsmusic"
                val artist = intent.getStringExtra("artist") ?: "Toca para reproducir"
                val isPlaying = intent.getBooleanExtra("isPlaying", false)
                val artworkUrl = intent.getStringExtra("artworkUrl")
                notifyPlaybackUpdate(context, title, artist, isPlaying, artworkUrl)
            }
            ACTION_WIDGET_PLAY_PAUSE -> {
                sendServiceAction(context, "app.vicsmusic.ACTION_WIDGET_PLAY_PAUSE")
            }
            ACTION_WIDGET_NEXT -> {
                sendServiceAction(context, "app.vicsmusic.ACTION_WIDGET_NEXT")
            }
            ACTION_WIDGET_PREV -> {
                sendServiceAction(context, "app.vicsmusic.ACTION_WIDGET_PREV")
            }
        }
    }

    private fun sendServiceAction(context: Context, action: String) {
        val serviceIntent = Intent().apply {
            component = ComponentName(context, "app.vicsmusic.core.player.service.PlaybackService")
            this.action = action
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    companion object {
        const val ACTION_PLAYBACK_STATE_CHANGED = "app.vicsmusic.ACTION_PLAYBACK_STATE_CHANGED"
        const val ACTION_WIDGET_PLAY_PAUSE = "app.vicsmusic.widget.ACTION_PLAY_PAUSE"
        const val ACTION_WIDGET_NEXT = "app.vicsmusic.widget.ACTION_NEXT"
        const val ACTION_WIDGET_PREV = "app.vicsmusic.widget.ACTION_PREV"

        private var lastTitle: String = "Vicsmusic"
        private var lastArtist: String = "Toca para reproducir"
        private var lastIsPlaying: Boolean = false
        private var lastArtworkUrl: String? = null
        private var lastArtworkBitmap: Bitmap? = null

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            title: String = lastTitle,
            artist: String = lastArtist,
            isPlaying: Boolean = lastIsPlaying,
            artworkBitmap: Bitmap? = lastArtworkBitmap
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_now_playing)

            views.setTextViewText(R.id.widget_title, title)
            views.setTextViewText(R.id.widget_artist, artist)
            views.setImageViewResource(
                R.id.widget_play_pause,
                if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
            )

            if (artworkBitmap != null) {
                views.setImageViewBitmap(R.id.widget_artwork, artworkBitmap)
            } else {
                views.setImageViewResource(R.id.widget_artwork, R.drawable.ic_launcher_foreground)
            }

            // Click en el cuerpo abre la app
            val appIntent = Intent(context, MainActivity::class.java)
            val appPendingIntent = PendingIntent.getActivity(
                context, 0, appIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_info_layout, appPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_artwork, appPendingIntent)

            // Controles
            views.setOnClickPendingIntent(
                R.id.widget_play_pause,
                getBroadcastPendingIntent(context, ACTION_WIDGET_PLAY_PAUSE, 1)
            )
            views.setOnClickPendingIntent(
                R.id.widget_next,
                getBroadcastPendingIntent(context, ACTION_WIDGET_NEXT, 2)
            )
            views.setOnClickPendingIntent(
                R.id.widget_prev,
                getBroadcastPendingIntent(context, ACTION_WIDGET_PREV, 3)
            )

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun notifyPlaybackUpdate(
            context: Context,
            title: String,
            artist: String,
            isPlaying: Boolean,
            artworkUrl: String? = null
        ) {
            lastTitle = title
            lastArtist = artist
            lastIsPlaying = isPlaying

            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, NowPlayingWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            if (appWidgetIds.isEmpty()) return

            for (id in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, id, title, artist, isPlaying, lastArtworkBitmap)
            }

            if (!artworkUrl.isNullOrBlank() && artworkUrl != lastArtworkUrl) {
                lastArtworkUrl = artworkUrl
                CoroutineScope(Dispatchers.IO).launch {
                    val bitmap = runCatching {
                        val input = URL(artworkUrl).openStream()
                        BitmapFactory.decodeStream(input)
                    }.getOrNull()
                    if (bitmap != null) {
                        lastArtworkBitmap = bitmap
                        for (id in appWidgetIds) {
                            updateAppWidget(context, appWidgetManager, id, title, artist, isPlaying, bitmap)
                        }
                    }
                }
            }
        }

        private fun getBroadcastPendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
            val intent = Intent(context, NowPlayingWidgetProvider::class.java).apply {
                this.action = action
            }
            return PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
