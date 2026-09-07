package io.pryce.android.autospeed.core.speed

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedConversionTest {
    @Test
    fun `converts meters per second to kmh exactly`() {
        assertEquals(36.0, SpeedConversion.metersPerSecondToKmh(10f), 0.0)
    }

    @Test
    fun `converts meters per second to mph exactly`() {
        assertEquals(22.369362921, SpeedConversion.metersPerSecondToMph(10f), 1e-9)
    }

    @Test
    fun `zero speed converts to zero in both units`() {
        assertEquals(0.0, SpeedConversion.metersPerSecondToKmh(0f), 0.0)
        assertEquals(0.0, SpeedConversion.metersPerSecondToMph(0f), 0.0)
    }

    @Test
    fun `rounds exact half up rather than to even`() {
        // Kotlin's kotlin.math.round() uses banker's rounding; Autospeed's display rounding
        // must round 0.5 and 2.5 both up, not alternate toward even values.
        assertEquals(1, SpeedConversion.roundForDisplay(0.5))
        assertEquals(3, SpeedConversion.roundForDisplay(2.5))
    }

    @Test
    fun `rounds below half down`() {
        assertEquals(0, SpeedConversion.roundForDisplay(0.49))
        assertEquals(2, SpeedConversion.roundForDisplay(2.49))
    }

    @Test
    fun `rounds above half up`() {
        assertEquals(1, SpeedConversion.roundForDisplay(0.51))
        assertEquals(3, SpeedConversion.roundForDisplay(2.51))
    }

    @Test
    fun `rounds exact whole numbers to themselves`() {
        assertEquals(5, SpeedConversion.roundForDisplay(5.0))
    }

    @Test
    fun `toDisplayValue converts and rounds for mph`() {
        assertEquals(22, SpeedConversion.toDisplayValue(10f, SpeedUnit.MPH))
    }

    @Test
    fun `toDisplayValue converts and rounds for kmh`() {
        assertEquals(36, SpeedConversion.toDisplayValue(10f, SpeedUnit.KMH))
    }
}
