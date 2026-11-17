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
 * JVM-reflection code-to-documentation parity (zero production source-text reading),
 * SWE.2 cross-referencing, and Stage 3 governance
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

    private fun loadProductionClass(fqcn: String): Class<*> {
        return try {
            Class.forName(fqcn, false, javaClass.classLoader)
        } catch (e: ClassNotFoundException) {
            throw AssertionError("Required production class '$fqcn' must exist on classpath", e)
        }
    }

    private fun enumConstantNames(fqcn: String): List<String> {
        val enumClass = loadProductionClass(fqcn)
        val constants = enumClass.enumConstants
            ?: throw AssertionError("Class '$fqcn' must be an enum")
        return constants.map { (it as Enum<*>).name }
    }

    private fun collectDeclaredMembers(vararg fqcns: String): Set<String> {
        val members = mutableSetOf<String>()
        for (fqcn in fqcns) {
            val clazz = loadProductionClass(fqcn)
            members.add(clazz.simpleName)
            clazz.declaredFields.forEach { members.add(it.name) }
            clazz.declaredMethods.forEach { members.add(it.name) }
            clazz.declaredClasses.forEach { members.add(it.simpleName) }
        }
        return members
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
     * TST-PRO-114.2: Verifies 100% code-to-documentation parity via JVM reflection
     * for all states, enums, lifecycle/concurrency fields, methods, and cited test classes.
     */
    @Test
    fun testDetailedDesignDocs_codeParityAcrossStatesSymbolsAndTests() {
        val trackerDesign = findRepoRootFile("docs/architecture/detailed_design/tracker_service.md").readText()
        val banalDesign = findRepoRootFile("docs/architecture/detailed_design/banal_service.md").readText()
        val btleDesign = findRepoRootFile("docs/architecture/detailed_design/my_btle_device.md").readText()

        // 1. Enum constants from compiled production classes must all appear in detailed-design docs
        for (mode in enumConstantNames("com.atrainingtracker.trainingtracker.TrackingMode")) {
            assertTrue("tracker_service.md must document TrackingMode.$mode", trackerDesign.contains(mode))
        }
        for (apState in enumConstantNames("com.atrainingtracker.trainingtracker.tracker.autopause.AutoPauseState")) {
            assertTrue("tracker_service.md must document AutoPauseState.$apState", trackerDesign.contains(apState))
        }
        for (startType in enumConstantNames("com.atrainingtracker.trainingtracker.tracker.TrackerService\$StartType")) {
            assertTrue("tracker_service.md must document StartType.$startType", trackerDesign.contains(startType))
        }
        for (btleState in enumConstantNames("com.atrainingtracker.banalservice.devices.bluetooth_le.MyBTLEDevice\$State")) {
            assertTrue("my_btle_device.md must document MyBTLEDevice.State.$btleState", btleDesign.contains(btleState))
        }

        // Honest negative guard: BANALService declares no SearchingState enum in production bytecode
        val banalClass = loadProductionClass("com.atrainingtracker.banalservice.BANALService")
        assertFalse(
            "BANALService must not be falsely modeled as declaring SearchingState enum",
            banalClass.declaredClasses.any { it.simpleName == "SearchingState" }
        )
        assertTrue(
            "banal_service.md must explicitly clarify absence of SearchingState enum",
            banalDesign.contains("**no** standalone `SearchingState` enum")
        )

        // 2. Key fields, flags, inner classes, and methods cited in each doc must exist via reflection
        val trackerMembers = collectDeclaredMembers(
            "com.atrainingtracker.trainingtracker.tracker.TrackerService"
        )
        val trackerSymbols = listOf(
            "mIsStopping", "mTrackingInterrupted", "mIsRehydrating", "mSearching", "mBanalBound",
            "mReceiversRegistered", "mSessionGeneration", "mTableInitializationFuture", "mFinalizationFuture",
            "mScheduler", "mDbExecutor", "mRrFlushExecutor", "mLiveRrQueue", "mAutoPauseManager",
            "mDiagnosticCollector", "RR_FLUSH_BATCH_THRESHOLD", "onStartCommand", "handleActionStop",
            "sampleAndWriteToDb", "endWorkout", "recreateValuesWhenResuming", "applyResumedState",
            "rehydrateLiveSession", "handleTrackingAborted", "performStartForeground", "performStopSelf",
            "runOnMainThread"
        )
        assertSymbolsInDesignAndMembers("tracker_service.md", trackerDesign, trackerSymbols, trackerMembers)

        val banalMembers = collectDeclaredMembers(
            "com.atrainingtracker.banalservice.BANALService",
            "com.atrainingtracker.banalservice.devices.DeviceManager",
            "com.atrainingtracker.banalservice.devices.MyRemoteDevice"
        )
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
        assertSymbolsInDesignAndMembers("banal_service.md", banalDesign, banalSymbols, banalMembers)

        val btleMembers = collectDeclaredMembers(
            "com.atrainingtracker.banalservice.devices.bluetooth_le.MyBTLEDevice"
        )
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
        assertSymbolsInDesignAndMembers("my_btle_device.md", btleDesign, btleSymbols, btleMembers)

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

    private fun assertSymbolsInDesignAndMembers(
        docName: String,
        designText: String,
        symbols: List<String>,
        declaredMembers: Set<String>
    ) {
        for (symbol in symbols) {
            assertTrue("$docName must document symbol '$symbol'", designText.contains(symbol))
            assertTrue("Compiled bytecode for $docName must declare symbol '$symbol'", declaredMembers.contains(symbol))
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
        assertTrue(
            "Stage 3 skill must cover TrackerService, BANALService, and MyBTLEDevice",
            stage3Skill.contains("TrackerService") && stage3Skill.contains("BANALService") && stage3Skill.contains("MyBTLEDevice")
        )
    }
}
