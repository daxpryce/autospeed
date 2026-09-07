package io.pryce.android.autospeed.companion

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.core.net.toUri
import io.pryce.android.autospeed.core.companion.CompanionLaunchValidator

/** One installed application Autospeed can offer as a map or media companion choice. */
data class InstalledApplication(
    val packageName: String,
    val label: String,
)

/**
 * The two companion roles Autospeed can hand off to (design section 3.5). Each role resolves its
 * own qualifying intent, which is also the exact set declared in the manifest `<queries>` block,
 * so Autospeed never needs broad installed-package visibility.
 */
enum class CompanionRole {
    /** Applications that can display a geographic location, i.e. mapping applications. */
    MAP,

    /** Applications that declare themselves a music/audio application. */
    MEDIA,
    ;

    /**
     * The intent whose resolution defines eligibility for this role (design section 9,
     * "Companion compatibility rules"). Resolving the intent is only a compatibility test: no
     * data is ever sent to the companion, and the launch itself uses the application's own
     * explicit launcher intent.
     */
    fun qualifyingIntent(): Intent = when (this) {
        MAP -> Intent(Intent.ACTION_VIEW, "geo:0,0".toUri())
        MEDIA -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC)
    }
}

/**
 * Resolves installed applications for the map/media companion pickers and launches a selected one
 * (design section 3.5). Selection uses Android intent resolution and application labels rather
 * than a hard-coded package list, and is narrowed to the intents that actually qualify an
 * application for the requested role so Autospeed never enumerates every installed application.
 * Every launch uses an explicit package-scoped intent; this class never falls back to an implicit
 * intent, browser, launcher, or chooser.
 */
class CompanionLauncher(
    private val context: Context,
) {
    private val packageManager: PackageManager = context.packageManager

    /** Lists installed applications eligible for [role], sorted by label, for a settings picker. */
    fun listApplications(role: CompanionRole): List<InstalledApplication> = packageManager
        .queryIntentActivities(role.qualifyingIntent(), 0)
        .mapNotNull { resolveInfo ->
            val applicationInfo = resolveInfo.activityInfo?.applicationInfo ?: return@mapNotNull null
            val packageName = applicationInfo.packageName ?: return@mapNotNull null
            if (packageName == context.packageName) return@mapNotNull null
            if (packageManager.getLaunchIntentForPackage(packageName) == null) return@mapNotNull null
            InstalledApplication(packageName, applicationInfo.labelFor())
        }.distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }

    /**
     * Whether [packageName] is still installed and eligible for explicit launch in [role].
     * Design section 6.5: a removed companion must be detected before every dependent operation.
     */
    fun canLaunch(
        packageName: String?,
        role: CompanionRole,
    ): Boolean = CompanionLaunchValidator.canLaunch(packageName, installedPackageNames(role))

    /**
     * Launches [packageName] using only its own explicit launcher intent. Returns `false` (and
     * launches nothing) if the package has no resolvable launcher intent; never substitutes an
     * implicit intent, browser, or chooser.
     */
    fun launch(packageName: String): Boolean {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    private fun installedPackageNames(role: CompanionRole): Set<String> =
        listApplications(role).map { it.packageName }.toSet()

    private fun ApplicationInfo.labelFor(): String = packageManager.getApplicationLabel(this).toString()
}
