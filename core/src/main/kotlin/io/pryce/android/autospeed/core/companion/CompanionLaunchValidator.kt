package io.pryce.android.autospeed.core.companion

/**
 * Confirms whether a configured companion (map or media) application is still eligible to be
 * launched: it must be selected and still present among the currently installed packages
 * Autospeed resolved through intent queries (design section 3.5). This never falls back to an
 * implicit intent, browser, launcher, or chooser; a `false` result means the caller must show a
 * local error or send the user back to the relevant setting instead.
 */
object CompanionLaunchValidator {
    fun canLaunch(
        selectedPackage: String?,
        installedPackages: Set<String>,
    ): Boolean = selectedPackage != null && selectedPackage in installedPackages
}
