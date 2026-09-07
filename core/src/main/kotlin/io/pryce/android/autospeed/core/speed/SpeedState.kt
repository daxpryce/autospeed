package io.pryce.android.autospeed.core.speed

import io.pryce.android.autospeed.core.location.ProviderTier

/** Whether Autospeed currently holds the access it needs to receive location updates. */
enum class LocationAccess {
    GRANTED,
    PERMISSION_DENIED,
    PROVIDER_DISABLED,
}

/**
 * The current trustworthiness of Autospeed's speed reading, independent of display formatting.
 * Provider-reported speed is authoritative; this type never represents an inferred or smoothed
 * value.
 */
sealed interface SpeedState {
    /** A trustworthy, fresh speed is available from [tier]. */
    data class Available(
        val metersPerSecond: Float,
        val tier: ProviderTier,
    ) : SpeedState

    /** No fix has ever qualified for display since Autospeed started requesting updates. */
    data object AwaitingFirstFix : SpeedState

    /** A previously accepted fix exists but is now older than the freshness budget. */
    data object Stale : SpeedState

    /** A fix exists but lacks a trustworthy speed (no speed, or speed accuracy too poor). */
    data object Unavailable : SpeedState

    /** Location access itself is unavailable, independent of any provider fix quality. */
    data class AccessRestricted(
        val access: LocationAccess,
    ) : SpeedState {
        init {
            require(access != LocationAccess.GRANTED) {
                "AccessRestricted must not be constructed with GRANTED access"
            }
        }
    }
}
