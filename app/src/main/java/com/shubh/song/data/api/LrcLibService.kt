package com.shubh.song.data.api

import com.shubh.song.data.api.dto.LrcLibResponseDto
import com.shubh.song.data.model.Lyrics
import com.shubh.song.data.model.Song
import com.shubh.song.ui.lyrics.LrcParser
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * High-level client for LRC Lib synced lyrics.
 *
 * Strategy (all calls are network-only; callers apply their own timeouts &
 * offline guards):
 *  1. precise `/api/get?artist_name&track_name&album_name&duration`
 *  2. if that misses, a fuzzy `/api/search` (by track name / artist)
 *  3. both `syncedLyrics` (karaoke-quality LRC) and `plainLyrics` accepted
 *
 * Returns [Lyrics] with `synced == true` whenever a usable LRC line set was
 * found, plain text otherwise, and null when the song has no lyrics at all.
 */
object LrcLibService {

    private const val BASE_URL = "https://lrclib.net/"

    private val api: LrcLibApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(ApiClient.okHttp)
            .addConverterFactory(GsonConverterFactory.create(ApiClient.gson))
            .build()
            .create(LrcLibApi::class.java)
    }

    /** Time budget for the whole fetch (exact + search fallback). */
    private const val FETCH_TIMEOUT_MS = 12_000L

    suspend fun fetchLyrics(song: Song): Lyrics? = withTimeoutOrNull(FETCH_TIMEOUT_MS) {
        val track = cleanTitle(song.name)
        val artists = artistCandidates(song.artists)
        val album = song.album.takeIf { it.isNotBlank() }
        val durationSec = song.durationSec.coerceAtLeast(1).toInt()

        // 1. exact metadata match (try each of the up-to-3 artist candidates)
        for (artist in artists) {
            val dto = toBody(
                api.getByMetadata(
                    artistName = artist,
                    trackName = track,
                    albumName = album,
                    durationSec = durationSec
                )
            )
            if (dto != null) {
                toLyrics(dto)?.let { return@withTimeoutOrNull it }
            }
        }

        // 2. fuzzy search fallback: same title first, then same artist
        val hits = api.search(trackName = track, artistName = artists.firstOrNull(), limit = 8)
            .body()
            ?.filter { it.instrumental != true }
            .orEmpty()
        if (hits.isNotEmpty()) {
            sortedCandidates(hits, track, artists).forEach { hit ->
                toLyrics(hit)?.let { return@withTimeoutOrNull it }
            }
        }

        null
    }

    // ---- helpers ----

    private fun toBody(response: Response<LrcLibResponseDto>): LrcLibResponseDto? =
        if (response.isSuccessful) response.body() else null

    private fun toLyrics(dto: LrcLibResponseDto): Lyrics? {
        dto.syncedLyrics?.takeIf { it.isNotBlank() }?.let { lrc ->
            val lines = LrcParser.parse(lrc)
            if (lines.isNotEmpty()) return Lyrics(lines, synced = true, copyright = null)
        }
        dto.plainLyrics?.takeIf { it.isNotBlank() }?.let { plain ->
            return Lyrics.parse(plain)
        }
        return null
    }

    /** Best-effort rank: exact same title → same artist → longest track name. */
    private fun sortedCandidates(
        hits: List<LrcLibResponseDto>,
        track: String,
        artists: List<String>
    ): List<LrcLibResponseDto> =
        hits.sortedWith(
            compareByDescending<LrcLibResponseDto> {
                it.trackName?.equals(track, ignoreCase = true) == true
            }.thenByDescending { hit ->
                artists.any { a -> hit.artistName?.contains(a, ignoreCase = true) == true }
            }.thenByDescending { it.trackName?.length ?: 0 }
        )

    /** "Title (feat. Artist)" → "Title"; strips parenthetical suffixes. */
    private fun cleanTitle(title: String): String {
        var out = title
        val paren = out.indexOf('(')
        if (paren > 0) out = out.substring(0, paren)
        val bracket = out.indexOf('[')
        if (bracket > 0) out = out.substring(0, bracket)
        return out.trim().take(120)
    }

    /** "A; B / A feat. B" → up to 3 artist candidates (without features). */
    private fun artistCandidates(artists: String): List<String> {
        val raw = artists.replace(" & ", ";")
            .replace(",", ";")
            .replace("/", ";")
        val parts = raw.split(";")
            .map { it.trim().removeSuffix(".").trim() }
            .filter { it.isNotBlank() && it.length <= 80 }
        val names = parts.map { it.substringBefore("feat.", it) }
            .map { it.substringBefore("Feat.", it) }
            .map { it.trim() }
            .filter { it.isNotBlank() }
        return names.distinct().take(3)
    }
}