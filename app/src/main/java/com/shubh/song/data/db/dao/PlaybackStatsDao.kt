package com.shubh.song.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.shubh.song.data.db.entity.PlaybackStatsEntity
import com.shubh.song.data.db.entity.PlayedDayEntity
import kotlinx.coroutines.flow.Flow

/** Aggregated artist row for the "top artist" card on the stats screen. */
data class TopArtistRow(
    val name: String,
    val playCount: Int
)

/**
 * Room access for the listening-stats dashboard: per-track history plus the
 * per-day aggregates that power the weekly activity chart.
 */
@Dao
interface PlaybackStatsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stats: PlaybackStatsEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDay(day: PlayedDayEntity)

    /** Top tracks first by play count, most recent ties first. */
    @Query("SELECT * FROM playback_history_table ORDER BY playCount DESC, lastPlayedAt DESC LIMIT 50")
    fun observeStats(): Flow<List<PlaybackStatsEntity>>

    @Query("SELECT * FROM playback_history_table WHERE trackId = :trackId LIMIT 1")
    suspend fun statsFor(trackId: String): PlaybackStatsEntity?

    @Query("SELECT * FROM playback_day_table WHERE dateKey = :dateKey LIMIT 1")
    suspend fun dayFor(dateKey: String): PlayedDayEntity?

    @Query("SELECT COALESCE(SUM(totalSeconds), 0) FROM playback_history_table")
    fun observeTotalSeconds(): Flow<Long>

    @Query("SELECT COALESCE(SUM(playCount), 0) FROM playback_history_table")
    fun observeTotalPlays(): Flow<Long>

    @Query(
        "SELECT artistName AS name, SUM(playCount) AS playCount FROM playback_history_table " +
            "WHERE artistName IS NOT NULL AND artistName != '' " +
            "GROUP BY artistName ORDER BY playCount DESC, MAX(lastPlayedAt) DESC LIMIT 1"
    )
    fun observeTopArtist(): Flow<TopArtistRow?>

    /** One row per day for the weekly chart (keys are local "yyyy-MM-dd"). */
    @Query(
        "SELECT * FROM playback_day_table WHERE dateKey >= :fromKey " +
            "ORDER BY dateKey ASC LIMIT 14"
    )
    fun observeDays(fromKey: String): Flow<List<PlayedDayEntity>>

    @Query("DELETE FROM playback_history_table")
    suspend fun clearStats()

    @Query("DELETE FROM playback_day_table")
    suspend fun clearDays()
}