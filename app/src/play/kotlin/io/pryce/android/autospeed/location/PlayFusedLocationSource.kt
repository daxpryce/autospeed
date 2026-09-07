package io.pryce.android.autospeed.location

import android.annotation.SuppressLint
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import io.pryce.android.autospeed.core.location.LocationFix
import io.pryce.android.autospeed.core.location.ProviderTier

/**
 * Wraps Google Play services [FusedLocationProviderClient] as the quality-driven optional
 * fallback described in design section 2.2.1. Only present in the `play` flavor's source set and
 * dependency graph; the `framework` flavor contains no reference to this class or to any Play
 * services artifact.
 */
class PlayFusedLocationSource(
    private val client: FusedLocationProviderClient,
    private val minIntervalMillis: Long,
) : LocationSource {
    override val tier: ProviderTier = ProviderTier.PLAY_FUSED

    private var callback: LocationCallback? = null

    @SuppressLint("MissingPermission")
    override fun start(onFix: (LocationFix) -> Unit) {
        if (callback != null) return
        val request =
            LocationRequest
                .Builder(Priority.PRIORITY_HIGH_ACCURACY, minIntervalMillis)
                .setMinUpdateIntervalMillis(minIntervalMillis)
                .build()
        val newCallback =
            object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val location = result.lastLocation ?: return
                    onFix(
                        LocationFix(
                            tier = ProviderTier.PLAY_FUSED,
                            speedMetersPerSecond = if (location.hasSpeed()) location.speed else 0f,
                            hasSpeed = location.hasSpeed(),
                            speedAccuracyMetersPerSecond =
                                if (location.hasSpeedAccuracy()) location.speedAccuracyMetersPerSecond else 0f,
                            hasSpeedAccuracy = location.hasSpeedAccuracy(),
                            timestampMillis = location.elapsedRealtimeNanos / 1_000_000L,
                        ),
                    )
                }
            }
        callback = newCallback
        client.requestLocationUpdates(request, newCallback, Looper.getMainLooper())
    }

    override fun stop() {
        callback?.let { client.removeLocationUpdates(it) }
        callback = null
    }
}
