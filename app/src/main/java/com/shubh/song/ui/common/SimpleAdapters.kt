package com.shubh.song.ui.common

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.shubh.song.R
import com.shubh.song.databinding.ItemLangChipBinding
import com.shubh.song.databinding.ItemPlaylistGridBinding
import com.shubh.song.databinding.ItemSuggestBinding
import com.shubh.song.data.db.entity.PlaylistEntity

class SuggestAdapter(
    private val onPick: (String) -> Unit
) : RecyclerView.Adapter<SuggestAdapter.Holder>() {

    private val items = mutableListOf<String>()

    @SuppressLint("NotifyDataSetChanged")
    fun submit(list: List<String>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemSuggestBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.binding.tvText.text = items[position]
        holder.binding.root.setOnClickListener { onPick(items[position]) }
    }

    class Holder(val binding: ItemSuggestBinding) : RecyclerView.ViewHolder(binding.root)
}

class PlaylistGridAdapter(
    private val onClick: (PlaylistEntity) -> Unit,
    private val onMore: (PlaylistEntity) -> Unit
) : RecyclerView.Adapter<PlaylistGridAdapter.Holder>() {

    private val items = mutableListOf<PlaylistEntity>()

    @SuppressLint("NotifyDataSetChanged")
    fun submit(list: List<PlaylistEntity>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemPlaylistGridBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val playlist = items[position]
        holder.binding.tvName.text = playlist.name
        com.shubh.song.util.ThemeManager.applyCardTint(holder.binding.root)
        holder.binding.root.setOnClickListener { onClick(playlist) }
        holder.binding.ivMore.setOnClickListener { onMore(playlist) }
    }

    class Holder(val binding: ItemPlaylistGridBinding) : RecyclerView.ViewHolder(binding.root)
}

class LangChipAdapter(
    private val selected: () -> Set<String>,
    private val onToggle: (String) -> Unit
) : RecyclerView.Adapter<LangChipAdapter.Holder>() {

    private val items = mutableListOf<String>()

    @SuppressLint("NotifyDataSetChanged")
    fun submit(list: List<String>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemLangChipBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val lang = items[position]
        val context = holder.itemView.context
        val isSelected = lang in selected()
        holder.binding.tvChip.text = lang.replaceFirstChar { it.uppercase() }
        holder.binding.tvChip.background =
            if (isSelected) accentChip(context)
            else context.getDrawable(R.drawable.bg_glass_chip)
        holder.binding.tvChip.setTextColor(
            context.getColor(if (isSelected) R.color.text_on_accent else R.color.text_primary)
        )
        holder.binding.root.setOnClickListener { onToggle(lang) }
    }

    class Holder(val binding: ItemLangChipBinding) : RecyclerView.ViewHolder(binding.root)
}

/** Builds a rounded chip filled with the currently selected accent color. */
private fun accentChip(context: android.content.Context): android.graphics.drawable.Drawable {
    val radius = (22 * context.resources.displayMetrics.density).toFloat()
    return android.graphics.drawable.GradientDrawable().apply {
        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
        cornerRadius = radius
        setColor(com.shubh.song.util.Ui.accentColor(context))
    }
}
