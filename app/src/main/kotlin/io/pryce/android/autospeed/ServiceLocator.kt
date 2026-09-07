package io.pryce.android.autospeed

import android.annotation.SuppressLint
import android.content.Context
import io.pryce.android.autospeed.appearance.AppearanceManager
import io.pryce.android.autospeed.companion.CompanionLauncher
import io.pryce.android.autospeed.core.build.BuildProfile
import io.pryce.android.autospeed.core.build.LocationBackend
import io.pryce.android.autospeed.location.LocationOrchestratorAdapter
import io.pryce.android.autospeed.location.PlayLocationSourceFactoryImpl
import io.pryce.android.autospeed.notification.ZenPolicyStateTracker
import io.pryce.android.autospeed.notification.ZenRuleManager
import io.pryce.android.autospeed.settings.SettingsRepository

/**
 * Holds the small number of shared singletons Autospeed needs across its activities and
 * services, constructed once in [AutospeedApplication.onCreate]. Deliberately a plain object
 * rather than a dependency-injection framework (design section 5.2).
 *
 * `StaticFieldLeak` is suppressed because [initialize] resolves `context.applicationContext`
 * before constructing anything: every holder below retains the process-lifetime Application
 * context, never an Activity or Service, so there is no context to leak. Lint cannot see through
 * the `applicationContext` hop into the constructors it flags.
 */
@SuppressLint("StaticFieldLeak")
object ServiceLocator {
    lateinit var buildProfile: BuildProfile
        private set
    lateinit var locationBackend: LocationBackend
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var locationOrchestratorAdapter: LocationOrchestratorAdapter
        private set
    lateinit var companionLauncher: CompanionLauncher
        private set
    lateinit var zenRuleManager: ZenRuleManager
        private set
    lateinit var appearanceManager: AppearanceManager
        private set
    lateinit var zenPolicyStateTracker: ZenPolicyStateTracker
        private set

    private var visibleSurfaceCount = 0

    fun initialize(context: Context) {
        val appContext = context.applicationContext
        buildProfile = BuildProfileProvider.buildProfile(appContext)
        locationBackend = BuildProfileProvider.locationBackend(appContext)
        settingsRepository = SettingsRepository(appContext, buildProfile)
        locationOrchestratorAdapter = LocationOrchestratorAdapter(appContext, PlayLocationSourceFactoryImpl())
        companionLauncher = CompanionLauncher(appContext)
        zenRuleManager = ZenRuleManager(appContext)
        appearanceManager = AppearanceManager(appContext)
        zenPolicyStateTracker =
            ZenPolicyStateTracker(buildProfile) { shouldActivate -> zenRuleManager.setActive(shouldActivate) }
    }

    /** Called when a main-activity or overlay surface becomes visible (design section 4.3). */
    fun surfaceBecameVisible() {
        visibleSurfaceCount++
        zenPolicyStateTracker.updateSurfaceVisible(visibleSurfaceCount > 0)
    }

    /**
     * Called when a main-activity or overlay surface is no longer visible. Only stops location
     * updates once every surface has gone, which is what lets ownership transfer between the
     * main activity and the overlay service (design section 5.3) without ever creating a second
     * platform location listener: whichever surface starts next simply becomes the new listener
     * on the still-registered [LocationOrchestratorAdapter] subscription (see its own
     * documentation), or restarts a fresh one if this was truly the last surface.
     */
    fun surfaceBecameHidden() {
        visibleSurfaceCount = maxOf(0, visibleSurfaceCount - 1)
        zenPolicyStateTracker.updateSurfaceVisible(visibleSurfaceCount > 0)
        if (visibleSurfaceCount == 0) {
            locationOrchestratorAdapter.stop()
        }
    }
}
