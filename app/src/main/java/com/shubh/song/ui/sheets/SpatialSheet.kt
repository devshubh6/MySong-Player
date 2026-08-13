package com.shubh.song.ui.sheets

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.shubh.song.R
import com.shubh.song.databinding.SheetSpatialBinding
import com.shubh.song.media.BinauralSpatial
import com.shubh.song.media.PlaybackManager
import com.shubh.song.util.LiquidGlass
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Spatial audio selector. Lets the user pick Off / 3D / 8D / 16D / 32D and
 * applies the choice live via [PlaybackManager.setSpatialMode]. The selected
 * chip is highlighted so the active mode is always visible.
 */
class SpatialSheet : BottomSheetDialogFragment() {

    private lateinit var binding: SheetSpatialBinding
    private val modeState = MutableStateFlow(BinauralSpatial.mode)

    private val modeChips: Map<BinauralSpatial.Mode, TextView>
        get() = mapOf(
            BinauralSpatial.Mode.OFF to binding.spatialOff,
            BinauralSpatial.Mode.THREE_D to binding.spatial3d,
            BinauralSpatial.Mode.EIGHT_D to binding.spatial8d,
            BinauralSpatial.Mode.SIXTEEN_D to binding.spatial16d,
            BinauralSpatial.Mode.THIRTY_TWO_D to binding.spatial32d
        )

    override fun onStart() {
        super.onStart()
        LiquidGlass.apply(dialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: android.os.Bundle?
    ): View {
        binding = SheetSpatialBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.spatialOff.setOnClickListener { select(BinauralSpatial.Mode.OFF) }
        binding.spatial3d.setOnClickListener { select(BinauralSpatial.Mode.THREE_D) }
        binding.spatial8d.setOnClickListener { select(BinauralSpatial.Mode.EIGHT_D) }
        binding.spatial16d.setOnClickListener { select(BinauralSpatial.Mode.SIXTEEN_D) }
        binding.spatial32d.setOnClickListener { select(BinauralSpatial.Mode.THIRTY_TWO_D) }

        binding.tvSpatialStatus.text = getString(
            R.string.spatial_current,
            BinauralSpatial.mode.label()
        )

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                modeState.collect { render(it) }
            }
        }
        render(BinauralSpatial.mode)
    }

    private fun select(mode: BinauralSpatial.Mode) {
        PlaybackManager.setSpatialMode(mode)
        modeState.value = mode
        binding.tvSpatialStatus.text = getString(R.string.spatial_current, mode.label())
        render(mode)
    }

    private fun render(mode: BinauralSpatial.Mode) {
        modeChips.forEach { (key, chip) ->
            val selected = key == mode
            chip.background =
                if (selected) accentChipDrawable()
                else requireContext().getDrawable(R.drawable.bg_glass_chip)
            chip.setTextColor(
                requireContext().getColor(
                    if (selected) R.color.text_on_accent else R.color.text_primary
                )
            )
        }
    }

    private fun accentChipDrawable(): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = 22f
            setColor(requireContext().getColor(R.color.accent))
        }
}