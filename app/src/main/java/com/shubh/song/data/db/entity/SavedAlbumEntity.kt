package com.shubh.song.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.reflect.TypeToken
import com.shubh.song.data.api.dto.DownloadDto
import com.shubh.song.data.model.Song

/**
 * A bookmarked album: all its tracks are stored locally (see
 * [SavedAlbumTrackEntity]) so the album appears instantly in the Library
 * without any network round-trip.
 */
@Entity(tableName = "saved_albums")
data class SavedAlbumEntity(
    @PrimaryKey val albumId: String,
    val title: String,
    val artist: String,
    val coverUrl: String,
    val trackCount: Int,
    val savedAt: Long
)

/** One track of a saved album, keyed by its position inside the album. */
@Entity(tableName = "saved_album_tracks", primaryKeys = ["albumId", "trackIndex"])
data class SavedAlbumTrackEntity(
    val albumId: String,
    val trackIndex: Int,
    val songId: String,
    val title: String,
    val artists: String,
    val album: String,
    val durationSec: Long,
    val imageUrl: String,
    val audioUrl: String,
    val downloadUrlsJson: String
) {
    fun toSong(): Song =
        Song(
            id = songId,
            name = title,
            artists = artists,
            album = album,
            imageUrl = imageUrl,
            durationSec = durationSec,
            year = "",
            language = "",
            playCount = "0",
            url = audioUrl,
            downloadUrls = decodeDownloadUrls()
        )

    /** Restores the per-track stream URLs persisted when the album was saved. */
    private fun decodeDownloadUrls(): List<DownloadDto> {
        if (downloadUrlsJson.isBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<DownloadDto>>() {}.type
            GSON.fromJson(downloadUrlsJson, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        private val GSON = com.google.gson.Gson()
    }
}