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

package com.atrainingtracker.banalservice.devices

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Unit tests for [Manufacturer] (REQ-CON-063, TST-CON-063).
 */
class ManufacturerTest {

    @Test
    fun testGetName_sparse337To350AndExtensions_noIndexShift_tstCon063_1() {
        val expectedExtensions = mapOf(
            151 to "Abawo",
            152 to "Bafang",
            153 to "Luhong Technology",
            330 to "EO Swimbetter",
            331 to "Mywhoosh",
            332 to "Ravemen",
            333 to "Tektro Racing Products",
            334 to "Darad Innovation Corporation",
            335 to "Cycloptim",
            337 to "Runna",
            339 to "Zepp",
            340 to "Peloton",
            341 to "Carv",
            342 to "Tissot",
            345 to "Real Velo",
            346 to "Wetech",
            347 to "Jespr",
            348 to "Huawei",
            349 to "Gotoes",
            350 to "Cadence App",
            5759 to "ActiGraph",
        )

        expectedExtensions.forEach { (id, expected) ->
            assertEquals("Mismatch for manufacturer ID $id", expected, Manufacturer.getName(id))
        }

        // Omitted FIT Profile IDs that previously caused the 337–350 array index shift
        listOf(336, 338, 343, 344).forEach { gapId ->
            assertNull("Expected null for omitted FIT ID $gapId", Manufacturer.getName(gapId))
        }
    }

    @Test
    fun testGetName_curatedOverridesAndFitSdkDisplayNames_tstCon063_2() {
        val curatedAndSdkSamples = mapOf(
            1 to "Garmin",
            2 to "Garmin",
            5 to "IDT",
            6 to "SRM",
            8 to "iBike",
            9 to "Saris / CycleOps",
            21 to "A & D Medical",
            23 to "Suunto",
            28 to "Peaksware (TrainingPeaks)",
            32 to "Wahoo Fitness",
            38 to "o-synce",
            40 to "Concept 2",
            51 to "4iiiis",
            69 to "Stages Cycling",
            86 to "Elite",
            89 to "Tacx",
            92 to "1partCarbon",
            95 to "Stryd",
            100 to "Campagnolo",
            107 to "Magene",
            123 to "Polar Electro",
            128 to "ifit com",
            132 to "Cycplus",
            135 to "Coospo",
            255 to "Development",
            260 to "Zwift",
            263 to "Favero Electronics",
            266 to "Amer Sports",
            269 to "Mio Technology",
            283 to "FSA",
            289 to "Hammerhead",
            294 to "Coros",
            320 to "Lishun Electric & Communication",
            323 to "Spinning MDA",
            326 to "Nike",
            329 to "Absolute Cycling",
        )

        curatedAndSdkSamples.forEach { (id, expected) ->
            assertEquals("Mismatch for curated/FIT ID $id", expected, Manufacturer.getName(id))
        }
    }

    @Test
    fun testGetName_everyFitSdkConstantResolvesToCleanDisplayName_tstCon063_2() {
        val fitConstants = com.garmin.fit.Manufacturer::class.java.fields
            .filter { field ->
                Modifier.isStatic(field.modifiers) &&
                    Modifier.isPublic(field.modifiers) &&
                    field.type == Int::class.javaPrimitiveType &&
                    field.name != "INVALID"
            }

        assertTrue("Expected >200 FIT SDK manufacturer constants", fitConstants.size > 200)

        fitConstants.forEach { field ->
            val id = field.getInt(null)
            val resolved = Manufacturer.getName(id)
            assertNotNull("FIT constant ${field.name} ($id) must resolve non-null", resolved)
            assertTrue("FIT constant ${field.name} ($id) must be non-blank", resolved!!.isNotBlank())
            assertFalse(
                "FIT constant ${field.name} ($id) must not expose raw underscores: $resolved",
                resolved.contains('_')
            )
        }
    }

    @Test
    fun testFormatFitToken_normalizesUnderscoresLeadingPrefixAndBlank_tstCon063_2() {
        assertNull(Manufacturer.formatFitToken(null))
        assertNull(Manufacturer.formatFitToken(""))
        assertNull(Manufacturer.formatFitToken("   "))
        assertNull(Manufacturer.formatFitToken("___"))
        assertEquals("Wahoo Fitness", Manufacturer.formatFitToken("WAHOO_FITNESS"))
        assertEquals("Favero Electronics", Manufacturer.formatFitToken("FAVERO_ELECTRONICS"))
        assertEquals("4iiiis", Manufacturer.formatFitToken("_4IIIIS"))
        assertEquals("Absolute Cycling", Manufacturer.formatFitToken("ABSOLUTE__CYCLING"))
    }

    @Test
    fun testGetName_unknownNegativeAndInvalidIdsReturnNull_tstCon063_3() {
        val unmappedIds = listOf(
            Int.MIN_VALUE,
            -100,
            -1,
            0,
            74,
            75,
            154,
            254,
            256,
            351,
            1000,
            com.garmin.fit.Manufacturer.INVALID,
            Int.MAX_VALUE,
        )

        unmappedIds.forEach { id ->
            assertNull("Expected null for unmapped manufacturer ID $id", Manufacturer.getName(id))
        }
    }
}
