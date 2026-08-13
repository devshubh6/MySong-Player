package com.shubh.song.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.shubh.song.data.db.entity.SavedAlbumEntity
import com.shubh.song.data.db.entity.SavedAlbumTrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedAlbumDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbum(album: SavedAlbumEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<SavedAlbumTrackEntity>)

    @Query("DELETE FROM saved_albums WHERE albumId = :albumId")
    suspend fun deleteAlbum(albumId: String)

    @Query("DELETE FROM saved_album_tracks WHERE albumId = :albumId")
    suspend fun deleteTracks(albumId: String)

    @Query("SELECT * FROM saved_albums ORDER BY savedAt DESC")
    fun observeAlbums(): Flow<List<SavedAlbumEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM saved_albums WHERE albumId = :albumId)")
    fun isSaved(albumId: String): Flow<Boolean>

    @Query(
        """
        SELECT * FROM saved_album_tracks
        WHERE albumId = :albumId
        ORDER BY trackIndex ASC
        """
    )
    fun observeTracks(albumId: String): Flow<List<SavedAlbumTrackEntity>>
}