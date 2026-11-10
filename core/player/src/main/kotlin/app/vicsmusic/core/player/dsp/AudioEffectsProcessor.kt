package app.vicsmusic.core.player.dsp

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class EqualizerBand(
    val index: Short,
    val centerFreqHz: Int,
    val currentLevelMb: Short,
    val minLevelMb: Short,
    val maxLevelMb: Short
)

data class AudioDspState(
    val isEqualizerEnabled: Boolean = false,
    val presets: List<String> = emptyList(),
    val selectedPresetIndex: Int = 0,
    val bands: List<EqualizerBand> = emptyList(),
    val bassBoostStrength: Short = 0, // 0..1000
    val loudnessNormalizationEnabled: Boolean = true, // Activada por defecto para volumen uniforme
    val targetGainMb: Int = 500, // +5 dB (normalización estándar -14 LUFS)
    val autoEqProfiles: List<AutoEqProfile> = AutoEqPresets.PROFILES,
    val selectedAutoEqProfileId: String? = null
)

@Singleton
class AudioEffectsProcessor @Inject constructor() {
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    private val _state = MutableStateFlow(AudioDspState())
    val state: StateFlow<AudioDspState> = _state.asStateFlow()

    fun attachToAudioSession(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        release()
        runCatching {
            val eq = Equalizer(0, audioSessionId).apply { enabled = _state.value.isEqualizerEnabled }
            val range = eq.bandLevelRange
            val bands = (0 until eq.numberOfBands).map { i ->
                val idx = i.toShort()
                EqualizerBand(
                    index = idx,
                    centerFreqHz = eq.getCenterFreq(idx) / 1000,
                    currentLevelMb = eq.getBandLevel(idx),
                    minLevelMb = range[0],
                    maxLevelMb = range[1]
                )
            }
            val presets = (0 until eq.numberOfPresets).map { eq.getPresetName(it.toShort()) }
            equalizer = eq
            bassBoost = BassBoost(0, audioSessionId).apply {
                enabled = _state.value.bassBoostStrength > 0
                setStrength(_state.value.bassBoostStrength)
            }
            loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                enabled = _state.value.loudnessNormalizationEnabled
                setTargetGain(_state.value.targetGainMb)
            }
            _state.value = _state.value.copy(bands = bands, presets = presets)
        }
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        equalizer?.enabled = enabled
        _state.value = _state.value.copy(isEqualizerEnabled = enabled)
    }

    fun setBandLevel(bandIndex: Short, levelMb: Short) {
        equalizer?.setBandLevel(bandIndex, levelMb)
        _state.value = _state.value.copy(
            bands = _state.value.bands.map {
                if (it.index == bandIndex) it.copy(currentLevelMb = levelMb) else it
            }
        )
    }

    fun setBassBoost(strength: Short) {
        val clamped = strength.coerceIn(0, 1000)
        bassBoost?.apply {
            enabled = clamped > 0
            setStrength(clamped)
        }
        _state.value = _state.value.copy(bassBoostStrength = clamped)
    }

    fun setLoudnessNormalization(enabled: Boolean) {
        loudnessEnhancer?.apply {
            this.enabled = enabled
            if (enabled) setTargetGain(_state.value.targetGainMb)
        }
        _state.value = _state.value.copy(loudnessNormalizationEnabled = enabled)
    }

    /**
     * Aplica la calibración de auriculares AutoEQ ajustando las ganancias
     * relativas en cada banda del ecualizador.
     */
    fun applyAutoEqProfile(profileId: String) {
        val profile = AutoEqPresets.PROFILES.firstOrNull { it.id == profileId } ?: return
        val eq = equalizer ?: return

        setEqualizerEnabled(true)
        val numBands = eq.numberOfBands.toInt()
        val range = eq.bandLevelRange
        val minLevel = range[0]
        val maxLevel = range[1]

        val updatedBands = mutableListOf<EqualizerBand>()
        for (i in 0 until numBands) {
            val bandIdx = i.toShort()
            val gainIdx = (i * profile.gainsMb.size) / numBands.coerceAtLeast(1)
            val desiredGain = profile.gainsMb.getOrElse(gainIdx) { 0.toShort() }
            val clampLevel = desiredGain.coerceIn(minLevel, maxLevel)

            eq.setBandLevel(bandIdx, clampLevel)
            updatedBands.add(
                EqualizerBand(
                    index = bandIdx,
                    centerFreqHz = eq.getCenterFreq(bandIdx) / 1000,
                    currentLevelMb = clampLevel,
                    minLevelMb = minLevel,
                    maxLevelMb = maxLevel
                )
            )
        }

        _state.value = _state.value.copy(
            isEqualizerEnabled = true,
            selectedAutoEqProfileId = profileId,
            bands = updatedBands
        )
    }

    fun release() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { loudnessEnhancer?.release() }
        equalizer = null
        bassBoost = null
        loudnessEnhancer = null
    }
}
