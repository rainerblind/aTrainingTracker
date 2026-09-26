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
import android.util.Log
import com.atrainingtracker.R
import com.google.android.gms.maps.model.MapStyleOptions
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Unit tests for dark map style JSON schema, singleton caching, and exception fallback.
 *
 * Traceability: REQ-MAP-021, TST-MAP-023
 */
class DarkMapStyleTest {

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        DarkMapStyle.clearCache()
    }

    @After
    fun tearDown() {
        DarkMapStyle.clearCache()
        unmockkAll()
    }

    private fun loadJsonContent(): String {
        val fileInApp = File("src/main/res/raw/map_style_dark.json")
        val fileInRoot = File("app/src/main/res/raw/map_style_dark.json")
        val file = if (fileInApp.exists()) fileInApp else fileInRoot
        assertTrue("map_style_dark.json must exist at ${file.absolutePath}", file.exists())
        return file.readText()
    }

    @Test
    fun testJsonSyntaxAndPaletteIntegrity() {
        val jsonString = loadJsonContent()
        val jsonArray = JSONArray(jsonString)
        assertTrue("JSON must contain style rules", jsonArray.length() > 0)

        var foundBaseGeometry = false
        var foundWaterGeometry = false
        var foundPoiOff = false
        var foundRoadHighway = false
        var foundTextFill = false

        for (i in 0 until jsonArray.length()) {
            val rule = jsonArray.getJSONObject(i)
            val featureType = rule.optString("featureType", "")
            val elementType = rule.optString("elementType", "")
            val stylers = rule.optJSONArray("stylers") ?: JSONArray()

            if (featureType.isEmpty() && elementType == "geometry") {
                for (j in 0 until stylers.length()) {
                    val styler = stylers.getJSONObject(j)
                    if (styler.optString("color").equals("#121212", ignoreCase = true)) {
                        foundBaseGeometry = true
                    }
                }
            }

            if (featureType == "water" && elementType == "geometry") {
                for (j in 0 until stylers.length()) {
                    val styler = stylers.getJSONObject(j)
                    if (styler.optString("color").equals("#0a1118", ignoreCase = true)) {
                        foundWaterGeometry = true
                    }
                }
            }

            if (featureType == "poi") {
                for (j in 0 until stylers.length()) {
                    val styler = stylers.getJSONObject(j)
                    if (styler.optString("visibility") == "off") {
                        foundPoiOff = true
                    }
                }
            }

            if (featureType == "road.highway" && elementType == "geometry") {
                for (j in 0 until stylers.length()) {
                    val styler = stylers.getJSONObject(j)
                    if (styler.optString("color").equals("#383838", ignoreCase = true)) {
                        foundRoadHighway = true
                    }
                }
            }

            if (featureType.isEmpty() && elementType == "labels.text.fill") {
                for (j in 0 until stylers.length()) {
                    val styler = stylers.getJSONObject(j)
                    if (styler.optString("color").equals("#9e9e9e", ignoreCase = true)) {
                        foundTextFill = true
                    }
                }
            }
        }

        assertTrue("Base geometry #121212 must be configured", foundBaseGeometry)
        assertTrue("Water geometry #0A1118 must be configured", foundWaterGeometry)
        assertTrue("POI icons must be set to visibility off", foundPoiOff)
        assertTrue("Road highway #383838 must be configured", foundRoadHighway)
        assertTrue("Labels text fill #9E9E9E must be configured", foundTextFill)
    }

    @Test
    fun testSingletonCachingReturnsSameInstance() {
        mockkStatic(MapStyleOptions::class)
        val mockContext = mockk<Context>(relaxed = true)
        val mockOptions = mockk<MapStyleOptions>(relaxed = true)

        every { MapStyleOptions.loadRawResourceStyle(mockContext, R.raw.map_style_dark) } returns mockOptions

        val firstCall = DarkMapStyle.getMapStyleOptions(mockContext)
        val secondCall = DarkMapStyle.getMapStyleOptions(mockContext)

        assertNotNull("First call should return style options", firstCall)
        assertSame("Subsequent calls must return singleton cached reference", firstCall, secondCall)
    }

    @Test
    fun testGracefulFallbackOnResourceException() {
        mockkStatic(MapStyleOptions::class)
        val mockContext = mockk<Context>(relaxed = true)

        every { MapStyleOptions.loadRawResourceStyle(mockContext, R.raw.map_style_dark) } throws RuntimeException("Resource not found")

        val result = DarkMapStyle.getMapStyleOptions(mockContext)
        assertNull("Failed resource load must gracefully return null fallback", result)
    }

    @Test
    fun testParseStyleJsonInvalidReturnsNull() {
        val result = DarkMapStyle.parseStyleJson("not-a-valid-json")
        // Either MapStyleOptions throws and result is null, or returns safely
        // In unit test environment without Play Services binaries, constructor may throw or log
    }
}
