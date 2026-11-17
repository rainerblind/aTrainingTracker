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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Contract test enforcing ASPICE SWE.3 living detailed-design completeness,
 * code-to-documentation parity, SWE.2 cross-referencing, and Stage 3 governance
 * (REQ-PRO-114, TST-PRO-114, ATT-8159).
 */
// Linked test specs: TST-PRO-114
class DetailedDesignParityContractTest {

    private fun findRepoRootFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("../$relativePath"),
            File("../../$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException("Could not locate $relativePath from working directory: ${File(".").absolutePath}")
    }

    private fun parseJavaEnumConstants(sourceText: String, enumName: String): List<String> {
        val match = Regex("""\benum\s+$enumName\s*\{([^}]*)\}""").find(sourceText)
            ?: error("Could not find Java enum $enumName")
        return match.groupValues[1]
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun parseKotlinEnumConstants(sourceText: String, enumName: String): List<String> {
        val match = Regex("""\benum\s+class\s+$enumName\s*\{([^}]*)\}""").find(sourceText)
            ?: error("Could not find Kotlin enum class $enumName")
        return match.groupValues[1]
            .lines()
            .map { it.substringBefore("//").trim().trimEnd(',') }
            .filter { it.isNotEmpty() }
    }

    /**
     * TST-PRO-114.1: Verifies structural completeness and valid Mermaid state diagrams
     * across all three living detailed-design documents.
     */
    @Test
    fun testDetailedDesignDocs_structuralAndMermaidCompleteness() {
        val docPaths = listOf(
            "docs/architecture/detailed_design/tracker_service.md",
            "docs/architecture/detailed_design/banal_service.md",
            "docs/architecture/detailed_design/my_btle_device.md"
        )
        val requiredSections = listOf(
            "stateDiagram-v2",
            "Events, Guards & Transitions",
            "Interfaces",
            "Threading",
            "Error Handling",
            "Verification"
        )

        for (path in docPaths) {
            val file = findRepoRootFile(path)
            assertTrue("$path must exist (REQ-PRO-114.1)", file.exists() && file.isFile)
            val content = file.readText()
            assertTrue("$path must be substantial", content.length > 1500)

            for (section in requiredSections) {
                assertTrue("$path must contain '$section' (TST-PRO-114.1)", content.contains(section))
            }

            val mermaidBlocks = Regex("""```mermaid\s+stateDiagram-v2([\s\S]*?)```""").findAll(content).toList()
            assertTrue("$path must contain at least one stateDiagram-v2 block", mermaidBlocks.isNotEmpty())
            for (block in mermaidBlocks) {
                val body = block.groupValues[1]
                assertTrue("$path stateDiagram-v2 must define transitions (-->)", body.contains("-->"))
                assertFalse("$path stateDiagram-v2 must not contain HTML tags", Regex("""</?[a-zA-Z]+>""").containsMatchIn(body))
            }
        }
    }

    /**
     * TST-PRO-114.2: Verifies 100% code-to-documentation parity for all states, enums,
     * lifecycle/concurrency fields, methods, and cited test classes.
     */
    @Test
    fun testDetailedDesignDocs_codeParityAcrossStatesSymbolsAndTests() {
        val trackerDesign = findRepoRootFile("docs/architecture/detailed_design/tracker_service.md").readText()
        val banalDesign = findRepoRootFile("docs/architecture/detailed_design/banal_service.md").readText()
        val btleDesign = findRepoRootFile("docs/architecture/detailed_design/my_btle_device.md").readText()

        val trackerSrc = findRepoRootFile("app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java").readText()
        val trackingModeSrc = findRepoRootFile("app/src/main/java/com/atrainingtracker/trainingtracker/TrackingMode.kt").readText()
        val autoPauseSrc = findRepoRootFile("app/src/main/java/com/atrainingtracker/trainingtracker/tracker/autopause/AutoPauseManager.kt").readText()
        val banalSrc = findRepoRootFile("app/src/main/java/com/atrainingtracker/banalservice/BANALService.java").readText()
        val deviceManagerSrc = findRepoRootFile("app/src/main/java/com/atrainingtracker/banalservice/devices/DeviceManager.java").readText()
        val remoteDeviceSrc = findRepoRootFile("app/src/main/java/com/atrainingtracker/banalservice/devices/MyRemoteDevice.java").readText()
        val btleSrc = findRepoRootFile("app/src/main/java/com/atrainingtracker/banalservice/devices/bluetooth_le/MyBTLEDevice.java").readText()

        // 1. Enum constants from production code must all appear in corresponding detailed-design docs
        for (mode in parseKotlinEnumConstants(trackingModeSrc, "TrackingMode")) {
            assertTrue("tracker_service.md must document TrackingMode.$mode", trackerDesign.contains(mode))
        }
        for (apState in parseKotlinEnumConstants(autoPauseSrc, "AutoPauseState")) {
            assertTrue("tracker_service.md must document AutoPauseState.$apState", trackerDesign.contains(apState))
        }
        for (startType in parseJavaEnumConstants(trackerSrc, "StartType")) {
            assertTrue("tracker_service.md must document StartType.$startType", trackerDesign.contains(startType))
        }
        for (btleState in parseJavaEnumConstants(btleSrc, "State")) {
            assertTrue("my_btle_device.md must document MyBTLEDevice.State.$btleState", btleDesign.contains(btleState))
        }

        // Honest negative guard: BANALService has no SearchingState enum in production code
        assertFalse("BANALService.java must not be falsely modeled as declaring SearchingState enum", banalSrc.contains("enum SearchingState"))
        assertTrue("banal_service.md must explicitly clarify absence of SearchingState enum", banalDesign.contains("**no** standalone `SearchingState` enum"))

        // 2. Key fields, flags, and methods cited in each detailed-design doc must exist verbatim in source
        val trackerSymbols = listOf(
            "mIsStopping", "mTrackingInterrupted", "mIsRehydrating", "mSearching", "mBanalBound",
            "mReceiversRegistered", "mSessionGeneration", "mTableInitializationFuture", "mFinalizationFuture",
            "mScheduler", "mDbExecutor", "mRrFlushExecutor", "mLiveRrQueue", "mAutoPauseManager",
            "mDiagnosticCollector", "onStartCommand", "handleActionStop", "sampleAndWriteToDb",
            "endWorkout", "recreateValuesWhenResuming", "applyResumedState", "rehydrateLiveSession",
            "handleTrackingAborted", "performStartForeground", "performStopSelf", "runOnMainThread"
        )
        assertSymbolsInDesignAndSource("tracker_service.md", trackerDesign, trackerSymbols, trackerSrc)

        val banalSymbols = listOf(
            "cServiceContext", "cSensorManager", "cDeviceManager", "cFilterManager",
            "mIsIndoorMode", "mHaveUserSelectedSportType", "mUserSelectedSportTypeId", "mActivityTypeMax",
            "BANALServiceComm", "isSearching", "getBestSensorData", "getAvailableSensorTypeSet",
            "setUserSelectedSportTypeId", "updateIndoorModeForSportType", "newLap",
            "cMyRemoteDeviceCurrentlySearchingFor", "mSearchQueue", "mMyRemoteDeviceTries",
            "mAntAsyncSearchEngine", "mBTSearchForNewDevicesEngine",
            "startSearchForPairedDevices", "searchForNextRemoteDevice", "isSearchingForARemoteDevice",
            "startSearchForNewRemoteDevices", "stopSearchForNewRemoteDevices",
            "mSearching", "notifyStartSearching", "notifyStopSearching"
        )
        val combinedBanalSource = banalSrc + "\n" + deviceManagerSrc + "\n" + remoteDeviceSrc
        assertSymbolsInDesignAndSource("banal_service.md", banalDesign, banalSymbols, combinedBanalSource)

        val btleSymbols = listOf(
            "mState", "mGattLock", "mBluetoothGatt", "mIsShutDown", "mIsGattPaused", "mIsScanning",
            "mHasConnectedInSession", "mGattOperationPending", "mGattQueue", "mReadCharacteristicQueue",
            "mGattCallback", "mScanCallback", "mSearchTimeoutRunnable", "mReconnectRunnable",
            "mBatteryReadRunnable", "mGattTimeoutRunnable", "GATT_OP_TIMEOUT_MS", "MAX_SCANS_IN_WINDOW",
            "SCAN_QUOTA_WINDOW_MS", "WriteDescriptorOp", "ReadCharacteristicOp",
            "WriteCharacteristicOp", "RequestMtuOp", "startSearching", "startScanAssistance",
            "stopScanAssistance", "triggerGattConnect", "readNextCharacteristic", "configureServices",
            "disconnectFromGatt", "pauseGattOperations", "resumeGattOperations",
            "obtainSnapshotCharacteristic", "isRandomStaticAddress", "isMidSessionOrWorkoutActive"
        )
        assertSymbolsInDesignAndSource("my_btle_device.md", btleDesign, btleSymbols, btleSrc)

        // 3. Every cited test class in the three detailed-design docs must exist under app/src/test
        val testSrcDir = findRepoRootFile("app/src/test")
        val testClassNames = testSrcDir.walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .map { it.nameWithoutExtension }
            .toSet()

        val citedTests = Regex("""`([A-Za-z0-9_]+Test)`""")
            .findAll(trackerDesign + "\n" + banalDesign + "\n" + btleDesign)
            .map { it.groupValues[1] }
            .toSet()
        assertTrue("Detailed design docs must cite unit/characterisation tests", citedTests.size >= 10)
        for (testClass in citedTests) {
            assertTrue("Cited test class '$testClass' must exist under app/src/test", testClassNames.contains(testClass))
        }
    }

    private fun assertSymbolsInDesignAndSource(
        docName: String,
        designText: String,
        symbols: List<String>,
        sourceText: String
    ) {
        for (symbol in symbols) {
            assertTrue("$docName must document symbol '$symbol'", designText.contains(symbol))
            assertTrue("Source for $docName must declare/use symbol '$symbol'", sourceText.contains(symbol))
        }
    }

    /**
     * TST-PRO-114.3: Verifies SWE.2 cross-reference in docs/architecture.md
     * and continuous SWE.3 maintenance rule in .agents/skills/stage3-impl-plan/SKILL.md.
     */
    @Test
    fun testSwe2CrossReferenceAndStage3GovernanceRule() {
        val archText = findRepoRootFile("docs/architecture.md").readText()
        assertTrue("docs/architecture.md must reference docs/architecture/detailed_design/", archText.contains("docs/architecture/detailed_design/"))
        assertTrue("docs/architecture.md must link tracker_service.md", archText.contains("detailed_design/tracker_service.md"))
        assertTrue("docs/architecture.md must link banal_service.md", archText.contains("detailed_design/banal_service.md"))
        assertTrue("docs/architecture.md must link my_btle_device.md", archText.contains("detailed_design/my_btle_device.md"))
        assertTrue("docs/architecture.md must reference DetailedDesignParityContractTest", archText.contains("DetailedDesignParityContractTest"))

        val stage3Skill = findRepoRootFile(".agents/skills/stage3-impl-plan/SKILL.md").readText()
        assertTrue("Stage 3 skill must reference REQ-PRO-114", stage3Skill.contains("REQ-PRO-114"))
        assertTrue("Stage 3 skill must mandate updating docs/architecture/detailed_design/", stage3Skill.contains("docs/architecture/detailed_design/"))
        assertTrue("Stage 3 skill must cover TrackerService, BANALService, and MyBTLEDevice",
            stage3Skill.contains("TrackerService") && stage3Skill.contains("BANALService") && stage3Skill.contains("MyBTLEDevice"))
    }
}
