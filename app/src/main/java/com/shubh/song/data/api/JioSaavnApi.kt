package com.shubh.song.data.api

import com.shubh.song.data.api.dto.AlbumDto
import com.shubh.song.data.api.dto.ApiEnvelope
import com.shubh.song.data.api.dto.ArtistAlbumsData
import com.shubh.song.data.api.dto.ArtistDto
import com.shubh.song.data.api.dto.ArtistSongsData
import com.shubh.song.data.api.dto.GlobalSearchData
import com.shubh.song.data.api.dto.LyricsDto
import com.shubh.song.data.api.dto.PlaylistDto
import com.shubh.song.data.api.dto.SearchResults
import com.shubh.song.data.api.dto.SongDto
import retrofit2.http.GET
import retrofit2.http.Query

interface JioSaavnApi {

    @GET("api/search/songs")
    suspend fun searchSongs(
        @Query("query") query: String,
        @Query("page") page: Int = 0,
        @Query("limit") limit: Int = 20
    ): ApiEnvelope<SearchResults<SongDto>>

    @GET("api/search/albums")
    suspend fun searchAlbums(
        @Query("query") query: String,
        @Query("page") page: Int = 0,
        @Query("limit") limit: Int = 20
    ): ApiEnvelope<SearchResults<AlbumDto>>

    @GET("api/search/artists")
    suspend fun searchArtists(
        @Query("query") query: String,
        @Query("page") page: Int = 0,
        @Query("limit") limit: Int = 20
    ): ApiEnvelope<SearchResults<ArtistDto>>

    @GET("api/search/playlists")
    suspend fun searchPlaylists(
        @Query("query") query: String,
        @Query("page") page: Int = 0,
        @Query("limit") limit: Int = 20
    ): ApiEnvelope<SearchResults<PlaylistDto>>

    @GET("api/search")
    suspend fun searchAll(@Query("query") query: String): ApiEnvelope<GlobalSearchData>

    @GET("api/songs")
    suspend fun songsById(@Query("ids") ids: String): ApiEnvelope<List<SongDto>>

    @GET("api/songs/{id}/suggestions")
    suspend fun songSuggestions(
        @retrofit2.http.Path("id") id: String,
        @Query("limit") limit: Int = 10
    ): ApiEnvelope<List<SongDto>>

    @GET("api/lyrics/{id}")
    suspend fun lyricsById(
        @retrofit2.http.Path("id") id: String
    ): ApiEnvelope<LyricsDto>

    @GET("api/albums")
    suspend fun albumById(@Query("id") id: String): ApiEnvelope<AlbumDto>

    @GET("api/artists/{id}")
    suspend fun artistById(@retrofit2.http.Path("id") id: String): ApiEnvelope<ArtistDto>

    @GET("api/artists/{id}/songs")
    suspend fun artistSongs(
        @retrofit2.http.Path("id") id: String,
        @Query("page") page: Int = 0,
        @Query("sortBy") sortBy: String = "popularity",
        @Query("sortOrder") sortOrder: String = "desc"
    ): ApiEnvelope<ArtistSongsData>

    @GET("api/playlists")
    suspend fun playlistById(
        @Query("id") id: String,
        @Query("page") page: Int = 0,
        @Query("limit") limit: Int = 100
    ): ApiEnvelope<PlaylistDto>
}
