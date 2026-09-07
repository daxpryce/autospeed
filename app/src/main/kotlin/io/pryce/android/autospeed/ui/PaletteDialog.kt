package io.pryce.android.autospeed.ui

import android.app.AlertDialog
import android.content.Context
import io.pryce.android.autospeed.R
import io.pryce.android.autospeed.appearance.AppearanceManager
import io.pryce.android.autospeed.core.appearance.Appearance

/**
 * Builds an [AlertDialog] in Autospeed's own day or night palette rather than the device theme
 * (design section 3.7).
 *
 * The palette is taken from [AppearanceManager.currentAppearance], which the visible surface has
 * already resolved. Passing a theme to the builder is the only reliable point of control: a
 * dialog's decor is inflated against the theme it is constructed with, so recolouring it
 * afterwards would mean chasing views the platform owns.
 */
fun AppearanceManager.dialogBuilder(context: Context): AlertDialog.Builder = AlertDialog.Builder(
    context,
    if (currentAppearance == Appearance.NIGHT) {
        R.style.Theme_Autospeed_Dialog_Night
    } else {
        R.style.Theme_Autospeed_Dialog_Day
    },
)
