package com.shubh.song.ui.sheets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.shubh.song.R
import com.shubh.song.data.model.Lyrics
import com.shubh.song.data.model.Song
import com.shubh.song.data.repository.LyricsRepository
import com.shubh.song.databinding.SheetLyricsBinding
import com.shubh.song.media.PlaybackManager
import com.shubh.song.ui.karaoke.LyricsAdapter
import com.shubh.song.util.LiquidGlass
import com.shubh.song.util.Ui
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Synchronized "karaoke" lyrics sheet.
 *
 * Lyrics come from the multi-source [LyricsRepository] — JioSaavn's lyrics
 * endpoint first, then LRC Lib for real-time synced `.lrc` timestamps that
 * drive the auto-centered glowing line, and finally the classic unavailable
 * state. Always offline-safe.
 */
class LyricsSheet : BottomSheetDialogFragment() {

    private lateinit var binding: SheetLyricsBinding
    private var adapter: LyricsAdapter? = null
    private var loadingJob: Job? = null
    private var lastSongId: String? = null
    private var lyrics: Lyrics? = null
    private var karaokeMode = false
    private var lastActive = -1
    override fun onStart() {
        super.onStart()
        LiquidGlass.apply(dialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = SheetLyricsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnLyricsClose.setOnClickListener { dismiss() }
        binding.tvLyricsTitle.text = getString(R.string.lyrics)

        binding.rvLyrics.layoutManager = LinearLayoutManager(requireContext())
        adapter = LyricsAdapter(Ui.accentColor(requireContext()))
        binding.rvLyrics.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                PlaybackManager.uiState.collectLatest { state ->
                    val song = state.currentSong
                    if (song == null) {
                        showUnavailable()
                        return@collectLatest
                    }
                    binding.tvLyricsSong.text = Ui.html("${song.name} — ${song.artists}")
                    if (song.id != lastSongId) loadLyrics(song)
                    updateActiveLine(state.positionMs)
                }
            }
        }
    }

    // ---- Lyrics loading: LRC Lib (karaoke) → static fallbacks ----

    private fun loadLyrics(song: Song) {
        lastSongId = song.id
        lyrics = null
        lastActive = -1
        karaokeMode = false
        adapter?.submit(emptyList())
        showLoading()
        loadingJob?.cancel()
        loadingJob = viewLifecycleOwner.lifecycleScope.launch {
            val result = fetchBestLyrics(song)
            if (song.id != lastSongId) return@launch
            if (result == null) showUnavailable() else showLyrics(result)
        }
    }

    private suspend fun fetchBestLyrics(song: Song): Lyrics? =
        runCatching { LyricsRepository.fetch(song) }.getOrNull()

    private fun showLyrics(result: Lyrics) {
        binding.progressLyrics.visibility = View.GONE
        binding.tvLyricsEmpty.visibility = View.GONE
        binding.rvLyrics.visibility = View.VISIBLE

        val copyright = result.copyright
        binding.tvLyricsCopyright.visibility =
            if (copyright.isNullOrBlank()) View.GONE else View.VISIBLE
        binding.tvLyricsCopyright.text = copyright.orEmpty()

        lyrics = result
        karaokeMode = result.synced
        adapter?.submit(result.lines)
        lastActive = -1
        binding.tvKaraokeBadge.visibility =
            if (karaokeMode) View.VISIBLE else View.GONE
        binding.tvLyricsTitle.text = getString(R.string.lyrics)
    }

    private fun showUnavailable() {
        loadingJob?.cancel()
        binding.progressLyrics.visibility = View.GONE
        binding.rvLyrics.visibility = View.GONE
        binding.tvLyricsCopyright.visibility = View.GONE
        binding.tvLyricsEmpty.visibility = View.VISIBLE
        binding.tvKaraokeBadge.visibility = View.GONE
    }

    private fun showLoading() {
        binding.tvLyricsEmpty.visibility = View.GONE
        binding.rvLyrics.visibility = View.GONE
        binding.tvLyricsCopyright.visibility = View.GONE
        binding.tvKaraokeBadge.visibility = View.GONE
        binding.progressLyrics.visibility = View.VISIBLE
    }

    // ---- Karaoke sync ----

    private fun updateActiveLine(positionMs: Long) {
        val lyrics = lyrics ?: return
        if (!lyrics.synced) return
        val index = lyrics.lineIndexAt(positionMs)
        if (index == lastActive) return
        lastActive = index
        adapter?.setActive(index)
        adapter?.scrollToActive(binding.rvLyrics, index)
    }

    override fun onDestroyView() {
        loadingJob?.cancel()
        adapter = null
        super.onDestroyView()
    }
}