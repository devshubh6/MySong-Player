package com.shubh.song.ui.player

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Player
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import coil.load
import com.shubh.song.MySongApp
import com.shubh.song.R
import com.shubh.song.databinding.ActivityPlayerBinding
import com.shubh.song.databinding.ItemPlayerPageBinding
import com.shubh.song.media.DownloadEngine
import com.shubh.song.media.PlaybackManager
import com.shubh.song.ui.common.AppViewModelFactory
import com.shubh.song.ui.detail.ArtistDetailFragment
import com.shubh.song.ui.sheets.LyricsSheet
import com.shubh.song.ui.sheets.QueueSheet
import com.shubh.song.ui.sheets.SleepSheet
import com.shubh.song.ui.sheets.SpatialSheet
import com.shubh.song.ui.sheets.TrackOptionsSheet
import com.shubh.song.ui.player.ReelsPageTransformer
import com.shubh.song.util.ThemeManager
import com.shubh.song.util.Ui
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private val viewModel: PlayerViewModel by viewModels { AppViewModelFactory }

    private val pagerAdapter = PlayerPagerAdapter(
        onTap = { onPlayerTap() },
        onSkip = { direction -> onSkip10(direction) },
        onSpeedStart = {
            PlaybackManager.setSpeed(2f)
            binding.tvSpeedChip.visibility = View.VISIBLE
        },
        onSpeedEnd = {
            PlaybackManager.setSpeed(1f)
            binding.tvSpeedChip.visibility = View.GONE
        }
    )

    private var userScrolling = false
    private var scrubbing = false
    private var pullDownHandled = false
    private var lastQueue: List<com.shubh.song.data.model.Song> = emptyList()

    /**
     * Tracks the karaoke/lyrics sheet lifecycle: dims the artwork to 60%
     * while the sheet is open (readability) and restores full brightness
     * the moment it closes.
     */
    private val lyricsScrimCallbacks =
        object : androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentViewCreated(
                fm: androidx.fragment.app.FragmentManager,
                f: androidx.fragment.app.Fragment,
                v: View,
                savedInstanceState: Bundle?
            ) {
                if (f is LyricsSheet) applyLyricsScrim(active = true)
            }

            override fun onFragmentViewDestroyed(
                fm: androidx.fragment.app.FragmentManager,
                f: androidx.fragment.app.Fragment
            ) {
                if (f is LyricsSheet) applyLyricsScrim(active = false)
            }
        }

    /** Pager double-tap: seek back/forward 10s and flash the skip chip. */
    private fun onSkip10(direction: Int) {
        try {
            val current = runCatching { PlaybackManager.player.currentPosition }
                .getOrDefault(PlaybackManager.uiState.value.positionMs)
            PlaybackManager.seekTo((current + direction * 10_000L).coerceAtLeast(0L))
            flashSkipIndicator(direction > 0)
        } catch (t: Throwable) {
            // Skip feedback is cosmetic; never crash playback.
        }
    }

    /** YouTube-style skip chip: slides in from the tapped side, then fades. */
    private fun flashSkipIndicator(forward: Boolean) {
        val v = if (forward) binding.tvSkipForward else binding.tvSkipBack
        try {
            v.visibility = View.VISIBLE
            v.animate().cancel()
            v.alpha = 0f
            v.translationX = if (forward) 24f else -24f
            v.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(180)
                .withEndAction {
                    try {
                        v.animate()
                            .alpha(0f)
                            .setDuration(500)
                            .withEndAction { v.visibility = View.GONE }
                            .start()
                    } catch (t: Throwable) {
                    }
                }
                .start()
        } catch (t: Throwable) {
            v.visibility = View.GONE
        }
    }

    /** Pager single-tap: toggles play/pause and flashes the centered icon. */
    private fun onPlayerTap() {
        try {
            PlaybackManager.togglePlayPause()
            val playing = runCatching { PlaybackManager.player.isPlaying }.getOrDefault(false)
            flashPlayPause(playing)
        } catch (t: Throwable) {
            // Tap feedback is cosmetic; never crash.
        }
    }

    /** Glowing play/pause glyph: pops in, lingers, then fades away. */
    private fun flashPlayPause(playing: Boolean) {
        val v = binding.ivPlayPauseIndicator
        try {
            v.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
            v.visibility = View.VISIBLE
            v.animate().cancel()
            v.alpha = 0f
            v.scaleX = 0.4f
            v.scaleY = 0.4f
            v.animate()
                .alpha(0.92f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(160)
                .withEndAction {
                    try {
                        v.animate()
                            .alpha(0f)
                            .scaleX(0.6f)
                            .scaleY(0.6f)
                            .setDuration(420)
                            .withEndAction { v.visibility = View.GONE }
                            .start()
                    } catch (t: Throwable) {
                    }
                }
                .start()
        } catch (t: Throwable) {
            v.visibility = View.GONE
        }
    }

    /** Animates the artwork dim layer (0% default, 60% while lyrics show). */
    private fun applyLyricsScrim(active: Boolean) {
        try {
            binding.artTint.animate().cancel()
            binding.artTint.animate()
                .alpha(if (active) 0.6f else 0f)
                .setDuration(320)
                .start()
        } catch (t: Throwable) {
            // Visual polish only — never crash for a scrim.
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyWindowInsets()
        applyAccent()
        ThemeManager.applyStatusBarIcons(window)

        binding.vpSongs.orientation = ViewPager2.ORIENTATION_VERTICAL
        binding.vpSongs.adapter = pagerAdapter
        binding.vpSongs.setPageTransformer(ReelsPageTransformer())
        binding.vpSongs.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrollStateChanged(state: Int) {
                userScrolling = state != ViewPager2.SCROLL_STATE_IDLE
            }

            override fun onPageSelected(position: Int) {
                try {
                    // Strict boundary check: never seek outside the current queue.
                    if (userScrolling && position in 0 until pagerAdapter.itemCount) {
                        PlaybackManager.seekToIndex(position)
                    }
                } catch (t: Throwable) {
                    // Page navigation must never crash the player.
                }
            }
        })
        attachOverscrollFallback()

        setupControls()

        // Lyrics view → 60% scrim over the artwork; removed → artwork back to full brightness.
        supportFragmentManager.registerFragmentLifecycleCallbacks(lyricsScrimCallbacks, true)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.playback.collectLatest { bind(it) } }
                launch { viewModel.isLiked.collectLatest { bindLike(it) } }
                launch { viewModel.isDownloaded.collectLatest { bindDownload(it) } }
                launch { viewModel.downloadProgress.collectLatest { bindDownload(viewModel.isDownloaded.value) } }
                launch { viewModel.sleepRemainingMs.collectLatest { bindSleep(it) } }
                launch { MySongApp.instance.accent.collectLatest { applyAccent() } }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Self-heal system-bar icon contrast after any theme shift.
        ThemeManager.applyStatusBarIcons(window)
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
    }

    /**
     * Reels gesture: pulling DOWN while already on the first track must never
     * crash — it falls back to restarting the current song from 0:00 instead
     * of indexing into an out-of-range position.
     */
    private fun playPreviousSong() {
        try {
            val index = viewModel.playback.value.index
            if (index > 0) {
                val previousIndex = index - 1
                PlaybackManager.seekToIndex(previousIndex)
                if (previousIndex in 0 until pagerAdapter.itemCount) {
                    binding.vpSongs.setCurrentItem(previousIndex, true)
                }
            } else {
                PlaybackManager.seekTo(0)
            }
        } catch (t: Throwable) {
            // Navigation is best-effort; never crash playback.
        }
    }

    /**
     * Watches the pager's internal RecyclerView for a clamped down-pull at
     * page 0 (ViewPager2 never fires onPageSelected for it) and converts it
     * into the safe previous-song handler (seek-to-start at index 0).
     */
    private fun attachOverscrollFallback() {
        try {
            val recycler = binding.vpSongs.getChildAt(0) as? RecyclerView ?: return
            recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                    if (dy < 0 && binding.vpSongs.currentItem == 0 && !pullDownHandled) {
                        pullDownHandled = true
                        playPreviousSong()
                    } else if (dy >= 0 && pullDownHandled) {
                        pullDownHandled = false
                    }
                }
            })
        } catch (t: Throwable) {
            // Overscroll mirror is polish-only.
        }
    }

    private fun bind(state: PlaybackManager.PlayerUiState) {
        val songs = state.queue
        if (songs != lastQueue || songs.size != lastQueue.size) {
            lastQueue = songs
            if (songs.isNotEmpty()) pagerAdapter.submit(songs)
        }
        if (songs.isNotEmpty() && !userScrolling && state.index in 0 until songs.size) {
            try {
                if (binding.vpSongs.currentItem != state.index) {
                    binding.vpSongs.setCurrentItem(state.index, false)
                }
            } catch (t: Throwable) {
                // Pager sync is best-effort; never crash playback.
            }
        }

        val current = state.currentSong
        binding.tvPlayingPosition.text =
            if (songs.isNotEmpty()) "${state.index + 1} / ${songs.size}" else ""

        binding.tvSongTitle.text = Ui.html(current?.name ?: "")
        binding.tvSongArtist.text = Ui.html(
            current?.let { fileOf(it) } ?: ""
        )

        val image = current?.imageUrl
        if (!image.isNullOrBlank()) {
            binding.ivBackground.load(image) {
                placeholder(R.drawable.bg_placeholder)
                error(R.drawable.bg_placeholder)
                crossfade(350)
            }
        } else {
            binding.ivBackground.setImageResource(R.drawable.bg_placeholder)
        }

        binding.tvOfflineBadge.visibility =
            if (state.isOffline) View.VISIBLE else View.GONE

        if (!image.isNullOrBlank()) {
            binding.ivArtistAvatar.load(image) {
                placeholder(R.drawable.bg_placeholder)
                error(R.drawable.bg_placeholder)
                crossfade(250)
            }
        }
        if (!scrubbing) {
            binding.tvCurrentTime.text = Ui.formatDuration(state.positionMs)
            binding.sbProgress.progress =
                if (state.durationMs > 0) {
                    (state.positionMs * 1000 / state.durationMs).toInt()
                        .coerceIn(0, 1000)
                } else 0
            binding.sbProgress.secondaryProgress =
                if (state.durationMs > 0) {
                    (state.bufferedPositionMs * 1000 / state.durationMs).toInt()
                        .coerceIn(0, 1000)
                } else 0
        }
        binding.pbBuffering.visibility =
            if (state.isBuffering) View.VISIBLE else View.GONE
        binding.loadingOverlay.visibility =
            if (state.isBuffering) View.VISIBLE else View.GONE
        binding.tvTotalTime.text = Ui.formatDuration(state.durationMs)
        bindPlayModes(state.shuffleEnabled, state.repeatMode)
    }

    private fun fileOf(song: com.shubh.song.data.model.Song): String =
        listOf(song.artists, song.album).filter { it.isNotBlank() }.joinToString("  •  ")

    private fun bindSleep(remainingMs: Long?) {
        if (remainingMs == null || remainingMs <= 0) {
            binding.tvSleepBadge.visibility = View.GONE
            return
        }
        val minutes = (remainingMs / 60000).toString()
        binding.tvSleepBadge.visibility = View.VISIBLE
        binding.tvSleepBadge.text = minutes
    }

    private fun bindPlayModes(shuffleEnabled: Boolean, repeatMode: Int) {
        val accent = Ui.accentColor(this)
        val white = android.graphics.Color.WHITE
        val dim = android.graphics.Color.argb(179, 255, 255, 255)

        fillSegment(binding.btnShuffle, shuffleEnabled, accent)
        binding.ivShuffle.setColorFilter(if (shuffleEnabled) white else dim)

        val repeatOne = repeatMode == Player.REPEAT_MODE_ONE
        fillSegment(binding.btnRepeat, repeatOne, accent)
        binding.tvRepeatBadge.visibility = if (repeatOne) View.VISIBLE else View.GONE
        binding.tvRepeatBadge.backgroundTintList = ColorStateList.valueOf(accent)
        binding.ivRepeat.setColorFilter(if (repeatMode == Player.REPEAT_MODE_OFF) dim else accent)
    }

    private fun fillSegment(view: View, filled: Boolean, color: Int) {
        (view.background as? android.graphics.drawable.GradientDrawable)?.setColor(
            if (filled) color else android.graphics.Color.TRANSPARENT
        )
    }

    private fun bindLike(liked: Boolean) {
        binding.ivLike.setImageResource(
            if (liked) R.drawable.ic_favorite else R.drawable.ic_favorite_border
        )
        binding.ivLike.colorFilter =
            if (liked) PorterDuffColorFilter(Ui.accentColor(this), android.graphics.PorterDuff.Mode.SRC_IN)
            else null
        binding.ivLike.setImageTintList(
            if (liked) null
            else ResourcesCompat.getColorStateList(resources, android.R.color.white, theme)
        )
    }

    private fun bindDownload(downloaded: Boolean) {
        val songId = viewModel.currentSong()?.id
        val progress = songId?.let { DownloadEngine.progressOf(it) }
        when {
            downloaded -> {
                binding.ivDownload.visibility = View.VISIBLE
                binding.pbDownload.visibility = View.GONE
                binding.ivDownload.setImageResource(R.drawable.ic_download_done)
                binding.ivDownload.imageTintList =
                    ResourcesCompat.getColorStateList(resources, R.color.accent, theme)
            }
            progress != null && progress in 0..99 -> {
                binding.ivDownload.visibility = View.INVISIBLE
                binding.pbDownload.visibility = View.VISIBLE
                binding.pbDownload.isIndeterminate = progress <= 0
                binding.pbDownload.progress = if (progress <= 0) 0 else progress
            }
            else -> {
                binding.ivDownload.visibility = View.VISIBLE
                binding.pbDownload.visibility = View.GONE
                binding.ivDownload.setImageResource(R.drawable.ic_download)
                binding.ivDownload.setColorFilter(Color.WHITE)
            }
        }
    }

    private fun applyAccent() {
        val accent = Ui.accentColor(this)
        binding.sbProgress.progressTintList = ColorStateList.valueOf(accent)
        binding.sbProgress.thumbTintList = ColorStateList.valueOf(accent)
        binding.tvSleepBadge.backgroundTintList = ColorStateList.valueOf(accent)
        binding.tvRepeatBadge.backgroundTintList = ColorStateList.valueOf(accent)
        bindLike(viewModel.isLiked.value)
        bindPlayModes(
            PlaybackManager.uiState.value.shuffleEnabled,
            PlaybackManager.uiState.value.repeatMode
        )
    }

    private fun setupControls() {
        binding.btnArtist.setOnClickListener { openArtistProfile() }

        binding.btnLike.setOnClickListener { viewModel.toggleLike() }
        binding.btnLyrics.setOnClickListener {
            showSheet(LyricsSheet(), "lyrics")
        }
        binding.btnShuffle.setOnClickListener { PlaybackManager.toggleShuffle() }
        binding.btnRepeat.setOnClickListener { PlaybackManager.cycleRepeat() }
        binding.btnDownload.setOnClickListener {
            if (viewModel.isDownloaded.value) {
                Ui.toast(getString(R.string.download_complete))
            } else {
                viewModel.downloadCurrent()
            }
        }
        binding.btnQueue.setOnClickListener { showSheet(QueueSheet(), "queue") }
        binding.btnMore.setOnClickListener { showSheet(TrackOptionsSheet(), "options") }
        binding.btnSleep.setOnClickListener { showSheet(SleepSheet(), "sleep") }
        binding.btnSpatial.setOnClickListener { showSheet(SpatialSheet(), "spatial") }

        setupTimelineScrubbing()
    }

    /**
     * Reels-style interactive timeline: ultra-thin (3dp) line pinned at the
     * very bottom edge that thickens to 6dp while the finger is down for an
     * easy touch target. Scrubbing seeks CONTINUOUSLY under the finger with
     * playback left running (`playWhenReady` untouched), so the audio jumps
     * instantly instead of pausing, and a live time preview tracks the thumb.
     */
    private fun setupTimelineScrubbing() {
        binding.sbProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser || seekBar == null) return
                val duration = viewModel.playback.value.durationMs
                if (duration > 0) {
                    val target = duration * progress.coerceIn(0, 1000) / 1000L
                    binding.tvCurrentTime.text = Ui.formatDuration(target)
                    // Live seek on every move event — playback never pauses,
                    // the audio simply jumps to the finger position.
                    PlaybackManager.seekTo(target)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                scrubbing = true
                animateTimelineHeight(6f)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                try {
                    val duration = viewModel.playback.value.durationMs
                    val progress = seekBar?.progress ?: 0
                    if (duration > 0 && progress >= 0) {
                        PlaybackManager.seekTo(duration * progress.coerceIn(0, 1000) / 1000L)
                    }
                } catch (t: Throwable) {
                    // Scrubbing is best-effort; never crash playback.
                }
                scrubbing = false
                animateTimelineHeight(3f)
            }
        })
    }

    /** Grows/shrinks the thin timeline so it reads as a live scrub handle. */
    private fun animateTimelineHeight(targetDp: Float) {
        try {
            val lp = binding.sbProgress.layoutParams
            lp.height = (targetDp * resources.displayMetrics.density).toInt()
            binding.sbProgress.layoutParams = lp
        } catch (t: Throwable) {
            // Height polish only.
        }
    }

    /**
     * Reels-style artist profile: tapping the avatar/title row opens the
     * artist's full catalog (songs, albums, more) as a full-screen overlay
     * hosted right inside the player.
     */
    private fun openArtistProfile() {
        try {
            val song = viewModel.currentSong() ?: return
            val name = song.artists.trim()
            if (name.isEmpty()) return
            val manager = supportFragmentManager
            if (manager.isDestroyed) return
            if (manager.findFragmentByTag(ArtistDetailFragment.TAG) != null) return
            manager.beginTransaction()
                .setCustomAnimations(
                    R.anim.slide_in_right,
                    R.anim.slide_out_left,
                    R.anim.slide_in_left,
                    R.anim.slide_out_right
                )
                .add(
                    R.id.overlay_host,
                    ArtistDetailFragment.newInstance(null, name, song.imageUrl.orEmpty()),
                    ArtistDetailFragment.TAG
                )
                .commitAllowingStateLoss()
        } catch (t: Throwable) {
            // Profile navigation must never crash playback.
        }
    }

    /** Crash-proof bottom sheet presentation: guards the FragmentManager. */
    private fun showSheet(sheet: androidx.fragment.app.DialogFragment, tag: String) {
        try {
            if (isFinishing || isDestroyed) return
            val manager = supportFragmentManager
            if (manager.isDestroyed || manager.findFragmentByTag(tag) != null) return
            sheet.show(manager, tag)
        } catch (e: Exception) {
            // Sheet presentation must never crash the player.
        }
    }

    companion object {
        /**
         * Safe launcher: guards against finishing/destroyed activities and
         * swallows any launch fault so navigation never crashes the app.
         */
        fun open(context: Context) {
            try {
                if (context is android.app.Activity &&
                    (context.isFinishing || context.isDestroyed)
                ) return
                context.startActivity(Intent(context, PlayerActivity::class.java))
            } catch (t: Throwable) {
                // Clicking the mini player mid-transition must never crash.
            }
        }
    }
}

