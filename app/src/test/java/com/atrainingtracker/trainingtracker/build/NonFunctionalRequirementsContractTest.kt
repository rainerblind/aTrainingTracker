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

package com.atrainingtracker.trainingtracker.build

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * Contract verification test for Non-Functional Requirements (NFRs) under ASPICE SWE.1 BP1/BP4,
 * SWE.2 BP3/BP4, and SWE.6 BP3 (`REQ-PRO-113`, `TST-PRO-113`, `REQ-NFR-001`..`018`, `ATT-8158`).
 *
 * Replaces legacy doc-string presence checks with authentic verification enforcement:
 * 1. Group A (11 JVM/Robolectric measurable NFRs: 001, 002, 003, 005, 007, 008, 009, 010, 011, 013, 014)
 *    MUST have executable warmup+measured benchmark implementations in `MeasuredNfrBenchmarkTest.kt`,
 *    and when `app/build/reports/nfr/nfr_measurements.json` is generated, every recorded entry MUST pass.
 * 2. Group B (7 hardware/battery/Vitals NFRs: 004, 006, 012, 015, 016, 017, 018) MUST be classified as
 *    `Verification Method: On-Device Manual`, MUST NOT be falsely marked `[Verified]` by JVM unit tests
 *    without an attested physical-device protocol report, and MUST be covered in
 *    `docs/engineering/test_evidence/nfr_manual_protocol_template.md`.
 * 3. Living requirements/test shards maintain bidirectional `REQ-NFR` <-> `TST-NFR` traceability
 *    and Architecture §4.9 traceability (`REQ-NFR-003`..`014`).
 */
class NonFunctionalRequirementsContractTest {

    companion object {
        val GROUP_A_MEASURABLE_IDS = listOf(1, 2, 3, 5, 7, 8, 9, 10, 11, 13, 14)
        val GROUP_B_MANUAL_IDS = listOf(4, 6, 12, 15, 16, 17, 18)
    }

