# Stage 3: Implementation Plan - ATT-8569: ViewModel Init Ordering & Eager Dispatcher Tests

**Ticket**: `ATT-8569` (`1 SP`, `severity-critical`, Epic `ATT-67`) | **Target Release**: `V5.0.0` | **Branch**: `feature/ATT-8569`  
**Requirement**: `REQ-PER-043` (`REQ-PER-043.1`, `REQ-PER-043.2`) | **Test Spec**: `TST-PER-051` (`TST-PER-051.1`..`TST-PER-051.4`)  
**Author**: AI Agent 1 (`--as agent1`) | **UI Consistency**: `N/A - no UI strings`

---

## 1. Architecture & Layering Design (SWE.2)
- **Presentation Layer (`ui.aftermath.periodlist.PeriodsViewModel`, `ui.clusters.WorkoutClustersViewModel`)**: Both ViewModels inherit `AndroidViewModel` and launch `viewModelScope` collectors inside `init`. Because `viewModelScope` executes eagerly on `Dispatchers.Main.immediate` on Android and `StateFlow.collect` emits synchronously upon subscription, Kotlin requires all properties referenced by `init` collectors (specifically `_peekedWorkoutDataWithTrack`) to be initialized prior to `init`.
- **Threading & Schema**: `viewModelScope` (`Dispatchers.Main.immediate`) and existing `ioDispatcher` / `defaultDispatcher` remain unchanged. Zero DB migrations, repository API changes, Compose UI edits, or string resource changes (`N/A - no UI strings`).

## 2. Atomic Stage 4 Implementation Sequence & Traceability
| Step | Target File(s) | Action & Scope | REQ / TST Mapping |
| :--- | :--- | :--- | :--- |
| **Step 1** | `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsViewModel.kt` | Move `init { ... }` (`:73-84`) below all property initializers (after `peekedWorkoutDataWithTrack` `:403`, e.g., at end of class after `clearPeekSelection()` or directly after all properties) so `_peekedWorkoutDataWithTrack` and every property are initialized before `workoutRepo.allWorkouts.collectIgnoringUnstubbedMock` executes; keep `REQ-PER-040.2` reconciliation & `workoutName` immutability untouched. | `REQ-PER-043.1`, `TST-PER-051.1` |
| **Step 2** | `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersViewModel.kt` | Move `init { refresh(); observeClusterMutations() }` (`:299-302`) below all property initializers (after `peekedWorkoutDataWithTrack` `:539` and `splitWorkouts` `:779`) for defensive initialization-order safety. | `REQ-PER-043.2`, `TST-PER-051.2` |
| **Step 3** | `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsViewModelTest.kt` | Add `UnconfinedTestDispatcher()` regression test(s) constructing `PeriodsViewModel` with both empty and populated `allWorkouts` `MutableStateFlow`, asserting zero `NullPointerException`, and verifying `selectWorkoutForPeek` + live `allWorkouts` emission updates `peekedWorkoutDataWithTrack.value.workoutData.clusterName` while preserving `workoutName`. | `REQ-PER-043.1`, `TST-PER-051.1` |
| **Step 4** | `app/src/test/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersViewModelTest.kt` | Add `UnconfinedTestDispatcher()` defensive eager-init test (with synchronous `MutableStateFlow` / replay-1 `clusterMutationEvents` emission on subscription) asserting construction completes without `NullPointerException`. | `REQ-PER-043.2`, `TST-PER-051.2` |
| **Step 5** | Stage 4 Gradle Execution | Run targeted Tier 1 unit tests under `parallel_sprint_lock.py`: `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodsViewModelTest" --tests "com.atrainingtracker.trainingtracker.ui.clusters.WorkoutClustersViewModelTest"`. | `TST-PER-051.4` |
| **Step 6** | Stage 5 Emulator Verification (`emulator-5554`) | Under `parallel_sprint_lock.py`, run `./gradlew installDebug` onto `emulator-5554`, launch `com.atrainingtracker.debug`, navigate to **Periods** via `uiautomator dump`, verify clean rendering, inspect screenshot (`read_file`), and verify `adb logcat` has zero `FATAL EXCEPTION` / `PeriodsViewModel` NPE. | `TST-PER-051.3` |

## 3. System Invariants & Constraints
1. **Functional Invariants (`REQ-PER-040.2`)**: `PeriodsViewModel._peekedWorkoutDataWithTrack` continues reconciling on `WorkoutRepository.allWorkouts` emissions; `WorkoutData.workoutName` stays immutable; `collectIgnoringUnstubbedMock` MockK handling remains unchanged.
2. **Isolation & Submodule Invariants**: Existing `StandardTestDispatcher()` tests remain deterministic; `Dispatchers.resetMain()` restored in `@After` / `finally`; `ANT-Android-SDKs` stays untouched at `0e8cea714a231fb9565170d4ed01eafa868f9798`; zero UI strings modified (`N/A - no UI strings`).

## 4. Verification & Rollback Plan
- **Verification**: `python3 /home/rainer/AndroidStudioProjects/aTrainingTracker/tools/parallel_sprint_lock.py --timeout 1800 -- ./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodsViewModelTest" --tests "com.atrainingtracker.trainingtracker.ui.clusters.WorkoutClustersViewModelTest"`, `emulator-5554` Periods navigation check, and `python3 tools/verify_requirement_governance.py`.
- **Rollback**: Pure declaration-order relocation in 2 ViewModels + additive tests; trivially revertible via `git checkout` with zero DB or preference impact.

## 5. Requirement Archaeology & Chesterton's Fence Audit (`REQ-PRO-022`)
- **Original Requirement ID & Target**: `REQ-PER-040` / `REQ-PER-040.2` (`TST-PER-048`, `ATT-7758`) targeting `PeriodsViewModel.kt` and `WorkoutClustersViewModel.kt`.
- **Historical Origin & Commit Trace**: `608c4e31` (`ATT-7758`, 2026-11-15) added `init` observers for peek-sheet cluster reconciliation.
- **Root Reason for Existing Formulation**: `_peekedWorkoutDataWithTrack` was appended at class bottom while `init` sat earlier; `StandardTestDispatcher()` hid eager `Dispatchers.Main.immediate` execution.
- **Preservation of Core Invariants**: `REQ-PER-040.2` peek reconciliation, `workoutName` immutability, and `collectIgnoringUnstubbedMock` behavior stay 100% intact.
