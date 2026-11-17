# Stage 5: Walkthrough & Verification - ATT-8569: PeriodsViewModel Init-Order NPE Fix

**Ticket**: `ATT-8569` (`1 SP`, `severity-critical`, Epic `ATT-67`) | **Target Release**: `V5.0.0`  
**Branch**: `feature/ATT-8569` (rebased onto `sprint/2026-11-17_01_Früh` @ `6768fb87cac71c58853102556f9d162f2542caea`, pre-walkthrough SHA `fe165f33383d4dbacfcbe793fc9fd45b8b621be3`)  
**Requirement Mapping**: `REQ-PER-043` (`REQ-PER-043.1`, `REQ-PER-043.2`) | **Test Mapping**: `TST-PER-051` (`TST-PER-051.1`..`TST-PER-051.4`)  
**UI Consistency**: `N/A - no UI strings` (`strings*.xml` and `androidTest/` untouched)

---

## 1. Executive Summary & Implemented Changes
1. **`PeriodsViewModel.kt`** (`app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsViewModel.kt`):
   - Moved `init` (which collects `workoutRepo.allWorkouts` `StateFlow` synchronously on `Dispatchers.Main.immediate`) below all property initializers (including `_peekedWorkoutDataWithTrack` and `peekedWorkoutDataWithTrack`), eliminating the `NullPointerException` on opening Periods while preserving `REQ-PER-040.2` live peek reconciliation and `WorkoutData.workoutName` immutability.
2. **`WorkoutClustersViewModel.kt`** (`app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersViewModel.kt`):
   - Defensively moved `init { refresh(); observeClusterMutations() }` below all property initializers (including `_peekedWorkoutDataWithTrack`) so synchronous subscription emissions can never dereference uninitialized `MutableStateFlow` properties.
3. **Regression Unit Tests (`PeriodsViewModelTest.kt`, `WorkoutClustersViewModelTest.kt`)**:
   - Added `testEagerMainDispatcherInit_withEmptyAndPopulatedAllWorkouts_doesNotThrowNpe_TST_PER_051_1` using `UnconfinedTestDispatcher()` for empty and populated `allWorkouts` `StateFlow` plus live peek cluster-rename reconciliation.
   - Added `testEagerMainDispatcherInit_withSynchronousMutationEmission_doesNotThrowNpe_TST_PER_051_2` using `UnconfinedTestDispatcher()` and synchronous `clusterMutationEvents` emission.

---

## 2. Requirement & Test Verification Matrix
* **Tier 1 Clean-Room Post-Rebase Unit Tests** (`python3 /home/rainer/AndroidStudioProjects/aTrainingTracker/tools/parallel_sprint_lock.py --timeout 1800 -- ./gradlew :app:testDebugUnitTest --tests "*.PeriodsViewModelTest" --tests "*.WorkoutClustersViewModelTest"`): **30/30 PASSED** (`0` failures, `0` errors, `0` skipped; `BUILD SUCCESSFUL`). `TranslationParityTest` & `:app:compileDebugAndroidTestKotlin` skipped (`strings*.xml` & `androidTest/` untouched); Tier 2 full-repo suite deferred to Sprint Integration Gate.

| Requirement | Test Spec | Verification Scope & Evidence | Result | Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-PER-043.1` | `TST-PER-051.1` | `PeriodsViewModelTest` (`14/14` XML: eager empty/populated init + peek sync + existing 13 tests) | **PASSED** (`14/14`) | `Verified` |
| `REQ-PER-043.2` | `TST-PER-051.2` | `WorkoutClustersViewModelTest` (`16/16` XML: eager synchronous `clusterMutationEvents` init + existing 15 tests) | **PASSED** (`16/16`) | `Verified` |
| `REQ-PER-043.1` | `TST-PER-051.3` | `emulator-5554` (`installDebug`, launch `MainActivityWithNavigation`, navigate to `Zeiträume`, `/tmp/att8569_periods.png` inspected via `read_file`; `logcat -b crash -d` empty, zero `FATAL EXCEPTION` / `PeriodsViewModel` errors) | **PASSED** | `Verified` |
| `REQ-PER-043.1`, `.2` | `TST-PER-051.4` | Combined Tier 1 clean-room post-rebase run (`30/30`) | **PASSED** (`30/30`) | `Verified` |

---

## 3. Invariant & Governance Verification
* **Living Docs**: `REQ-PER-043` (`docs/requirements/req_aftermath_periods.md`) and `TST-PER-051` (`docs/tests/tst_aftermath_periods.md`) promoted to `[Verified]`; master indexes synchronized (`docs/requirements.md`: `1216` total, `972` Verified; `docs/tests.md`: `1223` total, `1001` Verified).
* **Governance & Submodule**: `python3 tools/verify_requirement_governance.py` and `--text-file docs/engineering/walkthroughs/ATT-8569_walkthrough.md` exit `0`; `ANT-Android-SDKs` untouched at `0e8cea714a231fb9565170d4ed01eafa868f9798`.
