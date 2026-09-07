package io.pryce.android.autospeed.core.build

import org.junit.Assert.assertEquals
import org.junit.Test

class BuildProfileTest {
    @Test
    fun `profiles are distinct`() {
        assertEquals(2, BuildProfile.entries.size)
        assertEquals(BuildProfile.PUBLIC, BuildProfile.valueOf("PUBLIC"))
        assertEquals(BuildProfile.PERSONAL, BuildProfile.valueOf("PERSONAL"))
    }

    @Test
    fun `location backends are distinct`() {
        assertEquals(2, LocationBackend.entries.size)
        assertEquals(LocationBackend.FRAMEWORK, LocationBackend.valueOf("FRAMEWORK"))
        assertEquals(LocationBackend.PLAY, LocationBackend.valueOf("PLAY"))
    }
}
