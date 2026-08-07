package com.shubh.song.ui.common

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.shubh.song.R
import com.shubh.song.data.api.dto.AlbumDto
import com.shubh.song.data.api.dto.ArtistDto
import com.shubh.song.data.api.dto.PlaylistDto
import com.shubh.song.databinding.ItemArtistBinding
import com.shubh.song.databinding.ItemCardBinding

class AlbumAdapter(
    private val onClick: (AlbumDto) -> Unit
) : RecyclerView.Adapter<AlbumAdapter.Holder>() {

    private val items = mutableListOf<AlbumDto>()

    fun submit(list: List<AlbumDto>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemCardBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val album = items[position]
        val image = album.image.orEmpty().lastOrNull { it.url?.isNotBlank() == true }?.url.orEmpty()
        holder.binding.ivArt.load(image) {
            placeholder(R.drawable.bg_placeholder)
            error(R.drawable.bg_placeholder)
            crossfade(250)
        }
        holder.binding.tvTitle.text = com.shubh.song.util.Ui.html(album.name.orEmpty())
        holder.binding.tvSubtitle.text =
            album.artists?.primary.orEmpty().joinToString(", ") { it.name.orEmpty() }
                .ifBlank { album.language?.uppercase().orEmpty() }
        holder.binding.root.setOnClickListener { onClick(album) }
    }

    class Holder(val binding: ItemCardBinding) : RecyclerView.ViewHolder(binding.root)
}

class PlaylistAdapter(
    private val onClick: (PlaylistDto) -> Unit
) : RecyclerView.Adapter<PlaylistAdapter.Holder>() {

    private val items = mutableListOf<PlaylistDto>()

    fun submit(list: List<PlaylistDto>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemCardBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val playlist = items[position]
        val image = playlist.image.orEmpty().lastOrNull { it.url?.isNotBlank() == true }?.url.orEmpty()
        holder.binding.ivArt.load(image) {
            placeholder(R.drawable.bg_placeholder)
            error(R.drawable.bg_placeholder)
            crossfade(250)
        }
        holder.binding.tvTitle.text = com.shubh.song.util.Ui.html(playlist.name.orEmpty())
        holder.binding.tvSubtitle.text =
            playlist.songCount?.let { "$it songs" } ?: playlist.language?.uppercase().orEmpty()
        holder.binding.root.setOnClickListener { onClick(playlist) }
    }

    class Holder(val binding: ItemCardBinding) : RecyclerView.ViewHolder(binding.root)
}

class ArtistAdapter(
    private val onClick: (ArtistDto) -> Unit
) : RecyclerView.Adapter<ArtistAdapter.Holder>() {

    private val items = mutableListOf<ArtistDto>()

    fun submit(list: List<ArtistDto>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemArtistBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val artist = items[position]
        val image = artist.image.orEmpty().lastOrNull { it.url?.isNotBlank() == true }?.url.orEmpty()
        holder.binding.ivArt.load(image) {
            placeholder(R.drawable.bg_placeholder)
            error(R.drawable.bg_placeholder)
            crossfade(250)
        }
        holder.binding.tvName.text = com.shubh.song.util.Ui.html(artist.name.orEmpty())
        holder.binding.root.setOnClickListener { onClick(artist) }
    }

    class Holder(val binding: ItemArtistBinding) : RecyclerView.ViewHolder(binding.root)
}
