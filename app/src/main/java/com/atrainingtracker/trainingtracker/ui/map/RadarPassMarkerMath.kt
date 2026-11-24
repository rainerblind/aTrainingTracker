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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.annotation.VisibleForTesting
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import com.google.android.gms.maps.model.BitmapDescriptor
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor

/**
 * Pure zoom-scaling, color-priority, viewport-culling, dual-contrast silhouette rendering, and
 * bounded `O(1)` [BitmapDescriptor] caching for aftermarket radar vehicle pass map icons
 * (`REQ-RAD-006`, `REQ-TRK-073.5`, `ATT-8735`).
 */

/**
 * Minimum Google Maps zoom level at which radar car/truck pass icons are displayed.
 * Below this zoom threshold, streets are too narrow for half-road-width icons and overview
 * routes with 1,000+ passes must stay completely uncluttered (`0` radar markers emitted).
 */
const val RADAR_MARKER_MIN_VISIBLE_ZOOM = 14.5f

/** Default fallback zoom when [MarkerLayer] is invoked without an explicit camera zoom. */
const val RADAR_MARKER_DEFAULT_ZOOM = 16.0f

/** Relative closing speed threshold for Amber/Orange moderate closing speed (`25.2 km/h`). */
const val RADAR_MODERATE_CLOSING_SPEED_MPS = 7.0

/** Relative closing speed threshold for Red high closing speed (`54.0 km/h`, matching `FAST_APPROACHING`). */
const val RADAR_HIGH_CLOSING_SPEED_MPS = 15.0

/**
 * Quantized icon sizes (in dp) per discrete visible zoom bucket, calibrated to stay at most
 * ~half the rendered Google Maps road width at each street-level zoom band:
 * - Bucket 0 (`14.5..15.5`): `8 dp`
 * - Bucket 1 (`15.5..16.5`): `10 dp`
 * - Bucket 2 (`16.5..17.5`): `12 dp`
 * - Bucket 3 (`17.5..18.5`): `15 dp`
 * - Bucket 4 (`>= 18.5`): `18 dp`
 */
val RADAR_ZOOM_BUCKET_SIZES_DP: IntArray = intArrayOf(8, 10, 12, 15, 18)

/** Total number of quantized visible zoom buckets (`5` -> max `20` car bitmaps / `40` car+truck bitmaps). */
val RADAR_ZOOM_BUCKET_COUNT: Int = RADAR_ZOOM_BUCKET_SIZES_DP.size

/** Viewport expansion fraction on each side to eliminate edge pop-in during panning. */
const val RADAR_VIEWPORT_CULL_MARGIN_FRACTION = 0.25

/**
 * Defensive upper bound when `visibleBounds == null` transiently before map layout completes,
 * preventing 1,000+ unculled `Marker` composables from flooding Google Maps on Frame 0.
 */
const val MAX_UNCULLED_RADAR_MARKERS = 200

/**
 * Resolves the authoritative [RadarPassColorCategory] in strict hazard priority order (`REQ-RAD-006`):
 * 1. [RadarPassColorCategory.CLOSE_PASS] (Brown): `passingDistanceM != null && passingDistanceM < closePassThresholdM`
 * 2. [RadarPassColorCategory.HIGH_SPEED] (Red): relative closing speed `>= 15.0 m/s` (`54 km/h`)
 * 3. [RadarPassColorCategory.MODERATE_SPEED] (Amber/Orange): relative closing speed `>= 7.0 m/s` and `< 15.0 m/s`
 * 4. [RadarPassColorCategory.LOW_SPEED] (Green): relative closing speed `< 7.0 m/s` (or null)
 */
fun resolveRadarPassColorCategory(
    approachSpeedMps: Double?,
    passingDistanceM: Double?,
    closePassThresholdM: Float
): RadarPassColorCategory {
    if (passingDistanceM != null && passingDistanceM < closePassThresholdM.toDouble()) {
        return RadarPassColorCategory.CLOSE_PASS
    }
    val speed = approachSpeedMps ?: 0.0
    return when {
        speed >= RADAR_HIGH_CLOSING_SPEED_MPS -> RadarPassColorCategory.HIGH_SPEED
        speed >= RADAR_MODERATE_CLOSING_SPEED_MPS -> RadarPassColorCategory.MODERATE_SPEED
        else -> RadarPassColorCategory.LOW_SPEED
    }
}

/**
 * Resolves the centralized [TTColor] token for [category] (`ThemeColorCentralizationAuditTest`).
 */
fun resolveRadarPassColor(category: RadarPassColorCategory): Color = when (category) {
    RadarPassColorCategory.CLOSE_PASS -> TTColor.RadarClosePassBrown
    RadarPassColorCategory.HIGH_SPEED -> TTColor.RadarThreatRed
    RadarPassColorCategory.MODERATE_SPEED -> TTColor.RadarThreatAmber
    RadarPassColorCategory.LOW_SPEED -> TTColor.RadarClearGreen
}

