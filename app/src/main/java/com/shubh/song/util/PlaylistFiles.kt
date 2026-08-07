package com.shubh.song.util

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.shubh.song.data.api.dto.DownloadDto
import com.shubh.song.data.db.entity.PlaylistEntity
import com.shubh.song.data.model.Song

/**
 * Playlist backup tooling: exports a playlist's full song data to `.json`
 * (lossless — all stream qualities are preserved) or `.m3u` (with ambient
 * #MYSONG metadata so an import can rebuild exact streams). Import accepts
 * either our JSON dump or an exported .m3u.
 */
object PlaylistFiles {

    data class SongDump(
        val id: String,
        val name: String,
        val artists: String,
        val album: String,
        val imageUrl: String,
        val durationSec: Long,
        val year: String,
        val language: String,
        val playCount: String,
        val url: String,
        val downloadUrls: List<DownloadDto>
    )

    data class PlaylistDump(
        val name: String,
        val songs: List<SongDump>
    )

    private val gson = Gson()

    fun dumpOf(playlists: List<Pair<PlaylistEntity, List<Song>>>): List<PlaylistDump> =
        playlists.map { (entity, songs) ->
            PlaylistDump(
                name = entity.name,
                songs = songs.map { it.toDump() }
            )
        }

    fun toJson(dumps: List<PlaylistDump>): String = gson.toJson(dumps)

    fun toM3u(dumps: List<PlaylistDump>): String {
        val sb = StringBuilder("#EXTM3U\n")
        for (dump in dumps) {
            if (dump.songs.isEmpty()) continue
            sb.append("\n#PLAYLIST ").append(dump.name).append('\n')
            for (song in dump.songs) {
                val extinf = "#EXTINF:${song.durationSec},${song.artists} - ${song.name}"
                sb.append(extinf).append('\n')
                val streamUrl = song.downloadUrls.lastOrNull { !it.url.isNullOrBlank() }?.url
                    ?: song.url
                sb.append("#EXTMYSONG ").append(song.id).append('\n')
                sb.append(streamUrl.ifBlank { "https://example.com" }).append('\n')
            }
        }
        return sb.toString()
    }

    /** Parses `.json` produced by [toJson]. Returns null when the format is wrong. */
    fun parseJson(text: String): List<PlaylistDump>? {
        return try {
            val element = JsonParser.parseString(text)
            if (!element.isJsonArray) return null
            val type = object : TypeToken<List<PlaylistDump>>() {}.type
            gson.fromJson<List<PlaylistDump>>(element, type)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parses an `.m3u` file. Only lines with the #EXTMYSONG marker (written by
     * our exporter) carry real song data; entries without it are skipped so
     * we never create silent phantom tracks.
     */
    fun parseM3u(text: String): List<PlaylistDump>? {
        val names = mutableListOf<String>()
        val songs = mutableListOf<MutableList<SongDump>>()
        for (line in text.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#PLAYLIST ")) {
                names.add(trimmed.removePrefix("#PLAYLIST ").trim())
                songs.add(mutableListOf())
            }
        }
        if (names.isEmpty()) return null
        val dumps = names.mapIndexed { i, name -> PlaylistDump(name, songs[i]) }
        var current = -1
        for (line in text.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#PLAYLIST ")) {
                current++
                continue
            }
            if (current >= 0 && trimmed.startsWith("#EXTMYSONG ")) {
                val id = trimmed.removePrefix("#EXTMYSONG ").trim()
                if (id.isNotBlank()) songs[current].add(SongDump(id = id, name = "", artists = "", album = "", imageUrl = "",
                    durationSec = 0, year = "", language = "", playCount = "", url = "", downloadUrls = emptyList()))
            }
        }
        return dumps
    }
}

private fun Song.toDump(): PlaylistFiles.SongDump =
    PlaylistFiles.SongDump(
        id = id,
        name = name,
        artists = artists,
        album = album,
        imageUrl = imageUrl,
        durationSec = durationSec,
        year = year,
        language = language,
        playCount = playCount,
        url = url,
        downloadUrls = downloadUrls
    )