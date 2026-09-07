package io.pryce.android.autospeed.ui

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Pads [this] by [basePaddingPx] plus whatever the system bars and display cutout occupy.
 *
 * `android:fitsSystemWindows="true"` deliberately is not used for this. That attribute makes the
 * framework *replace* the view's padding with the inset values, silently discarding any padding
 * declared alongside it in XML. On a device whose left/right insets are zero that produced
 * screens with text pressed against the physical screen edge, which design sections 3.2 and 7
 * require us to avoid: content has to stay clear of both system decorations and the edge.
 */
fun View.applyContentInsets(basePaddingPx: Int) {
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInsets ->
        val insets =
            windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
        view.updatePadding(
            left = basePaddingPx + insets.left,
            top = basePaddingPx + insets.top,
            right = basePaddingPx + insets.right,
            bottom = basePaddingPx + insets.bottom,
        )
        windowInsets
    }
    ViewCompat.requestApplyInsets(this)
}
