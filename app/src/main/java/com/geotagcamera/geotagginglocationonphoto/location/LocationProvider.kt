package com.geotagcamera.geotagginglocationonphoto.location

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Location for the stamp. Two ways in:
 *
 * - [updates] streams fixes while the viewfinder is on screen, so the fix a
 *   photo gets is seconds old even if the user walked across a site between
 *   shots (the "stale address" complaint competitors get, docs/research.md).
 * - [getFreshFix] is a one-shot for when no recent streamed fix exists.
 *
 * A cached last-known fix is only accepted if it is younger than
 * [MAX_LAST_KNOWN_AGE_MS]; an hour-old position is worse than none for a
 * geotag, because it looks right.
 */
class LocationProvider(context: Context) {
    private val client = LocationServices.getFusedLocationProviderClient(context.applicationContext)

    /** Fixes every couple of seconds until the collector stops. Needs a location permission. */
    @SuppressLint("MissingPermission")
    fun updates(intervalMs: Long = 2_000L): Flow<LocationFix> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .setWaitForAccurateLocation(false)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it.toFix()) }
            }
        }
        val registered = runCatching {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        }.isSuccess
        if (!registered) close()
        awaitClose { client.removeLocationUpdates(callback) }
    }

    @SuppressLint("MissingPermission")
    suspend fun getFreshFix(freshFixTimeoutMs: Long = 8_000L): LocationFix? {
        val fresh = withTimeoutOrNull(freshFixTimeoutMs) { requestCurrentLocation() }
        if (fresh != null) return fresh
        return getLastKnownFix()?.takeIf { it.ageMs() <= MAX_LAST_KNOWN_AGE_MS }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestCurrentLocation(): LocationFix? = suspendCancellableCoroutine { cont ->
        val cancellationSource = CancellationTokenSource()
        cont.invokeOnCancellation { cancellationSource.cancel() }

        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMaxUpdateAgeMillis(10_000L)
            .build()

        runCatching {
            client.getCurrentLocation(request, cancellationSource.token)
                .addOnSuccessListener { location -> if (cont.isActive) cont.resume(location?.toFix()) }
                .addOnFailureListener { if (cont.isActive) cont.resume(null) }
        }.onFailure { if (cont.isActive) cont.resume(null) }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getLastKnownFix(): LocationFix? = suspendCancellableCoroutine { cont ->
        runCatching {
            client.lastLocation
                .addOnSuccessListener { location -> if (cont.isActive) cont.resume(location?.toFix()) }
                .addOnFailureListener { if (cont.isActive) cont.resume(null) }
        }.onFailure { if (cont.isActive) cont.resume(null) }
    }

    private fun android.location.Location.toFix() = LocationFix(
        latitude = latitude,
        longitude = longitude,
        altitudeMeters = if (hasAltitude()) altitude else null,
        accuracyMeters = if (hasAccuracy()) accuracy else null,
        bearingDegrees = if (hasBearing()) bearing else null,
        timestampEpochMs = time,
        elapsedRealtimeNanos = elapsedRealtimeNanos.takeIf { it > 0L }
    )

    companion object {
        /** Same limit the capture path applies to streamed fixes: nothing older is ever stamped. */
        const val MAX_LAST_KNOWN_AGE_MS = 30_000L
    }
}

/**
 * How old this fix is. Uses the monotonic clock when the fix has it, so a
 * wall-clock change can't make an old fix look fresh; [now] (wall clock) is
 * the fallback for fixes without it.
 */
fun LocationFix.ageMs(now: Long = System.currentTimeMillis()): Long {
    elapsedRealtimeNanos?.let { nanos ->
        return (android.os.SystemClock.elapsedRealtimeNanos() - nanos) / 1_000_000L
    }
    return now - timestampEpochMs
}
