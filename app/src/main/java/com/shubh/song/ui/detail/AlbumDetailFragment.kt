package com.shubh.song.ui.detail

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.shubh.song.MainActivity
import com.shubh.song.MySongApp
import com.shubh.song.R
import com.shubh.song.data.api.dto.AlbumDto
import com.shubh.song.data.model.Song
import com.shubh.song.databinding.FragmentAlbumDetailBinding
import com.shubh.song.databinding.ItemAlbumTrackBinding
import com.shubh.song.media.PlaybackManager
import com.shubh.song.ui.common.OverlayNav
import com.shubh.song.ui.player.PlayerActivity
import com.shubh.song.util.ThemeUtils
import com.shubh.song.util.Ui
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Dedicated album page: square cover + title / artist / release year / track
 * count header, Play All, an in-page search bar filtering the album tracks in
 * real time, and a numbered track list with durations and a 3-dot action menu.
 */
class AlbumDetailFragment : Fragment() {

    companion object {
        const val TAG = "album_detail"

        private const val ARG_ID = "album_id"
        private const val ARG_NAME = "album_name"
        private const val ARG_ARTIST = "album_artist"
        private const val ARG_YEAR = "album_year"
        private const val ARG_IMAGE = "album_image"

        fun newInstance(album: AlbumDto): AlbumDetailFragment =
            AlbumDetailFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_ID, album.id)
                    putString(ARG_NAME, album.name.orEmpty())
                    putString(
                        ARG_ARTIST,
                        album.artists?.primary.orEmpty().joinToString(", ") { it.name.orEmpty() }
                    )
                    putString(ARG_YEAR, album.year?.toString().orEmpty())
                    putString(
                        ARG_IMAGE,
                        album.image.orEmpty().lastOrNull { it.url?.isNotBlank() == true }?.url.orEmpty()
                    )
                }
            }
    }

    private var _binding: FragmentAlbumDetailBinding? = null
    private val binding get() = _binding!!

    private val repository = MySongApp.instance.musicRepository
    private val libraryRepository = MySongApp.instance.libraryRepository

    private val allTracks = mutableListOf<Song>()
    private var filterText = ""
    private var loading = false

    private lateinit var adapter: AlbumTrackAdapter

    private val albumId: String? get() = arguments?.getString(ARG_ID)
    private val albumName: String get() = arguments?.getString(ARG_NAME).orEmpty()
    private val albumArtist: String get() = arguments?.getString(ARG_ARTIST).orEmpty()
    private val albumYear: String get() = arguments?.getString(ARG_YEAR).orEmpty()
    private val albumImage: String get() = arguments?.getString(ARG_IMAGE).orEmpty()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAlbumDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? MainActivity)?.setSearchMode(true)

