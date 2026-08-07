package com.shubh.song.util

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.text.HtmlCompat
import com.shubh.song.MySongApp
import com.shubh.song.R
import java.util.Locale

object Ui {

    fun accentRes(name: String): Int = when (name) {
        "purple" -> R.color.accent_purple
        "pink" -> R.color.accent_pink
        "cyan" -> R.color.accent_cyan
        "amber" -> R.color.accent_amber
        "red" -> R.color.accent_red
        else -> R.color.accent_green
    }

    fun accentColor(context: Context): Int =
        ContextCompat.getColor(context, accentRes(MySongApp.instance.settingsRepository.accent))

    fun tint(view: View, color: Int) {
        (view as? ImageView)?.setColorFilter(color)
    }

    fun tintCircle(view: View, color: Int) {
        val d = GradientDrawable()
        d.shape = GradientDrawable.OVAL
        d.setColor(color)
        d.setStroke(2.dp(view.context), Color.WHITE)
        view.background = d
    }

    fun tintButton(view: View, color: Int) {
        view.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
    }

    fun softTint(color: Int): Int = ColorUtils.setAlphaComponent(color, 40)

    /** Decodes HTML entities (e.g. &quot;) so titles render as plain text. */
    fun html(text: String): String =
        HtmlCompat.fromHtml(text, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()

    fun toast(msg: String) {
        Toast.makeText(MySongApp.instance, msg, Toast.LENGTH_SHORT).show()
    }

    fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val m = totalSec / 60
        val s = totalSec % 60
        return String.format(Locale.US, "%d:%02d", m, s)
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024 && unit < units.size - 1) {
            value /= 1024
            unit++
        }
        return String.format(Locale.US, "%.1f %s", value, units[unit])
    }

    fun Int.dp(context: Context): Int =
        (this * context.resources.displayMetrics.density).toInt()
}
