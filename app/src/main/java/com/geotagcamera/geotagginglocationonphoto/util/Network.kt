package com.geotagcamera.geotagginglocationonphoto.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network as AndroidNetwork
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** What the phone's connection allows right now. */
enum class NetworkState { OFFLINE, METERED, UNMETERED }

object Network {

    fun current(context: Context): NetworkState {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return NetworkState.OFFLINE
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return NetworkState.OFFLINE
        return classify(caps)
    }

    /** Live connection state; emits the current value first. */
    fun state(context: Context): Flow<NetworkState> = callbackFlow {
        val cm = context.applicationContext.getSystemService(ConnectivityManager::class.java)
        if (cm == null) { trySend(NetworkState.OFFLINE); close(); return@callbackFlow }
        trySend(current(context))
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: AndroidNetwork, caps: NetworkCapabilities) { trySend(current(context)) }
            override fun onLost(network: AndroidNetwork) { trySend(current(context)) }
            override fun onAvailable(network: AndroidNetwork) { trySend(current(context)) }
        }
        runCatching { cm.registerDefaultNetworkCallback(callback) }
        awaitClose { runCatching { cm.unregisterNetworkCallback(callback) } }
    }.distinctUntilChanged()

    private fun classify(caps: NetworkCapabilities): NetworkState = when {
        !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> NetworkState.OFFLINE
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) -> NetworkState.UNMETERED
        else -> NetworkState.METERED
    }
}
