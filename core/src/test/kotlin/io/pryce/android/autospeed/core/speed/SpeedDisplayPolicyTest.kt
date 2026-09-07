package io.pryce.android.autospeed.core.speed

import io.pryce.android.autospeed.core.location.ProviderTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeedDisplayPolicyTest {
    @Test
    fun `permission denied overrides any speed state`() {
        val result =
            SpeedDisplayPolicy.decide(
                LocationAccess.PERMISSION_DENIED,
                SpeedState.Available(10f, ProviderTier.SYSTEM_FUSED),
                SpeedUnit.MPH,
                secondaryVisible = true,
            )
        assertEquals(SpeedDisplayStatus.PERMISSION_DENIED, result.status)
        assertNull(result.primaryValue)
        assertNull(result.secondaryValue)
    }

    @Test
    fun `provider disabled overrides any speed state`() {
        val result =
            SpeedDisplayPolicy.decide(
                LocationAccess.PROVIDER_DISABLED,
                SpeedState.Available(10f, ProviderTier.SYSTEM_FUSED),
                SpeedUnit.MPH,
                secondaryVisible = true,
            )
        assertEquals(SpeedDisplayStatus.LOCATION_DISABLED, result.status)
    }

    @Test
    fun `available with mph primary and secondary visible shows both units`() {
        val result =
            SpeedDisplayPolicy.decide(
                LocationAccess.GRANTED,
                SpeedState.Available(10f, ProviderTier.SYSTEM_FUSED),
                SpeedUnit.MPH,
                secondaryVisible = true,
            )
        assertEquals(SpeedDisplayStatus.AVAILABLE, result.status)
        assertEquals(SpeedUnit.MPH, result.primaryUnit)
        assertEquals(22, result.primaryValue)
        assertEquals(SpeedUnit.KMH, result.secondaryUnit)
        assertEquals(36, result.secondaryValue)
    }

    @Test
    fun `available with kmh primary shows mph secondary`() {
        val result =
            SpeedDisplayPolicy.decide(
                LocationAccess.GRANTED,
                SpeedState.Available(10f, ProviderTier.SYSTEM_FUSED),
                SpeedUnit.KMH,
                secondaryVisible = true,
            )
        assertEquals(SpeedUnit.KMH, result.primaryUnit)
        assertEquals(36, result.primaryValue)
        assertEquals(SpeedUnit.MPH, result.secondaryUnit)
        assertEquals(22, result.secondaryValue)
    }

    @Test
    fun `secondary hidden when setting is off`() {
        val result =
            SpeedDisplayPolicy.decide(
                LocationAccess.GRANTED,
                SpeedState.Available(10f, ProviderTier.SYSTEM_FUSED),
                SpeedUnit.MPH,
                secondaryVisible = false,
            )
        assertNull(result.secondaryUnit)
        assertNull(result.secondaryValue)
    }

    @Test
    fun `awaiting first fix shows no numeric value`() {
        val result =
            SpeedDisplayPolicy.decide(LocationAccess.GRANTED, SpeedState.AwaitingFirstFix, SpeedUnit.MPH, true)
        assertEquals(SpeedDisplayStatus.AWAITING_FIRST_FIX, result.status)
        assertNull(result.primaryValue)
    }

    @Test
    fun `stale shows no numeric value`() {
        val result = SpeedDisplayPolicy.decide(LocationAccess.GRANTED, SpeedState.Stale, SpeedUnit.MPH, true)
        assertEquals(SpeedDisplayStatus.STALE, result.status)
        assertNull(result.primaryValue)
    }

    @Test
    fun `unavailable shows no numeric value`() {
        val result = SpeedDisplayPolicy.decide(LocationAccess.GRANTED, SpeedState.Unavailable, SpeedUnit.MPH, true)
        assertEquals(SpeedDisplayStatus.UNAVAILABLE, result.status)
        assertNull(result.primaryValue)
    }

    @Test
    fun `raw access restricted state with permission denied is handled directly`() {
        val result =
            SpeedDisplayPolicy.decide(
                LocationAccess.GRANTED,
                SpeedState.AccessRestricted(LocationAccess.PERMISSION_DENIED),
                SpeedUnit.MPH,
                true,
            )
        assertEquals(SpeedDisplayStatus.PERMISSION_DENIED, result.status)
    }

    @Test
    fun `raw access restricted state with provider disabled is handled directly`() {
        val result =
            SpeedDisplayPolicy.decide(
                LocationAccess.GRANTED,
                SpeedState.AccessRestricted(LocationAccess.PROVIDER_DISABLED),
                SpeedUnit.MPH,
                true,
            )
        assertEquals(SpeedDisplayStatus.LOCATION_DISABLED, result.status)
    }

    @Test
    fun `view state data class exposes equality`() {
        val a = SpeedDisplayViewState(SpeedDisplayStatus.AVAILABLE, SpeedUnit.MPH, 10, SpeedUnit.KMH, 16)
        val b = SpeedDisplayViewState(SpeedDisplayStatus.AVAILABLE, SpeedUnit.MPH, 10, SpeedUnit.KMH, 16)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }
}
