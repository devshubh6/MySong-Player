package com.shubh.song.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shubh.song.MySongApp
import com.shubh.song.data.db.entity.PlaylistEntity
import com.shubh.song.data.db.entity.SavedAlbumEntity
import com.shubh.song.data.model.Song
import com.shubh.song.data.repository.LibraryRepository
import com.shubh.song.media.PlaybackManager
import com.shubh.song.ui.player.PlayerActivity
import com.shubh.song.util.Ui
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel : ViewModel() {

    private val repository: LibraryRepository = MySongApp.instance.libraryRepository

    val playlists: StateFlow<List<PlaylistEntity>> =
        repository.observePlaylists()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val likedCount: StateFlow<Int> =
        repository.observeLikedSongs().map { it.size }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val downloadsCount: StateFlow<Int> =
        repository.observeDownloadedSongs().map { it.size }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val cachedCount: StateFlow<Int> =
        repository.observeCachedSongs().map { it.size }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val savedAlbums: StateFlow<List<SavedAlbumEntity>> =
        repository.observeSavedAlbums()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val likedSongs: StateFlow<List<Song>> =
        repository.observeLikedSongs()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloadedSongs: StateFlow<List<Song>> =
        repository.observeDownloadedSongs()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cachedSongs: StateFlow<List<Song>> =
        repository.observeCachedSongs()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.createPlaylist(name)
            Ui.toast(MySongApp.instance.getString(com.shubh.song.R.string.playlist_created))
        }
    }

    fun renamePlaylist(playlist: PlaylistEntity, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.renamePlaylist(playlist.id, name)
            Ui.toast(MySongApp.instance.getString(com.shubh.song.R.string.playlist_renamed))
        }
    }

    fun deletePlaylist(playlist: PlaylistEntity) {
        viewModelScope.launch {
            repository.deletePlaylist(playlist.id)
            Ui.toast(MySongApp.instance.getString(com.shubh.song.R.string.playlist_deleted))
        }
    }

    /** Plays a saved album's locally stored tracks from the start. */
    fun playSavedAlbum(album: SavedAlbumEntity) {
        viewModelScope.launch {
            val tracks = repository.observeSavedAlbumTracks(album.albumId)
                .first()
                .map { it.toSong() }
                .filter { it.id.isNotBlank() }
            if (tracks.isEmpty()) {
                Ui.toast(MySongApp.instance.getString(com.shubh.song.R.string.detail_no_songs))
                return@launch
            }
            PlaybackManager.playQueue(tracks, 0)
            PlayerActivity.open(MySongApp.instance)
        }
    }

    /** Removes a locally saved album (bookmark + its tracks). */
    fun removeSavedAlbum(album: SavedAlbumEntity) {
        viewModelScope.launch {
            repository.removeSavedAlbum(album.albumId)
            Ui.toast(MySongApp.instance.getString(com.shubh.song.R.string.album_removed))
        }
    }
}
