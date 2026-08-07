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
import com.shubh.song.MySongApp
import com.shubh.song.data.model.Lyrics
import com.shubh.song.data.model.Song
import com.shubh.song.databinding.SheetLyricsBinding
import com.shubh.song.media.PlaybackManager
import com.shubh.song.ui.common.LyricsAdapter
import com.shubh.song.util.Ui
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LyricsSheet : BottomSheetDialogFragment() {

    private lateinit var binding: SheetLyricsBinding
    private var adapter: LyricsAdapter? = null
    private var loadingJob: Job? = null
    private var lastSongId: String? = null
    private var lyrics: Lyrics? = null
    private var lastActive = -1

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
        binding.tvLyricsTitle.text = getString(com.shubh.song.R.string.lyrics)

        binding.rvLyrics.layoutManager = LinearLayoutManager(requireContext())
        adapter = LyricsAdapter(Ui.accentColor(requireContext()))
        binding.rvLyrics.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                PlaybackManager.uiState.collectLatest { state ->
                    val song = state.currentSong
                    if (song == null) {
                        showUnavailable()
                    } else {
                        binding.tvLyricsSong.text = "${song.name} — ${song.artists}"
                        if (song.id != lastSongId) loadLyrics(song)
                        updateActiveLine(state.positionMs)
                    }
                }
            }
        }
    }

    private fun loadLyrics(song: Song) {
        lastSongId = song.id
        lyrics = null
        lastActive = -1
        adapter?.submit(emptyList())
        showLoading()
        loadingJob?.cancel()
        loadingJob = viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                MySongApp.instance.musicRepository.fetchLyrics(song)
            }
            if (song.id != lastSongId) return@launch
            lyrics = result
            if (result == null) showUnavailable() else showLyrics(result)
        }
    }

    private fun showLyrics(result: Lyrics) {
        binding.progressLyrics.visibility = View.GONE
        binding.tvLyricsEmpty.visibility = View.GONE
        binding.rvLyrics.visibility = View.VISIBLE
        val copyright = result.copyright
        binding.tvLyricsCopyright.visibility =
            if (copyright.isNullOrBlank()) View.GONE else View.VISIBLE
        binding.tvLyricsCopyright.text = copyright.orEmpty()
        adapter?.submit(result.lines)
        lastActive = -1
    }

    private fun showUnavailable() {
        loadingJob?.cancel()
        binding.progressLyrics.visibility = View.GONE
        binding.rvLyrics.visibility = View.GONE
        binding.tvLyricsCopyright.visibility = View.GONE
        binding.tvLyricsEmpty.visibility = View.VISIBLE
    }

    private fun showLoading() {
        binding.tvLyricsEmpty.visibility = View.GONE
        binding.rvLyrics.visibility = View.GONE
        binding.tvLyricsCopyright.visibility = View.GONE
        binding.progressLyrics.visibility = View.VISIBLE
    }

    private fun updateActiveLine(positionMs: Long) {
        val activeLyrics = lyrics ?: return
        val index = activeLyrics.lineIndexAt(positionMs)
        if (index == lastActive) return
        lastActive = index
        adapter?.setActive(index)
        if (index >= 0) {
            val manager = binding.rvLyrics.layoutManager as? LinearLayoutManager ?: return
            binding.rvLyrics.post {
                manager.scrollToPositionWithOffset(index, binding.rvLyrics.height / 3)
            }
        }
    }

    override fun onDestroyView() {
        loadingJob?.cancel()
        adapter = null
        super.onDestroyView()
    }
}