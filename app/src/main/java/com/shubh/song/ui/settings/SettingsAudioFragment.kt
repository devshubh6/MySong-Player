package com.shubh.song.ui.settings

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButton
import com.shubh.song.R
import com.shubh.song.databinding.FragmentSettingsAudioBinding
import com.shubh.song.media.PlaybackManager
import com.shubh.song.ui.common.AppViewModelFactory
import com.shubh.song.ui.common.OverlayNav
import com.shubh.song.util.Constants
import com.shubh.song.util.ThemeManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Audio & Playback sub-system: streaming quality, playback signature modes,
 * crossfade, Auto-Mix and Smart Volume.
 */
class SettingsAudioFragment : Fragment() {

    companion object {
        const val TAG = "settings_audio"
    }

    private var _binding: FragmentSettingsAudioBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by activityViewModels { AppViewModelFactory }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsAudioBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnAudioBack.setOnClickListener { close() }
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = close()
            }
        )

        binding.rbQLow.setOnClickListener { viewModel.setQuality(Constants.QUALITY_LOW) }
        binding.rbQMedium.setOnClickListener { viewModel.setQuality(Constants.QUALITY_MEDIUM) }
        binding.rbQHigh.setOnClickListener { viewModel.setQuality(Constants.QUALITY_HIGH) }

        binding.btnModeNormal.setOnClickListener {
            viewModel.setAudioMode(PlaybackManager.AudioMode.NORMAL)
        }
        binding.btnModeSlowed.setOnClickListener {
            viewModel.setAudioMode(PlaybackManager.AudioMode.SLOWED_REVERB)
        }
        binding.btnModeNightcore.setOnClickListener {
            viewModel.setAudioMode(PlaybackManager.AudioMode.NIGHTCORE)
        }

        binding.sbCrossfade.max = 12
        binding.sbCrossfade.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    binding.tvCrossfadeValue.text = getString(R.string.crossfade_value, progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                viewModel.setCrossfade(seekBar?.progress ?: 0)
            }
        })

        binding.swAutoMix.setOnCheckedChangeListener { _, checked ->
            viewModel.setAutoMix(checked)
        }
        binding.swLoudness.setOnCheckedChangeListener { _, checked ->
            viewModel.setSmartLoudness(checked)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.quality.collectLatest { checkQuality(it) } }
                launch { viewModel.audioMode.collectLatest { applyAudioModeButtons(it) } }
                launch { viewModel.crossfade.collectLatest { bindCrossfade(it) } }
                launch { viewModel.autoMix.collectLatest { it ->
                    if (binding.swAutoMix.isChecked != it) binding.swAutoMix.isChecked = it
                } }
                launch { viewModel.smartLoudness.collectLatest { it ->
                    if (binding.swLoudness.isChecked != it) binding.swLoudness.isChecked = it
                } }
            }
        }

        applyAccentColors()
    }

    private fun applyAccentColors() {
        val accent = ThemeManager.accentColor()
        val muted = ContextCompat.getColor(requireContext(), R.color.text_secondary)
        val tint = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(accent, muted)
        )
        listOf(binding.rbQLow, binding.rbQMedium, binding.rbQHigh).forEach {
            it.buttonTintList = tint
        }
        applyAudioModeButtons(viewModel.audioMode.value)
    }

    private fun checkQuality(quality: String) {
        val radio = when (quality) {
            Constants.QUALITY_LOW -> binding.rbQLow
            Constants.QUALITY_HIGH -> binding.rbQHigh
            else -> binding.rbQMedium
        }
        if (!radio.isChecked) radio.isChecked = true
    }

    private fun applyAudioModeButtons(mode: PlaybackManager.AudioMode) {
        val accent = ThemeManager.accentColor()
        applyModeChip(binding.btnModeNormal, mode == PlaybackManager.AudioMode.NORMAL, accent)
        applyModeChip(
            binding.btnModeSlowed,
            mode == PlaybackManager.AudioMode.SLOWED_REVERB,
            accent
        )
        applyModeChip(binding.btnModeNightcore, mode == PlaybackManager.AudioMode.NIGHTCORE, accent)
    }

    private fun applyModeChip(button: MaterialButton, active: Boolean, accent: Int) {
        button.backgroundTintList = ColorStateList.valueOf(
            if (active) accent else Color.TRANSPARENT
        )
        button.setTextColor(if (active) Color.WHITE else accent)
        button.strokeColor = ColorStateList.valueOf(accent)
        button.strokeWidth = if (active) 0 else 2
    }

    private fun bindCrossfade(seconds: Int) {
        if (binding.sbCrossfade.progress != seconds) binding.sbCrossfade.progress = seconds
        binding.tvCrossfadeValue.text = getString(R.string.crossfade_value, seconds)
    }

    private fun close() {
        try {
            OverlayNav.close(this, this)
        } catch (t: Throwable) {
            // Back navigation must never crash.
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}