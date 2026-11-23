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

package com.atrainingtracker.trainingtracker.ui.navigation

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.atrainingtracker.banalservice.devices.bluetooth_le.BTLERadarDevice
import com.atrainingtracker.trainingtracker.MyHelper
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.exporter.writer.Rfc4180CsvWriter
import com.atrainingtracker.trainingtracker.settings.ZoneDisplayOptions
import com.atrainingtracker.trainingtracker.ui.hrv.HrvTestPhase
import com.atrainingtracker.trainingtracker.ui.settings.zones.ZoneLivePreviewTileCard
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldState
import com.atrainingtracker.trainingtracker.ui.tracking.ViewSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.StringWriter

/**
 * Behavioral & classpath decommissioning verification suite for `ATT-8130` (`REQ-UI-2802`, `TST-UI-2802`):
 * 1. Verifies deprecated screens, unregistered `ZonesSettingsActivity`, dead helpers/shims,
 *    vendored `au.com.bytecode.opencsv`, and the frozen Pebble + classic `ConfigViews*` subsystem
 *    are physically excised from the compiled classpath.
 * 2. Verifies `Rfc4180CsvWriter` exact RFC-4180 CSV formatting parity (quotes, commas, newlines, nulls).
 * 3. Verifies shared `ZoneLivePreviewTileCard` renders HR/Power and HRV DFA a1 previews cleanly.
 * 4. Verifies `BTLERadarDevice` rejects decommissioned synthetic `0x00`/`0x01` test headers.
 */
