package io.pryce.android.autospeed.core.appearance

/** The user-selectable appearance setting (design section 3.7). */
enum class AppearanceMode {
    DAY,
    NIGHT,
    AUTOMATIC,
}

/** The resolved palette Autospeed should currently render. */
enum class Appearance {
    DAY,
    NIGHT,
}

/**
 * Hysteresis configuration for automatic appearance driven by the ambient-light sensor. Separate
 * enter-night/enter-day thresholds plus a dwell interval keep passing shadows and headlights from
 * flickering the interface between palettes.
 */
data class LightHysteresisConfig(
    val enterNightBelowLux: Float,
    val enterDayAboveLux: Float,
    val dwellMillis: Long,
) {
    init {
        require(enterNightBelowLux < enterDayAboveLux) {
            "enterNightBelowLux must be less than enterDayAboveLux to leave a dead zone between them"
        }
        require(dwellMillis >= 0) { "dwellMillis must not be negative" }
    }
}

/**
 * Resolves ambient-light samples (`Sensor.TYPE_LIGHT`) into a hysteretic day/night decision.
 *
 * A lux sample at or below [LightHysteresisConfig.enterNightBelowLux] proposes night; a sample at
 * or above [LightHysteresisConfig.enterDayAboveLux] proposes day; anything between is a dead zone
 * that never changes the current resolution. A proposal must persist continuously for
 * [LightHysteresisConfig.dwellMillis] before it takes effect, and any sample outside the proposed
 * band (including a dead-zone sample) resets the pending proposal, so the interface never
 * oscillates from momentary light changes.
 *
 * This class is a deterministic state machine driven entirely by the [onLuxSample] arguments; it
 * holds no sensor reference and performs no I/O.
 */
class AmbientLightAppearanceResolver(
    private val config: LightHysteresisConfig,
    initial: Appearance,
) {
    var current: Appearance = initial
        private set

    private var pendingProposal: Appearance? = null
    private var pendingSinceMillis: Long = 0L

    /** Feeds one lux sample at [nowMillis] and returns the (possibly unchanged) resolution. */
    fun onLuxSample(
        lux: Float,
        nowMillis: Long,
    ): Appearance {
        val proposed =
            when {
                lux <= config.enterNightBelowLux -> Appearance.NIGHT
                lux >= config.enterDayAboveLux -> Appearance.DAY
                else -> null
            }
        if (proposed == null || proposed == current) {
            resetPending()
            return current
        }
        if (pendingProposal != proposed) {
            pendingProposal = proposed
            pendingSinceMillis = nowMillis
            return current
        }
        if (nowMillis - pendingSinceMillis >= config.dwellMillis) {
            current = proposed
            resetPending()
        }
        return current
    }

    private fun resetPending() {
        pendingProposal = null
        pendingSinceMillis = 0L
    }
}

/**
 * Resolves the final [Appearance] Autospeed should render from the user's [AppearanceMode]
 * setting, ambient-light-sensor availability/result, and the system night-mode state. Automatic
 * mode falls back to the system's night-mode signal when no light sensor is present or no usable
 * sample has been produced yet (design section 3.7).
 */
object AppearanceResolver {
    fun resolve(
        mode: AppearanceMode,
        lightSensorAvailable: Boolean,
        lightSensorResolution: Appearance?,
        systemNightMode: Boolean,
    ): Appearance = when (mode) {
        AppearanceMode.DAY -> {
            Appearance.DAY
        }

        AppearanceMode.NIGHT -> {
            Appearance.NIGHT
        }

        AppearanceMode.AUTOMATIC -> {
            if (lightSensorAvailable && lightSensorResolution != null) {
                lightSensorResolution
            } else if (systemNightMode) {
                Appearance.NIGHT
            } else {
                Appearance.DAY
            }
        }
    }
}
