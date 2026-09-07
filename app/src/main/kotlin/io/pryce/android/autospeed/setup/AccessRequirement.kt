package io.pryce.android.autospeed.setup

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import io.pryce.android.autospeed.R
import io.pryce.android.autospeed.location.hasLocationPermission

/**
 * Every permission or special access Autospeed can use, in the order the setup screen offers
 * them (design section 4.2).
 *
 * Design section 4.2 originally required each of these to be requested only at the moment its
 * feature was first used. That is a poor fit for the actual environment: Autospeed is used while
 * driving, so a just-in-time prompt appears exactly when the driver must not be dealing with a
 * system dialog. Setup therefore offers all of them up front, before the vehicle moves, while the
 * just-in-time paths are kept as fallbacks for access that is revoked or skipped later
 * (design section 6.5).
 *
 * Only [LOCATION] is required for Autospeed to function as a speedometer. Declining any other
 * entry disables just that one feature.
 */
enum class AccessRequirement(
    val titleRes: Int,
    val explanationRes: Int,
    val required: Boolean = false,
) {
    LOCATION(
        titleRes = R.string.access_location_title,
        explanationRes = R.string.access_location_explanation,
        required = true,
    ),
    NOTIFICATIONS(
        titleRes = R.string.access_notifications_title,
        explanationRes = R.string.access_notifications_explanation,
    ),
    OVERLAY(
        titleRes = R.string.access_overlay_title,
        explanationRes = R.string.access_overlay_explanation,
    ),
    NOTIFICATION_ACCESS(
        titleRes = R.string.access_notification_listener_title,
        explanationRes = R.string.access_notification_listener_explanation,
    ),
    NOTIFICATION_POLICY(
        titleRes = R.string.access_notification_policy_title,
        explanationRes = R.string.access_notification_policy_explanation,
    ),
    ;

    fun isGranted(context: Context): Boolean = when (this) {
        LOCATION -> {
            hasLocationPermission(context)
        }

        NOTIFICATIONS -> {
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        }

        OVERLAY -> {
            Settings.canDrawOverlays(context)
        }

        // The listener service is enabled from a system screen rather than a runtime dialog, so
        // the only reliable signal is whether Android currently lists this package as enabled.
        NOTIFICATION_ACCESS -> {
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        }

        NOTIFICATION_POLICY -> {
            context.getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true
        }
    }

    /**
     * The runtime permissions this entry requests through a system dialog, or an empty array when
     * the entry is special access that can only be granted from a system settings screen.
     */
    fun runtimePermissions(): Array<String> = when (this) {
        LOCATION -> arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        NOTIFICATIONS -> arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        OVERLAY, NOTIFICATION_ACCESS, NOTIFICATION_POLICY -> emptyArray()
    }

    /** The system settings screen for special access, or null when a runtime dialog is used. */
    fun settingsIntent(context: Context): Intent? = when (this) {
        LOCATION, NOTIFICATIONS -> {
            null
        }

        OVERLAY -> {
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())
        }

        NOTIFICATION_ACCESS -> {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }

        NOTIFICATION_POLICY -> {
            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        }
    }
}
