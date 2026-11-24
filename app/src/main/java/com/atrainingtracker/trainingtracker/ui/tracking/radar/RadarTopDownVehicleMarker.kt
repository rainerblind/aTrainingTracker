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

package com.atrainingtracker.trainingtracker.ui.tracking.radar

import android.content.res.Configuration
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.devices.radar.LaneState
import com.atrainingtracker.banalservice.devices.radar.RadarConnectionState
import com.atrainingtracker.banalservice.devices.radar.RadarSource
import com.atrainingtracker.banalservice.devices.radar.RadarState
import com.atrainingtracker.banalservice.devices.radar.RadarTarget
import com.atrainingtracker.banalservice.devices.radar.ThreatLevel
import com.atrainingtracker.banalservice.devices.radar.VehicleSizeCategory
import com.atrainingtracker.trainingtracker.settings.RadarEdgeSide
import com.atrainingtracker.trainingtracker.settings.RadarFullScreenMarkerStyle
import com.atrainingtracker.trainingtracker.settings.RadarTrafficOrientation
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Overhead vehicle silhouette archetype for top-down ("GTA-1 style") full-screen radar markers
 * (REQ-TRK-098.2, ATT-8959). UNKNOWN_OR_LIGHT always maps to [COMPACT_VEHICLE] (never a motorcycle).
 */
internal enum class TopDownSilhouetteKind {
    COMPACT_VEHICLE,
    SEDAN,
    TRUCK_OR_BUS
}

internal data class TopDownVehicleVisualSpec(
    val silhouette: TopDownSilhouetteKind,
    val canvasWidth: Dp,
    val canvasHeight: Dp,
    val badgeMinWidth: Dp,
    val badgeMinHeight: Dp,
    val fontSize: TextUnit,
    val descriptionRes: Int
)

internal fun resolveTopDownVehicleVisualSpec(
    category: VehicleSizeCategory
): TopDownVehicleVisualSpec = when (category) {
    VehicleSizeCategory.UNKNOWN_OR_LIGHT -> TopDownVehicleVisualSpec(
        silhouette = TopDownSilhouetteKind.COMPACT_VEHICLE,
        canvasWidth = 46.dp,
        canvasHeight = 72.dp,
        badgeMinWidth = 68.dp,
        badgeMinHeight = 30.dp,
        fontSize = 18.sp,
        descriptionRes = R.string.radar_vehicle_size_light
    )
    VehicleSizeCategory.CAR -> TopDownVehicleVisualSpec(
        silhouette = TopDownSilhouetteKind.SEDAN,
        canvasWidth = 52.dp,
        canvasHeight = 84.dp,
        badgeMinWidth = 74.dp,
        badgeMinHeight = 32.dp,
        fontSize = 19.sp,
        descriptionRes = R.string.radar_vehicle_size_car
    )
    VehicleSizeCategory.TRUCK_OR_BUS -> TopDownVehicleVisualSpec(
        silhouette = TopDownSilhouetteKind.TRUCK_OR_BUS,
        canvasWidth = 60.dp,
        canvasHeight = 114.dp,
        badgeMinWidth = 82.dp,
        badgeMinHeight = 34.dp,
        fontSize = 20.sp,
        descriptionRes = R.string.radar_vehicle_size_truck
    )
}

/**
 * Touch-transparent, top-down ("GTA-1 style") overhead vehicle marker for Full-Screen Radar mode
 * aligned with forward riding direction (bottom -> top) with integrated tabular distance badge
 * and single-pass `.layout` Frame-1 bounds clamping (REQ-TRK-098.2/.3, ATT-8959).
 */
