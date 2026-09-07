package io.pryce.android.autospeed.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
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
 * The compact overlay readout (design section 3.6), drawn as a US speed-limit sign: a bordered
 * panel with the unit above the value.
 *
 * The overlay must never cover the screen and must stay readable over varying map colours, so this
 * uses a fixed opaque panel with its own border rather than the main screen's outlined text over a
 * transparent background. Its own surface is what guarantees contrast, independently of whatever
 * the companion application is drawing underneath.
 *
 * It is deliberately a separate view from [SpeedDisplayView]: the full readout carries a secondary
 * unit and multi-word status messages that cannot be legible at roughly one inch square.
 */
class SpeedSignView
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

        private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val borderPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpToPx(BORDER_WIDTH_DP)
            }
        private val valuePaint = newTextPaint()
        private val unitPaint = newTextPaint()

        private val panelBounds = RectF()
        private var normalTextColor = 0
        private var warningTextColor = 0

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
            typeface = FontProvider.displayTypeface()
        }

        /**
         * A real speed-limit sign is black on white. The night palette inverts it instead of
         * dimming it, because design section 3.7 requires night to substantially reduce emitted
         * light, and a full white panel over a night map is the brightest thing on screen.
         */
        private fun applyPalette() {
            val night = appearance == Appearance.NIGHT
            val panel = color(if (night) R.color.night_background else R.color.day_background)
            val ink = color(if (night) R.color.night_on_background else R.color.day_on_background)
            panelPaint.color = panel
            borderPaint.color = ink
            valuePaint.color = ink
            unitPaint.color = ink
            normalTextColor = ink
            warningTextColor = color(if (night) R.color.night_stale else R.color.day_stale)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val inset = borderPaint.strokeWidth / 2f
            panelBounds.set(inset, inset, width - inset, height - inset)
            val radius = dpToPx(CORNER_RADIUS_DP)
            canvas.drawRoundRect(panelBounds, radius, radius, panelPaint)
            canvas.drawRoundRect(panelBounds, radius, radius, borderPaint)

            unitPaint.textSize = height * UNIT_TEXT_FRACTION
            valuePaint.textSize = height * VALUE_TEXT_FRACTION
            val centerX = width / 2f
            canvas.drawText(unitLabel(), centerX, height * UNIT_BASELINE_FRACTION, unitPaint)
            valuePaint.color =
                if (viewState.status == SpeedDisplayStatus.AVAILABLE) normalTextColor else warningTextColor
            canvas.drawText(valueText(), centerX, height * VALUE_BASELINE_FRACTION, valuePaint)
        }

        /**
         * Every non-available state collapses to a dash. There is no room for a sentence here, and
         * design section 3.1 requires that a missing or stale fix never be shown as a number that
         * could be mistaken for a current speed; the warning colour reinforces the dash.
         */
        private fun valueText(): String {
            val value = viewState.primaryValue
            return if (viewState.status == SpeedDisplayStatus.AVAILABLE && value != null) value.toString() else NO_VALUE
        }

        private fun unitLabel(): String = when (viewState.primaryUnit) {
            SpeedUnit.MPH -> context.getString(R.string.unit_mph)
            SpeedUnit.KMH -> context.getString(R.string.unit_kmh)
        }

        private fun color(resId: Int): Int = ContextCompat.getColor(context, resId)

        private fun dpToPx(dp: Float): Float =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)

        private companion object {
            const val BORDER_WIDTH_DP = 3f
            const val CORNER_RADIUS_DP = 8f

            // Text is sized as a fraction of the view so the sign stays proportional at whatever
            // size the overlay layout gives it.
            const val UNIT_TEXT_FRACTION = 0.20f
            const val VALUE_TEXT_FRACTION = 0.52f
            const val UNIT_BASELINE_FRACTION = 0.30f
            const val VALUE_BASELINE_FRACTION = 0.85f

            const val NO_VALUE = "--"
        }
    }
