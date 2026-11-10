package com.shubh.song.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.shubh.song.data.db.entity.CacheEntity
import com.shubh.song.data.db.entity.DownloadEntity
import com.shubh.song.data.db.entity.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadsDao {

    @Query("SELECT EXISTS(SELECT 1 FROM downloads WHERE songId = :songId)")
    fun isDownloaded(songId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: DownloadEntity)

    @Query("DELETE FROM downloads WHERE songId = :songId")
    suspend fun remove(songId: String)

    @Query(
        """
        SELECT s.* FROM songs s
        INNER JOIN downloads d ON s.id = d.songId
        ORDER BY d.addedAt DESC
        """
    )
    fun observeDownloadedSongs(): Flow<List<SongEntity>>

    @Query("SELECT songId FROM downloads ORDER BY addedAt DESC")
    fun observeDownloadedIds(): Flow<List<String>>

    @Query("SELECT filePath FROM downloads WHERE songId = :songId")
    suspend fun filePathOf(songId: String): String?
}

@Dao
interface CachesDao {

    @Query("SELECT EXISTS(SELECT 1 FROM cached_songs WHERE songId = :songId)")
    fun isCached(songId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cache: CacheEntity)

    @Query("DELETE FROM cached_songs WHERE songId = :songId")
    suspend fun remove(songId: String)

    @Query(
        """
        SELECT s.* FROM songs s
        INNER JOIN cached_songs c ON s.id = c.songId
        ORDER BY c.addedAt DESC
        """
    )
    fun observeCachedSongs(): Flow<List<SongEntity>>

    @Query("SELECT filePath FROM cached_songs WHERE songId = :songId")
    suspend fun filePathOf(songId: String): String?

    @Query("SELECT filePath FROM cached_songs")
    suspend fun allFilePaths(): List<String>

    @Query("DELETE FROM cached_songs")
    suspend fun clearAll()
}
