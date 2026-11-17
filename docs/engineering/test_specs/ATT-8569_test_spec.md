# Stage 2: Requirement & Test Specification - ATT-8569: Eager Init-Order Safety for PeriodsViewModel & Sibling

**Ticket**: `ATT-8569` (`1 SP`, `severity-critical`, Epic `ATT-67`) | **Target Release**: `V5.0.0` | **Branch**: `feature/ATT-8569`  
**Requirement**: `REQ-PER-043` (`REQ-PER-043.1`, `REQ-PER-043.2`) | **Test Spec**: `TST-PER-051` (`TST-PER-051.1`..`TST-PER-051.4`)  
**Localization Parity**: `N/A - no UI strings`

---

## 1. Requirement Specification (`REQ-PER-043`)

### 1.1 Attributes
- **Priority**: Critical | **Type**: Functional | **Verification Method**: Unit, On-Device Manual | **System Requirement(s)**: Derived (SW-only)

### 1.2 Functional & Architectural Requirements
1. **`PeriodsViewModel` Init-Order Safety (`REQ-PER-043.1`)**:
   - `PeriodsViewModel` SHALL initialize `_peekedWorkoutDataWithTrack` and all other fields referenced inside `init` prior to executing `init`, so instantiation under `Dispatchers.Main.immediate` or `UnconfinedTestDispatcher` with either empty or populated `WorkoutRepository.allWorkouts` `StateFlow` succeeds without `NullPointerException`.
   - Subsequent `allWorkouts` emissions MUST continue reconciling `peekedWorkoutDataWithTrack` in-place while preserving `WorkoutData.workoutName` immutability (`REQ-PER-040.2`).
2. **`WorkoutClustersViewModel` Defensive Init-Order Safety (`REQ-PER-043.2`)**:
   - `WorkoutClustersViewModel` SHALL initialize `_peekedWorkoutDataWithTrack` and all other properties prior to executing `init` (`refresh()`, `observeClusterMutations()`), preventing `NullPointerException` even when `clusterMutationEvents` emits synchronously at subscription time.

### 1.3 Acceptance Criteria (Given-When-Then)
* *Given* `WorkoutRepository.allWorkouts` holds an empty or populated `StateFlow` and `Dispatchers.Main` executes eagerly (`Dispatchers.Main.immediate` / `UnconfinedTestDispatcher`), *When* `PeriodsViewModel` is instantiated, *Then* construction completes without `NullPointerException` and subsequent `allWorkouts` emissions still reconcile `peekedWorkoutDataWithTrack`.
* *Given* `WorkoutClustersViewModel` is constructed under eager dispatch with immediate `clusterMutationEvents` emission, *When* `init` runs `refresh()` and `observeClusterMutations()`, *Then* `_peekedWorkoutDataWithTrack` is already initialized without throwing `NullPointerException`.
* *Given* a debug build installed on `emulator-5554`, *When* navigating to the Periods screen, *Then* the screen renders cleanly without crashing.

### 1.4 Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PER-040` / `REQ-PER-040.2` (`TST-PER-048`, `ATT-7758`), targeting `PeriodsViewModel.kt` and `WorkoutClustersViewModel.kt`.
* **Historical Origin & Commit Trace**: Commit `608c4e31` (`ATT-7758`, 2026-11-15) added `PeriodsViewModel.init` (`:73-84`) and `WorkoutClustersViewModel.observeClusterMutations()` (`:299-323`) for live peek-sheet cluster reconciliation.
* **Root Reason for Existing Formulation**: `_peekedWorkoutDataWithTrack` was placed at the bottom near `selectWorkoutForPeek()` (`:402` / `:538`), while `init` was placed earlier; `StandardTestDispatcher()` in unit tests deferred coroutine execution until after construction.
* **Preservation of Core Invariants**: Preserves `REQ-PER-040.2` live peek reconciliation, `workoutName` immutability, `collectIgnoringUnstubbedMock` MockK resilience, and `StandardTestDispatcher` behavior (`Localization Parity: N/A - no UI strings`).

---

## 2. Test Specification (`TST-PER-051`)
1. **`TST-PER-051.1` (`PeriodsViewModelTest.kt` — Eager Init & Reconciliation)**: Construct `PeriodsViewModel` with `UnconfinedTestDispatcher()` for both empty and populated `allWorkouts` `StateFlow`; assert zero NPE, then peek a workout and emit an updated `WorkoutData` to verify `clusterName` reconciles and `workoutName` stays unchanged.
2. **`TST-PER-051.2` (`WorkoutClustersViewModelTest.kt` — Defensive Eager Init)**: Verify `WorkoutClustersViewModel` construction under `UnconfinedTestDispatcher()` (including synchronous `clusterMutationEvents` emission) succeeds without NPE.
3. **`TST-PER-051.3` (`emulator-5554` — UI Smoke Verification)**: Install debug build on `emulator-5554`, open Periods screen, and confirm clean rendering with zero crash / `FATAL EXCEPTION`.
4. **`TST-PER-051.4` (Targeted Unit Regression)**: Execute `./gradlew :app:testDebugUnitTest --tests "...PeriodsViewModelTest" --tests "...WorkoutClustersViewModelTest"` asserting 100% pass.

---

## 3. Traceability Matrix & Localization Audit

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PER-051.1` | Unit | `PeriodsViewModelTest`: `UnconfinedTestDispatcher` empty/populated init + peek sync | `REQ-PER-043.1` | Planned |
| `TST-PER-051.2` | Unit | `WorkoutClustersViewModelTest`: Defensive eager init under `UnconfinedTestDispatcher` | `REQ-PER-043.2` | Planned |
| `TST-PER-051.3` | On-Device Manual | `emulator-5554`: Periods screen launches cleanly without crash | `REQ-PER-043.1` | Planned |
| `TST-PER-051.4` | Regression | Targeted unit test run (`PeriodsViewModelTest`, `WorkoutClustersViewModelTest`) | `REQ-PER-043.1`, `REQ-PER-043.2` | Planned |

* **Localization Parity**: `N/A - no UI strings` (no string resources added or modified).
