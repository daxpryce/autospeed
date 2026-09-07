package io.pryce.android.autospeed.location

import android.annotation.SuppressLint
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import io.pryce.android.autospeed.core.location.LocationFix
import io.pryce.android.autospeed.core.location.ProviderTier

/**
 * Wraps a single Android [LocationManager] provider (design section 2.2.1: either
 * `LocationManager.FUSED_PROVIDER` or `LocationManager.GPS_PROVIDER` directly, selected by
 * [providerName]) and translates its [Location] callbacks into [LocationFix] snapshots.
 *
 * Uses [Location.getElapsedRealtimeNanos] (the same monotonic clock as
 * [SystemClock.elapsedRealtime], which callers use for `nowMillis`) rather than
 * [Location.getTime], since the latter is wall-clock time and can jump with clock changes.
 */
class FrameworkLocationManagerSource(
    private val locationManager: LocationManager,
    private val providerName: String,
    override val tier: ProviderTier,
    private val minIntervalMillis: Long,
) : LocationSource {
    private var listener: LocationListener? = null

    @SuppressLint("MissingPermission")
    override fun start(onFix: (LocationFix) -> Unit) {
        if (listener != null) return
        if (!locationManager.allProviders.contains(providerName)) return
        val newListener =
            LocationListener { location -> onFix(location.toLocationFix(tier)) }
        listener = newListener
        locationManager.requestLocationUpdates(
            providerName,
            minIntervalMillis,
            0f,
            newListener,
            Looper.getMainLooper(),
        )
    }

    override fun stop() {
        listener?.let { locationManager.removeUpdates(it) }
        listener = null
    }

    private companion object {
        fun Location.toLocationFix(tier: ProviderTier): LocationFix = LocationFix(
            tier = tier,
            speedMetersPerSecond = if (hasSpeed()) speed else 0f,
            hasSpeed = hasSpeed(),
            speedAccuracyMetersPerSecond = if (hasSpeedAccuracy()) speedAccuracyMetersPerSecond else 0f,
            hasSpeedAccuracy = hasSpeedAccuracy(),
            timestampMillis = elapsedRealtimeNanos / 1_000_000L,
        )
    }
}

/** True when [ContextCompat.checkSelfPermission] grants fine or coarse location. */
fun hasLocationPermission(context: android.content.Context): Boolean {
    val fine =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION)
    val coarse =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION)
    return fine == android.content.pm.PackageManager.PERMISSION_GRANTED ||
        coarse == android.content.pm.PackageManager.PERMISSION_GRANTED
}
