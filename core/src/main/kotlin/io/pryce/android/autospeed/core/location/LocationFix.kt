package io.pryce.android.autospeed.core.location

/**
 * The location backend that produced a fix. Ordered here only for readability; preference
 * order for escalation is decided explicitly by [ProviderOrchestrator], not by declaration order.
 */
enum class ProviderTier {
    /** Android `LocationManager.FUSED_PROVIDER`. */
    SYSTEM_FUSED,

    /** Google Play services `FusedLocationProviderClient`, only present in the Play variant. */
    PLAY_FUSED,

    /** Android `LocationManager.GPS_PROVIDER` requested directly. */
    DIRECT_GNSS,
}

/**
 * A platform-independent snapshot of the fields Autospeed needs from an Android `Location`.
 *
 * This mirrors `Location.getSpeed()`, `Location.hasSpeed()`, `Location.getSpeedAccuracyMetersPerSecond()`,
 * `Location.hasSpeedAccuracy()`, and `Location.getElapsedRealtimeMillis()` (used here as
 * [timestampMillis] on the same clock as the orchestrator's `nowMillis`), plus the tier that
 * produced it. It intentionally carries no latitude/longitude/bearing: Autospeed never derives
 * speed by differencing coordinates and never persists or displays a fix's position.
 */
data class LocationFix(
    val tier: ProviderTier,
    val speedMetersPerSecond: Float,
    val hasSpeed: Boolean,
    val speedAccuracyMetersPerSecond: Float,
    val hasSpeedAccuracy: Boolean,
    val timestampMillis: Long,
)
