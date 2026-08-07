package com.shubh.song.ui.search

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.shubh.song.MainActivity
import com.shubh.song.R
import com.shubh.song.data.api.dto.ArtistDto
import com.shubh.song.data.model.Song
import com.shubh.song.databinding.FragmentSearchBinding
import com.shubh.song.databinding.ItemArtistTopBinding
import com.shubh.song.databinding.ItemRecentChipBinding
import com.shubh.song.databinding.ItemSearchCategoryBinding
import com.shubh.song.databinding.ItemSongBinding
import com.shubh.song.media.PlaybackManager
import com.shubh.song.ui.common.AppViewModelFactory
import com.shubh.song.ui.common.OverlayNav
import com.shubh.song.ui.detail.ArtistDetailFragment
import com.shubh.song.ui.player.PlayerActivity
import com.shubh.song.util.ThemeUtils
import com.shubh.song.util.Ui
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Full-screen Spotify-style search page: extended input with clear button,
 * browse-all category grid + recent search chips, live suggestion results
 * rendered below the input (never over it), a top-artist card with
 * "Play Artist Radio", and an infinitely-scrolling paged song list.
 */
class SearchFragment : Fragment() {

    companion object {
        const val TAG = "search_fragment"
    }

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SearchViewModel by viewModels { AppViewModelFactory }

    private lateinit var categoryAdapter: SearchCategoryAdapter
    private lateinit var recentAdapter: RecentChipAdapter
    private lateinit var resultsAdapter: SearchResultsAdapter

    private val categories = listOf(
        SearchCategory(getStringSafe(R.string.search_category_pop), "pop songs", R.drawable.ic_music_note),
        SearchCategory(getStringSafe(R.string.search_category_bollywood), "bollywood hits", R.drawable.ic_trending_up),
        SearchCategory(getStringSafe(R.string.search_category_lofi), "lo-fi beats", R.drawable.ic_headset),
        SearchCategory(getStringSafe(R.string.search_category_workout), "workout energy", R.drawable.ic_speed),
        SearchCategory(getStringSafe(R.string.search_category_bhajans), "devotional bhajans", R.drawable.ic_star),
        SearchCategory(getStringSafe(R.string.search_category_classics), "old classics", R.drawable.ic_music_note)
    )

    private fun getStringSafe(resId: Int): String =
        try {
            getString(resId)
        } catch (e: Exception) {
            ""
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? MainActivity)?.setSearchMode(true)

