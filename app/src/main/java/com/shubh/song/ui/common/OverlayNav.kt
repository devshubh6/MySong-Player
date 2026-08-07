package com.shubh.song.ui.common

import androidx.fragment.app.Fragment
import com.shubh.song.MainActivity
import com.shubh.song.R

/**
 * Hosts full-screen overlay pages (search, artist, album) on top of the home
 * container with the standard slide transition. The activity chrome (bottom
 * nav + mini player) is hidden while any overlay is open and restored when
 * the last one closes.
 */
object OverlayNav {

    val HOST_ID = R.id.nav_host

    /** Slides [overlay] in on top of the current screen (tag-guarded). */
    fun open(host: Fragment, overlay: Fragment, tag: String) {
        val manager = host.parentFragmentManager
        if (manager.findFragmentByTag(tag) != null) return
        (host.activity as? MainActivity)?.setSearchMode(true)
        manager.beginTransaction()
            .setCustomAnimations(
                R.anim.slide_in_right,
                R.anim.slide_out_left,
                R.anim.slide_in_left,
                R.anim.slide_out_right
            )
            .add(HOST_ID, overlay, tag)
            .commitAllowingStateLoss()
    }

    /** Removes [overlay] with the reverse slide and releases the chrome. */
    fun close(host: Fragment, overlay: Fragment) {
        val manager = host.parentFragmentManager
        if (!overlay.isAdded) return
        (host.activity as? MainActivity)?.setSearchMode(false)
        manager.beginTransaction()
            .setCustomAnimations(
                R.anim.slide_in_left,
                R.anim.slide_out_right,
                R.anim.slide_in_right,
                R.anim.slide_out_left
            )
            .remove(overlay)
            .commitAllowingStateLoss()
    }
}
