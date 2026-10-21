# Stage 1 Analysis: ATT-4002 - [Bug] [High] WorkoutRepository loses concurrent updates and mutates state with side effects

**Ticket**: [ATT-4002](https://rainerblind.atlassian.net/browse/ATT-4002)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.5` | **Branch**: `feature/ATT-4002`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`) | **Date**: 2026-10-21  

---

## 1. Problem Domain & Root Cause Analysis

1. **Dropped Concurrent Reloads (`reloadWorkoutData`)**:
   - `reloadingWorkoutIds.add(workoutId)` drops concurrent notifications (e.g. `TRACKING_FINISHED_INTENT` arriving while `WORKOUT_UPDATED_INTENT` is processing) without scheduling a retry.
   - If the first query ran before SQLite committed `FINISHED = 1`, the session remains `finished = false` in memory, and downstream analytics (`PeriodsRepository.onWorkoutFinished`, `WorkoutClusterEngine.onWorkoutFinished`) never run.
2. **Progressive History Streaming Overwrite & Dropped Batches (`loadAllWorkouts`)**:
   - `isListLoading` is a non-thread-safe plain boolean variable subject to check-then-act races.
   - Progressive batch emissions execute `_allWorkouts.value = allLoadedWorkouts.toList()`, overwriting active memory and resurrecting workouts deleted (`deleteWorkout(id)`) while the cursor scanned.
   - Emitting on `processedCount == totalCount` drops the final partial batch when intermediate rows throw extraction exceptions and are skipped, lacking an unconditional terminal emission.
   - Row mapping (`mapper.fromSnapshot`) and export statuses in Step 3 are unshielded by `try/catch`.
3. **Impure CAS Lambdas in `MutableStateFlow.update`**:
   - `updateWorkoutInMemory` launches coroutines (`launch(Dispatchers.IO)`) inside `_allWorkouts.update { ... }`. In contention retries, this multiplies coroutines and redundant DB queries.
   - `saveWorkout` captures and mutates external closure state (`oldWorkout = current`) inside `_allWorkouts.update`.
4. **False Deletion Reporting & Stuck Progress Dialog (`deleteWorkout`)**:
   - `deleteFinishedEvent.postValue(Pair(id, true))` is posted unconditionally on failure (`else` branch).
   - Missing `try ... finally` leaves `_deletionProgress` stuck in `DeletionProgress.InProgress` on exception, hanging UI progress dialogs indefinitely.

---

## 2. Call Site & Blast Radius Audit

| Component | Target File | Nature of Change & Impact |
| :--- | :--- | :--- |
| **Workout Repository** | `WorkoutRepository.kt` | Conflated dirty-flag reload loop; atomic loading guard; merge with active deletions; unconditional terminal emission; pure CAS lambdas; safe deletion reporting in `try/finally`. |
| **Workout Summaries** | `WorkoutSummariesViewModel.kt` | Consumes `allWorkouts` and deletion events; receives accurate completion events without resurrection. |
| **Downstream Observers** | `PeriodsRepository.kt`, `WorkoutClusterEngine.kt` | Deterministically notified via `onWorkoutFinished` when tracking completes during active reloads. |
| **UI Progress Dialog** | `WorkoutTabsScreen.kt` | `WorkoutDeletionProgressDialog` guaranteed to dismiss without hanging. |

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Conflated per-workout dirty-flag reload loop in `reloadWorkoutData`.
  2. `AtomicBoolean` guard, safe deletion merging, row exception shielding, and unconditional terminal emission in `loadAllWorkouts`.
  3. Pure CAS lambdas in `updateWorkoutInMemory` and `saveWorkout` with zero coroutine launches inside `update`.
  4. Accurate boolean reporting and guaranteed `DeletionProgress.Idle` reset via `try ... finally` in `deleteWorkout`.
* **Out-of-Scope Non-Goals**:
  1. No changes to SQLite schema or column definitions.
  2. No changes to `WorkoutData` domain model or `WorkoutDataMapper` mapping algorithms.
  3. No changes to live sensor recording or tracking foreground services.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

* **Original Requirement ID & Target**: Net-new requirement `REQ-STB-018`, amending `REQ-STB-013`. Targets `WorkoutRepository.kt`.
* **Historical Origin & Commit Trace**: Sprint 2026-40.16 (`ATT-2309`, commit `3e710f3c`) and Sprint 2026-38 (`ATT-318` / `ATT-669`, commit `b7e305e2`). Discovered during whole-app code review of `feature/ATT-3921` (`0234bfc0`).
* **Root Reason for Existing Formulation**: Throttled bursts via `add()` early return without retry; direct list assignment for progressive pumping; launching coroutines in CAS lambda assuming simple lock; omission of `try/finally` and wrong boolean reporting.
* **Preservation of Core Invariants**: Monotonic forward-only cursor traversal (`c.moveToNext()`), vectorized batch metadata queries, single source of truth in `_allWorkouts`, query deduplication under burst broadcasts, and 100% full-suite test pass rate remain strictly preserved.

---

## 5. System Invariants & Risk Assessment

* **Core Invariants**: Monotonic forward-only cursor traversal (`REQ-STB-013`); single source of truth in `_allWorkouts`; lifecycle transition guarantee (`onWorkoutFinished`); pure CAS retry invariant; progress dialog cleanliness.
* **Risk Rating**: **HIGH** — `WorkoutRepository` is the central architectural data nexus feeding workout lists, summary cards, aftermath maps, elevation profiles, and clustering. Strict TDD concurrency verification required.
