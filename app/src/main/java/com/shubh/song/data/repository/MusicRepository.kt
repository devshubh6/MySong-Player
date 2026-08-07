package com.shubh.song.data.repository

import com.shubh.song.data.api.ApiClient
import com.shubh.song.data.api.JioSaavnApi
import com.shubh.song.data.api.dto.AlbumDto
import com.shubh.song.data.api.dto.ArtistDto
import com.shubh.song.data.api.dto.PlaylistDto
import com.shubh.song.data.model.Lyrics
import com.shubh.song.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.URLEncoder

data class FeedRow(
    val title: String,
    val kind: FeedKinds,
    val songs: List<Song> = emptyList(),
    val albums: List<AlbumDto> = emptyList(),
    val artists: List<ArtistDto> = emptyList(),
    val playlists: List<PlaylistDto> = emptyList()
)

enum class FeedKinds {
    TRENDING, TOP_CHARTS, FEATURED_ALBUMS, POPULAR_ARTISTS, TOP_PLAYLISTS, EXTRA
}

class FeedData(
    val trending: FeedRow,
    val topCharts: FeedRow,
    val featuredAlbums: FeedRow,
    val popularArtists: FeedRow,
    val topPlaylists: FeedRow,
    val extra: List<FeedRow> = emptyList()
)

class MusicRepository(private val api: JioSaavnApi = ApiClient.api) {

    suspend fun searchSongs(query: String, limit: Int = 20): List<Song> =
        safeCall {
            api.searchSongs(query, 0, limit).data?.results.orEmpty()
                .mapNotNull { Song.from(it) }
        }.orEmpty()

    /** Paged variant used by the endless home feed. */
    suspend fun searchSongsPaged(query: String, page: Int, limit: Int = 20): List<Song> =
        safeCall {
            api.searchSongs(query, page, limit).data?.results.orEmpty()
                .mapNotNull { Song.from(it) }
        }.orEmpty()

    suspend fun fetchSong(id: String): Song? {
        val fresh = safeCall { api.songsById(id).data.orEmpty() } ?: emptyList()
        return fresh.firstNotNullOfOrNull { Song.from(it) }
    }

    suspend fun fetchSongs(ids: List<String>): List<Song> {
        if (ids.isEmpty()) return emptyList()
        return safeCall {
            api.songsById(ids.joinToString(",")).data.orEmpty().mapNotNull { Song.from(it) }
        }.orEmpty()
    }

    suspend fun searchSuggestions(query: String): List<Song> =
        searchSongs(query, limit = 8)

    /** Best-matching artist for a search query (used by the top-artist card). */
    suspend fun topArtist(query: String): ArtistDto? {
        val q = query.trim()
        if (q.isBlank()) return null
        val results = safeCall { api.searchArtists(q, 0, 10).data?.results.orEmpty() }.orEmpty()
        if (results.isEmpty()) return null
        val wanted = q.lowercase()
        return results.firstOrNull { artist ->
            artist.name?.trim()?.lowercase()?.contains(wanted) == true
        } ?: results.first()
    }

    /**
     * Similar-track discovery for the endless "Auto-Mix": searches the primary
     * artist's catalogue, falling back to the album name.
     */
    suspend fun similarSongs(seed: Song, limit: Int = 10): List<Song> {
        val artist = seed.artists.substringBefore(",").trim()
        val byArtist = if (artist.isNotBlank()) searchSongs("$artist songs", limit) else emptyList()
        if (byArtist.isNotEmpty()) return byArtist
        val byAlbum = if (seed.album.isNotBlank()) searchSongs(seed.album, limit) else emptyList()
        return byAlbum
    }

    suspend fun albumSongs(albumId: String): List<Song> =
        safeCall { api.albumById(albumId).data?.songs?.mapNotNull { Song.from(it) } }.orEmpty()

    suspend fun artistSongs(artistId: String): List<Song> =
        safeCall { api.artistSongs(artistId).data?.songs?.mapNotNull { Song.from(it) } }.orEmpty()

    suspend fun playlistSongs(playlistId: String): List<Song> =
        safeCall { api.playlistById(playlistId).data?.songs?.mapNotNull { Song.from(it) } }.orEmpty()

    suspend fun artistDetail(artistId: String): ArtistDto? =
        safeCall { api.artistById(artistId).data }

    /**
     * Fetches lyrics for a song. Tries the JioSaavn proxy first
     * (/api/lyrics/{id}), then falls back to lyrics.ovh by artist + title.
     * Never throws; returns null when neither source has lyrics.
     */
    suspend fun fetchLyrics(song: Song): Lyrics? {
        val proxyText = safeCall { api.lyricsById(song.id).data?.lyrics }
        if (!proxyText.isNullOrBlank()) {
            return Lyrics.parse(proxyText, null)
        }
        val fallback = fetchLyricsFromOvh(song)
        return Lyrics.parse(fallback, null)
    }

