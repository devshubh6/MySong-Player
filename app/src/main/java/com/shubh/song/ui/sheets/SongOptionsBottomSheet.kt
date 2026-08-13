package com.shubh.song.ui.sheets

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.shubh.song.MySongApp
import com.shubh.song.MainActivity
import com.shubh.song.R
import com.shubh.song.data.db.entity.PlaylistEntity
import com.shubh.song.data.model.Song
import com.shubh.song.databinding.SheetSongOptionsBinding
import com.shubh.song.media.DownloadEngine
import com.shubh.song.media.PlaybackManager
import com.shubh.song.ui.detail.ArtistDetailFragment
import com.shubh.song.ui.player.PlayerActivity
import com.shubh.song.util.LiquidGlass
import com.shubh.song.util.Ui
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Universal "3-dots" song menu shown from every song row (Home, Search,
 * Artist, Album, Queue). Offers Play Now / Play Next / Add to Queue, Download,
 * Save to Liked, Add to a custom Playlist/Folder, View Artist, and Share.
 */
class SongOptionsBottomSheet : BottomSheetDialogFragment() {

    private var song: Song? = null
    private var likedState = false

    private lateinit var binding: SheetSongOptionsBinding

    companion object {
        const val TAG = "song_options"

        /** Convenience launcher that always shows a fully wired sheet. */
        fun show(
            manager: FragmentManager,
            song: Song
        ): SongOptionsBottomSheet {
            val sheet = SongOptionsBottomSheet()
            sheet.song = song
            try {
                sheet.show(manager, TAG)
            } catch (e: Exception) {
                // Never crash presentation when the manager is mid-transition.
            }
            return sheet
        }
    }

    override fun onStart() {
        super.onStart()
        LiquidGlass.apply(dialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = SheetSongOptionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val track = song ?: run { dismiss(); return }
        binding.tvSongHeader.text = Ui.html(track.name)
        binding.tvSongSubtitle.text = Ui.html(track.artists)

        binding.rowPlayNow.setOnClickListener {
            PlaybackManager.playQueue(listOf(track), 0)
            PlayerActivity.open(requireContext())
            dismiss()
        }
        binding.rowPlayNext.setOnClickListener {
            PlaybackManager.playNext(listOf(track))
            Ui.toast(getString(R.string.added_play_next))
            dismiss()
        }
        binding.rowAddQueue.setOnClickListener {
            PlaybackManager.appendToQueue(listOf(track))
            Ui.toast(getString(R.string.added_to_queue))
            dismiss()
        }
        binding.rowDownload.setOnClickListener {
            if (DownloadEngine.isActive(track.id)) {
                Ui.toast(getString(R.string.download_started))
            } else {
                DownloadEngine.start(track)
                Ui.toast(getString(R.string.download_started))
            }
            dismiss()
        }
        binding.rowLike.setOnClickListener { toggleLike(track) }
        binding.rowAddPlaylist.setOnClickListener {
            showPlaylistPicker(track)
        }
        binding.rowViewArtist.setOnClickListener {
            openArtist(track)
            dismiss()
        }
        binding.rowViewArtist.visibility = View.VISIBLE
        binding.rowShare.setOnClickListener {
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "${track.name} — ${track.artists}\n${track.url}")
            }
            startActivity(Intent.createChooser(share, getString(R.string.share_track)))
            dismiss()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                MySongApp.instance.libraryRepository.isLiked(track.id).collectLatest { liked ->
                    likedState = liked
                    renderLike(liked)
                }
            }
        }
    }

    private fun renderLike(liked: Boolean) {
        binding.ivLike.setImageResource(
            if (liked) R.drawable.ic_favorite else R.drawable.ic_favorite_border
        )
        binding.ivLike.imageTintList =
            android.content.res.ColorStateList.valueOf(
                if (liked) ContextCompat.getColor(requireContext(), R.color.accent)
                else ContextCompat.getColor(requireContext(), R.color.text_primary)
            )
        binding.tvLikeLabel.text = getString(
            if (liked) R.string.menu_unlike else R.string.menu_like
        )
    }

    private fun toggleLike(track: Song) {
        val next = !likedState
        viewLifecycleOwner.lifecycleScope.launch {
            MySongApp.instance.libraryRepository.toggleLike(track, next)
            Ui.toast(getString(if (next) R.string.liked_added else R.string.liked_removed))
        }
    }

    /** Picks an existing playlist/folder or creates a new one for this track. */
    private fun showPlaylistPicker(track: Song) {
        viewLifecycleOwner.lifecycleScope.launch {
            val playlists = try {
                MySongApp.instance.libraryRepository.observePlaylists().first()
            } catch (e: Exception) {
                emptyList()
            }
            if (!isAdded) return@launch

            val names = playlists.map { it.name }.toMutableList()
            names.add(0, getString(R.string.new_playlist))

            MaterialAlertDialogBuilder(requireContext())
                .setTitle(Ui.html(track.name))
                .setItems(names.toTypedArray()) { _, which ->
                    if (which == 0) {
                        promptNewPlaylist(track)
                    } else {
                        val target = playlists[which - 1]
                        addToPlaylist(track, target.id, target.name)
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun promptNewPlaylist(track: Song) {
        val input = android.widget.EditText(requireContext()).apply {
            hint = getString(R.string.playlist_name_hint)
            setPadding(24, 16, 24, 16)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.new_playlist)
            .setView(input)
            .setPositiveButton(R.string.create_playlist) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        val id = MySongApp.instance.libraryRepository.createPlaylist(name)
                        addToPlaylist(track, id, name)
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun addToPlaylist(track: Song, playlistId: Long, playlistName: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            MySongApp.instance.libraryRepository.addToPlaylist(playlistId, track)
            Ui.toast(getString(R.string.added_to_playlist, playlistName))
        }
        dismiss()
    }

    private fun openArtist(track: Song) {
        val artist = track.artists.substringBefore(",").trim()
        if (artist.isBlank()) return
        val manager = parentFragmentManager
        if (manager.findFragmentByTag(ArtistDetailFragment.TAG) != null) return
        (activity as? MainActivity)?.setSearchMode(true)
        manager.beginTransaction()
            .setCustomAnimations(
                R.anim.slide_in_right,
                R.anim.slide_out_left,
                R.anim.slide_in_left,
                R.anim.slide_out_right
            )
            .add(R.id.nav_host, ArtistDetailFragment.newInstance(null, artist, track.imageUrl), ArtistDetailFragment.TAG)
            .commitAllowingStateLoss()
    }

}
