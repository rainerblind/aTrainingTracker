# Stage 1 Analysis: ATT-6735 - Fix TrackerService WakeLock/thread attribution and link REQ-NFR IDs in architecture.md Section 4.9

**Ticket**: [ATT-6735](https://rainerblind.atlassian.net/browse/ATT-6735)  
**Sub-task**: [ATT-6860](https://rainerblind.atlassian.net/browse/ATT-6860) (`[Analysis]`)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `Sprint 2026-46.2`  
**Branch**: `feature/ATT-6735`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation
During the Sprint 2026-46.1 review of ATT-6181 (SWE.2 BP4 Resource & Timing Budgets), two factual code misattributions and an unlinked NFR traceability gap were identified in `docs/architecture.md` Section 4.9:
1. **Misattributed WakeLock Component**: Row 7 attributes `PARTIAL_WAKE_LOCK` to `BANALService`, whereas in production code `PARTIAL_WAKE_LOCK` (`aTrainingTracker:TrackingLock`) is acquired/released exclusively by `TrackerService` (`TrackerService.java:842-857`). `BANALService` contains zero WakeLock logic.
2. **Misattributed 1-Hz Sample Insert Threading**: Row 8 asserts 1-Hz `WorkoutSamplesDatabaseManager` writes run on `Dispatchers.IO / dbDispatcher`. In reality, live 1-Hz sample writes run on `TrackerService`'s Java `ScheduledExecutorService` (`mScheduler`) and dedicated single-threaded `mDbExecutor` (`TrackerService.java:183-184, 697, 2348`). `dbDispatcher` belongs strictly to `KnownLocationsRepository`, and `Dispatchers.IO` is reserved for repository/import writes.
3. **Missing REQ-NFR-* Traceability**: Row budgets currently reference generic `(ATT-6139)` in the header rather than concrete `REQ-NFR-*` IDs.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Peer Review & Verification**:
  * *WakeLock Audit*: Audited `BANALService.java` vs `TrackerService.java`. Confirmed `wakeLock = powerManager.newWakeLock(PARTIAL_WAKE_LOCK, "aTrainingTracker:TrackingLock")` resides solely in `TrackerService`. `BANALService` has zero `WakeLock` references.
  * *Threading Audit*: Confirmed 1-Hz sampling tick is triggered by `mScheduler` (`Executors.newScheduledThreadPool(1)`) and database write is enqueued via `mTableInitializationFuture.thenAcceptAsync(..., mDbExecutor)`. `dbDispatcher` is defined solely in `KnownLocationsRepository.kt:88` (`KnownLocationsDB-Thread`).
  * *NFR Traceability Audit*: Sharded requirements contain `REQ-NFR-001` and `REQ-NFR-002` (both `[Planned]`). Formally defining `REQ-NFR-003` through `REQ-NFR-014` establishes direct bidirectional traceability to all 12 budget rows in Section 4.9.

---

## 3. Scope Grounding & Non-Goals
* **In-Scope Goals**:
  * Correct Row 7 component to `TrackerService` under **Tracking Core** domain.
  * Correct Row 8 threading rationale to `mScheduler` / `mDbExecutor` off-main-thread execution (distinguishing `Dispatchers.IO` repository operations and `KnownLocationsRepository`'s `dbDispatcher`).
  * Formulate `REQ-NFR-003`..`REQ-NFR-014` in `docs/requirements/req_process_stability.md` and link each Section 4.9 budget row to its concrete `REQ-NFR-*` ID.
  * Extend `ArchitectureConformanceContractTest.kt` to enforce correct attribution and NFR link presence.
* **Out-of-Scope Non-Goals**:
  * Modifying `TrackerService.java` runtime WakeLock or threading behavior (pure SWE.2 architectural alignment).
  * Refactoring `WorkoutSamplesDatabaseManager` or repository coroutine dispatchers.

---

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PRO-052` (*Architectural Resource and Timing Budgets Specification*), refining `REQ-PRO-043` (*SWE.2 Architecture Conformance*). Targets `docs/architecture.md` Section 4.9 and `ArchitectureConformanceContractTest.kt`.
* **Historical Origin & Commit Trace**: Added in Sprint 2026-46.1 under ticket ATT-6181 to fulfill ASPICE SWE.2 BP4 finding C-07.
* **Root Reason for Existing Formulation**: WakeLock was provisionally grouped under `BANALService` assuming sensor lifecycle ownership; database threading conflated coroutine repository conventions (`Dispatchers.IO` / `dbDispatcher`) with the live Java service executor; `ATT-6139` backlog placement delayed concrete `REQ-NFR-*` allocation.
* **Preservation of Core Invariants**: Correcting attribution ensures 100% truthful architecture-to-code alignment (`Rule 81`) without altering runtime behavior or breaking existing contract tests.

---

## 5. System Invariants & Risk Assessment
* **Core Invariants**:
  * `ArchitectureConformanceContractTest.testArchitectureDoc_definesResourceAndTimingBudgets` MUST pass without regressing existing assertions.
  * All 12 numeric ceilings (< 50 ms, < 5%, < 40 MB, < 5%/h, < 25 ms, 3.5 s, < 30 ms, ~360 KB/h, < 16.6 ms) MUST be preserved.
  * Living documentation master tables MUST maintain 100% parity (`tools/verify_requirement_governance.py`).
* **Risk Rating**: `LOW`. Pure documentation and contract test alignment with zero Android runtime code alterations.
