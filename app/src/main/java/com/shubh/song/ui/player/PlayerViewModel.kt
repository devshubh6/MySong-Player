package com.shubh.song.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shubh.song.MySongApp
import com.shubh.song.data.db.entity.PlaylistEntity
import com.shubh.song.data.model.Song
import com.shubh.song.data.repository.LibraryRepository
import com.shubh.song.media.DownloadEngine
import com.shubh.song.media.NotifLike
import com.shubh.song.media.PlaybackManager
import com.shubh.song.media.SleepTimer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PlayerViewModel : ViewModel() {

    private val repository: LibraryRepository = MySongApp.instance.libraryRepository

    val playback: StateFlow<PlaybackManager.PlayerUiState> = PlaybackManager.uiState

    private val currentId: StateFlow<String?> = playback.map { it.currentSong?.id }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val isLiked: StateFlow<Boolean> = currentId.flatMapLatest { id ->
        if (id == null) kotlinx.coroutines.flow.flowOf(false)
        else repository.isLiked(id)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val isDownloaded: StateFlow<Boolean> = currentId.flatMapLatest { id ->
        if (id == null) kotlinx.coroutines.flow.flowOf(false)
        else repository.isDownloaded(id)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** All liked song ids — lets every player page paint its own heart. */
    val likedIds: StateFlow<Set<String>> = repository.observeLikedIds()
        .map { ids -> ids.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** All downloaded song ids — lets every player page paint its own badge. */
    val downloadedIds: StateFlow<Set<String>> = repository.observeDownloadedIds()
        .map { ids -> ids.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val downloadProgress: StateFlow<Map<String, Int>> = DownloadEngine.progress

    val sleepRemainingMs: StateFlow<Long?> = SleepTimer.remainingMs

    val playlists: StateFlow<List<PlaylistEntity>> =
        repository.observePlaylists()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun currentSong(): Song? = playback.value.currentSong

    fun toggleLike() {
        val song = currentSong() ?: return
        toggleLikeFor(song, isLiked.value)
    }

    /** Toggles the like flag for any page, not just the playing track. */
    fun toggleLikeFor(song: Song, currentlyLiked: Boolean) {
        val liked = !currentlyLiked
        NotifLike.set(song.id, liked)
        viewModelScope.launch {
            repository.toggleLike(song, liked)
        }
    }

    fun downloadCurrent() {
        val song = currentSong() ?: return
        download(song)
    }

    /** Starts a download for any page's song. */
    fun download(song: Song) {
        if (downloadedIds.value.contains(song.id) || DownloadEngine.isActive(song.id)) return
        DownloadEngine.start(song)
    }

    fun addCurrentToPlaylist(playlistId: Long) {
        val song = currentSong() ?: return
        viewModelScope.launch {
            repository.addToPlaylist(playlistId, song)
        }
    }

    fun createPlaylistAndAdd(name: String) {
        val song = currentSong() ?: return
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = repository.createPlaylist(name)
            repository.addToPlaylist(id, song)
        }
    }

    fun startSleepTimer(minutes: Int) {
        SleepTimer.start(minutes)
    }

    fun stopSleepTimer() {
        SleepTimer.stop()
    }
}
