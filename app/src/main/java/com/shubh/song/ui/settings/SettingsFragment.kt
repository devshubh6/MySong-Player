package com.shubh.song.ui.settings

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.SeekBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButton
import com.shubh.song.MainActivity
import com.shubh.song.MySongApp
import com.shubh.song.R
import com.shubh.song.data.model.Song
import com.shubh.song.databinding.FragmentSettingsBinding
import com.shubh.song.media.PlaybackManager
import com.shubh.song.ui.common.AppViewModelFactory
import com.shubh.song.ui.common.CountryChipAdapter
import com.shubh.song.ui.common.LangChipAdapter
import com.shubh.song.ui.driving.DrivingModeActivity
import com.shubh.song.util.AmoledUtils
import com.shubh.song.util.Constants
import com.shubh.song.util.PlaylistFiles
import com.shubh.song.util.ThemeUtils
import com.shubh.song.util.Ui
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by viewModels { AppViewModelFactory }

    private val langAdapter = LangChipAdapter(
        selected = { viewModel.languages.value },
        onToggle = { viewModel.toggleLanguage(it) }
    )

    private val countryAdapter = CountryChipAdapter { country ->
        viewModel.setCountry(country)
    }

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
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvLangs.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvLangs.adapter = langAdapter
        langAdapter.submit(Constants.LANGUAGES)

        binding.rvCountries.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvCountries.adapter = countryAdapter

        binding.btnClearCache.setOnClickListener {
            viewModel.clearCache()
        }

        binding.btnExportPlaylists.setOnClickListener {
            exportLauncher.launch("mysong-playlists.json")
        }
        binding.btnImportPlaylists.setOnClickListener {
            importLauncher.launch(
                arrayOf("application/json", "text/plain", "audio/x-mpegurl", "audio/mpegurl", "*/*")
            )
        }
        binding.btnCarMode.setOnClickListener {
            startActivity(Intent(requireContext(), DrivingModeActivity::class.java))
        }

        setupTheme()
        setupAccent()
        setupQuality()
        setupAudioMode()
        setupCrossfade()
        setupExtras()
        setupAbout()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.quality.collectLatest { checkQuality(it) } }
                launch { viewModel.theme.collectLatest { checkTheme(it) } }
                launch { MySongApp.instance.accent.collect { applyAccentColors() } }
                launch { viewModel.cacheSize.collectLatest {
                    binding.tvCacheSize.text = getString(R.string.cache_size, it)
                } }
                launch { viewModel.crossfade.collectLatest { bindCrossfade(it) } }
                launch { viewModel.autoMix.collectLatest { it ->
                    if (binding.swAutoMix.isChecked != it) binding.swAutoMix.isChecked = it
                } }
                launch { viewModel.amoled.collectLatest { it ->
                    if (binding.swAmoled.isChecked != it) binding.swAmoled.isChecked = it
                } }
                launch { viewModel.audioMode.collectLatest { applyAudioModeButtons(it) } }
            }
        }

        applyAccentColors()
    }

    /** Re-tints radios + chips when the accent color changes. */
    private fun applyAccentColors() {
        val accent = ThemeUtils.accentColor(requireContext())
        val muted = ContextCompat.getColor(requireContext(), R.color.text_secondary)
        val tint = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(accent, muted)
        )
        listOf(
            binding.rbDark, binding.rbLight, binding.rbSystem,
            binding.rbQLow, binding.rbQMedium, binding.rbQHigh
        ).forEach { it.buttonTintList = tint }

        binding.tvGithub.setTextColor(accent)

        langAdapter.submit(Constants.LANGUAGES)
        countryAdapter.submit(Constants.COUNTRIES, viewModel.country.value)
    }

    private fun setupTheme() {
        binding.rbDark.setOnClickListener { viewModel.setTheme("dark") }
        binding.rbLight.setOnClickListener { viewModel.setTheme("light") }
        binding.rbSystem.setOnClickListener { viewModel.setTheme("system") }
    }

    private fun checkTheme(mode: String) {
        val radio = when (mode) {
            "light" -> binding.rbLight
            "system" -> binding.rbSystem
            else -> binding.rbDark
        }
        if (!radio.isChecked) radio.isChecked = true
    }

    private fun setupQuality() {
        binding.rbQLow.setOnClickListener { viewModel.setQuality(Constants.QUALITY_LOW) }
        binding.rbQMedium.setOnClickListener { viewModel.setQuality(Constants.QUALITY_MEDIUM) }
        binding.rbQHigh.setOnClickListener { viewModel.setQuality(Constants.QUALITY_HIGH) }
    }

    private fun checkQuality(quality: String) {
        val radio = when (quality) {
            Constants.QUALITY_LOW -> binding.rbQLow
            Constants.QUALITY_HIGH -> binding.rbQHigh
            else -> binding.rbQMedium
        }
        if (!radio.isChecked) radio.isChecked = true
    }

    private fun setupAccent() {
        val buttons = mapOf(
            binding.accentBtnGreen to "green",
            binding.accentBtnPurple to "purple",
            binding.accentBtnPink to "pink",
            binding.accentBtnCyan to "cyan",
            binding.accentBtnAmber to "amber",
            binding.accentBtnRed to "red"
        )
        buttons.forEach { (button, name) ->
            val color = Ui.accentRes(name).let { ContextCompat.getColor(requireContext(), it) }
            Ui.tintCircle(button, color)
            button.setOnClickListener {
                viewModel.setAccent(name)
                (activity as? MainActivity)?.refreshAccent()
            }
        }
    }

    // ---- New audio controls ----

    private fun setupAudioMode() {
        binding.btnModeNormal.setOnClickListener {
            viewModel.setAudioMode(PlaybackManager.AudioMode.NORMAL)
        }
        binding.btnModeSlowed.setOnClickListener {
            viewModel.setAudioMode(PlaybackManager.AudioMode.SLOWED_REVERB)
        }
        binding.btnModeNightcore.setOnClickListener {
            viewModel.setAudioMode(PlaybackManager.AudioMode.NIGHTCORE)
        }
    }

    private fun applyAudioModeButtons(mode: PlaybackManager.AudioMode) {
        val accent = ThemeUtils.accentColor(requireContext())
        applyModeChip(binding.btnModeNormal, mode == PlaybackManager.AudioMode.NORMAL, accent)
        applyModeChip(
            binding.btnModeSlowed,
            mode == PlaybackManager.AudioMode.SLOWED_REVERB,
            accent
        )
        applyModeChip(binding.btnModeNightcore, mode == PlaybackManager.AudioMode.NIGHTCORE, accent)
    }

    private fun applyModeChip(button: MaterialButton, active: Boolean, accent: Int) {
        button.backgroundTintList = ColorStateList.valueOf(
            if (active) accent else Color.TRANSPARENT
        )
        button.setTextColor(if (active) Color.WHITE else accent)
        button.strokeColor = ColorStateList.valueOf(accent)
        button.strokeWidth = if (active) 0 else 2
    }

    private fun setupCrossfade() {
        binding.sbCrossfade.max = 12
        binding.sbCrossfade.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) binding.tvCrossfadeValue.text = getString(R.string.crossfade_value, progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                viewModel.setCrossfade(seekBar?.progress ?: 0)
            }
        })
    }

    private fun bindCrossfade(seconds: Int) {
        if (binding.sbCrossfade.progress != seconds) binding.sbCrossfade.progress = seconds
        binding.tvCrossfadeValue.text = getString(R.string.crossfade_value, seconds)
    }

    private fun setupExtras() {
        binding.swAutoMix.setOnCheckedChangeListener { _, checked ->
            viewModel.setAutoMix(checked)
        }
        binding.swAmoled.setOnCheckedChangeListener { _, checked ->
            viewModel.setAmoled(checked)
            // Re-paint every chrome surface immediately.
            val activity = activity as? MainActivity
            activity?.refreshChrome()
            AmoledUtils.apply(requireActivity(), binding.root)
            binding.root.setBackgroundColor(AmoledUtils.black())
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

    private fun setupAbout() {
        binding.tvVersion.text = getString(R.string.about_version, appVersion())

        binding.tvGithub.paintFlags =
            binding.tvGithub.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        binding.tvGithub.setOnClickListener {
            try {
                startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/devshubh6"))
                )
            } catch (e: Exception) {
                Ui.toast(getString(R.string.about_github))
            }
        }
    }

    private fun appVersion(): String =
        try {
            requireContext().packageManager.getPackageInfo(
                requireContext().packageName,
                0
            ).versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
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