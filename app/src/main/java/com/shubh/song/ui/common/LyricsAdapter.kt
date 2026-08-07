package com.shubh.song.ui.common

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.shubh.song.R
import com.shubh.song.data.model.LyricsLine
import com.shubh.song.databinding.ItemLyricLineBinding

/** Renders lyric lines; the active line is highlighted in the accent color. */
class LyricsAdapter(
    private val accentColor: Int
) : RecyclerView.Adapter<LyricsAdapter.Holder>() {

    private val lines = mutableListOf<LyricsLine>()
    private var activePosition = -1

    @SuppressLint("NotifyDataSetChanged")
    fun submit(newLines: List<LyricsLine>) {
        lines.clear()
        lines.addAll(newLines)
        activePosition = -1
        notifyDataSetChanged()
    }

    fun setActive(position: Int) {
        if (position == activePosition) return
        activePosition = position
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemLyricLineBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = lines.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val line = lines[position]
        val isActive = position == activePosition
        holder.binding.tvLine.text = line.text
        holder.binding.tvLine.setTextColor(
            if (isActive) accentColor
            else ContextCompat.getColor(holder.itemView.context, R.color.text_secondary)
        )
        holder.binding.tvLine.textSize = if (isActive) 19f else 15f
        holder.binding.tvLine.alpha = if (isActive) 1f else 0.62f
    }

    class Holder(val binding: ItemLyricLineBinding) : RecyclerView.ViewHolder(binding.root)
}
