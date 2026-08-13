package com.shubh.song.ui.settings

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.shubh.song.MySongApp
import com.shubh.song.R
import com.shubh.song.data.model.Song
import com.shubh.song.databinding.FragmentSettingsExtrasBinding
import com.shubh.song.ui.common.AppViewModelFactory
import com.shubh.song.ui.common.OverlayNav
import com.shubh.song.ui.driving.DrivingModeActivity
import com.shubh.song.ui.stats.StatsFragment
import com.shubh.song.util.PlaylistFiles
import com.shubh.song.util.ThemeManager
import com.shubh.song.util.Ui
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.launch

/**
 * Extras & Tools sub-screen: My Stats & Recap, Car Mode and playlist
 * export / import (SAF document pickers).
 */
class SettingsExtrasFragment : Fragment() {

    companion object {
        const val TAG = "settings_extras"
    }

    private var _binding: FragmentSettingsExtrasBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by activityViewModels { AppViewModelFactory }

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        if (uri != null && isAdded) exportPlaylists(uri)
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && isAdded) importPlaylists(uri)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsExtrasBinding.inflate(inflater, container, false)
        return binding.root
    }

    /** Re-tints action buttons to the live accent after any change. */
    override fun onResume() {
        super.onResume()
        applyAccentTints()
    }

    private fun applyAccentTints() {
        try {
            val accent = ThemeManager.accentColor()
            binding.btnExportPlaylists.backgroundTintList = ColorStateList.valueOf(accent)
        } catch (t: Throwable) {
            // Tinting is cosmetic; never crash.
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnExtrasBack.setOnClickListener { close() }
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = close()
            }
        )

        binding.btnStats.setOnClickListener {
            OverlayNav.open(this, StatsFragment(), StatsFragment.TAG)
        }
        binding.btnCarMode.setOnClickListener {
            startActivity(Intent(requireContext(), DrivingModeActivity::class.java))
        }
        binding.btnExportPlaylists.setOnClickListener {
            exportLauncher.launch("mysong-playlists.json")
        }
        binding.btnImportPlaylists.setOnClickListener {
            importLauncher.launch(
                arrayOf("application/json", "text/plain", "audio/x-mpegurl", "audio/mpegurl", "*/*")
            )
        }
    }

    // ---- Playlist export / import (SAF) ----

    private fun exportPlaylists(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            val repo = MySongApp.instance.libraryRepository
            val playlists = repo.observePlaylists().first()
            if (playlists.isEmpty()) {
                Ui.toast(getString(R.string.playlists_export_empty))
                return@launch
            }
            val data = playlists.mapNotNull { play ->
                val songs = repo.observePlaylistSongs(play.id).first()
                if (songs.isEmpty()) null else play to songs
            }
            val dumps = PlaylistFiles.dumpOf(data)
            // `.m3u` requested → real M3U (with embedded metadata); else JSON.
            val isM3u = displayNameOf(uri)?.lowercase()?.endsWith(".m3u") == true
            val text = if (isM3u) PlaylistFiles.toM3u(dumps) else PlaylistFiles.toJson(dumps)
            try {
                val out = requireContext().contentResolver.openOutputStream(uri)
                    ?: return@launch
                out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                Ui.toast(getString(R.string.playlists_exported))
            } catch (e: Exception) {
                Ui.toast(getString(R.string.playlists_export_failed))
            }
        }
    }

    private fun displayNameOf(uri: Uri): String? {
        return try {
            requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            } ?: uri.lastPathSegment
        } catch (e: Exception) {
            uri.lastPathSegment
        }
    }

    private fun importPlaylists(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            val text = try {
                requireContext().contentResolver.openInputStream(uri)
                    ?.use { it.readBytes().toString(Charsets.UTF_8) }
            } catch (e: Exception) {
                null
            }
            if (text.isNullOrBlank()) {
                Ui.toast(getString(R.string.playlists_import_failed))
                return@launch
            }
            val dumps = PlaylistFiles.parseJson(text) ?: PlaylistFiles.parseM3u(text)
            if (dumps.isNullOrEmpty()) {
                Ui.toast(getString(R.string.playlists_import_failed))
                return@launch
            }
            val repo = MySongApp.instance.libraryRepository
            val music = MySongApp.instance.musicRepository
            var added = 0
            for (dump in dumps) {
                val playlistId = repo.createPlaylist(dump.name.ifBlank { "Imported Playlist" })
                for (songDump in dump.songs) {
                    val song = if (songDump.name.isNotBlank()) songDump.toSong()
                    else music.fetchSong(songDump.id)
                    if (song != null) {
                        repo.addToPlaylist(playlistId, song)
                        added++
                    }
                }
            }
            Ui.toast(getString(R.string.playlists_imported))
        }
    }

    private fun close() {
        try {
            OverlayNav.close(this, this)
        } catch (t: Throwable) {
            // Back navigation must never crash.
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

private fun PlaylistFiles.SongDump.toSong(): Song =
    Song(
        id = id,
        name = name,
        artists = artists,
        album = album,
        imageUrl = imageUrl,
        durationSec = durationSec,
        year = year,
        language = language,
        playCount = playCount,
        url = url,
        downloadUrls = downloadUrls
    )