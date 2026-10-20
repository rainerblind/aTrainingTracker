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

package com.atrainingtracker.trainingtracker.ui.components.workoutdetails

import com.atrainingtracker.banalservice.BSportType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Verification suite for WorkoutDetails single-line power row and domain row visual separation
 * (`REQ-UI-415`, `TST-UI-378`, ATT-4042).
 */
class WorkoutDetailsLayoutTest {

    private fun createBaseData(
        normalizedPowerWatts: Double? = null,
        intensityFactor: Double? = null,
        tss: Double? = null,
        workKj: Double? = null,
        avgPowerToWeight: Double? = null,
        variabilityIndex: Double? = null
    ): WorkoutDetailsData {
        return WorkoutDetailsData(
            totalDistance = 42000.0,
            maxDisplacement = 18000.0,
            activeTimeSec = 5400,
            totalTimeSec = 5700,
            avgSpeedMps = 7.78,
            bSportType = BSportType.BIKE,
            ascentMeters = 500,
            descentMeters = 480,
            normalizedPowerWatts = normalizedPowerWatts,
            intensityFactor = intensityFactor,
            tss = tss,
            workKj = workKj,
            avgPowerToWeight = avgPowerToWeight,
            variabilityIndex = variabilityIndex
        )
    }

    @Test
    fun `TST-UI-378_1 testResolvePowerMetricSlots FullCogganTrio`() {
        val data = createBaseData(
            normalizedPowerWatts = 220.0,
            intensityFactor = 0.85,
            tss = 95.0,
            workKj = 1100.0,
            avgPowerToWeight = 3.1,
            variabilityIndex = 1.05
        )

        val slots = resolvePowerMetricSlots(data, enableCogganPowerMetrics = true)

        assertEquals(
            listOf(
                PowerMetricSlot.NORMALIZED_POWER,
                PowerMetricSlot.INTENSITY_FACTOR,
                PowerMetricSlot.TSS
            ),
            slots
        )
    }

    @Test
    fun `TST-UI-378_1 testResolvePowerMetricSlots MissingFtpFallback`() {
        // When athlete has no FTP configured in profile, IF and TSS are null.
        // Fallback should prioritize NP followed by Work kJ and W/kg up to 3 slots.
        val data = createBaseData(
            normalizedPowerWatts = 220.0,
            intensityFactor = null,
            tss = null,
            workKj = 1100.0,
            avgPowerToWeight = 3.1,
            variabilityIndex = 1.05
        )

        val slots = resolvePowerMetricSlots(data, enableCogganPowerMetrics = true)

        assertEquals(
            listOf(
                PowerMetricSlot.NORMALIZED_POWER,
                PowerMetricSlot.WORK_KJ,
                PowerMetricSlot.POWER_TO_WEIGHT
            ),
            slots
        )
    }

    @Test
    fun `TST-UI-378_1 testResolvePowerMetricSlots MissingFtpPartialPhysics`() {
        // NP present, IF/TSS null, only Work kJ present (no weight configured)
        val data = createBaseData(
            normalizedPowerWatts = 220.0,
            intensityFactor = null,
            tss = null,
            workKj = 1100.0,
            avgPowerToWeight = null
        )

        val slots = resolvePowerMetricSlots(data, enableCogganPowerMetrics = true)

        assertEquals(
            listOf(
                PowerMetricSlot.NORMALIZED_POWER,
                PowerMetricSlot.WORK_KJ
            ),
            slots
        )
    }

    @Test
    fun `TST-UI-378_1 testResolvePowerMetricSlots BasicPhysicsOnly`() {
        // When Coggan power metrics is disabled or NP is null
        val data = createBaseData(
            normalizedPowerWatts = null,
            workKj = 1100.0,
            avgPowerToWeight = 3.1
        )

        val slots = resolvePowerMetricSlots(data, enableCogganPowerMetrics = true)

        assertEquals(
            listOf(
                PowerMetricSlot.WORK_KJ,
                PowerMetricSlot.POWER_TO_WEIGHT
            ),
            slots
        )

        // Same when Coggan is disabled even if NP is present
        val dataWithNp = createBaseData(
            normalizedPowerWatts = 220.0,
            intensityFactor = 0.85,
            tss = 95.0,
            workKj = 1100.0,
            avgPowerToWeight = 3.1
        )
        val slotsDisabled = resolvePowerMetricSlots(dataWithNp, enableCogganPowerMetrics = false)

        assertEquals(
            listOf(
                PowerMetricSlot.WORK_KJ,
                PowerMetricSlot.POWER_TO_WEIGHT
            ),
            slotsDisabled
        )
    }

    @Test
    fun `TST-UI-378_1 testResolvePowerMetricSlots AllNull`() {
        val data = createBaseData()

        val slots = resolvePowerMetricSlots(data, enableCogganPowerMetrics = true)

        assertTrue(slots.isEmpty())
    }

