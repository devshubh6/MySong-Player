package com.shubh.song.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.shubh.song.ui.home.HomeViewModel
import com.shubh.song.ui.library.LibraryViewModel
import com.shubh.song.ui.player.PlayerViewModel
import com.shubh.song.ui.search.SearchViewModel
import com.shubh.song.ui.settings.SettingsViewModel

object AppViewModelFactory : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(HomeViewModel::class.java) -> HomeViewModel() as T
        modelClass.isAssignableFrom(LibraryViewModel::class.java) -> LibraryViewModel() as T
        modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel() as T
        modelClass.isAssignableFrom(PlayerViewModel::class.java) -> PlayerViewModel() as T
        modelClass.isAssignableFrom(SearchViewModel::class.java) -> SearchViewModel() as T
        else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
