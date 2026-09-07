package io.pryce.android.autospeed.notification

import io.pryce.android.autospeed.core.build.BuildProfile
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers [ZenPolicyStateTracker]'s edge-triggered activation callback: it must fire exactly once
 * per genuine flip of [io.pryce.android.autospeed.core.notification.ZenRuleDecision.shouldActivate] and never
 * for a redundant update that leaves the decision unchanged (design section 4.3).
 */
class ZenPolicyStateTrackerTest {
    @Test
    fun `does not activate until all three conditions are true`() {
        val activations = mutableListOf<Boolean>()
        val tracker = ZenPolicyStateTracker(BuildProfile.PUBLIC) { activations.add(it) }

        tracker.updateSuppressionEnabled(true)
        tracker.updatePolicyAccessGranted(true)
        assertEquals(emptyList<Boolean>(), activations)

        tracker.updateSurfaceVisible(true)
        assertEquals(listOf(true), activations)
    }

    @Test
    fun `redundant updates do not re-invoke the callback`() {
        val activations = mutableListOf<Boolean>()
        val tracker = ZenPolicyStateTracker(BuildProfile.PERSONAL) { activations.add(it) }

        // Personal profile defaults suppressionEnabled to true, but policy access and surface
        // visibility both still default to false, so it should not yet be active.
        tracker.updatePolicyAccessGranted(true)
        tracker.updateSurfaceVisible(true)
        assertEquals(listOf(true), activations)

        // Setting the same values again must not re-trigger the callback.
        tracker.updatePolicyAccessGranted(true)
        tracker.updateSurfaceVisible(true)
        tracker.updateSuppressionEnabled(true)
        assertEquals(listOf(true), activations)
    }

    @Test
    fun `deactivates when any single condition later becomes false`() {
        val activations = mutableListOf<Boolean>()
        val tracker = ZenPolicyStateTracker(BuildProfile.PUBLIC) { activations.add(it) }

        tracker.updateSuppressionEnabled(true)
        tracker.updatePolicyAccessGranted(true)
        tracker.updateSurfaceVisible(true)
        assertEquals(listOf(true), activations)

        tracker.updateSurfaceVisible(false)
        assertEquals(listOf(true, false), activations)
    }

    @Test
    fun `public profile defaults suppression disabled and personal defaults enabled`() {
        val publicActivations = mutableListOf<Boolean>()
        val personalActivations = mutableListOf<Boolean>()
        val publicTracker = ZenPolicyStateTracker(BuildProfile.PUBLIC) { publicActivations.add(it) }
        val personalTracker = ZenPolicyStateTracker(BuildProfile.PERSONAL) { personalActivations.add(it) }

        publicTracker.updatePolicyAccessGranted(true)
        publicTracker.updateSurfaceVisible(true)
        assertEquals(emptyList<Boolean>(), publicActivations)

        personalTracker.updatePolicyAccessGranted(true)
        personalTracker.updateSurfaceVisible(true)
        assertEquals(listOf(true), personalActivations)
    }
}