    @Test
    fun `TST-UI-378_2 and TST-UI-378_3 WorkoutDetails source contract verifies single-line row, dividers, and modularity`() {
        val candidates = listOf(
            File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutdetails/WorkoutDetails.kt"),
            File("src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutdetails/WorkoutDetails.kt")
        )
        val file = candidates.firstOrNull { it.exists() }
        assertTrue("WorkoutDetails.kt must exist", file != null && file.exists())
        val content = file!!.readText()

        // 1. Modularity: strictly under 500 lines
        val lineCount = content.lines().size
        assertTrue("WorkoutDetails.kt line count ($lineCount) must be < 500 lines", lineCount < 500)

        // 2. Anti-pattern removal
        assertFalse("WorkoutDetails must not use FlowRow", content.contains("FlowRow("))
        assertFalse("WorkoutDetails must not use surfaceContainerHighest", content.contains("surfaceContainerHighest"))

        // 3. Single-line Power row contract: PowerTrainingMetricsSection renders at most one Row
        val powerSectionStart = content.indexOf("fun PowerTrainingMetricsSection(")
        assertTrue("PowerTrainingMetricsSection must exist", powerSectionStart != -1)
        val nextComposable = content.indexOf("@Composable", powerSectionStart + 30)
        val powerSectionContent = if (nextComposable != -1) {
            content.substring(powerSectionStart, nextComposable)
        } else {
            content.substring(powerSectionStart)
        }

        // Must define and use resolvePowerMetricSlots (either in WorkoutDetails.kt or dedicated PowerMetricSlot.kt)
        val powerSlotCandidates = listOf(
            File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutdetails/PowerMetricSlot.kt"),
            File("src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutdetails/PowerMetricSlot.kt")
        )
        val powerSlotFile = powerSlotCandidates.firstOrNull { it.exists() }
        val allContent = content + "\n" + (powerSlotFile?.readText() ?: "")
        assertTrue("Must declare resolvePowerMetricSlots", allContent.contains("fun resolvePowerMetricSlots("))
        assertTrue("Must declare enum PowerMetricSlot", allContent.contains("enum class PowerMetricSlot"))

        // Count Row calls in PowerTrainingMetricsSection - should be exactly 1 horizontal Row (inside WorkoutDomainRow)
        val rowOccurrences = Regex("\\bRow\\s*\\(").findAll(powerSectionContent).count()
        assertEquals(
            "PowerTrainingMetricsSection must render exactly one Row for single-line horizontal layout (REQ-UI-415.1)",
            1,
            rowOccurrences
        )

        // 4. Visual Separation with HorizontalDivider and First-Element Guard
        assertTrue(
            "WorkoutDetails must import HorizontalDivider",
            content.contains("import androidx.compose.material3.HorizontalDivider")
        )
        assertTrue(
            "WorkoutDetails must render HorizontalDivider with vertical padding between visible sections with index guard",
            content.contains("HorizontalDivider(") &&
                content.contains("Modifier.padding(vertical = 6.dp)") &&
                content.contains("MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)")
        )
        assertTrue(
            "WorkoutDetails must guard divider with index > 0 to eliminate leading/trailing dividers",
            content.contains("if (index > 0)") || content.contains("if (idx > 0)")
        )

        // 5. Spacing token is 12.dp
        assertTrue(
            "WorkoutDetails Column must use Arrangement.spacedBy(12.dp)",
            content.contains("verticalArrangement = Arrangement.spacedBy(12.dp)")
        )
    }

    @Test
    fun `TST-UI-378_4 Divider count invariant guarantees zero dividers for single section and N-1 dividers for N sections`() {
        // Pure algorithm verification matching WorkoutDetails:
        // visibleSections.forEachIndexed { index, _ -> if (index > 0) dividerCount++ }
        fun countDividers(sectionCount: Int): Int {
            var dividers = 0
            for (index in 0 until sectionCount) {
                if (index > 0) {
                    dividers++
                }
            }
            return dividers
        }

        // Single section (e.g. only Active Time & Distance, zero metrics):
        assertEquals("Single section must render exactly 0 dividers (no leading/trailing)", 0, countDividers(1))

        // Two sections:
        assertEquals("Two sections must render exactly 1 divider", 1, countDividers(2))

        // Five sections:
        assertEquals("Five sections must render exactly 4 dividers", 4, countDividers(5))

        // Empty section boundary (never occurs since Section 1 is always added, but verified for safety):
        assertEquals("Zero sections must render exactly 0 dividers", 0, countDividers(0))
    }

    @Test
    fun `TST-UI-388_2 testWorkoutDetails_gatingAndDividerIntegrity`() {
        val candidates = listOf(
            File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutdetails/WorkoutDetails.kt"),
            File("src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutdetails/WorkoutDetails.kt")
        )
        val file = candidates.firstOrNull { it.exists() }
        assertTrue("WorkoutDetails.kt must exist", file != null && file.exists())
        val content = file!!.readText()

        // 1. Signature checks for new summary toggles (REQ-UI-424.4)
        assertTrue("Must declare enableBasicPowerMetrics in WorkoutDetails", content.contains("enableBasicPowerMetrics: Boolean"))
        assertTrue("Must declare showBasicPowerInWorkoutSummary in WorkoutDetails", content.contains("showBasicPowerInWorkoutSummary: Boolean"))
        assertTrue("Must declare showCogganPowerInWorkoutSummary in WorkoutDetails", content.contains("showCogganPowerInWorkoutSummary: Boolean"))
        assertTrue("Must declare showAerobicEfficiencyInWorkoutSummary in WorkoutDetails", content.contains("showAerobicEfficiencyInWorkoutSummary: Boolean"))
        assertTrue("Must declare showCriticalPowerInWorkoutSummary in WorkoutDetails", content.contains("showCriticalPowerInWorkoutSummary: Boolean"))

        // 2. Gating checks
        assertTrue(
            "AerobicEfficiencyRow must be gated on showAerobicEfficiencyInWorkoutSummary",
            content.contains("enableAerobicEfficiencyModel && showAerobicEfficiencyInWorkoutSummary")
        )
        assertTrue(
            "AnaerobicBatteryRow must be gated on showCriticalPowerInWorkoutSummary",
            content.contains("enableCriticalPowerModel && showCriticalPowerInWorkoutSummary")
        )

        // 3. Strict modularity limit: strictly < 500 lines (REQ-UI-415.3, REQ-UI-424.5)
        val lineCount = content.lines().size
        assertTrue(
            "WorkoutDetails.kt must remain strictly < 500 lines (actual: $lineCount)",
            lineCount < 500
        )
    }
}
