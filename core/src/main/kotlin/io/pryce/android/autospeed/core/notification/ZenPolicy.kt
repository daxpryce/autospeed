package io.pryce.android.autospeed.core.notification

import io.pryce.android.autospeed.core.build.BuildProfile

/**
 * Inputs needed to decide whether Autospeed's app-owned `AutomaticZenRule` should be active
 * (design section 4.3). All three conditions are required simultaneously; there is no other path
 * to activation.
 */
data class ZenPolicyState(
    val suppressionEnabled: Boolean,
    val policyAccessGranted: Boolean,
    val anySurfaceVisible: Boolean,
)

/**
 * Decides whether Autospeed's notification-suppression Zen rule should be active. Autospeed must
 * modify only its own rule and only while a main or overlay surface is visible; this function is
 * the single source of truth for that condition so the Android adapter never has to duplicate it.
 */
object ZenRuleDecision {
    fun shouldActivate(state: ZenPolicyState): Boolean =
        state.suppressionEnabled && state.policyAccessGranted && state.anySurfaceVisible
}

/**
 * The notification-suppression setting defaults to enabled in the personal profile and disabled
 * in the public profile (design section 3.8), while remaining a normal user-changeable setting in
 * either profile.
 */
object NotificationSuppressionDefaults {
    fun defaultFor(profile: BuildProfile): Boolean = profile == BuildProfile.PERSONAL
}

/**
 * The Play-location-fallback setting (Play variant only) defaults to enabled in the personal
 * profile and disabled in the public profile (design section 2.2.1).
 */
object PlayFallbackDefaults {
    fun defaultFor(profile: BuildProfile): Boolean = profile == BuildProfile.PERSONAL
}
