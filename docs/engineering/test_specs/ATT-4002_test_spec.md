# Stage 2: Requirement & Test Specification - ATT-4002: [Bug] [High] WorkoutRepository loses concurrent updates and mutates state with side effects

**Ticket**: [ATT-4002](https://rainerblind.atlassian.net/browse/ATT-4002) | **Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111)  
**Target Release**: `V4.9.40` | **Sprint**: `2026-43.5` | **Branch**: `feature/ATT-4002`  
**Requirement Mapping**: `REQ-STB-018` (amending `REQ-STB-013`) | **Test Spec ID**: `TST-STB-018`  
**Author**: AI Agent 1 (Implementer - `subagent_flash`) | **Date**: 2026-10-21  

---

## 1. Requirement Specification (REQ-STB-018)

### 1.1 Problem Statement & Rationale
`WorkoutRepository` loses concurrent updates, drops reloads under burst broadcasts, resurrects deleted workouts during history streaming, drops partial batches on skipped rows, launches coroutines inside CAS retry loops, and hangs deletion progress dialogs on failure.

### 1.2 Functional & Architectural Requirements
1. **Conflated Per-Workout Reload Pipeline (`REQ-STB-018.1`)**: `reloadWorkoutData(workoutId)` SHALL mark active IDs dirty (`dirtyReloadIds.add(workoutId)`) when busy and loop sequentially until clean. Finalization transitions (`finished == true`) SHALL trigger `PeriodsRepository.onWorkoutFinished` and `WorkoutClusterEngine.onWorkoutFinished`.
2. **Thread-Safe Streaming & Stale Snapshot Defense (`REQ-STB-018.2`)**: `loadAllWorkouts()` SHALL use `AtomicBoolean(isListLoading)`. Progressive and terminal emissions into `_allWorkouts` SHALL merge with active in-memory state without resurrecting workouts deleted via `deleteWorkout`. Unconditional terminal emission SHALL publish accumulated sessions even if rows are skipped. Mapping and export resolution in Step 3 SHALL be shielded by `try/catch`.
3. **Pure Functional CAS Invariants (`REQ-STB-018.3`)**: Blocks passed to `_allWorkouts.update` SHALL be pure functions. Zero coroutine launches, DB queries, or external mutations inside CAS retry blocks. In `updateWorkoutInMemory`, missing sessions are fetched outside CAS. In `saveWorkout`, previous state is snapshotted outside CAS.
4. **Accurate Deletion Reporting & Guaranteed Idle Reset (`REQ-STB-018.4`)**: `deleteWorkout(id)` SHALL post `Pair(id, true)` on success, `Pair(id, false)` on failure/exception. Entire body enclosed in `try ... finally` guaranteeing `_deletionProgress.postValue(DeletionProgress.Idle)`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Conflated Reload)**: *Given* active reload of workout 101L, *When* `TRACKING_FINISHED_INTENT` arrives, *Then* 101L is marked dirty, re-queried sequentially, `finished == true` is set, and lifecycle observers are notified.
* **Criterion 2 (Deletion Resurrection Defense)**: *Given* `loadAllWorkouts()` streaming, *When* `deleteWorkout(1L)` executes, *Then* subsequent emissions do not resurrect 1L.
* **Criterion 3 (Terminal Emission & Exception Shielding)**: *Given* row 5 throws during reading, *When* streaming, *Then* exception is caught, valid rows continue, and unconditional terminal emission publishes all valid workouts.
* **Criterion 4 (Atomic Guard)**: *Given* `loadAllWorkouts()` running, *When* concurrent call occurs, *Then* second call is atomically rejected.
* **Criterion 5 (Pure CAS)**: *Given* CAS contention on `_allWorkouts`, *When* updating, *Then* zero coroutines or I/O execute inside update lambda.
* **Criterion 6 (Deletion Reset)**: *Given* deletion failure or exception, *When* `deleteWorkout` executes, *Then* `Pair(id, false)` is posted and `_deletionProgress` resets to `Idle`.

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: Net-new requirement `REQ-STB-018`, amending `REQ-STB-013`. Targets `WorkoutRepository.kt`.
* `Historical Origin & Commit Trace`: Sprint 2026-40.16 (`ATT-2309`, commit `3e710f3c`) and Sprint 2026-38 (`ATT-318` / `ATT-669`, commit `b7e305e2`). Discovered during whole-app architecture review (`feature/ATT-3921` @ `0234bfc0`), tracked in `ATT-4002`.
* `Root Reason for Existing Formulation`: Throttled bursts via `add()` early return without retry; direct list assignment for progressive pumping; launching coroutines in CAS lambda assuming simple lock; omission of `try/finally` and wrong boolean reporting.
* `Preservation of Core Invariants`: Monotonic forward-only cursor traversal (`c.moveToNext()`), vectorized batch metadata queries, single source of truth in `_allWorkouts`, query deduplication under burst broadcasts, and 100% full-suite test pass rate remain strictly preserved.

