package com.shubh.song.ui.sheets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.shubh.song.R
import com.shubh.song.data.db.entity.PlaylistEntity
import com.shubh.song.databinding.SheetPlaylistPickerBinding
import com.shubh.song.ui.common.AppViewModelFactory
import com.shubh.song.ui.common.PlaylistPickerAdapter
import com.shubh.song.ui.player.PlayerViewModel
import com.shubh.song.util.LiquidGlass
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PlaylistPickerSheet : BottomSheetDialogFragment() {

    private lateinit var binding: SheetPlaylistPickerBinding
    private val viewModel: PlayerViewModel by viewModels { AppViewModelFactory }

    private val adapter = PlaylistPickerAdapter { playlist ->
        addTo(playlist)
    }

    override fun onStart() {
        super.onStart()
        LiquidGlass.apply(dialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = SheetPlaylistPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvPicker.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPicker.adapter = adapter

        binding.btnNewPlaylist.setOnClickListener {
            showNameDialog { name ->
                viewModel.createPlaylistAndAdd(name)
                dismiss()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.playlists.collectLatest { playlists ->
                    adapter.submit(playlists)
                }
            }
        }
    }

    private fun addTo(playlist: PlaylistEntity) {
        viewModel.addCurrentToPlaylist(playlist.id)
        dismiss()
    }

    private fun showNameDialog(onOk: (String) -> Unit) {
        val input = android.widget.EditText(requireContext()).apply {
            hint = getString(R.string.playlist_name_hint)
            setPadding(24, 16, 24, 16)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.new_playlist)
            .setView(input)
            .setPositiveButton(R.string.create_playlist) { _, _ -> onOk(input.text.toString()) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}