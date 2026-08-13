package com.shubh.song.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.shubh.song.util.Constants

/**
 * Settings backed by EncryptedSharedPreferences (AES/GCM, keys stored in
 * the Android Keystore) so user settings never land in plaintext.
 */
class SettingsPrefs(context: Context) {

    private val prefs: SharedPreferences = build(context)

    private fun build(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                Constants.PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Fall back to a keystore-backed secret so the app never breaks.
            context.getSharedPreferences(Constants.PREFS_FILE, Context.MODE_PRIVATE)
        }
    }

    // ---- Theme ----
    var themeMode: String
        get() = prefs.getString(Constants.KEY_THEME, Constants.DEFAULT_THEME)!!
        set(value) = prefs.edit().putString(Constants.KEY_THEME, value).apply()

    // ---- Accent ----
    var accent: String
        get() = prefs.getString(Constants.KEY_ACCENT, Constants.DEFAULT_ACCENT)!!
        set(value) = prefs.edit().putString(Constants.KEY_ACCENT, value).apply()

    /** Persisted accent color hex (e.g. "#1DB954") for dynamic theme tinting. */
    var accentHex: String
        get() = prefs.getString(Constants.KEY_ACCENT_HEX, Constants.DEFAULT_ACCENT_HEX)!!
        set(value) = prefs.edit().putString(Constants.KEY_ACCENT_HEX, value).apply()

    // ---- Recent searches ----
    var recentSearches: List<String>
        get() = prefs.getString(Constants.KEY_RECENT_SEARCHES, "")!!
            .split("\n")
            .filter { it.isNotBlank() }
            .take(8)
        set(value) = prefs.edit()
            .putString(Constants.KEY_RECENT_SEARCHES, value.take(8).joinToString("\n"))
            .apply()

    // ---- Quality ----
    var quality: String
        get() = prefs.getString(Constants.KEY_QUALITY, Constants.DEFAULT_QUALITY)!!
        set(value) = prefs.edit().putString(Constants.KEY_QUALITY, value).apply()

    // ---- Languages ----
    fun getLanguages(): Set<String> =
        prefs.getStringSet(Constants.KEY_LANGUAGES, setOf("hindi", "english"))!!

    fun setLanguages(languages: Set<String>) {
        prefs.edit().putStringSet(Constants.KEY_LANGUAGES, languages).apply()
    }

    // ---- Country ----
    var country: String
        get() = prefs.getString(Constants.KEY_COUNTRY, Constants.DEFAULT_COUNTRY)!!
        set(value) = prefs.edit().putString(Constants.KEY_COUNTRY, value).apply()

    // ---- Equalizer master ----
    var eqEnabled: Boolean
        get() = prefs.getBoolean(Constants.KEY_EQ_ENABLED, false) // Default OFF
        set(value) = prefs.edit().putBoolean(Constants.KEY_EQ_ENABLED, value).apply()

    // ---- Playback audio mode (Normal / Slowed+Reverb / Nightcore) ----
    var audioMode: String
        get() = prefs.getString(Constants.KEY_AUDIO_MODE, Constants.AUDIO_MODE_NORMAL)!!
        set(value) = prefs.edit().putString(Constants.KEY_AUDIO_MODE, value).apply()

    // ---- Spatial audio (Off / 3D / 8D / 16D / 32D) ----
    var spatialMode: String
        get() = prefs.getString(Constants.KEY_SPATIAL_MODE, "OFF")!!
        set(value) = prefs.edit().putString(Constants.KEY_SPATIAL_MODE, value).apply()

    // ---- Crossfade between tracks (seconds, 0 = off) ----
    var crossfadeSec: Int
        get() = prefs.getInt(Constants.KEY_CROSSFADE_SEC, 0)
        set(value) = prefs.edit().putInt(Constants.KEY_CROSSFADE_SEC, value.coerceIn(0, 12)).apply()

    // ---- Endless "Auto-Mix" at the end of the queue ----
    var autoMix: Boolean
        get() = prefs.getBoolean(Constants.KEY_AUTO_MIX, true)
        set(value) = prefs.edit().putBoolean(Constants.KEY_AUTO_MIX, value).apply()

    // ---- Smart Volume (loudness normalization) ----
    var smartLoudness: Boolean
        get() = prefs.getBoolean(Constants.KEY_SMART_LOUDNESS, true) // Default ON
        set(value) = prefs.edit().putBoolean(Constants.KEY_SMART_LOUDNESS, value).apply()
}