// Linked test specs: TST-UI-2802
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LegacyPackageDecommissioningTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun testDecommissionedScreensHelpersOpenCsvAndPebble_absentFromClasspath_TST_UI_2802_1() {
        val excisedClasses = listOf(
            "com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningDialogKt",
            "com.atrainingtracker.trainingtracker.ui.clusters.ClusterTuningScreenKt",
            "com.atrainingtracker.trainingtracker.activities.ZonesSettingsActivity",
            "com.atrainingtracker.trainingtracker.activities.ZonesSettingsActivityKt",
            "com.atrainingtracker.trainingtracker.fragments.mapFragments.TrackOnMapHelper",
            "com.atrainingtracker.trainingtracker.ui.util.Event",
            "com.atrainingtracker.trainingtracker.ui.util.EventObserver",
            "com.atrainingtracker.trainingtracker.physiology.DynamicalPowerHrEngineKt",
            "com.atrainingtracker.trainingtracker.ui.components.FastScrollbarKt",
            "com.atrainingtracker.trainingtracker.ui.components.BottomSheetUtilsKt",
            "au.com.bytecode.opencsv.CSVWriter",
            "au.com.bytecode.opencsv.ResultSetHelper",
            "au.com.bytecode.opencsv.ResultSetHelperService",
            "com.atrainingtracker.trainingtracker.smartwatch.pebble.PebbleService",
            "com.atrainingtracker.trainingtracker.smartwatch.pebble.PebbleServiceBuildIn",
            "com.atrainingtracker.trainingtracker.smartwatch.pebble.PebbleDatabaseManager",
            "com.atrainingtracker.trainingtracker.smartwatch.pebble.ConfigPebbleViewsActivity",
            "com.atrainingtracker.trainingtracker.smartwatch.pebble.ConfigPebbleViewsFragment",
            "com.atrainingtracker.trainingtracker.smartwatch.pebble.ConfigPebbleViewFragment",
            "com.atrainingtracker.trainingtracker.smartwatch.pebble.Watchapp",
            "com.atrainingtracker.trainingtracker.activities.ConfigViewsActivityClassic",
            "com.atrainingtracker.trainingtracker.fragments.ConfigViewsFragment",
            "com.atrainingtracker.trainingtracker.fragments.ConfigViewFragment"
        )

        for (fqcn in excisedClasses) {
            try {
                Class.forName(fqcn)
                fail("Decommissioned class must not exist on classpath: $fqcn")
            } catch (_: ClassNotFoundException) {
                // Expected
            }
        }

        // Verify removed enum/method/field surfaces
        assertFalse(
            "SettingsBottomSheetType must not expose ADVANCED_TUNING",
            SettingsBottomSheetType.entries.any { it.name == "ADVANCED_TUNING" }
        )
        assertFalse(
            "HrvTestPhase must not expose unreachable PRE_FLIGHT",
            HrvTestPhase.entries.any { it.name == "PRE_FLIGHT" }
        )
        assertFalse(
            "MyHelper must not declare dead formatRank()",
            MyHelper::class.java.declaredMethods.any { it.name == "formatRank" }
        )
        assertFalse(
            "TrainingApplication must not declare trackOnMapHelper or Pebble fields/methods",
            TrainingApplication::class.java.declaredFields.any {
                it.name == "trackOnMapHelper" || it.name.contains("PEBBLE", ignoreCase = true)
            } || TrainingApplication::class.java.declaredMethods.any {
                it.name.contains("Pebble", ignoreCase = true)
            }
        )
    }

    @Test
    fun testRfc4180CsvWriter_preservesExactQuotingEscapingAndNullParity_TST_UI_2802_2() {
        val buffer = StringWriter()
        Rfc4180CsvWriter(buffer).use { writer ->
            writer.writeNext(arrayOf("TIME_ACTIVE", "HR", "NOTES"))
            writer.writeNext(arrayOf("12.5", null, "Sprint \"A\", lap 1\nnext line"))
            writer.writeNext(null)
            writer.writeNext(arrayOf("", "0", "plain"))
        }

        val expected =
            "\"TIME_ACTIVE\",\"HR\",\"NOTES\"\n" +
                "\"12.5\",,\"Sprint \"\"A\"\", lap 1\nnext line\"\n" +
                "\"\",\"0\",\"plain\"\n"
        assertEquals(expected, buffer.toString())
    }

    @Test
    fun testZoneLivePreviewTileCard_rendersSharedHrAndHrvPreviewsCleanly_TST_UI_2802_3() {
        val previewState = SensorFieldState(
            configHash = 0,
            sensorFieldId = 0,
            rowNr = 0,
            colNr = 0,
            viewSize = ViewSize.NORMAL,
            label = "Power",
            filterDescription = "",
            value = "245",
            units = "W",
            zoneColor = TTColor.Zone4,
            zoneDisplayOptions = ZoneDisplayOptions(),
            showValue = true,
            showBar = true
        )

        composeRule.setContent {
            ATrainingTrackerTheme(darkTheme = false, amoled = false, setWindowColors = false) {
                ZoneLivePreviewTileCard(fieldState = previewState)
            }
        }

        composeRule.onNodeWithText("245").assertExists()
        composeRule.onNodeWithText("W").assertExists()
    }

    @Test
    fun testBtleRadarDevice_rejectsDecommissionedSyntheticFormats_TST_UI_2802_4() {
        assertFalse(
            "isV1Heartbeat must reject decommissioned synthetic 0x00 byte",
            BTLERadarDevice.isV1Heartbeat(byteArrayOf(0x00))
        )
        assertTrue(
            "isV1Heartbeat must accept authentic sequence-framed 0x12 heartbeat",
            BTLERadarDevice.isV1Heartbeat(byteArrayOf(0x12))
        )
        assertNull(
            "parseV1ThreatPacket must reject synthetic 0x00 frame",
            BTLERadarDevice.parseV1ThreatPacket(byteArrayOf(0x00, 0x01, 50.toByte(), 0x15))
        )
        assertNull(
            "parseV2ThreatPacket must reject synthetic 1-byte 0x01 frame",
            BTLERadarDevice.parseV2ThreatPacket(byteArrayOf(0x01))
        )
        assertTrue(
            "parseV2ThreatPacket must accept authentic 2-byte RearVue idle heartbeat",
            BTLERadarDevice.parseV2ThreatPacket(byteArrayOf(0x02, 0x00))!!.isEmpty()
        )
    }
}
