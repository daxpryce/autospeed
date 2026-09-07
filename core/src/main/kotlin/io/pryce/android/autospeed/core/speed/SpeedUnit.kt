package io.pryce.android.autospeed.core.speed

/** A unit in which Autospeed can display location-derived speed. */
enum class SpeedUnit {
    MPH,
    KMH,
    ;

    /** The other supported unit, used when deciding the secondary display value. */
    fun other(): SpeedUnit = if (this == MPH) KMH else MPH
}
