package com.geotagcamera.geotagginglocationonphoto.location

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
