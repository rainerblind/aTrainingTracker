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

package com.atrainingtracker.banalservice.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Automated contract test enforcing 100% coverage and bidirectional parity between
 * [SensorType] `internalUnit` metadata (via JVM reflection), `docs/architecture/sensor_units.md`,
 * and `docs/architecture.md` ({@code REQ-TRK-083}, {@code TST-TRK-083}, {@code REQ-PRO-126}).
 *
 * <p>Static per-constant Javadoc checks on {@code SensorType.java} are enforced offline by
 * {@code tools/verify_requirement_governance.py} ({@code verify_sensor_type_javadocs()})
 * so this test performs zero production source-text reads ({@code REQ-PRO-049.1}).
 */
class SensorTypeUnitsContractTest {

    private data class DocRow(
        val name: String,
        val category: String,
        val subCategory: String,
        val internalUnit: String,
        val valueType: String,
        val rangeAndSentinels: String,
        val uiFormatterAndDisplay: String,
        val exportUnits: String
    )

    private fun findRepoRoot(): File {
        var dir: File? = File(".").canonicalFile
        while (dir != null) {
            if (File(dir, "docs/architecture/sensor_units.md").isFile &&
                File(dir, "docs/architecture.md").isFile
            ) {
                return dir
            }
            dir = dir.parentFile
        }
        throw IllegalStateException("Could not locate repository root from CWD=${File(".").canonicalPath}")
    }

    private fun stripBackticks(value: String): String = value.trim().removeSurrounding("`")

    private fun parseDocRows(sensorUnitsFile: File): List<DocRow> {
        val rows = mutableListOf<DocRow>()
        for (line in sensorUnitsFile.readLines()) {
            val trimmed = line.trim()
            if (!trimmed.startsWith("|")) continue
            val cols = trimmed.split("|").drop(1).dropLast(1).map { it.trim() }
            if (cols.size != 8) continue
            val first = stripBackticks(cols[0])
            if (first == "SensorType" || first.startsWith(":---") || first.startsWith("---")) continue

            rows.add(
                DocRow(
                    name = first,
                    category = stripBackticks(cols[1]),
                    subCategory = stripBackticks(cols[2]),
                    internalUnit = stripBackticks(cols[3]),
                    valueType = stripBackticks(cols[4]),
                    rangeAndSentinels = cols[5].trim(),
                    uiFormatterAndDisplay = cols[6].trim(),
                    exportUnits = cols[7].trim()
                )
            )
        }
        return rows
    }

    @Test
    fun allSensorTypes_haveValidInternalUnit() {
        val placeholders = setOf("todo", "tbd", "n/a", "unknown", "none", "")

        for (sensor in SensorType.values()) {
            val internalUnit = sensor.internalUnit
            assertNotNull("${sensor.name}.internalUnit must not be null", internalUnit)
            assertTrue("${sensor.name}.internalUnit must not be blank", internalUnit.isNotBlank())
            assertFalse(
                "${sensor.name}.internalUnit must not be placeholder '$internalUnit'",
                placeholders.contains(internalUnit.trim().lowercase())
            )
        }
    }

    @Test
    fun sensorUnitsDoc_matchesAllSensorTypesBidirectionally_andArchitectureLinksIt() {
        val repoRoot = findRepoRoot()
        val sensorUnitsFile = File(repoRoot, "docs/architecture/sensor_units.md")
        val architectureFile = File(repoRoot, "docs/architecture.md")

        assertTrue("docs/architecture/sensor_units.md must exist", sensorUnitsFile.isFile)
        assertTrue("docs/architecture.md must exist", architectureFile.isFile)

        val archText = architectureFile.readText()
        assertTrue(
            "docs/architecture.md must link to architecture/sensor_units.md",
            archText.contains("architecture/sensor_units.md")
        )

        val docRows = parseDocRows(sensorUnitsFile)
        val docNames = docRows.map { it.name }
        assertEquals(
            "docs/architecture/sensor_units.md must not contain duplicate SensorType rows",
            docNames.size,
            docNames.toSet().size
        )

        val codeNames = SensorType.values().map { it.name }.toSet()
        val docNameSet = docNames.toSet()
        assertEquals(
            "Missing SensorType entries in docs/architecture/sensor_units.md: ${codeNames - docNameSet}; extra: ${docNameSet - codeNames}",
            codeNames,
            docNameSet
        )

        val rowByName = docRows.associateBy { it.name }
        for (sensor in SensorType.values()) {
            val row = rowByName.getValue(sensor.name)
            assertEquals("${sensor.name} category mismatch in sensor_units.md", sensor.category.name, row.category)
            val expectedSubCat = sensor.subCategory?.name ?: "null"
            assertEquals("${sensor.name} subCategory mismatch in sensor_units.md", expectedSubCat, row.subCategory)
            assertEquals("${sensor.name} internalUnit mismatch in sensor_units.md", sensor.internalUnit, row.internalUnit)
            assertEquals("${sensor.name} sensorValueType mismatch in sensor_units.md", sensor.sensorValueType.name, row.valueType)
            assertTrue("${sensor.name} Range & Sentinels column must be non-empty", row.rangeAndSentinels.isNotBlank())
            assertTrue("${sensor.name} UI Formatter & Display Unit column must be non-empty", row.uiFormatterAndDisplay.isNotBlank())
            assertTrue("${sensor.name} Export Units column must be non-empty", row.exportUnits.isNotBlank())
        }

        val docText = sensorUnitsFile.readText()
        listOf(
            "BATTERY_REMAINING_TIME",
            "CHARGING_STATUS_CODE",
            "STABILIZING_STATUS_CODE",
            "MAX_PACE",
            "W_PRIME_BAL",
            "MyHelper.getUnitsId",
            "ATT-8577",
            "FitFileWriter.kt",
            "TCXFileWriter.kt",
            "GPXFileWriter.java",
            "CSVFileWriter.java",
            "GCFileWriter.java"
        ).forEach { token ->
            assertTrue("docs/architecture/sensor_units.md must document '$token'", docText.contains(token))
        }
    }
}
