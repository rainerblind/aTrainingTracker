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

import android.graphics.Bitmap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Tile
import com.google.android.gms.maps.model.TileProvider
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Internal bounding box representation for candidate workout polylines.
 */
internal data class PolylineBoundingBox(
    val path: List<LatLng>,
    val minLat: Double,
    val maxLat: Double,
    val minLng: Double,
    val maxLng: Double
)

/**
 * True line-density [TileProvider] that rasterizes continuous GPS workout polylines
 * into Web Mercator map tiles using per-workout coverage masks (blended via max) and
 * unclamped additive accumulation across workouts, mapping final traversal counts to a 256-entry [ThermalColorLut].
 *
 * Eliminates synthetic point densification overhead (ATT-4480) and eliminates vertex bead
 * and standstill blob artifacts by counting each distinct workout once per pixel (ATT-4932 / REQ-MAP-056.5).
 *
 * @param allPaths The collection of geographic workout polylines to rasterize.
 * @param opacity Layer-wide opacity scaling (0.0 to 1.0).
 * @param strokeWidthPx Rendered stroke width in tile pixels.
 * @param intensityPerPass Fractional intensity added per traversal pass (0.0 to 1.0). If null, automatically calibrated to path count.
 * @param colorLut The 256-entry thermal color lookup table.
 * @param tileSize The output tile dimensions (standard: 256).
 * @param maxCorridorPassCount The period-wide maximum corridor overlap count for logarithmic normalization (ATT-4929 / REQ-MAP-056.6).
 * @param isMiniMap True if rendering within a compact card preview container, applying scale dampening (ATT-4933 / REQ-MAP-057.1).
 * @param dynamicStrokeScaling True to adapt stroke width to tile zoom level (ATT-4933 / REQ-MAP-057.1).
 */
