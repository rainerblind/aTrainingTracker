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

package com.atrainingtracker.trainingtracker.ui.aftermath.periodlist

import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.TileProvider
import com.google.maps.android.PolyUtil
import com.atrainingtracker.trainingtracker.ui.map.PolylineHeatmapTileProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Encapsulates the visual styling for period-based maps.
 */
data class PeriodMapVisuals(
    val heatmapProvider: TileProvider?,
    val polylineAlpha: Float,
    val polylineWidth: Float
)

/**
 * Immutable data state representing the pre-processed map preview for a PeriodSummaryCard.
 * Generated off the Android main thread (Dispatchers.Default) to ensure 60 fps scrolling.
 * (ATT-4477 / REQ-PER-019 / TST-PER-025)
 */
data class PeriodCardMapState(
    val primaryPaths: List<List<LatLng>> = emptyList(),
    val bounds: LatLngBounds? = null,
    val visuals: PeriodMapVisuals? = null,
    val regionCount: Int = 0,
    val primaryRegionPathsCount: Int = 0,
    val isReady: Boolean = false
)

/**
 * Returns Douglas-Peucker simplification tolerance in meters for the given [PeriodType].
 * Scales tolerance proportionally with period duration to bound aggregate vertices (<= 2,000 pts).
 * (REQ-PER-019.2)
 */
fun getPeriodSimplificationToleranceMeters(periodType: PeriodType): Double = when (periodType) {
    PeriodType.DAY -> 5.0
    PeriodType.WEEK -> 15.0
    PeriodType.MONTH -> 35.0
    PeriodType.YEAR -> 75.0
}

/**
 * Centralized logic to determine how workouts are visualized based on the period level.
 * Transitions from clear polylines (Day) to prominent line-density heatmaps (Year).
 * @param zoom The current map zoom level. If null, a default summary radius is used.
 */
fun getPeriodMapVisuals(
    periodType: PeriodType,
    allPaths: List<List<LatLng>>,
    isInteractive: Boolean = false,
    zoom: Float? = null
): PeriodMapVisuals {
    val heatmapProvider = run {
        if (allPaths.isEmpty() || periodType == PeriodType.DAY) {
            null
        } else {
            val opacity = when (periodType) {
                PeriodType.WEEK -> 0.4
                PeriodType.MONTH -> 0.5
                PeriodType.YEAR -> 0.6
                PeriodType.DAY -> 0.5
            }

            val strokeWidth = when {
                zoom != null -> (4f + (zoom - 10f).coerceAtLeast(0f) * 0.5f).coerceIn(3.5f, 7.5f)
                periodType == PeriodType.WEEK -> 6f
                periodType == PeriodType.MONTH -> 5f
                periodType == PeriodType.YEAR -> 4f
                else -> 5f
            }

            PolylineHeatmapTileProvider(
                allPaths = allPaths,
                opacity = opacity,
                strokeWidthPx = strokeWidth
            )
        }
    }

    val polylineAlpha = when (periodType) {
        PeriodType.DAY -> 1.0f
        PeriodType.WEEK -> 0.9f
        PeriodType.MONTH -> 0.8f
        PeriodType.YEAR -> 0.6f
    }

    val polylineWidth = when (periodType) {
        PeriodType.DAY -> 8f
        PeriodType.WEEK -> 8f
        PeriodType.MONTH -> 7f
        PeriodType.YEAR -> 5f
    }

    return PeriodMapVisuals(heatmapProvider, polylineAlpha, polylineWidth)
}

/**
 * Synchronous background computation pipeline for card map previews.
 * Decodes all member polylines, applies Douglas-Peucker decimation, clusters geographic regions,
 * and builds the appropriate line-density heatmap tile provider.
 * (REQ-PER-019)
 */