private class PlayerPagerAdapter(
    private val onTap: () -> Unit,
    private val onSkip: (direction: Int) -> Unit,
    private val onSpeedStart: () -> Unit,
    private val onSpeedEnd: () -> Unit
) : RecyclerView.Adapter<PlayerPagerAdapter.Page>() {

    private val items = mutableListOf<com.shubh.song.data.model.Song>()
    private var pressed = false

    fun submit(songs: List<com.shubh.song.data.model.Song>) {
        items.clear()
        items.addAll(songs)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Page =
        Page(ItemPlayerPageBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Page, position: Int) = holder.bind()

    inner class Page(val binding: ItemPlayerPageBinding) : RecyclerView.ViewHolder(binding.root) {

        private val detector = GestureDetector(
            binding.root.context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true

                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    onTap()
                    return false
                }

                /** Double-tap: left half rewinds 10s, right half skips 10s. */
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    val half = binding.root.width / 2f
                    onSkip(if (e.x < half) -1 else 1)
                    return true
                }

                override fun onLongPress(e: MotionEvent) {
                    pressed = true
                    onSpeedStart()
                }
            }
        )

        fun bind() {
            binding.pageRoot.setOnTouchListener { _, event ->
                detector.onTouchEvent(event)
                when (event.actionMasked) {
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (pressed) {
                            pressed = false
                            onSpeedEnd()
                        }
                    }
                }
                // Never consume: the vertical ViewPager2 must keep swiping.
                false
            }
        }
    }
}