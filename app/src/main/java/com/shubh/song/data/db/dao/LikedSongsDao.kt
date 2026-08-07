package com.shubh.song.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.shubh.song.data.db.entity.LikedSongEntity
import com.shubh.song.data.db.entity.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LikedSongsDao {

    @Query("SELECT EXISTS(SELECT 1 FROM liked_songs WHERE songId = :songId)")
    fun isLiked(songId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLike(like: LikedSongEntity)

    @Query("DELETE FROM liked_songs WHERE songId = :songId")
    suspend fun removeLike(songId: String)

    @Query(
        """
        SELECT s.* FROM songs s
        INNER JOIN liked_songs l ON s.id = l.songId
        ORDER BY l.addedAt DESC
        """
    )
    fun observeLikedSongs(): Flow<List<SongEntity>>

    @Query("SELECT songId FROM liked_songs ORDER BY addedAt DESC")
    fun observeLikedIds(): Flow<List<String>>
}
