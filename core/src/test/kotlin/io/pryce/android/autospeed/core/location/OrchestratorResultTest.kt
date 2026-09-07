package io.pryce.android.autospeed.core.location

import io.pryce.android.autospeed.core.speed.SpeedState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrchestratorResultTest {
    private fun canonical() = OrchestratorResult(
        acceptedTier = ProviderTier.SYSTEM_FUSED,
        speedState = SpeedState.Available(1f, ProviderTier.SYSTEM_FUSED),
        shouldWarmDirectGnss = true,
        shouldRequestPlay = false,
    )

    @Test
    fun `equal results with identical fields are equal`() {
        assertEquals(canonical(), canonical())
        assertEquals(canonical().hashCode(), canonical().hashCode())
    }

    @Test
    fun `differing accepted tier is not equal`() {
        assertNotEquals(canonical(), canonical().copy(acceptedTier = ProviderTier.DIRECT_GNSS))
    }

    @Test
    fun `differing accepted tier null is not equal`() {
        assertNotEquals(canonical(), canonical().copy(acceptedTier = null))
    }

    @Test
    fun `differing speed state is not equal`() {
        assertNotEquals(canonical(), canonical().copy(speedState = SpeedState.Stale))
    }

    @Test
    fun `differing warm flag is not equal`() {
        assertNotEquals(canonical(), canonical().copy(shouldWarmDirectGnss = false))
    }

    @Test
    fun `differing request play flag is not equal`() {
        assertNotEquals(canonical(), canonical().copy(shouldRequestPlay = true))
    }

    @Test
    fun `toString contains class name`() {
        assertTrue(canonical().toString().contains("OrchestratorResult"))
    }
}
