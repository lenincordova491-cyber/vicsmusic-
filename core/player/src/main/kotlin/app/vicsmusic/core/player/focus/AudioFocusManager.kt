package app.vicsmusic.core.player.focus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build

class AudioFocusManager(
    private val context: Context,
    private val onPause: () -> Unit,
    private val onResume: () -> Unit,
    private val onSetVolume: (Float) -> Unit
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var playbackNowAuthorized = false
    private var resumeOnFocusGain = false
    private var focusRequest: AudioFocusRequest? = null

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                playbackNowAuthorized = true
                onSetVolume(1.0f)
                if (resumeOnFocusGain) {
                    resumeOnFocusGain = false
                    onResume()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                playbackNowAuthorized = false
                onSetVolume(0.2f)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                playbackNowAuthorized = false
                resumeOnFocusGain = true
                onPause()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                playbackNowAuthorized = false
                resumeOnFocusGain = false
                onPause()
            }
        }
    }

    private var isBecomingNoisyRegistered = false
    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                onPause()
            }
        }
    }

    fun requestAudioFocus(): Boolean {
        if (playbackNowAuthorized) return true

        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
            focusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }

        playbackNowAuthorized = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (playbackNowAuthorized) {
            registerBecomingNoisy()
        }
        return playbackNowAuthorized
    }

    fun abandonAudioFocus() {
        if (!playbackNowAuthorized) return
        playbackNowAuthorized = false
        resumeOnFocusGain = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
        unregisterBecomingNoisy()
    }

    private fun registerBecomingNoisy() {
        if (!isBecomingNoisyRegistered) {
            val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            context.registerReceiver(becomingNoisyReceiver, filter)
            isBecomingNoisyRegistered = true
        }
    }

    private fun unregisterBecomingNoisy() {
        if (isBecomingNoisyRegistered) {
            runCatching { context.unregisterReceiver(becomingNoisyReceiver) }
            isBecomingNoisyRegistered = false
        }
    }
}
