package com.shubh.song.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.shubh.song.data.db.entity.SongEntity

@Dao
interface SongsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(song: SongEntity)

    @Query("SELECT * FROM songs WHERE id = :songId")
    suspend fun byId(songId: String): SongEntity?
}
