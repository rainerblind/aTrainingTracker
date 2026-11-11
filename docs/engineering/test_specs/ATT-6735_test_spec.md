# Stage 2: Requirement & Test Specification - ATT-6735: Fix TrackerService WakeLock/thread attribution and link REQ-NFR IDs in architecture.md Section 4.9

**Ticket**: [ATT-6735](https://rainerblind.atlassian.net/browse/ATT-6735)  
**Sub-task**: [ATT-6863](https://rainerblind.atlassian.net/browse/ATT-6863) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `Sprint 2026-46.2`  
**Requirement Mapping**: `REQ-PRO-052` (*Architectural Resource and Timing Budgets Specification*), `REQ-NFR-003`..`REQ-NFR-014`  
**Test Spec ID**: `TST-PRO-052`  
**Branch**: `feature/ATT-6735`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`)  
**Date**: 2026-11-11  

---

## 1. Requirement Specification (REQ-PRO-052, REQ-NFR-003..014)

### 1.1 Problem Statement & Rationale
Section 4.9 of `docs/architecture.md` contained two factual misattributions: attributing `PARTIAL_WAKE_LOCK` to `BANALService` (actually held by `TrackerService`), and attributing 1-Hz database writes to `Dispatchers.IO / dbDispatcher` (actually executed via `TrackerService`'s `mScheduler` / `mDbExecutor`). Additionally, budget rows lacked direct traceability links to concrete `REQ-NFR-*` IDs.

### 1.2 Functional & Architectural Requirements
* `docs/architecture.md` Section 4.9 Row 7 SHALL attribute `PARTIAL_WAKE_LOCK` (`aTrainingTracker:TrackingLock`) to `TrackerService` under the **Tracking Core** domain.
* `docs/architecture.md` Section 4.9 Row 8 SHALL document that live 1-Hz sample inserts execute off-main-thread on `TrackerService`'s `mScheduler` / `mDbExecutor` (`dbDispatcher` strictly belongs to `KnownLocationsRepository`).
* `docs/requirements/req_process_stability.md` SHALL define `REQ-NFR-003` through `REQ-NFR-014` (`[Planned]`) for all 12 budget rows.
* Section 4.9 budget table SHALL link every row directly to its concrete `REQ-NFR-003`..`REQ-NFR-014` ID.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (WakeLock & Threading Attribution)**:
  * *Given* `docs/architecture.md` Section 4.9,
  * *When* compared against `TrackerService.java`,
  * *Then* `PARTIAL_WAKE_LOCK` is attributed to `TrackerService` and 1-Hz sample writes reference `mScheduler` / `mDbExecutor`.
* **Criterion 2 (Explicit REQ-NFR-* Traceability)**:
  * *Given* `docs/architecture.md` Section 4.9 and `ArchitectureConformanceContractTest.kt`,
  * *When* evaluated,
  * *Then* each of the 12 budget rows explicitly cites its corresponding `REQ-NFR-003` through `REQ-NFR-014` identifier.

### 1.4 System Invariants
* Zero changes to production code in `app/src/main/`.
* All 12 numeric ceilings (< 50 ms, < 5%, < 40 MB, < 5%/h, < 25 ms, 3.5 s, < 30 ms, ~360 KB/h, < 16.6 ms) MUST remain identical.
* Master requirement and test tables MUST maintain 100% mathematical parity.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PRO-052` (*Architectural Resource and Timing Budgets Specification*), refining `REQ-PRO-043` (*SWE.2 Architecture Conformance*). Targets `docs/architecture.md` Section 4.9 and `ArchitectureConformanceContractTest.kt`.
* **Historical Origin & Commit Trace**: Added in Sprint 2026-46.1 under ticket ATT-6181; updated in Sprint 2026-46.2 under ATT-6735.
* **Root Reason for Existing Formulation**: ATT-6181 provisionally grouped WakeLock under `BANALService` and database write threading under `Dispatchers.IO / dbDispatcher`, and postponed concrete `REQ-NFR-*` linking pending ATT-6139. ATT-6735 remedies these code misattributions to truthfully reflect `TrackerService` implementation and links `REQ-NFR-003`..`014`.
* **Preservation of Core Invariants**: Correcting attribution ensures 100% truthful architecture-to-code alignment without altering runtime behavior or breaking existing contract test thresholds.

---

## 2. Test Specification (TST-PRO-052)

### Test Case 1: `testArchitectureDoc_definesResourceAndTimingBudgets` (`TST-PRO-052.1`, `TST-PRO-052.2`)
* **Scope**: Contract Test (`app/src/test/java/com/atrainingtracker/trainingtracker/build/ArchitectureConformanceContractTest.kt`)
* **Preconditions**: `docs/architecture.md` Section 4.9 updated with corrected attributions and REQ-NFR IDs.
* **Action**: Run `testArchitectureDoc_definesResourceAndTimingBudgets()`.
* **Expected Result**: Asserts presence of `TrackerService` WakeLock attribution, `mScheduler` / `mDbExecutor` threading notes, and `REQ-NFR-003`..`REQ-NFR-014` links alongside existing quantitative bounds.

### Test Case 2: 9-Language Localization & Specifier Audit (`TST-PRO-052.Localization`)
* **Scope**: Localization Parity Test
* **Goal**: Verify zero strings added or modified; all 9 locales remain 100% synchronized.
* **Expected Result**: 0 UI modifications, 100% string parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

### Test Case 3: Governance Parity & Tier 1 Targeted Regression Suite (`TST-PRO-052.3`)
* **Command**: `python3 tools/verify_requirement_governance.py && ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest"`
* **Goal**: Verify zero Chesterton's Fence regressions, 100% master table parity, and clean test execution.
* **Expected Result**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-052.1` | Contract | `testArchitectureDoc_definesResourceAndTimingBudgets` | `REQ-PRO-052.1`, `REQ-NFR-003`..`014` | Specified |
| `TST-PRO-052.2` | Contract | Quantitative metric assertions | `REQ-PRO-052.2` | Specified |
| `TST-PRO-052.Localization` | Localization | `TranslationParityTest` | `REQ-UI-106` | Specified (N/A - doc only) |
| `TST-PRO-052.3` | Regression | `ArchitectureConformanceContractTest` | `REQ-PRO-052`, `REQ-PRO-043` | Specified |
