package io.pryce.android.autospeed.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import io.pryce.android.autospeed.R
import io.pryce.android.autospeed.appearance.AppearanceManager
import io.pryce.android.autospeed.core.appearance.Appearance
import io.pryce.android.autospeed.core.settings.AutospeedSettings
import io.pryce.android.autospeed.core.settings.BackgroundMode

/**
 * Applies the resolved day/night palette and the selected background to the main screen (design
 * sections 3.7 and 3.8). Kept out of `MainActivity` so the activity holds only lifecycle,
 * permission, and companion-handoff logic.
 *
 * The background is one of the system wallpaper, a locally selected solid color, or a
 * user-selected image read through its persisted Storage Access Framework URI. If that image
 * cannot be read -- for example after the provider revoked the grant -- rendering falls back to
 * the palette's solid background rather than leaving an empty surface (design section 9,
 * "Background image lifecycle").
 *
 * The window itself is declared translucent and wallpaper-showing by `Theme.Autospeed`, because
 * both are window creation attributes that cannot be turned on afterwards. That leaves this class
 * responsible only for the content root: transparent to reveal the wallpaper, or an opaque palette
 * colour that covers it.
 */
class MainScreenAppearance(
    private val appearanceManager: AppearanceManager,
    private val window: Window,
    private val rootView: View,
    private val backgroundImageView: ImageView,
    private val speedDisplayView: SpeedDisplayView,
    private val iconButtons: List<ImageButton>,
    private val accentButtons: List<Button>,
) {
    private val context: Context get() = rootView.context

    /** Resolves the current [Appearance] from the user's setting, light sensor, and system mode. */
    fun resolve(settings: AutospeedSettings): Appearance = appearanceManager.resolve(
        settings.appearanceMode,
        AppearanceManager.systemNightMode(context),
    )

    /**
     * Applies [settings] to the speed display, the control scrims, and the background in one pass,
     * so the palette and background can never disagree about day/night.
     */
    fun apply(settings: AutospeedSettings) {
        val appearance = resolve(settings)
        speedDisplayView.setAppearance(appearance)
        applyControlPalette(appearance)
        applyBackground(settings, appearance)
        applySystemBarIcons(appearance)
    }

    /**
     * Design section 3.7: controls sit on a fixed high-contrast scrim so no wallpaper or image
     * choice can make them unreadable, and the night palette substantially reduces emitted light
     * rather than merely inverting the day palette.
     */
    private fun applyControlPalette(appearance: Appearance) {
        val night = appearance == Appearance.NIGHT
        val scrim = ColorStateList.valueOf(color(if (night) R.color.night_control_scrim else R.color.day_control_scrim))
        val onBackground = color(if (night) R.color.night_on_background else R.color.day_on_background)
        val outline = color(if (night) R.color.night_background else R.color.day_background)
        // The icons are single-colour vectors authored black, so without a tint they were
        // invisible against the night scrim. Tinting them to the palette's foreground colour is
        // what actually makes the transport controls visible.
        iconButtons.forEach { button ->
            button.imageTintList = ColorStateList.valueOf(onBackground)
            button.backgroundTintList = scrim
        }
        val accent = color(if (night) R.color.night_accent else R.color.day_accent)
        val accentTint = ColorStateList.valueOf(accent)
        accentButtons.forEach { button ->
            button.setTextColor(accent)
            // The leading glyphs are the same black-authored vectors as the transport icons, so
            // they need the identical tint to stay visible over a wallpaper.
            button.compoundDrawableTintList = accentTint
            button.backgroundTintList = scrim
            // Design section 3.7 permits an outline in place of a scrim, which is what keeps
            // this text readable now that the opaque control bars are gone.
            button.setShadowLayer(BUTTON_OUTLINE_RADIUS_PX, 0f, 0f, outline)
        }
    }

    private fun applyBackground(
        settings: AutospeedSettings,
        appearance: Appearance,
    ) {
        when (settings.backgroundMode) {
            BackgroundMode.SYSTEM_WALLPAPER -> {
                applyWallpaperBackground()
            }

            BackgroundMode.SOLID_COLOR -> {
                applySolidBackground(settings.backgroundColorArgb ?: defaultBackgroundColor(appearance))
            }

            BackgroundMode.IMAGE -> {
                applyImageBackground(settings, appearance)
            }
        }
    }

    /**
     * The system composites the wallpaper behind the translucent window, so all this has to do is
     * keep the content root out of the way. Nothing here reads the wallpaper bitmap, which is why
     * this mode needs no storage permission.
     */
    private fun applyWallpaperBackground() {
        backgroundImageView.visibility = View.GONE
        rootView.setBackgroundColor(Color.TRANSPARENT)
    }

    private fun applySolidBackground(colorArgb: Int) {
        backgroundImageView.visibility = View.GONE
        rootView.setBackgroundColor(colorArgb)
    }

    private fun applyImageBackground(
        settings: AutospeedSettings,
        appearance: Appearance,
    ) {
        val bitmap = settings.backgroundImageUri?.let { runCatching { loadBitmap(it.toUri()) }.getOrNull() }
        if (bitmap == null) {
            applySolidBackground(settings.backgroundColorArgb ?: defaultBackgroundColor(appearance))
            return
        }
        // The palette background stays behind the image so letterboxed edges and any alpha in the
        // image resolve against the correct day/night surface instead of the themed default.
        applySolidBackground(defaultBackgroundColor(appearance))
        backgroundImageView.setImageBitmap(bitmap)
        backgroundImageView.visibility = View.VISIBLE
    }

    /**
     * Keeps the status/navigation bar icons legible against whichever surface is behind them. The
     * theme pins these to their day values for the first frame; once settings resolve, the actual
     * appearance decides.
     */
    private fun applySystemBarIcons(appearance: Appearance) {
        val light = appearance != Appearance.NIGHT
        val controller = WindowCompat.getInsetsController(window, rootView)
        controller.isAppearanceLightStatusBars = light
        controller.isAppearanceLightNavigationBars = light
    }

    private fun defaultBackgroundColor(appearance: Appearance): Int =
        color(if (appearance == Appearance.NIGHT) R.color.night_background else R.color.day_background)

    private fun color(resId: Int): Int = ContextCompat.getColor(context, resId)

    /**
     * Decodes the background no larger than the display needs. A modern phone photo decoded at
     * full resolution is tens of megabytes for a view that is at most screen-sized, which is a
     * real out-of-memory risk on a surface that must never stop showing speed (design section 8).
     */
    private fun loadBitmap(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val metrics = context.resources.displayMetrics
        val options =
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, metrics.widthPixels, metrics.heightPixels)
            }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun sampleSize(
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
    ): Int {
        var sample = 1
        while (sourceWidth / (sample * 2) >= targetWidth && sourceHeight / (sample * 2) >= targetHeight) {
            sample *= 2
        }
        return sample
    }

    private companion object {
        const val BUTTON_OUTLINE_RADIUS_PX = 8f
    }
}