@Composable
internal fun RadarTopDownVehicleMarker(
    target: RadarTarget,
    maxRangeMeters: Float,
    trafficOrientation: RadarTrafficOrientation,
    usableWidthPx: Float,
    usableHeightPx: Float
) {
    val spec = resolveTopDownVehicleVisualSpec(target.vehicleSizeCategory)
    val vehicleDesc = stringResource(spec.descriptionRes)
    val distanceMeters = target.rangeMeters.roundToInt()

    val normalizedY = RadarOverlayMath.normalizeRadarRange(target.rangeMeters, maxRangeMeters)
    val animatedFractionState = animateFloatAsState(
        targetValue = normalizedY,
        animationSpec = tween(durationMillis = 150, easing = LinearEasing),
        label = "topDownTarget_${target.targetId}_pos"
    )
    val horizontalFraction = RadarOverlayMath.calculateFullScreenHorizontalFraction(target, trafficOrientation)

    val baseThreatColor = RadarOverlayMath.resolveThreatColor(target.threatLevel)
    val contentColor = if (target.threatLevel == ThreatLevel.APPROACHING) {
        TTColor.RadarFullScreenApproachingContent
    } else {
        TTColor.RadarFullScreenFastContent
    }
    val capsuleShape = RoundedCornerShape(percent = 50)

    Column(
        modifier = Modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                val fraction = animatedFractionState.value
                val perspectiveScale = 0.94f + (1.0f - fraction) * 0.12f
                val scaleOverhangX = (perspectiveScale - 1f).coerceAtLeast(0f) * placeable.width * 0.5f
                val scaleOverhangY = (perspectiveScale - 1f).coerceAtLeast(0f) * placeable.height * 0.5f

                val minX = ceil(scaleOverhangX).toInt()
                val maxX = floor(usableWidthPx - placeable.width - scaleOverhangX).toInt().coerceAtLeast(minX)
                val desiredCenterX = horizontalFraction * usableWidthPx
                val xOffsetPx = (desiredCenterX - placeable.width / 2f).roundToInt().coerceIn(minX, maxX)

                val minY = ceil(scaleOverhangY).toInt()
                val maxY = floor(usableHeightPx - placeable.height - scaleOverhangY).toInt().coerceAtLeast(minY)
                val travelHeightPx = (maxY - minY).toFloat().coerceAtLeast(0f)
                val yOffsetPx = (minY + fraction * travelHeightPx).roundToInt().coerceIn(minY, maxY)

                layout(placeable.width, placeable.height) {
                    placeable.placeRelative(xOffsetPx, yOffsetPx)
                }
            }
            .graphicsLayer {
                val perspectiveScale = 0.94f + (1.0f - animatedFractionState.value) * 0.12f
                scaleX = perspectiveScale
                scaleY = perspectiveScale
            }
            .semantics(mergeDescendants = true) {
                contentDescription = "$vehicleDesc, ${distanceMeters}m"
            }
            .testTag("RadarFullScreenMarker_${target.targetId}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Canvas(
            modifier = Modifier
                .size(width = spec.canvasWidth, height = spec.canvasHeight)
                .testTag("RadarTopDownCanvas_${target.targetId}")
        ) {
            when (spec.silhouette) {
                TopDownSilhouetteKind.COMPACT_VEHICLE -> drawCompactVehicleOverhead(baseThreatColor)
                TopDownSilhouetteKind.SEDAN -> drawSedanOverhead(baseThreatColor)
                TopDownSilhouetteKind.TRUCK_OR_BUS -> drawTruckOverhead(baseThreatColor)
            }
        }

        Box(
            modifier = Modifier
                .background(TTColor.RadarFullScreenMarkerScrim, capsuleShape)
                .padding(2.dp)
        ) {
            Box(
                modifier = Modifier
                    .widthIn(min = spec.badgeMinWidth)
                    .heightIn(min = spec.badgeMinHeight)
                    .shadow(elevation = 4.dp, shape = capsuleShape, clip = false)
                    .drawBehind {
                        val radiusPx = size.height / 2f
                        drawRoundRect(
                            color = baseThreatColor,
                            cornerRadius = CornerRadius(radiusPx, radiusPx)
                        )
                    }
                    .border(
                        width = 1.5.dp,
                        color = TTColor.RadarFullScreenMarkerInnerBorder,
                        shape = capsuleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${distanceMeters}m",
                    color = contentColor,
                    fontSize = spec.fontSize,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                )
            }
        }
    }
}