    private suspend fun fetchLyricsFromOvh(song: Song): String? =
        withContext(Dispatchers.IO) {
            val artist = song.artists.substringBefore(",").trim()
            val title = song.name.trim()
            if (artist.isBlank() || title.isBlank()) return@withContext null
            val url = "https://api.lyrics.ovh/v1/${encode(artist)}/${encode(title)}"
            val request = okhttp3.Request.Builder().url(url).build()
            try {
                ApiClient.okHttp.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val body = response.body?.string() ?: return@withContext null
                    val json = org.json.JSONObject(body)
                    json.optString("lyrics").takeIf { it.isNotBlank() }
                }
            } catch (_: Exception) {
                null
            }
        }

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8")

    /** Builds the five home-carousels from curated searches (no dedicated home endpoint). */
    suspend fun homeFeed(languages: Set<String>, country: String): FeedData = coroutineScope {
        val usedLangs = languages.take(3)

        val trending = async { searchTrending(usedLangs) }
        val charts = async { searchCharts(usedLangs) }
        val albums = async { searchAlbums(usedLangs) }
        val artists = async { searchArtists(usedLangs) }
        val playlists = async { searchPlaylists(usedLangs) }
        val party = async { searchCurated("party hits", usedLangs) }
        val workout = async { searchCurated("workout energy", usedLangs) }
        val romantic = async { searchCurated("romantic love songs", usedLangs) }
        val chill = async { searchCurated("chill lofi", usedLangs) }
        val devotional = async { searchCurated("devotional bhajans", usedLangs) }
        val classics = async { searchCurated("old classics", usedLangs) }
        val remixes = async { searchCurated("top remix mashup", usedLangs) }

        FeedData(
            trending = trending.await(),
            topCharts = charts.await(),
            featuredAlbums = albums.await(),
            popularArtists = artists.await(),
            topPlaylists = playlists.await(),
            extra = listOf(
                party.await(),
                workout.await(),
                romantic.await(),
                chill.await(),
                devotional.await(),
                classics.await(),
                remixes.await()
            )
        )
    }

    /** Curated horizontal row mixing per-language and generic theme queries. */
    private suspend fun searchCurated(theme: String, langs: List<String>): FeedRow {
        val songs = mutableListOf<Song>()
        for (lang in langs) {
            songs.addAll(
                (safeCall { api.searchSongs("$lang $theme", 0, 12).data?.results.orEmpty() }
                    ?: emptyList()).mapNotNull { Song.from(it) }
            )
        }
        songs.addAll(
            (safeCall { api.searchSongs(theme, 0, 12).data?.results.orEmpty() }
                ?: emptyList()).mapNotNull { Song.from(it) }
        )
        return FeedRow(
            title = theme,
            kind = FeedKinds.EXTRA,
            songs = songs.dedupe().take(12)
        )
    }

    private suspend fun searchTrending(langs: List<String>): FeedRow {
        val results = mutableListOf<Song>()
        for (lang in langs) {
            results.addAll(
                (safeCall { api.searchSongs("$lang songs", 0, 10).data?.results.orEmpty() }
                    ?: emptyList()).mapNotNull { Song.from(it) }
            )
        }
        return FeedRow(
            title = "Trending Now",
            FeedKinds.TRENDING,
            songs = results.dedupe().filterBy(langs)
        )
    }

    private suspend fun searchCharts(langs: List<String>): FeedRow {
        val playlists = mutableListOf<PlaylistDto>()
        for (lang in langs) {
            playlists.addAll(
                safeCall { api.searchPlaylists("$lang top charts", 0, 10).data?.results.orEmpty() }
                    ?: emptyList()
            )
        }
        for (extra in listOf("top charts 50", "global top charts")) {
            playlists.addAll(
                safeCall { api.searchPlaylists(extra, 0, 8).data?.results.orEmpty() }
                    ?: emptyList()
            )
        }
        return FeedRow(
            title = "Top Charts",
            FeedKinds.TOP_CHARTS,
            playlists = playlists.distinctBy { it.id }.take(12)
        )
    }

    private suspend fun searchAlbums(langs: List<String>): FeedRow {
        val albums = mutableListOf<AlbumDto>()
        for (lang in langs) {
            albums.addAll(
                safeCall { api.searchAlbums("$lang new releases", 0, 10).data?.results.orEmpty() }
                    ?: emptyList()
            )
        }
        albums.addAll(
            safeCall { api.searchAlbums("featured albums", 0, 8).data?.results.orEmpty() }
                ?: emptyList()
        )
        return FeedRow(
            title = "Featured Albums",
            FeedKinds.FEATURED_ALBUMS,
            albums = albums.distinctBy { it.id }.take(12)
        )
    }

    private suspend fun searchArtists(langs: List<String>): FeedRow {
        val artists = mutableListOf<ArtistDto>()
        for (lang in langs) {
            artists.addAll(
                safeCall { api.searchArtists("top $lang artists", 0, 10).data?.results.orEmpty() }
                    ?: emptyList()
            )
        }
        artists.addAll(
            safeCall { api.searchArtists("top artists", 0, 10).data?.results.orEmpty() }
                ?: emptyList()
        )
        return FeedRow(
            title = "Popular Artists",
            FeedKinds.POPULAR_ARTISTS,
            artists = artists.distinctBy { it.id }.take(12)
        )
    }

    private suspend fun searchPlaylists(langs: List<String>): FeedRow {
        val playlists = mutableListOf<PlaylistDto>()
        for (lang in langs) {
            playlists.addAll(
                safeCall { api.searchPlaylists("$lang playlists", 0, 10).data?.results.orEmpty() }
                    ?: emptyList()
            )
        }
        playlists.addAll(
            safeCall { api.searchPlaylists("top playlists", 0, 10).data?.results.orEmpty() }
                ?: emptyList()
        )
        return FeedRow(
            title = "Top Playlists",
            FeedKinds.TOP_PLAYLISTS,
            playlists = playlists.distinctBy { it.id }.take(12)
        )
    }

    private fun List<Song>.filterBy(langs: List<String>): List<Song> {
        val result = filter { it.language in langs }
        return if (result.isNotEmpty()) result else this
    }

    private fun List<Song>.dedupe(): List<Song> = distinctBy { it.id }.take(20)
}

suspend fun <T> safeCall(block: suspend () -> T?): T? =
    try {
        block()
    } catch (e: Exception) {
        null
    }