class PolylineHeatmapTileProvider(
    allPaths: List<List<LatLng>>,
    val opacity: Double = 1.0,
    val strokeWidthPx: Float = 5.0f,
    intensityPerPass: Float? = null,
    val colorLut: ThermalColorLut = ThermalColorLut(opacity),
    val tileSize: Int = 256,
    val maxCorridorPassCount: Int? = null,
    val isMiniMap: Boolean = false,
    val dynamicStrokeScaling: Boolean = true,
    val useClassicStepIntensity: Boolean = false
) : TileProvider {

    /**
     * Evaluates the effective pixel stroke width for a given tile zoom level (REQ-MAP-057.1).
     *
     * In standard view: scales from 1.8px at zoom <= 10 up to 5.4px at zoom 16 (clamped 1.8..6.0px).
     * In mini-map view: applies a dampening scale factor (0.65x, clamped 1.2..2.2px) to preserve street grid readability.
     */
    fun calculateEffectiveStrokeWidth(zoom: Int): Float {
        if (!dynamicStrokeScaling) {
            return strokeWidthPx
        }
        val baseWidth = (1.8f + (zoom - 10).coerceAtLeast(0) * 0.6f).coerceIn(1.8f, 6.0f)
        return if (isMiniMap) {
            (baseWidth * 0.65f).coerceIn(1.2f, 2.2f)
        } else {
            baseWidth
        }
    }

    private val pathBounds: List<PolylineBoundingBox> = allPaths.mapNotNull { path ->
        if (path.size < 2) null
        else {
            var minLat = Double.MAX_VALUE
            var maxLat = -Double.MAX_VALUE
            var minLng = Double.MAX_VALUE
            var maxLng = -Double.MAX_VALUE
            for (pt in path) {
                if (pt.latitude < minLat) minLat = pt.latitude
                if (pt.latitude > maxLat) maxLat = pt.latitude
                if (pt.longitude < minLng) minLng = pt.longitude
                if (pt.longitude > maxLng) maxLng = pt.longitude
            }
            PolylineBoundingBox(path, minLat, maxLat, minLng, maxLng)
        }
    }

    private val stepIntensity: Int = if (useClassicStepIntensity) {
        calculateClassicStepIntensity(allPaths.size, intensityPerPass)
    } else {
        calculateStepIntensity(allPaths.size, intensityPerPass)
    }

    companion object {
        /**
         * Calculates classic linear pass intensity [1..255] based on aggregate workout count (pre-ATT-4833 / REQ-MAP-058.2).
         * Saturates after 4..29 passes: <=5: 0.25f, <=20: 0.15f, <=60: 0.08f, <=150: 0.05f, >150: 0.035f.
         */
        fun calculateClassicStepIntensity(pathCount: Int, intensityPerPass: Float? = null): Int {
            val fraction = intensityPerPass ?: when {
                pathCount <= 5 -> 0.25f
                pathCount <= 20 -> 0.15f
                pathCount <= 60 -> 0.08f
                pathCount <= 150 -> 0.05f
                else -> 0.035f
            }
            return (255 * fraction).roundToInt().coerceIn(1, 255)
        }

        /**
         * Calculates the adaptive pass intensity [1..255] based on aggregate workout count (REQ-PER-024.4).
         */
        fun calculateStepIntensity(pathCount: Int, intensityPerPass: Float? = null): Int {
            val fraction = intensityPerPass ?: run {
                val targetPasses = when {
                    pathCount <= 3 -> 2f
                    pathCount <= 10 -> 3f
                    pathCount <= 35 -> 5f
                    pathCount <= 100 -> 8f
                    else -> 12f
                }
                1.0f / targetPasses
            }
            return (255 * fraction).roundToInt().coerceIn(1, 255)
        }

        /**
         * Estimates the maximum corridor overlap (pass count) across a collection of workout paths
         * by rasterizing candidate polylines onto a lightweight 2D coarse grid (ATT-4929 / REQ-MAP-056.6).
         * Runs in O(N) time with zero allocation churn, suitable for Dispatchers.Default background execution.
         *
         * @param allPaths The collection of geographic polylines to evaluate.
         * @param coarseGridSize The dimension of the coarse spatial grid (default: 128).
         * @return The estimated maximum corridor pass count (>= 1).
         */
        fun estimateMaxCorridorPassCount(
            allPaths: List<List<LatLng>>,
            coarseGridSize: Int = 128
        ): Int {
            if (allPaths.isEmpty()) return 1

            var minLat = Double.MAX_VALUE
            var maxLat = -Double.MAX_VALUE
            var minLng = Double.MAX_VALUE
            var maxLng = -Double.MAX_VALUE
            var validPathCount = 0

            for (path in allPaths) {
                if (path.size < 2) continue
                validPathCount++
                for (pt in path) {
                    if (pt.latitude < minLat) minLat = pt.latitude
                    if (pt.latitude > maxLat) maxLat = pt.latitude
                    if (pt.longitude < minLng) minLng = pt.longitude
                    if (pt.longitude > maxLng) maxLng = pt.longitude
                }
            }

            if (validPathCount == 0) return 1

            val spanLat = maxLat - minLat
            val spanLng = maxLng - minLng

            if (spanLat <= 0.0 && spanLng <= 0.0) {
                return validPathCount.coerceAtLeast(1)
            }

            val grid = IntArray(coarseGridSize * coarseGridSize)
            val visited = IntArray(coarseGridSize * coarseGridSize)

            for (pIndex in allPaths.indices) {
                val path = allPaths[pIndex]
                if (path.size < 2) continue
                val marker = pIndex + 1

                for (i in 0 until path.size - 1) {
                    val pt1 = path[i]
                    val pt2 = path[i + 1]

                    val gx1 = if (spanLng > 0.0) {
                        (((pt1.longitude - minLng) / spanLng) * (coarseGridSize - 1)).toInt().coerceIn(0, coarseGridSize - 1)
                    } else 0
                    val gy1 = if (spanLat > 0.0) {
                        (((pt1.latitude - minLat) / spanLat) * (coarseGridSize - 1)).toInt().coerceIn(0, coarseGridSize - 1)
                    } else 0

                    val gx2 = if (spanLng > 0.0) {
                        (((pt2.longitude - minLng) / spanLng) * (coarseGridSize - 1)).toInt().coerceIn(0, coarseGridSize - 1)
                    } else 0
                    val gy2 = if (spanLat > 0.0) {
                        (((pt2.latitude - minLat) / spanLat) * (coarseGridSize - 1)).toInt().coerceIn(0, coarseGridSize - 1)
                    } else 0

                    // Bresenham line traversal on coarse grid
                    var cx = gx1
                    var cy = gy1
                    val dx = kotlin.math.abs(gx2 - gx1)
                    val dy = kotlin.math.abs(gy2 - gy1)
                    val sx = if (gx1 < gx2) 1 else -1
                    val sy = if (gy1 < gy2) 1 else -1
                    var err = dx - dy

                    while (true) {
                        val cellIdx = cy * coarseGridSize + cx
                        if (visited[cellIdx] != marker) {
                            visited[cellIdx] = marker
                            grid[cellIdx]++
                        }
                        if (cx == gx2 && cy == gy2) break
                        val e2 = 2 * err
                        if (e2 > -dy) {
                            err -= dy
                            cx += sx
                        }
                        if (e2 < dx) {
                            err += dx
                            cy += sy
                        }
                    }
                }
            }

            var maxPasses = 1
            for (count in grid) {
                if (count > maxPasses) {
                    maxPasses = count
                }
            }
            return maxPasses
        }
    }

    override fun getTile(x: Int, y: Int, zoom: Int): Tile? {
        val accumulationBuffer = computeAccumulationBuffer(x, y, zoom) ?: return TileProvider.NO_TILE

        // Map accumulated workout counts through 256-entry ThermalColorLut
        val pixels = IntArray(tileSize * tileSize)
        var hasVisiblePixel = false

        val maxPasses = maxCorridorPassCount
        val baseCyanIntensity = 85

        for (i in accumulationBuffer.indices) {
            val count = accumulationBuffer[i]
            if (count > 0f) {
                val intensity = if (maxPasses != null && maxPasses > 1) {
                    // Logarithmic transfer curve (ATT-4929 / REQ-MAP-056.6):
                    // c = 1.0 maps to base cyan intensity (85)
                    // c = maxPasses maps to full Deep Indigo saturation (255)
                    // Anti-aliased edge pixels (0.0 < c < 1.0) linearly scale up to base cyan.
                    if (count < 1.0f) {
                        (count * baseCyanIntensity).roundToInt().coerceIn(0, baseCyanIntensity)
                    } else {
                        val ratio = (Math.log(count.toDouble()) / Math.log(maxPasses.toDouble())).coerceIn(0.0, 1.0)
                        (baseCyanIntensity + (255 - baseCyanIntensity) * ratio).roundToInt().coerceIn(baseCyanIntensity, 255)
                    }
                } else if (maxPasses != null && maxPasses <= 1) {
                    if (count < 1.0f) {
                        (count * baseCyanIntensity).roundToInt().coerceIn(0, baseCyanIntensity)
                    } else {
                        baseCyanIntensity
                    }
                } else {
                    // Fallback to legacy stepIntensity lookup if maxCorridorPassCount not provided
                    (count * stepIntensity).roundToInt().coerceIn(0, 255)
                }

                if (intensity > 0) {
                    pixels[i] = colorLut.getColor(intensity)
                    hasVisiblePixel = true
                } else {
                    pixels[i] = 0
                }
            } else {
                pixels[i] = 0
            }
        }

        if (!hasVisiblePixel) {
            return TileProvider.NO_TILE
        }

        val bitmap = Bitmap.createBitmap(pixels, tileSize, tileSize, Bitmap.Config.ARGB_8888)
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        bitmap.recycle()

        return Tile(tileSize, tileSize, stream.toByteArray())
    }

    /**
     * Projects candidate workout paths intersecting tile (x, y, zoom) and accumulates
     * per-workout coverage masks (blended via max) into an unclamped [FloatArray]
     * where each covered pixel represents the number of distinct workouts passing through it (ATT-4932).
     *
     * @return FloatArray of size tileSize * tileSize containing accumulated workout counts, or null if no paths draw.
     */
    internal fun computeAccumulationBuffer(x: Int, y: Int, zoom: Int): FloatArray? {
        if (pathBounds.isEmpty() || zoom < 0 || zoom > 22) return null

        val nTiles = 1 shl zoom
        if (x < 0 || x >= nTiles || y < 0 || y >= nTiles) return null

        val lonWest = x.toDouble() / nTiles * 360.0 - 180.0
        val lonEast = (x + 1).toDouble() / nTiles * 360.0 - 180.0

        val nNorth = Math.PI - 2.0 * Math.PI * y / nTiles
        val latNorth = Math.toDegrees(Math.atan(Math.sinh(nNorth)))

        val nSouth = Math.PI - 2.0 * Math.PI * (y + 1) / nTiles
        val latSouth = Math.toDegrees(Math.atan(Math.sinh(nSouth)))

        val effectiveStrokeWidth = calculateEffectiveStrokeWidth(zoom)
        val radius = effectiveStrokeWidth / 2.0f
        val strokeMarginPx = effectiveStrokeWidth * 2.0
        val lonMargin = (strokeMarginPx / (tileSize * nTiles)) * 360.0
        val latMargin = lonMargin

        val expandedWest = lonWest - lonMargin
        val expandedEast = lonEast + lonMargin
        val expandedSouth = latSouth - latMargin
        val expandedNorth = latNorth + latMargin

        // O(1) Candidate Bounding-Box Culling
        val candidates = pathBounds.filter { b ->
            b.maxLat >= expandedSouth && b.minLat <= expandedNorth &&
            b.maxLng >= expandedWest && b.minLng <= expandedEast
        }

        if (candidates.isEmpty()) {
            return null
        }

        // Thread-safe / call-local accumulation & scratch mask allocation
        val accumulationBuffer = FloatArray(tileSize * tileSize)
        val scratchMask = FloatArray(tileSize * tileSize)
        val dirtyBounds = IntArray(4)
        var anyDrawn = false

        for (candidate in candidates) {
            val path = candidate.path
            dirtyBounds[0] = tileSize
            dirtyBounds[1] = tileSize
            dirtyBounds[2] = -1
            dirtyBounds[3] = -1

            for (i in 0 until path.size - 1) {
                val pt1 = path[i]
                val pt2 = path[i + 1]

                val x1 = projectLonToTileX(pt1.longitude, x, nTiles, tileSize)
                val y1 = projectLatToTileY(pt1.latitude, y, nTiles, tileSize)
                val x2 = projectLonToTileX(pt2.longitude, x, nTiles, tileSize)
                val y2 = projectLatToTileY(pt2.latitude, y, nTiles, tileSize)

                rasterizeSegment(x1, y1, x2, y2, radius, scratchMask, dirtyBounds)
            }

            // If any segment of this candidate path drew to scratchMask, accumulate and reset dirty bounds
            if (dirtyBounds[2] >= dirtyBounds[0] && dirtyBounds[3] >= dirtyBounds[1]) {
                val minX = dirtyBounds[0]
                val minY = dirtyBounds[1]
                val maxX = dirtyBounds[2]
                val maxY = dirtyBounds[3]

                for (py in minY..maxY) {
                    val rowOffset = py * tileSize
                    for (px in minX..maxX) {
                        val idx = rowOffset + px
                        val maskVal = scratchMask[idx]
                        if (maskVal > 0f) {
                            accumulationBuffer[idx] += maskVal
                            scratchMask[idx] = 0f
                            anyDrawn = true
                        }
                    }
                }
            }
        }

        return if (anyDrawn) accumulationBuffer else null
    }

    private fun rasterizeSegment(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        radius: Float,
        scratchMask: FloatArray,
        dirtyBounds: IntArray
    ): Boolean {
        val minX = max(0, (min(x1, x2) - radius).toInt())
        val maxX = min(tileSize - 1, (max(x1, x2) + radius).toInt())
        val minY = max(0, (min(y1, y2) - radius).toInt())
        val maxY = min(tileSize - 1, (max(y1, y2) + radius).toInt())

        if (minX > maxX || minY > maxY) {
            return false
        }

        val vx = x2 - x1
        val vy = y2 - y1
        val l2 = vx * vx + vy * vy
        val r2 = radius * radius
        var segmentDrawn = false

        for (py in minY..maxY) {
            val rowOffset = py * tileSize
            for (px in minX..maxX) {
                val dist2 = if (l2 == 0f) {
                    val dx = px - x1
                    val dy = py - y1
                    dx * dx + dy * dy
                } else {
                    val t = ((px - x1) * vx + (py - y1) * vy) / l2
                    val clampedT = t.coerceIn(0f, 1f)
                    val qx = x1 + clampedT * vx
                    val qy = y1 + clampedT * vy
                    val dx = px - qx
                    val dy = py - qy
                    dx * dx + dy * dy
                }

                if (dist2 <= r2) {
                    val dist = sqrt(dist2)
                    val weight = if (dist <= radius - 0.75f) {
                        1.0f
                    } else {
                        ((radius - dist) / 0.75f).coerceIn(0f, 1f)
                    }
                    if (weight > 0f) {
                        val idx = rowOffset + px
                        if (weight > scratchMask[idx]) {
                            scratchMask[idx] = weight
                            segmentDrawn = true
                        }
                    }
                }
            }
        }

        if (segmentDrawn) {
            if (minX < dirtyBounds[0]) dirtyBounds[0] = minX
            if (minY < dirtyBounds[1]) dirtyBounds[1] = minY
            if (maxX > dirtyBounds[2]) dirtyBounds[2] = maxX
            if (maxY > dirtyBounds[3]) dirtyBounds[3] = maxY
        }

        return segmentDrawn
    }

    private fun projectLonToTileX(lon: Double, x: Int, nTiles: Int, tileSize: Int): Float {
        val worldX = (lon + 180.0) / 360.0 * tileSize * nTiles
        return (worldX - x * tileSize).toFloat()
    }

    private fun projectLatToTileY(lat: Double, y: Int, nTiles: Int, tileSize: Int): Float {
        val clampedLat = lat.coerceIn(-85.05112878, 85.05112878)
        val sinLat = Math.sin(Math.toRadians(clampedLat))
        val worldY = (0.5 - Math.log((1.0 + sinLat) / (1.0 - sinLat)) / (4.0 * Math.PI)) * tileSize * nTiles
        return (worldY - y * tileSize).toFloat()
    }
}
