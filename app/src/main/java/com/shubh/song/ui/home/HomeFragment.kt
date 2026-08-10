package com.shubh.song.ui.home

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.shubh.song.MySongApp
import com.shubh.song.R
import com.shubh.song.data.api.dto.AlbumDto
import com.shubh.song.data.api.dto.ArtistDto
import com.shubh.song.data.api.dto.PlaylistDto
import com.shubh.song.data.model.Song
import com.shubh.song.data.repository.FeedData
import com.shubh.song.databinding.FragmentHomeBinding
import com.shubh.song.databinding.ItemFeedRowBinding
import com.shubh.song.ui.common.AlbumAdapter
import com.shubh.song.ui.common.AppViewModelFactory
import com.shubh.song.ui.common.ArtistAdapter
import com.shubh.song.ui.common.OverlayNav
import com.shubh.song.ui.common.PlaylistAdapter
import com.shubh.song.ui.common.SkeletonAdapter
import com.shubh.song.ui.common.SongAdapter
import com.shubh.song.ui.detail.AlbumDetailFragment
import com.shubh.song.ui.detail.ArtistDetailFragment
import com.shubh.song.media.PlaybackManager
import com.shubh.song.ui.player.PlayerActivity
import com.shubh.song.ui.search.SearchFragment
import com.shubh.song.ui.sheets.LangFilterSheet
import com.shubh.song.util.ThemeManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels { AppViewModelFactory }

    private lateinit var forYouAdapter: SongAdapter
    private var forYouProgress: ProgressBar? = null
    private var skeletonAnimator: ObjectAnimator? = null

    /**
     * True once real feed content has been rendered. While false, the skeleton
     * shimmer owns the screen; once true, pull-to-refresh only uses the
     * SwipeRefresh spinner so the skeleton never overlaps live cards.
     */
    private var feedRendered = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ThemeManager.applyAppBackground(binding.root)

        setupSkeleton()
        setupForYou()
        setupRefresh()
        setupScrollPaging()
        setupLangFilter()
        setupSearchEntry()
        applyAccentTints()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.feed.collectLatest { state ->
                        renderFeed(state)
                    }
                }
                launch {
                    viewModel.infiniteSongs.collectLatest { songs ->
                        forYouAdapter.submit(songs)
                    }
                }
                launch {
                    viewModel.infiniteLoading.collectLatest { loading ->
                        forYouProgress?.visibility =
                            if (loading) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    MySongApp.instance.accent.collectLatest { applyAccentTints() }
                }
            }
        }
    }

    private fun setupSkeleton() {
        binding.rvSkeleton.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSkeleton.adapter = SkeletonAdapter()
    }

    private fun setupForYou() {
        forYouAdapter = SongAdapter(
            onClick = { song ->
                val songs = viewModel.infiniteSongs.value
                if (songs.isEmpty()) return@SongAdapter
                val index = songs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                PlaybackManager.playQueue(songs, index)
                viewModel.setPlayingInfinite(true)
                PlayerActivity.open(requireContext())
            }
        )
    }

    private fun setupRefresh() {
        binding.swipeFeed.setColorSchemeColors(ThemeManager.accentColor())
        binding.swipeFeed.setOnRefreshListener {
            viewModel.refresh()
        }
    }

    private fun setupScrollPaging() {
        binding.nsvFeed.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            val content = binding.nsvFeed.getChildAt(0)
            if (content != null) {
                val reachedEnd =
                    scrollY >= (content.height - binding.nsvFeed.height - 800)
                if (reachedEnd) viewModel.loadNextPage()
            }
        }
    }

    private fun setupLangFilter() {
        binding.btnLangFilter.setOnClickListener {
            LangFilterSheet.newInstance().setOnApply { selected ->
                MySongApp.instance.settingsRepository.setLanguages(selected)
                try {
                    MySongApp.instance.applyStoredLocale()
                } catch (_: Exception) {
                }
                viewModel.refresh()
            }.show(parentFragmentManager, "lang_filter")
        }
    }

    /** Opens the dedicated full-screen search page with a slide transition. */
    private fun setupSearchEntry() {
        binding.btnSearch.setOnClickListener { openSearch() }
    }

    private fun openSearch() {
        val manager = parentFragmentManager
        if (manager.findFragmentByTag(SearchFragment.TAG) != null) return
        manager.beginTransaction()
            .setCustomAnimations(
                R.anim.slide_in_right,
                R.anim.slide_out_left,
                R.anim.slide_in_left,
                R.anim.slide_out_right
            )
            .add(R.id.nav_host, SearchFragment(), SearchFragment.TAG)
            .commitAllowingStateLoss()
    }

    /** Applies the subtle accent header glow + swipe colours. */
    private fun applyAccentTints() {
        val accent = ThemeManager.accentColor()
        binding.swipeFeed.setColorSchemeColors(accent)
        ThemeManager.applyHeaderGradient(binding.homeHeader)
        forYouProgress?.indeterminateTintList =
            android.content.res.ColorStateList.valueOf(accent)
    }

    /**
     * Strict visibility state machine for the skeleton shimmer:
     *  - First load: skeleton VISIBLE + shimmer running, feed hidden.
     *  - Done / failed: shimmer stopped, skeleton GONE, feed VISIBLE.
     *  - Pull-to-refresh with data already on screen: only the swipe spinner.
     */
    private fun renderFeed(state: HomeViewModel.FeedState) {
        if (state.loading) {
            if (!feedRendered) {
                binding.nsvFeed.visibility = View.GONE
                startSkeleton(true)
            } else {
                startSkeleton(false)
            }
            return
        }

        startSkeleton(false)
        binding.nsvFeed.visibility = View.VISIBLE
        binding.swipeFeed.isRefreshing = false

        if (state.feed == null) {
            binding.tvFeedError.visibility = View.VISIBLE
            return
        }

        binding.tvFeedError.visibility = View.GONE
        buildFeed(state.feed)
        feedRendered = true
    }

    private fun startSkeleton(show: Boolean) {
        binding.rvSkeleton.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            if (skeletonAnimator == null) {
                skeletonAnimator = ObjectAnimator.ofFloat(binding.rvSkeleton, "alpha", 0.55f, 1f).apply {
                    duration = 850
                    repeatCount = ObjectAnimator.INFINITE
                    repeatMode = ObjectAnimator.REVERSE
                }
                skeletonAnimator?.start()
            } else if (!skeletonAnimator!!.isStarted) {
                skeletonAnimator?.start()
            }
        } else {
            skeletonAnimator?.cancel()
            skeletonAnimator = null
        }
    }

    private fun buildFeed(feed: FeedData) {
        val container = binding.feedContainer
        container.removeAllViews()
        addRow(
            container,
            getString(R.string.feed_trending),
            songs = feed.trending.songs,
            onSong = { song ->
                PlaybackManager.playQueue(feed.trending.songs, feed.trending.songs.indexOf(song))
                PlayerActivity.open(requireContext())
            }
        )
        addRow(
            container,
            getString(R.string.feed_top_charts),
            playlists = feed.topCharts.playlists,
            onPlaylist = { playlist ->
                openPlaylist(playlist)
            }
        )
        addRow(
            container,
            getString(R.string.feed_featured_albums),
            albums = feed.featuredAlbums.albums,
            onAlbum = { album ->
                openAlbum(album)
            }
        )
        addRow(
            container,
            getString(R.string.feed_popular_artists),
            artists = feed.popularArtists.artists,
            onArtist = { artist ->
                openArtist(artist)
            }
        )
        addRow(
            container,
            getString(R.string.feed_top_playlists),
            playlists = feed.topPlaylists.playlists,
            onPlaylist = { playlist ->
                openPlaylist(playlist)
            }
        )
        for (row in feed.extra) {
            addRow(
                container,
                localizedTitle(row.title),
                songs = row.songs,
                onSong = { song ->
                    PlaybackManager.playQueue(row.songs, row.songs.indexOf(song))
                    PlayerActivity.open(requireContext())
                }
            )
        }
        addForYouRow(container, getString(R.string.for_you))
    }

    /** Maps raw curated query strings to localized section titles. */
    private fun localizedTitle(theme: String): String = when (theme) {
        "party hits" -> getString(R.string.feed_party)
        "workout energy" -> getString(R.string.feed_workout)
        "romantic love songs" -> getString(R.string.feed_romantic)
        "chill lofi" -> getString(R.string.feed_chill)
        "devotional bhajans" -> getString(R.string.feed_devotional)
        "old classics" -> getString(R.string.feed_classics)
        "top remix mashup" -> getString(R.string.feed_remixes)
        else -> theme.replaceFirstChar { it.uppercase() }
    }

    private fun addForYouRow(container: ViewGroup, title: String) {
        val rowBinding = ItemFeedRowBinding.inflate(layoutInflater, container, false)
        rowBinding.tvRowTitle.text = com.shubh.song.util.Ui.html(title)
        val lm = LinearLayoutManager(requireContext())
        lm.isItemPrefetchEnabled = true
        rowBinding.rvRow.layoutManager = lm
        rowBinding.rvRow.adapter = forYouAdapter
        rowBinding.rvRow.isNestedScrollingEnabled = false
        container.addView(rowBinding.root)

        forYouProgress = ProgressBar(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER
            }
            indeterminateTintList = android.content.res.ColorStateList.valueOf(
                ThemeManager.accentColor()
            )
            visibility = View.GONE
        }
        container.addView(forYouProgress)
    }

    private fun addRow(
        container: ViewGroup,
        title: String,
        songs: List<Song> = emptyList(),
        albums: List<AlbumDto> = emptyList(),
        artists: List<ArtistDto> = emptyList(),
        playlists: List<PlaylistDto> = emptyList(),
        onSong: (Song) -> Unit = {},
        onAlbum: (AlbumDto) -> Unit = {},
        onArtist: (ArtistDto) -> Unit = {},
        onPlaylist: (PlaylistDto) -> Unit = {}
    ) {
        if (songs.isEmpty() && albums.isEmpty() && artists.isEmpty() && playlists.isEmpty()) return
        val rowBinding = ItemFeedRowBinding.inflate(layoutInflater, container, false)
        rowBinding.tvRowTitle.text = com.shubh.song.util.Ui.html(title)

        val rowList = rowBinding.rvRow
        rowList.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

        when {
            songs.isNotEmpty() -> {
                val adapter = SongAdapter(
                    onClick = onSong,
                    onArtistClick = { song -> openArtistFromSong(song) }
                )
                adapter.submit(songs)
                rowList.adapter = adapter
            }
            albums.isNotEmpty() -> {
                val adapter = AlbumAdapter(onClick = onAlbum)
                adapter.submit(albums)
                rowList.adapter = adapter
            }
            artists.isNotEmpty() -> {
                val adapter = ArtistAdapter(onClick = onArtist)
                adapter.submit(artists)
                rowList.adapter = adapter
            }
            playlists.isNotEmpty() -> {
                val adapter = PlaylistAdapter(onClick = onPlaylist)
                adapter.submit(playlists)
                rowList.adapter = adapter
            }
        }
        container.addView(rowBinding.root)
    }

    /** Opens the artist page from an artist name shown on a song row. */
    private fun openArtistFromSong(song: Song) {
        val artist = song.artists.substringBefore(",").trim()
        if (artist.isBlank()) return
        OverlayNav.open(
            this,
            ArtistDetailFragment.newInstance(null, artist, song.imageUrl),
            ArtistDetailFragment.TAG
        )
    }

    private fun openPlaylist(playlist: PlaylistDto) {
        viewLifecycleOwner.lifecycleScope.launch {
            val songs = viewModel.playlistSongs(playlist.id ?: return@launch)
            if (songs.isNotEmpty()) {
                PlaybackManager.playQueue(songs, 0)
                PlayerActivity.open(requireContext())
            }
        }
    }

    private fun openAlbum(album: AlbumDto) {
        OverlayNav.open(this, AlbumDetailFragment.newInstance(album), AlbumDetailFragment.TAG)
    }

    private fun openArtist(artist: ArtistDto) {
        val image = artist.image.orEmpty()
            .lastOrNull { it.url?.isNotBlank() == true }?.url.orEmpty()
        OverlayNav.open(
            this,
            ArtistDetailFragment.newInstance(artist.id, artist.name.orEmpty(), image),
            ArtistDetailFragment.TAG
        )
    }

    override fun onDestroyView() {
        startSkeleton(false)
        super.onDestroyView()
        _binding = null
    }
}