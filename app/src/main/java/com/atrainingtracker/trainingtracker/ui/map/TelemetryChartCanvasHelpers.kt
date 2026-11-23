/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see https://www.gnu.org/licenses/gpl-3.0
 */

package com.atrainingtracker.trainingtracker.ui.map

import android.graphics.Paint
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.trainingtracker.MyUnits
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil

/** Default opacity for unconditional marginal histogram background tracks (`Rule 69`, `REQ-UI-2789.1`). */
internal const val MARGINAL_HISTOGRAM_BG_ALPHA = 0.15f

/**
 * Clamped vertical geometry of a single marginal histogram bar on a chart canvas (`REQ-UI-2789.1`).
 */
internal data class MarginalBarVerticalBounds(
    val rectTop: Float,
    val rectHeight: Float
)

/**
 * Computes clamped vertical canvas bounds for a histogram bin within `[dataMin, dataMax]` (`Rule 70`, `REQ-UI-2789.1`).
 *
 * Out-of-domain bins and bins whose clamped height is `<= 0f` return `null` BEFORE [minBarHeightPx] is applied,
 * preventing translucent rectangles from stacking along canvas edges (`ATT-5311`).
 */
internal fun computeMarginalBarVerticalBounds(
    binRangeMin: Double,
    binRangeMax: Double,
    dataMin: Double,
    dataMax: Double,
    valueToY: (Double) -> Float,
    minBarHeightPx: Float = 0f
): MarginalBarVerticalBounds? {
    if (binRangeMax <= dataMin || binRangeMin >= dataMax) return null
    val clampedMin = maxOf(dataMin, binRangeMin)
    val clampedMax = minOf(dataMax, binRangeMax)
    if (clampedMin >= clampedMax) return null

    val yTop = valueToY(clampedMax)
    val yBottom = valueToY(clampedMin)
    val rectTop = minOf(yTop, yBottom)
    val rawHeight = abs(yTop - yBottom)
    if (rawHeight <= 0f) return null

    val rectHeight = if (minBarHeightPx > 0f) rawHeight.coerceAtLeast(minBarHeightPx) else rawHeight
    return MarginalBarVerticalBounds(rectTop = rectTop, rectHeight = rectHeight)
}

/**
 * Draws a single marginal bar rectangle using sharp corners (`drawRect`) when [cornerRadiusPx] `<= 0f`,
 * or rounded corners (`drawRoundRect`) otherwise (`REQ-UI-2789.1`).
 */
internal fun DrawScope.drawMarginalBarShape(
    color: Color,
    leftPx: Float,
    topPx: Float,
    widthPx: Float,
    heightPx: Float,
    cornerRadiusPx: Float
) {
    if (cornerRadiusPx <= 0f) {
        drawRect(
            color = color,
            topLeft = Offset(leftPx, topPx),
            size = Size(widthPx, heightPx)
        )
    } else {
        drawRoundRect(
            color = color,
            topLeft = Offset(leftPx, topPx),
            size = Size(widthPx, heightPx),
            cornerRadius = CornerRadius(cornerRadiusPx)
        )
    }
}

/**
 * Parameterized zero-allocation `DrawScope` helper unifying all 5 marginal histogram rendering loops
 * across `ElevationProfile` and `TelemetryMetricGraph` (`REQ-UI-2789.1`, `REQ-UI-443`, `REQ-UI-446`).
 *
 * Enforces:
 * - `Rule 70`: domain boundary filtering & clamping (`rectHeight <= 0f -> continue` before any [minBarHeightPx]).
 * - `Rule 69`: unconditional background track (`alpha = backgroundAlpha`) for every in-domain bin.
 * - Foreground frequency bar gated strictly on `durationSec > 0L && barWidthPx > 0f`.
 */
