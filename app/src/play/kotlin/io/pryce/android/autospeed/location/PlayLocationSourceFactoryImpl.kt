package io.pryce.android.autospeed.location

import android.content.Context
import com.google.android.gms.location.LocationServices

/**
 * The `play` flavor's Play-fused location source, built only when this build variant includes
 * `com.google.android.gms:play-services-location` (design section 2.2).
 */
class PlayLocationSourceFactoryImpl : PlayLocationSourceFactory {
    override fun create(
        context: Context,
        minIntervalMillis: Long,
    ): LocationSource = PlayFusedLocationSource(
        client = LocationServices.getFusedLocationProviderClient(context),
        minIntervalMillis = minIntervalMillis,
    )
}
