package com.shubh.song.util

import android.app.Dialog
import android.os.Build
import android.view.WindowManager

/**
 * Frosted-glass backdrop for bottom sheets.
 *
 * Android 12+ (API 31): real Live Blur via FLAG_BLUR_BEHIND + background blur
 * radius 25, which blurs whatever sits behind the translucent glass sheet.
 * Older devices: no blur API, so fall back to a slightly stronger window dim
 * so the translucent glass still reads as frosted instead of muddy.
 */
object LiquidGlass {

    private const val BLUR_RADIUS = 25

    fun apply(dialog: Dialog?) {
        val window = dialog?.window ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.setBackgroundBlurRadius(BLUR_RADIUS)
            } else {
                window.setDimAmount(0.5f)
            }
        } catch (t: Throwable) {
            // Blur is cosmetic; never crash the sheet.
        }
    }
}