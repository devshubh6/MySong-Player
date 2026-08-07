package com.shubh.song.ui.player

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.view.LayoutInflater
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
import com.shubh.song.ui.sheets.LyricsSheet
import com.shubh.song.ui.sheets.QueueSheet
import com.shubh.song.ui.sheets.SleepSheet
import com.shubh.song.ui.sheets.TrackOptionsSheet
import com.shubh.song.util.Ui
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private val viewModel: PlayerViewModel by viewModels { AppViewModelFactory }

    private val pagerAdapter = PlayerPagerAdapter(
        onTap = { PlaybackManager.togglePlayPause() },
        onSpeedStart = {
            PlaybackManager.setSpeed(2f)
            binding.tvSpeedChip.visibility = View.VISIBLE
        },
        onSpeedEnd = {
            PlaybackManager.setSpeed(1f)
            binding.tvSpeedChip.visibility = View.GONE
        }
    )

    private var userSeeking = false
    private var userScrolling = false
    private var lastQueue: List<com.shubh.song.data.model.Song> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyWindowInsets()
        applyAccent()
        com.shubh.song.util.AmoledUtils.apply(this, binding.root)

        binding.vpSongs.orientation = ViewPager2.ORIENTATION_VERTICAL
        binding.vpSongs.adapter = pagerAdapter
        binding.vpSongs.setPageTransformer { page, position ->
            val abs = kotlin.math.abs(position)
            val clamped = abs.coerceIn(0f, 1f)
            page.translationY = position * page.height * 0.45f
            page.alpha = 1f - clamped
            page.scaleX = 1f - 0.08f * clamped
            page.scaleY = 1f - 0.08f * clamped
        }
        binding.vpSongs.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrollStateChanged(state: Int) {
                userScrolling = state != ViewPager2.SCROLL_STATE_IDLE
            }

            override fun onPageSelected(position: Int) {
                if (userScrolling) {
                    PlaybackManager.seekToIndex(position)
                }
            }
        })

        setupControls()

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

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
    }

    private fun bind(state: PlaybackManager.PlayerUiState) {
        val songs = state.queue
        if (songs != lastQueue || songs.size != lastQueue.size) {
            lastQueue = songs
            if (songs.isNotEmpty()) pagerAdapter.submit(songs)
        }
        if (songs.isNotEmpty() && !userScrolling && state.index in 0 until songs.size) {
            if (binding.vpSongs.currentItem != state.index) {
                binding.vpSongs.setCurrentItem(state.index, false)
            }
        }

        val current = state.currentSong
        binding.tvPlayingPosition.text =
            if (songs.isNotEmpty()) "${state.index + 1} / ${songs.size}" else ""

        binding.tvSongTitle.text = current?.name ?: ""
        binding.tvSongArtist.text =
            current?.let { fileOf(it) } ?: ""

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

        binding.btnPlay.setImageResource(
            if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
        binding.tvCurrent.text = Ui.formatDuration(state.positionMs)
        binding.tvDuration.text = Ui.formatDuration(state.durationMs)
        binding.btnAudioMode.text = when (state.audioMode) {
            PlaybackManager.AudioMode.NORMAL -> "1x"
            PlaybackManager.AudioMode.SLOWED_REVERB -> "Slowed + Reverb"
            PlaybackManager.AudioMode.NIGHTCORE -> "Nightcore"
        }
        bindPlayModes(state.shuffleEnabled, state.repeatMode)
        if (!userSeeking) {
            val progress = if (state.durationMs > 0) {
                (state.positionMs * 1000 / state.durationMs).toInt().coerceIn(0, 1000)
            } else 0
            if (binding.sbProgress.progress != progress) {
                binding.sbProgress.progress = progress
            }
        }
    }

    private fun fileOf(song: com.shubh.song.data.model.Song): String =
        listOf(song.artists, song.album).filter { it.isNotBlank() }.joinToString(" • ")

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
        binding.btnPlay.backgroundTintList = ColorStateList.valueOf(accent)
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
        binding.btnPlay.setOnClickListener { PlaybackManager.togglePlayPause() }
        binding.btnPrev.setOnClickListener { PlaybackManager.playPrev() }
        binding.btnNext.setOnClickListener { PlaybackManager.playNext() }

        binding.sbProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {}

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                userSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val duration = PlaybackManager.uiState.value.durationMs
                if (duration > 0 && seekBar != null) {
                    PlaybackManager.seekTo(duration * seekBar.progress / 1000)
                }
                userSeeking = false
            }
        })

        binding.btnLike.setOnClickListener { viewModel.toggleLike() }
        binding.btnLyrics.setOnClickListener {
            showSheet(LyricsSheet(), "lyrics")
        }
        binding.btnShuffle.setOnClickListener { PlaybackManager.toggleShuffle() }
        binding.btnRepeat.setOnClickListener { PlaybackManager.cycleRepeat() }
        binding.btnAudioMode.setOnClickListener {
            PlaybackManager.cycleAudioMode()
            binding.btnAudioMode.text = PlaybackManager.audioModeLabel()
        }
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
    }

    /** Crash-proof bottom sheet presentation: guards the FragmentManager. */
    private fun showSheet(sheet: androidx.fragment.app.DialogFragment, tag: String) {
        try {
            if (isFinishing || isDestroyed) return
            val manager = supportFragmentManager ?: return
            if (manager.isDestroyed || manager.findFragmentByTag(tag) != null) return
            sheet.show(manager, tag)
        } catch (e: Exception) {
            // Sheet presentation must never crash the player.
        }
    }

    companion object {
        fun open(context: Context) {
            context.startActivity(Intent(context, PlayerActivity::class.java))
        }
    }
}

private class PlayerPagerAdapter(
    private val onTap: () -> Unit,
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

        fun bind() {
            binding.pageRoot.setOnClickListener { onTap() }
            binding.pageRoot.setOnLongClickListener {
                pressed = true
                onSpeedStart()
                true
            }
            binding.pageRoot.setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (pressed) {
                            pressed = false
                            onSpeedEnd()
                        }
                    }
                }
                false
            }
        }
    }
}