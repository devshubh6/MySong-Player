package com.shubh.song.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Per-track playback summary, one row per song, refreshed incrementally as
 * the track is played. Powers the "My Stats & Recap" dashboard.
 *
 * A row is created on the first listen and updated on every commit — the
 * commit writes a delta of elapsed listening (seconds) and bumps the play
 * counter exactly once per listen session (>= 5 seconds of play).
 */
@Entity(tableName = "playback_history_table")
data class PlaybackStatsEntity(
    @PrimaryKey val trackId: String,
    /** How many times this track was actually played (session = >= 5 s). */
    val playCount: Int = 0,
    /** Total wall-clock seconds the user spent listening to this track. */
    val totalSeconds: Long = 0L,
    /** System time (epoch millis) of the most recent play of this track. */
    val lastPlayedAt: Long = 0L,
    // Denormalized snapshot so the dashboard needs no joins.
    val songName: String = "",
    val artistName: String = "",
    val imageUrl: String = ""
)

/**
 * Calendar-day listening aggregate (keyed by "yyyy-MM-dd" local date) so the
 * 7-day activity bars can be computed exactly, without scanning the whole
 * playback history.
 */
@Entity(tableName = "playback_day_table")
data class PlayedDayEntity(
    @PrimaryKey val dateKey: String,
    val seconds: Long = 0L,
    val plays: Int = 0
)