package io.pryce.android.autospeed.core.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationFixTest {
    private fun canonical() = LocationFix(
        tier = ProviderTier.SYSTEM_FUSED,
        speedMetersPerSecond = 10f,
        hasSpeed = true,
        speedAccuracyMetersPerSecond = 1f,
        hasSpeedAccuracy = true,
        timestampMillis = 1000L,
    )

    @Test
    fun `equal fixes with identical fields are equal`() {
        assertEquals(canonical(), canonical())
        assertEquals(canonical().hashCode(), canonical().hashCode())
    }

    @Test
    fun `differing tier is not equal`() {
        assertNotEquals(canonical(), canonical().copy(tier = ProviderTier.DIRECT_GNSS))
    }

    @Test
    fun `differing speed is not equal`() {
        assertNotEquals(canonical(), canonical().copy(speedMetersPerSecond = 1f))
    }

    @Test
    fun `differing has speed is not equal`() {
        assertNotEquals(canonical(), canonical().copy(hasSpeed = false))
    }

    @Test
    fun `differing speed accuracy is not equal`() {
        assertNotEquals(canonical(), canonical().copy(speedAccuracyMetersPerSecond = 9f))
    }

    @Test
    fun `differing has speed accuracy is not equal`() {
        assertNotEquals(canonical(), canonical().copy(hasSpeedAccuracy = false))
    }

    @Test
    fun `differing timestamp is not equal`() {
        assertNotEquals(canonical(), canonical().copy(timestampMillis = 2000L))
    }

    @Test
    fun `toString contains class name`() {
        assertTrue(canonical().toString().contains("LocationFix"))
    }

    @Test
    fun `not equal to a different type`() {
        assertFalse(canonical().equals("not a fix"))
    }
}
