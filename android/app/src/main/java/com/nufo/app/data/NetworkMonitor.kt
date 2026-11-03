package com.nufo.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Emits whether the device currently has a validated internet connection. */
fun Context.onlineFlow(): Flow<Boolean> = callbackFlow {
    val cm = getSystemService(ConnectivityManager::class.java)
    fun online() = cm.getNetworkCapabilities(cm.activeNetwork)?.let {
        it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } ?: false
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { trySend(online()) }
        override fun onLost(network: Network) { trySend(online()) }
    }
    trySend(online())
    cm.registerDefaultNetworkCallback(callback)
    awaitClose { cm.unregisterNetworkCallback(callback) }
}.distinctUntilChanged()
/**
 * True only when the request never reached the internet (no DNS, no route, connection refused). A timeout on a slow
 * network or an HTTP error from a busy server is a different problem: telling the user to "connect to the internet"
 * while they are online sends them to fix the wrong thing.
 */
fun java.io.IOException.meansOffline(): Boolean =
    this is java.net.UnknownHostException || this is java.net.ConnectException || this is java.net.NoRouteToHostException
