# Stage 3: Implementation Plan - ATT-4018: Fix aftermath loader races, process-death restore and stale gesture data

**Ticket**: [ATT-4018](https://rainerblind.atlassian.net/browse/ATT-4018) | **Sub-task**: Pending Submission (`[Impl-Plan]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.5` | **Branch**: `feature/ATT-4018`  
**Requirement Mapping**: `REQ-UI-427` (reinforcing `REQ-MAP-032`, `REQ-DAT-011`, `REQ-UI-368`) | **Test Mapping**: `TST-UI-392`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`) | **Date**: 2026-10-21  

---

## 1. Architecture & Invariant Compliance

* **SWE.2 Architecture (`REQ-UI-427`)**:
  1. *Loader Coroutine Cancellation (`REQ-UI-427.1`)*: Introduce `loadJob: Job?` in `TrackOnMapAftermathViewModel` and `peekJob: Job?` in `WorkoutClustersViewModel` & `PeriodsViewModel`. Cancel active job before launching background fetch.
  2. *Process-Death Detail Restoration (`REQ-UI-427.2`)*: In `WorkoutSummariesTabbedScreen`, add `LaunchedEffect(selectedWorkoutIdForDetails)` to trigger `loadAftermathData` if details are selected but unpopulated.
  3. *Elevation Profile Gesture State Integrity (`REQ-UI-427.3`)*: In `ElevationProfile.kt`, wrap `pathPoints` in `val currentPathPoints by rememberUpdatedState(pathPoints)` and read in drag/tap handlers.
  4. *Destructive Operation Lifecycle Protection (`REQ-UI-427.4`)*: In `CropWorkoutViewModel`, set `isCropping = true` synchronously before dispatch. In `WorkoutRepository.cropWorkout`, rethrow `CancellationException`. In `deleteOldWorkouts`, wrap downstream cache resync in `withContext(NonCancellable)`.
  5. *ViewModel Scope Lifecycle Management (`REQ-UI-427.5`)*: Clear or scope `EditWorkoutViewModel` store owner in `WorkoutSummariesTabbedScreen` upon dialog dismissal (`selectedWorkoutIdForEdit = null`).
* **Core Invariants & Preserved Behavior (`REQ-UI-427.6`)**:
  1. *Zero SQLite Schema Migrations*: Purely presentation and repository coroutine lifecycle fixes.
  2. *UI Visual Baseline (Rule 23)*: No UI layout changes; preserves existing Material 3 styling.
  3. *Clean-Room Stability*: 100% test pass rate across existing test suites.

---

## 2. Atomic Implementation Steps

* **Step 1 — In-Flight Loader Cancellation (`REQ-UI-427.1`)**:
  * In `TrackOnMapAftermathViewModel.kt`: cancel `loadJob?.cancel()` before `viewModelScope.launch(ioDispatcher)`.
  * In `WorkoutClustersViewModel.kt` & `PeriodsViewModel.kt`: cancel `peekJob?.cancel()` before querying track/markers.
* **Step 2 — Process-Death Reactive Restore (`REQ-UI-427.2`)**:
  * In `WorkoutSummariesTabbedScreen.kt`: add `LaunchedEffect(selectedWorkoutIdForDetails)` triggering `loadAftermathData` when restored.
* **Step 3 — Elevation Profile Gesture State Integrity (`REQ-UI-427.3`)**:
  * In `ElevationProfile.kt`: declare `val currentPathPoints by rememberUpdatedState(pathPoints)`; replace direct reads in `pointerInput`.
* **Step 4 — Destructive Double-Tap & Cancellation Safety (`REQ-UI-427.4`)**:
  * In `CropWorkoutViewModel.kt`: synchronously execute `_uiState.update { copy(isCropping = true) }` before launching coroutine.
  * In `WorkoutRepository.kt`: rethrow `CancellationException` in `cropWorkout`; wrap cache resync in `NonCancellable` in `deleteOldWorkouts`.
* **Step 5 — Scoped EditWorkoutViewModel Lifecycle (`REQ-UI-427.5`)**:
  * In `WorkoutSummariesTabbedScreen.kt`: scope `EditWorkoutViewModel` to dialog lifecycle or clear store on dismissal.
* **Step 6 — Unit & Contract Tests (`TST-UI-392.1`–`TST-UI-392.8`)**:
  * Update/add tests in `TrackOnMapAftermathViewModelTest`, `CropWorkoutViewModelTest`, `WorkoutRepositoryCropTest`, `WorkoutRepositoryDeleteTest`.

---

## 3. File-Level Change Matrix

| File Path | Role | Nature of Modifications |
| :--- | :--- | :--- |
| `app/.../ui/map/TrackOnMapAftermathViewModel.kt` | Production | `loadJob: Job?` cancellation before launching `loadAftermathData`. |
| `app/.../ui/clusters/WorkoutClustersViewModel.kt` | Production | `peekJob: Job?` cancellation before launching `selectWorkoutForPeek`. |
| `app/.../ui/aftermath/periodlist/PeriodsViewModel.kt` | Production | `peekJob: Job?` cancellation before launching `selectWorkoutForPeek`. |
| `app/.../ui/aftermath/workoutlist/WorkoutSummariesTabbedScreen.kt` | Production | Reactive `LaunchedEffect` restore; scope/clear `EditWorkoutViewModel`. |
| `app/.../ui/map/ElevationProfile.kt` | Production | `rememberUpdatedState(pathPoints)` for live telemetry scrubbing. |
| `app/.../ui/aftermath/crop/CropWorkoutViewModel.kt` | Production | Synchronous `isCropping = true` update before coroutine launch. |
| `app/.../ui/aftermath/WorkoutRepository.kt` | Production | Rethrow `CancellationException` in crop; `NonCancellable` bulk delete resync. |
| `app/.../ui/map/TrackOnMapAftermathViewModelTest.kt` | Test | Unit test asserting previous `loadJob` cancellation on rapid switches. |
| `app/.../ui/aftermath/crop/CropWorkoutViewModelTest.kt` | Test | Unit test asserting synchronous double-tap rejection in `executeCrop`. |
| `app/.../ui/aftermath/WorkoutRepositoryCropTest.kt` | Test | Unit test asserting `CancellationException` propagation in `cropWorkout`. |
| `app/.../ui/aftermath/WorkoutRepositoryDeleteTest.kt` | Test | Unit test asserting `NonCancellable` completion of cache resynchronization. |

---

## 4. Test Execution Strategy

* **Tier 1 — Per-Ticket Verification (Stages 4 & 5)**:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.ui.clusters.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*"`
* **Governance Verification**: `python3 tools/verify_requirement_governance.py`

---

## 5. Rollback & Risk Mitigation

* **Risk Level**: **MEDIUM**. Concurrency and lifecycle modifications across 6 components; thoroughly isolated with unit/contract tests.
* **Rollback Plan**: Zero database schema changes; fully revertible via `git revert` or checkout on `feature/ATT-4018`.
