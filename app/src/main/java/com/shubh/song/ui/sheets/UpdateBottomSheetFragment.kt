package com.shubh.song.ui.sheets

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.shubh.song.BuildConfig
import com.shubh.song.R
import com.shubh.song.databinding.SheetUpdateBinding
import com.shubh.song.util.LiquidGlass

/**
 * App Updates sheet: shows the installed version and hands off to the
 * GitHub releases page for new builds.
 */
class UpdateBottomSheetFragment : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "update_sheet"
        private const val RELEASES_URL = "https://github.com/devshubh6/MySong-Player/releases"
    }

    private lateinit var binding: SheetUpdateBinding

    override fun onStart() {
        super.onStart()
        LiquidGlass.apply(dialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = SheetUpdateBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvUpdateVersion.text = getString(R.string.update_current_version, installedVersion())

        binding.btnCheckUpdates.setOnClickListener {
            checkForUpdates()
        }
    }

    /** Installed version straight from BuildConfig (e.g. v1.0.1). */
    private fun installedVersion(): String = "v${BuildConfig.VERSION_NAME}"

    /** Opens the GitHub releases page in the default browser. */
    private fun checkForUpdates() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_URL)))
        } catch (t: Throwable) {
            try {
                Toast.makeText(
                    requireContext(),
                    R.string.update_no_browser,
                    Toast.LENGTH_SHORT
                ).show()
            } catch (ignored: Throwable) {
                // No context, no dialog — nothing to do.
            }
        }
    }
}