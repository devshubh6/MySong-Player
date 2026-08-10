package com.shubh.song.ui.player

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import coil.load
import com.shubh.song.R
import com.shubh.song.databinding.ItemMiniPlayerBinding
import com.shubh.song.media.PlaybackManager
import com.shubh.song.util.Ui
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Persistent bottom mini player: artwork + title, play/pause, next, and
 * horizontal-swipe to skip tracks. Tap to expand the full vertical player.
 */
class MiniPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding: ItemMiniPlayerBinding =
        ItemMiniPlayerBinding.inflate(LayoutInflater.from(context), this, true)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                val dx = e2.x - (e1?.x ?: e2.x)
                if (kotlin.math.abs(dx) > 120 && kotlin.math.abs(velocityX) > 400) {
                    if (dx < 0) PlaybackManager.playNext() else PlaybackManager.playPrev()
                    return true
                }
                return false
            }
        }
    )

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        scope.launch {
            PlaybackManager.uiState.collectLatest { state ->
                binding.miniTitle.text = Ui.html(state.currentSong?.name ?: "")
                binding.miniArtist.text = Ui.html(state.currentSong?.artists ?: "")
                binding.miniPlay.setImageResource(
                    if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                )
                val image = state.currentSong?.imageUrl
                if (!image.isNullOrBlank()) {
                    binding.miniArt.load(image) {
                        placeholder(R.drawable.bg_placeholder)
                        error(R.drawable.bg_placeholder)
                        crossfade(250)
                    }
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scope.cancel()
    }

    init {
        binding.miniPlay.setOnClickListener { PlaybackManager.togglePlayPause() }
        binding.miniNext.setOnClickListener { PlaybackManager.playNext() }
        binding.miniPlayerRoot.setOnClickListener { runPlayer(context) }
        binding.miniPlay.colorFilter =
            android.graphics.PorterDuffColorFilter(
                Ui.accentColor(context),
                android.graphics.PorterDuff.Mode.SRC_IN
            )
        setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }
        isClickable = true
    }

    /**
     * Crash-proof expansion: never lets a stale/finishing activity context,
     * a mid-transition FragmentManager/ActivityManager rejection, or any other
     * runtime fault escape. Falls back to a NEW_TASK launch before giving up
     * silently.
     */
    private fun runPlayer(context: Context) {
        try {
            if (context is android.app.Activity && (context.isFinishing || context.isDestroyed)) return
            val manager = (context as? androidx.fragment.app.FragmentActivity)
                ?.supportFragmentManager
            if (manager != null && manager.isStateSaved) return
            PlayerActivity.open(context)
        } catch (t: Throwable) {
            try {
                val fallback = Intent(context, PlayerActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fallback)
            } catch (t2: Throwable) {
                // Both paths are blocked; the transition must never crash.
            }
        }
    }
}
