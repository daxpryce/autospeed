package io.pryce.android.autospeed

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import androidx.core.net.toUri
import io.pryce.android.autospeed.companion.CompanionRole
import io.pryce.android.autospeed.core.build.BuildProfile
import io.pryce.android.autospeed.core.settings.AutospeedSettings
import io.pryce.android.autospeed.core.settings.OrientationMode
import io.pryce.android.autospeed.core.speed.LocationAccess
import io.pryce.android.autospeed.core.speed.SpeedDisplayPolicy
import io.pryce.android.autospeed.core.speed.SpeedState
import io.pryce.android.autospeed.location.hasLocationPermission
import io.pryce.android.autospeed.media.MediaControlManager
import io.pryce.android.autospeed.overlay.OverlayCompanion
import io.pryce.android.autospeed.overlay.OverlayService
import io.pryce.android.autospeed.setup.SetupActivity
import io.pryce.android.autospeed.ui.MainScreenAppearance
import io.pryce.android.autospeed.ui.SpeedDisplayView
import io.pryce.android.autospeed.ui.applyContentInsets
import io.pryce.android.autospeed.ui.dialogBuilder
import io.pryce.android.autospeed.ui.keepScreenOnWhileVisible
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val LOCATION_PERMISSION_REQUEST_CODE = 1001

/**
 * Autospeed's single main screen (design section 3.1): the speedometer, media transport buttons,
 * and buttons that open the user's configured map/media companion app (with the overlay started
 * first, design section 3.6). Deliberately a plain [Activity] rather than an AppCompat/Compose
 * activity to keep the dependency footprint minimal.
 */
class MainActivity : Activity() {
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var speedDisplayView: SpeedDisplayView
    private lateinit var backgroundImageView: ImageView
    private lateinit var rootView: FrameLayout
    private lateinit var locationActionButton: Button
    private lateinit var screenAppearance: MainScreenAppearance

