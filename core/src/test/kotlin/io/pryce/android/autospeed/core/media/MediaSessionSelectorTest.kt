package io.pryce.android.autospeed.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSessionSelectorTest {
    private fun session(
        id: String,
        playing: Boolean,
        lastActive: Long,
    ) = MediaSessionInfo(id, "com.example.$id", playing, lastActive, true, true, true)

    @Test
    fun `no sessions selects nothing`() {
        assertNull(MediaSessionSelector.select(emptyList()))
    }

    @Test
    fun `single playing session is selected`() {
        val s = session("a", playing = true, lastActive = 1)
        assertEquals(s, MediaSessionSelector.select(listOf(s)))
    }

    @Test
    fun `playing session preferred over more recently active paused session`() {
        val playing = session("a", playing = true, lastActive = 1)
        val pausedNewer = session("b", playing = false, lastActive = 100)
        assertEquals(playing, MediaSessionSelector.select(listOf(pausedNewer, playing)))
    }

    @Test
    fun `most recently active playing session wins among several playing sessions`() {
        val older = session("a", playing = true, lastActive = 1)
        val newer = session("b", playing = true, lastActive = 2)
        assertEquals(newer, MediaSessionSelector.select(listOf(older, newer)))
    }

    @Test
    fun `falls back to most recently active session when none are playing`() {
        val older = session("a", playing = false, lastActive = 1)
        val newer = session("b", playing = false, lastActive = 2)
        assertEquals(newer, MediaSessionSelector.select(listOf(older, newer)))
    }

    @Test
    fun `session info equality across every field`() {
        val a = session("a", playing = true, lastActive = 1)
        assertEquals(a, a)
        assertEquals(a, a.copy())
        assertEquals(a.hashCode(), a.copy().hashCode())
        assertNotEquals(a, a.copy(sessionId = "other"))
        assertNotEquals(a, a.copy(packageName = "com.other"))
        assertNotEquals(a, a.copy(isPlaying = false))
        assertNotEquals(a, a.copy(lastActiveTimestampMillis = 2))
        assertNotEquals(a, a.copy(supportsPlayPause = false))
        assertNotEquals(a, a.copy(supportsSkipNext = false))
        assertNotEquals(a, a.copy(supportsSkipPrevious = false))
        assertFalse(a.equals("not a session"))
        assertTrue(a.toString().contains("MediaSessionInfo"))
    }
}
