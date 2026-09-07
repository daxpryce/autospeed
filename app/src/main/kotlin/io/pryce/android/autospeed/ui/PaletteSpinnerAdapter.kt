package io.pryce.android.autospeed.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView

/**
 * A spinner adapter whose item text colour follows the resolved day/night palette.
 *
 * The platform's `simple_spinner_item` inherits its colour from the device theme, which rendered
 * the selected value as pale grey on the settings background -- effectively invisible -- and could
 * never follow Autospeed's own night palette (design section 3.7). Colouring the item views here is
 * the only place the selected value and the dropdown rows can both be reached.
 */
class PaletteSpinnerAdapter(
    context: Context,
    entries: Array<CharSequence>,
) : ArrayAdapter<CharSequence>(context, android.R.layout.simple_spinner_item, entries) {
    private var textColor: Int? = null

    init {
        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    }

    fun setTextColor(color: Int) {
        if (textColor == color) return
        textColor = color
        notifyDataSetChanged()
    }

    override fun getView(
        position: Int,
        convertView: View?,
        parent: ViewGroup,
    ): View = super.getView(position, convertView, parent).applyTextColor()

    override fun getDropDownView(
        position: Int,
        convertView: View?,
        parent: ViewGroup,
    ): View = super.getDropDownView(position, convertView, parent).applyTextColor()

    private fun View.applyTextColor(): View = also {
        textColor?.let { color -> (it as? TextView)?.setTextColor(color) }
    }
}
