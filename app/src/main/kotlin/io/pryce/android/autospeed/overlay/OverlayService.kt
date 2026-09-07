package io.pryce.android.autospeed.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.pryce.android.autospeed.MainActivity
import io.pryce.android.autospeed.R
import io.pryce.android.autospeed.ServiceLocator
import io.pryce.android.autospeed.appearance.AppearanceManager
import io.pryce.android.autospeed.core.appearance.Appearance
import io.pryce.android.autospeed.core.settings.AutospeedSettings
import io.pryce.android.autospeed.core.speed.LocationAccess
import io.pryce.android.autospeed.core.speed.SpeedDisplayPolicy
import io.pryce.android.autospeed.core.speed.SpeedState
import io.pryce.android.autospeed.media.MediaControlManager
import io.pryce.android.autospeed.ui.SpeedSignView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val EXTRA_COMPANION = "companion"
private const val NOTIFICATION_CHANNEL_ID = "overlay"
private const val NOTIFICATION_ID = 1
private const val TAP_SLOP_PX = 12

/**
 * A small foreground-service-owned overlay window (design section 3.6): map-companion shows
 * speed plus previous/play-pause/next; media-companion shows speed only. Tapping the speed region
 * returns [MainActivity] to the foreground and removes the overlay. Location ownership is
 * transferred to/from the main activity via [ServiceLocator]'s surface-visibility tracking rather
 * than a second listener (see [io.pryce.android.autospeed.location.LocationOrchestratorAdapter]).
 */