private fun DrawScope.drawTiresAndMirrors(
    bodyLeft: Float,
    bodyRight: Float,
    tireTopFractions: List<Pair<Float, Float>>,
    mirrorTopFraction: Float
) {
    val w = size.width
    val h = size.height
    val tireW = w * 0.11f
    val tireCorner = CornerRadius(tireW * 0.4f, tireW * 0.4f)
    for ((startFrac, endFrac) in tireTopFractions) {
        val top = h * startFrac
        val tireH = h * (endFrac - startFrac)
        drawRoundRect(
            color = TTColor.RadarTopDownTire,
            topLeft = Offset(bodyLeft - tireW * 0.55f, top),
            size = Size(tireW, tireH),
            cornerRadius = tireCorner
        )
        drawRoundRect(
            color = TTColor.RadarTopDownTire,
            topLeft = Offset(bodyRight - tireW * 0.45f, top),
            size = Size(tireW, tireH),
            cornerRadius = tireCorner
        )
    }

    val mirrorW = w * 0.11f
    val mirrorH = h * 0.045f
    val mirrorY = h * mirrorTopFraction
    val mirrorCorner = CornerRadius(mirrorH * 0.45f, mirrorH * 0.45f)
    drawRoundRect(
        color = TTColor.RadarFullScreenMarkerScrim,
        topLeft = Offset(bodyLeft - mirrorW * 0.72f, mirrorY),
        size = Size(mirrorW, mirrorH),
        cornerRadius = mirrorCorner
    )
    drawRoundRect(
        color = TTColor.RadarFullScreenMarkerScrim,
        topLeft = Offset(bodyRight - mirrorW * 0.28f, mirrorY),
        size = Size(mirrorW, mirrorH),
        cornerRadius = mirrorCorner
    )
}

private fun DrawScope.drawChassisWithDualRim(
    topLeft: Offset,
    bodySize: Size,
    cornerRadius: CornerRadius,
    bodyColor: Color
) {
    val scrimStroke = 3.5.dp.toPx()
    val borderStroke = 1.5.dp.toPx()
    drawRoundRect(
        color = TTColor.RadarFullScreenMarkerScrim,
        topLeft = topLeft,
        size = bodySize,
        cornerRadius = cornerRadius,
        style = Stroke(width = scrimStroke)
    )
    drawRoundRect(
        color = bodyColor,
        topLeft = topLeft,
        size = bodySize,
        cornerRadius = cornerRadius
    )
    drawRoundRect(
        color = TTColor.RadarFullScreenMarkerInnerBorder,
        topLeft = topLeft,
        size = bodySize,
        cornerRadius = cornerRadius,
        style = Stroke(width = borderStroke)
    )
}

private fun DrawScope.drawWindshieldTrapezoid(
    topLeftX: Float,
    topRightX: Float,
    bottomLeftX: Float,
    bottomRightX: Float,
    topY: Float,
    bottomY: Float
) {
    val path = Path().apply {
        moveTo(topLeftX, topY)
        lineTo(topRightX, topY)
        lineTo(bottomRightX, bottomY)
        lineTo(bottomLeftX, bottomY)
        close()
    }
    drawPath(path = path, color = TTColor.RadarTopDownWindshield)
}

private fun DrawScope.drawHeadAndTailLights(
    bodyLeft: Float,
    bodyWidth: Float,
    headY: Float,
    tailY: Float,
    lampHeight: Float
) {
    val lampW = bodyWidth * 0.22f
    val inset = bodyWidth * 0.11f
    val corner = CornerRadius(lampHeight * 0.5f, lampHeight * 0.5f)
    // Headlights (front / top)
    drawRoundRect(
        color = TTColor.RadarTopDownHeadlight,
        topLeft = Offset(bodyLeft + inset, headY),
        size = Size(lampW, lampHeight),
        cornerRadius = corner
    )
    drawRoundRect(
        color = TTColor.RadarTopDownHeadlight,
        topLeft = Offset(bodyLeft + bodyWidth - inset - lampW, headY),
        size = Size(lampW, lampHeight),
        cornerRadius = corner
    )
    // Taillights (rear / bottom) with dark bezel so lamps stay visible on red FAST_APPROACHING chassis
    val bezelPad = 1.dp.toPx()
    for (lampX in listOf(bodyLeft + inset, bodyLeft + bodyWidth - inset - lampW)) {
        drawRoundRect(
            color = TTColor.RadarFullScreenMarkerScrim,
            topLeft = Offset(lampX - bezelPad, tailY - bezelPad),
            size = Size(lampW + bezelPad * 2f, lampHeight + bezelPad * 2f),
            cornerRadius = corner
        )
        drawRoundRect(
            color = TTColor.RadarTopDownTaillight,
            topLeft = Offset(lampX, tailY),
            size = Size(lampW, lampHeight),
            cornerRadius = corner
        )
    }
}