    private fun findRepoRootFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("../$relativePath"),
            File("../../$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Could not locate $relativePath in ${File(".").absolutePath}")
    }

    private fun extractSection(content: String, headingPrefix: String): String {
        val idx = content.indexOf(headingPrefix)
        assertTrue("Missing section starting with '$headingPrefix'", idx >= 0)
        val nextHeadingIdx = content.indexOf("\n### ", idx + headingPrefix.length)
        return if (nextHeadingIdx > idx) content.substring(idx, nextHeadingIdx) else content.substring(idx)
    }

    @Test
    fun testGroupA_measurableNfrsHaveRealBenchmarkImplementationsInMeasuredNfrBenchmarkTest() {
        val benchmarkFile = findRepoRootFile(
            "app/src/test/java/com/atrainingtracker/trainingtracker/build/MeasuredNfrBenchmarkTest.kt"
        )
        val benchmarkSource = benchmarkFile.readText()
        val reqContent = findRepoRootFile("docs/requirements/req_process_stability.md").readText()
        val tstContent = findRepoRootFile("docs/tests/tst_process_stability.md").readText()

        for (id in GROUP_A_MEASURABLE_IDS) {
            val reqId = String.format(Locale.US, "REQ-NFR-%03d", id)
            val tstId = String.format(Locale.US, "TST-NFR-%03d", id)

            assertTrue("MeasuredNfrBenchmarkTest.kt must implement $tstId", benchmarkSource.contains(tstId))
            assertTrue("MeasuredNfrBenchmarkTest.kt must record measurement for $reqId", benchmarkSource.contains(reqId))

            val reqSection = extractSection(reqContent, "### $reqId:")
            val tstSection = extractSection(tstContent, "### $tstId:")
            assertTrue("$reqId must cite $tstId", reqSection.contains(tstId))
            assertTrue("$tstId must cite $reqId", tstSection.contains(reqId))
            assertFalse(
                "$reqId is JVM/Robolectric measurable and must not be On-Device Manual",
                reqSection.contains("**Verification Method**: On-Device Manual")
            )
        }

        // If MeasuredNfrBenchmarkTest has already emitted measurements in this build, validate schema & verdicts
        val repoRoot = findRepoRootFile("tools/generate_test_evidence.py")
            .canonicalFile
            .parentFile
            ?.parentFile
            ?: error("Could not resolve repository root")
        val measurementsFile = File(repoRoot, "app/build/reports/nfr/nfr_measurements.json")
        if (measurementsFile.isFile) {
            val root = JSONObject(measurementsFile.readText())
            val arr = root.getJSONArray("measurements")
            val byReq = mutableMapOf<String, JSONObject>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                byReq[obj.getString("req_id")] = obj
            }
            for (id in GROUP_A_MEASURABLE_IDS) {
                val reqId = String.format(Locale.US, "REQ-NFR-%03d", id)
                val entry = byReq[reqId]
                if (entry != null) {
                    assertTrue("$reqId measured NFR must pass", entry.getBoolean("passed"))
                    assertTrue("$reqId must record positive measured_iterations", entry.getInt("measured_iterations") > 0)
                }
            }
            for (id in GROUP_B_MANUAL_IDS) {
                val reqId = String.format(Locale.US, "REQ-NFR-%03d", id)
                assertFalse(
                    "Group B manual NFR $reqId must never be emitted by JVM benchmark JSON",
                    byReq.containsKey(reqId)
                )
            }
        }
    }

    @Test
    fun testGroupB_manualHardwareNfrsEnforceOnDeviceManualHonestyAndProtocolTemplate() {
        val reqContent = findRepoRootFile("docs/requirements/req_process_stability.md").readText()
        val tstContent = findRepoRootFile("docs/tests/tst_process_stability.md").readText()
        val benchmarkSource = findRepoRootFile(
            "app/src/test/java/com/atrainingtracker/trainingtracker/build/MeasuredNfrBenchmarkTest.kt"
        ).readText()
        val protocolFile = findRepoRootFile("docs/engineering/test_evidence/nfr_manual_protocol_template.md")
        val protocolContent = protocolFile.readText()

        assertTrue(
            "Manual protocol template must carry explicit non-JVM execution stamp",
            protocolContent.contains("[MANUAL PROTOCOL — NOT EXECUTED IN JVM]")
        )
        assertTrue(
            "Manual protocol template must require device, build, date, operator, measured value, verdict",
            protocolContent.contains("Device / Build") &&
                protocolContent.contains("Operator") &&
                protocolContent.contains("Measured Value") &&
                protocolContent.contains("Verdict")
        )

        for (id in GROUP_B_MANUAL_IDS) {
            val reqId = String.format(Locale.US, "REQ-NFR-%03d", id)
            val tstId = String.format(Locale.US, "TST-NFR-%03d", id)

            val reqSection = extractSection(reqContent, "### $reqId:")
            val tstSection = extractSection(tstContent, "### $tstId:")

            assertTrue(
                "$reqId must declare Verification Method: On-Device Manual",
                reqSection.contains("**Verification Method**: On-Device Manual")
            )
            assertTrue(
                "$tstId must declare Verification Method: On-Device Manual",
                tstSection.contains("**Verification Method**: On-Device Manual")
            )
            assertFalse(
                "$reqId must NOT claim [Verified] without an attested physical-device run",
                reqSection.lines().first().contains("[Verified]")
            )
            assertFalse(
                "$tstId must NOT claim [Verified] without an attested physical-device run",
                tstSection.lines().first().contains("[Verified]")
            )
            assertTrue(
                "Manual protocol template must list $reqId and $tstId",
                protocolContent.contains(reqId) && protocolContent.contains(tstId)
            )
            assertFalse(
                "MeasuredNfrBenchmarkTest.kt must not falsely record JVM measurements for manual $reqId",
                benchmarkSource.contains("reqId = \"$reqId\"")
            )
        }
    }

    @Test
    fun testArchitectureSection49_traceabilityToNfrsAndPro113() {
        val archFile = findRepoRootFile("docs/architecture.md")
        val archContent = archFile.readText()

        for (id in 3..14) {
            val reqId = String.format(Locale.US, "REQ-NFR-%03d", id)
            assertTrue("Architecture Section 4.9 must trace to $reqId", archContent.contains(reqId))
        }

        val reqContent = findRepoRootFile("docs/requirements/req_process_stability.md").readText()
        val tstContent = findRepoRootFile("docs/tests/tst_process_stability.md").readText()
        assertEquals(11, GROUP_A_MEASURABLE_IDS.size)
        assertEquals(7, GROUP_B_MANUAL_IDS.size)
        assertTrue("req_process_stability.md must define REQ-PRO-113", reqContent.contains("### REQ-PRO-113:"))
        assertTrue("tst_process_stability.md must define TST-PRO-113:", tstContent.contains("### TST-PRO-113:"))
    }
}
