package io.pryce.android.autospeed.core.settings

import io.pryce.android.autospeed.core.appearance.AppearanceMode
import io.pryce.android.autospeed.core.build.BuildProfile
import io.pryce.android.autospeed.core.speed.SpeedUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrientationAndBackgroundModeTest {
    @Test
    fun `orientation modes are distinct`() {
        assertEquals(3, OrientationMode.entries.size)
        assertEquals(OrientationMode.LANDSCAPE, OrientationMode.valueOf("LANDSCAPE"))
        assertEquals(OrientationMode.PORTRAIT, OrientationMode.valueOf("PORTRAIT"))
        assertEquals(OrientationMode.AUTO_SELECT, OrientationMode.valueOf("AUTO_SELECT"))
    }

    @Test
    fun `background modes are distinct`() {
        assertEquals(3, BackgroundMode.entries.size)
        assertEquals(BackgroundMode.SYSTEM_WALLPAPER, BackgroundMode.valueOf("SYSTEM_WALLPAPER"))
        assertEquals(BackgroundMode.SOLID_COLOR, BackgroundMode.valueOf("SOLID_COLOR"))
        assertEquals(BackgroundMode.IMAGE, BackgroundMode.valueOf("IMAGE"))
    }
}

class AutospeedSettingsTest {
    @Test
    fun `default settings use documented values`() {
        val settings = AutospeedSettings()
        assertEquals(OrientationMode.LANDSCAPE, settings.orientationMode)
        assertEquals(SpeedUnit.MPH, settings.primaryUnit)
        assertTrue(settings.secondaryVisible)
        assertEquals(null, settings.selectedMapPackage)
        assertEquals(null, settings.selectedMediaPackage)
        assertFalse(settings.playFallbackEnabled)
        assertFalse(settings.notificationSuppressionEnabled)
        assertEquals(AppearanceMode.AUTOMATIC, settings.appearanceMode)
        assertEquals(BackgroundMode.SYSTEM_WALLPAPER, settings.backgroundMode)
        assertEquals(null, settings.backgroundColorArgb)
        assertEquals(null, settings.backgroundImageUri)
        assertEquals(null, settings.overlayPositionX)
        assertEquals(null, settings.overlayPositionY)
        assertFalse(settings.advisoryShown)
    }

    @Test
    fun `public profile defaults disable both privacy sensitive settings`() {
        val settings = SettingsDefaults.defaultsFor(BuildProfile.PUBLIC)
        assertFalse(settings.playFallbackEnabled)
        assertFalse(settings.notificationSuppressionEnabled)
    }

    @Test
    fun `personal profile defaults enable both privacy sensitive settings`() {
        val settings = SettingsDefaults.defaultsFor(BuildProfile.PERSONAL)
        assertTrue(settings.playFallbackEnabled)
        assertTrue(settings.notificationSuppressionEnabled)
    }

    @Test
    fun `settings equality across every field`() {
        val a = AutospeedSettings()
        assertEquals(a, a.copy())
        assertEquals(a.hashCode(), a.copy().hashCode())
        assertNotEquals(a, a.copy(orientationMode = OrientationMode.PORTRAIT))
        assertNotEquals(a, a.copy(primaryUnit = SpeedUnit.KMH))
        assertNotEquals(a, a.copy(secondaryVisible = false))
        assertNotEquals(a, a.copy(selectedMapPackage = "com.example.maps"))
        assertNotEquals(a, a.copy(selectedMediaPackage = "com.example.media"))
        assertNotEquals(a, a.copy(playFallbackEnabled = true))
        assertNotEquals(a, a.copy(notificationSuppressionEnabled = true))
        assertNotEquals(a, a.copy(appearanceMode = AppearanceMode.DAY))
        assertNotEquals(a, a.copy(backgroundMode = BackgroundMode.SOLID_COLOR))
        assertNotEquals(a, a.copy(backgroundColorArgb = 0xff000000.toInt()))
        assertNotEquals(a, a.copy(backgroundImageUri = "content://image"))
        assertNotEquals(a, a.copy(overlayPositionX = 1f))
        assertNotEquals(a, a.copy(overlayPositionY = 1f))
        assertNotEquals(a, a.copy(advisoryShown = true))
        assertFalse(a.equals("not settings"))
        assertTrue(a.toString().contains("AutospeedSettings"))
    }
}
