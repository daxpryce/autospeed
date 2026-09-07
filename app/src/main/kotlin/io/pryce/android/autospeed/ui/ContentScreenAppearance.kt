package io.pryce.android.autospeed.ui

import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import io.pryce.android.autospeed.R
import io.pryce.android.autospeed.appearance.AppearanceManager
import io.pryce.android.autospeed.core.appearance.Appearance
import io.pryce.android.autospeed.core.appearance.AppearanceMode

/**
 * Applies the resolved day/night palette to a text-and-controls screen -- settings and access
 * setup.
 *
 * Design section 3.7 requires the night palette across the app, not only on the speed display, and
 * these screens previously stayed permanently light because their colours came from the device
 * theme. The device theme also decided the toggle and spinner colours, which is why an enabled
 * toggle read as a slightly darker monochrome rather than an unambiguous accent.
 */
class ContentScreenAppearance(
    private val appearanceManager: AppearanceManager,
    private val window: Window,
    private val rootView: View,
) {
    private val context: Context get() = rootView.context

    fun apply(mode: AppearanceMode) {
        val night =
            appearanceManager.resolve(mode, AppearanceManager.systemNightMode(context)) == Appearance.NIGHT
        val palette =
            Palette(
                background = color(if (night) R.color.night_background else R.color.day_background),
                onBackground = color(if (night) R.color.night_on_background else R.color.day_on_background),
                accent = color(if (night) R.color.night_accent else R.color.day_accent),
            )
        rootView.setBackgroundColor(palette.background)
        // The content root does not extend under the system bars, so without this the window
        // background set by the theme (the day colour) shows through as a light strip behind the
        // navigation bar while the rest of the screen is in night mode.
        window.setBackgroundDrawable(palette.background.toDrawable())
        applyToTree(rootView, palette)
        val controller = WindowCompat.getInsetsController(window, rootView)
        controller.isAppearanceLightStatusBars = !night
        controller.isAppearanceLightNavigationBars = !night
    }

    private fun applyToTree(
        view: View,
        palette: Palette,
    ) {
        when (view) {
            // Checked order matters: a Switch and a Button are both TextViews, so the most
            // specific type has to win.
            is Switch -> applyToToggle(view, palette)

            is Button -> view.setTextColor(palette.accent)

            is Spinner -> applyToSpinner(view, palette)

            is TextView -> view.setTextColor(palette.onBackground)
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                applyToTree(view.getChildAt(index), palette)
            }
        }
    }

    private fun applyToToggle(
        toggle: Switch,
        palette: Palette,
    ) {
        toggle.setTextColor(palette.onBackground)
        // An "on" toggle has to be distinguishable at a glance while driving, so the checked state
        // takes the palette accent instead of the platform's near-identical grey.
        toggle.thumbTintList = checkedStateList(palette.accent, color(R.color.switch_off))
        toggle.trackTintList = checkedStateList(palette.accent, color(R.color.switch_off_track))
    }

    private fun applyToSpinner(
        spinner: Spinner,
        palette: Palette,
    ) {
        (spinner.adapter as? PaletteSpinnerAdapter)?.setTextColor(palette.onBackground)
        // The dropdown is a separate window, so it keeps the device theme's light background and
        // would otherwise show night-palette text on white.
        spinner.setPopupBackgroundDrawable(palette.background.toDrawable())
        spinner.backgroundTintList = ColorStateList.valueOf(palette.onBackground)
    }

    private fun checkedStateList(
        checked: Int,
        unchecked: Int,
    ): ColorStateList = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
        intArrayOf(checked, unchecked),
    )

    private fun color(resId: Int): Int = ContextCompat.getColor(context, resId)

    private data class Palette(
        val background: Int,
        val onBackground: Int,
        val accent: Int,
    )
}