private fun DrawScope.drawCompactVehicleOverhead(bodyColor: Color) {
    val w = size.width
    val h = size.height
    val bodyLeft = w * 0.13f
    val bodyRight = w * 0.87f
    val bodyWidth = bodyRight - bodyLeft
    val bodyTop = h * 0.04f
    val bodyHeight = h * 0.92f

    drawTiresAndMirrors(
        bodyLeft = bodyLeft,
        bodyRight = bodyRight,
        tireTopFractions = listOf(0.17f to 0.34f, 0.66f to 0.83f),
        mirrorTopFraction = 0.29f
    )
    drawChassisWithDualRim(
        topLeft = Offset(bodyLeft, bodyTop),
        bodySize = Size(bodyWidth, bodyHeight),
        cornerRadius = CornerRadius(bodyWidth * 0.30f, bodyWidth * 0.30f),
        bodyColor = bodyColor
    )

    // Front windshield (swept upward -> narrower at roof)
    drawWindshieldTrapezoid(
        topLeftX = bodyLeft + bodyWidth * 0.14f,
        topRightX = bodyRight - bodyWidth * 0.14f,
        bottomLeftX = bodyLeft + bodyWidth * 0.21f,
        bottomRightX = bodyRight - bodyWidth * 0.21f,
        topY = h * 0.24f,
        bottomY = h * 0.42f
    )
    // Roof panel highlight
    drawRoundRect(
        color = TTColor.RadarTopDownRoofHighlight,
        topLeft = Offset(bodyLeft + bodyWidth * 0.22f, h * 0.44f),
        size = Size(bodyWidth * 0.56f, h * 0.28f),
        cornerRadius = CornerRadius(bodyWidth * 0.12f, bodyWidth * 0.12f)
    )
    // Rear hatch glass
    drawWindshieldTrapezoid(
        topLeftX = bodyLeft + bodyWidth * 0.22f,
        topRightX = bodyRight - bodyWidth * 0.22f,
        bottomLeftX = bodyLeft + bodyWidth * 0.16f,
        bottomRightX = bodyRight - bodyWidth * 0.16f,
        topY = h * 0.74f,
        bottomY = h * 0.86f
    )

    drawHeadAndTailLights(
        bodyLeft = bodyLeft,
        bodyWidth = bodyWidth,
        headY = h * 0.065f,
        tailY = h * 0.895f,
        lampHeight = h * 0.045f
    )
}

private fun DrawScope.drawSedanOverhead(bodyColor: Color) {
    val w = size.width
    val h = size.height
    val bodyLeft = w * 0.12f
    val bodyRight = w * 0.88f
    val bodyWidth = bodyRight - bodyLeft
    val bodyTop = h * 0.035f
    val bodyHeight = h * 0.93f

    drawTiresAndMirrors(
        bodyLeft = bodyLeft,
        bodyRight = bodyRight,
        tireTopFractions = listOf(0.15f to 0.31f, 0.68f to 0.84f),
        mirrorTopFraction = 0.29f
    )
    drawChassisWithDualRim(
        topLeft = Offset(bodyLeft, bodyTop),
        bodySize = Size(bodyWidth, bodyHeight),
        cornerRadius = CornerRadius(bodyWidth * 0.26f, bodyWidth * 0.26f),
        bodyColor = bodyColor
    )

    // Hood highlight bevel
    drawRoundRect(
        color = TTColor.RadarTopDownRoofHighlight,
        topLeft = Offset(bodyLeft + bodyWidth * 0.20f, h * 0.10f),
        size = Size(bodyWidth * 0.60f, h * 0.14f),
        cornerRadius = CornerRadius(bodyWidth * 0.10f, bodyWidth * 0.10f)
    )
    // Raked front windshield
    drawWindshieldTrapezoid(
        topLeftX = bodyLeft + bodyWidth * 0.13f,
        topRightX = bodyRight - bodyWidth * 0.13f,
        bottomLeftX = bodyLeft + bodyWidth * 0.20f,
        bottomRightX = bodyRight - bodyWidth * 0.20f,
        topY = h * 0.26f,
        bottomY = h * 0.43f
    )
    // Side cabin windows
    val sideWindowW = bodyWidth * 0.08f
    val sideWindowTop = h * 0.44f
    val sideWindowH = h * 0.23f
    drawRoundRect(
        color = TTColor.RadarTopDownWindshield,
        topLeft = Offset(bodyLeft + bodyWidth * 0.10f, sideWindowTop),
        size = Size(sideWindowW, sideWindowH),
        cornerRadius = CornerRadius(sideWindowW * 0.4f, sideWindowW * 0.4f)
    )
    drawRoundRect(
        color = TTColor.RadarTopDownWindshield,
        topLeft = Offset(bodyRight - bodyWidth * 0.10f - sideWindowW, sideWindowTop),
        size = Size(sideWindowW, sideWindowH),
        cornerRadius = CornerRadius(sideWindowW * 0.4f, sideWindowW * 0.4f)
    )
    // Cabin roof panel
    drawRoundRect(
        color = TTColor.RadarTopDownRoofHighlight,
        topLeft = Offset(bodyLeft + bodyWidth * 0.22f, h * 0.45f),
        size = Size(bodyWidth * 0.56f, h * 0.22f),
        cornerRadius = CornerRadius(bodyWidth * 0.10f, bodyWidth * 0.10f)
    )
    // Rear window + rear trunk deck below
    drawWindshieldTrapezoid(
        topLeftX = bodyLeft + bodyWidth * 0.21f,
        topRightX = bodyRight - bodyWidth * 0.21f,
        bottomLeftX = bodyLeft + bodyWidth * 0.15f,
        bottomRightX = bodyRight - bodyWidth * 0.15f,
        topY = h * 0.69f,
        bottomY = h * 0.80f
    )

    drawHeadAndTailLights(
        bodyLeft = bodyLeft,
        bodyWidth = bodyWidth,
        headY = h * 0.055f,
        tailY = h * 0.905f,
        lampHeight = h * 0.042f
    )
}

