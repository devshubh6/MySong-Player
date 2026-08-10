package com.shubh.song.ui.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.shubh.song.MySongApp
import com.shubh.song.R
import com.shubh.song.data.db.entity.PlaylistEntity
import com.shubh.song.databinding.FragmentLibraryBinding
import com.shubh.song.ui.common.AppViewModelFactory
import com.shubh.song.ui.common.PlaylistGridAdapter
import com.shubh.song.ui.sheets.PlaylistSongsSheet
import com.shubh.song.util.ThemeManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LibraryFragment : Fragment() {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LibraryViewModel by viewModels { AppViewModelFactory }

    private var playlistAdapter: PlaylistGridAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ThemeManager.applyAppBackground(binding.root)

        binding.tvLibraryTitle.text = getString(R.string.library_title)
        applyAccentTints()

        binding.folderDownloads.tvTitle.text = getString(R.string.folder_downloads)
        binding.folderDownloads.tvDesc.text = getString(R.string.folder_downloads_desc)
        binding.folderDownloads.ivIcon.setImageResource(R.drawable.ic_download)
        binding.folderDownloads.root.setOnClickListener {
            PlaylistSongsSheet.newFolderInstance(
                getString(R.string.folder_downloads),
                PlaylistSongsSheet.Folder.DOWNLOADS
            ).show(parentFragmentManager, "downloads")
        }

        binding.folderLiked.tvTitle.text = getString(R.string.folder_liked)
        binding.folderLiked.tvDesc.text = getString(R.string.folder_liked_desc)
        binding.folderLiked.ivIcon.setImageResource(R.drawable.ic_favorite)
        binding.folderLiked.root.setOnClickListener {
            PlaylistSongsSheet.newFolderInstance(
                getString(R.string.folder_liked),
                PlaylistSongsSheet.Folder.LIKED
            ).show(parentFragmentManager, "liked")
        }

        binding.folderCached.tvTitle.text = getString(R.string.folder_cached)
        binding.folderCached.tvDesc.text = getString(R.string.folder_cached_desc)
        binding.folderCached.ivIcon.setImageResource(R.drawable.ic_cloud_off)
        binding.folderCached.root.setOnClickListener {
            PlaylistSongsSheet.newFolderInstance(
                getString(R.string.folder_cached),
                PlaylistSongsSheet.Folder.CACHED
            ).show(parentFragmentManager, "cached")
        }

        binding.btnAddPlaylist.setOnClickListener { showCreateDialog() }

        binding.rvPlaylists.layoutManager = GridLayoutManager(requireContext(), 2)
        playlistAdapter = PlaylistGridAdapter(
            onClick = { playlist ->
                PlaylistSongsSheet.newPlaylistInstance(playlist.id, playlist.name)
                    .show(parentFragmentManager, "playlist_songs")
            },
            onMore = { playlist -> showPlaylistMenu(playlist) }
        )
        binding.rvPlaylists.adapter = playlistAdapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    MySongApp.instance.accent.collectLatest { applyAccentTints() }
                }
                launch {
                    viewModel.playlists.collectLatest { playlists ->
                        playlistAdapter?.submit(playlists)
                        binding.tvEmptyPlaylists.visibility =
                            if (playlists.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.likedCount.collectLatest { binding.folderLiked.tvDesc.text =
                        getString(R.string.liked_songs_count, it) }
                }
                launch {
                    viewModel.downloadsCount.collectLatest { binding.folderDownloads.tvDesc.text =
                        getString(R.string.downloads_count, it) }
                }
                launch {
                    viewModel.cachedCount.collectLatest { binding.folderCached.tvDesc.text =
                        getString(R.string.cached_count, it) }
                }
            }
        }
    }

    /** Subtle accent tint on the folder cards + header glow. */
    private fun applyAccentTints() {
        ThemeManager.applyHeaderGradient(binding.libraryScroll)
        ThemeManager.applyCardTint(binding.folderDownloads.root)
        ThemeManager.applyCardTint(binding.folderLiked.root)
        ThemeManager.applyCardTint(binding.folderCached.root)
    }

    private fun showCreateDialog() {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.playlist_name_hint)
            setPadding(24, 16, 24, 16)
        }
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.new_playlist)
            .setView(input)
            .setPositiveButton(R.string.create_playlist, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    input.error = getString(R.string.playlist_name_hint)
                } else {
                    viewModel.createPlaylist(name)
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun showPlaylistMenu(playlist: PlaylistEntity) {
        val options = arrayOf(getString(R.string.playlist_songs), getString(R.string.rename), getString(R.string.delete))
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(playlist.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> PlaylistSongsSheet.newPlaylistInstance(playlist.id, playlist.name)
                        .show(parentFragmentManager, "playlist_songs")
                    1 -> showRenameDialog(playlist)
                    2 -> viewModel.deletePlaylist(playlist)
                }
            }
            .show()
    }

    private fun showRenameDialog(playlist: PlaylistEntity) {
        val input = EditText(requireContext()).apply {
            setText(playlist.name)
            setPadding(24, 16, 24, 16)
        }
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.rename)
            .setView(input)
            .setPositiveButton(R.string.rename, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = input.text.toString().trim()
                if (name.isBlank()) {
                    input.error = getString(R.string.playlist_name_hint)
                } else {
                    viewModel.renamePlaylist(playlist, name)
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}