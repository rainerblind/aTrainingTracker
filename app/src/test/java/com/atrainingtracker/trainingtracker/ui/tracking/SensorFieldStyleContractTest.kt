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

import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    }

    @Test
    fun testVariant1_outlinedTilesMetrics() {
        val v1 = SensorFieldStyle.Variant1_OutlinedTiles
        assertEquals("Variant 1 gridSpacing must be 4.dp", 4.dp, v1.gridSpacing)
        assertEquals("Variant 1 elevation must be 0.dp", 0.dp, v1.defaultElevation)
        assertEquals("Variant 1 cornerRadius must be 6.dp", 6.dp, v1.cornerRadius)
    }

    @Test
    fun testVariant2_elevatedCardsMetrics() {
        val v2 = SensorFieldStyle.Variant2_ElevatedCards
        assertEquals("Variant 2 gridSpacing must be 6.dp", 6.dp, v2.gridSpacing)
        assertEquals("Variant 2 elevation must be 2.dp", 2.dp, v2.defaultElevation)
        assertEquals("Variant 2 cornerRadius must be 8.dp", 8.dp, v2.cornerRadius)
    }

    @Test
    fun testVariant3_capsuleMetrics() {
        val v3 = SensorFieldStyle.Variant3_Capsules
        assertEquals("Variant 3 gridSpacing must be 8.dp", 8.dp, v3.gridSpacing)
        assertEquals("Variant 3 elevation must be 1.dp", 1.dp, v3.defaultElevation)
        assertEquals("Variant 3 cornerRadius must be 12.dp", 12.dp, v3.cornerRadius)
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
    }
}
