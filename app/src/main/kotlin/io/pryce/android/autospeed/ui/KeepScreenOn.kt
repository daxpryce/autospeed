package io.pryce.android.autospeed.ui

import android.view.Window
import android.view.WindowManager

/**
 * Keeps the display awake for exactly as long as this window is the visible one.
 *
 * Every Autospeed surface needs this, not just the speed display: the screen going dark mid-drive
 * is a safety problem whichever Autospeed window happens to be in front, and the settings and
 * access-setup screens are used with the phone mounted.
 *
 * A window flag is used rather than a `PowerManager` wake lock precisely because the system
 * releases it automatically when the window stops being visible, so Autospeed can never leak
 * screen-on state after the user leaves it (design section 8: no retained background work).
 */
fun Window.keepScreenOnWhileVisible() {
    addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
}
