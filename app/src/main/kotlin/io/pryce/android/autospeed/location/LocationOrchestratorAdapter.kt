package io.pryce.android.autospeed.location

import android.content.Context
import android.location.LocationManager
import android.os.SystemClock
import io.pryce.android.autospeed.core.location.LocationFix
import io.pryce.android.autospeed.core.location.ProviderOrchestrator
import io.pryce.android.autospeed.core.location.ProviderQualityBudget
import io.pryce.android.autospeed.core.location.ProviderTier
import io.pryce.android.autospeed.core.speed.LocationAccess
import io.pryce.android.autospeed.core.speed.SpeedState

/** The budget values chosen for Autospeed's first release (design section 2.2.1/9). */
object DefaultProviderQualityBudget {
    val value =
        ProviderQualityBudget(
            staleAfterMillis = 5_000L,
            maxSpeedAccuracyMetersPerSecond = 5f,
            recoveryStableIntervalMillis = 10_000L,
        )
    const val MIN_UPDATE_INTERVAL_MILLIS = 1_000L
}

/**
 * Owns the single active set of location subscriptions Autospeed may hold at a time (design
 * section 5.3): the system fused source, the direct-GNSS warming source, and (in the `play`
 * flavor, only when the user's [io.pryce.android.autospeed.core.settings.AutospeedSettings.playFallbackEnabled]
 * setting allows it) the Play-fused source. Feeds every fix into [ProviderOrchestrator] and
 * reports the resulting [SpeedState] plus [LocationAccess] to whichever callback [start] most
 * recently supplied.
 *
 * [start] is idempotent while already active: calling it again (e.g. when the overlay service
 * takes over from the main activity, or vice versa) only swaps which callback receives updates.
 * This is what lets ownership transfer between the main activity and the overlay service without
 * ever creating a second underlying platform location listener (design section 5.3); the caller
 * (`ServiceLocator`) is responsible for calling [stop] only once no Autospeed surface remains
 * visible at all.
 *
 * Direct GNSS is warmed concurrently whenever it is not the currently accepted tier, per the
 * orchestrator's own [io.pryce.android.autospeed.core.location.OrchestratorResult.shouldWarmDirectGnss] signal,
 * so the final fallback is ready quickly without ever blending two providers' speeds.
 */
class LocationOrchestratorAdapter(
    private val context: Context,
    private val playLocationSourceFactory: PlayLocationSourceFactory,
) {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val systemFusedSource =
        FrameworkLocationManagerSource(
            locationManager = locationManager,
            providerName = LocationManager.FUSED_PROVIDER,
            tier = ProviderTier.SYSTEM_FUSED,
            minIntervalMillis = DefaultProviderQualityBudget.MIN_UPDATE_INTERVAL_MILLIS,
        )

    private val directGnssSource =
        FrameworkLocationManagerSource(
            locationManager = locationManager,
            providerName = LocationManager.GPS_PROVIDER,
            tier = ProviderTier.DIRECT_GNSS,
            minIntervalMillis = DefaultProviderQualityBudget.MIN_UPDATE_INTERVAL_MILLIS,
        )

    private var playSource: LocationSource? = null
    private var orchestrator: ProviderOrchestrator? = null
    private val latestFixes = mutableMapOf<ProviderTier, LocationFix>()
    private var active = false
    private var listener: ((LocationAccess, SpeedState) -> Unit)? = null

    /**
     * Registers [onUpdate] as the current recipient of location updates and starts requesting
     * updates if not already active. [allowPlayFallback] must reflect the user's current
     * `playFallbackEnabled` setting; the framework flavor's [playLocationSourceFactory] always
     * returns `null` regardless of this flag, so it has no effect there. Ignored (beyond the
     * listener swap) if subscriptions are already running.
     */
    fun start(
        allowPlayFallback: Boolean,
        onUpdate: (LocationAccess, SpeedState) -> Unit,
    ) {
        listener = onUpdate
        if (active) return
        active = true
        latestFixes.clear()
        val newOrchestrator = ProviderOrchestrator(allowPlayFallback, DefaultProviderQualityBudget.value)
        orchestrator = newOrchestrator

        if (!hasLocationPermission(context)) {
            onUpdate(LocationAccess.PERMISSION_DENIED, SpeedState.AwaitingFirstFix)
            return
        }
        if (!locationManager.isLocationEnabled) {
            onUpdate(LocationAccess.PROVIDER_DISABLED, SpeedState.AwaitingFirstFix)
            return
        }

        fun handleFix(fix: LocationFix) {
            latestFixes[fix.tier] = fix
            val result = newOrchestrator.evaluate(SystemClock.elapsedRealtime(), latestFixes)
            listener?.invoke(LocationAccess.GRANTED, result.speedState)
            if (result.shouldWarmDirectGnss) {
                directGnssSource.start(::handleFix)
            } else {
                directGnssSource.stop()
            }
            if (result.shouldRequestPlay && playSource == null) {
                playSource =
                    playLocationSourceFactory.create(context, DefaultProviderQualityBudget.MIN_UPDATE_INTERVAL_MILLIS)
                playSource?.start(::handleFix)
            } else if (!result.shouldRequestPlay) {
                playSource?.stop()
                playSource = null
            }
        }

        systemFusedSource.start(::handleFix)
        onUpdate(LocationAccess.GRANTED, SpeedState.AwaitingFirstFix)
    }

    /** Stops every active subscription. Safe to call when already stopped. */
    fun stop() {
        active = false
        listener = null
        systemFusedSource.stop()
        directGnssSource.stop()
        playSource?.stop()
        playSource = null
        orchestrator = null
    }
}
