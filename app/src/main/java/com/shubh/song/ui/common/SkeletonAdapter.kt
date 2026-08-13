package com.shubh.song.ui.common

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.shubh.song.databinding.ShimmerItemBannerBinding
import com.shubh.song.databinding.ShimmerItemCardRowBinding
import com.shubh.song.databinding.ShimmerItemSongBinding

/**
 * Static skeleton placeholder fed into the home feed while network calls are
 * loading. The whole RecyclerView's alpha is pulsed by the host fragment to
 * produce a Facebook-style shimmer without any third-party dependency.
 */
class SkeletonAdapter : RecyclerView.Adapter<SkeletonAdapter.SkeletonHolder>() {

    private val rows = buildList {
        add(TYPE.BANNER)
        add(TYPE.CARD_ROW)
        add(TYPE.CARD_ROW)
        add(TYPE.CARD_ROW)
        repeat(6) { add(TYPE.SONG) }
    }

    enum class TYPE { BANNER, CARD_ROW, SONG }

    override fun getItemViewType(position: Int): Int = rows[position].ordinal

    override fun getItemCount(): Int = rows.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SkeletonHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (rows.first { it.ordinal == viewType }) {
            TYPE.BANNER -> SkeletonHolder(ShimmerItemBannerBinding.inflate(inflater, parent, false).root)
            TYPE.CARD_ROW -> SkeletonHolder(ShimmerItemCardRowBinding.inflate(inflater, parent, false).root)
            TYPE.SONG -> SkeletonHolder(ShimmerItemSongBinding.inflate(inflater, parent, false).root)
        }
    }

    override fun onBindViewHolder(holder: SkeletonHolder, position: Int) {}

    class SkeletonHolder(view: View) : RecyclerView.ViewHolder(view)
}