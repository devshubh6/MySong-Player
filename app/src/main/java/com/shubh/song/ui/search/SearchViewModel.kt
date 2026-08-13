package com.shubh.song.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shubh.song.MySongApp
import com.shubh.song.data.api.dto.ArtistDto
import com.shubh.song.data.model.Song
import com.shubh.song.media.PlaybackManager
import com.shubh.song.ui.player.PlayerActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SearchViewModel : ViewModel() {

    private val repository = MySongApp.instance.musicRepository
    private val prefs = MySongApp.instance.settingsRepository.prefs
    private val app = MySongApp.instance

    companion object {
        private const val PAGE_SIZE = 30
    }

    data class SearchUiState(
        val query: String = "",
        /** True once a full search was executed (vs. live suggestions). */
        val searched: Boolean = false,
        val loading: Boolean = false,
        val results: List<Song> = emptyList(),
        val topArtist: ArtistDto? = null,
        val hasMore: Boolean = true
    )

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state

    private val _recents = MutableStateFlow(prefs.recentSearches)
    val recents: StateFlow<List<String>> = _recents

    private var debounceJob: Job? = null

    /** Live suggestions while the user types; debounced to avoid API spam. */
    fun onQueryChanged(text: String) {
        debounceJob?.cancel()
        val query = text.trim()
        if (query.isEmpty()) {
            _state.value = SearchUiState()
            return
        }
        _state.value = _state.value.copy(query = query, loading = true)
        debounceJob = viewModelScope.launch {
            delay(280)
            val songs = try {
                repository.searchSuggestions(query)
            } catch (t: Throwable) {
                emptyList()
            }
            _state.value = SearchUiState(query = query, searched = false, results = songs)
        }
    }

    /** Executes a full search (also used by category/recent chips). */
    fun commitSearch(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        debounceJob?.cancel()
        _state.value = SearchUiState(query = q, searched = true, loading = true)
        viewModelScope.launch {
            val result = coroutineScope {
                val songs = async { repository.searchSongsPaged(q, 0, PAGE_SIZE) }
                val artist = async { repository.topArtist(q) }
                songs.await() to artist.await()
            }
            val (songs, artist) = result
            _state.value = SearchUiState(
                query = q,
                searched = true,
                results = songs,
                topArtist = artist,
                hasMore = songs.size >= PAGE_SIZE
            )
            addRecent(q)
        }
    }

    /** Fetches the next "infinite scroll" page and appends it to the results. */
    fun loadMore() {
        val snapshot = _state.value
        if (!snapshot.searched || snapshot.loading || !snapshot.hasMore || snapshot.results.isEmpty()) return
        val nextPage = snapshot.results.size / PAGE_SIZE
        if (nextPage < 1) return
        _state.value = snapshot.copy(loading = true)
        viewModelScope.launch {
            val more = try {
                repository.searchSongsPaged(snapshot.query, nextPage, PAGE_SIZE)
            } catch (t: Throwable) {
                emptyList()
            }
            val current = _state.value
            val merged = (current.results + more).distinctBy { it.id }
            _state.value = SearchUiState(
                query = current.query,
                searched = true,
                results = merged,
                topArtist = current.topArtist,
                hasMore = more.size >= PAGE_SIZE
            )
        }
    }

    /** Plays the top-artist "artist radio" — shuffled artist catalog. */
    fun playArtistRadio() {
        val artist = _state.value.topArtist ?: return
        val name = artist.name?.trim().orEmpty()
        if (name.isBlank()) return
        viewModelScope.launch {
            val songs = try {
                repository.searchSongs(name, 40)
            } catch (t: Throwable) {
                emptyList()
            }
            if (songs.isEmpty()) return@launch
            PlaybackManager.playQueue(songs.shuffled(), 0)
            PlayerActivity.open(app)
        }
    }

    fun clear() {
        debounceJob?.cancel()
        _state.value = SearchUiState()
    }

    private fun addRecent(query: String) {
        val next = (listOf(query) + _recents.value.filter { it != query }).take(8)
        prefs.recentSearches = next
        _recents.value = next
    }

    fun clearRecents() {
        prefs.recentSearches = emptyList()
        _recents.value = emptyList()
    }
}