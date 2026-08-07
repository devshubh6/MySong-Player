package com.shubh.song.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import coil.Coil
import coil.request.ImageRequest
import com.shubh.song.MainActivity
import com.shubh.song.R
import com.shubh.song.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground music service. Keeps the ExoPlayer (owned by [PlaybackManager])
 * alive in the background with a START_STICKY contract, an IMPORTANCE_LOW
 * "music_channel" notification showing artwork / title / artist /
 * play-pause / next / previous, and a media3 [MediaSession] bound to the
 * player so lock-screen and system media controls work too.
 */
class MusicService : Service() {

    companion object {
        const val CHANNEL_ID = "music_channel"
        const val NOTIFICATION_ID = 2001

        const val ACTION_START = "com.shubh.song.service.START"
        const val ACTION_PLAY_PAUSE = "com.shubh.song.service.PLAY_PAUSE"
        const val ACTION_NEXT = "com.shubh.song.service.NEXT"
        const val ACTION_PREVIOUS = "com.shubh.song.service.PREVIOUS"

        /** Starts the service as a foreground service (required on API 26+). */
        fun start(context: Context, action: String = ACTION_START) {
            val intent = Intent(context, MusicService::class.java).setAction(action)
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var mediaSession: MediaSession? = null
    private var artworkJob: Job? = null

    /** Track identity the current large icon belongs to. */
    private var artworkTrackId: String? = null
    private var lastArtwork: Bitmap? = null

    private var lastTrackId: String? = null
    private var lastIsPlaying: Boolean? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()

        // Keep the CPU awake while playing and never let the OS reap us.
        PlaybackManager.player.setWakeMode(C.WAKE_MODE_LOCAL)

        // Attach the ExoPlayer to a media3 MediaSession so the system media
        // controls, lock screen and other apps can steer playback.
        mediaSession = MediaSession.Builder(this, PlaybackManager.player)
            .setSessionActivity(openPlayerPendingIntent())
            .build()

        startForeground(NOTIFICATION_ID, buildNotification())

        observePlayback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> {
                val player = PlaybackManager.player
                if (player.isPlaying) player.pause() else player.play()
            }
            ACTION_NEXT -> PlaybackManager.playNext()
            ACTION_PREVIOUS -> PlaybackManager.playPrev()
        }

        // Must be called immediately after startForegroundService(), and
        // re-issued after every action so the notification stays current.
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = PlaybackManager.player
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        scope.cancel()
        mediaSession?.run {
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---- Notification ----

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Music playback",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Now playing and playback controls"
            setShowBadge(false)
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    /** Renders the full media notification from the current playback state. */
    private fun buildNotification(): Notification {
        val state = PlaybackManager.uiState.value
        val song = state.currentSong
        val isPlaying = state.isPlaying
        val player = PlaybackManager.player

        val artist = song?.artists.orEmpty()
        val album = song?.album.orEmpty()
        val subText = when {
            artist.isNotBlank() && album.isNotBlank() -> "$artist • $album"
            artist.isNotBlank() -> artist
            else -> album
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle(song?.name ?: getString(R.string.app_name))
            .setContentText(subText)
            .setContentIntent(openPlayerPendingIntent())
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(isPlaying)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .addAction(action(ACTION_PREVIOUS, R.drawable.ic_skip_prev, "Previous"))
            .addAction(
                action(
                    ACTION_PLAY_PAUSE,
                    if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                    if (isPlaying) "Pause" else "Play"
                )
            )
            .addAction(action(ACTION_NEXT, R.drawable.ic_skip_next, "Next"))

        mediaSession?.let { session ->
            builder.setStyle(
                MediaStyleNotificationHelper.MediaStyle(session)
                    .setShowActionsInCompactView(0, 1, 2)
            )
        }

        lastArtwork?.let { builder.setLargeIcon(it) }

        val durationMs = state.durationMs
        if (durationMs > 0) {
            val positionMs = state.positionMs.coerceIn(0L, durationMs)
            val progress = ((positionMs.toDouble() / durationMs) * 1000)
                .toInt()
                .coerceIn(0, 1000)
            builder.setProgress(1000, progress, false)
        }
        return builder.build()
    }

    private fun action(action: String, iconRes: Int, title: String): NotificationCompat.Action {
        val intent = Intent(this, NotifActionReceiver::class.java).setAction(action)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Action(iconRes, title, pendingIntent)
    }

    private fun openPlayerPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    // ---- Live refresh ----

    /** Re-renders the notification when the track or play state changes. */
    private fun observePlayback() {
        scope.launch {
            PlaybackManager.uiState.collect { state ->
                val trackId = state.currentSong?.id
                if (trackId != lastTrackId || state.isPlaying != lastIsPlaying) {
                    lastTrackId = trackId
                    lastIsPlaying = state.isPlaying
                    if (trackId != null) loadArtworkFor(state.currentSong)
                    notify(buildNotification())
                }
            }
        }
    }

    private fun loadArtworkFor(song: Song?) {
        if (song == null) {
            artworkTrackId = null
            lastArtwork = null
            return
        }
        if (artworkTrackId == song.id) return
        artworkTrackId = song.id
        lastArtwork = null
        artworkJob?.cancel()
        artworkJob = scope.launch {
            val bitmap = fetchArtwork(song)
            if (artworkTrackId == song.id) {
                lastArtwork = bitmap
                notify(buildNotification())
            }
        }
    }

    /** Prefers embedded artwork data, then fetches the album art via Coil. */
    private suspend fun fetchArtwork(song: Song): Bitmap? {
        decodeArtworkData(PlaybackManager.player.mediaMetadata)?.let { return it }
        val url = song.imageUrl.takeIf { it.isNotBlank() } ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(this@MusicService)
                    .data(url)
                    .size(512)
                    .allowHardware(false)
                    .build()
                val result = Coil.imageLoader(this@MusicService).execute(request)
                (result.drawable as? BitmapDrawable)?.bitmap
            } catch (_: Exception) {
                null
            }
        }
    }

    /** Decodes embedded [MediaMetadata.artworkData] with a safe size cap. */
    private fun decodeArtworkData(metadata: MediaMetadata): Bitmap? {
        val data = metadata.artworkData ?: return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 512 ||
                bounds.outHeight / (sample * 2) >= 512
            ) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeByteArray(data, 0, data.size, options)
        } catch (_: Exception) {
            null
        }
    }

    private fun notify(notification: Notification) {
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification)
    }
}