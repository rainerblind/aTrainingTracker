# Stage 3: Implementation Plan - ATT-6179: Resynchronize Architecture Document with Code

**Ticket**: [ATT-6179](https://rainerblind.atlassian.net/browse/ATT-6179)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6179`  
**Requirement Mapping**: `REQ-PRO-043` (`REQ-PRO-043.1`..`REQ-PRO-043.4`)  
**Test Mapping**: `TST-PRO-043` (`TST-PRO-043.1`..`TST-PRO-043.4`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Description & Background
`docs/architecture.md` exhibits architectural drift against production code by naming non-existent placeholder components (`RouteLearningEngine`, `GpxExporter`, `TcxExporter`, `WorkoutSamplesDb`, `MilestoneDetector`, `InAppReviewManager`), misrepresenting the tracking state machine against `TrackingMode.kt`, and over-generalizing database dispatcher claims. This plan details atomic steps to bring the architecture specification into full conformance with code while preserving all ASPICE SWE.2 contract invariants.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PRO-043` (*SWE.2 Architecture & Codebase Resynchronization*)
  * `REQ-PRO-043.1`: Replace drifted component names with authentic classes in `docs/architecture.md`.
  * `REQ-PRO-043.2`: Re-align Section 3.1 `stateDiagram-v2` with `TrackingMode` and `AutoPauseState`.
  * `REQ-PRO-043.3`: Accurately describe `dbDispatcher` and `Dispatchers.IO` usage in Section 4.1.
  * `REQ-PRO-043.4`: Enforce architecture code-grounding via `AspiceSweAuditContractTest.kt`.
* **Test Mapping**: `TST-PRO-043` (`TST-PRO-043.1`..`TST-PRO-043.4`)
  * `TST-PRO-043.1`: Presence of authentic component names.
  * `TST-PRO-043.2`: Absence of drifted component names.
  * `TST-PRO-043.3`: Conformance of `TrackingMode` states and threading tokens.
  * `TST-PRO-043.4`: Targeted unit test execution (`AspiceSweAuditContractTest`).

---

## 3. System Invariants & Preserved Behavior
1. **Contract Test Invariance**: All existing assertions in `AspiceSweAuditContractTest.testArchitectureDocument_fulfillsAspiceSwe2` (layers, 9 shards, `stateDiagram-v2`, $\ge 2$ `sequenceDiagram`s, `dbDispatcher`, `Dispatchers.Main`, `Dispatchers.IO`, `location|health`, `onUpgrade`) remain strictly satisfied.
2. **Zero Runtime Impact**: Zero modifications to production source code in `app/src/main/` or database schemas.

---

## 4. Proposed Architectural Changes
* **Component Resynchronization (`docs/architecture.md`)**:
  * `RouteLearningEngine` $\to$ `WorkoutClusterEngine` (Section 1 diagram, Section 2 table, Section 3.3 sequence diagram).
  * `GpxExporter, TcxExporter` $\to$ `GPXFileWriter, TCXFileWriter` (Section 1 diagram, Section 2 table, Section 3.3 sequence diagram).
  * `WorkoutSamplesDb` $\to$ `WorkoutSamplesDatabaseManager` (Section 2 table, Section 4.1).
  * `MilestoneDetector, InAppReviewManager` $\to$ `MilestoneAutoNamingManager, FirstJubileeActionHelper` (Section 2 table).
* **State Machine Resynchronization (`docs/architecture.md`)**:
  * Model `TrackingMode` states (`IDLE`, `READY`, `TRACKING`, `PAUSED`) with lifecycle transitions (start, pause, resume, save/finish, crash recovery) and document `AutoPauseState` as sub-state.
* **Threading Specification Accuracy (`docs/architecture.md`)**:
  * Clarify that `KnownLocationsRepository` uses a dedicated single-threaded `dbDispatcher`, while `WorkoutRepository` and other managers route operations through `Dispatchers.IO` with SQLite WAL mode.
* **Contract Test Suite Expansion (`AspiceSweAuditContractTest.kt`)**:
  * Add `@Test fun testArchitectureDocument_authenticComponentNomenclature()` asserting presence of authentic classes and absence of obsolete placeholders.
* **UI Consistency (Rule 23)**: No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (Architecture Doc Components & Diagrams)**: Edit `docs/architecture.md` to update component names across Section 1 diagram, Section 2 allocation table, and Section 3.3 sequence diagram (`REQ-PRO-043.1`, `TST-PRO-043.1`, `TST-PRO-043.2`).
2. **Step 2 (Architecture Doc State Machine)**: Update Section 3.1 `stateDiagram-v2` and narrative to reflect `TrackingMode` and `AutoPauseState` (`REQ-PRO-043.2`, `TST-PRO-043.3`).
3. **Step 3 (Architecture Doc Threading & Dispatchers)**: Refine Section 4.1 dispatcher description (`REQ-PRO-043.3`, `TST-PRO-043.3`).
4. **Step 4 (Contract Test Suite)**: Add `testArchitectureDocument_authenticComponentNomenclature()` in `AspiceSweAuditContractTest.kt` (`REQ-PRO-043.4`, `TST-PRO-043.1`, `TST-PRO-043.2`, `TST-PRO-043.3`).
5. **Step 5 (Targeted Test Execution)**: Run targeted test suite:
   ```bash
   ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.AspiceSweAuditContractTest"
   ```
   Confirm 100% PASS across all audit contract tests (`TST-PRO-043.4`).

---

## 6. Verification & Rollback Plan
* **Verification**: Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.AspiceSweAuditContractTest"`.
* **Rollback Plan**: Changes are isolated to `feature/ATT-6179`; can be reverted via `git checkout -- .`.