        // Push the panels above the soft keyboard so results never hide under
        // it or leave a phantom gap at the bottom (adjustResize is on the
        // activity; ime() reports the keyboard height).
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            binding.nsvBrowse.setPadding(0, 0, 0, ime.bottom)
            binding.resultsPanel.setPadding(0, 0, 0, ime.bottom)
            insets
        }

        setupAdapters()
        setupInput()
        setupBack()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collectLatest { render(it) } }
                launch { viewModel.recents.collectLatest { renderRecents(it) } }
            }
        }
    }

    private fun setupAdapters() {
        categoryAdapter = SearchCategoryAdapter(categories) { category ->
            binding.etSearchInput.setText(category.label)
            viewModel.commitSearch(category.query)
        }
        binding.rvCategories.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvCategories.adapter = categoryAdapter

        recentAdapter = RecentChipAdapter { term ->
            binding.etSearchInput.setText(term)
            viewModel.commitSearch(term)
        }
        binding.rvRecents.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvRecents.adapter = recentAdapter

        resultsAdapter = SearchResultsAdapter(
            onSongClick = { song ->
                val results = viewModel.state.value.results
                if (results.isEmpty()) return@SearchResultsAdapter
                val index = results.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                PlaybackManager.playQueue(results, index)
                PlayerActivity.open(requireContext())
            },
            onArtistClick = { song -> openArtistPage(song.artists, song.imageUrl) },
            onArtistOpen = { artist -> openArtistPage(artist.name.orEmpty(), artistBestImage(artist)) },
            onArtistRadio = { viewModel.playArtistRadio() }
        )
        binding.rvResults.layoutManager = LinearLayoutManager(requireContext())
        binding.rvResults.adapter = resultsAdapter
        binding.rvResults.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0 && !recyclerView.canScrollVertically(1)) {
                    viewModel.loadMore()
                }
            }
        })
    }

    /** Opens the artist page for a given artist name. */
    private fun openArtistPage(artistName: String, imageUrl: String) {
        val artist = artistName.trim()
        if (artist.isBlank()) return
        OverlayNav.open(
            this,
            ArtistDetailFragment.newInstance(null, artist, imageUrl),
            ArtistDetailFragment.TAG
        )
    }

    private fun artistBestImage(artist: ArtistDto?): String =
        artist?.image?.firstOrNull { it.quality == "150x150" }?.url
            ?: artist?.image?.firstOrNull()?.url
            ?: ""

    private fun setupInput() {
        binding.etSearchInput.doAfterTextChanged { text ->
            viewModel.onQueryChanged(text?.toString().orEmpty())
            binding.ivClear.visibility =
                if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
        binding.ivClear.setOnClickListener {
            binding.etSearchInput.setText("")
            viewModel.clear()
            binding.ivClear.visibility = View.GONE
        }
        binding.etSearchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                viewModel.commitSearch(binding.etSearchInput.text.toString())
                true
            } else {
                false
            }
        }
        binding.tvClearRecents.setOnClickListener { viewModel.clearRecents() }
    }

    private fun setupBack() {
        binding.btnBack.setOnClickListener { close() }
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

    /** Switches between the browse panel and the results list. */
    private fun render(state: SearchViewModel.SearchUiState) {
        val typing = state.query.isNotEmpty()
        binding.nsvBrowse.visibility = if (typing) View.GONE else View.VISIBLE
        binding.resultsPanel.visibility = if (typing) View.VISIBLE else View.GONE

        if (!typing) {
            binding.ivClear.visibility = View.GONE
            return
        }

        binding.tvResultsTitle.text = getString(
            if (state.searched) R.string.search_no_results_title else R.string.search_suggestions_title
        )
        binding.pbSearch.visibility = if (state.loading) View.VISIBLE else View.GONE
        binding.tvNoResults.visibility =
            if (!state.loading && state.results.isEmpty()) View.VISIBLE else View.GONE
        binding.rvResults.visibility =
            if (!state.loading && state.results.isNotEmpty()) View.VISIBLE else View.GONE
        resultsAdapter.submit(state.results, state.topArtist, ThemeUtils.accentColor(requireContext()))
        resultsAdapter.setCurrentId(PlaybackManager.uiState.value.currentSong?.id)
    }

    private fun renderRecents(recents: List<String>) {
        val hasRecents = recents.isNotEmpty()
        binding.recentsSection.visibility = if (hasRecents) View.VISIBLE else View.GONE
        binding.rvRecents.visibility = if (hasRecents) View.VISIBLE else View.GONE
        recentAdapter.submit(recents)
    }

    override fun onResume() {
        super.onResume()
        binding.etSearchInput.requestFocus()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

data class SearchCategory(
    val label: String,
    val query: String,
    val iconRes: Int
)

/**
 * Result list = optional top-artist card (position 0) followed by paged song
 * rows. Song rows reuse [ItemSongBinding] and expose tappable artist subtitles.
 */
private class SearchResultsAdapter(
    private val onSongClick: (Song) -> Unit,
    private val onArtistClick: (Song) -> Unit,
    private val onArtistOpen: (ArtistDto) -> Unit,
    private val onArtistRadio: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val songs = mutableListOf<Song>()
    private var artist: ArtistDto? = null
    private var accent = 0
    private var currentId: String? = null

    private fun hasArtist(): Boolean = artist != null

    @SuppressLint("NotifyDataSetChanged")
    fun submit(songs: List<Song>, artist: ArtistDto?, accentColor: Int) {
        this.songs.clear()
        this.songs.addAll(songs)
        this.artist = artist
        this.accent = accentColor
        notifyDataSetChanged()
    }

    fun setCurrentId(id: String?) {
        if (currentId == id) return
        currentId = id
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int =
        if (hasArtist() && position == 0) TYPE_ARTIST else TYPE_SONG

    override fun getItemCount(): Int = songs.size + if (hasArtist()) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        if (viewType == TYPE_ARTIST) {
            ArtistHolder(ItemArtistTopBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        } else {
            SongHolder(ItemSongBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ArtistHolder -> holder.bind(artist)
            is SongHolder -> holder.bind(songs[position - if (hasArtist()) 1 else 0])
        }
    }

    inner class ArtistHolder(val binding: ItemArtistTopBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(target: ArtistDto?) {
            val artist = target ?: return
            binding.tvArtistName.text = Ui.html(artist.name.orEmpty())
            val fans = artist.fanCount.orEmpty()
                .ifBlank { artist.followerCount?.toString().orEmpty() }
            binding.tvArtistSub.text =
                if (fans.isNotBlank()) "$fans listeners" else ""
            val image = artist.image?.firstOrNull { it.quality == "150x150" }?.url
                ?: artist.image?.firstOrNull()?.url
                ?: ""
            if (image.isNotBlank()) {
                binding.ivArtistArt.load(image) {
                    placeholder(R.drawable.bg_placeholder)
                    error(R.drawable.bg_placeholder)
                    crossfade(250)
                }
            } else {
                binding.ivArtistArt.setImageResource(R.drawable.ic_person)
                binding.ivArtistArt.setColorFilter(
                    androidx.core.content.res.ResourcesCompat.getColor(
                        binding.root.resources, R.color.text_secondary, null
                    )
                )
            }
            binding.btnArtistRadio.setOnClickListener { onArtistRadio() }
            binding.root.setOnClickListener { onArtistOpen(artist) }
        }
    }

    inner class SongHolder(val binding: ItemSongBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(song: Song) {
            binding.tvTitle.text = Ui.html(song.name)
            binding.tvSubtitle.text = listOf(song.artists, song.album)
                .filter { it.isNotBlank() }
                .joinToString(" • ")
            binding.ivArt.load(song.imageUrl) {
                placeholder(R.drawable.bg_placeholder)
                error(R.drawable.bg_placeholder)
                crossfade(250)
            }

            val isCurrent = song.id == currentId
            binding.ivPlaying.visibility =
                if (isCurrent) View.VISIBLE else View.GONE
            binding.tvTitle.setTextColor(
                binding.root.context.getColor(
                    if (isCurrent) R.color.accent else R.color.text_primary
                )
            )

            binding.ivMenu.visibility = View.GONE
            binding.root.setOnClickListener { onSongClick(song) }

            binding.tvSubtitle.isClickable = true
            binding.tvSubtitle.isFocusable = true
            binding.tvSubtitle.setTextColor(accent)
            binding.tvSubtitle.setOnClickListener { onArtistClick(song) }
        }
    }

    companion object {
        private const val TYPE_ARTIST = 0
        private const val TYPE_SONG = 1
    }
}

/** 2-column "Browse All" category grid with dynamic accent tiles. */
private class SearchCategoryAdapter(
    private val items: List<SearchCategory>,
    private val onClick: (SearchCategory) -> Unit
) : RecyclerView.Adapter<SearchCategoryAdapter.Holder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemSearchCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val category = items[position]
        holder.binding.tvCategoryLabel.text = Ui.html(category.label)
        holder.binding.ivCategoryIcon.setImageResource(category.iconRes)
        holder.binding.ivCategoryIcon.imageTintList =
            android.content.res.ColorStateList.valueOf(
                ThemeUtils.accentColor(holder.itemView.context)
            )
        ThemeUtils.categoryTile(holder.binding.root)
        holder.binding.root.setOnClickListener { onClick(category) }
    }

    class Holder(val binding: ItemSearchCategoryBinding) : RecyclerView.ViewHolder(binding.root)
}

/** Horizontal recent-search chips, accent-tinted once rendered. */
private class RecentChipAdapter(
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<RecentChipAdapter.Holder>() {

    private val items = mutableListOf<String>()

    @SuppressLint("NotifyDataSetChanged")
    fun submit(list: List<String>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemRecentChipBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val term = items[position]
        holder.binding.recentChip.text = Ui.html(term)
        ThemeUtils.chipFill(holder.binding.recentChip)
        holder.binding.recentChip.setTextColor(
            ThemeUtils.accentColor(holder.itemView.context)
        )
        holder.binding.recentChip.setOnClickListener { onClick(term) }
    }

    class Holder(val binding: ItemRecentChipBinding) : RecyclerView.ViewHolder(binding.root)
}