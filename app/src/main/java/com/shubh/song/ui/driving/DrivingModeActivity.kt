package com.shubh.song.ui.driving

import android.os.Bundle
import android.view.MotionEvent
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.load
import com.shubh.song.R
import com.shubh.song.databinding.ActivityDrivingModeBinding
import com.shubh.song.media.PlaybackManager
import com.shubh.song.util.Ui
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Hands-free "Car Mode": giant translucent targets, swipe-to-skip, keep-screen-on
 * and an always-touchable surface for driving.
 */
class DrivingModeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDrivingModeBinding

    private var downX = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDrivingModeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding.btnCarExit.setOnClickListener { finish() }
        binding.btnCarPlay.setOnClickListener { PlaybackManager.togglePlayPause() }
        binding.btnCarPrev.setOnClickListener { PlaybackManager.playPrev() }
        binding.btnCarNext.setOnClickListener { PlaybackManager.playNext() }

        // Horizontal fling = previous / next track.
        binding.drivingRoot.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = event.x - downX
                    if (kotlin.math.abs(dx) > 200) {
                        if (dx < 0) PlaybackManager.playNext() else PlaybackManager.playPrev()
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                PlaybackManager.uiState.collectLatest { state -> bind(state) }
            }
        }
    }

    private fun bind(state: PlaybackManager.PlayerUiState) {
        binding.tvCarTitle.text = state.currentSong?.name ?: getString(R.string.queue_empty)
        binding.tvCarArtist.text = state.currentSong?.let {
            listOf(it.artists, it.album).filter { s -> s.isNotBlank() }.joinToString(" • ")
        } ?: ""
        binding.tvCarPosition.text =
            "${Ui.formatDuration(state.positionMs)} / ${Ui.formatDuration(state.durationMs)}"

        val image = state.currentSong?.imageUrl
        if (!image.isNullOrBlank()) {
            binding.ivBg.load(image) {
                placeholder(R.drawable.bg_placeholder)
                error(R.drawable.bg_placeholder)
                crossfade(300)
            }
        } else {
            binding.ivBg.setImageResource(R.drawable.bg_placeholder)
        }

        binding.ivCarPlay.setImageResource(
            if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
    }
}