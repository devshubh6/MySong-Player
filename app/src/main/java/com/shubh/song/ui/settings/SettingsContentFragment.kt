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
import androidx.recyclerview.widget.LinearLayoutManager
import com.shubh.song.databinding.FragmentSettingsContentBinding
import com.shubh.song.ui.common.AppViewModelFactory
import com.shubh.song.ui.common.CountryChipAdapter
import com.shubh.song.ui.common.LangChipAdapter
import com.shubh.song.ui.common.OverlayNav
import com.shubh.song.util.Constants
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Content & Region sub-screen: language filters and country selection chips.
 */
class SettingsContentFragment : Fragment() {

    companion object {
        const val TAG = "settings_content"
    }

    private var _binding: FragmentSettingsContentBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by activityViewModels { AppViewModelFactory }

    private val langAdapter = LangChipAdapter(
        selected = { viewModel.languages.value },
        onToggle = { viewModel.toggleLanguage(it) }
    )

    private val countryAdapter = CountryChipAdapter { country ->
        viewModel.setCountry(country)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsContentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnContentBack.setOnClickListener { close() }
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = close()
            }
        )

        binding.rvLangs.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvLangs.adapter = langAdapter

        binding.rvCountries.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvCountries.adapter = countryAdapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.languages.collectLatest { langAdapter.submit(Constants.LANGUAGES) } }
                launch { viewModel.country.collectLatest {
                    countryAdapter.submit(Constants.COUNTRIES, it)
                } }
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