class OverlayService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var speedDisplayView: SpeedSignView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var latestSettings: AutospeedSettings? = null
    private var latestAccess: LocationAccess = LocationAccess.PERMISSION_DENIED
    private var latestSpeedState: SpeedState = SpeedState.AwaitingFirstFix

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.surfaceBecameVisible()
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val companion = intent?.getStringExtra(EXTRA_COMPANION)?.let(OverlayCompanion::valueOf) ?: OverlayCompanion.MAP
        startForeground(NOTIFICATION_ID, buildNotification(companion))
        showOverlay(companion)
        observeSettingsAndLocation(companion)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        removeOverlay()
        serviceScope.cancel()
        ServiceLocator.surfaceBecameHidden()
        super.onDestroy()
    }

    private fun observeSettingsAndLocation(companion: OverlayCompanion) {
        serviceScope.launch {
            ServiceLocator.settingsRepository.settings.collect { settings ->
                latestSettings = settings
                refreshDisplay()
            }
        }
        ServiceLocator.locationOrchestratorAdapter.start(
            latestSettings?.playFallbackEnabled ?: false,
        ) { access, state ->
            latestAccess = access
            latestSpeedState = state
            refreshDisplay()
        }
        if (companion == OverlayCompanion.MAP) {
            MediaControlManager.setListener { }
        }
    }

    private fun refreshDisplay() {
        val settings = latestSettings ?: return
        val viewState =
            SpeedDisplayPolicy.decide(latestAccess, latestSpeedState, settings.primaryUnit, settings.secondaryVisible)
        speedDisplayView?.setViewState(viewState)
        // The overlay window is created before the settings flow emits, so the appearance applied
        // during inflation is always the default. Re-applying here is what makes the overlay honour
        // the selected palette at all, and what lets it follow a later day/night change.
        overlayView?.let(::applyOverlayAppearance)
    }

    /**
     * Design section 3.7: the night palette applies to every Autospeed surface, not just the main
     * screen. Without this the overlay always rendered in day colours -- a bright panel over the
     * map at night -- and its black icons were invisible on a dark scrim.
     */
    private fun applyOverlayAppearance(view: View) {
        val settings = latestSettings ?: return
        val appearance =
            ServiceLocator.appearanceManager.resolve(
                settings.appearanceMode,
                AppearanceManager.systemNightMode(this),
            )
        val night = appearance == Appearance.NIGHT
        speedDisplayView?.setAppearance(appearance)
        view.findViewById<View>(R.id.overlay_controls)?.backgroundTintList =
            ColorStateList.valueOf(
                ContextCompat.getColor(
                    this,
                    if (night) R.color.night_surface_scrim else R.color.day_surface_scrim,
                ),
            )
        val iconTint =
            ColorStateList.valueOf(
                ContextCompat.getColor(
                    this,
                    if (night) R.color.night_on_background else R.color.day_on_background,
                ),
            )
        listOf(
            R.id.overlay_previous_button,
            R.id.overlay_play_pause_button,
            R.id.overlay_next_button,
            R.id.overlay_close_button,
        ).mapNotNull { view.findViewById<ImageButton>(it) }
            .forEach { it.imageTintList = iconTint }
    }

    private fun showOverlay(companion: OverlayCompanion) {
        val manager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = manager
        val layoutRes =
            if (companion == OverlayCompanion.MAP) R.layout.overlay_map_companion else R.layout.overlay_media_companion
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(layoutRes, null)
        overlayView = view
        speedDisplayView = view.findViewById(R.id.overlay_speed_display)
        applyOverlayAppearance(view)

        if (companion == OverlayCompanion.MAP) {
            view.findViewById<ImageButton>(R.id.overlay_previous_button).setOnClickListener {
                MediaControlManager.skipPrevious()
            }
            view.findViewById<ImageButton>(R.id.overlay_play_pause_button).setOnClickListener {
                MediaControlManager.playPause()
            }
            view.findViewById<ImageButton>(R.id.overlay_next_button).setOnClickListener {
                MediaControlManager.skipNext()
            }
            // Design section 3.6: an obvious close control that stops the overlay immediately.
            view.findViewById<ImageButton>(R.id.overlay_close_button).setOnClickListener {
                stopSelf()
            }
        }

        val params = newLayoutParams()
        layoutParams = params
        attachDragAndTapToReturn(view, params, manager)
        manager.addView(view, params)
    }

    private fun newLayoutParams(): WindowManager.LayoutParams {
        val settings = latestSettings
        return WindowManager
            .LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                // The overlay is the visible Autospeed surface while a companion application is in
                // the foreground, so it -- not the main activity -- is what has to hold the screen
                // awake. Without this the display sleeps mid-drive with the map up.
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT,
            ).apply {
                // Design section 3.6: the overlay remembers only its local position. Until the
                // user drags it, it sits in the top-right corner clear of the system status area.
                gravity = Gravity.TOP or Gravity.END
                x = settings?.overlayPositionX?.toInt() ?: screenMarginPx()
                y = settings?.overlayPositionY?.toInt() ?: screenMarginPx()
            }
    }

    private fun screenMarginPx(): Int = resources.getDimensionPixelSize(R.dimen.overlay_screen_margin)

    @Suppress("ClickableViewAccessibility")
    private fun attachDragAndTapToReturn(
        view: View,
        params: WindowManager.LayoutParams,
        manager: WindowManager,
    ) {
        var startX = 0
        var startY = 0
        var startTouchX = 0f
        var startTouchY = 0f
        var moved = false
        val speedRegion = view.findViewById<View>(R.id.overlay_speed_display)
        speedRegion.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    startTouchX = event.rawX
                    startTouchY = event.rawY
                    moved = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    // The window is positioned from the top-right corner, so a rightward drag
                    // decreases x.
                    val dx = (startTouchX - event.rawX).toInt()
                    val dy = (event.rawY - startTouchY).toInt()
                    if (abs(dx) > TAP_SLOP_PX || abs(dy) > TAP_SLOP_PX) moved = true
                    params.x = startX + dx
                    params.y = startY + dy
                    runCatching { manager.updateViewLayout(view, params) }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        onReturnToMainActivity()
                    } else {
                        persistOverlayPosition(params)
                    }
                    true
                }

                else -> {
                    false
                }
            }
        }
    }

    private fun persistOverlayPosition(params: WindowManager.LayoutParams) {
        serviceScope.launch {
            ServiceLocator.settingsRepository.update {
                it.copy(overlayPositionX = params.x.toFloat(), overlayPositionY = params.y.toFloat())
            }
        }
    }

    private fun onReturnToMainActivity() {
        val intent =
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
        startActivity(intent)
        stopSelf()
    }

    private fun removeOverlay() {
        val manager = windowManager
        val view = overlayView
        if (manager != null && view != null) {
            runCatching { manager.removeView(view) }
        }
        overlayView = null
        speedDisplayView = null
        windowManager = null
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel =
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.overlay_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(companion: OverlayCompanion): Notification {
        val text =
            if (companion == OverlayCompanion.MAP) {
                getString(R.string.overlay_notification_text_map)
            } else {
                getString(R.string.overlay_notification_text_media)
            }
        val stopIntent =
            PendingIntent.getService(
                this,
                0,
                Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE,
            )
        val contentIntent =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
        return NotificationCompat
            .Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_map)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .addAction(0, getString(R.string.overlay_stop_action), stopIntent)
            .build()
    }

    companion object {
        private const val ACTION_STOP = "io.pryce.android.autospeed.overlay.STOP"

        fun start(
            context: Context,
            companion: OverlayCompanion,
        ) {
            val intent =
                Intent(context, OverlayService::class.java).putExtra(EXTRA_COMPANION, companion.name)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }
}
