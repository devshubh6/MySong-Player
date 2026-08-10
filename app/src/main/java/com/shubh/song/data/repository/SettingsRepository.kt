package com.shubh.song.data.repository

import android.content.Context
import coil.Coil
import coil.ImageLoader
import com.shubh.song.data.local.FileStore
import com.shubh.song.data.prefs.SettingsPrefs
import com.shubh.song.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class SettingsRepository(context: Context) {

    private val appContext = context.applicationContext
    val prefs = SettingsPrefs(appContext)

    val theme: String
        get() = prefs.themeMode

    val accent: String
        get() = prefs.accent

    val quality: String
        get() = prefs.quality

    val languages: Set<String>
        get() = prefs.getLanguages()

    val country: String
        get() = prefs.country

    val eqEnabled: Boolean
        get() = prefs.eqEnabled

    val audioMode: String
        get() = prefs.audioMode

    val crossfadeSec: Int
        get() = prefs.crossfadeSec

    val autoMix: Boolean
        get() = prefs.autoMix

    val smartLoudness: Boolean
        get() = prefs.smartLoudness

    fun setEqEnabled(enabled: Boolean) {
        prefs.eqEnabled = enabled
    }

    fun setAudioMode(mode: String) {
        prefs.audioMode = mode
    }

    fun setCrossfade(seconds: Int) {
        prefs.crossfadeSec = seconds
    }

    fun setAutoMix(enabled: Boolean) {
        prefs.autoMix = enabled
    }

    fun setSmartLoudness(enabled: Boolean) {
        prefs.smartLoudness = enabled
    }

    fun setTheme(mode: String) {
        prefs.themeMode = mode
    }

    fun setAccent(name: String) {
        prefs.accent = name
    }

    fun setQuality(quality: String) {
        prefs.quality = quality
    }

    fun setLanguages(languages: Set<String>) {
        prefs.setLanguages(languages)
    }

    fun setCountry(country: String) {
        prefs.country = country
    }

    suspend fun cacheSizeBytes(): Long = withContext(Dispatchers.IO) {
        var size = 0L
        size += FileStore.cacheDir().size()
        size += legacyDownloadDirSize()
        size += imageCacheDir()?.size() ?: 0L
        size
    }

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        FileStore.cacheDir().deleteRecursively()
        FileStore.cacheDir().mkdirs()
        imageCacheDir()?.deleteRecursively()
        FileStore.legacyDownloadDir().listFiles()
            ?.filter { it.name.endsWith(".part") }
            ?.forEach { it.delete() }
    }

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    private fun imageCacheDir(): File? =
        try {
            val loader: ImageLoader = Coil.imageLoader(appContext)
            loader.diskCache?.directory?.toString()?.let { File(it) }
        } catch (e: Exception) {
            null
        }

    private fun legacyDownloadDirSize(): Long =
        FileStore.legacyDownloadDir().size()

    private fun File.size(): Long {
        if (!exists()) return 0L
        return if (isDirectory) {
            listFiles()?.sumOf { it.size() } ?: 0L
        } else {
            length()
        }
    }
}
