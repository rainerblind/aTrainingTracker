# Stage 1 Analysis: ATT-8569 - PeriodsViewModel Init-Order NPE Crash

**Ticket**: `ATT-8569` (`1 SP`, `severity-critical`, Epic `ATT-67`) | **Target Release**: `V5.0.0` | **Branch**: `feature/ATT-8569`

## 1. Problem Statement & Motivation
Opening the **Periods** screen (`com.atrainingtracker.debug`, observed on physical device `66020DLCR002FL`) immediately crashes on the main thread with an unhandled `NullPointerException` inside `PeriodsViewModel.<init>(PeriodsViewModel.kt:74)` attempting to call `MutableStateFlow.getValue()` on a null reference at `PeriodsViewModel.kt:76`.

## 2. Root Cause Analysis & Independent Peer Review
* **Primary Cause (`PeriodsViewModel.kt:73-84, 402`)**: Commit `608c4e31` (`ATT-7758`) added an `init` block at `PeriodsViewModel.kt:73-84` launching a `viewModelScope` coroutine (`Dispatchers.Main.immediate` on Android) that collects `workoutRepo.allWorkouts` (`StateFlow<List<WorkoutData>>`, `WorkoutRepository.kt:1846`).
* **Synchronous Crash Mechanics**: `collectIgnoringUnstubbedMock` (`WorkoutClusterRepository.kt:56-68`) calls `StateFlowImpl.collect`, which synchronously emits the current state on the caller thread before suspending. Line `76` reads `_peekedWorkoutDataWithTrack.value`, but `_peekedWorkoutDataWithTrack` is declared later at line `402`. Kotlin runs `init` blocks and property initializers in top-to-bottom declaration order, so `_peekedWorkoutDataWithTrack` is still `null`, causing `NullPointerException`.
* **Why Tests Missed It**: `PeriodsViewModelTest.kt:44` uses `StandardTestDispatcher()`, which queues `viewModelScope.launch` until `advanceUntilIdle()` executes after construction completes, masking `Dispatchers.Main.immediate` behavior.
* **Independent Peer Review & Verification (Live `e9766625`)**:
  * Verified every cited line (`PeriodsViewModel.kt:73-84, 402`, `WorkoutClusterRepository.kt:56-68`, `WorkoutRepository.kt:1846`, `PeriodsViewModelTest.kt:44, 642-688`).
  * **Sibling Audit — `WorkoutClustersViewModel.kt`**: `init` (`:299-302`) calls `observeClusterMutations()` (`:312-323`), whose `updatePeekedWorkout` helper (`:304-310`) accesses `_peekedWorkoutDataWithTrack` declared later at line `538`. In production `clusterMutationEvents` is a zero-replay `MutableSharedFlow(extraBufferCapacity = 16)` (`WorkoutClusterRepository.kt:115-116`), so it currently suspends before reading `:538`, but moving `init` after all property initializers removes this latent trap.
  * **Aftermath Sibling Scan**: Checked `CropWorkoutViewModel.kt:44-49` (`_uiState` before `init`), `EditWorkoutViewModel.kt:75-123` (`_workoutData`/state before `init`), and `WorkoutSummariesViewModel.kt:88-141` (`_filterCriteria` before `init`) — all declare `init`-accessed flows prior to `init`.
  * **Other `PeriodsViewModel` Properties**: `enabledMarkerTypes` (`:108`), `groupedPeriods` (`:261`), `migrationStatus` (`:262`), `_selectedSports` (`:265`), `filteredGroupedPeriods` (`:373`), and `migrationProgress` (`:387`) only reference earlier fields; placing `init` after line `403` ensures all properties are initialized beforehand.
  * **Device Availability**: `adb devices` confirms `emulator-5554` (`device`) is connected for Criterion 2 UI verification.

## 3. User Scope Grounding (ATT-1250)
* **In-Scope**: (1) Move `init` in `PeriodsViewModel.kt` after all property initializers (`_peekedWorkoutDataWithTrack` et al.); (2) Defensively move `init` in `WorkoutClustersViewModel.kt` after all property initializers (`_peekedWorkoutDataWithTrack` at `:538`); (3) Add an eager-dispatcher (`UnconfinedTestDispatcher`) regression test in `PeriodsViewModelTest.kt` covering empty and populated `allWorkouts` `StateFlow` plus subsequent reconciliation; (4) Verify Periods screen opens without crash on `emulator-5554`.
* **Out-of-Scope**: Refactoring `collectIgnoringUnstubbedMock`, altering `CropWorkoutViewModel`/`EditWorkoutViewModel`/`WorkoutSummariesViewModel`, changing DB schemas, or modifying `ANT-Android-SDKs`.

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PER-040` / `REQ-PER-040.2` (`TST-PER-048`, `docs/requirements/req_aftermath_periods.md:1321-1349`), targeting `PeriodsViewModel.kt` and `WorkoutClustersViewModel.kt`.
* **Historical Origin & Commit Trace**: Introduced in `608c4e31` (`ATT-7758`, 2026-11-15) to reconcile stale `WorkoutData.clusterId`/`clusterName` in peek sheets upon cluster rename/merge/delete/split.
* **Root Reason for Existing Formulation**: `PeriodsViewModel._peekedWorkoutDataWithTrack` was added at the bottom near `selectWorkoutForPeek()` (`:405`), while the `allWorkouts` observer `init` was placed near constructor repositories (`:73`), unnoticed because `PeriodsViewModelTest` used `StandardTestDispatcher`.
* **Preservation of Core Invariants**: Preserve 100% of `REQ-PER-040.2` live peek reconciliation on `allWorkouts` emissions, `WorkoutData.workoutName` immutability, `StandardTestDispatcher` test behavior, and `collectIgnoringUnstubbedMock` MockK resilience.

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Net-new `REQ-PER-*` (refining `REQ-PER-040.2` initialization-order safety).
* **Affected Architecture Elements**: `PeriodsViewModel.kt`, `WorkoutClustersViewModel.kt`, `PeriodsViewModelTest.kt`.
* **Verification Impact & Test Surface**: `PeriodsViewModelTest` (Tier 1 targeted run), `WorkoutClustersViewModelTest`, and `emulator-5554` UI launch check (`N/A - no UI strings`).
* **Risk Assessment & Invariants**: **LOW** (`1 SP`) — strict declaration-order relocation with zero API, schema, localization, or `ANT-Android-SDKs` changes.

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Every `MutableStateFlow` / `StateFlow` / `Job` accessed inside `init` MUST be declared before `init`; `peekedWorkoutDataWithTrack` reconciliation on `allWorkouts` updates MUST remain intact.
* **Risk Rating**: **LOW** (`1 SP`) — isolated to two ViewModels' member ordering and unit/emulator verification.
