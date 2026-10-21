# Stage 2: Requirement & Test Specification - ATT-4018: [Bug] [Medium] Fix aftermath loader races, process-death restore and stale gesture data

**Ticket**: [ATT-4018](https://rainerblind.atlassian.net/browse/ATT-4018) | **Sub-task**: Pending Submission (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.5` | **Branch**: `feature/ATT-4018`  
**Requirement Mapping**: `REQ-UI-427` (reinforcing `REQ-MAP-032`, `REQ-DAT-011`, `REQ-UI-368`) | **Test Spec ID**: `TST-UI-392`  
**Author**: AI Agent 1 (Implementer - `subagent_flash`) | **Date**: 2026-10-21  

---

## 1. Requirement Traceability Matrix

| Requirement | Description | Test Case | Target Artifact | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-427.1` | In-flight load job cancellation (`loadJob?.cancel()`, `peekJob?.cancel()`) | `TST-UI-392.1` | `TrackOnMapAftermathViewModel`, `WorkoutClustersViewModel`, `PeriodsViewModel` | Planned |
| `REQ-UI-427.2` | Process-death restore via `LaunchedEffect(selectedWorkoutIdForDetails)` | `TST-UI-392.2` | `WorkoutSummariesTabbedScreen.kt` | Planned |
| `REQ-UI-427.3` | Live telemetry scrubbing integrity via `rememberUpdatedState(pathPoints)` | `TST-UI-392.3` | `ElevationProfile.kt` | Planned |
| `REQ-UI-427.4` | Synchronous crop double-tap guard, `CancellationException` rethrow & `NonCancellable` bulk delete resync | `TST-UI-392.4`, `TST-UI-392.5` | `CropWorkoutViewModel`, `WorkoutRepository`, `WorkoutSummariesViewModel` | Planned |
| `REQ-UI-427.5` | Scoped `EditWorkoutViewModel` lifecycle management without store leak | `TST-UI-392.6` | `WorkoutSummariesTabbedScreen.kt` | Planned |
| `REQ-UI-427.6` | System invariants (zero DB schema migrations, full regression suite pass) | `TST-UI-392.7`, `TST-UI-392.8` | `./gradlew testDebugUnitTest`, `TranslationParityTest` | Planned |

---

## 2. Formal Requirement Specifications

* **Loader Coroutine Cancellation (`REQ-UI-427.1`)**: In `TrackOnMapAftermathViewModel`, `loadAftermathData` SHALL track its job in `loadJob: Job?` and cancel previous job (`loadJob?.cancel()`). In `WorkoutClustersViewModel` and `PeriodsViewModel`, `selectWorkoutForPeek` SHALL cancel in-flight `peekJob` before querying track points/markers.
* **Process-Death Detail Restoration (`REQ-UI-427.2`)**: In `WorkoutSummariesTabbedScreen`, a `LaunchedEffect(selectedWorkoutIdForDetails)` SHALL trigger `loadAftermathData` on process recreation if `selectedWorkoutForDetails != null` and detail tracks are missing or mismatched.
* **Elevation Profile Live Gesture State Integrity (`REQ-UI-427.3`)**: In `ElevationProfile.kt`, `pathPoints` captured within `pointerInput` SHALL be accessed via `rememberUpdatedState(pathPoints)` to ensure ongoing scrub gestures read high-resolution telemetry immediately upon completion of background load.
* **Destructive Operation Lifecycle Protection & Double-Tap Guards (`REQ-UI-427.4`)**: In `CropWorkoutViewModel`, `_uiState.update { copy(isCropping = true) }` SHALL be invoked synchronously before launching coroutine. In `WorkoutRepository.cropWorkout`, `CancellationException` SHALL be rethrown rather than wrapped in `Result.failure`. In `WorkoutSummariesViewModel` / `deleteOldWorkouts`, cache resync (`resyncAllPeriods`, `refreshClusters`) SHALL run under `NonCancellable`.
* **ViewModel Scope Lifecycle Management (`REQ-UI-427.5`)**: `EditWorkoutViewModel` in `WorkoutSummariesTabbedScreen` SHALL NOT accumulate indefinitely in the host `ViewModelStore` upon dialog dismissal.
* **System Invariants (`REQ-UI-427.6`)**: Zero SQLite database migrations; zero visual regressions in aftermath charts; 100% test pass rate.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-UI-427` reinforcing `REQ-MAP-032` (Scrubber Synchronization), `REQ-DAT-011` (Period Cache Resync), and `REQ-UI-368` (Workout Cropping). Targets `TrackOnMapAftermathViewModel.kt`, `WorkoutClustersViewModel.kt`, `PeriodsViewModel.kt`, `WorkoutSummariesTabbedScreen.kt`, `ElevationProfile.kt`, `CropWorkoutViewModel.kt`, `WorkoutRepository.kt`, `WorkoutSummariesViewModel.kt`.
* **Historical Origin & Commit Trace**: `REQ-MAP-032` (ATT-2340, `1faa8656`), `REQ-DAT-011` (ATT-987), `REQ-UI-368` (ATT-3274). Identified during aftermath stress-testing and process death audit in ATT-4018.
* **Root Reason for Existing Formulation**: Scrubber synchronization assumed static track models; bulk operations assumed single-screen retention; aftermath loading was originally tied directly to click navigation events.
* **Preservation of Core Invariants**: Retains single-pass telemetry decoding, full-span elevation scrubbing, atomic SQLite transactions, and downstream period/cluster synchronization without altering UI contracts.

---

## 3. Formal Test Case Specifications (Given / When / Then)

* **`TST-UI-392.1` — Loader Job Cancellation Unit Tests**: *Given* active `loadAftermathData` or `selectWorkoutForPeek` execution, *When* a new selection is triggered, *Then* the preceding coroutine job is cancelled immediately and does not overwrite subsequent state.
* **`TST-UI-392.2` — Process-Death Detail Restoration Contract Test**: *Given* process recreation where `selectedWorkoutIdForDetails` is restored via `rememberSaveable`, *When* composition occurs, *Then* `LaunchedEffect` triggers `loadAftermathData` and details load.
* **`TST-UI-392.3` — Elevation Profile Gesture Updated State Test**: *Given* an active drag gesture on `ElevationProfile`, *When* high-resolution telemetry points load asynchronously, *Then* scrubbing reads the updated `pathPoints` via `rememberUpdatedState`.
* **`TST-UI-392.4` — Synchronous Crop Double-Tap Guard & Cancellation Rethrow**: *Given* `CropWorkoutViewModel`, *When* `executeCrop` is called twice in rapid succession, *Then* `isCropping` is set synchronously to `true` on the first call, dropping the duplicate; `CancellationException` in `WorkoutRepository.cropWorkout` is rethrown cleanly.
* **`TST-UI-392.5` — Bulk Deletion NonCancellable Resync**: *Given* `deleteOldWorkouts` running, *When* caller coroutine scope is cancelled mid-operation, *Then* downstream cache resync (`resyncAllPeriods`, `refreshClusters`) completes under `NonCancellable`.
* **`TST-UI-392.6` — EditWorkoutViewModel Scope Cleanup**: *Given* edit workout dialog dismissed, *When* checked, *Then* `EditWorkoutViewModel` does not leak unbounded instances in the parent store.

---

## 4. Edge Case & Regression Matrix

| Edge Case / Scenario | Risk | Mitigation & Assertion |
| :--- | :--- | :--- |
| **Rapid Selection Flapping** | Ghost data overwrite | Pre-launch `loadJob?.cancel()` ensures only latest selection completes. |
| **Process Death With Null Workout** | NPK / crash | `selectedWorkoutForDetails` safely unwraps ID against `workouts` list before loading. |
| **Navigation Away Mid-Deletion** | Out-of-sync cache | `NonCancellable` context guarantees downstream period and cluster cache reconciliation. |
| **Double-Tap Crop Button** | Duplicate export/trim | Synchronous `_uiState.update { copy(isCropping = true) }` blocks second dispatch before launch. |

---

## 5. Localization & Resource Parity Matrix

* **Resource Impact**: Zero new string keys required. Existing aftermath error and export strings are reused.
* **Parity Status**: 100% parity across all 9 canonical locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) verified via `TranslationParityTest`.
