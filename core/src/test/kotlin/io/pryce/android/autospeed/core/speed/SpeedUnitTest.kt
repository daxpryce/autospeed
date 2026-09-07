package io.pryce.android.autospeed.core.speed

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedUnitTest {
    @Test
    fun `other returns kmh for mph`() {
        assertEquals(SpeedUnit.KMH, SpeedUnit.MPH.other())
    }

    @Test
    fun `other returns mph for kmh`() {
        assertEquals(SpeedUnit.MPH, SpeedUnit.KMH.other())
    }
}
