package com.shubh.song.ui.settings

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.shubh.song.MainActivity
import com.shubh.song.MySongApp
import com.shubh.song.R
import com.shubh.song.databinding.FragmentSettingsAppearanceBinding
import com.shubh.song.ui.common.AppViewModelFactory
import com.shubh.song.ui.common.OverlayNav
import com.shubh.song.util.ThemeManager
import com.shubh.song.util.Ui
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Appearance sub-screen: theme mode and accent color.
 */
class SettingsAppearanceFragment : Fragment() {

    companion object {
        const val TAG = "settings_appearance"
    }

    private var _binding: FragmentSettingsAppearanceBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by activityViewModels { AppViewModelFactory }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsAppearanceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnAppearanceBack.setOnClickListener { close() }
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = close()
            }
        )

        binding.rbDark.setOnClickListener { viewModel.setTheme("dark") }
        binding.rbLight.setOnClickListener { viewModel.setTheme("light") }
        binding.rbSystem.setOnClickListener { viewModel.setTheme("system") }

        val buttons = mapOf(
            binding.accentBtnGreen to "green",
            binding.accentBtnPurple to "purple",
            binding.accentBtnPink to "pink",
            binding.accentBtnCyan to "cyan",
            binding.accentBtnAmber to "amber",
            binding.accentBtnRed to "red"
        )
        buttons.forEach { (button, name) ->
            val color = Ui.accentRes(name).let { ContextCompat.getColor(requireContext(), it) }
            Ui.tintCircle(button, color)
            button.setOnClickListener {
                viewModel.setAccent(name)
                (activity as? MainActivity)?.refreshAccent()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.theme.collectLatest { checkTheme(it) } }
                launch { MySongApp.instance.accent.collect { applyAccentColors() } }
            }
        }

        applyAccentColors()
    }

    /** Re-tints radios + accent circles when the accent color changes. */
    private fun applyAccentColors() {
        val accent = ThemeManager.accentColor()
        val muted = ContextCompat.getColor(requireContext(), R.color.text_secondary)
        val tint = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(accent, muted)
        )
        listOf(binding.rbDark, binding.rbLight, binding.rbSystem).forEach {
            it.buttonTintList = tint
        }
        val buttons = listOf(
            binding.accentBtnGreen, binding.accentBtnPurple, binding.accentBtnPink,
            binding.accentBtnCyan, binding.accentBtnAmber, binding.accentBtnRed
        )
        buttons.forEachIndexed { i, button ->
            val name = listOf("green", "purple", "pink", "cyan", "amber", "red")[i]
            val color = Ui.accentRes(name).let { ContextCompat.getColor(requireContext(), it) }
            Ui.tintCircle(button, color)
        }
    }

    private fun checkTheme(mode: String) {
        val radio = when (mode) {
            "light" -> binding.rbLight
            "system" -> binding.rbSystem
            else -> binding.rbDark
        }
        if (!radio.isChecked) radio.isChecked = true
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