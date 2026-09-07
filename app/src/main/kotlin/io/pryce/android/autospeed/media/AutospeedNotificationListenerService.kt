package io.pryce.android.autospeed.media

import android.content.ComponentName
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import io.pryce.android.autospeed.core.media.MediaSessionInfo
import io.pryce.android.autospeed.core.media.MediaSessionSelector

/**
 * Autospeed's notification-listener access is used only to reach
 * [MediaSessionManager.getActiveSessions] and its authorized [MediaController] transport
 * controls (design section 3.4); it never reads, stores, or displays notification content, and
 * this class overrides no notification-content callback. Notification access is a special system
 * grant the user enables separately in Settings, distinct from an ordinary runtime permission.
 */
class AutospeedNotificationListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        registerActiveSessionsListener()
    }

    override fun onListenerDisconnected() {
        MediaControlManager.onControllersChanged(emptyList())
        super.onListenerDisconnected()
    }

    private fun registerActiveSessionsListener() {
        val manager = getSystemService(MediaSessionManager::class.java) ?: return
        val component = ComponentName(this, AutospeedNotificationListenerService::class.java)
        runCatching {
            manager.addOnActiveSessionsChangedListener(
                { controllers -> MediaControlManager.onControllersChanged(controllers.orEmpty()) },
                component,
            )
            MediaControlManager.onControllersChanged(manager.getActiveSessions(component))
        }
    }
}

/**
 * Chooses among the currently active, controllable media sessions using
 * [MediaSessionSelector]'s deterministic rule and exposes transport controls for the selected
 * one. Holds no track metadata, artwork, or listening history — only the fields
 * [MediaSessionInfo] defines.
 */
object MediaControlManager {
    private var controllers: List<MediaController> = emptyList()
    private var listener: (() -> Unit)? = null

    /** Registers a callback invoked whenever the set of controllable sessions changes. */
    fun setListener(onChanged: (() -> Unit)?) {
        listener = onChanged
    }

    fun onControllersChanged(newControllers: List<MediaController>) {
        controllers = newControllers
        listener?.invoke()
    }

    fun currentSessions(): List<MediaSessionInfo> = controllers.map { it.toMediaSessionInfo() }

    fun selectedSession(): MediaSessionInfo? = MediaSessionSelector.select(currentSessions())

    fun playPause() {
        val target = selectedController() ?: return
        val playing = target.playbackState?.state == PlaybackState.STATE_PLAYING
        if (playing) target.transportControls.pause() else target.transportControls.play()
    }

    fun skipNext() {
        selectedController()?.transportControls?.skipToNext()
    }

    fun skipPrevious() {
        selectedController()?.transportControls?.skipToPrevious()
    }

    private fun selectedController(): MediaController? {
        val selected = selectedSession() ?: return null
        return controllers.find { it.sessionToken.toString() == selected.sessionId }
    }

    private fun MediaController.toMediaSessionInfo(): MediaSessionInfo {
        val state = playbackState
        val actions = state?.actions ?: 0L
        return MediaSessionInfo(
            sessionId = sessionToken.toString(),
            packageName = packageName,
            isPlaying = state?.state == PlaybackState.STATE_PLAYING,
            lastActiveTimestampMillis = state?.lastPositionUpdateTime ?: SystemClock.elapsedRealtime(),
            supportsPlayPause =
                actions and
                    (
                        PlaybackState.ACTION_PLAY or
                            PlaybackState.ACTION_PAUSE or
                            PlaybackState.ACTION_PLAY_PAUSE
                    ) != 0L,
            supportsSkipNext = actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L,
            supportsSkipPrevious = actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L,
        )
    }
}
