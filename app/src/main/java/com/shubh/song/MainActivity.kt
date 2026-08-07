package com.shubh.song

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.shubh.song.databinding.ActivityMainBinding
import com.shubh.song.media.PlaybackManager
import com.shubh.song.ui.detail.AlbumDetailFragment
import com.shubh.song.ui.detail.ArtistDetailFragment
import com.shubh.song.ui.home.HomeFragment
import com.shubh.song.ui.library.LibraryFragment
import com.shubh.song.ui.search.SearchFragment
import com.shubh.song.ui.settings.SettingsFragment
import com.shubh.song.util.NetworkObserver
import com.shubh.song.util.ThemeUtils
import com.shubh.song.util.Ui
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val homeFragment = HomeFragment()
    private val libraryFragment = LibraryFragment()
    private val settingsFragment = SettingsFragment()

    private val fragments: Map<Int, Fragment> by lazy {
        mapOf(
            R.id.nav_home to homeFragment,
            R.id.nav_library to libraryFragment,
            R.id.nav_settings to settingsFragment
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyWindowInsets()
        requestNotificationPermissionIfNeeded()
        applyAccent()
        setupNavigation()

        scope.launch {
            PlaybackManager.uiState.collectLatest { state ->
                // Only manage the mini player when no overlay page owns the screen.
                if (!overlayActive()) {
                    binding.miniPlayer.visibility =
                        if (state.currentSong != null) View.VISIBLE else View.GONE
                }
            }
        }
        scope.launch {
            MySongApp.instance.accent.collectLatest { applyAccent() }
        }
    }

    /** Keeps content clear of the status & navigation system bars. */
    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
    }

    private fun setupNavigation() {
        // Pre-select without notifying the listener so the initial fragment
        // is added exactly once.
        binding.bottomNav.selectedItemId = R.id.nav_home
        binding.bottomNav.setOnItemSelectedListener { item ->
            showFragment(fragments[item.itemId] ?: return@setOnItemSelectedListener false)
            true
        }
        showFragment(homeFragment)
    }

    private fun showFragment(fragment: Fragment) {
        val manager = supportFragmentManager
        val current = manager.findFragmentById(R.id.nav_host)
        if (current?.javaClass == fragment.javaClass) return
        manager.beginTransaction()
            .replace(R.id.nav_host, fragment)
            .commitAllowingStateLoss()
    }

    private fun applyAccent() {
        ThemeUtils.tintBottomNav(binding.bottomNav)
    }

    /** Number of stacked full-screen overlays — kept for API compatibility. */
    private var overlayDepth = 0

    /**
     * True while any full-screen overlay page (search, artist, album) is present
     * in the back stack. Re-derived from the FragmentManager instead of a depth
     * counter, so chrome state self-heals after process death, config changes,
     * or any removal path (system back, swipe, programmatic pop).
     */
    private fun overlayActive(): Boolean =
        listOf(
            SearchFragment.TAG,
            ArtistDetailFragment.TAG,
            AlbumDetailFragment.TAG
        ).any { supportFragmentManager.findFragmentByTag(it)?.isAdded == true }

    /**
     * Hides the bottom navigation + mini player while a full-screen page
     * (search, artist, album) is open. Uses GONE for the nav bar so the
     * resized window never leaves a phantom black band under the mini player
     * when the soft keyboard is shown.
     */
    fun setSearchMode(active: Boolean) {
        overlayDepth = (overlayDepth + if (active) 1 else -1).coerceAtLeast(0)
        refreshChrome()
    }

    /** Single source of truth for the activity chrome. Safe to call any time. */
    fun refreshChrome() {
        val hidden = overlayActive()
        binding.bottomNav.visibility = if (hidden) View.GONE else View.VISIBLE
        binding.miniPlayer.visibility = when {
            hidden || PlaybackManager.uiState.value.currentSong == null -> View.GONE
            else -> View.VISIBLE
        }
        applyAmoledChrome()
    }

    /** Paints pure-black surfaces when AMOLED mode is enabled. */
    private fun applyAmoledChrome() {
        val black = com.shubh.song.util.AmoledUtils.black()
        binding.root.setBackgroundColor(black)
        binding.bottomNav.setBackgroundColor(black)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }
    }

    /** Accent changed (called from SettingsFragment) — re-apply tints. */
    fun refreshAccent() {
        applyAccent()
    }

    override fun onStart() {
        super.onStart()
        refreshChrome()
        if (!NetworkObserver.isOnline) {
            binding.bottomNav.selectedItemId = R.id.nav_library
            showFragment(libraryFragment)
            Ui.toast(getString(R.string.offline_mode_active))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}