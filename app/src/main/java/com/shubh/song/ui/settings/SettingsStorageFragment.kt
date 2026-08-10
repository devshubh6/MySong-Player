package com.shubh.song.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.shubh.song.R
import com.shubh.song.databinding.FragmentSettingsStorageBinding
import com.shubh.song.ui.common.AppViewModelFactory
import com.shubh.song.ui.common.OverlayNav
import kotlinx.coroutines.launch

/**
 * Storage & Cache sub-screen: live cache size + one-tap clear.
 */
class SettingsStorageFragment : Fragment() {

    companion object {
        const val TAG = "settings_storage"
    }

    private var _binding: FragmentSettingsStorageBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by activityViewModels { AppViewModelFactory }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsStorageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnStorageBack.setOnClickListener { close() }
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = close()
            }
        )

        binding.btnClearCache.setOnClickListener { viewModel.clearCache() }

        viewModel.refreshCacheSize()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.cacheSize.collect { size ->
                    binding.tvCacheSize.text = getString(R.string.cache_size, size)
                }
            }
        }
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