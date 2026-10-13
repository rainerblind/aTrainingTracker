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

package com.atrainingtracker.trainingtracker.ui.tracking

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural contract and behavior verification for cockpit sensor field styling
 * and grid spacing prototyping variants (REQ-UI-258, TST-UI-217, ATT-2058).
 */
class SensorFieldStyleContractTest {

    private fun findFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
            File("../$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("File not found in candidates: $relativePath")
    }

    @Test
    fun testDefaultParameters_preserveProductionBaseline() {
        val baseline = SensorFieldStyle.Variant0_Baseline
        assertEquals("Baseline shape must be RectangleShape", RectangleShape, baseline.shape)
        assertEquals("Baseline gridSpacing must be 0.dp", 0.dp, baseline.gridSpacing)
        assertEquals("Baseline elevation must be 0.dp", 0.dp, baseline.defaultElevation)
        assertEquals("Baseline cornerRadius must be 0.dp", 0.dp, baseline.cornerRadius)
        assertEquals("Baseline borderThickness must be 1.0.dp", 1.0.dp, baseline.borderThickness)
        assertEquals("Baseline borderContrast must be 0.0f", 0.0f, baseline.borderContrast, 0.001f)
    }

    @Test
    fun testVariant1_outlinedTilesMetrics() {
        val v1 = SensorFieldStyle.Variant1_OutlinedTiles
        assertEquals("Variant 1 gridSpacing must be 4.dp", 4.dp, v1.gridSpacing)
        assertEquals("Variant 1 elevation must be 0.dp", 0.dp, v1.defaultElevation)
        assertEquals("Variant 1 cornerRadius must be 8.dp", 8.dp, v1.cornerRadius)
        assertEquals("Variant 1 borderThickness must be 2.0.dp", 2.0.dp, v1.borderThickness)
        assertEquals("Variant 1 borderContrast must be 0.5f", 0.5f, v1.borderContrast, 0.001f)
    }

    @Test
    fun testVariant2_elevatedCardsMetrics() {
        val v2 = SensorFieldStyle.Variant2_ElevatedCards
        assertEquals("Variant 2 gridSpacing must be 6.dp", 6.dp, v2.gridSpacing)
        assertEquals("Variant 2 elevation must be 3.dp", 3.dp, v2.defaultElevation)
        assertEquals("Variant 2 cornerRadius must be 10.dp", 10.dp, v2.cornerRadius)
        assertEquals("Variant 2 borderThickness must be 0.0.dp", 0.0.dp, v2.borderThickness)
        assertEquals("Variant 2 borderContrast must be 0.0f", 0.0f, v2.borderContrast, 0.001f)
    }

    @Test
    fun testVariant3_capsuleMetrics() {
        val v3 = SensorFieldStyle.Variant3_Capsules
        assertEquals("Variant 3 gridSpacing must be 8.dp", 8.dp, v3.gridSpacing)
        assertEquals("Variant 3 elevation must be 1.dp", 1.dp, v3.defaultElevation)
        assertEquals("Variant 3 cornerRadius must be 16.dp", 16.dp, v3.cornerRadius)
        assertEquals("Variant 3 borderThickness must be 1.5.dp", 1.5.dp, v3.borderThickness)
        assertEquals("Variant 3 borderContrast must be 0.7f", 0.7f, v3.borderContrast, 0.001f)
    }

    @Test
    fun testResolveShape_mapsZeroToRectangleAndPositiveToRoundedCorner() {
        assertEquals("0.dp must resolve to RectangleShape", RectangleShape, SensorFieldStyle.resolveShape(0.dp))
        assertEquals("negative dp must resolve to RectangleShape", RectangleShape, SensorFieldStyle.resolveShape((-2).dp))
        val rounded = SensorFieldStyle.resolveShape(8.dp)
        assertTrue("8.dp must resolve to RoundedCornerShape", rounded is RoundedCornerShape)
    }

    @Test
    fun testResolveBorder_borderlessWhenZeroOrNegative() {
        assertNull("0.dp border thickness must return null", SensorFieldStyle.resolveBorder(0.dp, 0.5f, isDarkTheme = true))
        assertNull("negative border thickness must return null", SensorFieldStyle.resolveBorder((-1).dp, 0.5f, isDarkTheme = false))
    }

    @Test
    fun testResolveBorder_moveSelectionOverrides() {
        val border = SensorFieldStyle.resolveBorder(0.dp, 0f, isDarkTheme = true, isSelectedForMove = true)
        assertNotNull("Move selection must produce non-null border even if thickness was 0", border)
        assertEquals(2.dp, border!!.width)
        assertEquals(Color(0xFF2196F3), border.brush.let {
            // solid color brush
            (it as androidx.compose.ui.graphics.SolidColor).value
        })
    }

