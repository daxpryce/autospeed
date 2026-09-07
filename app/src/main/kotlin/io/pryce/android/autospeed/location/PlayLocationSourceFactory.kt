package io.pryce.android.autospeed.location

import android.content.Context

/**
 * Produces the Play-fused [LocationSource] when this build's location flavor includes Google
 * Play services, or `null` when it does not. The `framework` flavor's implementation of this
 * interface never references any Play services class, keeping the framework variant's dependency
 * graph and compiled bytecode completely free of Play (design section 2.2).
 */
interface PlayLocationSourceFactory {
    fun create(
        context: Context,
        minIntervalMillis: Long,
    ): LocationSource?
}
