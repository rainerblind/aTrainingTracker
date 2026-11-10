# Stage 2: Requirement & Test Specification - ATT-6179: Resynchronize Architecture Document with Code

**Ticket**: [ATT-6179](https://rainerblind.atlassian.net/browse/ATT-6179)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6179`  
**Requirement Mapping**: `REQ-PRO-043` (*SWE.2 Architecture & Codebase Resynchronization*)  
**Test Spec ID**: `TST-PRO-043`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Requirement Specification (REQ-PRO-043)

### 1.1 Problem Statement & Rationale
`docs/architecture.md` (authored under `ATT-5431` for ASPICE SWE.2) exhibits drift by referencing fictitious component names (`RouteLearningEngine`, `GpxExporter`, `TcxExporter`, `WorkoutSamplesDb`, `MilestoneDetector`, `InAppReviewManager`), mismodeling tracking states against `TrackingMode`, and over-generalizing SQLite dispatcher usage. Resynchronizing architecture with authentic code ensures strict ASPICE SWE.2 compliance.

### 1.2 Functional & Architectural Requirements
* **REQ-PRO-043.1** (*Component Nomenclature Code-Grounding*): `docs/architecture.md` SHALL use authentic class names (`WorkoutClusterEngine`, `GPXFileWriter`, `TCXFileWriter`, `WorkoutSamplesDatabaseManager`, `MilestoneAutoNamingManager`, `FirstJubileeActionHelper`) and eliminate drifted names.
* **REQ-PRO-043.2** (*Tracking State Machine Conformance*): Section 3.1 `stateDiagram-v2` SHALL model authentic `TrackingMode` states (`IDLE`, `READY`, `TRACKING`, `PAUSED`) and session lifecycle transitions.
* **REQ-PRO-043.3** (*Concurrency & Dispatcher Precision*): Section 4.1 SHALL document `KnownLocationsRepository`'s dedicated `dbDispatcher` and general `Dispatchers.IO` usage with SQLite WAL.
* **REQ-PRO-043.4** (*Automated Architecture Conformance Contract*): `AspiceSweAuditContractTest.kt` SHALL assert presence of authentic components and absence of drifted placeholders.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Component Alignment)**: *Given* `docs/architecture.md`, *When* scanned for components, *Then* authentic classes are present and drifted placeholders do not exist.
* **Criterion 2 (State Machine)**: *Given* Section 3.1, *When* inspected, *Then* `stateDiagram-v2` models authentic `TrackingMode` states.
* **Criterion 3 (Contract Test)**: *Given* `AspiceSweAuditContractTest.kt`, *When* executed, *Then* architecture conformance assertions pass with 100% success.

---

## 2. Test Specification (TST-PRO-043)

### Test Case 1: Authentic Component Presence Test (`TST-PRO-043.1`)
* **Scope**: Contract Test (`AspiceSweAuditContractTest.kt`)
* **Assertion**: Verify `docs/architecture.md` contains authentic class names (`WorkoutClusterEngine`, `GPXFileWriter`, `TCXFileWriter`, `WorkoutSamplesDatabaseManager`, `MilestoneAutoNamingManager`).

### Test Case 2: Drifted Component Absence Test (`TST-PRO-043.2`)
* **Scope**: Contract Test (`AspiceSweAuditContractTest.kt`)
* **Assertion**: Verify `docs/architecture.md` does not contain `RouteLearningEngine`, `GpxExporter`, `TcxExporter`, `WorkoutSamplesDb`, `MilestoneDetector`, or `InAppReviewManager`.

### Test Case 3: Tracking State Machine & Dispatcher Conformance Test (`TST-PRO-043.3`)
* **Scope**: Contract Test (`AspiceSweAuditContractTest.kt`)
* **Assertion**: Verify `docs/architecture.md` specifies `TrackingMode` states and preserves required threading tokens (`dbDispatcher`, `Dispatchers.Main`, `Dispatchers.IO`).

### Test Case 4: Targeted Unit Test Suite Execution (`TST-PRO-043.4`)
* **Scope**: Targeted Unit Test Suite
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.AspiceSweAuditContractTest"`
* **Expected Result**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-043.1` | Contract | `AspiceSweAuditContractTest.kt` | `REQ-PRO-043.1` | Planned |
| `TST-PRO-043.2` | Contract | `AspiceSweAuditContractTest.kt` | `REQ-PRO-043.1` | Planned |
| `TST-PRO-043.3` | Contract | `AspiceSweAuditContractTest.kt` | `REQ-PRO-043.2`, `REQ-PRO-043.3` | Planned |
| `TST-PRO-043.4` | Regression | `AspiceSweAuditContractTest.kt` | `REQ-PRO-043.4` | Planned |
