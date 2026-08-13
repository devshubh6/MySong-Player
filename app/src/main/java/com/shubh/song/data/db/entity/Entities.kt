package com.shubh.song.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: String,
    val name: String,
    val artists: String,
    val album: String,
    val image: String,
    val durationSec: Long,
    val year: String,
    val language: String,
    val playCount: String,
    val url: String,
    val downloadUrlsJson: String,
    val addedAt: Long
)

@Entity(tableName = "liked_songs")
data class LikedSongEntity(
    @PrimaryKey val songId: String,
    val addedAt: Long
)

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val songId: String,
    val filePath: String,
    val quality: String,
    val size: Long,
    val addedAt: Long
)

@Entity(tableName = "cached_songs")
data class CacheEntity(
    @PrimaryKey val songId: String,
    val filePath: String,
    val size: Long,
    val addedAt: Long
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val name: String,
    val createdAt: Long
)

@Entity(tableName = "playlist_songs", primaryKeys = ["playlistId", "songId"])
data class PlaylistSongCrossRef(
    val playlistId: Long,
    val songId: String,
    val addedAt: Long
)
