package com.shubh.song.data.api.dto

import com.google.gson.annotations.SerializedName

/**
 * Payload from the LRC Lib API (https://lrclib.net), documented at
 * https://lrclib.net/docs. Both `/api/get` and `/api/search` return this
 * shape; only fields used by the karaoke lyrics pipeline are mapped.
 */
data class LrcLibResponseDto(
    val id: Long = 0,
    val trackName: String? = null,
    val artistName: String? = null,
    val albumName: String? = null,
    val duration: Double = 0.0,
    val instrumental: Boolean = false,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,

    // `/api/search` results are wrapped by an unpaginated JSON array, while
    // `/api/get` returns the object itself. Retrofit+Gson maps both.
    @SerializedName("artist")
    val artistFallback: String? = null
)