setupHeader()
        setupList()
        setupActions()
        setupFilter()
        setupBack()

        // Keep the track list above the soft keyboard when filtering.
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            binding.rvTracks.setPadding(0, 0, 0, ime.bottom)
            insets
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                PlaybackManager.uiState.collectLatest { state ->
                    adapter.setCurrentId(state.currentSong?.id)
                }
            }
        }

        loadAlbum()
    }

    private fun setupHeader() {
        binding.tbAlbum.setNavigationOnClickListener { close() }
        binding.tvTitle.text = Ui.html(albumName)
        binding.tvArtist.text = Ui.html(albumArtist)

        val image = albumImage
        if (image.isBlank()) {
            val accent = ThemeUtils.accentColor(requireContext())
            val gradient = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    ThemeUtils.withAlpha(accent, 60),
                    ThemeUtils.withAlpha(accent, 22),
                    android.graphics.Color.parseColor("#14141F")
                )
            )
            binding.ivCover.background = gradient
            binding.ivCover.setImageDrawable(null)
        } else {
            binding.ivCover.load(image) {
                placeholder(R.drawable.bg_placeholder)
                error(R.drawable.bg_placeholder)
                crossfade(300)
            }
        }
        binding.pbLoad.visibility = View.VISIBLE
    }

    private fun setupList() {
        adapter = AlbumTrackAdapter(
            onOpen = { song -> playAt(song) },
            onMenu = { song -> showTrackMenu(song) }
        )
        binding.rvTracks.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTracks.adapter = adapter
    }

    private fun setupActions() {
        binding.btnPlayAll.setOnClickListener {
            val list = if (filterText.isBlank()) allTracks else visibleTracks()
            if (list.isNotEmpty()) {
                PlaybackManager.playQueue(list, 0)
                PlayerActivity.open(requireContext())
            }
        }
    }

    private fun setupFilter() {
        binding.etFilter.doAfterTextChanged { text ->
            filterText = text?.toString().orEmpty().trim()
            adapter.submit(visibleTracks())
            adapter.setCurrentId(PlaybackManager.uiState.value.currentSong?.id)
        }
    }

    private fun setupBack() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = close()
            }
        )
    }

    private fun close() {
        OverlayNav.close(this, this)
    }

    private fun loadAlbum() {
        val id = albumId ?: run {
            binding.pbLoad.visibility = View.GONE
            binding.tvEmpty.visibility = View.VISIBLE
            return
        }
        if (loading) return
        loading = true
        viewLifecycleOwner.lifecycleScope.launch {
            val tracks = repository.albumSongs(id)
            allTracks.clear()
            allTracks.addAll(tracks.filter { it.id.isNotBlank() })
            loading = false
            binding.pbLoad.visibility = View.GONE
            adapter.submit(visibleTracks())
            updateMeta()
        }
    }

    private fun visibleTracks(): List<Song> {
        if (filterText.isBlank()) return allTracks
        val query = filterText.lowercase()
        return allTracks.filter {
            it.name.lowercase().contains(query) ||
                it.artists.lowercase().contains(query) ||
                it.album.lowercase().contains(query)
        }
    }

    private fun updateMeta() {
        if (allTracks.isEmpty()) {
            binding.tvMeta.text = getString(R.string.detail_no_songs)
            binding.tvEmpty.visibility = View.VISIBLE
            return
        }
        binding.tvEmpty.visibility = View.GONE
        val totalSec = allTracks.sumOf { it.durationSec.coerceAtLeast(0) }
        val year = albumYear.takeIf { it.isNotBlank() }
        val parts = mutableListOf<String>()
        year?.let { parts.add(it) }
        parts.add(getString(R.string.album_track_count, allTracks.size))
        parts.add(Ui.formatDuration(totalSec * 1000))
        binding.tvMeta.text = parts.joinToString(" • ")
    }

    private fun playAt(song: Song) {
        val index = allTracks.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        PlaybackManager.playQueue(allTracks, index)
        PlayerActivity.open(requireContext())
    }

    /** 3-dot action menu for an individual track. */
    private fun showTrackMenu(song: Song) {
        val items = arrayOf(
            getString(R.string.menu_play),
            getString(R.string.menu_love),
            getString(R.string.menu_share)
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(Ui.html(song.name))
            .setItems(items) { dialog, which ->
                when (which) {
                    0 -> {
                        dialog.dismiss()
                        playAt(song)
                    }
                    1 -> {
                        dialog.dismiss()
                        viewLifecycleOwner.lifecycleScope.launch {
                            libraryRepository.toggleLike(song, true)
                            Ui.toast(getString(R.string.detail_added_liked))
                        }
                    }
                    2 -> {
                        dialog.dismiss()
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "${song.name} — ${song.artists}\n${song.url}")
                        }
                        startActivity(Intent.createChooser(share, getString(R.string.share_track)))
                    }
                }
            }
            .show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

/** Numbered album track list with duration + 3-dot menu per row. */
private class AlbumTrackAdapter(
    private val onOpen: (Song) -> Unit,
    private val onMenu: (Song) -> Unit
) : RecyclerView.Adapter<AlbumTrackAdapter.Holder>() {

    private val items = mutableListOf<Song>()
    private var currentId: String? = null

    fun submit(list: List<Song>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun setCurrentId(id: String?) {
        if (currentId == id) return
        currentId = id
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemAlbumTrackBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val song = items[position]
        holder.binding.tvIndex.text = (position + 1).toString()
        holder.binding.tvTitle.text = com.shubh.song.util.Ui.html(song.name)
        holder.binding.tvSubtitle.text = song.artists
        holder.binding.tvDuration.text =
            com.shubh.song.util.Ui.formatDuration(song.durationSec.coerceAtLeast(0) * 1000)

        val isCurrent = song.id == currentId
        holder.binding.tvTitle.setTextColor(
            holder.binding.root.context.getColor(
                if (isCurrent) R.color.accent else R.color.text_primary
            )
        )
        holder.binding.root.setOnClickListener { onOpen(song) }
        holder.binding.ivMenu.setOnClickListener { onMenu(song) }
    }

    class Holder(val binding: ItemAlbumTrackBinding) : RecyclerView.ViewHolder(binding.root)
}