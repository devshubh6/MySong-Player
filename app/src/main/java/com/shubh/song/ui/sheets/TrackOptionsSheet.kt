package com.shubh.song.ui.sheets

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.shubh.song.R
import com.shubh.song.data.model.Song
import com.shubh.song.databinding.SheetTrackOptionsBinding
import com.shubh.song.media.PlaybackManager
import com.shubh.song.util.Ui

class TrackOptionsSheet : BottomSheetDialogFragment() {

    private lateinit var binding: SheetTrackOptionsBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = SheetTrackOptionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val song = PlaybackManager.uiState.value.currentSong
        binding.root.apply {
            (getChildAt(0) as? android.widget.TextView)?.let { header ->
                if (song != null) header.text = "${song.name} • ${song.artists}"
            }
        }

        binding.rowShare.setOnClickListener {
            val song = currentSong() ?: return@setOnClickListener
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "${song.name} — ${song.artists}\n${song.url}")
            }
            startActivity(Intent.createChooser(share, getString(R.string.share_track)))
        }

        binding.rowDetails.setOnClickListener {
            val song = currentSong() ?: return@setOnClickListener
            if (song.album.isNotBlank()) {
                Ui.toast("${song.name} — ${song.album}")
            }
        }

        binding.rowAddPlaylist.setOnClickListener {
            showChildSheet(PlaylistPickerSheet(), "picker")
            dismiss()
        }

        binding.rowLyrics.setOnClickListener {
            showChildSheet(LyricsSheet(), "lyrics")
            dismiss()
        }

        binding.rowSleep.setOnClickListener {
            showChildSheet(SleepSheet(), "sleep")
            dismiss()
        }

        binding.rowEqualizer.setOnClickListener {
            showChildSheet(EqSheet(), "eq")
            dismiss()
        }
    }

    /** Crash-proof presentation of child sheets on top of this one. */
    private fun showChildSheet(sheet: androidx.fragment.app.DialogFragment, tag: String) {
        try {
            val manager = parentFragmentManager ?: return
            if (manager.isDestroyed || manager.findFragmentByTag(tag) != null) return
            sheet.show(manager, tag)
        } catch (e: Exception) {
            // Never crash the player when stacking sheets.
        }
    }

    private fun currentSong(): Song? = PlaybackManager.uiState.value.currentSong
}