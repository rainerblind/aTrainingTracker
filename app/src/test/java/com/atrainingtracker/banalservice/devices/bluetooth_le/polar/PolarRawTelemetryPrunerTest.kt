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

@file:Suppress("PackageNaming")

package com.atrainingtracker.banalservice.devices.bluetooth_le.polar

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.trainingtracker.settings.RawSensorRetentionPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Unit verification for Polar raw telemetry pruner and cascade deletion (TST-SET-119.3 / REQ-SET-117.3).
 */
@RunWith(RobolectricTestRunner::class)
class PolarRawTelemetryPrunerTest {

    private lateinit var context: Context
    private lateinit var rawDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        rawDir = PolarRawTelemetryRecorder.getRawSensorDir(context)
        cleanRawDir()
    }

    @After
    fun tearDown() {
        cleanRawDir()
    }

    private fun cleanRawDir() {
        rawDir.listFiles()?.forEach { it.delete() }
    }

    // TST-SET-119.3
    @Test
    fun deleteSidecar_cascadeDeletesSidecarAndTmp() {
        val baseFileName = "2026-11-16_120000"
        val sidecar = File(rawDir, "$baseFileName.polar_raw.csv.gz")
        val tmp = File(rawDir, "$baseFileName.polar_raw.csv.gz.tmp")

        sidecar.writeText("sidecar content")
        tmp.writeText("tmp content")

        assertTrue(sidecar.exists())
        assertTrue(tmp.exists())

        val deleted = PolarRawTelemetryPruner.deleteSidecar(context, baseFileName)
        assertTrue(deleted)
        assertFalse(sidecar.exists())
        assertFalse(tmp.exists())
    }

    // TST-SET-119.3
    @Test
    fun pruneExpiredSidecars_keepAll_preservesAllFiles() {
        val f1 = File(rawDir, "2020-01-01_100000.polar_raw.csv.gz")
        f1.writeText("old file")

        val deleted = PolarRawTelemetryPruner.pruneExpiredSidecars(
            context = context,
            policy = RawSensorRetentionPolicy.KEEP_ALL
        )
        assertEquals(0, deleted)
        assertTrue(f1.exists())
    }

    // TST-SET-119.3
    @Test
    fun pruneExpiredSidecars_off_deletesAllFiles() {
        val f1 = File(rawDir, "2026-11-16_100000.polar_raw.csv.gz")
        val f2 = File(rawDir, "2026-11-15_100000.polar_raw.csv.gz")
        f1.writeText("recent file")
        f2.writeText("yesterday file")

        val deleted = PolarRawTelemetryPruner.pruneExpiredSidecars(
            context = context,
            policy = RawSensorRetentionPolicy.OFF
        )
        assertEquals(2, deleted)
        assertFalse(f1.exists())
        assertFalse(f2.exists())
    }

    // TST-SET-119.3
    @Test
    fun pruneExpiredSidecars_days30_prunesOnlyFilesOlderThan30Days() {
        val nowMs = System.currentTimeMillis()
        val msPerDay = 24L * 60L * 60L * 1000L
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US)

        val recentDate = Date(nowMs - (10L * msPerDay))
        val oldDate = Date(nowMs - (35L * msPerDay))

        val recentBase = dateFormat.format(recentDate)
        val oldBase = dateFormat.format(oldDate)

        val recentFile = File(rawDir, "$recentBase.polar_raw.csv.gz")
        val oldFile = File(rawDir, "$oldBase.polar_raw.csv.gz")

        recentFile.writeText("10 days old")
        oldFile.writeText("35 days old")

        val deleted = PolarRawTelemetryPruner.pruneExpiredSidecars(
            context = context,
            policy = RawSensorRetentionPolicy.DAYS_30,
            nowEpochMs = nowMs
        )

        assertEquals(1, deleted)
        assertTrue("Recent file (10d) must be kept", recentFile.exists())
        assertFalse("Old file (35d) must be pruned", oldFile.exists())
    }
}
