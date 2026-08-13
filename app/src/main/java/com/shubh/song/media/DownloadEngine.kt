package com.shubh.song.media

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.shubh.song.MySongApp
import com.shubh.song.data.local.FileStore
import com.shubh.song.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

/**
 * Downloads MP3s at the user-selected quality straight into the device's
 * public Downloads/Mysong folder (MediaStore on Android 10+), with
 * real-time progress exposed through [progress].
 */
object DownloadEngine {

    /** songId -> progress percent; -1 = failed. */
    private val _progress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val progress: StateFlow<Map<String, Int>> = _progress

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = mutableSetOf<String>()

    fun isActive(songId: String): Boolean = activeJobs.contains(songId)

    fun progressOf(songId: String): Int? = _progress.value[songId]

    fun start(song: Song) {
        if (activeJobs.contains(song.id)) return
        activeJobs.add(song.id)
        _progress.value = _progress.value + (song.id to 0)
        scope.launch {
            val ok = download(song)
            _progress.value = _progress.value + (song.id to if (ok) 100 else -1)
            if (ok) {
                delay(2500)
                _progress.value = _progress.value - song.id
            }
            activeJobs.remove(song.id)
        }
    }

    suspend fun download(song: Song): Boolean = withContext(Dispatchers.IO) {
        try {
            val repo = MySongApp.instance.libraryRepository
            val settings = MySongApp.instance.settingsRepository

            // Search results often omit download URLs; fetch the fresh song.
            val fresh = MySongApp.instance.musicRepository.fetchSong(song.id) ?: song
            val url = fresh.bestDownloadUrl(settings.quality)
                ?: fresh.bestDownloadUrl("160")
            if (url.isNullOrBlank()) return@withContext false

            val request = Request.Builder().url(url).build()
            val response = com.shubh.song.data.api.ApiClient.okHttp.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return@withContext false
            }
            val total = response.body?.contentLength() ?: -1L
            val tempFile = File(FileStore.cacheFileFor(song.id).path + ".part")
            tempFile.parentFile?.mkdirs()

            var written = 0L
            response.body?.use { body ->
                tempFile.outputStream().use { out ->
                    val input = body.byteStream()
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer, 0, buffer.size)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        written += read
                        if (total > 0) {
                            val percent = ((written * 100) / total).toInt().coerceIn(0, 99)
                            _progress.value = _progress.value + (song.id to percent)
                        }
                    }
                }
            }
            response.close()

            val finalUri = publishToDownloads(MySongApp.instance, fresh, tempFile)
            if (finalUri != null) {
                repo.addDownload(fresh, finalUri, settings.quality, tempFile.length())
                FileStore.deleteQuietly(tempFile)
                return@withContext true
            }
            FileStore.deleteQuietly(tempFile)
            return@withContext false
        } catch (e: Exception) {
            false
        }
    }

    /** Deletes the physical file (MediaStore or legacy path) and DB record. */
    suspend fun delete(song: Song): Boolean = withContext(Dispatchers.IO) {
        val context = MySongApp.instance
        try {
            val path = context.libraryRepository.downloadFilePath(song.id)
            if (!path.isNullOrBlank()) {
                val uri = android.net.Uri.parse(path)
                if (uri.scheme == "content") {
                    context.contentResolver.delete(uri, null, null)
                } else {
                    File(uri.path ?: path).delete()
                }
            }
            FileStore.legacyDownloadFileFor(song.id).delete()
            FileStore.deleteQuietly(FileStore.cacheFileFor(song.id))
            context.libraryRepository.removeDownload(song.id)
            _progress.value = _progress.value - song.id
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun publishToDownloads(context: Context, song: Song, file: File): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, "${FileStore.sanitize(song.name)}.mp3")
                    put(MediaStore.Downloads.MIME_TYPE, "audio/mpeg")
                    put(
                        MediaStore.Downloads.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/" + com.shubh.song.util.Constants.DOWNLOAD_DIR_NAME
                    )
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolver.insert(
                    MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                    values
                ) ?: return null
                resolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                } ?: return null
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri.toString()
            } else {
                val dest = FileStore.legacyDownloadFileFor(song.id)
                dest.parentFile?.mkdirs()
                file.copyTo(dest, overwrite = true)
                dest.absolutePath
            }
        } catch (e: Exception) {
            null
        }
    }
}
