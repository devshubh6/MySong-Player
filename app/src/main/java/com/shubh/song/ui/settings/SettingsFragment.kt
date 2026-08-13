package com.shubh.song.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.shubh.song.BuildConfig
import com.shubh.song.R
import com.shubh.song.databinding.FragmentSettingsBinding
import com.shubh.song.ui.common.AppViewModelFactory
import com.shubh.song.ui.common.OverlayNav
import com.shubh.song.ui.sheets.AppLogsSheet
import com.shubh.song.ui.sheets.UpdateBottomSheetFragment
import com.shubh.song.util.ThemeManager

/**
 * Settings hub: a clean category list. Each card slides in its dedicated
 * sub-screen (Appearance / Audio & Playback / Content & Region /
 * Storage & Cache / Extras & Tools / About & Developer) via OverlayNav,
 * so the main page never grows into one endless scroll.
 */
class SettingsFragment : Fragment() {

    companion object {
        const val TAG = "settings"
    }

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by activityViewModels { AppViewModelFactory }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ThemeManager.applyAppBackground(binding.root)
        // Keep the shared ViewModel alive & warm (cache size probe, etc.).
        viewModel.refreshCacheSize()

        binding.cardBtnAppearance.setOnClickListener {
            openSub(SettingsAppearanceFragment(), SettingsAppearanceFragment.TAG)
        }
        binding.cardBtnAudio.setOnClickListener {
            openSub(SettingsAudioFragment(), SettingsAudioFragment.TAG)
        }
        binding.cardBtnContent.setOnClickListener {
            openSub(SettingsContentFragment(), SettingsContentFragment.TAG)
        }
        binding.cardBtnStorage.setOnClickListener {
            openSub(SettingsStorageFragment(), SettingsStorageFragment.TAG)
        }
        binding.cardBtnExtras.setOnClickListener {
            openSub(SettingsExtrasFragment(), SettingsExtrasFragment.TAG)
        }
        binding.cardBtnAbout.setOnClickListener {
            openSub(SettingsAboutFragment(), SettingsAboutFragment.TAG)
        }
        binding.cardBtnAppLogs.setOnClickListener {
            showLogsSheet()
        }
        binding.cardBtnUpdates.setOnClickListener {
            showUpdateSheet()
        }
        binding.tvVersionFooter.text = getString(R.string.about_version, BuildConfig.VERSION_NAME)
    }

    /** App Updates sheet (version + GitHub releases link). */
    private fun showUpdateSheet() {
        try {
            if (!isAdded) return
            UpdateBottomSheetFragment().show(childFragmentManager, UpdateBottomSheetFragment.TAG)
        } catch (t: Throwable) {
            // Dialog presentation must never crash the app.
        }
    }

    /** App diagnostic log sheet (errors, warnings, playback faults). */
    private fun showLogsSheet() {
        try {
            if (!isAdded) return
            AppLogsSheet().show(childFragmentManager, "app_logs")
        } catch (t: Throwable) {
            // Dialog presentation must never crash the app.
        }
    }

    /** Crash-proof sub-screen presentation (tag-guarded, no-op on faults). */
    private fun openSub(sub: Fragment, tag: String) {
        try {
            if (!isAdded) return
            OverlayNav.open(this, sub, tag)
        } catch (t: Throwable) {
            // Navigation must never crash the app.
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}