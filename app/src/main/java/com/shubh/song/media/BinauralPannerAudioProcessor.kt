package com.shubh.song.media

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Spatial audio selector exposed to the UI. The processor below polls the
 * currently selected mode on every audio frame, so switching 3D / 8D / 16D /
 * 32D takes effect instantly without re-creating the ExoPlayer audio sink.
 *
 *  - OFF:   pure pass-through (no DSP).
 *  - 3D:    wide, gentle spatial "air" (slow breathing around center).
 *  - 8D+ :  rotation that glides smoothly AND reacts to the music's bass
 *           beat — each bass hit steps the sound around the head, slower at
 *           8D and livelier at 16D / 32D.
 */
object BinauralSpatial {
    enum class Mode(val baseHz: Double) {
        OFF(0.0),
        THREE_D(0.03),
        EIGHT_D(0.04),
        SIXTEEN_D(0.05),
        THIRTY_TWO_D(0.06);

        fun label(): String = when (this) {
            OFF -> "Off"
            THREE_D -> "3D Surround"
            EIGHT_D -> "8D Rotating"
            SIXTEEN_D -> "16D Spatial"
            THIRTY_TWO_D -> "32D Binaural"
        }
    }

    @Volatile
    var mode: Mode = Mode.OFF
}

/**
 * Custom ExoPlayer [AudioProcessor] that generates 3D→32D surround / rotation
 * by smoothly rotating the stereo image around the head.
 *
 * Two things make it feel good:
 *
 * 1. **Constant-velocity glide.** Gains are cos²/sin² of half the phase so the
 *    image moves left → centre → right → centre at uniform speed with constant
 *    power (cos²θ + sin²θ = 1). No mid-acceleration, no stall at the edges.
 *
 * 2. **Beat-reactive stepping.** A lightweight envelope detector (attack fast,
 *    decay slow) compares the current bass energy to a slow running average.
 *    When a bass hit lands (beat), the rotation jumps forward by a small step
 *    and a depth pulse widens the image for a split second. Between beats the
 *    phase still advances slowly, so the effect breathes with the track — it
 *    never spins maniacally (the old 8D→32D "too fast" complaint) and never
 *    sits dead during a quiet part.
 *
 * The sample counter and phase are monotonic — reset only on a full processor
 * reset, never on flush — so seeks / buffering never yank the rotation back.
 */
class BinauralPannerAudioProcessor : BaseAudioProcessor() {

    private var sampleRate = 44100
    private var channelCount = 0
    private var encoding = C.ENCODING_PCM_16BIT
    private var bytesPerFrame = 4

    /** Never reset on flush so the rotation stays continuous. */
    private var frameIndex = 0L

    /** Rotation phase in [0, 1). */
    private var phase = 0.0

    /** Bass envelope state + beat book-keeping. */
    private var fastEnv = 0.0
    private var slowEnv = 0.0
    private var beatPulse = 0.0
    private var lastBeatIndex = Long.MIN_VALUE / 2

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        // Only stereo linear PCM is supported for spatial panning.
        if (inputAudioFormat.channelCount != 2) return AudioProcessor.AudioFormat.NOT_SET
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        this.sampleRate = inputAudioFormat.sampleRate
        this.channelCount = inputAudioFormat.channelCount
        this.encoding = inputAudioFormat.encoding
        this.bytesPerFrame = inputAudioFormat.bytesPerFrame
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return

        val remaining = inputBuffer.remaining()
        val mode = BinauralSpatial.mode

        // False = pure pass-through (no DSP, mono, or a mode we can't process).
        val passthrough = mode == BinauralSpatial.Mode.OFF || channelCount != 2

        // Allocate a fresh output buffer. NEVER copy input into itself — that
        // throws "The source buffer is this buffer". replaceOutputBuffer always
        // returns a distinct buffer owned by the processor.
        val output = replaceOutputBuffer(remaining)

        if (passthrough) {
            // Byte-level copy into the fresh output buffer.
            while (inputBuffer.hasRemaining()) {
                output.put(inputBuffer.get())
            }
        } else {
            val frameBytes = bytesPerFrame
            while (inputBuffer.remaining() >= frameBytes) {
                val index = frameIndex++
                processFrame(inputBuffer, output, index, mode)
            }
        }