internal inline fun <T> DrawScope.drawMarginalHistogramBars(
    bins: List<T>,
    dataMin: Double,
    dataMax: Double,
    stripStartPx: Float,
    stripWidthPx: Float,
    noinline valueToY: (Double) -> Float,
    rangeMinOf: (T) -> Double,
    rangeMaxOf: (T) -> Double,
    durationSecOf: (T) -> Long,
    percentageOf: (T) -> Float,
    colorOf: (T) -> Color,
    cornerRadiusPx: Float = 0f,
    backgroundAlpha: Float = MARGINAL_HISTOGRAM_BG_ALPHA,
    foregroundAlpha: Float = 1f,
    minBarHeightPx: Float = 0f,
    onBinDrawn: (DrawScope.(bin: T, rectTop: Float, rectHeight: Float) -> Unit) = { _, _, _ -> }
) {
    if (bins.isEmpty() || stripWidthPx <= 0f) return

    var maxBinPct = 0f
    for (bin in bins) {
        val pct = percentageOf(bin)
        if (pct > maxBinPct) maxBinPct = pct
    }
    if (maxBinPct <= 0f) maxBinPct = 1f

    for (bin in bins) {
        val bounds = computeMarginalBarVerticalBounds(
            binRangeMin = rangeMinOf(bin),
            binRangeMax = rangeMaxOf(bin),
            dataMin = dataMin,
            dataMax = dataMax,
            valueToY = valueToY,
            minBarHeightPx = minBarHeightPx
        ) ?: continue

        val baseColor = colorOf(bin)

        // Rule 69: Unconditional background container track across plotted [dataMin, dataMax]
        drawMarginalBarShape(
            color = baseColor.copy(alpha = backgroundAlpha),
            leftPx = stripStartPx,
            topPx = bounds.rectTop,
            widthPx = stripWidthPx,
            heightPx = bounds.rectHeight,
            cornerRadiusPx = cornerRadiusPx
        )

        // Weighted horizontal frequency bar gated on durationSec > 0L
        if (durationSecOf(bin) > 0L) {
            val barWidthPx = (percentageOf(bin) / maxBinPct).coerceIn(0f, 1f) * stripWidthPx
            if (barWidthPx > 0f) {
                val fgColor = if (foregroundAlpha >= 1f) baseColor else baseColor.copy(alpha = foregroundAlpha)
                drawMarginalBarShape(
                    color = fgColor,
                    leftPx = stripStartPx,
                    topPx = bounds.rectTop,
                    widthPx = barWidthPx,
                    heightPx = bounds.rectHeight,
                    cornerRadiusPx = cornerRadiusPx
                )
            }
        }

        onBinDrawn(bin, bounds.rectTop, bounds.rectHeight)
    }
}

/**
 * Computes strictly interior X-axis tick values within `(startValue, startValue + visibleSpan)`
 * snapped to multiples of [step] (`REQ-UI-2789.2`).
 */
internal fun computeXAxisTickValues(
    startValue: Double,
    visibleSpan: Double,
    step: Double
): List<Double> {
    if (step <= 0.0 || visibleSpan <= 0.0 || !step.isFinite() || !visibleSpan.isFinite() || !startValue.isFinite()) {
        return emptyList()
    }
    val endValue = startValue + visibleSpan
    var current = ceil(startValue / step) * step
    if (current <= startValue) {
        current += step
    }
    val values = mutableListOf<Double>()
    while (current < endValue) {
        values.add(current)
        current += step
    }
    return values
}

/**
 * Formats a locale-aware intermediate distance tick label for [ElevationProfile] (`REQ-UI-2789.2`).
 */
internal fun formatElevationDistanceTickLabel(
    distanceMeters: Double,
    visibleSpanMeters: Double,
    unit: MyUnits,
    locale: Locale
): String = if (unit == MyUnits.METRIC) {
    if (visibleSpanMeters < 1500.0) {
        "${distanceMeters.toInt()}m"
    } else if (distanceMeters % 1000.0 != 0.0) {
        String.format(locale, "%.1f", distanceMeters / 1000.0)
    } else {
        "${(distanceMeters / 1000.0).toInt()}"
    }
} else {
    val miles = distanceMeters / BANALService.METER_PER_MILE
    if (miles % 1.0 != 0.0) {
        String.format(locale, "%.1f", miles)
    } else {
        "${miles.toInt()}"
    }
}

/**
 * Parameterized `DrawScope` helper unifying all 4 time/distance X-axis tick rendering loops
 * across `ElevationProfile` and `TelemetryMetricGraph` (`REQ-UI-2789.2`, `REQ-UI-220`).
 *
 * Separates [onTickInBounds] (e.g., full-height vertical gridlines in `TelemetryMetricGraph`)
 * from [onLabelRendered] (e.g., bottom tick marks + label text in `ElevationProfile`) so each
 * caller retains exact visual semantics while sharing stride, X-projection, and collision tracking.
 */
