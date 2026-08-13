package com.shubh.song.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build

/**
 * Tracks connectivity for the whole app. [isOnline] flips instantly when the
 * default network appears/disappears so the UI can react (e.g. auto-redirect
 * to the offline Library).
 */
object NetworkObserver {

    @Volatile
    var isOnline: Boolean = true
        private set

    private var manager: ConnectivityManager? = null

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            isOnline = true
        }

        override fun onLost(network: Network) {
            isOnline = false
        }

        override fun onUnavailable() {
            isOnline = false
        }
    }

    fun init(context: Context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        manager = cm

        val active = cm.activeNetwork
        isOnline = active != null &&
            cm.getNetworkCapabilities(active)
                ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                cm.registerDefaultNetworkCallback(callback)
            } else {
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                cm.registerNetworkCallback(request, callback)
            }
        } catch (_: Exception) {
        }
    }

    fun isOnlineNow(): Boolean {
        val cm = manager ?: return true
        val active = cm.activeNetwork ?: return false
        return cm.getNetworkCapabilities(active)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }
}