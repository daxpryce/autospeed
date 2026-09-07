package io.pryce.android.autospeed.core.settings

import io.pryce.android.autospeed.core.appearance.AppearanceMode
import io.pryce.android.autospeed.core.build.BuildProfile
import io.pryce.android.autospeed.core.notification.NotificationSuppressionDefaults
import io.pryce.android.autospeed.core.notification.PlayFallbackDefaults
import io.pryce.android.autospeed.core.speed.SpeedUnit

/**
 * Every value Autospeed is allowed to persist (design section 3.8). No location fix, speed
 * sample, trip, route, media metadata, usage event, diagnostic event, or permission history may
 * be added here.
 */
data class AutospeedSettings(
    val orientationMode: OrientationMode = OrientationMode.LANDSCAPE,
    val primaryUnit: SpeedUnit = SpeedUnit.MPH,
    val secondaryVisible: Boolean = true,
    val selectedMapPackage: String? = null,
    val selectedMediaPackage: String? = null,
    val playFallbackEnabled: Boolean = false,
    val notificationSuppressionEnabled: Boolean = false,
    val appearanceMode: AppearanceMode = AppearanceMode.AUTOMATIC,
    val backgroundMode: BackgroundMode = BackgroundMode.SYSTEM_WALLPAPER,
    val backgroundColorArgb: Int? = null,
    val backgroundImageUri: String? = null,
    val overlayPositionX: Float? = null,
    val overlayPositionY: Float? = null,
    val advisoryShown: Boolean = false,
    /**
     * Whether the user has been taken through the up-front access setup screen. Design section
     * 3.8 permits storing completion state for local onboarding; this is that flag and nothing
     * more. It records only that the screen was shown and dismissed, never which access was
     * granted or denied, which would be permission history.
     */
    val setupCompleted: Boolean = false,
)

/**
 * The settings Autospeed should start with before the user changes anything, given the compile-time
 * build profile. Only [AutospeedSettings.playFallbackEnabled] and
 * [AutospeedSettings.notificationSuppressionEnabled] vary by profile; every other field uses the
 * same default regardless of profile.
 */
object SettingsDefaults {
    fun defaultsFor(profile: BuildProfile): AutospeedSettings = AutospeedSettings(
        playFallbackEnabled = PlayFallbackDefaults.defaultFor(profile),
        notificationSuppressionEnabled = NotificationSuppressionDefaults.defaultFor(profile),
    )
}
