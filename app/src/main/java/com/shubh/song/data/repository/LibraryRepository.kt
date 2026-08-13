package com.shubh.song.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.shubh.song.data.db.AppDatabase
import com.shubh.song.data.db.entity.CacheEntity
import com.shubh.song.data.db.entity.DownloadEntity
import com.shubh.song.data.db.entity.LikedSongEntity
import com.shubh.song.data.db.entity.PlaylistEntity
import com.shubh.song.data.db.entity.PlaylistSongCrossRef
import com.shubh.song.data.db.entity.SavedAlbumEntity
import com.shubh.song.data.db.entity.SavedAlbumTrackEntity
import com.shubh.song.data.db.entity.SongEntity
import com.shubh.song.data.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LibraryRepository(context: Context) {

    private val db = AppDatabase.get(context)
    private val songsDao = db.songsDao()
    private val likedDao = db.likedSongsDao()
    private val downloadsDao = db.downloadsDao()
    private val cachesDao = db.cachesDao()
    private val playlistDao = db.playlistDao()
    private val savedAlbumDao = db.savedAlbumDao()

    // ---- Saved Albums ----
    fun observeSavedAlbums(): Flow<List<SavedAlbumEntity>> = savedAlbumDao.observeAlbums()

    fun isAlbumSaved(albumId: String): Flow<Boolean> = savedAlbumDao.isSaved(albumId)

    fun observeSavedAlbumTracks(albumId: String): Flow<List<SavedAlbumTrackEntity>> =
        savedAlbumDao.observeTracks(albumId)

    /**
     * Bookmarks an album with all of its tracks in one atomic transaction.
     * Passing an empty list still saves the album row itself.
     */
    suspend fun saveAlbum(album: SavedAlbumEntity, tracks: List<SavedAlbumTrackEntity>) {
        savedAlbumDao.insertAlbum(album)
        if (tracks.isNotEmpty()) savedAlbumDao.insertTracks(tracks)
    }

    suspend fun removeSavedAlbum(albumId: String) {
        savedAlbumDao.deleteTracks(albumId)
        savedAlbumDao.deleteAlbum(albumId)
    }

    suspend fun upsertSong(song: Song) = songsDao.upsert(song.toEntity())

    // ---- Liked ----
    fun isLiked(songId: String): Flow<Boolean> = likedDao.isLiked(songId)

    fun observeLikedSongs(): Flow<List<Song>> =
        likedDao.observeLikedSongs().map { list -> list.map { Song.fromEntity(it) } }

    suspend fun toggleLike(song: Song, liked: Boolean) {
        upsertSong(song)
        if (liked) {
            likedDao.insertLike(LikedSongEntity(song.id, System.currentTimeMillis()))
        } else {
            likedDao.removeLike(song.id)
        }
    }

    // ---- Downloads ----
    fun isDownloaded(songId: String): Flow<Boolean> = downloadsDao.isDownloaded(songId)

    fun observeDownloadedSongs(): Flow<List<Song>> =
        downloadsDao.observeDownloadedSongs().map { list -> list.map { Song.fromEntity(it) } }

    suspend fun downloadFilePath(songId: String): String? = downloadsDao.filePathOf(songId)

    suspend fun addDownload(song: Song, filePath: String, quality: String, size: Long) {
        db.withTransaction {
            songsDao.upsert(song.toEntity())
            downloadsDao.insert(DownloadEntity(song.id, filePath, quality, size, System.currentTimeMillis()))
        }
    }

    suspend fun removeDownload(songId: String) = downloadsDao.remove(songId)

    // ---- Cache ----
    fun isCached(songId: String): Flow<Boolean> = cachesDao.isCached(songId)

    fun observeCachedSongs(): Flow<List<Song>> =
        cachesDao.observeCachedSongs().map { list -> list.map { Song.fromEntity(it) } }

    suspend fun cacheFilePath(songId: String): String? = cachesDao.filePathOf(songId)

    suspend fun addCache(song: Song, filePath: String, size: Long) {
        db.withTransaction {
            songsDao.upsert(song.toEntity())
            cachesDao.insert(CacheEntity(song.id, filePath, size, System.currentTimeMillis()))
        }
    }

    suspend fun removeCache(songId: String) = cachesDao.remove(songId)

    suspend fun clearCacheRows() = cachesDao.clearAll()

    // ---- Playlists ----
    fun observePlaylists(): Flow<List<PlaylistEntity>> = playlistDao.observePlaylists()

    suspend fun createPlaylist(name: String): Long =
        playlistDao.insert(PlaylistEntity(0, name, System.currentTimeMillis()))

    suspend fun renamePlaylist(id: Long, name: String) = playlistDao.rename(id, name)

    suspend fun deletePlaylist(id: Long) {
        db.withTransaction {
            playlistDao.deleteSongsFor(id)
            playlistDao.delete(id)
        }
    }

    fun observePlaylistSongs(playlistId: Long): Flow<List<Song>> =
        playlistDao.observePlaylistSongs(playlistId).map { list -> list.map { Song.fromEntity(it) } }

    suspend fun addToPlaylist(playlistId: Long, song: Song) {
        db.withTransaction {
            songsDao.upsert(song.toEntity())
            playlistDao.addSong(
                PlaylistSongCrossRef(playlistId, song.id, System.currentTimeMillis())
            )
        }
    }

    suspend fun removeFromPlaylist(playlistId: Long, songId: String) =
        playlistDao.removeSong(playlistId, songId)
}
