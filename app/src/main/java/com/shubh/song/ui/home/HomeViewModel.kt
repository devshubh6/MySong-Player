package com.shubh.song.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shubh.song.MySongApp
import com.shubh.song.data.model.Song
import com.shubh.song.data.repository.FeedData
import com.shubh.song.data.repository.MusicRepository
import com.shubh.song.media.PlaybackManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    private val repository: MusicRepository = MySongApp.instance.musicRepository

    data class FeedState(
        val loading: Boolean = true,
        val error: Boolean = false,
        val feed: FeedData? = null
    )

    private val _feed = MutableStateFlow(FeedState())
    val feed: StateFlow<FeedState> = _feed

    private val _suggestions = MutableStateFlow<List<Song>>(emptyList())
    val suggestions: StateFlow<List<Song>> = _suggestions

    private val _infiniteSongs = MutableStateFlow<List<Song>>(emptyList())
    val infiniteSongs: StateFlow<List<Song>> = _infiniteSongs

    private val _infiniteLoading = MutableStateFlow(false)
    val infiniteLoading: StateFlow<Boolean> = _infiniteLoading

    private val _infiniteEnded = MutableStateFlow(false)
    val infiniteEnded: StateFlow<Boolean> = _infiniteEnded

    private var searchJob: Job? = null
    private val langPages = mutableMapOf<String, Int>()
    private val seenIds = mutableSetOf<String>()
    private var pageLoads = 0
    private var playingInfinite = false

    init {
        refresh()
    }

    fun refresh() {
        val languages = MySongApp.instance.settingsRepository.languages
        val country = MySongApp.instance.settingsRepository.country
        viewModelScope.launch {
            _feed.value = FeedState(loading = true)
            try {
                val data = repository.homeFeed(languages, country)
                _feed.value = FeedState(loading = false, feed = data)
            } catch (t: Throwable) {
                _feed.value = FeedState(loading = false, error = true)
            }
        }
        restartInfinite()
    }

    /** Resets the endless "For You" feed over the active languages. */
    fun restartInfinite() {
        _infiniteSongs.value = emptyList()
        langPages.clear()
        seenIds.clear()
        pageLoads = 0
        _infiniteEnded.value = false
        loadNextPage()
    }

    fun loadNextPage() {
        if (_infiniteLoading.value) return
        val langs = MySongApp.instance.settingsRepository.languages.toList()
        if (langs.isEmpty()) {
            _infiniteEnded.value = true
            return
        }
        _infiniteLoading.value = true
        viewModelScope.launch {
            try {
                val lang = langs[pageLoads % langs.size]
                val page = langPages.getOrDefault(lang, 0)
                langPages[lang] = page + 1
                pageLoads++
                val results = repository.searchSongsPaged("$lang songs", page, 20)
                val fresh = results.filter { seenIds.add(it.id) }
                if (fresh.isEmpty() && langs.size == 1 && page > 4) {
                    _infiniteEnded.value = true
                }
                val all = _infiniteSongs.value + fresh
                _infiniteSongs.value = all
                if (playingInfinite && fresh.isNotEmpty()) {
                    PlaybackManager.appendToQueue(fresh)
                }
            } catch (t: Throwable) {
                _infiniteEnded.value = true
            } finally {
                _infiniteLoading.value = false
            }
        }
    }

    fun setPlayingInfinite(playing: Boolean) {
        playingInfinite = playing
    }

    fun isInfiniteLoading(): Boolean = _infiniteLoading.value

    fun onSearchQuery(query: String) {
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _suggestions.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            try {
                val results = repository.searchSuggestions(trimmed)
                _suggestions.value = results
            } catch (t: Throwable) {
                _suggestions.value = emptyList()
            }
        }
    }

    fun clearSuggestions() {
        searchJob?.cancel()
        _suggestions.value = emptyList()
    }

    suspend fun searchDirect(query: String): List<Song> =
        repository.searchSongs(query, limit = 15)

    suspend fun playlistSongs(id: String): List<Song> =
        repository.playlistSongs(id)

    suspend fun albumSongs(id: String): List<Song> =
        repository.albumSongs(id)

    suspend fun artistSongs(id: String): List<Song> =
        repository.artistSongs(id)
}