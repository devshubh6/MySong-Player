package com.shubh.song.data.api

import com.shubh.song.data.api.dto.LrcLibResponseDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * LRC Lib public API surface (https://lrclib.net/api).
 *
 * The precise GET endpoint takes the 4 query parameters that uniquely
 * identify a release: artist name, track name, album name and duration in
 * whole seconds. The search endpoint is used as a fuzzy fallback when the
 * exact match misses.
 */
interface LrcLibApi {

    @GET("api/get")
    suspend fun getByMetadata(
        @Query("artist_name") artistName: String,
        @Query("track_name") trackName: String,
        @Query("album_name") albumName: String? = null,
        @Query("duration") durationSec: Int? = null
    ): Response<LrcLibResponseDto>

    @GET("api/search")
    suspend fun search(
        @Query("q") query: String? = null,
        @Query("track_name") trackName: String? = null,
        @Query("artist_name") artistName: String? = null,
        @Query("album_name") albumName: String? = null,
        @Query("duration") durationSec: Int? = null,
        @Query("limit") limit: Int = 10
    ): Response<List<LrcLibResponseDto>>
}