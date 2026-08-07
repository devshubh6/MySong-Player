package com.shubh.song.media

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import com.shubh.song.MySongApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 5/10-band interactive equalizer built on the Android AudioEffect API,
 * plus 3D Bass Boost and Virtualizer effects. Presets are approximated from
 * classic EQ curves and scaled to the device's band count.
 *
 * A persisted master switch controls whether any AudioEffect is attached to
 * the ExoPlayer audio session at all. When the switch is OFF the effects are
 * fully released/detached so digital gain stays 100% untouched.
 */
object EqualizerController {

    data class EqUiState(
        val available: Boolean = false,
        val enabled: Boolean = false, // default OFF on clean install
        val bandFrequencies: List<Int> = emptyList(),
        val levels: List<Int> = emptyList(),
        val preset: String = "flat",
        val bassBoost: Int = 0,
        val virtualizer: Int = 0
    )

    private val _state = MutableStateFlow(EqUiState())
    val state: StateFlow<EqUiState> = _state

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var lastAudioSessionId = -1
    private var currentLevels: List<Int> = emptyList()
    private var currentPreset = "flat"
    private var currentBass = 0
    private var currentVirtual = 0

    private val presetProfiles = mapOf(
        "flat" to floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        "bass" to floatArrayOf(6f, 5f, 3f, 2f, 1f, 0f, 0f, 0f, 0f, 0f),
        "treble" to floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 3f, 5f),
        "pop" to floatArrayOf(2f, 3f, 1f, -1f, 0f, 2f, 3f, 2f, 0f, 1f),
        "rock" to floatArrayOf(4f, 3f, 1f, -1f, -1f, 1f, 3f, 3f, 2f, 2f),
        "classical" to floatArrayOf(3f, 2f, 1f, 0f, -1f, -1f, 0f, 2f, 3f, 3f),
        "jazz" to floatArrayOf(2f, 2f, 1f, 0f, -1f, 0f, 1f, 2f, 3f, 2f)
    )

    private fun masterEnabled(): Boolean =
        MySongApp.instance.settingsRepository.eqEnabled

    fun attach(audioSessionId: Int) {
        lastAudioSessionId = audioSessionId
        if (!masterEnabled()) {
            release()
            return
        }
        release()
        try {
            val eq = Equalizer(0, audioSessionId)
            val bands = eq.numberOfBands.toInt().coerceIn(1, 10)
            val range = eq.bandLevelRange
            currentLevels = List(bands) { 0 }
            currentPreset = "flat"
            currentBass = 0
            currentVirtual = 0

            val freqs = (0 until bands).map { eq.getCenterFreq(it.toShort()).toInt() }

            val bb = BassBoost(0, audioSessionId)
            val virtual = Virtualizer(0, audioSessionId)

            equalizer = eq
            bassBoost = bb
            virtualizer = virtual

            _state.value = EqUiState(
                available = true,
                enabled = true,
                bandFrequencies = freqs,
                levels = currentLevels,
                preset = currentPreset,
                bassBoost = currentBass,
                virtualizer = currentVirtual
            )
            applyPreset("flat")
        } catch (e: Exception) {
            release()
        }
    }

    /** Master switch: ON attaches all effects, OFF detaches them entirely. */
    fun setEnabled(on: Boolean) {
        MySongApp.instance.settingsRepository.setEqEnabled(on)
        if (on) {
            if (lastAudioSessionId > 0) attach(lastAudioSessionId)
            pushState()
        } else {
            release()
            pushState()
        }
    }

    fun release() {
        try {
            equalizer?.release()
            bassBoost?.release()
            virtualizer?.release()
        } catch (_: Exception) {
        }
        equalizer = null
        bassBoost = null
        virtualizer = null
        _state.value = EqUiState(
            available = false,
            enabled = masterEnabled(),
            bandFrequencies = _state.value.bandFrequencies,
            levels = currentLevels,
            preset = currentPreset,
            bassBoost = currentBass,
            virtualizer = currentVirtual
        )
    }

    fun isAvailable(): Boolean = equalizer != null

    fun setBandLevel(bandIndex: Int, level: Int) {
        val eq = equalizer ?: return
        val bands = eq.numberOfBands
        if (bandIndex !in 0 until bands) return
        try {
            currentLevels = currentLevels.toMutableList().apply { set(bandIndex, level) }
            currentPreset = "custom"
            applyLevelsToEffect()
            pushState()
        } catch (_: Exception) {
        }
    }

    fun applyPreset(name: String) {
        val eq = equalizer ?: return
        if (name !in presetProfiles) return
        val bands = eq.numberOfBands.toInt()
        val profile = presetProfiles[name] ?: return
        val scaled = IntArray(bands) { index ->
            val sourceIndex = if (bands > 1) index * 9 / (bands - 1) else 0
            (profile[sourceIndex] * 100).toInt()
        }
        currentLevels = scaled.toList()
        currentPreset = name
        applyLevelsToEffect()
        pushState()
    }

    fun setBassBoost(strength: Int) {
        currentBass = strength.coerceIn(0, 1000)
        try {
            bassBoost?.setStrength(currentBass.toShort())
            bassBoost?.enabled = currentBass > 0
        } catch (_: Exception) {
        }
        pushState()
    }

    fun setVirtualizer(strength: Int) {
        currentVirtual = strength.coerceIn(0, 1000)
        try {
            virtualizer?.setStrength(currentVirtual.toShort())
            virtualizer?.enabled = currentVirtual > 0
        } catch (_: Exception) {
        }
        pushState()
    }

    private fun applyLevelsToEffect() {
        val eq = equalizer ?: return
        val range = eq.bandLevelRange
        val lo = range[0].toFloat()
        val hi = range[1].toFloat()
        currentLevels.forEachIndexed { index, level ->
            val mapped = (lo + (hi - lo) * (level / 1000f)).toInt()
            try {
                eq.setBandLevel((index).toShort(), mapped.toShort())
            } catch (_: Exception) {
            }
        }
        try {
            eq.enabled = true
        } catch (_: Exception) {
        }
    }

    private fun pushState() {
        _state.value = EqUiState(
            available = equalizer != null,
            enabled = masterEnabled(),
            bandFrequencies = _state.value.bandFrequencies,
            levels = currentLevels,
            preset = currentPreset,
            bassBoost = currentBass,
            virtualizer = currentVirtual
        )
    }
}
