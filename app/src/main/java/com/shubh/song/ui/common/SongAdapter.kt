package com.shubh.song.ui.common

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.shubh.song.R
import com.shubh.song.data.model.Song
import com.shubh.song.databinding.ItemSongBinding

class SongAdapter(
    private val onClick: (Song) -> Unit,
    private val onMenu: ((Song) -> Unit)? = null,
    private val onArtistClick: ((Song) -> Unit)? = null
) : RecyclerView.Adapter<SongAdapter.SongHolder>() {

    private val items = mutableListOf<Song>()
    private var currentId: String? = null

    fun setCurrentId(id: String?) {
        if (currentId == id) return
        currentId = id
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun submit(list: List<Song>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongHolder =
        SongHolder(ItemSongBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: SongHolder, position: Int) {
        val song = items[position]
        holder.bind(song)
    }

    inner class SongHolder(val binding: ItemSongBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(song: Song) {
            binding.tvTitle.text = com.shubh.song.util.Ui.html(song.name)
            binding.tvSubtitle.text = listOf(song.artists, song.album)
                .map { com.shubh.song.util.Ui.html(it) }
                .filter { it.isNotBlank() }
                .joinToString(" • ")
            binding.ivArt.load(song.imageUrl) {
                placeholder(R.drawable.bg_placeholder)
                error(R.drawable.bg_placeholder)
                crossfade(250)
            }

            val isCurrent = song.id == currentId
            binding.ivPlaying.visibility = if (isCurrent) android.view.View.VISIBLE else android.view.View.GONE
            binding.tvTitle.setTextColor(
                binding.root.context.getColor(
                    if (isCurrent) R.color.accent else R.color.text_primary
                )
            )

            binding.ivMenu.visibility = if (onMenu != null) android.view.View.VISIBLE else android.view.View.GONE
            binding.ivMenu.setOnClickListener { onMenu?.invoke(song) }
            binding.root.setOnClickListener { onClick(song) }

            // Artist names on song rows are tappable shortcuts to the artist page.
            if (onArtistClick != null) {
                binding.tvSubtitle.isClickable = true
                binding.tvSubtitle.isFocusable = true
                binding.tvSubtitle.setTextColor(
                    com.shubh.song.util.ThemeManager.accentColor()
                )
                val callback = onArtistClick
                binding.tvSubtitle.setOnClickListener { callback.invoke(song) }
            } else {
                binding.tvSubtitle.isClickable = false
                binding.tvSubtitle.isFocusable = false
                binding.tvSubtitle.setOnClickListener(null)
            }
        }
    }
}
