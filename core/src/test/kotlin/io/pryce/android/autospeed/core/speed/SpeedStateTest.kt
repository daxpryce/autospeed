package io.pryce.android.autospeed.core.speed

import io.pryce.android.autospeed.core.location.ProviderTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedStateTest {
    @Test
    fun `available holds meters per second and tier`() {
        val state = SpeedState.Available(12.5f, ProviderTier.SYSTEM_FUSED)
        assertEquals(12.5f, state.metersPerSecond)
        assertEquals(ProviderTier.SYSTEM_FUSED, state.tier)
    }

    @Test
    fun `available is equal for identical values and unequal for different tiers`() {
        val a = SpeedState.Available(12.5f, ProviderTier.SYSTEM_FUSED)
        val b = SpeedState.Available(12.5f, ProviderTier.SYSTEM_FUSED)
        val c = SpeedState.Available(12.5f, ProviderTier.DIRECT_GNSS)
        val d = SpeedState.Available(1f, ProviderTier.SYSTEM_FUSED)
        assertEquals(a, a)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
        assertNotEquals(a, d)
        assertNotEquals(a, "not a speed state")
        assertTrue(a.toString().contains("Available"))
    }

    @Test
    fun `access restricted accepts permission denied`() {
        val state = SpeedState.AccessRestricted(LocationAccess.PERMISSION_DENIED)
        assertEquals(LocationAccess.PERMISSION_DENIED, state.access)
    }

    @Test
    fun `access restricted accepts provider disabled`() {
        val state = SpeedState.AccessRestricted(LocationAccess.PROVIDER_DISABLED)
        assertEquals(LocationAccess.PROVIDER_DISABLED, state.access)
    }

    @Test
    fun `access restricted rejects granted access`() {
        assertThrows(IllegalArgumentException::class.java) {
            SpeedState.AccessRestricted(LocationAccess.GRANTED)
        }
    }

    @Test
    fun `singleton states are stable`() {
        assertFalse(SpeedState.AwaitingFirstFix == SpeedState.Stale)
        assertEquals(SpeedState.AwaitingFirstFix, SpeedState.AwaitingFirstFix)
        assertEquals(SpeedState.Stale, SpeedState.Stale)
        assertEquals(SpeedState.Unavailable, SpeedState.Unavailable)
    }
}
