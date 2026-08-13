package com.shubh.song.util

import com.shubh.song.BuildConfig

object Constants {
    /**
     * Base URL for the JioSaavn API. Injected from `local.properties` at build
     * time via `BuildConfig.API_BASE_URL` so the endpoint is never committed
     * to source code. Falls back to an empty string if the key is missing.
     */
    val API_BASE = BuildConfig.API_BASE_URL

    const val PREFS_FILE = "mysong_prefs"
    const val PREFS_MASTER_KEY = "mysong_master_key"

    const val KEY_THEME = "theme_mode"          // dark | light | system
    const val KEY_ACCENT = "accent_color"       // green | purple | pink | cyan | amber | red
    const val KEY_ACCENT_HEX = "accent_hex"     // "#1DB954" — persisted accent color code
    const val KEY_QUALITY = "stream_quality"    // 96 | 160 | 320
    const val KEY_LANGUAGES = "languages"       // Set<String>
    const val KEY_COUNTRY = "country"
    const val KEY_EQ_ENABLED = "eq_enabled"     // master Equalizer switch
    const val KEY_RECENT_SEARCHES = "recent_searches"
    const val KEY_AUDIO_MODE = "audio_mode"     // normal | slowed_reverb | nightcore
    const val KEY_SPATIAL_MODE = "spatial_mode" // OFF | THREE_D | EIGHT_D | SIXTEEN_D | THIRTY_TWO_D
    const val KEY_CROSSFADE_SEC = "crossfade_seconds" // 0..12
    const val KEY_AUTO_MIX = "auto_mix"         // append similar tracks at queue end
    const val KEY_SMART_LOUDNESS = "smart_loudness" // DynamicsProcessing volume normalize

    const val AUDIO_MODE_NORMAL = "normal"
    const val AUDIO_MODE_SLOWED = "slowed"
    const val AUDIO_MODE_NIGHTCORE = "nightcore"

    const val DEFAULT_THEME = "dark"
    const val DEFAULT_ACCENT = "green"
    const val DEFAULT_ACCENT_HEX = "#1DB954"
    const val DEFAULT_QUALITY = "160"
    const val DEFAULT_COUNTRY = "India"

    const val QUALITY_LOW = "96"
    const val QUALITY_MEDIUM = "160"
    const val QUALITY_HIGH = "320"

    const val DOWNLOAD_DIR_NAME = "Mysong"
    const val CACHE_DIR_NAME = "stream_cache"

    val LANGUAGES = listOf(
        "hindi", "english", "bhojpuri", "punjabi", "haryanvi",
        "tamil", "telugu", "malayalam", "kannada", "bengali",
        "gujarati", "marathi"
    )

    val COUNTRIES = listOf(
        "India", "United States", "United Kingdom", "Canada",
        "UAE", "Australia", "Singapore"
    )

    /** JioSaavn stream qualities returned by the API. */
    val STREAM_QUALITIES = listOf("12kbps", "48kbps", "96kbps", "160kbps", "320kbps")
}
