package com.shubh.song.ui.common

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.shubh.song.R
import com.shubh.song.data.db.entity.PlaylistEntity
import com.shubh.song.data.model.Song
import com.shubh.song.databinding.ItemEqBandBinding
import com.shubh.song.databinding.ItemLangChipBinding
import com.shubh.song.databinding.ItemPlaylistGridBinding
import com.shubh.song.databinding.ItemSongBinding

class CountryChipAdapter(
    private val onSelect: (String) -> Unit
) : RecyclerView.Adapter<CountryChipAdapter.Holder>() {

    private val items = mutableListOf<String>()
    private var selectedItem = ""

    @SuppressLint("NotifyDataSetChanged")
    fun submit(list: List<String>, current: String) {
        items.clear()
        items.addAll(list)
        selectedItem = current
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemLangChipBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val country = items[position]
        val context = holder.itemView.context
        val isSelected = country == selectedItem
        holder.binding.tvChip.text = country
        holder.binding.tvChip.background =
            if (isSelected) accentChipDrawable(context)
            else context.getDrawable(R.drawable.bg_glass_chip)
        holder.binding.tvChip.setTextColor(
            context.getColor(
                if (isSelected) R.color.text_on_accent else R.color.text_primary
            )
        )
        holder.binding.root.setOnClickListener { onSelect(country) }
    }

    class Holder(val binding: ItemLangChipBinding) : RecyclerView.ViewHolder(binding.root)
}

private fun accentChipDrawable(context: android.content.Context): android.graphics.drawable.Drawable {
    val radius = (22 * context.resources.displayMetrics.density).toFloat()
    return android.graphics.drawable.GradientDrawable().apply {
        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
        cornerRadius = radius
        setColor(com.shubh.song.util.Ui.accentColor(context))
    }
}

class QueueAdapter(
    currentId: String?,
    private val onClick: (Int) -> Unit,
    private val onRemove: (Int) -> Unit
) : RecyclerView.Adapter<QueueAdapter.Holder>() {

    private val items = mutableListOf<Song>()
    private var currentId: String? = currentId

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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemSongBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val song = items[position]
        holder.binding.tvTitle.text = com.shubh.song.util.Ui.html(song.name)
        holder.binding.tvSubtitle.text = song.artists
        holder.binding.ivArt.load(song.imageUrl) {
            placeholder(R.drawable.bg_placeholder)
            error(R.drawable.bg_placeholder)
            crossfade(250)
        }
        holder.binding.ivPlaying.visibility =
            if (song.id == currentId) View.VISIBLE else View.GONE
        holder.binding.tvTitle.setTextColor(
            holder.itemView.context.getColor(
                if (song.id == currentId) R.color.accent else R.color.text_primary
            )
        )
        holder.binding.root.setOnClickListener { onClick(position) }
        holder.binding.root.isLongClickable = true
        holder.binding.root.setOnLongClickListener {
            onRemove(position)
            true
        }
    }

    class Holder(val binding: ItemSongBinding) : RecyclerView.ViewHolder(binding.root)
}

class PlaylistPickerAdapter(
    private val onClick: (PlaylistEntity) -> Unit
) : RecyclerView.Adapter<PlaylistPickerAdapter.Holder>() {

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
        holder.binding.ivMore.visibility = View.GONE
        holder.binding.root.setOnClickListener { onClick(playlist) }
    }

    class Holder(val binding: ItemPlaylistGridBinding) : RecyclerView.ViewHolder(binding.root)
}

class EqBandAdapter(
    private val onBandChanged: (Int, Int) -> Unit
) : RecyclerView.Adapter<EqBandAdapter.Holder>() {

    private val frequencies = mutableListOf<Int>()
    private val levels = mutableListOf<Int>()
    private var enabled = true

    @SuppressLint("NotifyDataSetChanged")
    fun submit(freqs: List<Int>, levels: List<Int>) {
        frequencies.clear()
        frequencies.addAll(freqs)
        this.levels.clear()
        this.levels.addAll(levels)
        notifyDataSetChanged()
    }

    fun setEnabled(enabled: Boolean) {
        if (this.enabled == enabled) return
        this.enabled = enabled
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemEqBandBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = frequencies.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val adapterPosition = holder.bindingAdapterPosition
        if (adapterPosition == RecyclerView.NO_POSITION) return
        holder.binding.tvBandLabel.text = prettyFreq(frequencies[adapterPosition])
        holder.binding.tvBandValue.text = levels.getOrElse(adapterPosition) { 0 }.toString()
        holder.binding.sbBand.isEnabled = enabled
        holder.binding.sbBand.alpha = if (enabled) 1f else 0.4f
        holder.binding.sbBand.setOnSeekBarChangeListener(null)
        holder.binding.sbBand.progress = levels.getOrElse(adapterPosition) { 0 }.coerceIn(0, 1000)
        holder.binding.sbBand.setOnSeekBarChangeListener(
            object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser && enabled) {
                        holder.binding.tvBandValue.text = progress.toString()
                        onBandChanged(adapterPosition, progress)
                    }
                }

                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}

                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
            }
        )
    }

    private fun prettyFreq(freq: Int): String =
        if (freq >= 1000) "${freq / 1000} kHz" else "$freq Hz"

    class Holder(val binding: ItemEqBandBinding) : RecyclerView.ViewHolder(binding.root)
}

class EqPresetAdapter(
    selected: String,
    private val onSelect: (String) -> Unit
) : RecyclerView.Adapter<EqPresetAdapter.Holder>() {

    private val items = mutableListOf<String>()
    private var selected: String = selected
    private var enabled = true

    fun setEnabled(enabled: Boolean) {
        if (this.enabled == enabled) return
        this.enabled = enabled
        notifyDataSetChanged()
    }

    fun setSelected(name: String) {
        if (selected == name) return
        selected = name
        notifyDataSetChanged()
    }

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
        val preset = items[position]
        val context = holder.itemView.context
        val isSelected = preset == selected
        holder.binding.tvChip.text = preset.replaceFirstChar { it.uppercase() }
        holder.binding.tvChip.background =
            if (isSelected) accentChipDrawable(context)
            else context.getDrawable(R.drawable.bg_glass_chip)
        holder.binding.tvChip.setTextColor(
            context.getColor(
                if (isSelected) R.color.text_on_accent else R.color.text_primary
            )
        )
        holder.binding.root.setOnClickListener {
            if (enabled) onSelect(preset)
        }
        holder.binding.tvChip.alpha = if (enabled) 1f else 0.4f
    }

    class Holder(val binding: ItemLangChipBinding) : RecyclerView.ViewHolder(binding.root)
}
