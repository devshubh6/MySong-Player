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
import com.shubh.song.data.model.Song
import com.shubh.song.databinding.SheetPlaylistSongsBinding
import com.shubh.song.media.DownloadEngine
import com.shubh.song.media.PlaybackManager
import com.shubh.song.MySongApp
import com.shubh.song.ui.common.SongAdapter
import com.shubh.song.ui.player.PlayerActivity
import com.shubh.song.util.Ui
import kotlinx.coroutines.launch

class PlaylistSongsSheet : BottomSheetDialogFragment() {

    private var title: String = ""
    private var playlistId: Long = -1L
    private var folderKind: String = ""

    private lateinit var binding: SheetPlaylistSongsBinding
    private var adapter: SongAdapter? = null

    enum class Folder(val key: String) {
        LIKED("liked"),
        DOWNLOADS("downloads"),
        CACHED("cached")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = SheetPlaylistSongsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        arguments?.let {
            title = it.getString(ARG_TITLE, "")
            playlistId = it.getLong(ARG_PLAYLIST_ID, -1L)
            folderKind = it.getString(ARG_FOLDER, "")
        }

        binding.btnPsClose.setOnClickListener { dismiss() }
        binding.tvPsTitle.text = title

        binding.rvPs.layoutManager = LinearLayoutManager(requireContext())
        adapter = SongAdapter(
            onClick = { song -> playAt(song) },
            onMenu = if (folderKind == Folder.DOWNLOADS.key) { song ->
                confirmDeleteDownload(song)
            } else null
        )
        binding.rvPs.adapter = adapter

        binding.btnPsPlayAll.setOnClickListener {
            playAll()
        }

        when {
            playlistId >= 0 -> observePlaylist()
            folderKind.isNotEmpty() -> observeFolder()
            else -> render(emptyList())
        }
    }

    private fun observePlaylist() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                MySongApp.instance.libraryRepository.observePlaylistSongs(playlistId)
                    .collect { render(it) }
            }
        }
    }

    private fun observeFolder() {
        val repo = MySongApp.instance.libraryRepository
        val source = when (folderKind) {
            Folder.LIKED.key -> repo.observeLikedSongs()
            Folder.DOWNLOADS.key -> repo.observeDownloadedSongs()
            Folder.CACHED.key -> repo.observeCachedSongs()
            else -> null
        } ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                source.collect { render(it) }
            }
        }
    }

    private var lastSongs: List<Song> = emptyList()

    private fun render(list: List<Song>) {
        lastSongs = list
        adapter?.submit(list)
        binding.tvPsCount.text = list.size.toString()
        binding.tvPsEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun playAll() {
        if (lastSongs.isEmpty()) return
        PlaybackManager.playQueue(lastSongs, 0)
        PlayerActivity.open(requireContext())
        dismiss()
    }

    private fun playAt(song: Song) {
        if (lastSongs.isEmpty()) return
        val index = lastSongs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        PlaybackManager.playQueue(lastSongs, index)
        PlayerActivity.open(requireContext())
        dismiss()
    }

    private fun confirmDeleteDownload(song: Song) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_download)
            .setMessage(R.string.delete_download_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val ok = DownloadEngine.delete(song)
                    Ui.toast(
                        if (ok) getString(R.string.download_deleted)
                        else getString(R.string.download_failed)
                    )
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    companion object {
        const val ARG_TITLE = "title"
        const val ARG_PLAYLIST_ID = "playlist_id"
        const val ARG_FOLDER = "folder"

        fun newPlaylistInstance(playlistId: Long, name: String): PlaylistSongsSheet =
            PlaylistSongsSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, name)
                    putLong(ARG_PLAYLIST_ID, playlistId)
                }
            }

        fun newFolderInstance(title: String, folder: Folder): PlaylistSongsSheet =
            PlaylistSongsSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, title)
                    putString(ARG_FOLDER, folder.key)
                }
            }
    }
}