internal fun DrawScope.drawXAxisTicks(
    startValue: Double,
    visibleSpan: Double,
    step: Double,
    plotStartX: Float,
    plotWidth: Float,
    paint: Paint,
    labelY: Float,
    initialLastDrawnRightX: Float,
    formatLabel: (Double) -> String,
    shouldRenderLabel: (labelLeft: Float, labelRight: Float, lastDrawnRightX: Float) -> Boolean,
    onTickInBounds: (DrawScope.(tickX: Float) -> Unit)? = null,
    onLabelRendered: DrawScope.(tickX: Float, label: String, labelLeft: Float, labelY: Float) -> Unit
) {
    if (step <= 0.0 || visibleSpan <= 0.0 || !step.isFinite() || !visibleSpan.isFinite() || !startValue.isFinite()) {
        return
    }
    if (plotWidth <= 0f) return

    val endValue = startValue + visibleSpan
    val plotEndX = plotStartX + plotWidth
    var lastDrawnRightX = initialLastDrawnRightX
    var current = ceil(startValue / step) * step
    if (current <= startValue) {
        current += step
    }

    while (current < endValue) {
        val tickX = plotStartX + ElevationProfileZoomMath.distanceToCanvasX(current, startValue, visibleSpan, plotWidth)
        if (tickX in plotStartX..plotEndX) {
            onTickInBounds?.invoke(this, tickX)
            val label = formatLabel(current)
            val labelWidth = paint.measureText(label)
            val labelLeft = tickX - (labelWidth / 2f)
            val labelRight = tickX + (labelWidth / 2f)
            if (shouldRenderLabel(labelLeft, labelRight, lastDrawnRightX)) {
                onLabelRendered(tickX, label, labelLeft, labelY)
                lastDrawnRightX = labelRight
            }
        }
        current += step
    }
}

/** Priority tier for the `Ø` entire-workout average Y-axis label (always wins collisions; `REQ-UI-2797.2`). */
internal const val Y_AXIS_PRIORITY_AVERAGE = 100

/** Priority tier for threshold / zone-boundary Y-axis labels (e.g., `DFA α1` thresholds, `ATT-9164` zones). */
internal const val Y_AXIS_PRIORITY_THRESHOLD = 50

/** Priority tier for top/bottom min/max Y-axis boundary labels. */
internal const val Y_AXIS_PRIORITY_EXTREMA = 20

/** Opacity for the muted dotted grey average reference line (`REQ-UI-2797.3`). */
internal const val AVERAGE_LINE_ALPHA = 0.60f

/** Horizontal alignment of a Y-axis label within the `50.dp` left gutter (`REQ-UI-2797.2`). */
internal enum class TelemetryYAxisLabelAlignment {
    LEFT_GUTTER,
    RIGHT_GUTTER
}

/** Candidate Y-axis label submitted to [resolveTelemetryYAxisLabels] (`REQ-UI-2797.2`). */
internal data class TelemetryYAxisLabelCandidate(
    val id: String,
    val text: String,
    val baselineY: Float,
    val priority: Int,
    val alignment: TelemetryYAxisLabelAlignment = TelemetryYAxisLabelAlignment.LEFT_GUTTER
)

/** Non-overlapping placed Y-axis label returned by [resolveTelemetryYAxisLabels] (`REQ-UI-2797.2`). */
internal data class PlacedTelemetryYAxisLabel(
    val id: String,
    val text: String,
    val baselineY: Float,
    val boxTop: Float,
    val boxBottom: Float,
    val priority: Int,
    val alignment: TelemetryYAxisLabelAlignment
)

/**
 * Returns [averageValue] when it is finite and lies within the plotted `[dataMin, dataMax]` domain,
 * or `null` otherwise so off-scale/invalid average lines and `Ø` labels are cleanly hidden (`REQ-UI-2797.1`).
 */
internal fun resolveVisibleAverageValue(
    averageValue: Double?,
    dataMin: Double,
    dataMax: Double
): Double? {
    if (averageValue == null || !averageValue.isFinite()) return null
    if (!dataMin.isFinite() || !dataMax.isFinite() || dataMax <= dataMin) return null
    return averageValue.takeIf { it in dataMin..dataMax }
}

/**
 * Computes a vertically centered baseline Y coordinate around [anchorY] clamped strictly within
 * `[minBaselineY, maxBaselineY]` (`REQ-UI-2797.2`).
 */
internal fun computeCenteredYAxisBaseline(
    anchorY: Float,
    ascent: Float,
    descent: Float,
    minBaselineY: Float,
    maxBaselineY: Float
): Float {
    val centered = anchorY - ((ascent + descent) / 2f)
    if (maxBaselineY <= minBaselineY) return minBaselineY
    return centered.coerceIn(minBaselineY, maxBaselineY)
}

/**
 * Resolves non-overlapping Y-axis labels in descending [TelemetryYAxisLabelCandidate.priority] order
 * (`Y_AXIS_PRIORITY_AVERAGE > Y_AXIS_PRIORITY_THRESHOLD > Y_AXIS_PRIORITY_EXTREMA`) so the `Ø` average
 * label always takes strict visual priority over colliding min/max, `DFA α1` threshold, or zone boundary
 * labels (`REQ-UI-2797.2` / `ATT-9170`).
 */