private fun DrawScope.drawTruckOverhead(bodyColor: Color) {
    val w = size.width
    val h = size.height
    val trailerLeft = w * 0.11f
    val trailerRight = w * 0.89f
    val trailerWidth = trailerRight - trailerLeft
    val cabInset = trailerWidth * 0.05f
    val cabLeft = trailerLeft + cabInset
    val cabRight = trailerRight - cabInset
    val cabWidth = cabRight - cabLeft

    // Steer tires on tractor cab + tandem dual rear tires on cargo trailer
    drawTiresAndMirrors(
        bodyLeft = cabLeft,
        bodyRight = cabRight,
        tireTopFractions = listOf(0.08f to 0.19f),
        mirrorTopFraction = 0.095f
    )
    val tireW = w * 0.10f
    val tireCorner = CornerRadius(tireW * 0.35f, tireW * 0.35f)
    for ((startFrac, endFrac) in listOf(0.68f to 0.78f, 0.81f to 0.91f)) {
        val top = h * startFrac
        val tireH = h * (endFrac - startFrac)
        drawRoundRect(
            color = TTColor.RadarTopDownTire,
            topLeft = Offset(trailerLeft - tireW * 0.50f, top),
            size = Size(tireW, tireH),
            cornerRadius = tireCorner
        )
        drawRoundRect(
            color = TTColor.RadarTopDownTire,
            topLeft = Offset(trailerRight - tireW * 0.50f, top),
            size = Size(tireW, tireH),
            cornerRadius = tireCorner
        )
    }

    // Dark fifth-wheel articulation hitch between cab and trailer
    drawRect(
        color = TTColor.RadarTopDownTire,
        topLeft = Offset(trailerLeft + trailerWidth * 0.24f, h * 0.24f),
        size = Size(trailerWidth * 0.52f, h * 0.10f)
    )

    // Tractor Cab (front / top)
    drawChassisWithDualRim(
        topLeft = Offset(cabLeft, h * 0.03f),
        bodySize = Size(cabWidth, h * 0.23f),
        cornerRadius = CornerRadius(cabWidth * 0.20f, cabWidth * 0.20f),
        bodyColor = bodyColor
    )
    // Wide panoramic cab windshield
    drawWindshieldTrapezoid(
        topLeftX = cabLeft + cabWidth * 0.11f,
        topRightX = cabRight - cabWidth * 0.11f,
        bottomLeftX = cabLeft + cabWidth * 0.16f,
        bottomRightX = cabRight - cabWidth * 0.16f,
        topY = h * 0.09f,
        bottomY = h * 0.175f
    )
    // Cab roof highlight
    drawRoundRect(
        color = TTColor.RadarTopDownRoofHighlight,
        topLeft = Offset(cabLeft + cabWidth * 0.16f, h * 0.185f),
        size = Size(cabWidth * 0.68f, h * 0.055f),
        cornerRadius = CornerRadius(cabWidth * 0.08f, cabWidth * 0.08f)
    )

    // Long Cargo Trailer (rear / bottom)
    val trailerTop = h * 0.30f
    val trailerHeight = h * 0.66f
    drawChassisWithDualRim(
        topLeft = Offset(trailerLeft, trailerTop),
        bodySize = Size(trailerWidth, trailerHeight),
        cornerRadius = CornerRadius(trailerWidth * 0.10f, trailerWidth * 0.10f),
        bodyColor = bodyColor
    )
    // Subtle sunlit cargo roof inner panel
    drawRoundRect(
        color = TTColor.RadarTopDownRoofHighlight,
        topLeft = Offset(trailerLeft + trailerWidth * 0.10f, trailerTop + trailerHeight * 0.05f),
        size = Size(trailerWidth * 0.80f, trailerHeight * 0.86f),
        cornerRadius = CornerRadius(trailerWidth * 0.05f, trailerWidth * 0.05f)
    )
    // Transverse cargo roof ribs
    val ribStroke = 1.25.dp.toPx()
    for (ribFrac in listOf(0.22f, 0.42f, 0.62f, 0.80f)) {
        val ribY = trailerTop + trailerHeight * ribFrac
        drawLine(
            color = TTColor.RadarTopDownTrailerRib,
            start = Offset(trailerLeft + trailerWidth * 0.09f, ribY),
            end = Offset(trailerRight - trailerWidth * 0.09f, ribY),
            strokeWidth = ribStroke
        )
    }

    drawHeadAndTailLights(
        bodyLeft = cabLeft,
        bodyWidth = cabWidth,
        headY = h * 0.042f,
        tailY = h * 0.918f,
        lampHeight = h * 0.030f
    )
}

