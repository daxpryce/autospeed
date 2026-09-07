package io.pryce.android.autospeed.overlay

/** Which companion overlay layout Autospeed should show (design section 3.6). */
enum class OverlayCompanion {
    /** Speed + previous/play-pause/next, shown while the selected map application is foreground. */
    MAP,

    /** Speed only, shown while the selected media application is foreground. */
    MEDIA,
}
