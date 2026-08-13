package com.shubh.song.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import com.shubh.song.BuildConfig
import com.shubh.song.R
import com.shubh.song.databinding.FragmentSettingsAboutBinding
import com.shubh.song.ui.common.OverlayNav
import com.shubh.song.util.Ui

/**
 * About & Developer sub-screen: app version, developer credit, social
 * profiles and library attribution.
 */
class SettingsAboutFragment : Fragment() {

    companion object {
        const val TAG = "settings_about"

        private const val GITHUB_URL = "https://github.com/devshubh6"
        private const val INSTAGRAM_URL =
            "https://www.instagram.com/devshubh66?igsh=MXhocmZjb2FkMXJhZw=="
        private const val X_URL = "https://x.com/Shubhamm65"
    }

    private var _binding: FragmentSettingsAboutBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsAboutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnAboutBack.setOnClickListener { close() }
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = close()
            }
        )

        binding.tvVersion.text = getString(R.string.about_version, appVersion())

        binding.btnSocialGithub.setOnClickListener { openUrl(GITHUB_URL) }
        binding.btnSocialInstagram.setOnClickListener { openUrl(INSTAGRAM_URL) }
        binding.btnSocialX.setOnClickListener { openUrl(X_URL) }
    }

    /** Opens a social profile safely in the external browser/app. */
    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            try {
                Ui.toast(getString(R.string.social_link_error))
            } catch (ignored: Throwable) {
                // No context, no toast — nothing to do.
            }
        }
    }

    private fun appVersion(): String = BuildConfig.VERSION_NAME

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