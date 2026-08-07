package com.shubh.song.util

import android.app.Activity
import android.graphics.Color
import android.view.View
import com.shubh.song.MySongApp
import com.shubh.song.R

/**
 * Pure-#000000 "AMOLED" UI option. Local bright-text themes already use dark
 * surfaces; toggling this forces every chrome surface to true black so OLED
 * pixels switch fully off.
 */
object AmoledUtils {

    fun enabled(): Boolean =
        MySongApp.instance.settingsRepository.amoledBlack

    /** Surface color: true black when AMOLED is on, default primary otherwise. */
    fun black(): Int =
        if (enabled()) Color.BLACK
        else MySongApp.instance.getColor(R.color.bg_primary)

    /**
     * Paints the activity's root (background) and the system bars. Best-effort
     * — each surface also re-paints itself when the toggle is changed.
     */
    fun apply(activity: Activity, root: View) {
        if (!enabled()) return
        root.setBackgroundColor(Color.BLACK)
        activity.window.statusBarColor = Color.BLACK
        activity.window.navigationBarColor = Color.BLACK
    }
}