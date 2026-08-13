package com.shubh.song.data.repository

import com.shubh.song.MySongApp
import com.shubh.song.data.db.AppDatabase
import com.shubh.song.data.db.entity.PlaybackStatsEntity
import com.shubh.song.data.db.entity.PlayedDayEntity
import com.shubh.song.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Incremental playback-statistics writer.
 *
 * [record] is called by the playback engine whenever a chunk of listening
 * is committed (pause, track switch, periodic flush). It merges the delta
 * into the per-track row and the calendar-day row:
 *  - [deltaSeconds] adds wall-clock listening seconds to both;
 *  - [countedPlay] bumps the play counter exactly once per listen session
 *    (the engine decides when a session qualifies, >= 5 s of play).
 *
 * All commits go through a single-threaded IO scope so read-modify-write
 * pairs can never interleave.
 */
object StatsRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db: AppDatabase by lazy { AppDatabase.get(MySongApp.instance) }
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun record(song: Song, deltaSeconds: Long, countedPlay: Boolean) {
        if (deltaSeconds <= 0L) return
        scope.launch {
            try {
                val dao = db.playbackStatsDao()
                val now = System.currentTimeMillis()
                val base = dao.statsFor(song.id) ?: PlaybackStatsEntity(
                    trackId = song.id,
                    songName = song.name,
                    artistName = song.artists,
                    imageUrl = song.imageUrl
                )
                dao.upsert(
                    base.copy(
                        playCount = base.playCount + if (countedPlay) 1 else 0,
                        totalSeconds = base.totalSeconds + deltaSeconds,
                        lastPlayedAt = now,
                        songName = song.name,
                        artistName = song.artists,
                        imageUrl = song.imageUrl
                    )
                )

                val key = dayFormat.format(Date(now))
                val day = dao.dayFor(key) ?: PlayedDayEntity(dateKey = key)
                dao.upsertDay(
                    day.copy(
                        seconds = day.seconds + deltaSeconds,
                        plays = day.plays + if (countedPlay) 1 else 0
                    )
                )
            } catch (t: Throwable) {
                // Stats writes must never disturb playback.
            }
        }
    }

    /** Full factory reset of the listening history (stats screen action). */
    fun clearAll() {
        scope.launch {
            try {
                db.playbackStatsDao().clearStats()
                db.playbackStatsDao().clearDays()
            } catch (_: Throwable) {
            }
        }
    }
}