    @Test
    fun testResolveBorder_themeContrastInterpolation() {
        // Dark theme: contrast 0 -> 0xFF383838, contrast 1 -> Color.White
        val darkSubtle = SensorFieldStyle.resolveBorder(1.dp, 0.0f, isDarkTheme = true)!!
        val darkMax = SensorFieldStyle.resolveBorder(1.dp, 1.0f, isDarkTheme = true)!!
        assertEquals(Color(0xFF383838), (darkSubtle.brush as androidx.compose.ui.graphics.SolidColor).value)
        assertEquals(Color.White, (darkMax.brush as androidx.compose.ui.graphics.SolidColor).value)

        // Light theme: contrast 0 -> 0xFFD6D6D6, contrast 1 -> Color.Black
        val lightSubtle = SensorFieldStyle.resolveBorder(1.dp, 0.0f, isDarkTheme = false)!!
        val lightMax = SensorFieldStyle.resolveBorder(1.dp, 1.0f, isDarkTheme = false)!!
        assertEquals(Color(0xFFD6D6D6), (lightSubtle.brush as androidx.compose.ui.graphics.SolidColor).value)
        assertEquals(Color.Black, (lightMax.brush as androidx.compose.ui.graphics.SolidColor).value)
    }

    @Test
    fun testSensorFieldVariantEnum_resolvesAllVariants() {
        for (variant in SensorFieldVariant.values()) {
            val style = SensorFieldStyle.forVariant(variant)
            assertNotNull("Each variant must resolve a non-null style", style)
            assertTrue("Display name must not be blank", variant.displayName.isNotBlank())
            assertTrue("Title string resource ID must be valid", variant.titleResId != 0)
        }
    }

    @Test
    fun testSensorFieldVariant_fromStorageDeserialization() {
        assertEquals(SensorFieldVariant.CLASSIC_SEAMLESS, SensorFieldVariant.fromStorage("CLASSIC_SEAMLESS"))
        assertEquals(SensorFieldVariant.OUTLINED_TILES, SensorFieldVariant.fromStorage("OUTLINED_TILES"))
        assertEquals(SensorFieldVariant.ELEVATED_CARDS, SensorFieldVariant.fromStorage("ELEVATED_CARDS"))
        assertEquals(SensorFieldVariant.SOFT_CAPSULES, SensorFieldVariant.fromStorage("SOFT_CAPSULES"))

        // Legacy compatibility mappings
        assertEquals(SensorFieldVariant.CLASSIC_SEAMLESS, SensorFieldVariant.fromStorage("VARIANT_0_BASELINE"))
        assertEquals(SensorFieldVariant.OUTLINED_TILES, SensorFieldVariant.fromStorage("VARIANT_1_OUTLINED_TILES"))
        assertEquals(SensorFieldVariant.ELEVATED_CARDS, SensorFieldVariant.fromStorage("VARIANT_2_ELEVATED_CARDS"))
        assertEquals(SensorFieldVariant.SOFT_CAPSULES, SensorFieldVariant.fromStorage("VARIANT_3_CAPSULES"))

        // Fallbacks for unknown or null values
        assertEquals(SensorFieldVariant.CLASSIC_SEAMLESS, SensorFieldVariant.fromStorage(null))
        assertEquals(SensorFieldVariant.CLASSIC_SEAMLESS, SensorFieldVariant.fromStorage("UNKNOWN_VARIANT"))
    }

    @Test
    fun testSensorFieldView_structuralContracts() {
        val viewFile = findFile("src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/SensorFieldView.kt")
        val content = viewFile.readText()

        // 1. Parameter defaults in SensorFieldView
        assertTrue(
            "SensorFieldView must define shape with default RectangleShape",
            content.contains("shape: Shape = RectangleShape")
        )
        assertTrue(
            "SensorFieldView must define cardElevation with default 0.dp",
            content.contains("cardElevation: CardElevation = CardDefaults.cardElevation(defaultElevation = 0.dp)")
        )
        assertTrue(
            "SensorFieldView must define border stroke with outlineVariant fallback",
            content.contains("BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)")
        )

        // 2. Previews exist for all 4 variants
        assertTrue(
            "PreviewCockpitVariant0_Baseline preview must exist",
            content.contains("fun PreviewCockpitVariant0_Baseline()")
        )
        assertTrue(
            "PreviewCockpitVariant1_OutlinedTiles preview must exist",
            content.contains("fun PreviewCockpitVariant1_OutlinedTiles()")
        )
        assertTrue(
            "PreviewCockpitVariant2_ElevatedCards preview must exist",
            content.contains("fun PreviewCockpitVariant2_ElevatedCards()")
        )
        assertTrue(
            "PreviewCockpitVariant3_Capsules preview must exist",
            content.contains("fun PreviewCockpitVariant3_Capsules()")
        )
        assertTrue(
            "PreviewCockpitVariantsComparison preview must exist",
            content.contains("fun PreviewCockpitVariantsComparison()")
        )
    }

