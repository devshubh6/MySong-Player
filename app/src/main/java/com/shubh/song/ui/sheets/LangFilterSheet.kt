package com.shubh.song.ui.sheets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.shubh.song.MySongApp
import com.shubh.song.R
import com.shubh.song.databinding.SheetLangFilterBinding
import com.shubh.song.ui.common.LangChipAdapter
import com.shubh.song.util.Constants
import com.shubh.song.util.Ui

/** Multi-language picker shown from the Home header; applies a live feed filter. */
class LangFilterSheet : BottomSheetDialogFragment() {

    private lateinit var binding: SheetLangFilterBinding
    private val selected = MySongApp.instance.settingsRepository.languages.toMutableSet()
    private lateinit var adapter: LangChipAdapter

    private var onApply: ((Set<String>) -> Unit)? = null

    fun setOnApply(callback: (Set<String>) -> Unit): LangFilterSheet {
        onApply = callback
        return this
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = SheetLangFilterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnLangClose.setOnClickListener { dismiss() }

        adapter = LangChipAdapter(
            selected = { selected },
            onToggle = { lang -> toggleLang(lang) }
        )

        binding.rvLangChips.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvLangChips.adapter = adapter
        adapter.submit(Constants.LANGUAGES)

        binding.btnLangApply.setOnClickListener {
            onApply?.invoke(selected.toSet())
            dismiss()
        }

        binding.btnLangApply.backgroundTintList =
            android.content.res.ColorStateList.valueOf(Ui.accentColor(requireContext()))
    }

    private fun toggleLang(lang: String) {
        if (!selected.add(lang)) selected.remove(lang)
        adapter.submit(Constants.LANGUAGES)
    }

    companion object {
        fun newInstance(): LangFilterSheet = LangFilterSheet()
    }
}