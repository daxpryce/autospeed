package io.pryce.android.autospeed.core.media

/**
 * A platform-independent snapshot of one controllable media session, derived from an Android
 * `MediaController` reached through Autospeed's notification-listener access. This never carries
 * track metadata, artwork, or listening history; only what is needed to pick and control a
 * session.
 */
data class MediaSessionInfo(
    val sessionId: String,
    val packageName: String,
    val isPlaying: Boolean,
    val lastActiveTimestampMillis: Long,
    val supportsPlayPause: Boolean,
    val supportsSkipNext: Boolean,
    val supportsSkipPrevious: Boolean,
)

/**
 * Chooses which of several controllable media sessions Autospeed's transport controls should
 * target, per the Autospeed design's deterministic selection rule: prefer a playing session,
 * then fall back to the most recently active controllable session.
 */
object MediaSessionSelector {
    fun select(sessions: List<MediaSessionInfo>): MediaSessionInfo? {
        val playing = sessions.filter { it.isPlaying }
        if (playing.isNotEmpty()) {
            return playing.maxByOrNull { it.lastActiveTimestampMillis }
        }
        return sessions.maxByOrNull { it.lastActiveTimestampMillis }
    }
}