fun prepareCardMapPreviewSync(summary: PeriodSummary): PeriodCardMapState {
    val rawPolylines: Collection<String> = if (summary.workoutIdToPolylineMap.isNotEmpty()) {
        summary.workoutIdToPolylineMap.values
    } else {
        summary.polylines
    }

    if (rawPolylines.isEmpty()) {
        return PeriodCardMapState(isReady = true)
    }

    val tolerance = getPeriodSimplificationToleranceMeters(summary.periodType)

    val decodedAndSimplified: List<List<LatLng>> = rawPolylines.mapNotNull { encoded ->
        if (encoded.isEmpty() || !encoded.all { it in '?'..'~' }) null
        else {
            try {
                val decoded = PolyUtil.decode(encoded)
                val hasValidCoordinates = decoded.isNotEmpty() && decoded.all {
                    it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0
                }
                if (!hasValidCoordinates) null
                else if (tolerance > 0.0) {
                    PolyUtil.simplify(decoded, tolerance)
                } else {
                    decoded
                }
            } catch (e: Exception) {
                null
            }
        }
    }.filter { it.isNotEmpty() }

    if (decodedAndSimplified.isEmpty()) {
        return PeriodCardMapState(isReady = true)
    }

    val pathRegions = if (summary.minLat < 90.0 && summary.maxLat > -90.0) {
        val diagonal = SpatialRegionEngine.distanceBetween(
            LatLng(summary.minLat, summary.minLng),
            LatLng(summary.maxLat, summary.maxLng)
        )
        if (diagonal >= SpatialRegionConfig.SINGLE_REGION_ENVELOPE_METERS) {
            SpatialRegionEngine.detectRegionsFromPaths(decodedAndSimplified)
        } else {
            val allPoints = decodedAndSimplified.flatten()
            val bounds = if (allPoints.isNotEmpty()) {
                SpatialRegionEngine.buildNormalizedBounds(allPoints)
            } else {
                LatLngBounds(LatLng(summary.minLat, summary.minLng), LatLng(summary.maxLat, summary.maxLng))
            }
            listOf(
                SpatialPathRegion(
                    region = SpatialRegion(
                        id = "region_0",
                        label = "Region 1",
                        bounds = bounds,
                        center = LatLng((summary.minLat + summary.maxLat) / 2.0, (summary.minLng + summary.maxLng) / 2.0),
                        workoutCount = decodedAndSimplified.size,
                        totalDistanceMeters = 0.0,
                        mostRecentTimestampS = 0L,
                        isPrimary = true
                    ),
                    paths = decodedAndSimplified
                )
            )
        }
    } else {
        val allPoints = decodedAndSimplified.flatten()
        val bounds = if (allPoints.isNotEmpty()) SpatialRegionEngine.buildNormalizedBounds(allPoints) else null
        if (bounds != null) {
            listOf(
                SpatialPathRegion(
                    region = SpatialRegion(
                        id = "region_0",
                        label = "Region 1",
                        bounds = bounds,
                        center = LatLng(0.0, 0.0),
                        workoutCount = decodedAndSimplified.size,
                        totalDistanceMeters = 0.0,
                        mostRecentTimestampS = 0L,
                        isPrimary = true
                    ),
                    paths = decodedAndSimplified
                )
            )
        } else emptyList()
    }

    val primaryPathRegion = pathRegions.firstOrNull { it.region.isPrimary } ?: pathRegions.firstOrNull()
    val activePaths = primaryPathRegion?.paths ?: decodedAndSimplified
    val activeBounds = primaryPathRegion?.region?.bounds ?: run {
        val allPoints = activePaths.flatten()
        if (allPoints.isNotEmpty()) SpatialRegionEngine.buildNormalizedBounds(allPoints) else null
    }
    val visuals = getPeriodMapVisuals(summary.periodType, activePaths, isInteractive = false)

    return PeriodCardMapState(
        primaryPaths = activePaths,
        bounds = activeBounds,
        visuals = visuals,
        regionCount = pathRegions.size,
        primaryRegionPathsCount = activePaths.size,
        isReady = true
    )
}

/**
 * Asynchronously prepares the [PeriodCardMapState] on a background dispatcher.
 * (REQ-PER-019.1)
 */
suspend fun prepareCardMapPreview(
    summary: PeriodSummary,
    dispatcher: CoroutineDispatcher = Dispatchers.Default
): PeriodCardMapState = withContext(dispatcher) {
    prepareCardMapPreviewSync(summary)
}
