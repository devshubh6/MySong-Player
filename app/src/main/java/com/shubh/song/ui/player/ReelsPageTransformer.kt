package com.shubh.song.ui.player

import android.view.View
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.abs

/**
 * Reels / Shorts-style depth transition for the vertical player pager.
 *
 * The current page stays full-frame while sliding out with a fade, and the
 * incoming page rises into place scaled down to 85% — growing to full size as
 * it settles — exactly like Instagram Reels vertical swiping.
 */
class ReelsPageTransformer : ViewPager2.PageTransformer {

    override fun transformPage(page: View, position: Float) {
        page.apply {
            when {
                position < -1 -> alpha = 0f
                position <= 0 -> {
                    alpha = 1f + position
                    translationY = 0f
                    scaleX = 1f
                    scaleY = 1f
                }
                position <= 1 -> {
                    alpha = 1f - position
                    translationY = height * -position
                    val scaleFactor = 0.85f + (1f - 0.85f) * (1f - abs(position))
                    scaleX = scaleFactor
                    scaleY = scaleFactor
                }
                else -> alpha = 0f
            }
        }
    }
}