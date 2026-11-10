# Stage 2: Requirement & Test Specification - ATT-6181: Define Resource and Timing Budgets in Architecture

**Ticket**: [ATT-6181](https://rainerblind.atlassian.net/browse/ATT-6181)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6181`  
**Requirement Mapping**: `REQ-PRO-052` (*Architectural Resource and Timing Budgets Specification*)  
**Test Spec ID**: `TST-PRO-052`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Requirement Specification (REQ-PRO-052)

### 1.1 Problem Statement & Rationale
ASPICE pre-assessment finding C-07 (SWE.2 BP4) identified that `docs/architecture.md` specifies only 1 Hz sampling rate and 3.5 s sensor failover without documented CPU, memory, battery, disk growth, or timing ceilings for runtime-critical components. Defining explicit resource and timing budgets in Section 4.9 and enforcing them via automated contract tests prevents resource starvation, UI frame jank, and battery drain during endurance activities.

### 1.2 Functional & Technical Requirements
* **REQ-PRO-052.1** (*Resource & Timing Budget Specification in Architecture*): `docs/architecture.md` Section 4.9 SHALL define a quantified resource and timing budget specification covering 4 runtime-critical domains:
  - `TrackerService`: CPU < 5% single-core, Heap < 40 MB, Tick latency < 50 ms / 1-Hz tick, Battery drain < 5%/h (screen off).
  - Sensor Ingestion (`BANALService` / `ProxySensor`): Dispatch latency < 25 ms, failover timeout 3.5 s, partial wake-lock bounded to tracking.
  - SQLite Persistence (`WorkoutSamplesDatabaseManager`): Insertion latency < 30 ms on `Dispatchers.IO`, disk growth ceiling ~360 KB/h at 1 Hz.
  - Cockpit UI & Map Rendering (`SensorGridScreen` / `ATrainingTrackerMap`): Frame time < 16.6 ms (60 FPS), polyline update latency < 30 ms, conflated 1-Hz UI emission.
* **REQ-PRO-052.2** (*Architectural Budget Conformance Contract Testing*): `ArchitectureConformanceContractTest.kt` SHALL assert that `docs/architecture.md` contains Section 4.9 and verifies numeric threshold specifications across all 4 domains.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Architecture Specification)**: *Given* `docs/architecture.md`, *When* Section 4.9 is inspected, *Then* it SHALL document explicit resource and timing budgets for `TrackerService`, `BANALService`, `WorkoutSamplesDatabaseManager`, and Cockpit UI/Map.
* **Criterion 2 (Contract Test Verification)**: *Given* `ArchitectureConformanceContractTest.kt`, *When* executed in the unit test suite, *Then* `testArchitectureDoc_definesResourceAndTimingBudgets` SHALL pass with 100% success.
* **Criterion 3 (Regression & Parity)**: *Given* living documentation, *When* `verify_requirement_governance.py` executes, *Then* master index tables match with 100% parity.

---

## 2. Test Specification (TST-PRO-052)

### Test Case 1: Architecture Section 4.9 Structural Conformance Test (`TST-PRO-052.1`)
* **Scope**: Contract Test (`ArchitectureConformanceContractTest.kt`)
* **Assertion**: Verify `docs/architecture.md` contains Section 4.9 covering Tracking, Sensors, Database, and UI/Map.

### Test Case 2: Quantitative Budget Threshold Verification Test (`TST-PRO-052.2`)
* **Scope**: Contract Test (`ArchitectureConformanceContractTest.kt`)
* **Assertion**: Verify numeric bounds for CPU (< 5%), heap (< 40 MB), latency (< 50 ms / < 25 ms / < 30 ms), battery (< 5%/h), and disk (~360 KB/h).

### Test Case 3: Clean-Room Targeted Suite Execution (`TST-PRO-052.3`)
* **Scope**: Contract Test Suite (`ArchitectureConformanceContractTest.kt`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest"`
* **Expected Result**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-052.1` | Contract | `docs/architecture.md`, `ArchitectureConformanceContractTest.kt` | `REQ-PRO-052.1` | Planned |
| `TST-PRO-052.2` | Contract | `docs/architecture.md`, `ArchitectureConformanceContractTest.kt` | `REQ-PRO-052.1`, `REQ-PRO-052.2` | Planned |
| `TST-PRO-052.3` | Suite | `ArchitectureConformanceContractTest.kt` | `REQ-PRO-052.2` | Planned |
