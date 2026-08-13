package com.shubh.song.ui.sheets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.shubh.song.R
import com.shubh.song.databinding.SheetSleepBinding
import com.shubh.song.media.SleepTimer
import com.shubh.song.util.LiquidGlass
import com.shubh.song.util.Ui
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SleepSheet : BottomSheetDialogFragment() {

    private lateinit var binding: SheetSleepBinding

    override fun onStart() {
        super.onStart()
        LiquidGlass.apply(dialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = SheetSleepBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.sleep15.setOnClickListener { startTimer(15) }
        binding.sleep30.setOnClickListener { startTimer(30) }
        binding.sleep45.setOnClickListener { startTimer(45) }
        binding.sleep60.setOnClickListener { startTimer(60) }

        binding.btnSleepStart.setOnClickListener {
            val minutes = binding.etCustomMin.text.toString().toIntOrNull()
            if (minutes == null || minutes <= 0) {
                Ui.toast(getString(R.string.sleep_custom))
            } else {
                startTimer(minutes)
            }
        }

        binding.btnSleepStop.setOnClickListener {
            SleepTimer.stop()
            dismiss()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                SleepTimer.remainingMs.collectLatest { remaining ->
                    val active = remaining != null && remaining > 0
                    binding.btnSleepStop.visibility = if (active) View.VISIBLE else View.GONE
                    binding.tvSleepStatus.visibility = if (active) View.VISIBLE else View.GONE
                    if (active) {
                        binding.tvSleepStatus.text = getString(
                            R.string.sleep_active,
                            Ui.formatDuration(remaining ?: 0L)
                        )
                    }
                }
            }
        }
    }

    private fun startTimer(minutes: Int) {
        SleepTimer.start(minutes)
        dismiss()
    }
}