# Stage 2: Requirement & Test Specification - ATT-6739: Replace hallucinated radar/audio classes and enforce file existence in sysreq_live_tracking.md

**Ticket**: [ATT-6739](https://rainerblind.atlassian.net/browse/ATT-6739)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-PRO-054` (*System Requirements for Live Tracking with Sensors*)  
**Test Spec ID**: `TST-PRO-054`  
**Branch**: `feature/ATT-6739`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Requirement Specification (REQ-PRO-054)

### 1.1 Problem Statement & Rationale
`docs/system/sysreq_live_tracking.md` cites hallucinated or stale symbols: `RadarThreatView.kt`, `SoundEngine.java`, `BikeBellAudioSynthesizer.java`, `BANALServiceTest`, `MapViewportHelper.kt`, `FusedLocationProviderClient`, and contradictory FGS type `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE`. These must be replaced with authentic codebase counterparts and verified via automated file existence checks.

### 1.2 Functional & Architectural Requirements
1. **Radar & Audio Grounding (`SYSREQ-TRK-006`)**: Replace `RadarThreatView.kt` with `RadarEdgeOverlay.kt`, `SoundEngine.java` with `RadarAlertManager.kt` / `RadarAudioSynthesizer.kt` / `NavigationAudioAlertManager.kt`, and `BikeBellAudioSynthesizer.java` with `BikeBellAudioSynthesizer.kt`.
2. **Test & Map Helper Grounding (`SYSREQ-TRK-001`, `SYSREQ-TRK-004`)**: Replace `BANALServiceTest` with `BANALServiceLifecycleTest.kt` / `BANALServiceRepositoryTest.kt`, and replace `MapViewportHelper.kt` with `MapDetailViewportMath.kt`.
3. **Location Client & FGS Alignment (`SYSREQ-TRK-004`, `SYSREQ-TRK-005`)**: Specify `SpeedAndLocationDevice_GoogleFused` using `LocationServices.FusedLocationApi` / `GoogleApiClient` + `LocationListener`, and restrict FGS types in line 78 to `location|health` only.
4. **Dynamic Contract Test Assertions**: Extend `SystemLiveTrackingRequirementsContractTest.kt` to dynamically extract all cited `.kt`/`.java` filenames and verify `File.exists()` on disk without reading production source text.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Specification Accuracy)**:
  * *Given* `docs/system/sysreq_live_tracking.md`,
  * *When* audited,
  * *Then* all cited `.kt` and `.java` components SHALL match authentic repository files on disk with zero hallucinated classes.
* **Criterion 2 (Dynamic Contract Verification)**:
  * *Given* `SystemLiveTrackingRequirementsContractTest.kt`,
  * *When* executed in the unit test suite,
  * *Then* dynamic `File.exists()` assertions for all cited files and constant grounding assertions SHALL pass with 100% success.

### 1.4 System Invariants
Zero runtime bytecode modifications in `app/src/main/java`; zero source-text reading violations (ATT-6186); 100% test pass rate.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PRO-054` / `TST-PRO-054` (*System Requirements for Live Tracking with Sensors*).
* **Historical Origin & Commit Trace**: Independent ASPICE pre-assessment finding S-02 (2026-11-08) and ticket ATT-6160 (commit `1c055509`).
* **Root Reason for Existing Formulation**: Early SYS.2 draft referenced non-existent radar/audio classes from early conceptual models (`RadarThreatView`, `SoundEngine`), pre-Kotlin file extensions (`BikeBellAudioSynthesizer.java`), incorrect test names (`BANALServiceTest`), non-existent map helper (`MapViewportHelper.kt`), legacy location client (`FusedLocationProviderClient`), and contradictory FGS type (`CONNECTED_DEVICE`).
* **Preservation of Core Invariants**: All 6 requirements `SYSREQ-TRK-001..006` remain 100% preserved and allocated to `SYS-EL-01..05`; zero changes to production runtime code; specifications and contract tests are brought into strict alignment with production reality.

---

## 2. Test Specification (TST-PRO-054)

### Test Case 1: Dynamic File Grounding Verification (`TST-PRO-054.2`)
* **Scope**: Unit / Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemLiveTrackingRequirementsContractTest.kt`
* **Preconditions**: Repository contains live Kotlin/Java sources and unit tests.
* **Action**: Dynamically assert `File.exists()` for each backticked `.kt` and `.java` file cited in `sysreq_live_tracking.md`.
* **Expected Result**: All cited files exist on disk with zero missing elements.

### Test Case 2: Code Grounding Parity (`TST-PRO-054.2`)
* **Scope**: Unit / Contract Test
* **Action**: Assert BLE UUIDs in `BluetoothConstants.java`, reconnect delays in `MyBTLEDevice.java`, radar watchdog timeout in `BTLERadarDevice.java`, and 1-Hz sampling tick in `TrackerService.java`.
* **Expected Result**: 100% parity against production constants.

### Test Case 3: Tier 1 Targeted Regression Suite (`TST-PRO-054.3`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Expected Result**: 100% pass across all build and specification contract tests.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-054.1` | Contract | `testSysreqLiveTracking_allRequirementsHaveMandatorySections` | `REQ-PRO-054.1` | Specified |
| `TST-PRO-054.2` | Contract | `testSysreqLiveTracking_allReferencedCodeFilesExistOnDisk` | `REQ-PRO-054.2` | Specified |
| `TST-PRO-054.3` | Regression | `SystemLiveTrackingRequirementsContractTest` | `REQ-PRO-001`, `REQ-PRO-054` | Specified |
