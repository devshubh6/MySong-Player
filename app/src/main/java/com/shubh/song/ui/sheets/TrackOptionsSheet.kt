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
import com.shubh.song.util.LiquidGlass
import com.shubh.song.util.Ui

class TrackOptionsSheet : BottomSheetDialogFragment() {

    private lateinit var binding: SheetTrackOptionsBinding

    override fun onStart() {
        super.onStart()
        LiquidGlass.apply(dialog)
    }

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
                if (song != null) header.text = Ui.html("${song.name} • ${song.artists}")
            }
        }

        binding.rowShare.setOnClickListener {
            val track = currentSong() ?: return@setOnClickListener
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "${track.name} — ${track.artists}\n${track.url}")
            }
            startActivity(Intent.createChooser(share, getString(R.string.share_track)))
        }

        binding.rowDetails.setOnClickListener {
            val track = currentSong() ?: return@setOnClickListener
            if (track.album.isNotBlank()) {
                Ui.toast("${track.name} — ${track.album}")
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

        setupPlaybackModes()
    }

    /**
     * Playback signature selector ([Normal] [Slowed + Reverb] [Nightcore]),
     * moved off the player screen into this modal.
     */
    private fun setupPlaybackModes() {
        fun select(mode: PlaybackManager.AudioMode) {
            PlaybackManager.setAudioMode(mode)
            applyModeButtons(mode)
        }
        binding.btnModeNormal.setOnClickListener {
            select(PlaybackManager.AudioMode.NORMAL)
        }
        binding.btnModeSlowed.setOnClickListener {
            select(PlaybackManager.AudioMode.SLOWED_REVERB)
        }
        binding.btnModeNightcore.setOnClickListener {
            select(PlaybackManager.AudioMode.NIGHTCORE)
        }
        applyModeButtons(PlaybackManager.uiState.value.audioMode)
    }

    private fun applyModeButtons(mode: PlaybackManager.AudioMode) {
        val accent = Ui.accentColor(requireContext())
        val enabled = android.graphics.Color.WHITE
        val idle = accent
        applyModeChip(binding.btnModeNormal, mode == PlaybackManager.AudioMode.NORMAL, accent, enabled, idle)
        applyModeChip(
            binding.btnModeSlowed,
            mode == PlaybackManager.AudioMode.SLOWED_REVERB,
            accent,
            enabled,
            idle
        )
        applyModeChip(
            binding.btnModeNightcore,
            mode == PlaybackManager.AudioMode.NIGHTCORE,
            accent,
            enabled,
            idle
        )
    }

    private fun applyModeChip(
        button: com.google.android.material.button.MaterialButton,
        active: Boolean,
        accent: Int,
        enabled: Int,
        idle: Int
    ) {
        button.backgroundTintList = android.content.res.ColorStateList.valueOf(
            if (active) accent else android.graphics.Color.TRANSPARENT
        )
        button.setTextColor(if (active) enabled else idle)
        button.strokeColor = android.content.res.ColorStateList.valueOf(accent)
        button.strokeWidth = if (active) 0 else 2
    }

    /** Crash-proof presentation of child sheets on top of this one. */
    private fun showChildSheet(sheet: androidx.fragment.app.DialogFragment, tag: String) {
        try {
            val manager = parentFragmentManager
            if (manager.isDestroyed || manager.findFragmentByTag(tag) != null) return
            sheet.show(manager, tag)
        } catch (e: Exception) {
            // Never crash the player when stacking sheets.
        }
    }

    private fun currentSong(): Song? = PlaybackManager.uiState.value.currentSong
}