internal fun resolveTelemetryYAxisLabels(
    candidates: List<TelemetryYAxisLabelCandidate>,
    ascent: Float,
    descent: Float,
    minVerticalGapPx: Float = 2f
): List<PlacedTelemetryYAxisLabel> {
    if (candidates.isEmpty()) return emptyList()
    val effAscent = minOf(ascent, 0f)
    val effDescent = maxOf(descent, 0f)
    val gap = minVerticalGapPx.coerceAtLeast(0f)
    val placed = mutableListOf<PlacedTelemetryYAxisLabel>()
    val ordered = candidates
        .filter { it.text.isNotEmpty() && it.baselineY.isFinite() }
        .sortedByDescending { it.priority }

    for (candidate in ordered) {
        val boxTop = candidate.baselineY + effAscent
        val boxBottom = candidate.baselineY + effDescent
        val collides = placed.any { existing ->
            boxTop < existing.boxBottom + gap && existing.boxTop < boxBottom + gap
        }
        if (!collides) {
            placed.add(
                PlacedTelemetryYAxisLabel(
                    id = candidate.id,
                    text = candidate.text,
                    baselineY = candidate.baselineY,
                    boxTop = boxTop,
                    boxBottom = boxBottom,
                    priority = candidate.priority,
                    alignment = candidate.alignment
                )
            )
        }
    }
    return placed
}

/**
 * Computes an auto-fitted text size so wide `Ø`-prefixed labels (e.g., `Ø10:45`, `Ø0,83`) never
 * overflow the `50.dp` left gutter or clip into the plot area across any screen density (`REQ-UI-2797.2`).
 */
internal fun computeFittedGutterTextSize(
    baseTextSizePx: Float,
    measuredTextWidthPx: Float,
    maxGutterWidthPx: Float,
    minTextSizePx: Float = 14f
): Float {
    if (!baseTextSizePx.isFinite() || baseTextSizePx <= 0f) return minTextSizePx
    val effMin = minTextSizePx.coerceAtMost(baseTextSizePx)
    if (!measuredTextWidthPx.isFinite() || measuredTextWidthPx <= 0f) return baseTextSizePx
    if (!maxGutterWidthPx.isFinite() || maxGutterWidthPx <= 0f) return effMin
    if (measuredTextWidthPx <= maxGutterWidthPx) return baseTextSizePx
    val scaled = baseTextSizePx * (maxGutterWidthPx / measuredTextWidthPx)
    return scaled.coerceIn(effMin, baseTextSizePx)
}

/**
 * Computes the left X draw coordinate for a label right-aligned against [gutterRightPx] without
 * crossing past [gutterLeftPx] (`REQ-UI-2797.2`).
 */
internal fun computeRightAlignedGutterX(
    textWidthPx: Float,
    gutterLeftPx: Float,
    gutterRightPx: Float
): Float {
    if (!textWidthPx.isFinite() || !gutterLeftPx.isFinite() || !gutterRightPx.isFinite()) {
        return gutterLeftPx.takeIf { it.isFinite() } ?: 0f
    }
    return (gutterRightPx - textWidthPx).coerceAtLeast(gutterLeftPx)
}

/**
 * Draws the horizontal dotted grey entire-workout average reference line across
 * `[plotStartX, plotStartX + plotWidth]` at [avgY] using crisp round dots (`0.5.dp` on / `5.dp` off,
 * `StrokeCap.Round`) visually distinct from `6.dp / 6.dp` dashed guidelines (`REQ-UI-2797.3` / `ATT-9170`).
 *
 * Safely no-ops on narrow/zero-width canvases (`plotWidth <= 0f`) or non-finite coordinates.
 */
internal fun DrawScope.drawAverageReferenceLine(
    avgY: Float,
    plotStartX: Float,
    plotWidth: Float,
    color: Color,
    alpha: Float = AVERAGE_LINE_ALPHA,
    pathEffect: PathEffect? = null
) {
    if (plotWidth <= 0f || !plotWidth.isFinite() || !plotStartX.isFinite() || !avgY.isFinite()) {
        return
    }
    val strokeWidthPx = 1.5.dp.toPx()
    val effectivePathEffect = pathEffect ?: PathEffect.dashPathEffect(
        floatArrayOf(0.5.dp.toPx(), 5.dp.toPx()),
        0f
    )
    drawLine(
        color = color.copy(alpha = alpha),
        start = Offset(plotStartX, avgY),
        end = Offset(plotStartX + plotWidth, avgY),
        strokeWidth = strokeWidthPx,
        cap = StrokeCap.Round,
        pathEffect = effectivePathEffect
    )
}
