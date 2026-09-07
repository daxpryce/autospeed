package io.pryce.android.autospeed.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import io.pryce.android.autospeed.R
import io.pryce.android.autospeed.appearance.FontProvider
import io.pryce.android.autospeed.core.appearance.Appearance
import io.pryce.android.autospeed.core.speed.SpeedDisplayStatus
import io.pryce.android.autospeed.core.speed.SpeedDisplayViewState
import io.pryce.android.autospeed.core.speed.SpeedUnit

/**
 * Renders a [SpeedDisplayViewState]: a large primary value and unit, an optional smaller
 * secondary value and unit, and a status message when no numeric value is available (design
 * section 3.2). Custom-drawn rather than composed from multiple `TextView`s to keep the overlay
 * and main-screen implementations identical and dependency-free.
 */
class SpeedDisplayView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : View(context, attrs, defStyleAttr) {
        private var viewState =
            SpeedDisplayViewState(
                status = SpeedDisplayStatus.AWAITING_FIRST_FIX,
                primaryUnit = SpeedUnit.MPH,
                primaryValue = null,
                secondaryUnit = null,
                secondaryValue = null,
            )
        private var appearance: Appearance = Appearance.DAY

        private val displayTypeface = FontProvider.displayTypeface()

        private val primaryPaint = newTextPaint()
        private val secondaryPaint = newTextPaint()
        private val statusPaint = newTextPaint()

        private var statusNormalColor = 0
        private var statusWarningColor = 0

        init {
            applyPalette()
        }

        fun setViewState(state: SpeedDisplayViewState) {
            viewState = state
            invalidate()
        }

        fun setAppearance(newAppearance: Appearance) {
            appearance = newAppearance
            applyPalette()
            invalidate()
        }

        private fun newTextPaint(): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = displayTypeface
        }

        private fun applyPalette() {
            val night = appearance == Appearance.NIGHT
            val color =
                ContextCompat.getColor(
                    context,
                    if (night) R.color.night_on_background else R.color.day_on_background,
                )
            primaryPaint.color = color
            secondaryPaint.color = color
            statusPaint.color = color
            // Design section 3.7 allows a fixed surface, scrim, or outline. An outline is used
            // here so the speed stays legible over any wallpaper without an opaque panel hiding
            // it. The glow is the opposing background colour, so it reads against either.
            val outline =
                ContextCompat.getColor(
                    context,
                    if (night) R.color.night_background else R.color.day_background,
                )
            listOf(primaryPaint, secondaryPaint, statusPaint).forEach {
                it.setShadowLayer(OUTLINE_RADIUS_PX, 0f, 0f, outline)
            }
            // Design section 7: status is never encoded by color alone -- every state also has its
            // own text -- but a distinct stale/unavailable color reinforces that the number on
            // screen is not a current speed (design section 3.1).
            statusWarningColor =
                ContextCompat.getColor(context, if (night) R.color.night_stale else R.color.day_stale)
            statusNormalColor = color
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            primaryPaint.textSize = spToPx(PRIMARY_TEXT_SIZE_SP)
            secondaryPaint.textSize = spToPx(SECONDARY_TEXT_SIZE_SP)
            statusPaint.textSize = spToPx(STATUS_TEXT_SIZE_SP)

            val centerX = width / 2f
            val state = viewState
            if (state.status == SpeedDisplayStatus.AVAILABLE && state.primaryValue != null) {
                drawAvailableState(canvas, centerX, state)
            } else {
                statusPaint.color = if (isWarningStatus(state.status)) statusWarningColor else statusNormalColor
                canvas.drawText(statusMessage(state.status), centerX, height / 2f, statusPaint)
            }
        }

        /** States in which no trustworthy current speed exists and the user must notice that. */
        private fun isWarningStatus(status: SpeedDisplayStatus): Boolean = when (status) {
            SpeedDisplayStatus.STALE,
            SpeedDisplayStatus.UNAVAILABLE,
            SpeedDisplayStatus.PERMISSION_DENIED,
            SpeedDisplayStatus.LOCATION_DISABLED,
            -> true

            SpeedDisplayStatus.AWAITING_FIRST_FIX, SpeedDisplayStatus.AVAILABLE -> false
        }

        private fun drawAvailableState(
            canvas: Canvas,
            centerX: Float,
            state: SpeedDisplayViewState,
        ) {
            val primaryText = "${state.primaryValue} ${state.primaryUnit.shortLabel()}"
            val primaryBaseline = height / 2f
            canvas.drawText(primaryText, centerX, primaryBaseline, primaryPaint)
            val secondaryUnit = state.secondaryUnit
            val secondaryValue = state.secondaryValue
            if (secondaryUnit != null && secondaryValue != null) {
                val secondaryText = "$secondaryValue ${secondaryUnit.shortLabel()}"
                canvas.drawText(secondaryText, centerX, primaryBaseline + spToPx(SECONDARY_OFFSET_SP), secondaryPaint)
            }
        }

        private fun spToPx(sp: Float): Float =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics)

        private fun SpeedUnit.shortLabel(): String = when (this) {
            SpeedUnit.MPH -> context.getString(R.string.unit_mph)
            SpeedUnit.KMH -> context.getString(R.string.unit_kmh)
        }

        private fun statusMessage(status: SpeedDisplayStatus): String {
            val resId =
                when (status) {
                    SpeedDisplayStatus.AWAITING_FIRST_FIX -> R.string.status_awaiting_first_fix
                    SpeedDisplayStatus.STALE -> R.string.status_stale
                    SpeedDisplayStatus.UNAVAILABLE -> R.string.status_unavailable
                    SpeedDisplayStatus.PERMISSION_DENIED -> R.string.status_permission_denied
                    SpeedDisplayStatus.LOCATION_DISABLED -> R.string.status_location_disabled
                    SpeedDisplayStatus.AVAILABLE -> return ""
                }
            return context.getString(resId)
        }

        private companion object {
            const val PRIMARY_TEXT_SIZE_SP = 72f
            const val SECONDARY_TEXT_SIZE_SP = 28f
            const val STATUS_TEXT_SIZE_SP = 20f
            const val SECONDARY_OFFSET_SP = 40f
            const val OUTLINE_RADIUS_PX = 12f
        }
    }
