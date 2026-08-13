package com.shubh.song.media

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receives taps on the notification actions (play/pause, next, previous) and
 * forwards them to [MusicService], which performs the action and refreshes
 * the foreground notification.
 */
class NotifActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        MusicService.start(context, action)
    }
}