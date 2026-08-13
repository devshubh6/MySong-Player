package com.shubh.song.ui.karaoke

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.shubh.song.R
import com.shubh.song.data.model.LyricsLine
import com.shubh.song.databinding.ItemLyricLineBinding

/**
 * Karaoke-style lyrics adapter.
 *
 * The line that is currently playing is pushed to the center of the sheet —
 * enlarged, bold, fully opaque and glowing in the accent color — while the
 * rest of the text stays small and dimmed for a classic karaoke reading
 * track. Lines without timestamps (timeMs = [Long.MAX_VALUE]) never
 * activate.
 */
class LyricsAdapter(
    private val accentColor: Int
) : RecyclerView.Adapter<LyricsAdapter.Holder>() {

    private val lines = mutableListOf<LyricsLine>()
    private var activePosition = -1

    @SuppressLint("NotifyDataSetChanged")
    fun submit(lyrics: List<LyricsLine>) {
        lines.clear()
        lines.addAll(lyrics)
        activePosition = -1
        notifyDataSetChanged()
    }

    fun setActive(position: Int) {
        val clamped = if (activePosition >= 0 && activePosition < lines.size) activePosition else -1
        if (clamped == position) return
        activePosition = position
        if (clamped >= 0) notifyItemChanged(clamped)
        if (position >= 0) notifyItemChanged(position)
    }

    /** True when the current track uses timed lines (karaoke mode). */
    fun isSynced(): Boolean = lines.any { it.timeMs != Long.MAX_VALUE }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemLyricLineBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = lines.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val isActive = position == activePosition
        val tv = holder.binding.tvLine
        tv.text = lines[position].text
        tv.textSize = if (isActive) 20f else 15f
        tv.alpha = if (isActive) 1f else 0.55f
        tv.setTextColor(
            if (isActive) {
                accentColor
            } else {
                ContextCompat.getColor(holder.itemView.context, R.color.text_secondary)
            }
        )
        tv.setShadowLayer(
            if (isActive) 18f else 0f,
            0f,
            0f,
            if (isActive) accentHexWithAlpha(accentColor) else Color.TRANSPARENT
        )
    }

    /** Accent at ~35% alpha — soft neon glow behind the playing line. */
    private fun accentHexWithAlpha(color: Int): Int =
        (color and 0x00FFFFFF) or (0x55 shl 24)

    /** Centers [position] in the [recycler] viewport with a smooth glide. */
    fun scrollToActive(recycler: RecyclerView, position: Int) {
        if (position < 0) return
        val manager = recycler.layoutManager as? LinearLayoutManager ?: return
        val offset = (recycler.height / 2).coerceAtLeast(0)
        recycler.post {
            manager.smoothScrollToPosition(recycler, RecyclerView.State(), position)
            // after the glide lands, snap the active line to the vertical center
            manager.scrollToPositionWithOffset(position, offset)
        }
    }

    class Holder(val binding: ItemLyricLineBinding) : RecyclerView.ViewHolder(binding.root)
}