/**
 * Resolves z-index so higher-urgency radar passes (Brown close passes, Red fast approaches)
 * always render cleanly above lower-urgency passes and above the route polyline (`zIndex <= 52f`).
 */
fun resolveRadarPassZIndex(category: RadarPassColorCategory): Float = when (category) {
    RadarPassColorCategory.LOW_SPEED -> 60f
    RadarPassColorCategory.MODERATE_SPEED -> 61f
    RadarPassColorCategory.HIGH_SPEED -> 62f
    RadarPassColorCategory.CLOSE_PASS -> 63f
}

/**
 * Maps [currentZoom] to a quantized zoom bucket index in `0 until RADAR_ZOOM_BUCKET_COUNT`,
 * or `null` when zoomed out below [RADAR_MARKER_MIN_VISIBLE_ZOOM] (hiding all radar icons).
 */
fun resolveRadarPassZoomBucket(currentZoom: Float): Int? {
    if (!currentZoom.isFinite() || currentZoom < RADAR_MARKER_MIN_VISIBLE_ZOOM) return null
    val bucket = floor((currentZoom - RADAR_MARKER_MIN_VISIBLE_ZOOM).toDouble()).toInt()
    return bucket.coerceIn(0, RADAR_ZOOM_BUCKET_COUNT - 1)
}

/**
 * Returns the target icon bounding size in dp for a quantized [zoomBucket] (`0..4`).
 */
fun resolveRadarPassIconSizeDp(zoomBucket: Int): Int {
    val clamped = zoomBucket.coerceIn(0, RADAR_ZOOM_BUCKET_SIZES_DP.lastIndex)
    return RADAR_ZOOM_BUCKET_SIZES_DP[clamped]
}

/**
 * Hysteresis viewport bounds resolver (`ATT-8735`): retains [previousBuffered] across minor pans
 * while [currentVisible] remains inside the pre-expanded margin and within `2.5x` span ratio,
 * allowing Compose Strong Skipping to bypass redundant `MapContentScopeImpl.Render` passes during
 * smooth dragging while immediately tightening/updating bounds on large pans or zoom-ins.
 */
fun resolveBufferedViewportBounds(
    previousBuffered: MapViewportBounds?,
    currentVisible: MapViewportBounds?,
    marginFraction: Double = RADAR_VIEWPORT_CULL_MARGIN_FRACTION
): MapViewportBounds? {
    if (currentVisible == null) return previousBuffered
    if (previousBuffered == null) return currentVisible.expandBy(marginFraction)
    if (!previousBuffered.containsBounds(currentVisible)) {
        return currentVisible.expandBy(marginFraction)
    }
    val spanOversized = previousBuffered.latSpan > currentVisible.latSpan * 2.5 ||
        previousBuffered.lngSpan > currentVisible.lngSpan * 2.5
    return if (spanOversized) {
        currentVisible.expandBy(marginFraction)
    } else {
        previousBuffered
    }
}

/**
 * Filters radar pass [markers] for rendering at [currentZoom] and optional [visibleBounds]:
 * - Returns `emptyList()` immediately when `currentZoom < RADAR_MARKER_MIN_VISIBLE_ZOOM` (`0` overview markers).
 * - When [visibleBounds] is provided, expands by [marginFraction] and retains strictly in-viewport passes.
 * - When [visibleBounds] is `null` (pre-layout), caps output at [MAX_UNCULLED_RADAR_MARKERS] to guarantee
 *   1,000+ pass workouts never flood Google Maps prior to projection initialization.
 */
fun cullRadarPassMarkers(
    markers: List<LocationMarker>,
    currentZoom: Float,
    visibleBounds: MapViewportBounds?,
    marginFraction: Double = RADAR_VIEWPORT_CULL_MARGIN_FRACTION
): List<LocationMarker> {
    if (markers.isEmpty() || resolveRadarPassZoomBucket(currentZoom) == null) {
        return emptyList()
    }
    if (visibleBounds == null) {
        return if (markers.size <= MAX_UNCULLED_RADAR_MARKERS) {
            markers
        } else {
            emptyList()
        }
    }
    val expanded = visibleBounds.expandBy(marginFraction)
    val result = ArrayList<LocationMarker>(minOf(markers.size, 64))
    for (marker in markers) {
        if (expanded.contains(marker.position)) {
            result.add(marker)
        }
    }
    return result
}

/**
 * Builds the outer silhouette [Path] matching `ic_radar_map_car.xml` on a 24x24 viewport scaled by [scale],
 * used to draw the crisp dual-contrast dark hairline + opaque white silhouette halo behind the vector icon.
 */
