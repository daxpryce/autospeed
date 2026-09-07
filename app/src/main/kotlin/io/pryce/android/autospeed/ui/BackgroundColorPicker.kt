package io.pryce.android.autospeed.ui

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import io.pryce.android.autospeed.R
import io.pryce.android.autospeed.appearance.AppearanceManager

/**
 * The solid-background colour choices (design section 3.7).
 *
 * A fixed palette rather than a free colour wheel: the speed readout has to remain legible over
 * whatever is selected, and every entry here is a surface the day/night palettes already read
 * against.
 */
enum class BackgroundColorChoice(
    val labelRes: Int,
    val colorRes: Int,
) {
    BLACK(R.string.color_black, R.color.background_choice_black),
    CHARCOAL(R.string.color_charcoal, R.color.background_choice_charcoal),
    SLATE(R.string.color_slate, R.color.background_choice_slate),
    NAVY(R.string.color_navy, R.color.background_choice_navy),
    FOREST(R.string.color_forest, R.color.background_choice_forest),
    MAROON(R.string.color_maroon, R.color.background_choice_maroon),
    SAND(R.string.color_sand, R.color.background_choice_sand),
    WHITE(R.string.color_white, R.color.background_choice_white),
}

/**
 * Presents [BackgroundColorChoice] as swatches. Each row is painted in the colour it selects, with
 * its label flipped to black or white by luminance so the choice is readable without relying on
 * colour alone (design section 7).
 */
object BackgroundColorPicker {
    fun show(
        context: Context,
        appearanceManager: AppearanceManager,
        onPicked: (Int) -> Unit,
    ) {
        val choices = BackgroundColorChoice.entries
        val adapter =
            object : ArrayAdapter<BackgroundColorChoice>(
                context,
                android.R.layout.simple_list_item_1,
                choices,
            ) {
                override fun getView(
                    position: Int,
                    convertView: View?,
                    parent: ViewGroup,
                ): View = super.getView(position, convertView, parent).also { row ->
                    val color = ContextCompat.getColor(context, choices[position].colorRes)
                    row.setBackgroundColor(color)
                    (row as? TextView)?.apply {
                        setText(choices[position].labelRes)
                        setTextColor(readableInkOn(color))
                        minHeight = resources.getDimensionPixelSize(R.dimen.color_swatch_height)
                    }
                }
            }
        appearanceManager
            .dialogBuilder(context)
            .setTitle(R.string.settings_background_color)
            .setAdapter(adapter) { _, index ->
                onPicked(ContextCompat.getColor(context, choices[index].colorRes))
            }.show()
    }

    private fun readableInkOn(color: Int): Int =
        if (ColorUtils.calculateLuminance(color) > LIGHT_LUMINANCE) Color.BLACK else Color.WHITE

    private const val LIGHT_LUMINANCE = 0.5
}
