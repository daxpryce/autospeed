package io.pryce.android.autospeed.core.build

/** Autospeed's compile-time driving-advisory profile (design section 2.1). */
enum class BuildProfile {
    PUBLIC,
    PERSONAL,
}

/** Autospeed's compile-time location backend (design section 2.2). */
enum class LocationBackend {
    FRAMEWORK,
    PLAY,
}
