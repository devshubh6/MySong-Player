package com.shubh.song.media

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Sleep timer with a live countdown. When the countdown reaches zero the
 * volume fades out smoothly and playback pauses.
 */
object SleepTimer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _remainingMs = MutableStateFlow<Long?>(null)
    val remainingMs: StateFlow<Long?> = _remainingMs

    private var tickerJob: Job? = null
    private var totalMs: Long = 0L

    val isActive: Boolean
        get() = _remainingMs.value != null

    fun start(minutes: Int) {
        if (minutes <= 0) return
        totalMs = minutes * 60_000L
        _remainingMs.value = totalMs
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (_remainingMs.value != null) {
                delay(1000)
                val left = (_remainingMs.value ?: 0L) - 1000L
                if (left <= 0) {
                    _remainingMs.value = null
                    onFinished()
                    break
                }
                _remainingMs.value = left
            }
        }
    }

    fun stop() {
        tickerJob?.cancel()
        _remainingMs.value = null
    }

    fun addTime(minutes: Int) {
        val current = _remainingMs.value
        if (current == null) {
            start(minutes)
        } else {
            _remainingMs.value = current + minutes * 60_000L
        }
    }

    fun resetTicker() {
        val left = _remainingMs.value
        if (left != null) {
            _remainingMs.value = totalMs
        }
    }

    private fun onFinished() {
        scope.launch {
            PlaybackManager.fadeOutAndPause()
        }
    }
}
