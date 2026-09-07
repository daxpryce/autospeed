package io.pryce.android.autospeed.core.appearance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LightHysteresisConfigTest {
    @Test
    fun `accepts a valid config`() {
        val config = LightHysteresisConfig(enterNightBelowLux = 10f, enterDayAboveLux = 50f, dwellMillis = 1000)
        assertEquals(10f, config.enterNightBelowLux)
    }

    @Test
    fun `rejects night threshold not below day threshold`() {
        assertThrows(IllegalArgumentException::class.java) {
            LightHysteresisConfig(enterNightBelowLux = 50f, enterDayAboveLux = 50f, dwellMillis = 0)
        }
    }

    @Test
    fun `rejects negative dwell`() {
        assertThrows(IllegalArgumentException::class.java) {
            LightHysteresisConfig(enterNightBelowLux = 10f, enterDayAboveLux = 50f, dwellMillis = -1)
        }
    }

    @Test
    fun `equality across every field`() {
        val a = LightHysteresisConfig(10f, 50f, 1000)
        assertEquals(a, a.copy())
        assertNotEquals(a, a.copy(enterNightBelowLux = 5f))
        assertNotEquals(a, a.copy(enterDayAboveLux = 60f))
        assertNotEquals(a, a.copy(dwellMillis = 500))
        assertEquals(a.hashCode(), a.copy().hashCode())
        assertTrue(a.toString().contains("LightHysteresisConfig"))
    }
}

class AmbientLightAppearanceResolverTest {
    private val config = LightHysteresisConfig(enterNightBelowLux = 10f, enterDayAboveLux = 50f, dwellMillis = 1000)

    @Test
    fun `starts at the given initial appearance`() {
        val resolver = AmbientLightAppearanceResolver(config, Appearance.DAY)
        assertEquals(Appearance.DAY, resolver.current)
    }

    @Test
    fun `dead zone sample never changes appearance`() {
        val resolver = AmbientLightAppearanceResolver(config, Appearance.DAY)
        assertEquals(Appearance.DAY, resolver.onLuxSample(30f, 0))
    }

    @Test
    fun `sample matching current appearance resets any pending proposal`() {
        val resolver = AmbientLightAppearanceResolver(config, Appearance.DAY)
        resolver.onLuxSample(5f, 0) // pending NIGHT since t=0
        resolver.onLuxSample(60f, 100) // still day, cancels the pending night proposal
        // Night must now need a full fresh dwell period starting from this new proposal.
        resolver.onLuxSample(5f, 100) // pending NIGHT since t=100 (freshly re-armed)
        assertEquals(Appearance.DAY, resolver.onLuxSample(5f, 100 + 999))
        assertEquals(Appearance.NIGHT, resolver.onLuxSample(5f, 100 + 1000))
    }

    @Test
    fun `sustained low lux switches to night after the dwell interval`() {
        val resolver = AmbientLightAppearanceResolver(config, Appearance.DAY)
        assertEquals(Appearance.DAY, resolver.onLuxSample(5f, 0))
        assertEquals(Appearance.DAY, resolver.onLuxSample(5f, 999))
        assertEquals(Appearance.NIGHT, resolver.onLuxSample(5f, 1000))
    }

    @Test
    fun `sustained high lux switches to day after the dwell interval`() {
        val resolver = AmbientLightAppearanceResolver(config, Appearance.NIGHT)
        assertEquals(Appearance.NIGHT, resolver.onLuxSample(60f, 0))
        assertEquals(Appearance.NIGHT, resolver.onLuxSample(60f, 999))
        assertEquals(Appearance.DAY, resolver.onLuxSample(60f, 1000))
    }

    @Test
    fun `a dead zone sample midway through the dwell resets the timer`() {
        val resolver = AmbientLightAppearanceResolver(config, Appearance.DAY)
        resolver.onLuxSample(5f, 0) // pending NIGHT since t=0
        resolver.onLuxSample(30f, 500) // dead zone: cancels the pending proposal
        // At t=1000, 1000ms have passed since the original (now-cancelled) proposal at t=0, but
        // the timer must have restarted, so the appearance has not changed yet.
        assertEquals(Appearance.DAY, resolver.onLuxSample(5f, 1000))
        assertEquals(Appearance.DAY, resolver.onLuxSample(5f, 1999))
        assertEquals(Appearance.NIGHT, resolver.onLuxSample(5f, 2000))
    }

    @Test
    fun `sample already at the current appearance while a proposal is pending is a no-op`() {
        val resolver = AmbientLightAppearanceResolver(config, Appearance.DAY)
        resolver.onLuxSample(60f, 0) // still day: proposed == current branch
        assertEquals(Appearance.DAY, resolver.current)
    }
}

class AppearanceResolverTest {
    @Test
    fun `day mode is always day`() {
        assertEquals(Appearance.DAY, AppearanceResolver.resolve(AppearanceMode.DAY, true, Appearance.NIGHT, true))
    }

    @Test
    fun `night mode is always night`() {
        assertEquals(Appearance.NIGHT, AppearanceResolver.resolve(AppearanceMode.NIGHT, false, null, false))
    }

    @Test
    fun `automatic mode uses the sensor result when available`() {
        assertEquals(
            Appearance.NIGHT,
            AppearanceResolver.resolve(AppearanceMode.AUTOMATIC, true, Appearance.NIGHT, systemNightMode = false),
        )
    }

    @Test
    fun `automatic mode falls back to system night mode when sensor unavailable`() {
        assertEquals(
            Appearance.NIGHT,
            AppearanceResolver.resolve(AppearanceMode.AUTOMATIC, false, null, systemNightMode = true),
        )
    }

    @Test
    fun `automatic mode falls back to system day when sensor unavailable and system is day`() {
        assertEquals(
            Appearance.DAY,
            AppearanceResolver.resolve(AppearanceMode.AUTOMATIC, false, null, systemNightMode = false),
        )
    }

    @Test
    fun `automatic mode falls back to system mode when sensor available but has no sample yet`() {
        assertEquals(
            Appearance.NIGHT,
            AppearanceResolver.resolve(AppearanceMode.AUTOMATIC, true, null, systemNightMode = true),
        )
    }
}
