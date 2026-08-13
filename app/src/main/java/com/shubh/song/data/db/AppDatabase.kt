package com.shubh.song.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.shubh.song.data.db.dao.CachesDao
import com.shubh.song.data.db.dao.DownloadsDao
import com.shubh.song.data.db.dao.LikedSongsDao
import com.shubh.song.data.db.dao.PlaybackStatsDao
import com.shubh.song.data.db.dao.PlaylistDao
import com.shubh.song.data.db.dao.SavedAlbumDao
import com.shubh.song.data.db.dao.SongsDao
import com.shubh.song.data.db.entity.CacheEntity
import com.shubh.song.data.db.entity.DownloadEntity
import com.shubh.song.data.db.entity.LikedSongEntity
import com.shubh.song.data.db.entity.PlaybackStatsEntity
import com.shubh.song.data.db.entity.PlayedDayEntity
import com.shubh.song.data.db.entity.PlaylistEntity
import com.shubh.song.data.db.entity.PlaylistSongCrossRef
import com.shubh.song.data.db.entity.SavedAlbumEntity
import com.shubh.song.data.db.entity.SavedAlbumTrackEntity
import com.shubh.song.data.db.entity.SongEntity

@Database(
    entities = [
        SongEntity::class,
        LikedSongEntity::class,
        DownloadEntity::class,
        CacheEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class,
        PlaybackStatsEntity::class,
        PlayedDayEntity::class,
        SavedAlbumEntity::class,
        SavedAlbumTrackEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun songsDao(): SongsDao
    abstract fun likedSongsDao(): LikedSongsDao
    abstract fun downloadsDao(): DownloadsDao
    abstract fun cachesDao(): CachesDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun savedAlbumDao(): SavedAlbumDao
    abstract fun playbackStatsDao(): PlaybackStatsDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mysong.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}