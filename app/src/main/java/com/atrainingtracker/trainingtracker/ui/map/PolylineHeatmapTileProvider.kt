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
 * into Web Mercator map tiles using additive distance-to-segment line accumulation and
 * maps accumulated traversal intensity to a 256-entry [ThermalColorLut].
 *
 * Replaces Gaussian point-cloud [com.google.maps.android.heatmaps.HeatmapTileProvider]
 * to eliminate vertex-density speed bias and synthetic point densification overhead (ATT-4480).
 *
 * @param allPaths The collection of geographic workout polylines to rasterize.
 * @param opacity Layer-wide opacity scaling (0.0 to 1.0).
 * @param strokeWidthPx Rendered stroke width in tile pixels.
 * @param intensityPerPass Fractional intensity added per traversal pass (0.0 to 1.0). If null, automatically calibrated to path count.
 * @param colorLut The 256-entry thermal color lookup table.
 * @param tileSize The output tile dimensions (standard: 256).
 */
class PolylineHeatmapTileProvider(
    allPaths: List<List<LatLng>>,
    val opacity: Double = 0.8,
    val strokeWidthPx: Float = 5.0f,
    intensityPerPass: Float? = null,
    val colorLut: ThermalColorLut = ThermalColorLut(opacity),
    val tileSize: Int = 256
) : TileProvider {

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

    private val stepIntensity: Int = run {
        val fraction = intensityPerPass ?: when {
            allPaths.size <= 5 -> 0.25f
            allPaths.size <= 20 -> 0.15f
            allPaths.size <= 60 -> 0.08f
            allPaths.size <= 150 -> 0.05f
            else -> 0.035f
        }
        (255 * fraction).roundToInt().coerceIn(1, 255)
    }

    override fun getTile(x: Int, y: Int, zoom: Int): Tile? {
        if (pathBounds.isEmpty() || zoom < 0 || zoom > 22) return TileProvider.NO_TILE

        val nTiles = 1 shl zoom
        if (x < 0 || x >= nTiles || y < 0 || y >= nTiles) return TileProvider.NO_TILE

        val lonWest = x.toDouble() / nTiles * 360.0 - 180.0
        val lonEast = (x + 1).toDouble() / nTiles * 360.0 - 180.0

        val nNorth = Math.PI - 2.0 * Math.PI * y / nTiles
        val latNorth = Math.toDegrees(Math.atan(Math.sinh(nNorth)))

        val nSouth = Math.PI - 2.0 * Math.PI * (y + 1) / nTiles
        val latSouth = Math.toDegrees(Math.atan(Math.sinh(nSouth)))

        val radius = strokeWidthPx / 2.0f
        val strokeMarginPx = strokeWidthPx * 2.0
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
            return TileProvider.NO_TILE
        }

        // Thread-safe intensity buffer allocation (256x256 int array)
        val intensityBuffer = IntArray(tileSize * tileSize)
        var anyDrawn = false

        for (candidate in candidates) {
            val path = candidate.path
            for (i in 0 until path.size - 1) {
                val pt1 = path[i]
                val pt2 = path[i + 1]

                val x1 = projectLonToTileX(pt1.longitude, x, nTiles, tileSize)
                val y1 = projectLatToTileY(pt1.latitude, y, nTiles, tileSize)
                val x2 = projectLonToTileX(pt2.longitude, x, nTiles, tileSize)
                val y2 = projectLatToTileY(pt2.latitude, y, nTiles, tileSize)

                if (rasterizeSegment(x1, y1, x2, y2, radius, stepIntensity, intensityBuffer)) {
                    anyDrawn = true
                }
            }
        }

        if (!anyDrawn) {
            return TileProvider.NO_TILE
        }

        // Map accumulated intensity through 256-entry ThermalColorLut
        val pixels = IntArray(tileSize * tileSize)
        var hasVisiblePixel = false

        for (i in intensityBuffer.indices) {
            val intensity = intensityBuffer[i]
            if (intensity > 0) {
                pixels[i] = colorLut.getColor(intensity)
                hasVisiblePixel = true
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

    private fun rasterizeSegment(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        radius: Float,
        intensity: Int,
        buffer: IntArray
    ): Boolean {
        val vx = x2 - x1
        val vy = y2 - y1
        val l2 = vx * vx + vy * vy

        val minX = max(0, (min(x1, x2) - radius).toInt())
        val maxX = min(tileSize - 1, (max(x1, x2) + radius).toInt())
        val minY = max(0, (min(y1, y2) - radius).toInt())
        val maxY = min(tileSize - 1, (max(y1, y2) + radius).toInt())

        if (minX > maxX || minY > maxY) {
            return false
        }

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
                    val addedVal = (weight * intensity).roundToInt()
                    if (addedVal > 0) {
                        val idx = rowOffset + px
                        buffer[idx] = minOf(255, buffer[idx] + addedVal)
                        segmentDrawn = true
                    }
                }
            }
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
