# Stage 2: Requirement & Test Specification - ATT-6741: Reconcile 13 Orphan SYSREQ IDs, Fix 10 Hallucinated Classes & Sequence Actor in System Architecture

**Ticket**: [ATT-6741](https://rainerblind.atlassian.net/browse/ATT-6741)  
**Sub-task**: [ATT-6789](https://rainerblind.atlassian.net/browse/ATT-6789) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture & Test Design*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2` | **Branch**: `feature/ATT-6741`  
**Requirement Mapping**: `REQ-PRO-056` (*System Architectural Design & SYSREQ Allocation*)  
**Test Spec ID**: `TST-PRO-056` (*System Architectural Design Contract Test Suite*)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Requirement Specification (REQ-PRO-056)

### 1.1 Problem Statement & Rationale
`docs/system/system_architecture.md` (ASPICE SYS.3) drifted from ASPICE SYS.2 and production code:
1. Section 5 allocates 25 requirements including 13 orphan IDs (`SYSREQ-ACQ-001..005`, `SYSREQ-PER-001..005`, `SYSREQ-ENV-001..003`) absent from SYS.2 (`sysreq_live_tracking.md`, `sysreq_data_exchange.md`, `docs/system_requirements.md`), which define only 12 requirements (`SYSREQ-TRK-001..006`, `SYSREQ-DEX-001..006`).
2. Ten classes/constants/entities are hallucinated: `HeartRateData`, `PowerData`, `BatteryLevelData`, `AntPlusManager`, `RadarThreatView`, `SoundEngine`, `FusedLocationProviderClient`, `LocationCallback`, `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE`, `backup_rules.xml`, and process `com.atrainingtracker.trainingtracker`.
3. Sequence Diagram 4.2 inaccurately models `WorkoutDetailScreen` handling workout finish/save, instead of `ControlTrackingScreen` / notification stop delegating to `TrackerService` on `mDbExecutor`.
4. `SystemArchitectureDesignContractTest.kt` checked only unilateral subset containment `declaredSysreqs <= allocatedSysreqs`, allowing orphan IDs to pass.

### 1.2 Functional & Architectural Requirements
* **REQ-PRO-056.1** (*System Architectural Design Specification Reconciliation*):
  - Section 5 of `docs/system/system_architecture.md` SHALL allocate exactly the 12 authoritative SYS.2 system requirements (`SYSREQ-TRK-001..006` and `SYSREQ-DEX-001..006`) across 2 domains (Live Tracking Loop and Data Exchange), with zero orphan IDs.
  - Document prose SHALL state "all 12 system requirements" (replacing 24/25).
  - All 10 non-existent symbols SHALL be replaced with grounded production entities (`RadarEdgeOverlay`, `RadarAlertManager`, `RadarAudioSynthesizer`, `NavigationAudioAlertManager`, `BikeBellAudioSynthesizer`, `SensorBatteryNotificationManager`, `GoogleApiClient` + `LocationListener`, `full_backup_content.xml`, process `com.atrainingtracker`).
  - Sequence Diagram 4.2 SHALL model athlete stopping via `ControlTrackingScreen` / notification action delegating to `TrackerService` (`mDbExecutor`) running `endWorkout()` to finalize `WorkoutSummaries.db` and invoke `ExportManager`.
* **REQ-PRO-056.2** (*System Architecture Contract Testing Hardening*):
  - `SystemArchitectureDesignContractTest.kt` SHALL assert exact bidirectional set equality (`declaredSysreqs == allocatedSysreqs`) against `docs/system_requirements.md`.
  - The test suite SHALL assert dynamic reflection `Class.forName` and `File.exists` checks verifying grounded components without source-text reading per ATT-6186.
  - The test suite SHALL verify Diagram 4.2 asserts `TrackerService` and `mDbExecutor` finalization actors.

### 1.3 Acceptance Criteria (Given-When-Then) & System Invariants
* *Given* `docs/system/system_architecture.md`, *When* inspected, *Then* it contains exactly 12 allocated SYSREQ IDs matching `docs/system_requirements.md`, zero orphan IDs, grounded production symbols, and accurate `TrackerService` finalization sequence.
* *Given* `SystemArchitectureDesignContractTest.kt`, *When* executed via `./gradlew testDebugUnitTest`, *Then* all structural, bidirectional equality, sequence actor, and code-grounding reflection assertions PASS.
* *Invariants*: 5 System Elements (`SYS-EL-01..05`) and 6 System Interfaces (`SYS-IF-01..06`) preserved; zero modifications to Android production runtime code in `app/src/main/`.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PRO-056` (*System Architectural Design & SYSREQ Allocation*), targeting `docs/system/system_architecture.md` and `SystemArchitectureDesignContractTest.kt`.
* **Historical Origin & Commit Trace**: Commit `7563a9c2` (`ATT-6164`), closing ASPICE pre-assessment finding S-05; refined in `ATT-6741`.
* **Root Reason for Existing Formulation**: Rapid initial drafting of Section 5 fragmented live tracking into 25 items containing 13 orphan IDs not grounded in SYS.2, with hallucinated symbols and unilateral contract test checks (`declaredSysreqs <= allocatedSysreqs`).
* **Preservation of Core Invariants**: 5 System Elements (`SYS-EL-01..05`) and 6 Interfaces (`SYS-IF-01..06`) preserved. All 12 authentic SYS.2 requirements remain 100% allocated with exact bidirectional equality. Zero runtime production code changes.

---

## 2. Test Specification (TST-PRO-056)

### Test Case 1: Elements & Interfaces Structural Coverage (`TST-PRO-056.1`)
* **Scope**: Contract Test (`SystemArchitectureDesignContractTest.kt`)
* **Assertion**: Verify document defines `SYS-EL-01..05` and `SYS-IF-01..06` with protocol, payload schema, and timing/rate bounds.

### Test Case 2: Dynamic Sequence Diagrams & Finalization Actor (`TST-PRO-056.2`)
* **Scope**: Contract Test (`SystemArchitectureDesignContractTest.kt`)
* **Assertion**: Verify valid Mermaid sequence diagrams for 1-Hz sampling and cloud sync, asserting Diagram 4.2 specifies `ControlTrackingScreen`, `TrackerService`, and `mDbExecutor`.

### Test Case 3: 100% Bidirectional SYSREQ Allocation & Exact Equality (`TST-PRO-056.3`)
* **Scope**: Contract Test (`SystemArchitectureDesignContractTest.kt`)
* **Assertion**: Verify `SYSREQ-TRK-001..006` and `SYSREQ-DEX-001..006` are allocated, and assert exact bidirectional set equality `assertEquals(declaredSysreqs, allocatedSysreqs)`.

### Test Case 4: Dynamic Production Code Grounding Reflection (`TST-PRO-056.5`)
* **Scope**: Contract Test (`SystemArchitectureDesignContractTest.kt`)
* **Assertion**: Verify existence of `RadarEdgeOverlayKt`, `RadarAlertManager`, `RadarAudioSynthesizer`, `NavigationAudioAlertManager`, `BikeBellAudioSynthesizer`, and `SpeedAndLocationDevice_GoogleFused` via `Class.forName()`, `app/src/main/res/xml/full_backup_content.xml` via `File.exists()`, and process `com.atrainingtracker`.

### Test Case 5: Clean-Room Targeted Regression Suite (`TST-PRO-056.4`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.SystemArchitectureDesignContractTest"`
* **Assertion**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-056.1` | Contract | `testSystemArchitecture_definesAllSystemElements` & `Interfaces` | `REQ-PRO-056.1` | Specified |
| `TST-PRO-056.2` | Contract | `testSystemArchitecture_definesDynamicBehaviorSequenceDiagrams` | `REQ-PRO-056.1`, `REQ-PRO-056.2` | Specified |
| `TST-PRO-056.3` | Contract | `testSystemArchitecture_crossChecksSystemRequirementsSpec` | `REQ-PRO-056.1`, `REQ-PRO-056.2` | Specified |
| `TST-PRO-056.4` | Regression | `./gradlew testDebugUnitTest --tests "...build.SystemArchitectureDesignContractTest"` | `REQ-PRO-001`, `REQ-PRO-056.2` | Specified |
| `TST-PRO-056.5` | Reflection | `testSystemArchitecture_productionCodeGrounding` | `REQ-PRO-056.2` | Specified |
