package com.shubh.song.ui.player

import android.view.View
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.abs

/**
 * Reels / Shorts-style depth transition for the vertical player pager.
 *
 * The pages themselves keep their natural vertical slide (the adjacent song
 * artwork follows the finger), while this transformer adds a subtle depth
 * scale: the settled page is full-size and the incoming/outgoing pages are
 * slightly smaller. No opacity fade — both reels stay visible during the
 * gesture, exactly like short-form video feeds.
 */
class ReelsPageTransformer : ViewPager2.PageTransformer {

    override fun transformPage(page: View, position: Float) {
        val distance = abs(position).coerceAtMost(1f)
        val scale = MIN_SCALE + (1f - MIN_SCALE) * (1f - distance)

        page.alpha = 1f
        page.translationY = 0f
        page.scaleX = scale
        page.scaleY = scale
    }

    private companion object {
        /** Artwork scale of the page that is one full swipe away. */
        const val MIN_SCALE = 0.92f
    }
}
