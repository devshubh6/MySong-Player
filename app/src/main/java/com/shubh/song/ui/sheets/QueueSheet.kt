package com.shubh.song.ui.sheets

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.shubh.song.data.model.Song
import com.shubh.song.databinding.SheetQueueBinding
import com.shubh.song.media.PlaybackManager
import com.shubh.song.ui.common.QueueAdapter
import com.shubh.song.util.Ui
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class QueueSheet : BottomSheetDialogFragment() {

    private lateinit var binding: SheetQueueBinding
    private var adapter: QueueAdapter? = null

    private val filterChips: List<String> by lazy {
        listOf(
            getString(com.shubh.song.R.string.filter_all),
            "Remix", "Hindi", "English", "Bhojpuri", "Punjabi", "Pop", "Devotional"
        )
    }

    private var fullQueue: List<Song> = emptyList()
    private var activeFilter: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = SheetQueueBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnQueueClose.setOnClickListener { dismiss() }
        binding.tvQueueTitle.text = getString(com.shubh.song.R.string.player_queue)
        activeFilter = getString(com.shubh.song.R.string.filter_all)

        binding.rvQueue.layoutManager = LinearLayoutManager(requireContext())
        adapter = QueueAdapter(
            currentId = PlaybackManager.uiState.value.currentSong?.id,
            onClick = { displayIndex -> seekToReal(displayIndex) },
            onRemove = { displayIndex -> removeReal(displayIndex) }
        )
        binding.rvQueue.adapter = adapter

        buildFilterChips()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                PlaybackManager.uiState.collectLatest { state ->
                    fullQueue = state.queue
                    val (shown, real) = filter(activeFilter)
                    adapter?.setCurrentId(state.currentSong?.id)
                    adapter?.submit(shown)
                    binding.tvQueueCount.text = shown.size.toString()
                    binding.tvQueueEmpty.visibility =
                        if (shown.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun buildFilterChips() {
        val chipGroup = binding.chipGroupQueue
        chipGroup.isSingleSelection = true
        filterChips.forEach { tag ->
            val chip = Chip(requireContext()).apply {
                text = tag
                isCheckable = true
                isChecked = tag == activeFilter
                chipCornerRadius = 18 * resources.displayMetrics.density
                chipStrokeWidth = 0f
                chipBackgroundColor = ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(Ui.accentColor(context), android.graphics.Color.TRANSPARENT)
                )
                setTextColor(
                    ColorStateList(
                        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                        intArrayOf(
                            context.getColor(com.shubh.song.R.color.text_on_accent),
                            context.getColor(com.shubh.song.R.color.text_secondary)
                        )
                    )
                )
            }
            chipGroup.addView(chip)
        }
        chipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            val id = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val chip = group.findViewById<Chip>(id)
            activeFilter = chip.text.toString()
            val (shown, _) = filter(activeFilter)
            adapter?.setCurrentId(PlaybackManager.uiState.value.currentSong?.id)
            adapter?.submit(shown)
            binding.tvQueueCount.text = shown.size.toString()
            binding.tvQueueEmpty.visibility =
                if (shown.isEmpty()) View.VISIBLE else View.GONE
        }
        applyChipTint(chipGroup)
    }

    private fun applyChipTint(group: com.google.android.material.chip.ChipGroup) {
        for (i in 0 until group.childCount) {
            val chip = group.getChildAt(i) as? Chip ?: continue
            chip.chipBackgroundColor = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(Ui.accentColor(requireContext()), android.graphics.Color.TRANSPARENT)
            )
            chip.setTextColor(
                ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(
                        requireContext().getColor(com.shubh.song.R.color.text_on_accent),
                        requireContext().getColor(com.shubh.song.R.color.text_secondary)
                    )
                )
            )
        }
    }

    /** Returns displayed songs plus the original queue indices behind them. */
    private fun filter(tag: String): Pair<List<Song>, List<Int>> {
        val all = getString(com.shubh.song.R.string.filter_all)
        val shown = mutableListOf<Song>()
        val real = mutableListOf<Int>()
        fullQueue.forEachIndexed { index, song ->
            if (matches(song, tag) || tag == all) {
                shown.add(song)
                real.add(index)
            }
        }
        return shown to real
    }

    private fun matches(song: Song, tag: String): Boolean {
        val t = tag.lowercase()
        val lang = song.language.lowercase()
        return when {
            lang.contains(t) -> true
            t in setOf("remix", "pop", "devotional") -> song.name.lowercase().contains(t)
            else -> false
        }
    }

    private fun lastRealIndices(): List<Int> = filter(activeFilter).second

    private fun seekToReal(displayIndex: Int) {
        val real = lastRealIndices()
        if (displayIndex in real.indices) {
            PlaybackManager.seekToIndex(real[displayIndex])
        }
    }

    private fun removeReal(displayIndex: Int) {
        val real = lastRealIndices()
        if (displayIndex in real.indices) {
            PlaybackManager.removeFromQueue(real[displayIndex])
        }
    }

    override fun onDestroyView() {
        adapter = null
        super.onDestroyView()
    }
}