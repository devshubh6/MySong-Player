package com.shubh.song

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
import androidx.core.os.LocaleListCompat
import com.shubh.song.data.local.FileStore
import com.shubh.song.data.offline.OfflineEngine
import com.shubh.song.data.repository.LibraryRepository
import com.shubh.song.data.repository.MusicRepository
import com.shubh.song.data.repository.SettingsRepository
import com.shubh.song.media.PlaybackManager
import com.shubh.song.util.CrashLogger
import com.shubh.song.util.NetworkObserver
import com.shubh.song.util.ThemeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

class MySongApp : Application() {

    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var libraryRepository: LibraryRepository
        private set
    lateinit var musicRepository: MusicRepository
        private set

    private val _accent = MutableStateFlow("")
    /** Emits the currently selected accent name; UI collects it to re-tint live. */
    val accent: StateFlow<String> = _accent

    override fun onCreate() {
        super.onCreate()
        instance = this

        CrashLogger.install(this)

        FileStore.init(this)
        OfflineEngine.init(this)
        NetworkObserver.init(this)

        settingsRepository = SettingsRepository(this)
        libraryRepository = LibraryRepository(this)
        musicRepository = MusicRepository()

        _accent.value = settingsRepository.accent

        PlaybackManager.init(this)

        applyStoredTheme()
        try {
            applyStoredLocale()
        } catch (_: Exception) {
            // Locale application is best-effort; never block app start.
        }
    }

    /** Persists the accent (name + color hex) and notifies every observing screen. */
    fun setAccent(name: String) {
        settingsRepository.setAccent(name)
        settingsRepository.prefs.accentHex = ThemeUtils.accentHex(name)
        _accent.value = name
    }

    /** Applies dark / light / system theme from encrypted prefs. */
    fun applyStoredTheme() {
        val mode = when (settingsRepository.theme) {
            "light" -> MODE_NIGHT_NO
            "system" -> MODE_NIGHT_FOLLOW_SYSTEM
            else -> MODE_NIGHT_YES
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    /** Localizes the UI based on the first selected content language. */
    fun applyStoredLocale() {
        val lang = settingsRepository.languages.firstOrNull() ?: "english"
        val localeTag = when (lang) {
            "hindi" -> "hi"
            "bengali" -> "bn"
            "tamil" -> "ta"
            "telugu" -> "te"
            "marathi" -> "mr"
            "gujarati" -> "gu"
            "kannada" -> "kn"
            "malayalam" -> "ml"
            "punjabi" -> "pa"
            else -> Locale.ENGLISH.language
        }
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(localeTag))
    }

    companion object {
        lateinit var instance: MySongApp
            private set
    }
}
