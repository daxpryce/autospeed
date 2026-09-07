package io.pryce.android.autospeed.notification

import io.pryce.android.autospeed.core.build.BuildProfile
import io.pryce.android.autospeed.core.notification.NotificationSuppressionDefaults
import io.pryce.android.autospeed.core.notification.ZenPolicyState
import io.pryce.android.autospeed.core.notification.ZenRuleDecision

/**
 * Tracks the inputs [ZenRuleDecision] needs and calls back into the Android adapter only when the
 * decision actually flips, so [io.pryce.android.autospeed.notification.ZenRuleManager] never issues
 * redundant `NotificationManager` calls. This class is deliberately platform-independent so its
 * transition logic is covered by JVM tests; only the Android side effect it triggers needs
 * Android-level testing.
 */
class ZenPolicyStateTracker(
    profile: BuildProfile,
    private val onActivationChanged: (Boolean) -> Unit,
) {
    private var state =
        ZenPolicyState(
            suppressionEnabled = NotificationSuppressionDefaults.defaultFor(profile),
            policyAccessGranted = false,
            anySurfaceVisible = false,
        )
    private var active = false

    fun updateSuppressionEnabled(enabled: Boolean) = update(state.copy(suppressionEnabled = enabled))

    fun updatePolicyAccessGranted(granted: Boolean) = update(state.copy(policyAccessGranted = granted))

    fun updateSurfaceVisible(visible: Boolean) = update(state.copy(anySurfaceVisible = visible))

    private fun update(newState: ZenPolicyState) {
        state = newState
        val shouldBeActive = ZenRuleDecision.shouldActivate(state)
        if (shouldBeActive != active) {
            active = shouldBeActive
            onActivationChanged(shouldBeActive)
        }
    }
}
