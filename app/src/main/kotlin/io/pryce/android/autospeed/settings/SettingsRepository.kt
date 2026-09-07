package io.pryce.android.autospeed.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.pryce.android.autospeed.core.appearance.AppearanceMode
import io.pryce.android.autospeed.core.build.BuildProfile
import io.pryce.android.autospeed.core.settings.AutospeedSettings
import io.pryce.android.autospeed.core.settings.BackgroundMode
import io.pryce.android.autospeed.core.settings.OrientationMode
import io.pryce.android.autospeed.core.settings.SettingsDefaults
import io.pryce.android.autospeed.core.speed.SpeedUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.autospeedDataStore: DataStore<Preferences> by preferencesDataStore(name = "autospeed_settings")

/**
 * Persists and restores exactly the fields listed in [AutospeedSettings] (design section 3.8)
 * using Android DataStore. No location fix, speed sample, or other runtime-only value is ever
 * written here.
 */
class SettingsRepository(
    private val context: Context,
    private val defaultProfile: BuildProfile,
) {
    private object Keys {
        val orientationMode = stringPreferencesKey("orientation_mode")
        val primaryUnit = stringPreferencesKey("primary_unit")
        val secondaryVisible = booleanPreferencesKey("secondary_visible")
        val selectedMapPackage = stringPreferencesKey("selected_map_package")
        val selectedMediaPackage = stringPreferencesKey("selected_media_package")
        val playFallbackEnabled = booleanPreferencesKey("play_fallback_enabled")
        val notificationSuppressionEnabled = booleanPreferencesKey("notification_suppression_enabled")
        val appearanceMode = stringPreferencesKey("appearance_mode")
        val backgroundMode = stringPreferencesKey("background_mode")
        val backgroundColorArgb = intPreferencesKey("background_color_argb")
        val backgroundImageUri = stringPreferencesKey("background_image_uri")
        val overlayPositionX = floatPreferencesKey("overlay_position_x")
        val overlayPositionY = floatPreferencesKey("overlay_position_y")
        val advisoryShown = booleanPreferencesKey("advisory_shown")
        val setupCompleted = booleanPreferencesKey("setup_completed")
    }

    val settings: Flow<AutospeedSettings> =
        context.autospeedDataStore.data.map { prefs -> prefs.toSettings(defaultProfile) }

    suspend fun update(transform: (AutospeedSettings) -> AutospeedSettings) {
        context.autospeedDataStore.edit { prefs ->
            val current = prefs.toSettings(defaultProfile)
            val next = transform(current)
            prefs.writeFrom(next)
        }
    }

    private fun Preferences.toSettings(profile: BuildProfile): AutospeedSettings {
        val defaults = SettingsDefaults.defaultsFor(profile)
        return AutospeedSettings(
            orientationMode = this[Keys.orientationMode]?.toOrientationModeOrNull() ?: defaults.orientationMode,
            primaryUnit = this[Keys.primaryUnit]?.toSpeedUnitOrNull() ?: defaults.primaryUnit,
            secondaryVisible = this[Keys.secondaryVisible] ?: defaults.secondaryVisible,
            selectedMapPackage = this[Keys.selectedMapPackage],
            selectedMediaPackage = this[Keys.selectedMediaPackage],
            playFallbackEnabled = this[Keys.playFallbackEnabled] ?: defaults.playFallbackEnabled,
            notificationSuppressionEnabled =
                this[Keys.notificationSuppressionEnabled] ?: defaults.notificationSuppressionEnabled,
            appearanceMode = this[Keys.appearanceMode]?.toAppearanceModeOrNull() ?: defaults.appearanceMode,
            backgroundMode = this[Keys.backgroundMode]?.toBackgroundModeOrNull() ?: defaults.backgroundMode,
            backgroundColorArgb = this[Keys.backgroundColorArgb],
            backgroundImageUri = this[Keys.backgroundImageUri],
            overlayPositionX = this[Keys.overlayPositionX],
            overlayPositionY = this[Keys.overlayPositionY],
            advisoryShown = this[Keys.advisoryShown] ?: defaults.advisoryShown,
            setupCompleted = this[Keys.setupCompleted] ?: defaults.setupCompleted,
        )
    }

    private fun MutablePreferences.writeFrom(settings: AutospeedSettings) {
        this[Keys.orientationMode] = settings.orientationMode.name
        this[Keys.primaryUnit] = settings.primaryUnit.name
        this[Keys.secondaryVisible] = settings.secondaryVisible
        writeOrRemove(Keys.selectedMapPackage, settings.selectedMapPackage)
        writeOrRemove(Keys.selectedMediaPackage, settings.selectedMediaPackage)
        this[Keys.playFallbackEnabled] = settings.playFallbackEnabled
        this[Keys.notificationSuppressionEnabled] = settings.notificationSuppressionEnabled
        this[Keys.appearanceMode] = settings.appearanceMode.name
        this[Keys.backgroundMode] = settings.backgroundMode.name
        writeOrRemove(Keys.backgroundColorArgb, settings.backgroundColorArgb)
        writeOrRemove(Keys.backgroundImageUri, settings.backgroundImageUri)
        writeOrRemove(Keys.overlayPositionX, settings.overlayPositionX)
        writeOrRemove(Keys.overlayPositionY, settings.overlayPositionY)
        this[Keys.advisoryShown] = settings.advisoryShown
        this[Keys.setupCompleted] = settings.setupCompleted
    }

    private fun <T : Any> MutablePreferences.writeOrRemove(
        key: Preferences.Key<T>,
        value: T?,
    ) {
        if (value == null) {
            remove(key)
        } else {
            this[key] = value
        }
    }
}

private fun String.toOrientationModeOrNull(): OrientationMode? =
    runCatching { OrientationMode.valueOf(this) }.getOrNull()

private fun String.toSpeedUnitOrNull(): SpeedUnit? = runCatching { SpeedUnit.valueOf(this) }.getOrNull()

private fun String.toAppearanceModeOrNull(): AppearanceMode? = runCatching { AppearanceMode.valueOf(this) }.getOrNull()

private fun String.toBackgroundModeOrNull(): BackgroundMode? = runCatching { BackgroundMode.valueOf(this) }.getOrNull()
