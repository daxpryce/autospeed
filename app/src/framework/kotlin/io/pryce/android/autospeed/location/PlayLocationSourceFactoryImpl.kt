package io.pryce.android.autospeed.location

import android.content.Context

/**
 * The `framework` flavor never constructs a Play location source (design section 2.2): this
 * implementation always returns `null` and contains no reference to any
 * `com.google.android.gms` class.
 */
class PlayLocationSourceFactoryImpl : PlayLocationSourceFactory {
    override fun create(
        context: Context,
        minIntervalMillis: Long,
    ): LocationSource? = null
}