@Preview(name = "Radar FullScreen TopDown - V2 Light", showBackground = true)
@Composable
private fun PreviewRadarTopDownVehiclesLight() {
    ATrainingTrackerTheme(darkTheme = false) {
        Surface(modifier = Modifier.width(360.dp).height(480.dp)) {
            RadarEdgeOverlay(
                radarState = RadarState(
                    connectionState = RadarConnectionState.CONNECTED,
                    overallThreatLevel = ThreatLevel.FAST_APPROACHING,
                    targets = listOf(
                        RadarTarget(
                            targetId = 1,
                            rangeMeters = 20f,
                            approachSpeedMps = 24f,
                            threatLevel = ThreatLevel.FAST_APPROACHING,
                            lateralOffsetMeters = -2.0f,
                            laneState = LaneState.PASSING_LANE,
                            vehicleSizeCategory = VehicleSizeCategory.CAR
                        ),
                        RadarTarget(
                            targetId = 2,
                            rangeMeters = 105f,
                            approachSpeedMps = 12f,
                            threatLevel = ThreatLevel.APPROACHING,
                            lateralOffsetMeters = 0.0f,
                            laneState = LaneState.IN_RIDER_LANE,
                            vehicleSizeCategory = VehicleSizeCategory.TRUCK_OR_BUS
                        ),
                        RadarTarget(
                            targetId = 3,
                            rangeMeters = 160f,
                            approachSpeedMps = 10f,
                            threatLevel = ThreatLevel.APPROACHING,
                            lateralOffsetMeters = -1.2f,
                            laneState = LaneState.CHANGING_LANE,
                            vehicleSizeCategory = VehicleSizeCategory.UNKNOWN_OR_LIGHT
                        )
                    ),
                    source = RadarSource.BLE
                ),
                edgeSide = RadarEdgeSide.FULL_SCREEN,
                fullScreenMarkerStyle = RadarFullScreenMarkerStyle.TOP_DOWN_VEHICLES,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Preview(
    name = "Radar FullScreen TopDown - V1 Dark AMOLED",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true
)
@Composable
private fun PreviewRadarTopDownVehiclesDark() {
    ATrainingTrackerTheme(darkTheme = true) {
        Surface(color = Color.Black, modifier = Modifier.width(360.dp).height(480.dp)) {
            RadarEdgeOverlay(
                radarState = RadarState(
                    connectionState = RadarConnectionState.CONNECTED,
                    overallThreatLevel = ThreatLevel.FAST_APPROACHING,
                    targets = listOf(
                        RadarTarget(
                            targetId = 1,
                            rangeMeters = 45f,
                            approachSpeedMps = 22f,
                            threatLevel = ThreatLevel.FAST_APPROACHING
                        ),
                        RadarTarget(
                            targetId = 2,
                            rangeMeters = 118f,
                            approachSpeedMps = 12f,
                            threatLevel = ThreatLevel.APPROACHING
                        )
                    ),
                    source = RadarSource.BLE
                ),
                edgeSide = RadarEdgeSide.FULL_SCREEN,
                fullScreenMarkerStyle = RadarFullScreenMarkerStyle.TOP_DOWN_VEHICLES,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
