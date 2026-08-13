package com.shubh.song.data.api.dto

import com.google.gson.annotations.SerializedName

data class ApiEnvelope<T>(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: T?
)

data class SearchResults<T>(
    @SerializedName("total") val total: Long?,
    @SerializedName("start") val start: Int?,
    @SerializedName("results") val results: List<T>?
)

data class LyricsDto(
    @SerializedName("id") val id: String?,
    @SerializedName("lyrics") val lyrics: String?,
    @SerializedName("copyright") val copyright: String?
)

data class ImageDto(
    @SerializedName("quality") val quality: String?,
    @SerializedName("url") val url: String?
)

data class DownloadDto(
    @SerializedName("quality") val quality: String?,
    @SerializedName("url") val url: String?
)

data class ArtistRefDto(
    @SerializedName("id") val id: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("role") val role: String?,
    @SerializedName("type") val type: String?,
    @SerializedName("image") val image: List<ImageDto>?,
    @SerializedName("url") val url: String?
)

data class SongAlbumDto(
    @SerializedName("id") val id: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("url") val url: String?
)

data class ArtistsDto(
    @SerializedName("primary") val primary: List<ArtistRefDto>?,
    @SerializedName("featured") val featured: List<ArtistRefDto>?,
    @SerializedName("all") val all: List<ArtistRefDto>?
)

data class SongDto(
    @SerializedName("id") val id: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("type") val type: String?,
    @SerializedName("year") val year: String?,
    @SerializedName("releaseDate") val releaseDate: String?,
    @SerializedName("duration") val duration: Number?,
    @SerializedName("label") val label: String?,
    @SerializedName("explicitContent") val explicitContent: Boolean?,
    @SerializedName("playCount") val playCount: Number?,
    @SerializedName("language") val language: String?,
    @SerializedName("hasLyrics") val hasLyrics: Boolean?,
    @SerializedName("lyricsId") val lyricsId: String?,
    @SerializedName("url") val url: String?,
    @SerializedName("copyright") val copyright: String?,
    @SerializedName("album") val album: SongAlbumDto?,
    @SerializedName("artists") val artists: ArtistsDto?,
    @SerializedName("image") val image: List<ImageDto>?,
    @SerializedName("downloadUrl") val downloadUrl: List<DownloadDto>?
)

data class AlbumDto(
    @SerializedName("id") val id: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("year") val year: Number?,
    @SerializedName("type") val type: String?,
    @SerializedName("playCount") val playCount: Number?,
    @SerializedName("language") val language: String?,
    @SerializedName("explicitContent") val explicitContent: Boolean?,
    @SerializedName("artists") val artists: ArtistsDto?,
    @SerializedName("songCount") val songCount: Number?,
    @SerializedName("url") val url: String?,
    @SerializedName("image") val image: List<ImageDto>?,
    @SerializedName("songs") val songs: List<SongDto>?
)

data class ArtistBioDto(
    @SerializedName("text") val text: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("sequence") val sequence: Number?
)

data class ArtistDto(
    @SerializedName("id") val id: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("url") val url: String?,
    @SerializedName("type") val type: String?,
    @SerializedName("image") val image: List<ImageDto>?,
    @SerializedName("followerCount") val followerCount: Number?,
    @SerializedName("fanCount") val fanCount: String?,
    @SerializedName("isVerified") val isVerified: Boolean?,
    @SerializedName("dominantLanguage") val dominantLanguage: String?,
    @SerializedName("dominantType") val dominantType: String?,
    @SerializedName("bio") val bio: List<ArtistBioDto>?,
    @SerializedName("dob") val dob: String?,
    @SerializedName("fb") val fb: String?,
    @SerializedName("twitter") val twitter: String?,
    @SerializedName("wiki") val wiki: String?,
    @SerializedName("availableLanguages") val availableLanguages: List<String>?,
    @SerializedName("isRadioPresent") val isRadioPresent: Boolean?,
    @SerializedName("topSongs") val topSongs: List<SongDto>?,
    @SerializedName("topAlbums") val topAlbums: List<AlbumDto>?,
    @SerializedName("singles") val singles: List<SongDto>?,
    @SerializedName("similarArtists") val similarArtists: List<ArtistDto>?
)

data class PlaylistDto(
    @SerializedName("id") val id: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("year") val year: Number?,
    @SerializedName("type") val type: String?,
    @SerializedName("playCount") val playCount: Number?,
    @SerializedName("language") val language: String?,
    @SerializedName("explicitContent") val explicitContent: Boolean?,
    @SerializedName("songCount") val songCount: Number?,
    @SerializedName("url") val url: String?,
    @SerializedName("image") val image: List<ImageDto>?,
    @SerializedName("songs") val songs: List<SongDto>?
)

data class ArtistSongsData(
    @SerializedName("total") val total: Long?,
    @SerializedName("songs") val songs: List<SongDto>?
)

data class ArtistAlbumsData(
    @SerializedName("total") val total: Long?,
    @SerializedName("albums") val albums: List<AlbumDto>?
)

data class GlobalSearchGroup(
    @SerializedName("position") val position: Number?,
    @SerializedName("results") val results: List<GlobalSearchItem>?
)

data class GlobalSearchData(
    @SerializedName("albums") val albums: GlobalSearchGroup?,
    @SerializedName("songs") val songs: GlobalSearchGroup?,
    @SerializedName("artists") val artists: GlobalSearchGroup?,
    @SerializedName("playlists") val playlists: GlobalSearchGroup?,
    @SerializedName("topQuery") val topQuery: GlobalSearchGroup?
)

data class GlobalSearchItem(
    @SerializedName("id") val id: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("type") val type: String?,
    @SerializedName("url") val url: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("image") val image: List<ImageDto>?,
    @SerializedName("album") val album: String?,
    @SerializedName("primaryArtists") val primaryArtists: String?,
    @SerializedName("singers") val singers: String?,
    @SerializedName("language") val language: String?,
    @SerializedName("year") val year: String?,
    @SerializedName("artist") val artist: String?,
    @SerializedName("songIds") val songIds: String?,
    @SerializedName("position") val position: Number?
)
