package io.pryce.android.autospeed.core.speed

/** Coarse-grained status the main and overlay UIs use to choose which affordance to show. */
enum class SpeedDisplayStatus {
    AWAITING_FIRST_FIX,
    AVAILABLE,
    STALE,
    UNAVAILABLE,
    PERMISSION_DENIED,
    LOCATION_DISABLED,
}

/** A fully resolved, display-ready snapshot of the speedometer. */
data class SpeedDisplayViewState(
    val status: SpeedDisplayStatus,
    val primaryUnit: SpeedUnit,
    val primaryValue: Int?,
    val secondaryUnit: SpeedUnit?,
    val secondaryValue: Int?,
)

/**
 * Turns location access, a [SpeedState], and the user's unit/visibility preferences into the
 * exact values and status the UI should render. This is the single place that decides how a
 * stale, unavailable, or access-restricted state is presented, so the main display and both
 * overlays stay consistent.
 */
object SpeedDisplayPolicy {
    fun decide(
        access: LocationAccess,
        state: SpeedState,
        primaryUnit: SpeedUnit,
        secondaryVisible: Boolean,
    ): SpeedDisplayViewState {
        val secondaryUnit = if (secondaryVisible) primaryUnit.other() else null
        if (access != LocationAccess.GRANTED) {
            val status =
                if (access == LocationAccess.PERMISSION_DENIED) {
                    SpeedDisplayStatus.PERMISSION_DENIED
                } else {
                    SpeedDisplayStatus.LOCATION_DISABLED
                }
            return SpeedDisplayViewState(status, primaryUnit, null, secondaryUnit, null)
        }
        return when (state) {
            is SpeedState.Available -> {
                availableViewState(state, primaryUnit, secondaryUnit)
            }

            SpeedState.AwaitingFirstFix -> {
                SpeedDisplayViewState(SpeedDisplayStatus.AWAITING_FIRST_FIX, primaryUnit, null, secondaryUnit, null)
            }

            SpeedState.Stale -> {
                SpeedDisplayViewState(SpeedDisplayStatus.STALE, primaryUnit, null, secondaryUnit, null)
            }

            SpeedState.Unavailable -> {
                SpeedDisplayViewState(SpeedDisplayStatus.UNAVAILABLE, primaryUnit, null, secondaryUnit, null)
            }

            is SpeedState.AccessRestricted -> {
                // The orchestrator itself never emits this variant (access is applied above,
                // ahead of the orchestrator's own state), but the branch stays exhaustive and
                // safe for direct callers that pass a raw restricted state.
                val status =
                    if (state.access == LocationAccess.PERMISSION_DENIED) {
                        SpeedDisplayStatus.PERMISSION_DENIED
                    } else {
                        SpeedDisplayStatus.LOCATION_DISABLED
                    }
                SpeedDisplayViewState(status, primaryUnit, null, secondaryUnit, null)
            }
        }
    }

    private fun availableViewState(
        state: SpeedState.Available,
        primaryUnit: SpeedUnit,
        secondaryUnit: SpeedUnit?,
    ): SpeedDisplayViewState {
        val primaryValue = SpeedConversion.toDisplayValue(state.metersPerSecond, primaryUnit)
        val secondaryValue = secondaryUnit?.let { SpeedConversion.toDisplayValue(state.metersPerSecond, it) }
        return SpeedDisplayViewState(
            SpeedDisplayStatus.AVAILABLE,
            primaryUnit,
            primaryValue,
            secondaryUnit,
            secondaryValue,
        )
    }
}
