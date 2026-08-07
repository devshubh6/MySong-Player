package com.shubh.song.media

import java.util.concurrent.ConcurrentHashMap

/**
 * Tiny synchronous mirror of the "liked" flags so the media notification can
 * paint the correct heart icon off the render thread without Room I/O.
 * Kept in sync from the player screen and from the notification's own like
 * action, and re-seeded from the database whenever a notification rebuilds.
 */
object NotifLike {

    private val cache = ConcurrentHashMap<String, Boolean>()

    /** Returns the pessimistic default (false) when the flag is not known yet. */
    fun isLiked(songId: String?): Boolean = songId?.let { cache[it] } ?: false

    fun set(songId: String, liked: Boolean) {
        if (liked) cache[songId] = true else cache.remove(songId)
    }

    fun clear() = cache.clear()
}