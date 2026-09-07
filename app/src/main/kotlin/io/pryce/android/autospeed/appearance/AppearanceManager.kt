package io.pryce.android.autospeed.appearance

import android.content.Context
import android.content.res.Configuration
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import io.pryce.android.autospeed.core.appearance.AmbientLightAppearanceResolver
import io.pryce.android.autospeed.core.appearance.Appearance
import io.pryce.android.autospeed.core.appearance.AppearanceMode
import io.pryce.android.autospeed.core.appearance.AppearanceResolver
import io.pryce.android.autospeed.core.appearance.LightHysteresisConfig

/** The ambient-light hysteresis Autospeed starts with (design sections 3.7 and 9). */
private val DEFAULT_LIGHT_HYSTERESIS =
    LightHysteresisConfig(
        enterNightBelowLux = 10f,
        enterDayAboveLux = 40f,
        dwellMillis = 3_000L,
    )

/**
 * Resolves [Appearance] from the user's [AppearanceMode] setting, an optional ambient-light
 * sensor (`Sensor.TYPE_LIGHT`), and the system night-mode configuration (design section 3.7). No
 * sensor permission is required or requested: `TYPE_LIGHT` is a normal, non-dangerous sensor.
 */
class AppearanceManager(
    context: Context,
) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val lightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)
    private val hysteresisResolver =
        AmbientLightAppearanceResolver(DEFAULT_LIGHT_HYSTERESIS, initial = Appearance.DAY)

    private var lastLightResolution: Appearance? = null
    private var listener: SensorEventListener? = null

    val lightSensorAvailable: Boolean get() = lightSensor != null

    /**
     * The appearance most recently resolved for a visible surface.
     *
     * A dialog is a separate window built on demand, so it cannot be recoloured by walking a view
     * tree the way [io.pryce.android.autospeed.ui.ContentScreenAppearance] does, and left alone it
     * inherits the device theme instead of Autospeed's. Recording the resolution here lets
     * [io.pryce.android.autospeed.ui.dialogBuilder] pick a matching dialog theme at construction
     * time without every caller having to carry the current settings around (design section 3.7).
     */
    var currentAppearance: Appearance = Appearance.DAY
        private set

    fun start(onChanged: () -> Unit) {
        val sensor = lightSensor ?: return
        val manager = sensorManager ?: return
        val newListener =
            object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    lastLightResolution =
                        hysteresisResolver.onLuxSample(event.values[0], SystemClock.elapsedRealtime())
                    onChanged()
                }

                override fun onAccuracyChanged(
                    sensor: Sensor,
                    accuracy: Int,
                ) = Unit
            }
        listener = newListener
        manager.registerListener(newListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun stop() {
        listener?.let { sensorManager?.unregisterListener(it) }
        listener = null
    }

    fun resolve(
        mode: AppearanceMode,
        systemNightMode: Boolean,
    ): Appearance = AppearanceResolver
        .resolve(
            mode = mode,
            lightSensorAvailable = lightSensorAvailable,
            lightSensorResolution = lastLightResolution,
            systemNightMode = systemNightMode,
        ).also { currentAppearance = it }

    companion object {
        /** Reads the system's current night-mode state from [Configuration.uiMode]. */
        fun systemNightMode(context: Context): Boolean {
            val mode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            return mode == Configuration.UI_MODE_NIGHT_YES
        }
    }
}