        output.flip()
    }

    private fun processFrame(
        input: ByteBuffer,
        output: ByteBuffer,
        index: Long,
        mode: BinauralSpatial.Mode
    ) {
        val (l, r) = if (encoding == C.ENCODING_PCM_FLOAT) {
            val lf = input.float.toDouble()
            val rf = input.float.toDouble()
            Pair(lf, rf)
        } else {
            val ls = input.short.toDouble()
            val rs = input.short.toDouble()
            Pair(ls, rs)
        }

        val energy = (abs(l) + abs(r)) * 0.5
        val pulse = beatPulse

        // Advance phase: slow constant glide + a step when a bass beat lands.
        phase = (phase + mode.baseHz / sampleRate + beatStepFor(mode, index)) % 1.0

        val (gL, gR) = channelGains(mode, phase, pulse)

        if (encoding == C.ENCODING_PCM_FLOAT) {
            output.putFloat((l * gL).toFloat())
            output.putFloat((r * gR).toFloat())
        } else {
            output.putShort(scaled(l, gL))
            output.putShort(scaled(r, gR))
        }

        // Update envelopes AFTER using them (energy = current frame only).
        updateEnvelope(energy, index)
    }

    /** Detects a bass beat and returns the phase step (0 when no beat). */
    private fun beatStepFor(mode: BinauralSpatial.Mode, index: Long): Double {
        val beat = fastEnv > slowEnv * 1.6 &&
            fastEnv > 0.02 &&
            index - lastBeatIndex > (sampleRate * 0.18)
        if (!beat) {
            beatPulse *= 0.995
            return 0.0
        }
        lastBeatIndex = index
        beatPulse = 1.0
        return when (mode) {
            BinauralSpatial.Mode.THREE_D -> 0.0
            BinauralSpatial.Mode.EIGHT_D -> 0.06
            BinauralSpatial.Mode.SIXTEEN_D -> 0.1
            BinauralSpatial.Mode.THIRTY_TWO_D -> 0.15
            else -> 0.0
        }
    }

    /** Two-stage envelope: fast attack/decay tracks hits, slow average normalises. */
    private fun updateEnvelope(energy: Double, index: Long) {
        // Skip a few frames on flush boundaries so a gap isn't a "beat".
        if (index <= 4) return
        val attack = if (energy > fastEnv) 0.5 else 0.06
        fastEnv += (energy - fastEnv) * attack
        slowEnv += (energy - slowEnv) * 0.006
    }

    /**
     * Gains for the current phase. Phase is normalised to [0, 1), with the two
     * ears offset slightly (interaural difference) so motion reads as 3D.
     *
     * `depth` scales travel from centre (3D stays airy/centred). The beat
     * [pulse] (0..1) briefly widens the depth for a punchy "bass feel".
     */
    private fun channelGains(mode: BinauralSpatial.Mode, phase: Double, pulse: Double): Pair<Double, Double> {
        val (phaseOffset, baseDepth) = when (mode) {
            BinauralSpatial.Mode.THREE_D -> 0.5 to 0.35
            BinauralSpatial.Mode.EIGHT_D -> 0.6 to 1.0
            BinauralSpatial.Mode.SIXTEEN_D -> 0.9 to 1.0
            BinauralSpatial.Mode.THIRTY_TWO_D -> 1.2 to 1.0
            else -> return Pair(1.0, 1.0)
        }

        val depth = (baseDepth + pulse * 0.25).coerceIn(0.0, 1.0)

        val theta = 2.0 * PI * phase
        val halfL = theta / 2.0
        val halfR = (theta + 2.0 * PI * phaseOffset) / 2.0

        val cl = cos(halfL)
        val sr = sin(halfR)

        // depth=1 → full L→R→L; depth<1 blends toward a centred image.
        val gL = (depth * cl * cl + (1.0 - depth) * 0.5).coerceIn(0.0, 1.0)
        val gR = (depth * sr * sr + (1.0 - depth) * 0.5).coerceIn(0.0, 1.0)
        return Pair(gL, gR)
    }

    private fun scaled(sample: Double, gain: Double): Short =
        (sample * gain).roundToInt().coerceIn(-32768, 32767).toShort()

    override fun onFlush() {
        // Intentionally do NOT reset frameIndex/phase: the rotation must stay
        // continuous across buffer flushes, seeks and track transitions.
    }

    override fun onReset() {
        frameIndex = 0L
        phase = 0.0
        fastEnv = 0.0
        slowEnv = 0.0
        beatPulse = 0.0
        lastBeatIndex = Long.MIN_VALUE / 2
    }
}