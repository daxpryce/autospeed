package io.pryce.android.autospeed.core.location

/**
 * Quality budget thresholds that govern the privacy-first provider ladder described in the
 * Autospeed design (section 2.2.1). All durations share a single clock with the `nowMillis` and
 * [LocationFix.timestampMillis] values passed to [ProviderOrchestrator].
 */
data class ProviderQualityBudget(
    /** A fix older than this many milliseconds is treated as stale rather than current. */
    val staleAfterMillis: Long,
    /** A fix whose reported speed accuracy is worse (larger) than this is rejected. */
    val maxSpeedAccuracyMetersPerSecond: Float,
    /**
     * How long a more-preferred tier must continuously qualify before the orchestrator
     * de-escalates back to it. Prevents rapid provider flapping.
     */
    val recoveryStableIntervalMillis: Long,
) {
    init {
        require(staleAfterMillis > 0) { "staleAfterMillis must be positive" }
        require(maxSpeedAccuracyMetersPerSecond > 0f) { "maxSpeedAccuracyMetersPerSecond must be positive" }
        require(recoveryStableIntervalMillis >= 0) { "recoveryStableIntervalMillis must not be negative" }
    }
}