---

## 2. Test Specification (TST-STB-018)

### Test Case 1: Conflated Per-Workout Reload & Lifecycle Callback (`[TST-STB-018.1]`)
* **Scope**: Unit Test (`WorkoutRepositoryConcurrencyTest.kt`)
* **Preconditions**: Mocked DB, `PeriodsRepository`, `WorkoutClusterEngine`. Workout 101L in memory with `finished = false`.
* **Action**: Trigger `reloadWorkoutData(101L)` paused on latch. Mock DB `finished = true`, trigger second reload. Release latch.
* **Expected Result**: Second reload marks dirty; runs sequentially; in-memory `finished = true`; lifecycle callbacks invoked.

### Test Case 2: Concurrent Delete-During-Stream Stale Overwrite Defense (`[TST-STB-018.2]`)
* **Scope**: Unit Test (`WorkoutRepositoryConcurrencyTest.kt`)
* **Preconditions**: 120 workouts across chunks.
* **Action**: Launch `loadAllWorkouts()`. After batch 1, call `deleteWorkout(1L)` (item from batch 1). Complete stream.
* **Expected Result**: Workout 1L is absent from `allWorkouts.value`; no resurrection.

### Test Case 3: Skipped Row Recovery & Guaranteed Terminal Batch Emission (`[TST-STB-018.3]`)
* **Scope**: Unit Test (`WorkoutRepositoryConcurrencyTest.kt`)
* **Preconditions**: 65 workouts. Row 5 throws exception in `readCursorSnapshot`.
* **Action**: Execute `loadAllWorkouts()`.
* **Expected Result**: Exception logged; remaining 64 valid workouts processed; terminal emission publishes all 64 items.

### Test Case 4: Atomic Loading Guard Concurrency Protection (`[TST-STB-018.4]`)
* **Scope**: Unit Test (`WorkoutRepositoryConcurrencyTest.kt`)
* **Preconditions**: `WorkoutRepository` initialized.
* **Action**: Trigger `loadAllWorkouts()` holding suspension. Call `loadAllWorkouts()` a second time concurrently.
* **Expected Result**: Second call returns immediately without executing cursor iteration.

### Test Case 5: Pure Functional CAS Transformation Contract (`[TST-STB-018.5]`)
* **Scope**: Unit Test (`WorkoutRepositoryConcurrencyTest.kt`)
* **Preconditions**: Known workouts in repository.
* **Action**: Invoke `updateWorkoutInMemory(id)` for existing and un-cached workouts under simulated CAS contention.
* **Expected Result**: Zero coroutines launched inside `_allWorkouts.update`; missing workout DB query executes outside CAS lambda.

### Test Case 6: Defensive Deletion Reporting (`[TST-STB-018.6]`)
* **Scope**: Unit Test (`WorkoutRepositoryConcurrencyTest.kt`)
* **Preconditions**: `deleteFinishedEvent` observed.
* **Action**: Mock deletion returning `false`, call `deleteWorkout(id)`. Mock deletion throwing exception, call `deleteWorkout(id)`.
* **Expected Result**: In both cases: `deleteFinishedEvent` receives `Pair(id, false)`; `deletionProgress` resets to `DeletionProgress.Idle`.

### Test Case 7: 9-Language Localization Parity (`[TST-STB-018.7]`)
* **Scope**: Localization Parity Test (`TranslationParityTest.kt`)
* **Expected Result**: 100% parity across all 9 locales.

### Test Case 8: Clean-Room Regression Suite (`[TST-STB-018.8]`)
* **Command**: `./gradlew testDebugUnitTest`
* **Expected Result**: 100% pass rate across the full test suite.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-STB-018.1]` | Unit | `WorkoutRepository.reloadWorkoutData` | `REQ-STB-018.1` | Planned |
| `[TST-STB-018.2]` | Unit | `WorkoutRepository.loadAllWorkouts` | `REQ-STB-018.2` | Planned |
| `[TST-STB-018.3]` | Unit | `WorkoutRepository.loadAllWorkouts` | `REQ-STB-018.2` | Planned |
| `[TST-STB-018.4]` | Unit | `WorkoutRepository.loadAllWorkouts` | `REQ-STB-018.2` | Planned |
| `[TST-STB-018.5]` | Unit | `WorkoutRepository.updateWorkoutInMemory` | `REQ-STB-018.3` | Planned |
| `[TST-STB-018.6]` | Unit | `WorkoutRepository.deleteWorkout` | `REQ-STB-018.4` | Planned |
| `[TST-STB-018.7]` | Localization | `TranslationParityTest` | `REQ-UI-106`, `REQ-STB-018` | Planned |
| `[TST-STB-018.8]` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001`, `REQ-STB-018` | Planned |
