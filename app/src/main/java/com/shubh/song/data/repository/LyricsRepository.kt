package com.shubh.song.data.repository

import com.shubh.song.MySongApp
import com.shubh.song.data.api.LrcLibService
import com.shubh.song.data.model.Lyrics
import com.shubh.song.data.model.Song
import com.shubh.song.util.NetworkObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Multi-source lyrics engine.
 *
 * Primary source: the JioSaavn lyrics endpoint (via [MusicRepository.fetchLyrics],
 * which also carries a lyrics.ovh fallback). If that yields nothing usable, the
 * LRC Lib API ([LrcLibService.fetchLyrics]) is queried — its synced `.lrc`
 * timestamps power the karaoke auto-scroll RecyclerView, synchronized against
 * the ExoPlayer position. Every call is offline-safe and never throws.
 */
object LyricsRepository {

    /**
     * Best-effort lyrics lookup for [song]. Tries the primary source first,
     * then the fallback, and finally gives up silently (returns null).
     */
    suspend fun fetch(song: Song): Lyrics? {
        val online = NetworkObserver.isOnlineNow()

        // 1. Primary — JioSaavn lyrics endpoint (plain or synced text).
        if (online) {
            primarySource(song)?.let { return it }
        }

        // 2. Fallback — LRC Lib (synced LRC for karaoke mode).
        if (online) {
            fallbackSource(song)?.let { return it }
        }

        return null
    }

    private suspend fun primarySource(song: Song): Lyrics? =
        withContext(Dispatchers.IO) {
            runCatching { MySongApp.instance.musicRepository.fetchLyrics(song) }
                .getOrNull()
        }?.takeIf { it.lines.isNotEmpty() }

    private suspend fun fallbackSource(song: Song): Lyrics? =
        withContext(Dispatchers.IO) {
            runCatching { LrcLibService.fetchLyrics(song) }
                .getOrNull()
        }?.takeIf { it.lines.isNotEmpty() }
}