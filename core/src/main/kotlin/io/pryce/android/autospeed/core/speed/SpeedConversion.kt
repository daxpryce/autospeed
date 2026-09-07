package io.pryce.android.autospeed.core.speed

import kotlin.math.floor

/**
 * Converts a provider-reported speed in meters per second (the unit returned by
 * `android.location.Location.getSpeed()`) into the units Autospeed displays.
 *
 * Conversion always starts from the original meters-per-second value; one displayed unit is
 * never derived from the other, per the Autospeed design's conversion requirement.
 */
object SpeedConversion {
    private const val KMH_PER_MPS = 3.6
    private const val MPH_PER_MPS = 2.2369362921

    /** Converts meters per second to kilometers per hour. */
    fun metersPerSecondToKmh(metersPerSecond: Float): Double = metersPerSecond * KMH_PER_MPS

    /** Converts meters per second to miles per hour. */
    fun metersPerSecondToMph(metersPerSecond: Float): Double = metersPerSecond * MPH_PER_MPS

    /**
     * Rounds a non-negative display value to the nearest whole unit, rounding an exact half
     * up rather than using banker's rounding, so behavior at unit boundaries (e.g. `x.5`) is
     * predictable for the driver and for tests.
     */
    fun roundForDisplay(value: Double): Int = floor(value + ROUNDING_OFFSET).toInt()

    private const val ROUNDING_OFFSET = 0.5

    /** Converts and rounds a meters-per-second value directly to the requested display unit. */
    fun toDisplayValue(
        metersPerSecond: Float,
        unit: SpeedUnit,
    ): Int {
        val raw =
            when (unit) {
                SpeedUnit.MPH -> metersPerSecondToMph(metersPerSecond)
                SpeedUnit.KMH -> metersPerSecondToKmh(metersPerSecond)
            }
        return roundForDisplay(raw)
    }
}
