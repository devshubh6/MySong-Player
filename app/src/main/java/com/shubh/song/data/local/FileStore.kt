package com.shubh.song.data.local

import android.content.Context
import android.os.Environment
import com.shubh.song.util.Constants
import java.io.File

object FileStore {

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
        ensureDirs()
    }

    private fun ensureDirs() {
        cacheDir().mkdirs()
        legacyDownloadDir().mkdirs()
    }

    fun cacheDir(): File =
        File(appContext.filesDir, Constants.CACHE_DIR_NAME)

    /** Public "Mysong" music folder used for downloads on devices below Android 10. */
    fun legacyDownloadDir(): File =
        File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            Constants.DOWNLOAD_DIR_NAME
        )

    fun cacheFileFor(songId: String): File =
        File(cacheDir(), "${sanitize(songId)}.mp3")

    fun legacyDownloadFileFor(songId: String): File =
        File(legacyDownloadDir(), "${sanitize(songId)}.mp3")

    fun sanitize(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._-]"), "_")

    fun sizeOf(file: File): Long =
        if (file.isFile) file.length() else 0L

    fun deleteQuietly(file: File) {
        try {
            if (file.exists()) file.delete()
        } catch (_: Exception) {
        }
    }
}