internal fun buildRadarCarOuterSilhouettePath(scale: Float): Path {
    fun x(v: Float) = v * scale
    fun y(v: Float) = v * scale
    return Path().apply {
        moveTo(x(7.8f), y(5.2f))
        lineTo(x(16.2f), y(5.2f))
        cubicTo(x(17.0f), y(5.2f), x(17.6f), y(5.7f), x(17.9f), y(6.4f))
        lineTo(x(19.2f), y(10.2f))
        lineTo(x(19.4f), y(10.2f))
        cubicTo(x(19.9f), y(10.2f), x(20.2f), y(10.6f), x(20.2f), y(11.1f))
        lineTo(x(20.2f), y(15.8f))
        cubicTo(x(20.2f), y(16.2f), x(19.9f), y(16.5f), x(19.4f), y(16.5f))
        lineTo(x(19.0f), y(16.5f))
        lineTo(x(19.0f), y(18.2f))
        cubicTo(x(19.0f), y(18.7f), x(18.6f), y(19.1f), x(18.1f), y(19.1f))
        lineTo(x(16.7f), y(19.1f))
        cubicTo(x(16.2f), y(19.1f), x(15.8f), y(18.7f), x(15.8f), y(18.2f))
        lineTo(x(15.8f), y(16.5f))
        lineTo(x(8.2f), y(16.5f))
        lineTo(x(8.2f), y(18.2f))
        cubicTo(x(8.2f), y(18.7f), x(7.8f), y(19.1f), x(7.3f), y(19.1f))
        lineTo(x(5.9f), y(19.1f))
        cubicTo(x(5.4f), y(19.1f), x(5.0f), y(18.7f), x(5.0f), y(18.2f))
        lineTo(x(5.0f), y(16.5f))
        lineTo(x(4.6f), y(16.5f))
        cubicTo(x(4.1f), y(16.5f), x(3.8f), y(16.2f), x(3.8f), y(15.8f))
        lineTo(x(3.8f), y(11.1f))
        cubicTo(x(3.8f), y(10.6f), x(4.1f), y(10.2f), x(4.6f), y(10.2f))
        lineTo(x(4.8f), y(10.2f))
        lineTo(x(6.1f), y(6.4f))
        cubicTo(x(6.4f), y(5.7f), x(7.0f), y(5.2f), x(7.8f), y(5.2f))
        close()
    }
}

/**
 * Builds the outer silhouette [Path] matching `ic_radar_map_truck.xml` on a 24x24 viewport scaled by [scale].
 */
internal fun buildRadarTruckOuterSilhouettePath(scale: Float): Path {
    fun x(v: Float) = v * scale
    fun y(v: Float) = v * scale
    return Path().apply {
        moveTo(x(6.0f), y(3.8f))
        lineTo(x(18.0f), y(3.8f))
        cubicTo(x(19.1f), y(3.8f), x(20.0f), y(4.7f), x(20.0f), y(5.8f))
        lineTo(x(20.0f), y(16.1f))
        cubicTo(x(20.0f), y(16.6f), x(19.6f), y(17.0f), x(19.1f), y(17.0f))
        lineTo(x(18.8f), y(17.0f))
        lineTo(x(18.8f), y(18.9f))
        cubicTo(x(18.8f), y(19.4f), x(18.4f), y(19.8f), x(17.9f), y(19.8f))
        lineTo(x(16.3f), y(19.8f))
        cubicTo(x(15.8f), y(19.8f), x(15.4f), y(19.4f), x(15.4f), y(18.9f))
        lineTo(x(15.4f), y(17.0f))
        lineTo(x(8.6f), y(17.0f))
        lineTo(x(8.6f), y(18.9f))
        cubicTo(x(8.6f), y(19.4f), x(8.2f), y(19.8f), x(7.7f), y(19.8f))
        lineTo(x(6.1f), y(19.8f))
        cubicTo(x(5.6f), y(19.8f), x(5.2f), y(19.4f), x(5.2f), y(18.9f))
        lineTo(x(5.2f), y(17.0f))
        lineTo(x(4.9f), y(17.0f))
        cubicTo(x(4.4f), y(17.0f), x(4.0f), y(16.6f), x(4.0f), y(16.1f))
        lineTo(x(4.0f), y(5.8f))
        cubicTo(x(4.0f), y(4.7f), x(4.9f), y(3.8f), x(6.0f), y(3.8f))
        close()
    }
}

/**
 * Renders a crisp, compact, centered car or truck silhouette [Bitmap] with a dual-contrast
 * outline (outer dark hairline + opaque white silhouette halo) and tinted `ic_radar_map_car` /
 * `ic_radar_map_truck` vector drawable (`REQ-RAD-006`, `ATT-8735`).
 */
