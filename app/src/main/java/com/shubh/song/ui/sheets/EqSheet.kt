package com.shubh.song.ui.sheets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.shubh.song.R
import com.shubh.song.databinding.SheetEqualizerBinding
import com.shubh.song.media.EqualizerController
import com.shubh.song.ui.common.EqBandAdapter
import com.shubh.song.ui.common.EqPresetAdapter
import kotlinx.coroutines.launch

class EqSheet : BottomSheetDialogFragment() {

    private lateinit var binding: SheetEqualizerBinding
    private val bandAdapter = EqBandAdapter { band, level ->
        EqualizerController.setBandLevel(band, level)
    }
    private val presetAdapter = EqPresetAdapter(
        selected = "flat",
        onSelect = { EqualizerController.applyPreset(it) }
    )
    private var syncingSwitch = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = SheetEqualizerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvEqPresets.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvEqPresets.adapter = presetAdapter
        presetAdapter.submit(
            listOf("flat", "bass", "treble", "pop", "rock", "classical", "jazz")
        )

        binding.rvEqBands.layoutManager = LinearLayoutManager(requireContext())
        binding.rvEqBands.adapter = bandAdapter

        binding.swEqMaster.setOnCheckedChangeListener { _, checked ->
            if (syncingSwitch) return@setOnCheckedChangeListener
            EqualizerController.setEnabled(checked)
        }

        binding.sbBass.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && seekBar?.isEnabled == true) EqualizerController.setBassBoost(progress)
            }

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        binding.sbVirtual.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && seekBar?.isEnabled == true) EqualizerController.setVirtualizer(progress)
            }

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                EqualizerController.state.collect { state ->
                    syncingSwitch = true
                    binding.swEqMaster.isChecked = state.enabled
                    syncingSwitch = false
                    val active = state.enabled && state.available
                    bandAdapter.submit(state.bandFrequencies, state.levels)
                    bandAdapter.setEnabled(active)
                    presetAdapter.setSelected(state.preset)
                    presetAdapter.setEnabled(active)
                    binding.tvEqPreset.text = getString(R.string.eq_preset, state.preset)
                    binding.sbBass.isEnabled = active
                    binding.sbVirtual.isEnabled = active
                    binding.sbBass.alpha = if (active) 1f else 0.4f
                    binding.sbVirtual.alpha = if (active) 1f else 0.4f
                    binding.sbBass.progress = state.bassBoost
                    binding.sbVirtual.progress = state.virtualizer
                }
            }
        }
    }
}