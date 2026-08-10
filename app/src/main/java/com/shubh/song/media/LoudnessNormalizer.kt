package com.shubh.song.media

import android.media.audiofx.DynamicsProcessing
import android.os.Build
import com.shubh.song.MySongApp
import java.lang.reflect.Constructor

/**
 * Smart volume normalization — a DynamicsProcessing chain (3-band multi-band
 * compressor + brick-wall limiter at ≈0 dBFS) that keeps every track at a
 * consistent perceived loudness, especially when queueing songs mixed to
 * radically different levels.
 *
 * Note: the Android SDK stub hides the `Config` constructors, so the full
 * config is materialized via reflection against the public 12-argument
 * `DynamicsProcessing.Config` constructor that exists at runtime (API 29+).
 * Every reflective/effect step is guarded: exotic vendors simply fall back
 * to an untouched signal.
 *
 * The effect binds to the ExoPlayer audio session exactly like the
 * [EqualizerController]; the session id changes per track, so [attach] is
 * re-invoked on every `STATE_READY`.
 */
object LoudnessNormalizer {

    /** 3-band multi-band compressor profile (gentle equal-loudness). */
    private const val BAND_COUNT = 3

    private var effect: DynamicsProcessing? = null
    private var session = 0

    private fun enabled(): Boolean =
        MySongApp.instance.settingsRepository.prefs.smartLoudness

    /** Attaches (or re-attaches) the normalizer to [audioSessionId]. */
    fun attach(audioSessionId: Int) {
        session = audioSessionId
        if (Build.VERSION.SDK_INT < 28 || !enabled()) {
            release()
            return
        }
        release()
        try {
            val config = buildConfig() ?: return
            val dp = DynamicsProcessing(0, audioSessionId, config)
            // tune the freshly created stages in-place
            for (bandIndex in 0 until BAND_COUNT) {
                dp.setMbcBandByChannelIndex(0, bandIndex, mbcBand(bandIndex))
            }
            dp.setLimiterAllChannelsTo(limiter())
            dp.setInputGainAllChannelsTo(0f)
            dp.enabled = true
            effect = dp
        } catch (t: Throwable) {
            release() // unsupported effect / config mismatch / attach failure
        }
    }

    /** Toggles the persisted switch and (re)attaches the chain immediately. */
    fun setEnabled(on: Boolean) {
        MySongApp.instance.settingsRepository.setSmartLoudness(on)
        if (on) attach(session) else release()
    }

    /** Detaches and releases the native effect (idempotent). */
    fun release() {
        session = 0
        try {
            effect?.setEnabled(false)
            effect?.release()
        } catch (_: Throwable) {
        }
        effect = null
    }

    // ---- profile ----------

    private fun limiter() =
        DynamicsProcessing.Limiter(
            true,           // inUse
            true,           // enabled
            0,              // link group (all channels linked)
            0.004f,         // attack seconds
            0.120f,         // release seconds
            0.033f,         // static ratio → near-brickwall slope
            -0.5f,          // ceiling dBFS
            0f              // post gain dB
        )

    private fun mbcBand(index: Int): DynamicsProcessing.MbcBand =
        when (index) {
            0 -> DynamicsProcessing.MbcBand(
                true, 200f, 0.006f, 0.120f, 0.50f, -4f, 2f, -14f, 1.6f, 5f, 0.5f
            )
            1 -> DynamicsProcessing.MbcBand(
                true, 1000f, 0.005f, 0.120f, 0.55f, -5f, 2f, -16f, 1.5f, 3f, 0f
            )
            else -> DynamicsProcessing.MbcBand(
                true, 6000f, 0.005f, 0.100f, 0.50f, -6f, 3f, -16f, 1.5f, 3f, 0f
            )
        }

    // ---- config construction (reflection-based, fully guarded) ----

    /**
     * Mirrors the public API-29 constructor:
     * Config(channelCount, skipFrames, variant, inputGain, postGain,
     *        preEqInUse, preEqBandCount, mbcInUse, mbcBandCount,
     *        postEqInUse, postEqBandCount, limiterInUse)
     */
    private fun buildConfig(): DynamicsProcessing.Config? {
        return try {
            val klass = DynamicsProcessing.Config::class.java
            val ctor = declaredConfigConstructor(klass) ?: return null
            ctor.newInstance(
                2,      // channelCount (stereo engine — matches modern devices)
                false,  // skipFrames
                "Mysong",
                0f,     // inputGain mB
                0f,     // postGain mB
                0,      // preEqInUse
                0,      // preEqBandCount
                1,      // mbcInUse
                BAND_COUNT,
                0,      // postEqInUse
                0,      // postEqBandCount
                true    // limiterInUse
            ) as DynamicsProcessing.Config
        } catch (t: Throwable) {
            null
        }
    }

    /** Prefers the 12-arg public ctor, then the 1-arg snapshot ctor. */
    private fun declaredConfigConstructor(klass: Class<*>): Constructor<*>? {
        val candidates = klass.declaredConstructors
            .filter { !it.isSynthetic }
            .sortedByDescending { it.parameterCount }
        val preferred = candidates.firstOrNull { it.parameterCount == 12 }
            ?: candidates.firstOrNull { it.parameterCount == 1 }
            ?: return null
        if (!preferred.isAccessible) preferred.isAccessible = true
        return preferred
    }
}