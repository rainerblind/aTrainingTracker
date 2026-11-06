# Stage 2: Requirement & Test Specification - ATT-5565: Eliminate Race Condition Between deleteFinishedEvent and _deletionProgress.Idle

**Ticket**: [ATT-5565](https://rainerblind.atlassian.net/browse/ATT-5565)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*No crashs*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.8` | **Branch**: `feature/ATT-5565`  
**Requirement Mapping**: `REQ-STB-018` (`REQ-STB-018.4`) | **Test Spec ID**: `TST-STB-018` (`TST-STB-018.6`)  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-06  

---

## 1. Requirement Specification (`REQ-STB-018.4`)

### 1.1 Problem Statement & Rationale
Under high CPU contention during Tier 2 unit test runs (`./gradlew testDebugUnitTest --max-workers=2`), `WorkoutRepositoryConcurrencyTest.testDefensiveDeletionReporting_failureAndExceptionResetIdle` failed intermittently with:
`AssertionError: Progress must reset to Idle on failure expected:<DeletionProgress> but was:<InProgress(workoutName=Workout ID: 201, workoutId=201)>`.
In `WorkoutRepository.deleteWorkout`, `deleteFinishedEvent.postValue(Pair(id, false))` was dispatched inside `try`/`catch` prior to `finally { _deletionProgress.postValue(DeletionProgress.Idle) }`. Under `InstantTaskExecutorRule`, the test observer wakes up upon event delivery and inspects `deletionProgress` before the asynchronous `Dispatchers.IO` thread reaches the `finally` block, violating Rule 74 (`docs/coding_standards.md` §5.5).

### 1.2 Functional & Architectural Requirements
1. **Rule 74 Progress Reset Precedence (`REQ-STB-018.4`)**: In `WorkoutRepository.deleteWorkout(id)`, `_deletionProgress.postValue(DeletionProgress.Idle)` SHALL execute prior to emitting `deleteFinishedEvent.postValue(Pair(id, success))` under all execution paths (success, failure, exception) inside the `finally` block.
2. **Defensive Deletion Reporting Invariant (`REQ-STB-018.4`)**: `deleteFinishedEvent` SHALL post `Pair(id, true)` if and only if deletion succeeds in the database, and `Pair(id, false)` if deletion returns `false` or throws an exception.
3. **Deterministic Multi-Thread Synchronization (`TST-STB-018.6`)**: `WorkoutRepositoryConcurrencyTest` SHALL synchronize cleanly without order-dependent race conditions across both `deleteFinishedEvent` and `deletionProgress`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (Zero Stale Progress Read)**: *Given* an observer listening to `deleteFinishedEvent`, *When* `deleteFinishedEvent` is received, *Then* `deletionProgress.value` is guaranteed to be `DeletionProgress.Idle`.
* **AC-2 (Failure/Exception Reporting)**: *Given* a workout deletion that fails in the database or throws an SQLite exception, *When* `deleteWorkout` completes, *Then* `deleteFinishedEvent` posts `Pair(id, false)` and `deletionProgress` is `Idle`.
* **AC-3 (Success Reporting)**: *Given* a successful workout deletion, *When* `deleteWorkout` completes, *Then* `deleteFinishedEvent` posts `Pair(id, true)` and `deletionProgress` is `Idle`.

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: `REQ-STB-018.4` (amended in ATT-5565), targeting `WorkoutRepository.kt` and `WorkoutRepositoryConcurrencyTest.kt`.
* `Historical Origin & Commit Trace`: Sprint 2026-43.3 (`ATT-4002`, commit `1c8e1a68`) introduced `REQ-STB-018.4` to prevent unhandled deletion exceptions from stranding progress dialogs, placing `_deletionProgress.postValue(Idle)` in `finally` while leaving `deleteFinishedEvent.postValue` in `try`/`catch`.
* `Root Reason for Existing Formulation`: Resetting progress in `finally` was intended as a catch-all safety net, but emitting `deleteFinishedEvent` earlier in `try`/`catch` introduced an observer-wakeup race condition under thread scheduling contention.
* `Preservation of Core Invariants`: Success reporting `Pair(id, true)`, failure reporting `Pair(id, false)`, guaranteed `Idle` reset, zero stuck progress dialogs, and 100% full-suite test pass rate remain strictly preserved.

---

## 2. Test Specification (`TST-STB-018.6`)

### Test Case 1: Failure and Exception Reset Idle Contract (`TST-STB-018.6`)
* **Scope**: Concurrency unit test in `WorkoutRepositoryConcurrencyTest.kt` (`testDefensiveDeletionReporting_failureAndExceptionResetIdle`).
* **Procedure**: Execute `deleteWorkout(201L)` where deletion returns `false`, and `deleteWorkout(202L)` where deletion throws `SQLiteException`. Assert `deleteFinishedEvent` emits `Pair(id, false)` and assert `deletionProgress.value == DeletionProgress.Idle` upon event arrival with zero race condition failures under repeated high-load runs.

### Test Case 2: Success Deletion Progress Reset Order (`TST-STB-018.6`)
* **Scope**: Concurrency unit test in `WorkoutRepositoryConcurrencyTest.kt`.
* **Procedure**: Execute `deleteWorkout(id)` where deletion succeeds. Verify observer awakened by `deleteFinishedEvent` immediately observes `deletionProgress.value == DeletionProgress.Idle`.

### Test Case 3: Targeted Aftermath Concurrency Suite Regression
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepositoryConcurrencyTest" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Expected Result**: 100% test pass rate across all targeted tests.

---

## 3. Traceability Matrix

| Requirement ID | Description | Test Case ID | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-STB-018.4` | Rule 74 progress reset precedence before completion event | `TST-STB-018.6` | `WorkoutRepositoryConcurrencyTest.testDefensiveDeletionReporting_failureAndExceptionResetIdle` |
| `REQ-STB-018.4` | Defensive failure and exception reporting (`Pair(id, false)`) | `TST-STB-018.6` | `WorkoutRepositoryConcurrencyTest.testDefensiveDeletionReporting_failureAndExceptionResetIdle` |
| `REQ-STB-018.4` | Success reporting (`Pair(id, true)`) and guaranteed `Idle` | `TST-STB-018.6` | Concurrency Unit Test |
| `REQ-STB-018` | Aftermath concurrency invariants and build stability | `TST-STB-018` | Targeted Unit Test Suite & Governance Parity |
