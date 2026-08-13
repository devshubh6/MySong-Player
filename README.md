# Mysong 🎵

**Your music. Your way.**

Mysong is a full-featured, offline-first music streaming & player app built for Android — powered by the **JioSaavn Unofficial API**. It delivers a Spotify-style experience: trending feeds, artist & album pages, a full-screen swipe player, downloads for 100% offline playback, a 10-band equalizer, sleep timer, lyrics, playlist backup and even a dedicated **Car Mode** for driving.

Built with **Kotlin + Jetpack (Media3 · Room · ViewBinding · StateFlow)** on a single-session continuous-development workflow, fully compile-ready under **AndroidIDE**.

---

## Table of Contents

- [Highlights](#highlights)
- [Features](#features)
- [Screens](#screens)
- [Tech Stack & Libraries](#tech-stack--libraries)
- [Project Structure](#project-structure)
- [Architecture](#architecture)
- [Data Layer](#data-layer)
- [Playback Engine](#playback-engine)
- [Offline System](#offline-system)
- [Equalizer](#equalizer)
- [Settings & Preferences](#settings--preferences)
- [Search System](#search-system)
- [Detail Pages](#detail-pages)
- [Car Mode](#car-mode)
- [Playlist Backup (Export / Import)](#playlist-backup-export--import)
- [Theming (Accents + AMOLED)](#theming-accents--amoled)
- [Build & Run](#build--run)
- [Permission Usage](#permission-usage)
- [Known Limitations](#known-limitations)
- [Development Log](#development-log)
- [License & Credits](#license--credits)

---

## Highlights

- 🎧 **Infinite streaming** from the JioSaavn Unofficial API (songs, albums, artists, playlists).
- 💾 **Offline-first** — downloads + auto streaming cache survive without a network.
- 🎚️ **10-band Equalizer** with presets, Bass Boost and Virtualizer (default off).
- 🎛️ **Playback signatures** — Normal, **Slowed + Reverb**, **Nightcore**.
- 🔀 **Crossfade (0–12 s)** and **endless Auto-Mix** similar-track radio.
- 🚗 **Car Mode** — huge touch targets, swipe-to-skip, keep-screen-on.
- 🖤 **AMOLED pure-black** mode, 6 accent colors, Dark / Light / System themes.
- 🌐 **12 languages & 7 countries** drive a localized home feed.
- 📦 **Playlist backup** — export/import `.json` or `.m3u` via Storage Access Framework.

---

## Features

**Home (`HomeFragment`)**
- Endless curated feed: *Trending Now, Top Charts, Featured Albums, Popular Artists, Top Playlists* + 7 extra rows (Party, Workout, Romantic, Chill, Devotional, Classics, Remixes).
- Language × country-filtered searches (top 3 selected languages drive the feed).
- Shimmer loading skeletons, pull-to-refresh, offline-aware fallback.

**Search (`SearchFragment`)**
- Debounced live suggestions (280 ms), recent-search chips, 6 "Browse All" categories.
- Full search with **Top Artist card** (`Play Artist Radio`), playable songs, and **paged infinite scroll** (30 / page).
- Keyboard-aware layout (IME insets) — results never hide under the soft keyboard.

**Player (`PlayerActivity`)**
- Vertical swipeable artwork pages; tap = play/pause, long-press = 2×.
- Seek bar, shuffle/repeat segmented pill, Like, Download, Queue, Lyrics, Sleep Timer, Track Options.
- **Playback signature chip** cycling `1x → Slowed + Reverb → Nightcore`.

**Artist / Album pages**
- Dedicated overlay pages with paged artist catalogs, in-page filters, Play All / Shuffle, and album track lists with index / duration / 3-dot menu (Play / Love / Share).

**Playback**
- Single global `ExoPlayer` in `PlaybackManager`; foreground `Service` with MediaSession + action notification; gapless queue, Auto-Mix on queue end.

**Settings**
- Theme, accent color, quality (96/160/320 kbps), languages, country, cache viewer/cleaner.
- Audio: mode picker, **crossfade slider (0–12 s)**, **Auto-Mix switch**.
- Appearance: **AMOLED Pure Black** toggle.
- Extras: **Car Mode** launcher, **Playlist Export / Import**.
- About section with GitHub + version.

---

## Screens

| Home | Search | Player | Settings |
|---|---|---|---|
| Carousels + skeleton feed | Suggestions / Top Artist card | Swipe pages + bottom bar | Sections + Extras |

*(Screenshots pending — emulator captures aren't available in this build environment.)*

---

## Tech Stack & Libraries

| Layer | Tech |
|---|---|
| Language | Kotlin 1.9.24 |
| UI | Android Views + **ViewBinding**, Material 3 (1.12.0) |
| Async | kotlinx-coroutines 1.7.3, StateFlow / repeatOnLifecycle |
| Media | **media3-exoplayer 1.4.1** (+ dash, hls, okhttp datasource, media3-session) |
| Persistence | Room 2.6.1 (KSP) + **EncryptedSharedPreferences** (security-crypto) |
| Network | Retrofit 2.11 + Gson, OkHttp 4.12 + logging interceptor |
| Images | Coil 2.6.0 |
| Others | AppCompat, Fragment-ktx, Activity-ktx, ConstraintLayout, RecyclerView, SwipeRefreshLayout |

> All icons are hand-drawn Android vector drawables (.xml) — no external assets.

---

## Project Structure

```
app/src/main/java/com/shubh/song/
├── MySongApp.kt              # Application: initializes repos, playback, theme/locale
├── MainActivity.kt           # Bottom-nav host + overlay chrome manager (self-healing)
│
├── data/
│   ├── api/
│   │   ├── ApiClient.kt      # Retrofit + OkHttp (shared client for streaming)
│   │   ├── JioSaavnApi.kt    # REST endpoints (search, songs, albums, artists, ...)
│   │   └── dto/Dtos.kt       # SongDto, AlbumDto, ArtistDto, PlaylistDto, ...
│   ├── db/
│   │   ├── AppDatabase.kt    # Room database + DAO access
│   │   ├── dao/              # SongsDao, LikedSongsDao, DownloadsDao, PlaylistDao
│   │   └── entity/Entities.kt
│   ├── local/FileStore.kt    # Download / cache directory helpers
│   ├── model/                # Song.kt (mapping from DTO ↔ Entity), Lyrics.kt
│   ├── offline/OfflineEngine.kt
│   ├── prefs/SettingsPrefs.kt
│   └── repository/           # MusicRepository, LibraryRepository, SettingsRepository
│
├── media/
│   ├── PlaybackManager.kt    # THE core engine (queue, audio modes, crossfade, auto-mix)
│   ├── MusicService.kt       # Foreground service + MediaSession + notification
│   ├── NotifActionReceiver.kt
│   ├── EqualizerController.kt
│   ├── DownloadEngine.kt
│   ├── StreamCache.kt        # Auto-cache of streamed files
│   ├── SleepTimer.kt
│   └── NotifLike.kt
│
├── ui/
│   ├── common/               # AppViewModelFactory, OverlayNav, adapters, skeletons
│   ├── home/                 # HomeFragment + HomeViewModel
│   ├── search/               # SearchFragment + SearchViewModel (+ Top Artist card)
│   ├── library/              # LibraryFragment + LibraryViewModel
│   ├── settings/             # SettingsFragment + SettingsViewModel
│   ├── player/               # PlayerActivity, MiniPlayerView, PlayerViewModel
│   ├── detail/               # ArtistDetailFragment, AlbumDetailFragment
│   ├── driving/              # DrivingModeActivity (Car Mode)
│   └── sheets/               # Queue, Lyrics, Sleep, TrackOptions, Eq, PlaylistPicker, ...
│
└── util/                     # Constants, ThemeUtils, AmoledUtils, PlaylistFiles, Ui,
                              # CrashLogger, NetworkObserver
```

```
app/src/main/res/
├── anim/            # slide in/left/right/out — overlay transitions
├── color/           # nav_item_color selector
├── drawable/        # 40+ custom vector icons & shapes (glass cards, neon pill, scrims)
├── layout/          # 9 screens, ~15 items + shimmer & sheet layouts
├── menu/            # bottom navigation
├── mipmap/          # launcher icons
└── values*/         # themes, colors, strings, + values-hi (हिन्दी) & values-night
```

---

## Architecture

Single-module Android app using the classic **ViewModel + Repository + Room/Retrofit** stack with **StateFlow** UI streams:

```
UI (ViewBinding)
   │  collect(stateFlow)
   ▼
ViewModel (AppViewModelFactory DI-less singleton factories)
   │
   ▼
Repository      ──► Retrofit/JioSaavnApi  (network)
   │
   └────────────► Room (liked/downloads/playlist/cache)
                  + EncryptedSettingsPrefs
   │
   ▼
PlaybackManager (object singleton) ──► ExoPlayer + AudioEffects + MediaSession
```

- **No DI framework** — `MySongApp.instance` singletons, `AppViewModelFactory` doing manual `create()` mapping.
- **Single-activity with fragments** hosted in `MainActivity`; full-search/pages open as **overlay fragments** added to `R.id.nav_host` with slide animations (`OverlayNav`).
- **Chrome management** — `MainActivity.setSearchMode/refreshChrome()` hides the bottom nav & mini player while any overlay (`search_fragment`/`artist_detail`/`album_detail`) is present. Derived from the FragmentManager, so chrome self-heals after any back-path.

---

## Data Layer

### API (JioSaavn Unofficial)

Upstream: https://github.com/sumitkolhe/jiosaavn-api (configured in `util/Constants.kt`).

Endpoints used (in `JioSaavnApi`):

| Method | Purpose |
|---|---|
| `searchSongs(q, page, limit)` | pages of songs |
| `searchAlbums(q, page, limit)` | albums |
| `searchArtists(q, page, limit)` | artists |
| `searchPlaylists(q, page, limit)` | playlists |
| `searchAll(q, page, limit)` | global grouped search |
| `songsById(ids)` | fetch song details by comma-joined ids |
| `albumById(id)` | album detail + songs |
| `artistById(id)` | artist detail |
| `artistSongs(id)` | artist's songs |
| `playlistById(id)` | playlist detail + songs |
| `lyricsById(id)` | lyrics proxy |

### Local (Room)

Entities: `SongEntity`, `LikedSongEntity` (`flag` + timestamp), `DownloadEntity` (path + quality + size), `CacheEntity`, `PlaylistEntity` (id, name, createdAt), `PlaylistSongCrossRef` (playlistId ↔ songId, addedAt).

Queries in DAOs:
- `PlaylistDao` — observePlaylists (NULL created DESC), insert/rename/delete, add/remove song (REPLACE), `observePlaylistSongs` join ordered `ORDER BY addedAt DESC`, `isInPlaylist`.
- `SongsDao`/`LikedSongsDao`/`DownloadsDao`/`CacheDao` like the liked/downloaded/cached song Flows.

---

## Playback Engine

`media/PlaybackManager.kt` is a **singleton `object`** owning the single ExoPlayer instance.

### State
```kotlin
PlayerUiState(
  currentSong: Song?, queue: List<Song>, index: Int,
  isPlaying, isBuffering: Boolean, positionMs, durationMs: Long,
  speed: Float, isOffline: Boolean, shuffleEnabled: Boolean,
  repeatMode: Int, audioMode: AudioMode, crossfadeSec: Int
)
```
Every screen (mini player, PlayerActivity, Car Mode, notifications) collects `PlaybackManager.uiState`.

### Control API
`playQueue(songs, index)`, `appendToQueue`, `togglePlayPause`, `play`, `pause`, `playNext/Prev`, `seekTo`, `seekToIndex`, `setSpeed`, `toggleShuffle`, `cycleRepeat`, `removeFromQueue`, `clearQueue`, `fadeOutAndPause`.

### Audio signatures (playback modes)
- **Normal** — `PlaybackParameters(1f, 1f)`.
- **Slowed + Reverb** — `PlaybackParameters(0.85f, 0.85f)` + an `android.media.audiofx.EnvironmentalReverb` effect (decay 1600 ms, reverb 450) attached to the active session.
- **Nightcore** — `PlaybackParameters(1.15f, 1.25f)` (chip: + pitch).

Change via `setAudioMode(AudioMode)`, `cycleAudioMode()` (chip in PlayerActivity, buttons in Settings). Restored from prefs each launch; auto re-applied per track and re-attached on new sessions.

### Crossfade (0–12 s)
media3 1.4.1 has no native crossfade API, so the app implements it with **volume automation** on a single player:
- `scheduleCrossfadeOut()` — `Handler`-takes fade-out to the final `crossfadeMs` of the playing item.
- `fadeInOnTransition()` — ramp from 0 → player volume over `crossfadeMs` on the next track.
- Both read the live pref every transition; if the crossfade is `0`, normal volume is restored.

### Auto-Mix (endless queue)
On `STATE_ENDED` (repeat off), if Auto-Mix is enabled it:
1. Seeds from the current (last) song,
2. Asks `MusicRepository.similarSongs()` (searches `"<primaryArtists> songs"`, fallback album),
3. Appends up to 10 new, non-duplicate tracks, seeks to the first of them and resumes.

### Foreground service & notification
- `MusicService` — classic Service, `START_STICKY`, Starts via `ContextCompat.startForegroundService` on first play, `startForeground()` with a MediaSession notification (id 2001, channel `music_channel`).
- Notification is refreshed from `uiState` tickers and supports Like / Play-Pause / Next / Prev via `NotifActionReceiver`.

### Equalizer
- `EqualizerController` — 10-band equalizer + Bass Boost + Virtualizer; indicator broken into bands; accented UI bars.
- **Master switch pref is OFF by default** (Privacy-flag settings); attaches only on session ready.

---

## Offline system

- **Downloads** — explicit: `DownloadEngine` streams the best URL for the selected quality and registers `DownloadEntity` (path, quality, size).
- **Cache** — automatic: `StreamCache` copies streamed audio to `stream_cache` so recently-played songs can warm instantly.
- `PlaybackManager.resolveUri()` decides in order: **download → cache → remote stream (quality-aware)**; `isOffline` marking local sources.
- When offline, `MainActivity.onStart` falls back to Library + toast `Offline Mode Active`.
- `SettingsRepository.cacheSizeBytes()` measures all cache-folders; `clearCache()` wipes them.

---

## Settings & Preferences

Encrypted (AES/GCM, Keystore-backed) via `SettingsPrefs`.

| Key | Default | Meaning |
|---|---|---|
| `theme_mode` | `dark` | dark / light / system |
| `accent_color` | `green` | green / purple / pink / cyan / amber / red |
| `accent_hex` | `#1DB954` | persisted accent color |
| `stream_quality` | `160` | 96 / 160 / 320 kbps |
| `languages` | {hindi, english} | content-lang set |
| `country` | `India` | region |
| `eq_enabled` | `false` | Equalizer master (**default OFF**) |
| `audio_mode` | `normal` | normal / slowed / nightcore |
| `crossfade_seconds` | `0` | 0–12 s crossfade |
| `auto_mix` | `true` | endless similar-track queue |
| `amoled_black` | `false` | pure #000000 chrome |
| `recent_searches` | — | last 8 search terms |

---

## Search System

- Suggestion debounce 280 ms, results of up to 8 tracks.
- **Infinite paging** — `searchSongs(query, page, 30)`; the scroll listener triggers when `!canScrollVertically(1)`.
- **Top Artist card** → `MusicRepository.matchTopArtist(query)`:
  - Hits `searchSongs`, then picks the best artist by name-contains or the first.
  - Card shows circular photo + listener count; **Play Artist Radio** plays `searchArtist(query)` shuffled through `PlaybackManager.playQueue`.
  - Card row opens the `ArtistDetailFragment`.
- Recents persisted; `Clear` resets them.

## Detail Pages

- **Artist** (`ArtistDetailFragment`): banner → name/count row → **Play All / Shuffle** → **filter search** → infinite-paged song list (in-file filter on name/artist/album). Playing highlight in `SongAdapter`.
- **Album** (`AlbumDetailFragment`): square cover, title/artist/year/meta, **Play All**, filter box, numbered track list (`AlbumTrackAdapter`) with duration + 3-dot menu (Play / Love / Share), current-playing accent.
- Keyboard-aware list padding (IME insets on the scroll view).

---

## Car mode

`DrivingModeActivity`:
- Fullscreen, glass-scrimmed artwork background.
- **Giant touch chips**: prev (120 dp), play/pause (152 dp), next (120 dp).
- **Swipe-to-skip** (≥ 200 px horizontal fling) — next/prev.
- `FLAG_KEEP_SCREEN_ON`, huge title/artist, live progress, exit button.
- Launched from **Settings → Extras → Car Mode**.

---

## Playlist Backup (Export / Import)

- **Export** (`CreateDocument`): pick a name; extension decides format:
  - `.json` → full lossless dump (`PlaylistFiles.toJson`) with all stream qualities → re-import offline-capable.
  - `.m3u` → real M3U (`#EXTINF` lines + `#EXTMYSONG <id>` markers + stream URL) so other players can open it.
- **Import** (`OpenDocument`): auto-detects JSON (`parseJson`) or M3U (`parseM3u`); JSON imports rebuild full songs, M3U falls back to fetch-by-ID for missing URLs.
- Feedback via toasts (`Playlists exported/imported`).

---

## Theming (Accents + AMOLED)

- `ThemeUtils` — accent map, `accentColor/withAlpha/cardFill/applyCardTint/applyHeaderGradient/tintBottomNav/tintSeekBar/chipFill/categoryTile`.
- 6 accents: `green #1DB954` (default), purple `#A855F7`, pink `#EC4899`, cyan `#22D3EE`, amber `#F59E0B`, red `#EF4444`.
- **AMOLED** (`AmoledUtils`): forces all chrome (`root`, bottom nav, system bars) to `#000000` for OLED power saving; switchable in Settings → Appearance.
- Themes use DayNight resources — `values-night` + `values` color palettes.

---

## API / Current Endpoint

- Upstream: https://github.com/sumitkolhe/jiosaavn-api
- **Already shipped endpoints**: search*, songsById, albumById, artistById, artistSongs, playlistById, lyricsById.
- No hard-coded keys; the app proxies through that upstream.

---

## Build & Run

Requirements:
- JDK 17 (`JAVA_HOME=/opt/java/jdk-17.0.20+8` in this environment)
- Android SDK (compileSdk 35)
- Gradle 8.x wrapper

```
git clone <repo>
cd Mysong
./gradlew :app:assembleDebug
# APK → app/build/outputs/apk/debug/app-debug.apk
```

Alternatively, open in **AndroidIDE** and hit **Run**.

**Note:** AndroidIDE (rooted Android, no `pm`/`logcat`) — verify with the APK + crash logs; lint has pre-existing warnings.

---

## Permissions

- `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`
- `WAKE_LOCK`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`
- `POST_NOTIFICATIONS` (runtime, Android 13+) + notification channel
- `WRITE_EXTERNAL_STORAGE` ≤ API 28, `READ_EXTERNAL_STORAGE` ≤ 32

---

## Known Limitations

- Lint contains ~150 pre-existing warnings (builds fine — they are not blockers).
- Offset paging for artists is by-search-page; very large catalogs are paginated by keyword.
- Crossfade uses volume fades — true overlapping audio would require a second player.
- Car Mode portrait-only.

---

## Development Log

| Round | Work delivered |
|---|---|
| 1 | MVP shell: Home feed + Search + Library + Settings, themes & accents, skeletons |
| 2 | PlayerActivity (swipe pages, bottom bar, sheets: Queue/Lyrics/Sleep/Options), downloads, liked songs, offline engine |
| 3 | Artist/Album detail overlay pages, OverlayNav chrome management, search fixes |
| 4 | Header redesign (offset-free rows), bottom-nav restore via fragment-tag chrome, Top Artist card + paged search, Equalizer default **OFF**, Slowed+Reverb / Nightcore, Crossfade 0–12 s, Auto-Mix, Car Mode, AMOLED theme, playlist export/import — APK green |
| 5 | **LRC Lib karaoke lyrics** (synced LRC fetch + `LrcParser` multi-timestamp/offset, glowing centered active line, JioSaavn/plain fallback), **dynamic palette theme** (artwork-driven gradients on full player + tinted bottom panel, cross-faded), **My Stats & Recap** (Room `playback_history_table` + per-day aggregates: total time, play counts, top artist, top 5 songs, 7-day activity bars; recorded incrementally by the player), **Smart Volume** (DynamicsProcessing limiter + 3-band MBC, settings toggle, re-attach per audio session) — APK green |
| 6 | **Reels/Shorts player**: next/prev removed (vertical swipe skips tracks), 2dp accent progress line at the bottom edge, clickable circular artist avatar + profile overlay → full artist catalog inside the player, bottom-right Like/Download/More rail, tap = play/pause, long-press = 2× speed; **crash-proof mini player** expansion (guarded launch + NEW_TASK fallback); **categorized Settings**: category cards (Appearance / Audio & Playback / Content & Region / Storage & Cache / Extras & Tools / About & Developer), each opening its own sub-screen — APK green |
| 7 | **Contrast & lyrics polish**: artwork 100% bright by default (no overlays), 60% scrim fades in ONLY while lyrics/karaoke are open; playback timers (`01:20 / 03:45`) beside the thin progress line; rail reordered Like → Download → Shuffle/Repeat pill → ⋯ (3-dots); playback modes moved inside the 3-dots sheet; dynamic status/nav bar icon tints from surface luminance; light-theme nav-bar + dialog surface `colorOnSurface` fixes; **LyricsRepository** (JioSaavn primary → LRC Lib fallback, offline-safe) feeding the auto-scrolling karaoke adapter — APK green |
| 8 | **Draggable timeline + gesture crash-proofing**: static progress line → interactive `SeekBar` (4dp resting, thickens to 8dp while scrubbing, live `mm:ss` preview, seek on release); down-swipe at first track can no longer crash — strict pager index boundary checks + overscroll-down fallback to `playPreviousSong()` (index>0 → previous track, index 0 → restart from 0:00); rail split into standalone Like/Download/Shuffle/Repeat/More circles (16dp gaps, 28dp from edge); zero-padded `01:15 / 03:45` timers app-wide; status/nav-bar icon contrast re-asserted in `onResume` after any theme switch — APK green |
| 9 | **Crash-proof track navigation + readable HUD**: every ExoPlayer listener callback (`onIsPlayingChanged` / `onPlaybackStateChanged` / `onMediaItemTransition`) and every seek/nav entry point (`seekTo`, `seekToIndex`, `playNext`, `playPrev`, pager callbacks, `replaceMediaItem`, auto-mix seek) wrapped in try/catch so no transition path can crash the app — down-swipe to the previous song can never throw; new `bg_player_hud` scrim (darkest behind the text row) + text shadows + bumped sizes (17sp title / 13sp artist) so song title, artist and `00:00 / 00:00` timers stay readable on any artwork — APK green |
| 10 | **Zero-warning build + bright artwork**: removed the 96%-opaque palette gradient that was permanently darkening the artwork (artwork now renders 100% bright, `centerCrop`; only a subtle bottom-25% `bg_player_bottom_gradient` remains for text legibility, plus the lyrics-only 60% dim); glassmorphic `bg_glass_circle` buttons on the whole player rail (Like → Download → Shuffle → Repeat → ⋯); 38dp artist avatar, HUD floated 32dp from the bottom; all 40 Kotlin warnings cleared (unused vars, redundant elvis, `GestureDetectorCompat`→`GestureDetector`, `@OptIn` Coil/coroutines, `Virtualizer`/`chipCornerRadius` deprecations, name shadowing, nullability) — APK green |
| 11 | **Liquid-glass sheets + reels transform + tap indicator**: every bottom sheet (Queue/Lyrics/Track options/Equalizer/Sleep/Playlist picker/Playlist songs/Language filter) now uses a frosted `bg_liquid_glass` surface (`#33121212` fill, 1dp `#40FFFFFF` ring, 24dp top corners) with real **Live Blur** (`FLAG_BLUR_BEHIND` + 25px `RenderEffect` blur on Android 12+, stronger window dim below); the player's vertical pager got a **ReelsPageTransformer** (outgoing page scales to 85% and fades to black, incoming page slides with depth); single-tap now flashes a centered glowing play/pause glyph (`iv_play_pause_indicator`, pop-in → linger → fade); timers row re-anchored left so the rail's 3-dots can never overlap the total time; all API-driven titles/artists decoded with `HtmlCompat` (`&amp;`, `&#39;`, …) in adapters, player HUD, mini player and sheet headers — APK green (13 845 626 B, zero warnings) |
| 12 | **Rail above gradient + reels transform v2 + App Updates + warning fix**: side action rail re-anchored to float strictly ABOVE the bottom HUD/gradient (`marginBottom` 28dp→112dp, 16dp from the right edge) so Like/Download/Shuffle/Repeat/⋯ never overlap the dark bar; `ReelsPageTransformer` rewritten to the true Shorts formula — current page slides out full-frame while fading, incoming page rises from below scaled 85%→100% (depth shift, symmetric in both swipe directions); **App Updates** card on the Settings hub (subtitle "Check for new releases on GitHub") opens a liquid-glass `UpdateBottomSheetFragment` showing app name + **Current Version: v1.0.0** (read from the installed package) with a prominent accent **Check for Updates** button → `Intent.ACTION_VIEW` to `github.com/devshubh6/MySong-Player/releases` (crash-guarded, Toast fallback); removed the redundant `?:` on the non-nullable `ivPlayPauseIndicator` binding in `PlayerActivity` — APK green (13 848 279 B, zero warnings) |
| 13 | **Live-scrubbing bottom seekbar + unified accent engine + v1.0.1**: the bottom timeline is now an interactive segway — 3dp resting line pinned at the very bottom edge that thickens to 6dp on `ACTION_DOWN` for a comfy touch target, and scrubbing seeks **continuously** under the finger (`seekTo(duration ⋅ progress / 1000)` on every move, `playWhenReady` untouched → audio jumps instantly, never pauses); `ThemeUtils` renamed to **`ThemeManager`** — the unified accent engine adds `appBackground()`/`applyAppBackground()` (screen surface blended 12% toward the selected accent, full opacity) applied live to Home / Library / Search / Settings roots (player artwork backdrop untouched); MainActivity's accent collector now does one `recreate()` per real accent change (change-guarded, no re-create loop) so all tabs re-tint instantly (cards/borders/bottom-nav/progress were already accent-driven; tracker in prefs); `BuildConfig` enabled with `BuildConfig.VERSION_NAME` shown in Settings footer, About sub-screen and the App Updates sheet — APK green (12 596 408 B, zero warnings) |
| 15 | **Version bump → v1.0.5** (`versionCode = 3`, `versionName = "1.0.5"`) — APK green (12 605 391 B) |
| 14 | **Double-tap 10s skip + buffered progress + live accent buttons + social links**: the player pager now uses a `GestureDetector` — single-tap still toggles play/pause, long-press still holds 2× speed, and **double-tap seeks ±10s** (left half ⏪-10s, right half +10s⏩) with YouTube-style glass chips that slide in from the tapped side and fade (`onSkip` → `seekTo(currentPosition ± 10s)`, crash-guarded, playback keeps running); the bottom timeline now shows the **buffered portion** (`secondaryProgress` from ExoPlayer's `bufferedPosition`, pushed via `PlayerUiState.bufferedPositionMs`) behind the played progress plus a subtle indeterminate shimmer (`pb_buffering`, `#59FFFFFF`) whenever `STATE_BUFFERING`; **hardcoded green buttons fixed** — "Play All" (Album + Artist) and "Export Playlists" (Extras) swapped `@color/accent` → `?attr/colorPrimary` in XML and re-tint programmatically via `backgroundTintList = ColorStateList.valueOf(ThemeManager.accentColor())` in each fragment's `onResume()`, so pink/purple/blue/red accents now apply; About & Developer replaced the plain "GitHub: https://…" text with a **3-button social row** (44dp `bg_glass_circle` glass buttons: GitHub / Instagram / X, new `ic_github` / `ic_instagram` / `ic_x_twitter` vectors) launching each profile via crash-safe `ACTION_VIEW` — APK green (14 007 077 B, zero warnings) |

---

## Credits

- **API**: JioSaavn Unofficial.
- **Libraries**: Media3 · Room · Retrofit · Coil · Coroutines · Material.
- **Author**: Shubham Singh Yadav — https://github.com/devshubh6