package com.shubh.song.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shubh.song.MySongApp
import com.shubh.song.R
import com.shubh.song.data.repository.SettingsRepository
import com.shubh.song.util.Ui
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel : ViewModel() {

    private val app = MySongApp.instance
    private val repository: SettingsRepository = app.settingsRepository

    private val _theme = MutableStateFlow(repository.theme)
    val theme: StateFlow<String> = _theme

    private val _accent = MutableStateFlow(repository.accent)
    val accent: StateFlow<String> = _accent

    private val _quality = MutableStateFlow(repository.quality)
    val quality: StateFlow<String> = _quality

    private val _languages = MutableStateFlow(repository.languages)
    val languages: StateFlow<Set<String>> = _languages

    private val _country = MutableStateFlow(repository.country)
    val country: StateFlow<String> = _country

    private val _cacheSize = MutableStateFlow("")
    val cacheSize: StateFlow<String> = _cacheSize

    private val _crossfade = MutableStateFlow(repository.crossfadeSec)
    val crossfade: StateFlow<Int> = _crossfade

    private val _autoMix = MutableStateFlow(repository.autoMix)
    val autoMix: StateFlow<Boolean> = _autoMix

    private val _amoled = MutableStateFlow(repository.amoledBlack)
    val amoled: StateFlow<Boolean> = _amoled

    private val _audioMode = MutableStateFlow(
        com.shubh.song.media.PlaybackManager.uiState.value.audioMode
    )
    val audioMode: StateFlow<com.shubh.song.media.PlaybackManager.AudioMode> = _audioMode

    init {
        refreshCacheSize()
    }

    fun setTheme(mode: String) {
        if (mode == _theme.value) return
        repository.setTheme(mode)
        _theme.value = mode
        app.applyStoredTheme()
    }

    fun setAccent(name: String) {
        if (name == _accent.value) return
        _accent.value = name
        app.setAccent(name)
    }

    fun setQuality(quality: String) {
        if (quality == _quality.value) return
        repository.setQuality(quality)
        _quality.value = quality
    }

    fun toggleLanguage(lang: String) {
        val updated = _languages.value.toMutableSet().apply {
            if (lang in this) remove(lang) else add(lang)
        }
        if (updated.isEmpty()) updated.add("english")
        repository.setLanguages(updated)
        _languages.value = updated
        app.applyStoredLocale()
    }

    fun setCountry(country: String) {
        if (country == _country.value) return
        repository.setCountry(country)
        _country.value = country
    }

    fun refreshCacheSize() {
        viewModelScope.launch {
            _cacheSize.value = Ui.formatBytes(repository.cacheSizeBytes())
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            repository.clearCache()
            app.libraryRepository.clearCacheRows()
            refreshCacheSize()
            Ui.toast(app.getString(R.string.cache_cleared))
        }
    }

    fun setCrossfade(seconds: Int) {
        val clamped = seconds.coerceIn(0, 12)
        if (clamped == _crossfade.value) return
        repository.setCrossfade(clamped)
        _crossfade.value = clamped
        com.shubh.song.media.PlaybackManager.setCrossfade(clamped)
    }

    fun setAutoMix(enabled: Boolean) {
        if (enabled == _autoMix.value) return
        repository.setAutoMix(enabled)
        _autoMix.value = enabled
    }

    fun setAmoled(enabled: Boolean) {
        if (enabled == _amoled.value) return
        repository.setAmoledBlack(enabled)
        _amoled.value = enabled
    }

    fun setAudioMode(mode: com.shubh.song.media.PlaybackManager.AudioMode) {
        if (mode == _audioMode.value) return
        com.shubh.song.media.PlaybackManager.setAudioMode(mode)
        _audioMode.value = mode
    }
}
