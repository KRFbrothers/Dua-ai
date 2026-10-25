/*
 * Adapted from Now in Android's ConnectivityManagerNetworkMonitor.kt and NetworkMonitor.kt
 * (https://github.com/android/nowinandroid, read through the fork https://github.com/KRFbrothers/nowinandroid),
 * Copyright 2022 The Android Open Source Project, licensed under the Apache License, Version 2.0.
 * See THIRD_PARTY_NOTICES.md. Changes: no Hilt or tracing, follows the default (active) network and
 * requires NET_CAPABILITY_VALIDATED so captive portals count as offline, repeats are filtered.
 */
package com.dua.ai.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

/** Whether the phone has a working internet connection right now. */
interface NetworkMonitor {
    val isOnline: Flow<Boolean>
    fun currentlyOnline(): Boolean
}

class ConnectivityManagerNetworkMonitor(context: Context) : NetworkMonitor {
    private val connectivity: ConnectivityManager? = context.getSystemService(ConnectivityManager::class.java)

    override fun currentlyOnline(): Boolean = connectivity?.let { manager ->
        runCatching { manager.getNetworkCapabilities(manager.activeNetwork)?.validated() == true }.getOrDefault(false)
    } ?: false

    override val isOnline: Flow<Boolean> = callbackFlow {
        val manager = connectivity
        if (manager == null) { trySend(false); close(); return@callbackFlow }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(currentlyOnline()) }
            override fun onLost(network: Network) { trySend(currentlyOnline()) }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) { trySend(capabilities.validated()) }
        }
        val registered = runCatching { manager.registerDefaultNetworkCallback(callback); true }.getOrDefault(false)
        trySend(currentlyOnline())
        awaitClose { if (registered) runCatching { manager.unregisterNetworkCallback(callback) } }
    }.conflate().distinctUntilChanged()

    private fun NetworkCapabilities.validated() =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
