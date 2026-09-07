package io.pryce.android.autospeed.appearance

import android.graphics.Typeface

/**
 * Resolves Autospeed's display typeface (design section 3.7).
 *
 * Autospeed deliberately ships no font asset: the device's default sans-serif family is already
 * highly legible, is the family the user's own system font-scaling and accessibility settings
 * apply to, and adds nothing to the package size. The display face is bold so the large primary
 * speed keeps a strong stroke weight over every supported background option.
 */
object FontProvider {
    fun displayTypeface(): Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
}
