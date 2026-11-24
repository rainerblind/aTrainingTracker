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
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.LatLng
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit & Robolectric verification suite for aftermarket radar map pass icons (`REQ-RAD-006`,
 * `REQ-TRK-073.5`, `TST-RAD-006`, `ATT-8735`):
 * - Priority color resolution (Brown close pass -> Red -> Amber -> Green) & centralized [TTColor] tokens
 * - Zoom-out suppression (`currentZoom < 14.5f` -> `0` markers) & 5-bucket half-road-width sizing
 * - 1,500-pass viewport culling, antimeridian bounds safety & hysteresis stability
 * - Strictly bounded `O(1)` [RadarPassMarkerIconCache] reuse across 1,500+ passes
 * - Dual-contrast vector silhouette rendering (`ic_radar_map_car`, `ic_radar_map_truck`) &
 *   `Rule 8.5` visual verification PNG preview export
 */
// Linked test specs: TST-RAD-006
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "xxhdpi")
class RadarPassMarkerMathTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        RadarPassMarkerIconCache.clearForTesting()
    }

    @After
    fun tearDown() {
        RadarPassMarkerIconCache.clearForTesting()
    }

    @Test
    fun resolveRadarPassColorCategory_enforcesStrictPriorityAndThresholdBoundaries() {
        // 1. Close pass (Brown) takes strict highest priority over all speed tiers
        assertEquals(
            RadarPassColorCategory.CLOSE_PASS,
            resolveRadarPassColorCategory(approachSpeedMps = 3.0, passingDistanceM = 1.49, closePassThresholdM = 1.5f)
        )
        assertEquals(
            RadarPassColorCategory.CLOSE_PASS,
            resolveRadarPassColorCategory(approachSpeedMps = 10.0, passingDistanceM = 1.2, closePassThresholdM = 1.5f)
        )
        assertEquals(
            RadarPassColorCategory.CLOSE_PASS,
            resolveRadarPassColorCategory(approachSpeedMps = 25.0, passingDistanceM = 0.8, closePassThresholdM = 1.5f)
        )

        // Exact threshold equality (1.5 == 1.5) is NOT a close pass -> falls through to speed
        assertEquals(
            RadarPassColorCategory.HIGH_SPEED,
            resolveRadarPassColorCategory(approachSpeedMps = 15.0, passingDistanceM = 1.5, closePassThresholdM = 1.5f)
        )

        // 2. High closing speed (>= 15.0 m/s = 54 km/h) -> Red
        assertEquals(
            RadarPassColorCategory.HIGH_SPEED,
            resolveRadarPassColorCategory(approachSpeedMps = 15.0, passingDistanceM = null, closePassThresholdM = 1.5f)
        )
        assertEquals(
            RadarPassColorCategory.HIGH_SPEED,
            resolveRadarPassColorCategory(approachSpeedMps = 28.5, passingDistanceM = 2.4, closePassThresholdM = 1.5f)
        )

        // 3. Moderate closing speed ([7.0, 15.0) m/s) -> Orange / Amber
        assertEquals(
            RadarPassColorCategory.MODERATE_SPEED,
            resolveRadarPassColorCategory(approachSpeedMps = 7.0, passingDistanceM = null, closePassThresholdM = 1.5f)
        )
        assertEquals(
            RadarPassColorCategory.MODERATE_SPEED,
            resolveRadarPassColorCategory(approachSpeedMps = 14.99, passingDistanceM = 1.8, closePassThresholdM = 1.5f)
        )

        // 4. Low closing speed (< 7.0 m/s or null) -> Green
        assertEquals(
            RadarPassColorCategory.LOW_SPEED,
            resolveRadarPassColorCategory(approachSpeedMps = 6.99, passingDistanceM = null, closePassThresholdM = 1.5f)
        )
        assertEquals(
            RadarPassColorCategory.LOW_SPEED,
            resolveRadarPassColorCategory(approachSpeedMps = 0.0, passingDistanceM = 2.0, closePassThresholdM = 1.5f)
        )
        assertEquals(
            RadarPassColorCategory.LOW_SPEED,
            resolveRadarPassColorCategory(approachSpeedMps = null, passingDistanceM = null, closePassThresholdM = 1.5f)
        )

        // Centralized TTColor mapping & severity z-index hierarchy
        assertEquals(TTColor.RadarClosePassBrown, resolveRadarPassColor(RadarPassColorCategory.CLOSE_PASS))
        assertEquals(TTColor.RadarThreatRed, resolveRadarPassColor(RadarPassColorCategory.HIGH_SPEED))
        assertEquals(TTColor.RadarThreatAmber, resolveRadarPassColor(RadarPassColorCategory.MODERATE_SPEED))
        assertEquals(TTColor.RadarClearGreen, resolveRadarPassColor(RadarPassColorCategory.LOW_SPEED))

        assertTrue(
            resolveRadarPassZIndex(RadarPassColorCategory.LOW_SPEED) <
                resolveRadarPassZIndex(RadarPassColorCategory.MODERATE_SPEED)
        )
        assertTrue(
            resolveRadarPassZIndex(RadarPassColorCategory.MODERATE_SPEED) <
                resolveRadarPassZIndex(RadarPassColorCategory.HIGH_SPEED)
        )
        assertTrue(
            resolveRadarPassZIndex(RadarPassColorCategory.HIGH_SPEED) <
                resolveRadarPassZIndex(RadarPassColorCategory.CLOSE_PASS)
        )
    }

    @Test
    fun resolveRadarPassZoomBucket_hidesBelowThresholdAndScalesMonotonicallyHalfRoadWidth() {
        // Zoomed-out overview levels must return null (0 markers)
        listOf(-1f, 0f, 10f, 12f, 14.0f, 14.49f, Float.NaN, Float.NEGATIVE_INFINITY).forEach { z ->
            assertNull("Zoom $z must hide radar car icons", resolveRadarPassZoomBucket(z))
        }

        // Quantized buckets 0..4 across street-level zooms
        assertEquals(0, resolveRadarPassZoomBucket(14.5f))
        assertEquals(0, resolveRadarPassZoomBucket(15.49f))
        assertEquals(1, resolveRadarPassZoomBucket(15.5f))
        assertEquals(2, resolveRadarPassZoomBucket(16.5f))
        assertEquals(3, resolveRadarPassZoomBucket(17.5f))
        assertEquals(4, resolveRadarPassZoomBucket(18.5f))
        assertEquals(4, resolveRadarPassZoomBucket(21.0f))

        // Sizes grow monotonically and remain compact (<= 18 dp)
        val sizes = (0 until RADAR_ZOOM_BUCKET_COUNT).map { resolveRadarPassIconSizeDp(it) }
        for (i in 1 until sizes.size) {
            assertTrue("Bucket $i size (${sizes[i]}dp) must exceed bucket ${i - 1} (${sizes[i - 1]}dp)", sizes[i] > sizes[i - 1])
        }
        assertTrue("Smallest bucket must be legible (>= 8dp)", sizes.first() >= 8)
        assertTrue("Largest bucket must stay compact (<= 18dp)", sizes.last() <= 18)
    }

    @Test
    fun cullRadarPassMarkers_1500SyntheticPasses_emitsZeroWhenZoomedOutAndCullsToViewportWhenZoomedIn() {
        val passes = buildSyntheticRadarPasses(1500)

        // Zoomed-out overview levels emit strictly 0 markers
        assertEquals(0, cullRadarPassMarkers(passes, currentZoom = 12.0f, visibleBounds = null).size)
        assertEquals(0, cullRadarPassMarkers(passes, currentZoom = 14.0f, visibleBounds = null).size)
        assertEquals(0, cullRadarPassMarkers(passes, currentZoom = 14.49f, visibleBounds = null).size)

        // Zoomed-in with null pre-layout bounds enforces defensive cap (never dumps 1,500 unculled markers)
        assertEquals(0, cullRadarPassMarkers(passes, currentZoom = 16.5f, visibleBounds = null).size)

        // Zoomed-in street viewport (~1.5 km slice of 60 km route) culls > 90% of passes
        val streetBounds = MapViewportBounds(
            south = 48.200,
            west = 9.000,
            north = 48.215,
            east = 9.030
        )
        val culled = cullRadarPassMarkers(passes, currentZoom = 16.5f, visibleBounds = streetBounds, marginFraction = 0.25)
        assertTrue("Zoomed-in viewport must include passes on visible street", culled.isNotEmpty())
        assertTrue(
            "Viewport culling must drastically reduce 1,500 passes (actual=${culled.size})",
            culled.size < 120
        )
        val expanded = streetBounds.expandBy(0.25)
        assertTrue("Every culled marker must lie within expanded viewport", culled.all { expanded.contains(it.position) })

        // Antimeridian crossing viewport works cleanly (contains & containsBounds regression for Stage 6 Bounce 1)
        val antimeridian = MapViewportBounds(south = -10.0, west = 170.0, north = 10.0, east = -170.0)
        assertTrue(antimeridian.contains(LatLng(0.0, 179.8)))
        assertTrue(antimeridian.contains(LatLng(0.0, -179.8)))
        assertFalse(antimeridian.contains(LatLng(0.0, 0.0)))

        // Crossing bounds contains nested crossing bounds and single-side non-crossing bounds
        assertTrue(antimeridian.containsBounds(MapViewportBounds(-5.0, 175.0, 5.0, -175.0)))
        assertTrue(antimeridian.containsBounds(MapViewportBounds(-5.0, 172.0, 5.0, 178.0)))
        assertTrue(antimeridian.containsBounds(MapViewportBounds(-5.0, -178.0, 5.0, -172.0)))
        // Crossing bounds MUST reject opposite 350-degree non-crossing span across 0 deg longitude
        assertFalse(antimeridian.containsBounds(MapViewportBounds(-5.0, -175.0, 5.0, 175.0)))
        // Crossing bounds rejects wider crossing bounds extending outside [170, -170]
        assertFalse(antimeridian.containsBounds(MapViewportBounds(-5.0, 165.0, 5.0, -175.0)))
        // Non-crossing bounds rejects crossing bounds (unless full 360-deg globe)
        val nonCrossing = MapViewportBounds(south = -10.0, west = -175.0, north = 10.0, east = 175.0)
        assertFalse(nonCrossing.containsBounds(antimeridian))
        val fullGlobe = MapViewportBounds(south = -10.0, west = -180.0, north = 10.0, east = 180.0)
        assertTrue(fullGlobe.containsBounds(antimeridian))
    }

    @Test
    fun resolveBufferedViewportBounds_preservesIdentityOnMinorPanAndUpdatesOnEdgeOrZoomIn() {
        val initial = MapViewportBounds(48.10, 9.00, 48.12, 9.02)
        val buffered = resolveBufferedViewportBounds(null, initial, marginFraction = 0.25)
        assertNotNull(buffered)
        assertTrue(buffered!!.containsBounds(initial))

        // Small pan inside margin returns exact same instance (Compose Strong Skipping stays clean)
        val minorPan = MapViewportBounds(48.102, 9.002, 48.122, 9.022)
        val afterMinorPan = resolveBufferedViewportBounds(buffered, minorPan, marginFraction = 0.25)
        assertSame("Minor pan inside buffer must preserve bounds instance", buffered, afterMinorPan)

        // Pan beyond buffer refreshes bounds
        val farPan = MapViewportBounds(48.20, 9.10, 48.22, 9.12)
        val afterFarPan = resolveBufferedViewportBounds(buffered, farPan, marginFraction = 0.25)
        assertTrue(afterFarPan!!.containsBounds(farPan))
        assertFalse(afterFarPan == buffered)

        // Deep zoom-in (> 2.5x smaller span) tightens culling bounds immediately
        val zoomedIn = MapViewportBounds(48.108, 9.008, 48.112, 9.012)
        val afterZoomIn = resolveBufferedViewportBounds(buffered, zoomedIn, marginFraction = 0.25)
        assertTrue("Zoom-in must tighten oversized buffered bounds", afterZoomIn!!.latSpan < buffered.latSpan * 0.5)
    }

    @Test
    fun radarPassMarkerIconCache_1500PassesAcrossAllZooms_strictlyBoundedO1WithInstanceReuse() {
        val allocations = AtomicInteger(0)
        val factory: (Bitmap) -> BitmapDescriptor = {
            allocations.incrementAndGet()
            mockk(relaxed = true)
        }

        // Phase 1: 1,500 V1 car-only passes across all 4 color categories & 5 zoom buckets -> <= 20 bitmaps
        val firstCarDescriptor = RadarPassMarkerIconCache.getOrCreate(
            context, RadarPassColorCategory.LOW_SPEED, isLargeVehicle = false, zoomBucket = 2, descriptorFactory = factory
        )
        assertNotNull(firstCarDescriptor)

        for (i in 0 until 1500) {
            val category = RadarPassColorCategory.entries[i % RadarPassColorCategory.entries.size]
            val bucket = i % RADAR_ZOOM_BUCKET_COUNT
            val descriptor = RadarPassMarkerIconCache.getOrCreate(
                context, category, isLargeVehicle = false, zoomBucket = bucket, descriptorFactory = factory
            )
            assertNotNull(descriptor)
            if (category == RadarPassColorCategory.LOW_SPEED && bucket == 2) {
                assertSame("Cache must reuse identical BitmapDescriptor instance", firstCarDescriptor, descriptor)
            }
        }
        assertEquals("V1 car-only cache must allocate at most 4 * 5 = 20 bitmaps", 20, allocations.get())
        assertEquals(20, RadarPassMarkerIconCache.size())

        // Phase 2: Add V2 trucks across all colors & zoom buckets -> <= 40 bitmaps total (MAX_ENTRIES)
        for (i in 0 until 500) {
            val category = RadarPassColorCategory.entries[i % RadarPassColorCategory.entries.size]
            val bucket = i % RADAR_ZOOM_BUCKET_COUNT
            val descriptor = RadarPassMarkerIconCache.getOrCreate(
                context, category, isLargeVehicle = true, zoomBucket = bucket, descriptorFactory = factory
            )
            assertNotNull(descriptor)
        }
        assertEquals(RadarPassMarkerIconCache.MAX_ENTRIES, allocations.get())
        assertEquals(RadarPassMarkerIconCache.MAX_ENTRIES, RadarPassMarkerIconCache.size())
        assertTrue("Total cache ceiling must stay <= 40", RadarPassMarkerIconCache.size() <= 40)
    }

    @Test
    fun resolveStandardMarkerIdentityKey_preventsDuplicateTitleCollisionAndKeepsDragStable() {
        val m1 = LocationMarker(LatLng(48.137, 11.576), R.drawable.ic_place, title = "Vehicle Pass (36.0 km/h)", draggable = false)
        val m2 = LocationMarker(LatLng(48.138, 11.577), R.drawable.ic_place, title = "Vehicle Pass (36.0 km/h)", draggable = false)
        val m3SamePos = LocationMarker(LatLng(48.137, 11.576), R.drawable.ic_place, title = "Vehicle Pass (36.0 km/h)", draggable = false)

        val k1 = resolveStandardMarkerIdentityKey(m1, 0)
        val k2 = resolveStandardMarkerIdentityKey(m2, 1)
        val k3 = resolveStandardMarkerIdentityKey(m3SamePos, 2)

        assertFalse("Markers with identical title at different positions must have distinct keys", k1 == k2)
        assertFalse("Markers with identical title and position must still have distinct keys by index", k1 == k3)

        // Draggable marker preserves identity key across position updates during drag
        val dragBefore = LocationMarker(LatLng(48.137, 11.576), R.drawable.control_start, title = "Start", draggable = true)
        val dragAfter = dragBefore.copy(position = LatLng(48.140, 11.580))
        assertEquals(
            "Draggable marker identity key must stay stable across drag position changes",
            resolveStandardMarkerIdentityKey(dragBefore, 0),
            resolveStandardMarkerIdentityKey(dragAfter, 0)
        )
    }

    @Test
    fun renderRadarPassMarkerBitmap_allColorsAndVehicleShapes_rendersCrispIconsAndExportsVisualPreviewPng() {
        val density = context.resources.displayMetrics.density
        assertTrue("xxhdpi Robolectric test density expected >= 2.0", density >= 2f)

        val carBitmapsByCategory = mutableMapOf<RadarPassColorCategory, Bitmap>()
        val truckBitmapsByCategory = mutableMapOf<RadarPassColorCategory, Bitmap>()

        for (category in RadarPassColorCategory.entries) {
            val targetColor = resolveRadarPassColor(category).toArgb()
            val carBmp = renderRadarPassMarkerBitmap(context, category, isLargeVehicle = false, sizeDp = 12)
            val truckBmp = renderRadarPassMarkerBitmap(context, category, isLargeVehicle = true, sizeDp = 12)

            assertEquals((12 * density).toInt(), carBmp.width)
            assertEquals((12 * density).toInt(), carBmp.height)
            assertEquals((12 * density).toInt(), truckBmp.width)
            assertEquals((12 * density).toInt(), truckBmp.height)

            assertTrue("Car bitmap ($category) must contain colored body pixels", countMatchingPixels(carBmp, targetColor) > 30)
            assertTrue("Truck bitmap ($category) must contain colored body pixels", countMatchingPixels(truckBmp, targetColor) > 40)
            assertTrue("Car bitmap ($category) must contain white halo/windshield pixels", countMatchingPixels(carBmp, TTColor.RadarMapIconOutlineLight.toArgb()) > 25)
            assertTrue("Car and truck shapes must differ visually", countDifferingPixels(carBmp, truckBmp) > 25)

            carBitmapsByCategory[category] = carBmp
            truckBitmapsByCategory[category] = truckBmp
        }

        // Export crisp Rule 8.5 visual self-inspection PNG comparing Light & Dark map tiles + zoom scaling
        val previewPng = generateVisualVerificationSheet(context, density)
        assertTrue("Visual preview PNG must exist and be non-empty", previewPng.exists() && previewPng.length() > 10_000L)
    }

    private fun buildSyntheticRadarPasses(count: Int): List<LocationMarker> {
        val list = ArrayList<LocationMarker>(count)
        for (i in 0 until count) {
            // Spread 1,500 passes smoothly across ~60 km (~0.54 deg lat)
            val lat = 48.000 + (i.toDouble() / count) * 0.54
            val lng = 9.000 + (i.toDouble() / count) * 0.08
            val category = RadarPassColorCategory.entries[i % RadarPassColorCategory.entries.size]
            val isLarge = i % 10 == 0
            list.add(
                LocationMarker(
                    position = LatLng(lat, lng),
                    iconResId = if (isLarge) R.drawable.ic_radar_map_truck else R.drawable.ic_radar_map_car,
                    title = "Vehicle Pass (36.0 km/h)",
                    anchor = Offset(0.5f, 0.5f),
                    radarPass = RadarPassMarkerData(
                        passIndex = i + 1,
                        colorCategory = category,
                        isLargeVehicle = isLarge,
                        approachSpeedMps = 10.0
                    )
                )
            )
        }
        return list
    }

    private fun countMatchingPixels(bitmap: Bitmap, targetArgb: Int, tolerance: Int = 24): Int {
        val tr = (targetArgb shr 16) and 0xFF
        val tg = (targetArgb shr 8) and 0xFF
        val tb = targetArgb and 0xFF
        var count = 0
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val p = bitmap.getPixel(x, y)
                val a = (p ushr 24) and 0xFF
                if (a < 200) continue
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                if (kotlin.math.abs(r - tr) <= tolerance &&
                    kotlin.math.abs(g - tg) <= tolerance &&
                    kotlin.math.abs(b - tb) <= tolerance
                ) {
                    count++
                }
            }
        }
        return count
    }

    private fun countDifferingPixels(a: Bitmap, b: Bitmap): Int {
        var diff = 0
        for (y in 0 until minOf(a.height, b.height)) {
            for (x in 0 until minOf(a.width, b.width)) {
                if (a.getPixel(x, y) != b.getPixel(x, y)) diff++
            }
        }
        return diff
    }

    /**
     * Generates a clean multi-panel visual verification PNG (`ATT-8735_radar_map_icons_preview.png`)
     * for `Rule 8.5` inspection via `read_file`, showing:
     * 1. Enlarged 4x car & truck silhouettes across all 4 color states on Light & Dark map tiles.
     * 2. True-scale (1:1 xxhdpi) zoom progression (`Zoom 13 [Hidden]` through `Zoom 19 [18dp]`)
     *    over proportionally scaled Google Maps roads + route polylines demonstrating `<= half road width`.
     */
    private fun generateVisualVerificationSheet(context: Context, density: Float): File {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "app").exists() && dir.parentFile != null) {
            dir = dir.parentFile!!
        }
        val outFile = File(dir, "docs/engineering/walkthroughs/img/ATT-8735_radar_map_icons_preview.png")
        outFile.parentFile?.mkdirs()

        val width = 1280
        val height = 1040
        val sheet = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(0xFFF6F7F9.toInt())

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF1B1B1F.toInt()
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF44474F.toInt()
            textSize = 18f
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF1B1B1F.toInt()
            textSize = 17f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val darkLabelPaint = Paint(labelPaint).apply { color = 0xFFE3E2E6.toInt() }

        canvas.drawText("ATT-8735: Radar Map Car & Truck Icons (4 Color States + Zoom Scaling)", 36f, 48f, titlePaint)
        canvas.drawText("Priority: 1. Brown Close Pass (< threshold)  |  2. Red >= 54 km/h  |  3. Amber 25-54 km/h  |  4. Green < 25 km/h", 36f, 78f, subPaint)

        val categories = listOf(
            RadarPassColorCategory.LOW_SPEED to "Green (< 7 m/s)",
            RadarPassColorCategory.MODERATE_SPEED to "Amber (7-15 m/s)",
            RadarPassColorCategory.HIGH_SPEED to "Red (>= 15 m/s)",
            RadarPassColorCategory.CLOSE_PASS to "Brown (Close Pass)"
        )

        // Panel 1A: Light Map Tile Enlarged Gallery (Left)
        drawMapTileBackdrop(canvas, RectF(36f, 104f, 624f, 470f), isDark = false)
        canvas.drawText("Light Map Tile — Car (top) & Truck (bottom) @ 3x Preview", 56f, 134f, subPaint)
        categories.forEachIndexed { idx, (cat, label) ->
            val cx = 115f + idx * 142f
            val carLarge = renderRadarPassMarkerBitmap(context, cat, isLargeVehicle = false, sizeDp = 28)
            val truckLarge = renderRadarPassMarkerBitmap(context, cat, isLargeVehicle = true, sizeDp = 28)
            canvas.drawBitmap(carLarge, null, Rect(cx.toInt() - 42, 162, cx.toInt() + 42, 246), null)
            canvas.drawBitmap(truckLarge, null, Rect(cx.toInt() - 42, 290, cx.toInt() + 42, 374), null)
            canvas.drawText(label, cx, 434f, labelPaint)
        }

        // Panel 1B: Dark Map Tile Enlarged Gallery (Right)
        drawMapTileBackdrop(canvas, RectF(656f, 104f, 1244f, 470f), isDark = true)
        val darkSub = Paint(subPaint).apply { color = 0xFFC4C6D0.toInt() }
        canvas.drawText("Dark Map Tile — Car (top) & Truck (bottom) @ 3x Preview", 676f, 134f, darkSub)
        categories.forEachIndexed { idx, (cat, label) ->
            val cx = 735f + idx * 142f
            val carLarge = renderRadarPassMarkerBitmap(context, cat, isLargeVehicle = false, sizeDp = 28)
            val truckLarge = renderRadarPassMarkerBitmap(context, cat, isLargeVehicle = true, sizeDp = 28)
            canvas.drawBitmap(carLarge, null, Rect(cx.toInt() - 42, 162, cx.toInt() + 42, 246), null)
            canvas.drawBitmap(truckLarge, null, Rect(cx.toInt() - 42, 290, cx.toInt() + 42, 374), null)
            canvas.drawText(label, cx, 434f, darkLabelPaint)
        }

        // Panel 2: True-to-Scale Zoom Progression vs Google Maps Road Width (Light & Dark)
        canvas.drawText("True-to-Scale (xxhdpi 1:1) Zoom Buckets vs. Rendered Street Width (Icon <= Half Road Width)", 36f, 520f, titlePaint)
        val zoomSamples = listOf(
            Triple(13.0f, 10f, "Zoom 13\nHidden (0)"),
            Triple(15.0f, 16f, "Zoom 15\n8 dp / ~16dp rd"),
            Triple(16.0f, 20f, "Zoom 16\n10 dp / ~20dp rd"),
            Triple(17.0f, 26f, "Zoom 17\n12 dp / ~26dp rd"),
            Triple(18.0f, 34f, "Zoom 18\n15 dp / ~34dp rd"),
            Triple(19.0f, 44f, "Zoom 19\n18 dp / ~44dp rd")
        )

        val colW = 194f
        zoomSamples.forEachIndexed { idx, (zoom, roadDp, caption) ->
            val left = 36f + idx * (colW + 8f)
            val lightRect = RectF(left, 542f, left + colW, 742f)
            val darkRect = RectF(left, 756f, left + colW, 956f)
            drawZoomRoadStrip(canvas, context, lightRect, zoom, roadDp * density, isDark = false)
            drawZoomRoadStrip(canvas, context, darkRect, zoom, roadDp * density, isDark = true)
            val lines = caption.split("\n")
            canvas.drawText(lines[0], left + colW / 2f, 990f, labelPaint)
            canvas.drawText(lines[1], left + colW / 2f, 1014f, subPaint.apply { textAlign = Paint.Align.CENTER })
            subPaint.textAlign = Paint.Align.LEFT
        }

        FileOutputStream(outFile).use { fos ->
            sheet.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
        return outFile
    }

    private fun drawMapTileBackdrop(canvas: Canvas, rect: RectF, isDark: Boolean) {
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isDark) 0xFF1B2028.toInt() else 0xFFEAECEF.toInt()
        }
        canvas.drawRoundRect(rect, 16f, 16f, bg)

        // Two horizontal simulated roads + route polyline right behind icon rows
        val roadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isDark) 0xFF343B46.toInt() else 0xFFFFFFFF.toInt()
            strokeWidth = 64f
        }
        val roadCasingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isDark) 0xFF232830.toInt() else 0xFFD0D5DD.toInt()
            strokeWidth = 68f
        }
        val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isDark) TTColor.DarkMapTrackCyan.toArgb() else TTColor.RouteSelected.toArgb()
            strokeWidth = 10f
        }

        listOf(204f, 332f).forEach { y ->
            canvas.drawLine(rect.left + 12f, y, rect.right - 12f, y, roadCasingPaint)
            canvas.drawLine(rect.left + 12f, y, rect.right - 12f, y, roadPaint)
            canvas.drawLine(rect.left + 12f, y, rect.right - 12f, y, routePaint)
        }
    }

    private fun drawZoomRoadStrip(
        canvas: Canvas,
        context: Context,
        rect: RectF,
        zoom: Float,
        roadWidthPx: Float,
        isDark: Boolean
    ) {
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isDark) 0xFF181C22.toInt() else 0xFFECEFF1.toInt()
        }
        canvas.drawRoundRect(rect, 12f, 12f, bg)

        val cy = rect.centerY()
        val casingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isDark) 0xFF262B34.toInt() else 0xFFCFD8DC.toInt()
            strokeWidth = roadWidthPx + 4f
        }
        val roadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isDark) 0xFF374151.toInt() else 0xFFFFFFFF.toInt()
            strokeWidth = roadWidthPx
        }
        val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isDark) TTColor.DarkMapTrackCyan.toArgb() else TTColor.RouteSelected.toArgb()
            strokeWidth = 8f
        }
        canvas.drawLine(rect.left + 6f, cy, rect.right - 6f, cy, casingPaint)
        canvas.drawLine(rect.left + 6f, cy, rect.right - 6f, cy, roadPaint)
        canvas.drawLine(rect.left + 6f, cy, rect.right - 6f, cy, routePaint)

        val bucket = resolveRadarPassZoomBucket(zoom)
        if (bucket == null) {
            val hiddenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (isDark) 0xFF9E9E9E.toInt() else 0xFF616161.toInt()
                textSize = 15f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("(0 radar icons)", rect.centerX(), rect.bottom - 16f, hiddenPaint)
            return
        }

        val sizeDp = resolveRadarPassIconSizeDp(bucket)
        val specs = listOf(
            RadarPassColorCategory.LOW_SPEED to false,
            RadarPassColorCategory.MODERATE_SPEED to false,
            RadarPassColorCategory.HIGH_SPEED to false,
            RadarPassColorCategory.CLOSE_PASS to true
        )
        val spacing = (rect.width() - 32f) / specs.size
        specs.forEachIndexed { idx, (cat, isTruck) ->
            val bmp = renderRadarPassMarkerBitmap(context, cat, isLargeVehicle = isTruck, sizeDp = sizeDp)
            val cx = rect.left + 16f + spacing * (idx + 0.5f)
            canvas.drawBitmap(bmp, cx - bmp.width / 2f, cy - bmp.height / 2f, null)
        }
    }
}
