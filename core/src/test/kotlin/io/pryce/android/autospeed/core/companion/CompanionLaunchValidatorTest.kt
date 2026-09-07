package io.pryce.android.autospeed.core.companion

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionLaunchValidatorTest {
    @Test
    fun `no selection cannot launch`() {
        assertFalse(CompanionLaunchValidator.canLaunch(null, setOf("com.example.maps")))
    }

    @Test
    fun `selection present in installed packages can launch`() {
        assertTrue(CompanionLaunchValidator.canLaunch("com.example.maps", setOf("com.example.maps")))
    }

    @Test
    fun `selection no longer installed cannot launch`() {
        assertFalse(CompanionLaunchValidator.canLaunch("com.example.maps", emptySet()))
    }
}
