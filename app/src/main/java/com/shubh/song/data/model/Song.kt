package com.shubh.song.data.model

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.shubh.song.data.api.dto.DownloadDto
import com.shubh.song.data.api.dto.SongDto
import com.shubh.song.data.db.entity.SongEntity
import com.shubh.song.util.Constants

data class Song(
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
) {
    fun bestDownloadUrl(quality: String): String? {
        val wanted = "$quality" + "kbps"
        return downloadUrls.firstOrNull { it.quality == wanted }?.url
            ?: downloadUrls.lastOrNull { !it.url.isNullOrBlank() }?.url
    }

    fun bestImage(size: String = "500x500"): String =
        imageUrl

    fun toEntity(): SongEntity =
        SongEntity(
            id = id,
            name = name,
            artists = artists,
            album = album,
            image = imageUrl,
            durationSec = durationSec,
            year = year,
            language = language,
            playCount = playCount,
            url = url,
            downloadUrlsJson = DOWNLOADS_GSON.toJson(downloadUrls),
            addedAt = System.currentTimeMillis()
        )

    companion object {
        private val DOWNLOADS_GSON = Gson()

        val EMPTY = Song(
            id = "", name = "", artists = "", album = "", imageUrl = "",
            durationSec = 0, year = "", language = "", playCount = "",
            url = "", downloadUrls = emptyList()
        )

        fun from(song: SongDto): Song {
            val imageUrl = song.image.orEmpty()
                .filter { !it.url.isNullOrBlank() }
                .let { list ->
                    list.firstOrNull { it.quality == "500x500" }
                        ?: list.firstOrNull { it.quality == "150x150" }
                        ?: list.lastOrNull()
                }?.url.orEmpty()

            return Song(
                id = song.id.orEmpty(),
                name = song.name.orEmpty(),
                artists = song.artists?.primary.orEmpty().joinToString(", ") { it.name.orEmpty() }
                    .ifBlank { song.artists?.all.orEmpty().joinToString(", ") { it.name.orEmpty() } },
                album = song.album?.name.orEmpty(),
                imageUrl = imageUrl,
                durationSec = (song.duration as? Number)?.toLong() ?: 0L,
                year = song.year.orEmpty(),
                language = song.language.orEmpty(),
                playCount = song.playCount?.toString().orEmpty(),
                url = song.url.orEmpty(),
                downloadUrls = song.downloadUrl.orEmpty().filter { !it.url.isNullOrBlank() }
            )
        }

        fun fromEntity(entity: SongEntity): Song {
            val urls: List<DownloadDto> = try {
                val type = object : TypeToken<List<DownloadDto>>() {}.type
                DOWNLOADS_GSON.fromJson(entity.downloadUrlsJson, type)
            } catch (e: Exception) {
                emptyList()
            }
            return Song(
                id = entity.id,
                name = entity.name,
                artists = entity.artists,
                album = entity.album,
                imageUrl = entity.image,
                durationSec = entity.durationSec,
                year = entity.year,
                language = entity.language,
                playCount = entity.playCount,
                url = entity.url,
                downloadUrls = urls
            )
        }
    }
}