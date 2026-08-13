package com.shubh.song.media

import android.content.Context
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

/**
 * Custom [DefaultRenderersFactory] that injects the [BinauralPannerAudioProcessor]
 * into the audio pipeline so the 3D/8D/16D/32D spatial effect is applied.
 *
 * The processor is ALWAYS attached so the user can toggle any spatial mode at
 * runtime from the player. When the current mode is OFF the processor is a
 * pure, sample-accurate pass-through (it does NOT touch the audio), so normal
 * playback uses the same output as the stock sink. This keeps the default
 * behaviour safe while making the effect instantly available.
 */
class BinauralRenderersFactory(context: Context) : DefaultRenderersFactory(context) {

    override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
        DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .setAudioProcessors(arrayOf<AudioProcessor>(BinauralPannerAudioProcessor()))
            .build()
}