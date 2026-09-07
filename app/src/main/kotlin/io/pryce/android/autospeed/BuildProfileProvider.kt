package io.pryce.android.autospeed

import android.content.Context
import io.pryce.android.autospeed.core.build.BuildProfile
import io.pryce.android.autospeed.core.build.LocationBackend

/**
 * Resolves this build's compile-time [BuildProfile] and [LocationBackend] from the flavor-scoped
 * string resources set in `app/build.gradle.kts` (`advisory_profile`, `location_backend`). Using
 * resources instead of `BuildConfig` fields avoids enabling the `buildConfig` feature for a build
 * this small.
 */
object BuildProfileProvider {
    fun buildProfile(context: Context): BuildProfile {
        val raw = context.getString(R.string.advisory_profile)
        return when (raw) {
            "personal" -> BuildProfile.PERSONAL
            else -> BuildProfile.PUBLIC
        }
    }

    fun locationBackend(context: Context): LocationBackend {
        val raw = context.getString(R.string.location_backend)
        return when (raw) {
            "play" -> LocationBackend.PLAY
            else -> LocationBackend.FRAMEWORK
        }
    }
}
