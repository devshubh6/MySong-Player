package com.shubh.song.media

import android.content.Context
import android.media.audiofx.EnvironmentalReverb
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.shubh.song.MySongApp
import com.shubh.song.data.model.Song
import com.shubh.song.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Central playback engine. Holds the single ExoPlayer instance, manages the
 * play queue, and exposes a [StateFlow] the whole UI (mini player, full
 * player, queue sheet) observes.
 */
object PlaybackManager {

    enum class AudioMode {
        NORMAL, SLOWED_REVERB, NIGHTCORE;

        fun prefKey(): String = when (this) {
            NORMAL -> Constants.AUDIO_MODE_NORMAL
            SLOWED_REVERB -> Constants.AUDIO_MODE_SLOWED
            NIGHTCORE -> Constants.AUDIO_MODE_NIGHTCORE
        }
    }

    data class PlayerUiState(
        val currentSong: Song? = null,
        val queue: List<Song> = emptyList(),
        val index: Int = -1,
        val isPlaying: Boolean = false,
        val isBuffering: Boolean = false,
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
        val speed: Float = 1f,
        val isOffline: Boolean = false,
        val shuffleEnabled: Boolean = false,
        val repeatMode: Int = Player.REPEAT_MODE_OFF,
        val audioMode: AudioMode = AudioMode.NORMAL,
        val crossfadeSec: Int = 0
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState

    lateinit var player: ExoPlayer
        private set

    private var queue: List<Song> = emptyList()
    private var tickerJob: Job? = null
    private var lastSessionId = 0
    private var currentUriIsLocal = false
    private var stopAfterMediaItem: Boolean = false
    private var appContext: Context? = null
    private var lastAudioMode: AudioMode = AudioMode.NORMAL
    private var reverb: EnvironmentalReverb? = null
    private var reverbSession = 0
    private var autoMixing = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var fadeOutRunnable: Runnable? = null
    private var fadeJob: Job? = null

    fun init(context: Context) {
        if (::player.isInitialized) return
        appContext = context.applicationContext

        val okHttpDataSourceFactory = OkHttpDataSource.Factory(com.shubh.song.data.api.ApiClient.okHttp)
        val dataSourceFactory = DefaultDataSource.Factory(context, okHttpDataSourceFactory)

        player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        player.volume = 1f
        player.setWakeMode(C.WAKE_MODE_LOCAL)

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    MusicService.start(appContext ?: MySongApp.instance)
                }
                pushState()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                pushState()
                if (playbackState == Player.STATE_READY) {
                    val sessionId = player.audioSessionId
                    if (sessionId > 0 && sessionId != lastSessionId) {
                        lastSessionId = sessionId
                        EqualizerController.attach(sessionId)
                        attachReverb(sessionId)
                    }
                    scheduleCrossfadeOut()
                } else if (playbackState == Player.STATE_ENDED) {
                    maybeAutoMix()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                onTrackChanged()
                fadeInOnTransition()
                scheduleCrossfadeOut()
            }

            override fun onPlayerError(error: PlaybackException) {
                scope.launch {
                    delay(600)
                    playNext()
                }
            }
        })

        // Restore the persisted playback signature (speed / pitch) on cold start.
        lastAudioMode = MySongApp.instance.settingsRepository.prefs.audioMode.toAudioMode()
        player.playbackParameters = parametersFor(lastAudioMode)

        startTicker()
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (true) {
                pushState()
                delay(500)
            }
        }
    }

    private fun pushState() {
        val index = player.currentMediaItemIndex
        val current = if (index in queue.indices) queue[index] else null
        _uiState.value = PlayerUiState(
            currentSong = current,
            queue = queue,
            index = index,
            isPlaying = player.isPlaying,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.coerceAtLeast(0L),
            speed = player.playbackParameters.speed,
            isOffline = currentUriIsLocal,
            shuffleEnabled = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            audioMode = lastAudioMode,
            crossfadeSec = MySongApp.instance.settingsRepository.prefs.crossfadeSec
        )
    }

    private fun onTrackChanged() {
        player.playbackParameters = parametersFor(lastAudioMode)
        val index = player.currentMediaItemIndex
        val current = if (index in queue.indices) queue[index] else null
        if (current == null) return

        currentUriIsLocal = false
        scope.launch {
            resolveUri(current).let { (uri, isLocal) ->
                currentUriIsLocal = isLocal
                val mediaItem = buildMediaItem(current, uri)
                if (player.currentMediaItemIndex == index) {
                    if (player.currentMediaItem?.mediaId != current.id || isLocal) {
                        player.replaceMediaItem(index, mediaItem)
                    }
                }
            }
        }

        if (stopAfterMediaItem) {
            stopAfterMediaItem = false
            scope.launch {
                delay(200)
                player.pause()
            }
        }

        StreamCache.autoCache(current)
        pushState()
    }

    // ---- Public playback control ----

    fun playQueue(songs: List<Song>, startIndex: Int) {
        if (songs.isEmpty()) return
        val safeIndex = startIndex.coerceIn(0, songs.size - 1)
        queue = songs
        scope.launch {
            val items = songs.map { song ->
                val (uri, isLocal) = resolveUri(song)
                currentUriIsLocal = isLocal
                buildMediaItem(song, uri)
            }
            player.setMediaItems(items, safeIndex, 0L)
            player.prepare()
            player.play()
            pushState()
        }
    }

    /** Appends songs to the back of the queue without interrupting playback. */
    fun appendToQueue(songs: List<Song>) {
        if (songs.isEmpty()) return
        val previous = queue
        queue = previous + songs
        scope.launch {
            val items = songs.map { song ->
                val (uri, isLocal) = resolveUri(song)
                currentUriIsLocal = isLocal
                buildMediaItem(song, uri)
            }
            if (player.mediaItemCount > 0) {
                player.addMediaItems(items)
            } else {
                player.setMediaItems(items, 0, 0L)
                player.prepare()
            }
            pushState()
        }
    }

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun play() {
        player.play()
    }

    fun pause() {
        player.pause()
    }

    fun playNext() {
        if (queue.isEmpty()) return
        if (player.hasNextMediaItem()) player.seekToNextMediaItem() else player.seekTo(0, 0L)
    }

    fun playPrev() {
        if (queue.isEmpty()) return
        if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun seekToIndex(index: Int) {
        if (queue.isEmpty()) return
        val safe = index.coerceIn(0, queue.size - 1)
        if (player.currentMediaItemIndex == safe && player.playbackState != Player.STATE_IDLE) {
            player.play()
            return
        }
        player.seekTo(safe, 0L)
        player.play()
        pushState()
    }

    fun setSpeed(speed: Float) {
        player.setPlaybackSpeed(speed.coerceIn(0.5f, 2f))
    }

    // ---- Playback signature: Normal / Slowed+Reverb / Nightcore ----

    fun setAudioMode(mode: AudioMode) {
        if (mode == lastAudioMode) {
            pushState()
            return
        }
        lastAudioMode = mode
        MySongApp.instance.settingsRepository.prefs.audioMode = mode.prefKey()
        applyAudioMode()
    }

    /** Cycles Normal → Slowed+Reverb → Nightcore → Normal, returning the new mode. */
    fun cycleAudioMode(): AudioMode {
        val next = when (lastAudioMode) {
            AudioMode.NORMAL -> AudioMode.SLOWED_REVERB
            AudioMode.SLOWED_REVERB -> AudioMode.NIGHTCORE
            AudioMode.NIGHTCORE -> AudioMode.NORMAL
        }
        setAudioMode(next)
        return next
    }

    fun audioModeLabel(): String = when (lastAudioMode) {
        AudioMode.NORMAL -> "1x"
        AudioMode.SLOWED_REVERB -> "Slowed"
        AudioMode.NIGHTCORE -> "Nightcore"
    }

    private fun parametersFor(mode: AudioMode) = when (mode) {
        AudioMode.NORMAL -> PlaybackParameters(1f, 1f)
        AudioMode.SLOWED_REVERB -> PlaybackParameters(0.85f, 0.85f)
        AudioMode.NIGHTCORE -> PlaybackParameters(1.15f, 1.25f)
    }

    private fun applyAudioMode() {
        player.playbackParameters = parametersFor(lastAudioMode)
        if (lastAudioMode == AudioMode.SLOWED_REVERB) {
            attachReverb(player.audioSessionId)
        } else {
            releaseReverb()
        }
        pushState()
    }

    /** Ambient reverb effect (Slowed+Reverb mood) bound to the audio session. */
    private fun attachReverb(sessionId: Int) {
        if (lastAudioMode != AudioMode.SLOWED_REVERB) return
        if (sessionId <= 0) return
        if (reverb != null && reverbSession == sessionId) return
        releaseReverb()
        try {
            val effect = EnvironmentalReverb(0, sessionId)
            effect.roomLevel = -600
            effect.reverbLevel = 450
            effect.decayTime = 1600
            effect.decayHFRatio = 800
            effect.diffusion = 900
            effect.reverbDelay = 30
            effect.enabled = true
            reverb = effect
            reverbSession = sessionId
        } catch (_: Exception) {
            reverb = null
            reverbSession = 0
        }
    }

    private fun releaseReverb() {
        try {
            reverb?.setEnabled(false)
            reverb?.release()
        } catch (_: Exception) {
        }
        reverb = null
        reverbSession = 0
    }

    // ---- Crossfade (0–12 s, applied via volume automation on a single player) ----

    fun setCrossfade(seconds: Int) {
        val clamped = seconds.coerceIn(0, 12)
        MySongApp.instance.settingsRepository.prefs.crossfadeSec = clamped
        pushState()
    }

    /** Fades the freshly transitioned track in over the configured gap. */
    private fun fadeInOnTransition() {
        val crossMs = MySongApp.instance.settingsRepository.prefs.crossfadeSec * 1000L
        if (crossMs <= 0) {
            player.volume = 1f
            return
        }
        val target = player.volume.coerceIn(0f, 1f)
        if (target <= 0f) {
            player.volume = 1f
            fadeInTo(1f, crossMs)
            return
        }
        player.volume = 0f
        fadeInTo(target, crossMs)
    }

    private fun fadeInTo(to: Float, durationMs: Long) {
        fadeJob?.cancel()
        fadeJob = scope.launch {
            val start = SystemClock.elapsedRealtime()
            while (true) {
                val elapsed = SystemClock.elapsedRealtime() - start
                val t = if (durationMs <= 0) 1f else (elapsed.toFloat() / durationMs).coerceIn(0f, 1f)
                player.volume = (to * t).coerceIn(0f, 1f)
                if (t >= 1f) break
                delay(16)
            }
        }
    }

    /** Schedules a fade-out shortly before the end of the playing item. */
    private fun scheduleCrossfadeOut() {
        fadeOutRunnable?.let { mainHandler.removeCallbacks(it) }
        fadeOutRunnable = null
        val crossMs = MySongApp.instance.settingsRepository.prefs.crossfadeSec * 1000L
        if (crossMs <= 0) return
        val duration = player.duration
        if (duration <= 0 || duration == C.TIME_UNSET) return
        val startAt = (duration - crossMs).coerceAtLeast(0L)
        if (startAt <= 0) return
        val runnable = Runnable {
            val current = player.volume
            if (current > 0f) {
                fadeJob?.cancel()
                fadeJob = scope.launch {
                    val startVol = current
                    val begin = SystemClock.elapsedRealtime()
                    while (true) {
                        val elapsed = SystemClock.elapsedRealtime() - begin
                        val t = if (crossMs <= 0) 1f else (elapsed.toFloat() / crossMs).coerceIn(0f, 1f)
                        player.volume = (startVol * (1f - t)).coerceIn(0f, 1f)
                        if (t >= 1f) break
                        delay(16)
                    }
                }
            }
        }
        fadeOutRunnable = runnable
        mainHandler.postDelayed(runnable, startAt)
    }

    // ---- Auto-mix: keep the playlist running at the end of the queue ----
    // Appends up to 10 similar songs once the last queued track finishes.

    private fun maybeAutoMix() {
        if (autoMixing) return
        if (!PlayerPrefs.autoMix) return
        val index = player.currentMediaItemIndex
        val seed = if (index in queue.indices) queue[index] else null
        if (seed == null) return
        autoMixing = true
        scope.launch {
            try {
                val similar = try {
                    MySongApp.instance.musicRepository.similarSongs(seed, 10)
                } catch (t: Throwable) {
                    emptyList()
                }
                val existing = queue.map { it.id }.toSet()
                val fresh = similar.filter { it.id != seed.id && it.id !in existing }.take(10)
                if (fresh.isNotEmpty()) {
                    appendToQueue(fresh)
                    player.seekTo(queue.lastIndex - fresh.size + 1, 0L)
                    player.play()
                }
            } finally {
                autoMixing = false
            }
        }
    }

    fun toggleShuffle() {
        player.shuffleModeEnabled = !player.shuffleModeEnabled
        pushState()
    }

    fun cycleRepeat() {
        val next = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        player.repeatMode = next
        pushState()
    }

    fun removeFromQueue(index: Int) {
        if (index < 0 || index >= queue.size) return
        queue = queue.filterIndexed { i, _ -> i != index }
        player.removeMediaItem(index)
        pushState()
    }

    fun clearQueue() {
        queue = emptyList()
        player.clearMediaItems()
        pushState()
    }

    fun fadeOutAndPause() {
        scope.launch {
            val steps = 12
            repeat(steps) { i ->
                player.volume = 1f - (i + 1).toFloat() / steps
                delay(120)
            }
            player.pause()
            player.volume = 1f
        }
    }

    // ---- Internals ----

    private suspend fun resolveUri(song: Song): Pair<Uri, Boolean> = withContext(Dispatchers.IO) {
        val repo = MySongApp.instance.libraryRepository

        val downloadPath = repo.downloadFilePath(song.id)
        if (downloadPath != null && localFileExists(downloadPath)) {
            return@withContext Pair(Uri.parse(downloadPath), true)
        }

        val cachePath = repo.cacheFilePath(song.id)
        if (cachePath != null && localFileExists(cachePath)) {
            return@withContext Pair(Uri.parse(cachePath), true)
        }

        val quality = MySongApp.instance.settingsRepository.quality
        val remote = song.bestDownloadUrl(quality)
            ?: song.bestDownloadUrl("160")
        if (!remote.isNullOrBlank()) {
            return@withContext Pair(Uri.parse(remote), false)
        }
        Pair(Uri.parse(song.url.ifBlank { remote.orEmpty() }), false)
    }

    private fun localFileExists(uriString: String): Boolean {
        val uri = Uri.parse(uriString)
        if (uri.scheme == "content") {
            return try {
                val fd = MySongApp.instance.contentResolver.openFileDescriptor(uri, "r")
                    ?: return false
                fd.close()
                true
            } catch (e: Exception) {
                false
            }
        }
        val path = uri.path ?: return false
        return File(path).isFile
    }

    private fun buildMediaItem(song: Song, uri: Uri): MediaItem {
        val artwork = song.imageUrl.takeIf { it.isNotBlank() }?.let(Uri::parse)
        val metadata = MediaMetadata.Builder()
            .setTitle(song.name)
            .setArtist(song.artists)
            .setAlbumTitle(song.album)
            .setArtworkUri(artwork)
            .build()
        return MediaItem.Builder()
            .setMediaId(song.id)
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()
    }
}

/** Reads persisted playback preferences. */
object PlayerPrefs {
    val autoMix: Boolean
        get() = MySongApp.instance.settingsRepository.prefs.autoMix
}

private fun String.toAudioMode(): PlaybackManager.AudioMode = when (this) {
    Constants.AUDIO_MODE_SLOWED -> PlaybackManager.AudioMode.SLOWED_REVERB
    Constants.AUDIO_MODE_NIGHTCORE -> PlaybackManager.AudioMode.NIGHTCORE
    else -> PlaybackManager.AudioMode.NORMAL
}
