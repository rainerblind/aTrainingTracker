# Stage 1 Analysis: ATT-4018 - [Bug] [Medium] Fix aftermath loader races, process-death restore and stale gesture data

**Ticket**: [ATT-4018](https://rainerblind.atlassian.net/browse/ATT-4018)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.5` | **Branch**: `feature/ATT-4018`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`) | **Date**: 2026-10-21  

---

## 1. Problem Statement & Motivation

* **Uncancelled Loader Races**: Switching workouts rapidly in aftermath or cluster/period peeks launches concurrent coroutines without cancelling previous jobs, causing older loads to overwrite newer selections with ghost data.
* **Process-Death Blank Details**: On process recreation, `selectedWorkoutIdForDetails` is restored via `rememberSaveable`, but `TrackOnMapAftermathViewModel.loadAftermathData` was only wired to `onMapClick`, leaving the restored screen blank (empty map, no tracks/zones).
* **Stale Gesture Data in Elevation Profile**: `ElevationProfile.pointerInput` captures initial `pathPoints` without `rememberUpdatedState`. When high-resolution telemetry loads, scrubbing reads stale fast-path points without HR/power.
* **Lifecycle & Double-Tap Hazards**: `CropWorkoutViewModel` sets `isCropping` asynchronously inside `viewModelScope.launch` allowing double-tap races; `WorkoutRepository.cropWorkout` swallows `CancellationException`; and `deleteOldWorkouts` in `viewModelScope` leaves database caches inconsistent if the user departs mid-deletion.
* **ViewModel Leaks**: `EditWorkoutViewModel` instances keyed by workout ID accumulate indefinitely in the parent `ViewModelStore`.

---

## 2. Root Cause Analysis (Forensic Investigation)

| Component | Target File & Lines | Forensic Root Cause |
| :--- | :--- | :--- |
| **Aftermath Loader** | `TrackOnMapAftermathViewModel.kt:95` | `loadAftermathData` launches on `viewModelScope` without tracking/cancelling previous `Job`. Older background fetches overwrite `_uiState`. |
| **Peek Loaders** | `WorkoutClustersViewModel.kt:384`, `PeriodsViewModel.kt:220` | `selectWorkoutForPeek` launches uncancelled coroutines; out-of-order completions corrupt peek sheet state. |
| **Process-Death Restore** | `WorkoutSummariesTabbedScreen.kt:230,321` | `selectedWorkoutIdForDetails` restored via `rememberSaveable`, but `loadAftermathData` only invoked in `onMapClick`; never triggered on restore. |
| **Stale Gesture Capture** | `ElevationProfile.kt:498,592,636` | `pointerInput(totalSpan, ...)` captures `pathPoints` by reference without `rememberUpdatedState`; scrubbing reads initial thinned fast-track. |
| **Double-Tap & Cancellation** | `CropWorkoutViewModel.kt:128`, `WorkoutRepository.kt:1388` | `isCropping = true` set inside launched coroutine (race window); `cropWorkout` catches `Throwable` without re-throwing `CancellationException`. |
| **Bulk Deletion Scope** | `WorkoutSummariesViewModel.kt:257`, `WorkoutRepository.kt:2030` | `deleteOldWorkouts` runs in UI `viewModelScope`; navigating away cancels cache resync (`resyncAllPeriods`, `refreshClusters`). |
| **VM Accumulation** | `WorkoutSummariesTabbedScreen.kt:351` | `viewModel(key = "edit_workout_$id")` registers into parent Activity/Fragment `ViewModelStore` without lifecycle cleanup on dismissal. |

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Job cancellation in `TrackOnMapAftermathViewModel.loadAftermathData`, `WorkoutClustersViewModel.selectWorkoutForPeek`, and `PeriodsViewModel.selectWorkoutForPeek`.
  2. Reactive aftermath data loading in `WorkoutSummariesTabbedScreen.kt` upon process-death restore via `LaunchedEffect`.
  3. Wrap `pathPoints` in `rememberUpdatedState` in `ElevationProfile.kt` for live scrubbing accuracy.
  4. Synchronous double-tap guard in `CropWorkoutViewModel`, `CancellationException` rethrow in `WorkoutRepository.cropWorkout`, and `NonCancellable` protection for bulk deletion resync.
  5. Lifecycle scoping / cleanup for `EditWorkoutViewModel` on dialog dismissal.
* **Out-of-Scope Non-Goals**:
  1. No modifications to database schemas or SQLite table structures.
  2. No changes to cluster calculation algorithms or map marker clustering logic.
  3. No changes to crop range math or coordinate projection algorithms.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

* **Target Requirements**: Net-new requirement `REQ-UI-427` (*Aftermath Loader Concurrency, Process-Death Detail Restoration, Elevation Gesture State Integrity and Lifecycle Safety*), reinforcing `REQ-MAP-032` (*Scrubber Synchronization*), `REQ-DAT-011` (*Period Cache Resynchronization*), and `REQ-UI-368` (*Workout Cropping*).
* **Historical Origin**: `REQ-MAP-032` (ATT-2340, `1faa8656`), `REQ-DAT-011` (ATT-987), `REQ-UI-368` (ATT-3274).
* **Root Reason for Existing Formulation**: Scrubber synchronization assumed static track models; bulk operations assumed single-screen retention; aftermath loading was originally tied directly to click navigation events.
* **Preservation of Core Invariants**: Retains single-pass telemetry decoding, full-span elevation scrubbing, atomic SQLite transactions, and downstream period/cluster synchronization without altering UI contracts.

---

## 5. Architectural Strategy & High-Level Solution

* **Loader Cancellation Pattern**: Introduce `loadJob: Job? = null` in ViewModels; cancel previous job prior to starting new loads.
* **Process-Death Reactive Loader**: Add `LaunchedEffect(selectedWorkoutForDetails?.id)` in `WorkoutSummariesTabbedScreen.kt` to load aftermath data whenever a workout is selected and tracks are missing.
* **Updated State for Gestures**: Use `val currentPathPoints by rememberUpdatedState(pathPoints)` in `ElevationProfile.kt` so pointer loops always reference the latest telemetry.
* **Lifecycle & Concurrency Guards**: Set `isCropping = true` synchronously before dispatch; re-throw `CancellationException` in `WorkoutRepository.kt`; wrap cache resync in `NonCancellable`.
* **Scoped ViewModel Owner**: Clear or dispose `EditWorkoutViewModel` using a disposable store owner or explicit removal on dialog exit.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**: Zero regressions in aftermath rendering; preserve existing unit test pass rate; maintain structured concurrency and database transaction boundaries.
* **Risk Rating**: **MEDIUM** — Touches multiple ViewModels, gesture event loops, and coroutine scopes; low blast radius on database layer; thoroughly verifiable via unit and contract tests.
