# Stage 3: Implementation Plan - ATT-6735: Fix TrackerService WakeLock/thread attribution and link REQ-NFR IDs in architecture.md Section 4.9

**Ticket**: [ATT-6735](https://rainerblind.atlassian.net/browse/ATT-6735)  
**Sub-task**: [ATT-6864](https://rainerblind.atlassian.net/browse/ATT-6864) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `Sprint 2026-46.2`  
**Requirement Mapping**: `REQ-PRO-052`, `REQ-NFR-003`..`REQ-NFR-014`  
**Test Mapping**: `TST-PRO-052`  
**Branch**: `feature/ATT-6735`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-11  

---

## 1. Problem Description & Background
Section 4.9 of `docs/architecture.md` incorrectly attributes `PARTIAL_WAKE_LOCK` to `BANALService` (which contains zero WakeLock logic; it is held exclusively by `TrackerService`), asserts 1-Hz database writes run on `Dispatchers.IO / dbDispatcher` (live inserts run on `TrackerService`'s `mScheduler` / `mDbExecutor`; `dbDispatcher` belongs strictly to `KnownLocationsRepository`), and lacks concrete `REQ-NFR-*` row IDs. This plan defines atomic SWE.2 documentation alignment and contract test verification.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PRO-052` (*Architectural Resource and Timing Budgets Specification*), `REQ-NFR-003`..`REQ-NFR-014` (*Non-Functional Budgets*)
* **Test Mapping**: `TST-PRO-052` (*Architectural Resource and Timing Budgets Contract Test Suite*) in `ArchitectureConformanceContractTest.kt`

---

## 3. System Invariants & Preserved Behavior
1. **Zero Runtime Impact**: Zero modifications to production source code in `app/src/main/`.
2. **Contract Preservation**: All existing assertions in `testArchitectureDoc_definesResourceAndTimingBudgets` (4 domains, numeric thresholds) remain passing.
3. **Governance Parity**: Living documentation master tables maintain 100% mathematical parity (`verify_requirement_governance.py`).

---

## 4. Proposed Architectural Changes
### Component 1: `docs/architecture.md` (Section 4.9 Table)
* Row 7: Set Subsystem Domain to `**Tracking Core**`, Critical Component to `TrackerService`, and document `aTrainingTracker:TrackingLock` lifecycle linking `REQ-NFR-009` and `REQ-TRK-003`.
* Row 8: Detail 1-Hz sample write off-main-thread execution on `TrackerService`'s `mScheduler` / `mDbExecutor` (`Dispatchers.IO` for repository/import writes; `dbDispatcher` strictly for `KnownLocationsRepository`), linking `REQ-NFR-010`.
* Rows 1–6, 9–12: Add explicit links to `REQ-NFR-003` through `REQ-NFR-008` and `REQ-NFR-011` through `REQ-NFR-014`.

### Component 2: `ArchitectureConformanceContractTest.kt`
* Extend `testArchitectureDoc_definesResourceAndTimingBudgets()` with assertions verifying:
  - `PARTIAL_WAKE_LOCK` is attributed to `TrackerService`.
  - 1-Hz write threading notes `mScheduler` / `mDbExecutor`.
  - Section 4.9 contains explicit references to `REQ-NFR-003` through `REQ-NFR-014`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (Check Gate 3)**: Verify Gate 3 sign-off via `python3 tools/jira_util.py check-gate <IMPL_PLAN_KEY>`.
2. **Step 2 (Update docs/architecture.md)**: Edit Section 4.9 table with accurate `TrackerService` WakeLock and `mScheduler`/`mDbExecutor` threading attribution and `REQ-NFR-003`..`014` links.
3. **Step 3 (Update ArchitectureConformanceContractTest.kt)**: Add contract test assertions for WakeLock attribution, threading notes, and NFR links.
4. **Step 4 (Test Execution & Governance)**: Run targeted test `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest"` and `python3 tools/verify_requirement_governance.py`.

---

## 6. Verification & Rollback Plan
* **Verification**: `ArchitectureConformanceContractTest` passes with 100% success; `verify_requirement_governance.py` reports zero errors.
* **Rollback**: `git checkout docs/architecture.md app/src/test/java/com/atrainingtracker/trainingtracker/build/ArchitectureConformanceContractTest.kt`.
