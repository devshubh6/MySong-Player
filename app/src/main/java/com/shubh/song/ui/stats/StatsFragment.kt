package com.shubh.song.ui.stats

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.shubh.song.MySongApp
import com.shubh.song.R
import com.shubh.song.data.db.AppDatabase
import com.shubh.song.data.db.entity.PlaybackStatsEntity
import com.shubh.song.data.db.entity.PlayedDayEntity
import com.shubh.song.data.repository.StatsRepository
import com.shubh.song.databinding.FragmentStatsBinding
import com.shubh.song.databinding.ItemStatsSongBinding
import com.shubh.song.ui.common.OverlayNav
import com.shubh.song.util.Ui
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * "My Stats & Recap" — the listening dashboard:
 *  - total listening time + total play count
 *  - top artist (by play count)
 *  - top 5 songs (ranked, tap-free summary rows)
 *  - a 7-day weekly activity bar chart driven by per-day aggregates.
 *
 * All data comes from the Room playback-history tables via Flow, so the
 * screen refreshes itself the moment a new commit lands.
 */
class StatsFragment : Fragment() {

    companion object {
        const val TAG = "stats"
    }

    private var _binding: FragmentStatsBinding? = null
    private val binding get() = _binding!!
    private var songAdapter: SongAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnStatsBack.setOnClickListener { OverlayNav.close(this, this) }
        binding.btnStatsClear.setOnClickListener { confirmReset() }

        binding.rvTopSongs.layoutManager = LinearLayoutManager(requireContext())
        songAdapter = SongAdapter()
        binding.rvTopSongs.adapter = songAdapter

        val dao = AppDatabase.get(requireContext()).playbackStatsDao()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val accent = Ui.accentColor(requireContext())
                launch {
                    dao.observeTotalSeconds().collect { totalSeconds ->
                        val hours = totalSeconds / 3600.0
                        binding.tvTotalHours.text = String.format(Locale.US, "%.1f", hours)
                        binding.tvTotalHours.setTextColor(accent)
                        onAnyData(totalSeconds)
                    }
                }

                launch {
                    dao.observeTotalPlays().collect { plays ->
                        binding.tvTotalPlays.text = "$plays"
                    }
                }
                launch {
                    dao.observeTopArtist().collect { row ->
                        if (row == null) {
                            binding.cardTopArtist.visibility = View.GONE
                        } else {
                            binding.cardTopArtist.visibility = View.VISIBLE
                            binding.tvTopArtistName.text = row.name
                            binding.tvTopArtistPlays.text = getString(R.string.stats_plays_count, row.playCount)
                            binding.tvTopArtistPlays.setTextColor(accent)
                        }
                    }
                }
                launch {
                    dao.observeStats().collect { stats ->
                        songAdapter?.submit(stats.take(5))
                    }
                }
                launch {
                    dao.observeDays(weekStartKey()).collect { days ->
                        renderWeek(days)
                    }
                }
            }
        }
    }

    // ---- rendering ----

    private fun onAnyData(totalSeconds: Long) {
        val hasData = totalSeconds > 0L
        binding.blockEmpty.visibility = if (hasData) View.GONE else View.VISIBLE
        binding.tvWeekEmpty.visibility = if (hasData) View.GONE else View.VISIBLE
    }

    private fun weekStartKey(): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -13)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }

    /** Builds the 7-day bar chart for the current ISO week (Mon → Sun). */
    private fun renderWeek(days: List<com.shubh.song.data.db.entity.PlayedDayEntity>) {
        val chart = binding.weeklyChart
        chart.removeAllViews()
        val accent = Ui.accentColor(requireContext())
        val max = (days.maxOfOrNull { it.seconds } ?: 0L).coerceAtLeast(1L)

        val cal = Calendar.getInstance()
        while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dayNames = intArrayOf(
            R.string.day_short_mon, R.string.day_short_tue, R.string.day_short_wed,
            R.string.day_short_thu, R.string.day_short_fri, R.string.day_short_sat,
            R.string.day_short_sun
        )

        for (i in 0 until 7) {
            val key = format.format(cal.time)
            val seconds = days.firstOrNull { it.dateKey == key }?.seconds ?: 0L
            chart.addView(dayColumn(getString(dayNames[i]), seconds, max, accent))
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
    }

    private fun dayColumn(label: String, seconds: Long, max: Long, accent: Int): View {
        val density = resources.displayMetrics.density
        val column = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val track = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (104 * density).toInt()
            )
        }
        val barHeight = if (seconds <= 0L) {
            (3 * density).toInt()
        } else {
            (96f * density * seconds.coerceAtLeast(1L).toFloat() / max).toInt().coerceAtLeast((6 * density).toInt())
        }
        val bar = View(requireContext()).apply {
            val helper = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = (5 * density)
                setColor(
                    if (seconds <= 0L) ColorUtils.setAlphaComponent(accent, 30)
                    else accent
                )
            }
            background = helper
            layoutParams = LinearLayout.LayoutParams(
                (11 * density).toInt(),
                barHeight
            )
        }
        track.addView(bar)
        column.addView(track)

        val labelView = TextView(requireContext()).apply {
            text = label
            setTextColor(
                ContextCompat.getColor(requireContext(), R.color.text_secondary)
            )
            textSize = 8f
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (4 * density).toInt() }
        }
        column.addView(labelView)
        return column
    }

    private fun confirmReset() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.stats_clear)
            .setMessage(R.string.stats_cleared_confirm)
            .setPositiveButton(R.string.reset) { _, _ ->
                StatsRepository.clearAll()
                Ui.toast(getString(R.string.stats_cleared))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        _binding = null
        songAdapter = null
        super.onDestroyView()
    }

    // ---- top songs list ----

    private class SongAdapter : RecyclerView.Adapter<SongAdapter.Holder>() {

        private var rows = listOf<PlaybackStatsEntity>()
        private val accentColor by lazy { Ui.accentColor(MySongApp.instance) }

        fun submit(stats: List<PlaybackStatsEntity>) {
            if (rows == stats) return
            rows = stats
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(ItemStatsSongBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount(): Int = rows.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val row = rows[position]
            holder.binding.tvRank.text = "${position + 1}"
            holder.binding.tvRank.setTextColor(accentColor)
            holder.binding.tvSongTitle.text = row.songName.ifBlank { row.trackId }
            holder.binding.tvSongArtist.text = row.artistName.ifBlank { "-" }
            holder.binding.tvSongPlays.text =
                holder.itemView.context.getString(R.string.stats_plays_count, row.playCount)
        }

        class Holder(val binding: ItemStatsSongBinding) : RecyclerView.ViewHolder(binding.root)
    }
}