package io.pryce.android.autospeed.core.notification

import io.pryce.android.autospeed.core.build.BuildProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZenPolicyTest {
    @Test
    fun `activates only when all three conditions hold`() {
        val active = ZenPolicyState(suppressionEnabled = true, policyAccessGranted = true, anySurfaceVisible = true)
        assertTrue(ZenRuleDecision.shouldActivate(active))
    }

    @Test
    fun `does not activate when suppression is disabled`() {
        val state = ZenPolicyState(suppressionEnabled = false, policyAccessGranted = true, anySurfaceVisible = true)
        assertFalse(ZenRuleDecision.shouldActivate(state))
    }

    @Test
    fun `does not activate without policy access`() {
        val state = ZenPolicyState(suppressionEnabled = true, policyAccessGranted = false, anySurfaceVisible = true)
        assertFalse(ZenRuleDecision.shouldActivate(state))
    }

    @Test
    fun `does not activate when no surface is visible`() {
        val state = ZenPolicyState(suppressionEnabled = true, policyAccessGranted = true, anySurfaceVisible = false)
        assertFalse(ZenRuleDecision.shouldActivate(state))
    }

    @Test
    fun `notification suppression defaults enabled for personal and disabled for public`() {
        assertTrue(NotificationSuppressionDefaults.defaultFor(BuildProfile.PERSONAL))
        assertFalse(NotificationSuppressionDefaults.defaultFor(BuildProfile.PUBLIC))
    }

    @Test
    fun `play fallback defaults enabled for personal and disabled for public`() {
        assertTrue(PlayFallbackDefaults.defaultFor(BuildProfile.PERSONAL))
        assertFalse(PlayFallbackDefaults.defaultFor(BuildProfile.PUBLIC))
    }

    @Test
    fun `zen policy state equality across every field`() {
        val a = ZenPolicyState(true, true, true)
        assertEquals(a, a.copy())
        assertNotEquals(a, a.copy(suppressionEnabled = false))
        assertNotEquals(a, a.copy(policyAccessGranted = false))
        assertNotEquals(a, a.copy(anySurfaceVisible = false))
        assertEquals(a.hashCode(), a.copy().hashCode())
        assertTrue(a.toString().contains("ZenPolicyState"))
    }
}