    private var latestSettings: AutospeedSettings? = null
    private var latestAccess: LocationAccess = LocationAccess.PERMISSION_DENIED
    private var latestSpeedState: SpeedState = SpeedState.AwaitingFirstFix
    private var pendingCompanionLaunch: OverlayCompanion? = null
    private var settingsJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.keepScreenOnWhileVisible()
        bindContent()
    }

    /**
     * Inflates the orientation-appropriate layout and binds every view and listener to it.
     *
     * This is separate from [onCreate] because the activity declares `configChanges` for
     * orientation, so the system hands rotation to [onConfigurationChanged] instead of recreating
     * the activity. Re-running this is what actually swaps between `layout/activity_main.xml` and
     * `layout-land/activity_main.xml`; both declare the same ids, so the binding below is
     * identical in either orientation. Keeping the activity alive across rotation also keeps a
     * single uninterrupted location subscription rather than tearing it down and restarting it.
     */
    private fun bindContent() {
        setContentView(R.layout.activity_main)
        speedDisplayView = findViewById(R.id.speed_display)
        backgroundImageView = findViewById(R.id.background_image)
        rootView = findViewById(android.R.id.content)
        locationActionButton = findViewById(R.id.location_action_button)
        findViewById<View>(R.id.content_container)
            .applyContentInsets(resources.getDimensionPixelSize(R.dimen.content_padding))
        screenAppearance =
            MainScreenAppearance(
                appearanceManager = ServiceLocator.appearanceManager,
                window = window,
                rootView = rootView,
                backgroundImageView = backgroundImageView,
                speedDisplayView = speedDisplayView,
                iconButtons =
                    listOf(
                        findViewById(R.id.settings_button),
                        findViewById(R.id.previous_button),
                        findViewById(R.id.play_pause_button),
                        findViewById(R.id.next_button),
                    ),
                accentButtons =
                    listOf(
                        locationActionButton,
                        findViewById(R.id.map_button),
                        findViewById(R.id.media_button),
                    ),
            )
        wireButtons()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        bindContent()
        latestSettings?.let { screenAppearance.apply(it) }
        refreshDisplay()
        refreshMediaButton()
    }

    override fun onStart() {
        super.onStart()
        ServiceLocator.surfaceBecameVisible()
        // Design section 3.6: the overlay is the Autospeed surface shown *while a companion
        // application is in the foreground*. Once the main screen is visible it is redundant, and
        // it renders on top of Autospeed itself. Only tapping the overlay used to dismiss it, so
        // every other way back -- the back gesture, recents, the launcher, or the ongoing
        // notification, whose contentIntent opens this activity without stopping the service --
        // left it stranded over the main screen. Stopping it here covers all of them at once.
        // This cannot cancel a companion launch: launchCompanion starts the overlay while this
        // activity is already started, so no onStart runs between that call and onStop.
        OverlayService.stop(this)
        ServiceLocator.appearanceManager.start { latestSettings?.let { screenAppearance.apply(it) } }
        observeSettings()
        // Only resume updates for access we already hold. Asking for anything is deferred until
        // settings load, so that a first run opens the setup screen instead of firing a bare
        // system dialog at a user who has not been told why it is being asked for.
        if (hasLocationPermission(this)) {
            startLocationUpdates()
        }
        MediaControlManager.setListener { refreshMediaButton() }
        refreshMediaButton()
    }

    override fun onStop() {
        // Cancelling here matters: onStart is called again after every return to the app, and
        // without this each visit would leave another live collector attached to the settings
        // flow for the lifetime of the activity.
        settingsJob?.cancel()
        settingsJob = null
        ServiceLocator.appearanceManager.stop()
        MediaControlManager.setListener(null)
        ServiceLocator.surfaceBecameHidden()
        super.onStop()
    }

    override fun onDestroy() {
        activityScope.cancel()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            startLocationUpdates()
        }
    }

    private fun wireButtons() {
        findViewById<ImageButton>(R.id.settings_button).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<ImageButton>(R.id.previous_button).setOnClickListener { MediaControlManager.skipPrevious() }
        findViewById<ImageButton>(R.id.play_pause_button).setOnClickListener { MediaControlManager.playPause() }
        findViewById<ImageButton>(R.id.next_button).setOnClickListener { MediaControlManager.skipNext() }
        findViewById<Button>(R.id.map_button).setOnClickListener { onCompanionButtonPressed(OverlayCompanion.MAP) }
        findViewById<Button>(R.id.media_button).setOnClickListener { onCompanionButtonPressed(OverlayCompanion.MEDIA) }
        locationActionButton.setOnClickListener { onLocationActionPressed() }
    }

    /**
     * Design sections 4.2 and 6.5: a denied permission or a disabled device location must route
     * the user to the correct system screen instead of silently leaving the speed blank.
     */
    private fun onLocationActionPressed() {
        when (latestAccess) {
            LocationAccess.PERMISSION_DENIED -> requestLocationIfNeeded()
            LocationAccess.PROVIDER_DISABLED -> startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            LocationAccess.GRANTED -> Unit
        }
    }

    private fun observeSettings() {
        settingsJob =
            activityScope.launch {
                ServiceLocator.settingsRepository.settings.collect { settings ->
                    val firstObservedSettings = latestSettings == null
                    latestSettings = settings
                    applyOrientation(settings.orientationMode)
                    refreshDisplay()
                    screenAppearance.apply(settings)
                    if (firstObservedSettings) {
                        maybeShowAdvisory(settings)
                        if (settings.setupCompleted) {
                            requestLocationIfNeeded()
                        } else {
                            // Design section 4.2 as amended: offer every permission and special
                            // access once, up front, rather than interrupting the driver with a
                            // system dialog the first time each feature is touched.
                            startActivity(Intent(this@MainActivity, SetupActivity::class.java))
                        }
                    }
                }
            }
    }

    private fun maybeShowAdvisory(settings: AutospeedSettings) {
        if (ServiceLocator.buildProfile != BuildProfile.PUBLIC || settings.advisoryShown) return
        ServiceLocator.appearanceManager
            .dialogBuilder(this)
            .setMessage(R.string.advisory_message)
            .setCancelable(false)
            .setPositiveButton(R.string.advisory_dismiss) { _, _ ->
                activityScope.launch {
                    ServiceLocator.settingsRepository.update { it.copy(advisoryShown = true) }
                }
            }.show()
    }

    private fun requestLocationIfNeeded() {
        if (hasLocationPermission(this)) {
            startLocationUpdates()
            return
        }
        requestPermissions(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            LOCATION_PERMISSION_REQUEST_CODE,
        )
    }

    private fun startLocationUpdates() {
        val allowPlayFallback = latestSettings?.playFallbackEnabled ?: false
        ServiceLocator.locationOrchestratorAdapter.start(allowPlayFallback) { access, state ->
            latestAccess = access
            latestSpeedState = state
            refreshDisplay()
        }
    }

    /**
     * Design section 3.3: the orientation setting is applied to the window immediately, and the
     * fixed choices are sensor-aware so the screen still flips between the two opposite
     * landscape (or portrait) directions as the phone is turned in its mount.
     *
     * Until this was wired up the setting was persisted and shown in Settings but never acted on,
     * which is why the app opened in whatever orientation the sensor happened to pick.
     */
    private fun applyOrientation(mode: OrientationMode) {
        val requested =
            when (mode) {
                OrientationMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                OrientationMode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                OrientationMode.AUTO_SELECT -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        if (requestedOrientation != requested) {
            requestedOrientation = requested
        }
    }

    private fun refreshDisplay() {
        val settings = latestSettings ?: return
        val viewState =
            SpeedDisplayPolicy.decide(latestAccess, latestSpeedState, settings.primaryUnit, settings.secondaryVisible)
        speedDisplayView.setViewState(viewState)
        refreshLocationAction()
    }

    private fun refreshLocationAction() {
        val labelRes =
            when (latestAccess) {
                LocationAccess.PERMISSION_DENIED -> R.string.action_grant_location_permission
                LocationAccess.PROVIDER_DISABLED -> R.string.action_open_location_settings
                LocationAccess.GRANTED -> null
            }
        if (labelRes == null) {
            locationActionButton.visibility = View.GONE
            return
        }
        locationActionButton.setText(labelRes)
        locationActionButton.visibility = View.VISIBLE
    }

    private fun refreshMediaButton() {
        val playing = MediaControlManager.selectedSession()?.isPlaying == true
        val icon = if (playing) R.drawable.ic_pause else R.drawable.ic_play
        findViewById<ImageButton>(R.id.play_pause_button).setImageResource(icon)
    }

    private fun onCompanionButtonPressed(companion: OverlayCompanion) {
        val settings = latestSettings ?: return
        val packageName =
            if (companion == OverlayCompanion.MAP) settings.selectedMapPackage else settings.selectedMediaPackage
        if (packageName.isNullOrEmpty()) {
            startActivity(Intent(this, SettingsActivity::class.java))
            return
        }
        val role = if (companion == OverlayCompanion.MAP) CompanionRole.MAP else CompanionRole.MEDIA
        if (!ServiceLocator.companionLauncher.canLaunch(packageName, role)) {
            // Design section 6.5: a removed or no-longer-eligible companion clears its own
            // setting and sends the user back to pick another, rather than failing silently.
            clearCompanionSelection(companion)
            showLaunchError(R.string.error_companion_missing)
            startActivity(Intent(this, SettingsActivity::class.java))
            return
        }
        if (!hasOverlayPermission()) {
            pendingCompanionLaunch = companion
            requestOverlayPermission()
            return
        }
        launchCompanion(companion, packageName)
    }

    private fun clearCompanionSelection(companion: OverlayCompanion) {
        activityScope.launch {
            ServiceLocator.settingsRepository.update { settings ->
                if (companion == OverlayCompanion.MAP) {
                    settings.copy(selectedMapPackage = null)
                } else {
                    settings.copy(selectedMediaPackage = null)
                }
            }
        }
    }

    private fun launchCompanion(
        companion: OverlayCompanion,
        packageName: String,
    ) {
        OverlayService.start(this, companion)
        val launched = ServiceLocator.companionLauncher.launch(packageName)
        if (!launched) {
            // Design section 3.5: if the launch fails, stop the overlay and present a clear
            // error, keeping Autospeed itself in the foreground. Never fall back to a chooser.
            OverlayService.stop(this)
            showLaunchError(R.string.error_launch_failed)
        }
    }

    /**
     * Design section 7: an error must not be presented only as a transient message. A dismissible
     * dialog keeps the failure on screen until the user acknowledges it, and leaves Autospeed
     * itself in the foreground (design section 3.5).
     */
    private fun showLaunchError(messageRes: Int) {
        ServiceLocator.appearanceManager
            .dialogBuilder(this)
            .setMessage(messageRes)
            .setPositiveButton(R.string.advisory_dismiss, null)
            .show()
    }

    private fun hasOverlayPermission(): Boolean = Settings.canDrawOverlays(this)

    private fun requestOverlayPermission() {
        val intent =
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri())
        startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
    }

    @Deprecated("Deprecated in Java", ReplaceWith("registerForActivityResult"))
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            val companion = pendingCompanionLaunch
            pendingCompanionLaunch = null
            if (companion != null && hasOverlayPermission()) {
                onCompanionButtonPressed(companion)
            } else if (companion != null) {
                showLaunchError(R.string.error_overlay_permission_required)
            }
        }
    }

    private companion object {
        const val OVERLAY_PERMISSION_REQUEST_CODE = 1002
    }
}
