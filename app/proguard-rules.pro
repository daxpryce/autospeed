# Autospeed's release-build R8/ProGuard rules.
#
# Autospeed keeps no reflection-based APIs, no serialization frameworks, and no libraries that
# require consumer rules beyond what they ship in their own AARs, so this file intentionally adds
# nothing beyond a couple of narrowly-scoped keep rules for platform service entry points that the
# manifest references by name and R8 could otherwise consider unreachable.

# Entry points instantiated by the platform via manifest component names, not by application code,
# so R8's reachability analysis can't see the reference from MainActivity/AndroidManifest alone.
-keep class io.pryce.android.autospeed.MainActivity
-keep class io.pryce.android.autospeed.SettingsActivity
-keep class io.pryce.android.autospeed.AutospeedApplication
-keep class io.pryce.android.autospeed.media.AutospeedNotificationListenerService
-keep class io.pryce.android.autospeed.notification.AutospeedZenConditionProviderService
-keep class io.pryce.android.autospeed.overlay.OverlayService
