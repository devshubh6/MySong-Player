package com.shubh.song.util

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.imageLoader
import coil.request.ImageRequest
import com.shubh.song.MySongApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Dynamic palette theming helpers.
 *
 * [extract] downloads the album artwork (bitmap kept in software memory so
 * the color engine can read its pixels) through the app's Coil image loader
 * — previously streamed covers therefore resolve from the disk cache
 * instantly — then runs AndroidX Palette on a background thread. The result
 * drives the player's artwork gradient overlay and the themed bottom panel.
 *
 * Every step is defensive: unreadable images, cancelled requests or
 * palette-less photos fall back to the app accent color.
 */
object PaletteUtils {

    /** Colors derived from the artwork for the dynamic theme layers. */
    data class ArtworkPalette(
        val topColor: Int,       // rich saturated color — gradient head
        val bottomColor: Int,    // deep color — gradient tail
        val darkSurface: Int,    // readable surface tint for the bottom panel
        val popColor: Int        // vibrant accent (falls back to app accent)
    ) {
        companion object {
            fun fallback(accent: Int, deep: Int): ArtworkPalette =
                ArtworkPalette(accent, deep, deep, accent)
        }
    }

    /**
     * Decodes the artwork at [url] and generates the palette.
     * Runs off the main thread; returns null on any failure.
     */
    suspend fun extractOf(url: String, accent: Int, deep: Int): ArtworkPalette? =
        withContext(Dispatchers.Default) {
            val bitmap = decodeBitmap(url) ?: return@withContext null
            try {
                val palette = Palette.from(bitmap).generate()
                val vibrant = palette.vibrantSwatch
                val darkVibrant = palette.darkVibrantSwatch
                val dominant = palette.dominantSwatch
                val darkMuted = palette.darkMutedSwatch

                ArtworkPalette(
                    topColor = vibrant?.rgb ?: dominant?.rgb ?: accent,
                    bottomColor = darkVibrant?.rgb ?: darkMuted?.rgb ?: dominant?.rgb ?: deep,
                    darkSurface = darkVibrant?.rgb ?: darkMuted?.rgb ?: deep,
                    popColor = vibrant?.rgb ?: dominant?.rgb ?: accent
                )
            } finally {
                bitmap.recycle()
            }
        }

    private suspend fun decodeBitmap(url: String): Bitmap? = withContext(Dispatchers.IO) {
        val context = MySongApp.instance.applicationContext
        val loader: ImageLoader = context.imageLoader
        try {
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false) // Palette needs CPU-readable pixels
                .memoryCacheKey("palette-$url")
                .build()
            (loader.execute(request).drawable as? BitmapDrawable)?.bitmap
        } catch (t: Throwable) {
            null
        }
    }

    /** Vertical tint gradient — colorful head, deep tail — over the artwork. */
    fun gradientOverlayOf(palette: ArtworkPalette): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                withAlpha(palette.topColor, 46),
                withAlpha(palette.bottomColor, 82),
                withAlpha(palette.bottomColor, 96)
            )
        )

    /** Bottom-panel vertical scrim tinted by the artwork's dark surface. */
    fun panelTintOf(palette: ArtworkPalette): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(withAlpha(palette.darkSurface, 10), withAlpha(palette.darkSurface, 92))
        )

    /** Cross-fades the bottom-panel tint derived from [palette] onto [view]. */
    fun animatePanelTint(view: View, palette: ArtworkPalette, durationMs: Long = 420L) {
        fadeTo(view, panelTintOf(palette), durationMs)
    }

    private fun withAlpha(color: Int, percent: Int): Int =
        (color and 0x00FFFFFF) or ((percent * 255 / 100).coerceIn(0, 255) shl 24)

    /**
     * Cross-fades the [drawable] onto [view]: the old layer fades up and
     * blurs out slightly while the new gradient snaps in beneath it.
     */
    fun fadeTo(view: View, drawable: GradientDrawable, durationMs: Long = 420L) {
        val current = view.background as? GradientDrawable
        if (current == null) {
            view.background = drawable
            view.alpha = 0f
            val appear = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = durationMs
                interpolator = DecelerateInterpolator()
                addUpdateListener { view.alpha = (it.animatedValue as Float) * 0.96f }
            }
            appear.start()
            return
        }
        val swap = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = durationMs / 2
            interpolator = DecelerateInterpolator()
            addUpdateListener { t ->
                view.alpha = t.animatedValue as Float
                if (t.animatedFraction >= 1f) view.background = drawable
            }
        }
        swap.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                val rise = ValueAnimator.ofFloat(0f, 0.96f).apply {
                    duration = durationMs / 2
                    interpolator = DecelerateInterpolator()
                    addUpdateListener { v -> view.alpha = v.animatedValue as Float }
                }
                rise.start()
            }
        })
        swap.start()
    }
}