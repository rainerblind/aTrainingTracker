# Stage 3: Implementation Plan - ATT-5565: Eliminate Race Condition Between deleteFinishedEvent and _deletionProgress.Idle

**Ticket**: [ATT-5565](https://rainerblind.atlassian.net/browse/ATT-5565)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*No crashs*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.8` | **Branch**: `feature/ATT-5565`  
**Requirement Mapping**: `REQ-STB-018` (`REQ-STB-018.4`)  
**Test Mapping**: `TST-STB-018` (`TST-STB-018.6`)  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-06  

---

## 1. Problem Description & Background
Under heavy CPU load during Tier 2 unit test suite execution, `WorkoutRepositoryConcurrencyTest.testDefensiveDeletionReporting_failureAndExceptionResetIdle` failed sporadically with `AssertionError: Progress must reset to Idle on failure expected:<DeletionProgress> but was:<InProgress(...)>`. In `WorkoutRepository.deleteWorkout`, `deleteFinishedEvent.postValue(Pair(id, false))` was dispatched in `try`/`catch` before the `finally` block executed `_deletionProgress.postValue(DeletionProgress.Idle)`. Under `InstantTaskExecutorRule`, the test observer woke up on event delivery and asserted `deletionProgress` before the asynchronous `Dispatchers.IO` thread reached `finally`, violating Rule 74 (`docs/coding_standards.md` §5.5).

---

## 2. Traceability & Requirements Mapping
* **Requirements**: `REQ-STB-018.4` (Rule 74 progress reset precedence before completion events; defensive deletion reporting).
* **Test Mapping**: `TST-STB-018.6` (Defensive deletion reporting & Rule 74 completion ordering unit test in `WorkoutRepositoryConcurrencyTest.kt`).

---

## 3. System Invariants & Preserved Behavior
1. **Accurate Deletion Reporting**: `deleteFinishedEvent` posts `Pair(id, true)` on DB deletion success, and `Pair(id, false)` on failure or exception.
2. **Guaranteed Idle Reset**: `_deletionProgress` unconditionally resets to `DeletionProgress.Idle` under all exit paths.
3. **Zero Stale Progress Reads (Rule 74)**: Any observer awakened by `deleteFinishedEvent` is guaranteed to observe `_deletionProgress == DeletionProgress.Idle`.
4. **Code Lock (Rule 4)**: Zero production code edits in `app/src/` until Gate 3 passes audit.

---

## 4. Proposed Architectural Changes
### Component 1: `WorkoutRepository.kt` (`ui/aftermath/`)
* In `deleteWorkout(id: Long)`:
  * Initialize `var success = false`.
  * In `try`: perform DB deletion `success = deletionHelper.deleteWorkout(id)` and trigger surgical updates/in-memory cleanup if `success`.
  * In `catch (e: Exception)`: log error and ensure `success = false`.
  * In `finally`: execute `_deletionProgress.postValue(DeletionProgress.Idle)` followed by `deleteFinishedEvent.postValue(Pair(id, success))`.

### Component 2: `WorkoutRepositoryConcurrencyTest.kt`
* In `testDefensiveDeletionReporting_failureAndExceptionResetIdle`:
  * Observe `deletionProgress` and `deleteFinishedEvent` to verify that when `deleteFinishedEvent` posts, `deletionProgress.value` is guaranteed `Idle`.
  * Maintain clean observer deregistration in test `finally`.

### UI Consistency (Rule 23)
* No UI changes (repository concurrency and unit test synchronization only).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
### Step 1: Update `WorkoutRepository.kt` (`REQ-STB-018.4`)
* Restructure `deleteWorkout(id)` to order `_deletionProgress.postValue(DeletionProgress.Idle)` before `deleteFinishedEvent.postValue(Pair(id, success))` in `finally`.

### Step 2: Update `WorkoutRepositoryConcurrencyTest.kt` (`TST-STB-018.6`)
* Align test assertions and synchronization with the guaranteed Rule 74 ordering.

### Step 3: Verification Gate (`TST-STB-018.6`)
* Run targeted unit test suite and governance checks:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepositoryConcurrencyTest"
  python3 tools/verify_requirement_governance.py
  ```

---

## 6. Verification & Rollback Plan
* **Verification**: Repeated execution of `WorkoutRepositoryConcurrencyTest` to confirm elimination of race condition flake.
* **Rollback**: Revert working tree via `git checkout feature/ATT-5565 -- .`.
