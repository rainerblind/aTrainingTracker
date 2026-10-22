# Stage 3: Implementation Plan - ATT-4002: [Bug] [High] WorkoutRepository loses concurrent updates and mutates state with side effects

**Ticket**: [ATT-4002](https://rainerblind.atlassian.net/browse/ATT-4002) | **Sub-task**: Pending Submission (`[Impl-Plan]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.5` | **Branch**: `feature/ATT-4002`  
**Requirement Mapping**: `REQ-STB-018` (amending `REQ-STB-013`) | **Test Mapping**: `TST-STB-018`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`) | **Date**: 2026-10-21  

---

## 1. Problem Description & Background
1. `reloadWorkoutData`: `reloadingWorkoutIds.add(workoutId)` drops concurrent notifications (e.g. `TRACKING_FINISHED_INTENT` arriving while reload is in-flight), leaving finished sessions unfinalized in memory and bypassing downstream analytics (`PeriodsRepository`, `WorkoutClusterEngine`).
2. `loadAllWorkouts`: non-thread-safe plain boolean `isListLoading`; wholesale overwrite `_allWorkouts.value = allLoadedWorkouts.toList()` resurrecting workouts deleted in memory during cursor scanning; missing unconditional terminal emission dropping partial batches when rows throw exceptions; unshielded Step 3 row mapping.
3. `updateWorkoutInMemory`: launches coroutines (`launch(Dispatchers.IO)`) inside `_allWorkouts.update` CAS retry loop, multiplying coroutines on contention; `saveWorkout` mutates external closure state (`oldWorkout`) inside `update`.
4. `deleteWorkout`: posts `Pair(id, true)` unconditionally on failure; lacks `try/catch/finally`, leaving `_deletionProgress` permanently stuck in `InProgress` on SQLite exceptions.

---

## 2. Proposed Architectural Changes (SWE.2)

| Layer / Class | Architectural Responsibility & Modification |
| :--- | :--- |
| **Conflated Reload**<br>`WorkoutRepository.kt` | Maintain thread-safe `activeReloadIds` and `dirtyReloadIds` (`ConcurrentHashMap.newKeySet<Long>()`). If `workoutId` is active, mark dirty and return; active loop executes `do { dirtyReloadIds.remove(id); ... } while (dirtyReloadIds.remove(id))` in `try/finally` clearing `activeReloadIds`. Guaranteed sequential pass reads latest DB state and invokes `PeriodsRepository.onWorkoutFinished` / `WorkoutClusterEngine.onWorkoutFinished`. |
| **Streaming & Stale Defense**<br>`WorkoutRepository.kt` | Replace `isListLoading` with `AtomicBoolean(false)`. Maintain `deletedDuringLoadIds` concurrent set. Progressive emissions and terminal emission merge loaded sessions via `_allWorkouts.update`, filtering out `deletedDuringLoadIds` and preserving concurrent in-memory updates. Wrap Step 3 row mapping and export status creation in `try/catch`. Execute unconditional terminal emission after cursor loop. |
| **Pure Functional CAS**<br>`WorkoutRepository.kt` | In `updateWorkoutInMemory`, check `_allWorkouts.value` first: if present, perform pure in-place mapping with zero launches; if absent, fetch asynchronously on `Dispatchers.IO` outside CAS lambda. In `saveWorkout`, snapshot `oldWorkout` before entering `_allWorkouts.update`. |
| **Defensive Deletion**<br>`WorkoutRepository.kt` | Record deleted ID in `deletedDuringLoadIds` if loading. Enclose coroutine body in `try ... catch ... finally`: post `Pair(id, true)` on success, `Pair(id, false)` on failure/exception; `finally` resets `_deletionProgress.postValue(DeletionProgress.Idle)`. |

### UI Consistency (Rule 23)
* **Reference**: `WorkoutTabsScreen.kt` (`WorkoutDeletionProgressDialog`). Zero UI layout changes; guarantees progress dialog dismissal and consistent workout list state.

---

## 3. Step-by-Step Implementation Sequence (Stage 4)

### Step 1: Conflated Reload Pipeline in `reloadWorkoutData`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepository.kt`
* Define `activeReloadIds` and `dirtyReloadIds`.
* Implement dirty-flag conflation loop in `reloadWorkoutData` ensuring sequential re-query and lifecycle observer invocation on `finished` transition.

### Step 2: Thread-Safe Streaming, Row Exception Shielding & Deletion Resurrection Defense
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepository.kt`
* Guard `loadAllWorkouts` via `isListLoading = AtomicBoolean(false)`. Track `deletedDuringLoadIds`.
* Wrap Step 3 row mapping in `try/catch`. Update `_allWorkouts` progressively and execute unconditional terminal emission merging active memory and filtering deleted IDs.

### Step 3: Pure Functional CAS Transformations in `updateWorkoutInMemory` and `saveWorkout`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepository.kt`
* Move background DB fetching outside `_allWorkouts.update` in `updateWorkoutInMemory`.
* Snapshot `oldWorkout` outside `_allWorkouts.update` in `saveWorkout`.

### Step 4: Accurate Deletion Reporting & Guaranteed Progress Reset
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepository.kt`
* Add `deletedDuringLoadIds.add(id)` if loading.
* Wrap `deleteWorkout` in `try ... catch ... finally`: post accurate boolean result and guarantee `DeletionProgress.Idle` reset.

### Step 5: Concurrency Unit Verification (`TST-STB-018.1..6`)
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepositoryConcurrencyTest.kt`
* Verify latch-paused conflated reload, delete-during-stream defense, skipped row recovery, atomic guard rejection, pure CAS contract, and defensive deletion reporting.
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepositoryConcurrencyTest"`

---

## 4. System Invariants & Rollback Plan
* **Invariants**: Monotonic forward-only cursor traversal (`REQ-STB-013`); single source of truth in `_allWorkouts`; lifecycle transition guarantee; pure CAS retry invariant; progress dialog cleanliness; 100% test pass rate.
* **Rollback Plan**: Changes confined to `feature/ATT-4002`; branch isolation allows clean checkout or git revert.

---

## 5. Traceability Matrix

| Requirement | Implementation Step | Test Case | Target Artifact |
| :--- | :--- | :--- | :--- |
| `REQ-STB-018.1` (Conflated Reload) | Step 1 | `TST-STB-018.1` | `WorkoutRepository.reloadWorkoutData` |
| `REQ-STB-018.2` (Safe Streaming & Stale Defense) | Step 2 | `TST-STB-018.2..4` | `WorkoutRepository.loadAllWorkouts` |
| `REQ-STB-018.3` (Pure Functional CAS) | Step 3 | `TST-STB-018.5` | `WorkoutRepository.updateWorkoutInMemory` / `saveWorkout` |
| `REQ-STB-018.4` (Defensive Deletion) | Step 4 | `TST-STB-018.6` | `WorkoutRepository.deleteWorkout` |

