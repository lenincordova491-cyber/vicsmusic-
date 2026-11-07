package app.vicsmusic.core.player.dsp

import android.media.audiofx.Visualizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.hypot

class SpectrumVisualizer {
    private var visualizer: Visualizer? = null
    private val _magnitudes = MutableStateFlow(FloatArray(32))
    val magnitudes: StateFlow<FloatArray> = _magnitudes.asStateFlow()

    fun start(audioSessionId: Int) {
        stop()
        if (audioSessionId <= 0) return
        runCatching {
            visualizer = Visualizer(audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(512)
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            v: Visualizer?,
                            waveform: ByteArray?,
                            samplingRate: Int
                        ) = Unit

                        override fun onFftDataCapture(
                            v: Visualizer?,
                            fft: ByteArray?,
                            samplingRate: Int
                        ) {
                            if (fft == null) return
                            val bands = FloatArray(32)
                            for (i in 0 until 32) {
                                val rfk = fft[2 * i + 2].toInt()
                                val ifk = fft[2 * i + 3].toInt()
                                val magnitude = hypot(rfk.toDouble(), ifk.toDouble()).toFloat()
                                bands[i] = (magnitude / 128f).coerceIn(0f, 1f)
                            }
                            _magnitudes.value = bands
                        }
                    },
                    Visualizer.getMaxCaptureRate() / 2,
                    /* waveform = */ false,
                    /* fft = */ true
                )
                enabled = true
            }
        }
    }

    fun stop() {
        runCatching {
            visualizer?.enabled = false
            visualizer?.release()
        }
        visualizer = null
    }
}
