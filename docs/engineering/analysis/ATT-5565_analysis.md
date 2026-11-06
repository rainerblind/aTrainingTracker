# Stage 1 Analysis: ATT-5565 - Eliminate Race Condition Between deleteFinishedEvent and _deletionProgress.Idle in WorkoutRepository

**Ticket**: [ATT-5565](https://rainerblind.atlassian.net/browse/ATT-5565)  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*No crashs*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.8` | **Branch**: `feature/ATT-5565`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-06  

---

## 1. Problem Statement & Motivation
* **Intermittent Test Flake**: Under high CPU load during Tier 2 full-suite execution (`./gradlew testDebugUnitTest --max-workers=2`), `WorkoutRepositoryConcurrencyTest.testDefensiveDeletionReporting_failureAndExceptionResetIdle` failed sporadically:
  `AssertionError: Progress must reset to Idle on failure expected:<DeletionProgress> but was:<InProgress(workoutName=Workout ID: 201, workoutId=201)>`.
* **Impact**: Observers (UI or tests) listening to `deleteFinishedEvent` can observe a stale `InProgress` state on `deletionProgress` when awakened by the completion event before the worker thread reaches the `finally` block.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA**: In `WorkoutRepository.deleteWorkout(id)` on `Dispatchers.IO`, `deleteFinishedEvent.postValue(Pair(id, false))` was called within the `try`/`catch` body prior to `finally { _deletionProgress.postValue(DeletionProgress.Idle) }`.
* **Mechanism**: Under `InstantTaskExecutorRule`, `deleteFinishedEvent.postValue(...)` immediately decrements the test's `CountDownLatch`. The test thread wakes up and asserts `deletionProgress.value == DeletionProgress.Idle` before the asynchronous `Dispatchers.IO` coroutine thread executes the two lines into the `finally` block.
* **Independent Peer Review & Verification**:
  * Audited `WorkoutRepository.kt` (L2317–2347): `deleteFinishedEvent.postValue(Pair(id, true))` (success), `deleteFinishedEvent.postValue(Pair(id, false))` (failure), and `deleteFinishedEvent.postValue(Pair(id, false))` (catch) all fire before the `finally` block.
  * Verified Rule 74 (`docs/coding_standards.md` §5.5): In asynchronous methods updating both progress state and a terminal completion event, progress must always be reset to `Idle` *before* posting the completion event.
  * Moving `_deletionProgress.postValue(DeletionProgress.Idle)` directly before `deleteFinishedEvent.postValue(Pair(id, success))` inside `finally` guarantees consistent ordering across all execution paths.

---

## 3. User Scope Grounding (ATT-1250) & Side-Effect Audit (Rule 68)
* **In-Scope Goals**:
  * Restructure `WorkoutRepository.deleteWorkout` so `_deletionProgress.postValue(DeletionProgress.Idle)` runs before `deleteFinishedEvent.postValue(Pair(id, success))` in `finally`.
  * Update `WorkoutRepositoryConcurrencyTest.kt` to synchronize on both `deleteFinishedEvent` and `deletionProgress` transition to `Idle`.
  * Update living documentation (`REQ-STB-018.4`, `TST-STB-018.6`) to reflect Rule 74 ordering.
* **Out-of-Scope Non-Goals**:
  * Changing SQLite deletion logic (`WorkoutDeletionHelper`) or bulk deletion (`deleteOldWorkouts`).
  * Altering downstream notification contracts (`PeriodsRepository`, `WorkoutClusterEngine`).
* **Complexity Rating**: **1 SP (Trivial)** (Single coroutine reordering and test synchronization hardening).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-STB-018.4` (*Accurate Deletion Reporting & Guaranteed Progress Teardown*) and `TST-STB-018.6`. Targets `WorkoutRepository.kt` and `WorkoutRepositoryConcurrencyTest.kt`.
* **Historical Origin & Commit Trace**: Introduced in `ATT-4002` (commit `1c8e1a68`, Sprint 2026-43.3) to prevent unhandled deletion exceptions from stranding progress dialogs.
* **Root Reason for Existing Formulation**: ATT-4002 placed `_deletionProgress.postValue(Idle)` in `finally` as a cleanup safeguard, but naively left `deleteFinishedEvent.postValue` in the `try`/`catch` blocks without accounting for observer wake-up race conditions.
* **Preservation of Core Invariants**:
  1. *Accurate Reporting*: Success posts `Pair(id, true)`; failure/exception posts `Pair(id, false)`.
  2. *Guaranteed Idle Reset*: `_deletionProgress` unconditionally resets to `Idle` on all paths.
  3. *Zero Observer Stale Reads*: Any observer triggered by `deleteFinishedEvent` sees `DeletionProgress.Idle`.

---

## 5. Architectural Strategy & High-Level Solution
* **Production Code**: In `WorkoutRepository.deleteWorkout`, track `var success = false`, execute database deletion and surgical updates in `try`, capture exceptions in `catch`, and in `finally`:
  1. `_deletionProgress.postValue(DeletionProgress.Idle)`
  2. `deleteFinishedEvent.postValue(Pair(id, success))`
* **Test Code**: In `WorkoutRepositoryConcurrencyTest.testDefensiveDeletionReporting_failureAndExceptionResetIdle`, observe `deletionProgress` in addition to `deleteFinishedEvent` with dedicated countdown latches, ensuring deterministic multi-threaded synchronization.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: 100% thread safety on `Dispatchers.IO`, zero stuck progress dialogs, identical public API signatures.
* **Risk Rating**: **LOW** (Atomic reordering within existing `try ... finally` block).
