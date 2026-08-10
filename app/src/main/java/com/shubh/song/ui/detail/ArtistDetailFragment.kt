package com.shubh.song.ui.detail

import android.content.res.ColorStateList
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
import com.shubh.song.MainActivity
import com.shubh.song.MySongApp
import com.shubh.song.R
import com.shubh.song.data.model.Song
import com.shubh.song.databinding.FragmentArtistDetailBinding
import com.shubh.song.media.PlaybackManager
import com.shubh.song.ui.common.OverlayNav
import com.shubh.song.ui.common.SongAdapter
import com.shubh.song.ui.player.PlayerActivity
import com.shubh.song.util.ThemeManager
import com.shubh.song.util.Ui
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Dedicated artist page: collapsible artwork banner with name + song count,
 * Play All / Shuffle actions, an in-page search bar filtering the artist's
 * tracks in real time, and an endless (paged) vertical song list. Tapping a
 * track queues the artist's full catalog.
 */
class ArtistDetailFragment : Fragment() {

    companion object {
        const val TAG = "artist_detail"

        private const val ARG_ID = "artist_id"
        private const val ARG_NAME = "artist_name"
        private const val ARG_IMAGE = "artist_image"
        private const val PAGE_SIZE = 30

        fun newInstance(id: String?, name: String, imageUrl: String): ArtistDetailFragment =
            ArtistDetailFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_ID, id)
                    putString(ARG_NAME, name)
                    putString(ARG_IMAGE, imageUrl)
                }
            }
    }

    private var _binding: FragmentArtistDetailBinding? = null
    private val binding get() = _binding!!

    private val repository = MySongApp.instance.musicRepository

    private val songs = mutableListOf<Song>()
    private var filterText = ""
    private var page = 0
    private var hasMore = true
    private var loading = false

    private lateinit var adapter: SongAdapter

    private val artistId: String? get() = arguments?.getString(ARG_ID)
    private val artistName: String get() = arguments?.getString(ARG_NAME).orEmpty()
    private val artistImage: String get() = arguments?.getString(ARG_IMAGE).orEmpty()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentArtistDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    /** Re-tints the Play All button to the live accent after any change. */
    override fun onResume() {
        super.onResume()
        applyAccentTints()
    }

    private fun applyAccentTints() {
        try {
            binding.btnPlayAll.backgroundTintList =
                ColorStateList.valueOf(ThemeManager.accentColor())
        } catch (t: Throwable) {
            // Tinting is cosmetic; never crash.
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? MainActivity)?.setSearchMode(true)

        setupHeader()
        setupList()
        setupFilter()
        setupActions()
        setupBack()

        // Keep the list above the soft keyboard when filtering.
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            binding.rvList.setPadding(0, 0, 0, ime.bottom)
            insets
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                PlaybackManager.uiState.collectLatest { state ->
                    adapter.setCurrentId(state.currentSong?.id)
                }
            }
        }

        loadNextPage(reset = true)
    }

    private fun setupHeader() {
        binding.tvHeaderName.text = Ui.html(artistName)
        binding.tvHeaderMeta.text = getString(R.string.detail_loading)

        if (artistImage.isBlank()) {
            // Fallback artwork: accent gradient + big initial letter.
            val accent = ThemeManager.accentColor()
            val gradient = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    ThemeManager.withAlpha(accent, 62),
                    ThemeManager.withAlpha(accent, 20),
                    android.graphics.Color.parseColor("#14141F")
                )
            )
            binding.ivBanner.background = gradient
            binding.ivBanner.setImageDrawable(null)
            binding.tvInitial.text = artistName.trim().firstOrNull()?.uppercase() ?: "?"
            binding.tvInitial.visibility = View.VISIBLE
        } else {
            binding.ivBanner.load(artistImage) {
                placeholder(R.drawable.bg_placeholder)
                error(R.drawable.bg_placeholder)
                crossfade(300)
            }
        }
    }

    private fun setupList() {
        adapter = SongAdapter(
            onClick = { song ->
                val index = songs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                PlaybackManager.playQueue(songs, index)
                PlayerActivity.open(requireContext())
            }
        )
        binding.rvList.layoutManager = LinearLayoutManager(requireContext())
        binding.rvList.adapter = adapter

        binding.rvList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (!recyclerView.canScrollVertically(1)) loadNextPage(reset = false)
            }
        })
    }

    private fun setupFilter() {
        binding.etFilter.doAfterTextChanged { text ->
            filterText = text?.toString().orEmpty().trim()
            adapter.submit(visibleSongs())
            adapter.setCurrentId(PlaybackManager.uiState.value.currentSong?.id)
        }
    }

    private fun setupActions() {
        binding.btnPlayAll.setOnClickListener {
            val list = if (filterText.isBlank()) songs else visibleSongs()
            if (list.isNotEmpty()) {
                PlaybackManager.playQueue(list, 0)
                PlayerActivity.open(requireContext())
            }
        }
        binding.btnShuffle.setOnClickListener {
            val list = if (filterText.isBlank()) songs else visibleSongs()
            if (list.isNotEmpty()) {
                PlaybackManager.playQueue(list.shuffled(), 0)
                PlayerActivity.open(requireContext())
            }
        }
    }

    private fun setupBack() {
        binding.tbBack.setNavigationOnClickListener { close() }
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

    /** Paged loading of the artist's catalog ("infinite scroll"). */
    private fun loadNextPage(reset: Boolean) {
        if (loading || (!reset && !hasMore)) return
        loading = true
        binding.pbLoad.visibility = View.VISIBLE

        val pageToFetch = page
        viewLifecycleOwner.lifecycleScope.launch {
            val fetched = repository.searchSongsPaged(artistName, pageToFetch, PAGE_SIZE)
            if (reset) songs.clear()
            songs.addAll(fetched.filter { it.id.isNotBlank() })
            hasMore = fetched.size == PAGE_SIZE
            page++
            loading = false
            binding.pbLoad.visibility = View.GONE
            adapter.submit(visibleSongs())
            adapter.setCurrentId(PlaybackManager.uiState.value.currentSong?.id)
            updateMeta()
        }
    }

    private fun visibleSongs(): List<Song> {
        if (filterText.isBlank()) return songs
        val query = filterText.lowercase()
        return songs.filter {
            it.name.lowercase().contains(query) ||
                it.artists.lowercase().contains(query) ||
                it.album.lowercase().contains(query)
        }
    }

    private fun updateMeta() {
        binding.tvHeaderMeta.text = getString(R.string.artist_song_count, songs.size)
        binding.tvEmpty.visibility =
            if (songs.isEmpty() && !loading) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
