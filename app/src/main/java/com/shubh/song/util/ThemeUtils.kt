package com.shubh.song.util

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.SeekBar
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.shubh.song.MySongApp
import com.shubh.song.R

/**
 * App-wide dynamic subtle accent system.
 *
 * All screens derive their emphasis colours from the single persisted accent:
 * the raw accent for interactive elements (seekbars, play buttons, active tab
 * icons) and a low-alpha blend of it for quiet surfaces (card backgrounds,
 * tab indicator pill, header gradient overlays). Contrast is preserved by
 * blending over the surface colour at full opacity instead of using
 * semi-transparent tints.
 */
object ThemeUtils {

    // ---- Accent lookup ----

    fun accentHex(name: String): String = when (name) {
        "purple" -> "#A855F7"
        "pink" -> "#EC4899"
        "cyan" -> "#22D3EE"
        "amber" -> "#F59E0B"
        "red" -> "#EF4444"
        else -> "#1DB954"
    }

    fun accentName(context: Context): String =
        MySongApp.instance.settingsRepository.accent

    fun accentColor(context: Context): Int {
        val hex = MySongApp.instance.settingsRepository.prefs.accentHex
            .takeIf { it.isNotBlank() }
            ?: accentHex(accentName(context))
        return try {
            Color.parseColor(hex)
        } catch (e: IllegalArgumentException) {
            Color.parseColor(accentHex(accentName(context)))
        }
    }

    /** Accent with the given alpha percentage (0-100). */
    fun withAlpha(accent: Int, alphaPercent: Int): Int =
        (accent and 0x00FFFFFF) or ((alphaPercent * 255 / 100).coerceIn(0, 255) shl 24)

    /** Readable text colour that stays sharp over the accent surface. */
    fun onAccentText(context: Context): Int {
        val accent = accentColor(context)
        return if (ColorUtils.calculateLuminance(accent) > 0.5) Color.BLACK else Color.WHITE
    }

    // ---- Surfaces ----

    /**
     * A subtle accent-tinted card fill: the surface colour (white glass) is
     * blended towards the accent by [fraction], keeping full opacity so text
     * contrast never drops.
     */
    fun cardFill(context: Context, fraction: Float = 0.10f): Int =
        ColorUtils.blendARGB(
            ContextCompat.getColor(context, R.color.bg_surface),
            accentColor(context),
            fraction.coerceIn(0f, 1f)
        )

    /**
     * Rounded card background with the subtle accent tint and a faint accent
     * stroke. Replaces the static glass background of card views.
     */
    fun applyCardTint(view: View) {
        val context = view.context
        val accent = accentColor(context)
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 18f * context.resources.displayMetrics.density
            setColor(cardFill(context))
            setStroke(
                (1 * context.resources.displayMetrics.density).toInt(),
                withAlpha(accent, 18)
            )
        }
        view.background = drawable
    }

    /** Vertical top-glow gradient (accent fading out) for screen headers. */
    fun applyHeaderGradient(view: View, fromPercent: Int = 16, toPercent: Int = 0) {
        val accent = accentColor(view.context)
        val drawable = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(withAlpha(accent, fromPercent), withAlpha(accent, toPercent))
        )
        view.background = drawable
    }

    // ---- Components ----

    /** Tints BottomNavigation icons, labels and the active-tab indicator pill. */
    fun tintBottomNav(nav: BottomNavigationView) {
        val context = nav.context
        val accent = accentColor(context)
        val muted = ContextCompat.getColor(context, R.color.text_secondary)
        val checked = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(accent, muted)
        )
        nav.itemIconTintList = checked
        nav.itemTextColor = checked
        nav.itemActiveIndicatorColor = ColorStateList.valueOf(withAlpha(accent, 22))
    }

    /** Tints a seekbar: accent progress/thumb, subtle accent track. */
    fun tintSeekBar(seekBar: SeekBar) {
        val accent = accentColor(seekBar.context)
        seekBar.progressTintList = ColorStateList.valueOf(accent)
        seekBar.thumbTintList = ColorStateList.valueOf(accent)
        seekBar.progressBackgroundTintList = ColorStateList.valueOf(withAlpha(accent, 28))
        seekBar.secondaryProgressTintList = ColorStateList.valueOf(withAlpha(accent, 40))
    }

    /** Rounded chip background filled with the subtle accent tint. */
    fun chipFill(view: View) {
        val context = view.context
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = (22 * context.resources.displayMetrics.density).toFloat()
            setColor(cardFill(context, 0.08f))
            setStroke(
                (1 * context.resources.displayMetrics.density).toInt(),
                withAlpha(accentColor(context), 25)
            )
        }
        view.background = drawable
    }

    /** Tile background for browse/search category cards (accent glow). */
    fun categoryTile(view: View) {
        val context = view.context
        val accent = accentColor(context)
        val drawable = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                withAlpha(accent, 26),
                withAlpha(accent, 8),
                cardFill(context, 0.04f)
            )
        ).apply {
            cornerRadius = 18 * context.resources.displayMetrics.density
            setStroke(
                (1 * context.resources.displayMetrics.density).toInt(),
                withAlpha(accent, 30)
            )
        }
        view.background = drawable
    }
}