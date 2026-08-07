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

    val downloadProgress: StateFlow<Map<String, Int>> = DownloadEngine.progress

    val sleepRemainingMs: StateFlow<Long?> = SleepTimer.remainingMs

    val playlists: StateFlow<List<PlaylistEntity>> =
        repository.observePlaylists()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun currentSong(): Song? = playback.value.currentSong

    fun toggleLike() {
        val song = currentSong() ?: return
        val liked = !isLiked.value
        NotifLike.set(song.id, liked)
        viewModelScope.launch {
            repository.toggleLike(song, liked)
        }
    }

    fun downloadCurrent() {
        val song = currentSong() ?: return
        if (isDownloaded.value || DownloadEngine.isActive(song.id)) return
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
