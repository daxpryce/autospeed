package io.pryce.android.autospeed.location

import io.pryce.android.autospeed.core.location.LocationFix
import io.pryce.android.autospeed.core.location.ProviderTier

/**
 * An Android-side source of [LocationFix] snapshots for one [ProviderTier]. Implementations wrap
 * a single Android or Play-services location API and translate its callbacks into the
 * platform-independent [LocationFix] type consumed by `:core`'s
 * [io.pryce.android.autospeed.core.location.ProviderOrchestrator]. Only one component may hold an active
 * subscription per source at a time (design section 5.3); [LocationOrchestratorAdapter] owns that
 * invariant.
 */
interface LocationSource {
    val tier: ProviderTier

    /** Begins requesting updates, if not already started; delivers fixes to [onFix]. */
    fun start(onFix: (LocationFix) -> Unit)

    /** Stops requesting updates and releases any platform listener. Safe to call when stopped. */
    fun stop()
}
