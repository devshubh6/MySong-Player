package com.shubh.song.ui.sheets

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.shubh.song.R
import com.shubh.song.databinding.SheetAppLogsBinding
import com.shubh.song.util.AppLogger
import com.shubh.song.util.LiquidGlass
import com.shubh.song.util.Ui

/**
 * Shows the captured app diagnostic log with actions to Save to Downloads,
 * share the file, refresh and clear. Logs are collected in the background by
 * [AppLogger] so any error / warning / playback fault shows up here, along
 * with the most recent crash (so an app that dies on launch reports it too).
 *
 * Filter chips let the user view All logs, only Warnings, or only Errors.
 */
class AppLogsSheet : BottomSheetDialogFragment() {

    private lateinit var binding: SheetAppLogsBinding
    private var currentLevel = AppLogger.Level.ALL

    override fun onStart() {
        super.onStart()
        LiquidGlass.apply(dialog)
        expandSheet()
    }

    /**
     * Forces the sheet to fully expanded so the Save-to-Downloads and Share
     * buttons (at the bottom of the log) are always visible — the default
     * collapsed peek hides them on smaller screens.
     */
    private fun expandSheet() {
        try {
            val sheet = dialog?.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            ) ?: return
            val behavior = (sheet.layoutParams as? androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams)
                ?.behavior as? com.google.android.material.bottomsheet.BottomSheetBehavior<*>
                ?: return
            behavior.skipCollapsed = true
            behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        } catch (_: Throwable) {
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = SheetAppLogsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        renderLog()

        binding.btnExport.setOnClickListener {
            val uri = AppLogger.export(requireContext(), currentLevel)
            if (uri != null) Ui.toast(getString(R.string.app_logs_exported))
        }
        binding.btnShare.setOnClickListener { AppLogger.share(requireContext(), currentLevel) }
        binding.btnCopyLogs.setOnClickListener { copyLogs() }
        binding.btnShareLogs.setOnClickListener { AppLogger.share(requireContext(), currentLevel) }
        binding.btnExportLogs.setOnClickListener {
            val uri = AppLogger.export(requireContext(), currentLevel)
            if (uri != null) Ui.toast(getString(R.string.app_logs_exported))
        }
        binding.btnRefresh.setOnClickListener { renderLog() }
        binding.btnClear.setOnClickListener { confirmClear() }

        binding.filterAll.setOnClickListener { setFilter(AppLogger.Level.ALL) }
        binding.filterWarning.setOnClickListener { setFilter(AppLogger.Level.WARNING) }
        binding.filterError.setOnClickListener { setFilter(AppLogger.Level.ERROR) }
    }

    private fun setFilter(level: Int) {
        currentLevel = level
        styleChip(binding.filterAll, level == AppLogger.Level.ALL)
        styleChip(binding.filterWarning, level == AppLogger.Level.WARNING)
        styleChip(binding.filterError, level == AppLogger.Level.ERROR)
        renderLog()
    }

    private fun styleChip(chip: TextView, active: Boolean) {
        val ctx = requireContext()
        chip.background =
            if (active) ctx.getDrawable(R.drawable.bg_btn_neon)
            else ctx.getDrawable(R.drawable.bg_glass_chip)
        chip.setTextColor(
            ctx.getColor(if (active) R.color.text_on_accent else R.color.text_primary)
        )
    }

    private fun renderLog() {
        val lines = AppLogger.readLogLines(requireContext())
            .filter { matchesLevel(it.level) }

        val sb = SpannableStringBuilder()
        for ((i, line) in lines.withIndex()) {
            // SPAN_EXCLUSIVE_EXCLUSIVE cannot span zero-length text — skip any
            // blank line so we never crash on empty chunks.
            if (line.text.isEmpty()) {
                if (i < lines.size - 1) sb.append("\n")
                continue
            }
            val chunk = if (i < lines.size - 1) line.text + "\n" else line.text
            sb.append(chunk)
            sb.setSpan(
                ForegroundColorSpan(colorForLevel(line.level)),
                sb.length - chunk.length,
                sb.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        binding.tvLogContent.text = sb
        binding.tvLogEmpty.visibility =
            if (lines.isEmpty() || sb.isEmpty()) View.VISIBLE else View.GONE
    }

    /** Copies the current (filtered) log text to the system clipboard. */
    private fun copyLogs() {
        try {
            val text = AppLogger.filteredText(requireContext(), currentLevel)
            if (text.isBlank()) {
                Ui.toast(getString(R.string.app_logs_empty))
                return
            }
            val clipboard =
                requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Mysong App Log", text))
            Ui.toast(getString(R.string.app_logs_copied))
        } catch (_: Throwable) {
        }
    }

    private fun matchesLevel(level: Int): Boolean =
        when (currentLevel) {
            AppLogger.Level.WARNING -> level == AppLogger.Level.WARNING
            AppLogger.Level.ERROR -> level == AppLogger.Level.ERROR
            else -> true
        }

    private fun colorForLevel(level: Int): Int {
        val res = requireContext().resources
        return when (level) {
            AppLogger.Level.ERROR -> res.getColor(R.color.log_error, null)
            AppLogger.Level.WARNING -> res.getColor(R.color.log_warning, null)
            else -> res.getColor(R.color.log_normal, null)
        }
    }

    /** Confirms before wiping the log so a tap can't erase everything. */
    private fun confirmClear() {
        try {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.app_logs_clear_confirm_title)
                .setMessage(R.string.app_logs_clear_confirm_msg)
                .setPositiveButton(R.string.app_logs_clear) { _, _ ->
                    AppLogger.logFile(requireContext()).delete()
                    renderLog()
                    Ui.toast(getString(R.string.app_logs_cleared))
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        } catch (_: Throwable) {
        }
    }
}