    @Test
    fun testSensorGridScreen_structuralContracts() {
        val gridFile = findFile("src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        val content = gridFile.readText()

        // 1. Parameter defaults in SensorGridScreen
        assertTrue(
            "SensorGridScreen must define gridSpacing defaulting to 0.dp",
            content.contains("gridSpacing: Dp = 0.dp")
        )
        assertTrue(
            "SensorGridScreen must define fieldShape defaulting to RectangleShape",
            content.contains("fieldShape: Shape = RectangleShape")
        )

        // 2. Dynamic reactive resolution from tuningConfig
        assertTrue(
            "SensorGridScreen must resolve active style from tuningConfig.sensorFieldVariant",
            content.contains("SensorFieldStyle.forVariant(tuningConfig.sensorFieldVariant)")
        )

        // 3. Arrangement.spacedBy logic with effectiveSpacing
        assertTrue(
            "Column must conditionally apply spacedBy(effectiveSpacing)",
            content.contains("if (effectiveSpacing > 0.dp) Arrangement.spacedBy(effectiveSpacing) else Arrangement.Top")
        )
        assertTrue(
            "Row must conditionally apply spacedBy(effectiveSpacing)",
            content.contains("if (effectiveSpacing > 0.dp) Arrangement.spacedBy(effectiveSpacing) else Arrangement.Start")
        )

        // 4. Granular shape and border resolution (ATT-2456 / REQ-UI-276)
        assertTrue(
            "SensorGridScreen must resolve effectiveShape using SensorFieldStyle.resolveShape",
            content.contains("SensorFieldStyle.resolveShape(tuningConfig.sensorFieldCornerRadius.dp)")
        )
        assertTrue(
            "SensorGridScreen must resolve effectiveBorder using SensorFieldStyle.resolveBorder",
            content.contains("SensorFieldStyle.resolveBorder(")
        )
        assertTrue(
            "SensorGridScreen must resolve effectiveSpacing using SensorFieldStyle.resolveGridSpacing",
            content.contains("SensorFieldStyle.resolveGridSpacing(")
        )
        assertTrue(
            "SensorGridScreen must pass border with move selection override to SensorFieldView",
            content.contains("border = if (isSelected)")
        )
    }

    @Test
    fun testResolveGridSpacing_preservesBaselineForClassicSeamless() {
        assertEquals(
            "Baseline CLASSIC_SEAMLESS (0 radius, 1.0 dp border) must return 0.dp",
            0.dp,
            SensorFieldStyle.resolveGridSpacing(SensorFieldVariant.CLASSIC_SEAMLESS, 0.dp, 1.0.dp)
        )
        assertEquals(
            "Baseline CLASSIC_SEAMLESS (0 radius, 0.0 dp borderless) must return 0.dp",
            0.dp,
            SensorFieldStyle.resolveGridSpacing(SensorFieldVariant.CLASSIC_SEAMLESS, 0.dp, 0.0.dp)
        )
        assertEquals(
            "Baseline CLASSIC_SEAMLESS (0 radius, 0.5 dp border) must return 0.dp",
            0.dp,
            SensorFieldStyle.resolveGridSpacing(SensorFieldVariant.CLASSIC_SEAMLESS, 0.dp, 0.5.dp)
        )
    }

    @Test
    fun testResolveGridSpacing_scalesWithBorderThicknessAndCornerRadius() {
        val outlined = SensorFieldStyle.resolveGridSpacing(SensorFieldVariant.OUTLINED_TILES, 8.dp, 2.0.dp)
        assertTrue("OUTLINED_TILES baseline spacing must be >= 6.dp, was $outlined", outlined >= 6.dp)

        val thickBorder = SensorFieldStyle.resolveGridSpacing(SensorFieldVariant.CLASSIC_SEAMLESS, 0.dp, 4.0.dp)
        assertTrue("Thick 4 dp border must scale spacing to >= 6.dp to avoid collisions, was $thickBorder", thickBorder >= 6.dp)

        val roundedCorners = SensorFieldStyle.resolveGridSpacing(SensorFieldVariant.CLASSIC_SEAMLESS, 10.dp, 1.0.dp)
        assertTrue("Rounded corners on CLASSIC_SEAMLESS must produce > 0.dp spacing, was $roundedCorners", roundedCorners > 0.dp)

        val extremeThick = SensorFieldStyle.resolveGridSpacing(SensorFieldVariant.SOFT_CAPSULES, 20.dp, 4.0.dp)
        assertTrue("Clamping must cap spacing at <= 12.dp, was $extremeThick", extremeThick <= 12.dp)
    }
}