fun renderRadarPassMarkerBitmap(
    context: Context,
    colorCategory: RadarPassColorCategory,
    isLargeVehicle: Boolean,
    sizeDp: Int
): Bitmap {
    val density = context.resources.displayMetrics.density.coerceAtLeast(1f)
    val sizePx = (sizeDp * density).toInt().coerceAtLeast(12)
    val bitmap = createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val scale = sizePx / 24f
    val outerPath = if (isLargeVehicle) {
        buildRadarTruckOuterSilhouettePath(scale)
    } else {
        buildRadarCarOuterSilhouettePath(scale)
    }

    // 1. Subtle outer dark contrast hairline so the white halo stays sharp on bright light-mode roads
    val darkOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL_AND_STROKE
        strokeWidth = (3.4f * scale).coerceAtLeast(2.0f)
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        color = TTColor.RadarMapIconOutlineDark.toArgb()
    }
    canvas.drawPath(outerPath, darkOutlinePaint)

    // 2. Crisp opaque white silhouette halo + bright cutout backing for windshield & headlights
    val whiteHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL_AND_STROKE
        strokeWidth = (2.0f * scale).coerceAtLeast(1.2f)
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        color = TTColor.RadarMapIconOutlineLight.toArgb()
    }
    canvas.drawPath(outerPath, whiteHaloPaint)

    // 3. Tinted compact car/truck vector drawable with transparent evenOdd windshield & headlight cutouts
    val drawableRes = if (isLargeVehicle) R.drawable.ic_radar_map_truck else R.drawable.ic_radar_map_car
    val vectorDrawable = ContextCompat.getDrawable(context, drawableRes)?.mutate()
    if (vectorDrawable != null) {
        vectorDrawable.setTint(resolveRadarPassColor(colorCategory).toArgb())
        vectorDrawable.setBounds(0, 0, sizePx, sizePx)
        vectorDrawable.draw(canvas)
    } else {
        // Fallback fill if drawable resolution is unavailable in a minimal test harness
        val fallbackFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = resolveRadarPassColor(colorCategory).toArgb()
        }
        canvas.drawPath(outerPath, fallbackFill)
    }

    return bitmap
}

/**
 * Bounded cache key for shared radar pass map [BitmapDescriptor]s (`O(1)` total bitmaps).
 */
data class RadarPassIconCacheKey(
    val colorCategory: RadarPassColorCategory,
    val isLargeVehicle: Boolean,
    val zoomBucket: Int
)

/**
 * Strictly bounded `O(1)` shared [BitmapDescriptor] cache for radar pass map markers (`REQ-RAD-006`, `ATT-8735`).
 *
 * Bounded by `4 color categories x 2 vehicle shapes x 5 zoom buckets = 40` max entries (`20` for V1 car-only
 * rides) regardless of whether a ride contains 10 or 10,000+ passes. Automatically invalidates on display
 * density changes.
 */
object RadarPassMarkerIconCache {

    /** Theoretical maximum cache cardinality (`4 x 2 x 5 = 40`). */
    val MAX_ENTRIES: Int = RadarPassColorCategory.entries.size * 2 * RADAR_ZOOM_BUCKET_COUNT

    private val cache = ConcurrentHashMap<RadarPassIconCacheKey, BitmapDescriptor>()
    @Volatile
    private var cachedDensityDpi: Int = -1

    /**
     * Returns the shared cached [BitmapDescriptor] for ([colorCategory], [isLargeVehicle], [zoomBucket]),
     * generating and caching it at most once per key for the active display density.
     */
    fun getOrCreate(
        context: Context,
        colorCategory: RadarPassColorCategory,
        isLargeVehicle: Boolean,
        zoomBucket: Int,
        descriptorFactory: (Bitmap) -> BitmapDescriptor? = ::saveBitmapDescriptorFactoryFromBitmap
    ): BitmapDescriptor? {
        val densityDpi = context.resources.displayMetrics.densityDpi
        if (cachedDensityDpi != densityDpi) {
            synchronized(this) {
                if (cachedDensityDpi != densityDpi) {
                    cache.clear()
                    cachedDensityDpi = densityDpi
                }
            }
        }
        val clampedBucket = zoomBucket.coerceIn(0, RADAR_ZOOM_BUCKET_COUNT - 1)
        val key = RadarPassIconCacheKey(colorCategory, isLargeVehicle, clampedBucket)
        cache[key]?.let { return it }

        val sizeDp = resolveRadarPassIconSizeDp(clampedBucket)
        val bitmap = renderRadarPassMarkerBitmap(context, colorCategory, isLargeVehicle, sizeDp)
        val descriptor = descriptorFactory(bitmap) ?: return null
        return cache.putIfAbsent(key, descriptor) ?: descriptor
    }

    @VisibleForTesting
    internal fun size(): Int = cache.size

    @VisibleForTesting
    internal fun clearForTesting() {
        cache.clear()
        cachedDensityDpi = -1
    }
}
