package com.shubh.song.media

import com.shubh.song.MySongApp
import com.shubh.song.data.local.FileStore
import com.shubh.song.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Request
import java.io.File

/**
 * Silently downloads the currently playing track to the private streaming
 * cache as soon as playback starts, so reopening it later works fully
 * offline (and until then every replayed scroll is instant).
 */
object StreamCache {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val active = mutableSetOf<String>()

    fun autoCache(song: Song) {
        if (song.id.isBlank()) return
        if (!active.add(song.id)) return

        scope.launch {
            try {
                val repo = MySongApp.instance.libraryRepository

                val existingDownload = repo.downloadFilePath(song.id)
                if (existingDownload != null) {
                    return@launch
                }
                val existingCache = repo.cacheFilePath(song.id)
                if (existingCache != null) {
                    val file = runCatching { java.io.File(android.net.Uri.parse(existingCache).path!!) }.getOrNull()
                    if (file != null && file.isFile && file.length() > 0) return@launch
                }

                val cacheFile = FileStore.cacheFileFor(song.id)
                if (cacheFile.isFile && cacheFile.length() > 0) return@launch

                val fresh = MySongApp.instance.musicRepository.fetchSong(song.id) ?: song
                val quality = MySongApp.instance.settingsRepository.quality
                val url = fresh.bestDownloadUrl(quality) ?: fresh.bestDownloadUrl("160")
                if (url.isNullOrBlank()) return@launch

                val request = Request.Builder().url(url).build()
                val response = com.shubh.song.data.api.ApiClient.okHttp.newCall(request).execute()
                if (!response.isSuccessful) {
                    response.close()
                    return@launch
                }
                cacheFile.parentFile?.mkdirs()
                response.body?.use { body ->
                    cacheFile.outputStream().use { out ->
                        val input = body.byteStream()
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer, 0, buffer.size)
                            if (read == -1) break
                            out.write(buffer, 0, read)
                        }
                    }
                }
                response.close()

                if (cacheFile.isFile && cacheFile.length() > 0) {
                    repo.addCache(fresh, cacheFile.absolutePath, cacheFile.length())
                }
            } catch (e: Exception) {
                // Caching must never interrupt playback.
            } finally {
                active.remove(song.id)
            }
        }
    }
}