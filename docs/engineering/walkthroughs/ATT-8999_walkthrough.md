# Stage 5: Walkthrough & Verification - ATT-8999: Stop 1-Hz Heap/OOM Cascade on WorkoutRepository._allWorkouts During Live Tracking

**Ticket**: [ATT-8999](https://rainerblind.atlassian.net/browse/ATT-8999) (`3 SP`, `Bug`, `high-risk`, `severity-critical`) | **Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*No Crashes & App Stability*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `sprint/2026-11-18_03_Nacht` | **Branch**: `feature/ATT-8999` | **Status**: `Verified`  
**Requirement Mapping**: `REQ-STB-020` (`REQ-STB-020.1`–`.5`) | **Test Mapping**: `TST-STB-020` (`TST-STB-020.1`–`.4`)  
**Author**: AI Agent 1 (`agent1`, `Pro`) | **Date**: 2026-11-19

---

## 1. Executive Summary & Verification Overview
* **Discrete Lifecycle Broadcasts & `TrackerService` Isolation (`REQ-STB-020.1`, `.2`)**: Added [LiveWorkoutBroadcastTracker.kt](../../../app/src/main/java/com/atrainingtracker/trainingtracker/tracker/LiveWorkoutBroadcastTracker.kt) (`102` LoC) and updated [TrackerService.java](../../../app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java) (`3,556 <= 3,559` LoC) to remove 1-Hz `updateExtremaValueInMemory` calls and active/paused steady-state `WORKOUT_UPDATED_INTENT` broadcasts in `sampleAndWriteToDb`, firing single discrete edge broadcasts only on start/resume init, `TRACKING <-> PAUSED` edge ticks, live `saveLap` outside `endWorkout`, sport changes, existing altitude calibration, and terminal `TRACKING_FINISHED_INTENT` while retaining `recordLiveSample` and paused `updateWorkoutTotalTime`.
* **Finished-Session Guards & Bounce 1 Warning Cleanup (`REQ-STB-020.3`, `.4`)**: Filtered `list.filter { it.finished }` before `toPeriodProjectionKey()` and in `precalculateGroups()` inside [PeriodsRepository.kt](../../../app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsRepository.kt) (`1,211` LoC); guarded [AthleteProfileViewModel.kt](../../../app/src/main/java/com/atrainingtracker/trainingtracker/ui/athleteprofile/AthleteProfileViewModel.kt) (`607` LoC) with `it.finished` + `FinishedWorkoutSyncKey` (preserving lazy Cardio-Power & Polar offline backfills) and replaced deprecated `SensorType.{HR,POWER}.shortNameId` calls with `SensorType.{HR,POWER}.getShortName(getApplication<Application>())` (`0` warnings); guarded [KnownLocationsViewModel.kt](../../../app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsViewModel.kt) (`621` LoC) with `it.finished` + `(id, startLatLng, bSportType)` deduplication.
* **Parallel-Lane Isolation & Docs (`REQ-STB-020.5`)**: Rebased cleanly onto `sprint/2026-11-18_03_Nacht` (`941a1ea5` with `ATT-8916`), kept `0` edits in [WorkoutRepository.kt](../../../app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepository.kt) (`4,973 <= 5,117` LoC), `LegacyImportEngine.kt`, and `ANT-Android-SDKs`, and promoted `REQ-STB-020.1`–`.5` / `TST-STB-020.1`–`.4` to `[Verified]`.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Scope (`Rebased --rerun-tasks + Contract Suites`) | Result | Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-STB-020.1` | `TST-STB-020.1` | `LiveWorkoutBroadcastTrackerTest` (`3/3`), `TrackerService*Test` (`17` suites, `110/110`, incl. `CharacterisationTest` `8/8`) | **PASSED** | `Verified` |
| `REQ-STB-020.2` | `TST-STB-020.1`, `.4` | `LiveWorkoutBroadcastTrackerTest` (`3/3`), `ArchitectureConformanceContractTest` (`11/11`, `TrackerService.java = 3,556 <= 3,559` LoC) | **PASSED** | `Verified` |
| `REQ-STB-020.3` | `TST-STB-020.2` | `ui.aftermath.periodlist.*` (`21` suites, `132/132`, incl. `PeriodsRepositorySyncTest` `8/8`, `MemoryTest` `5/5`, `PeriodsViewModelTest` `14/14`) | **PASSED** | `Verified` |
| `REQ-STB-020.4` | `TST-STB-020.3` | `ui.athleteprofile.*` (`7` suites, `38/38`, incl. `CardioPowerTest` `6/6`), `ui.knownlocations.*` (`13` suites, `95/95`, incl. `KnownLocationsViewModelTest` `19/19`), `WorkoutSummaries*` (`13/13`), `WorkoutRepository{Projection,Stress,Telemetry}Test` (`25/25`) | **PASSED** | `Verified` |
| `REQ-STB-020.5` | `TST-STB-020.4` | `PeriodsRepositorySyncTest` 3,600-tick / 2,200-workout churn test, `9` contract suites (`44/44`), `TranslationParityTest` (`10/10`), `verify_requirement_governance.py` (`exit 0`) | **PASSED** | `Verified` |

---

## 3. Automated Test & Compiler Evidence
```text
python3 tools/parallel_sprint_lock.py --timeout 1800 ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest \
  --rerun-tasks --console=plain --tests ...
74 suites: 470 completed, 470 passed, 0 failed, 0 errors, 0 skipped (BUILD SUCCESSFUL in 11m 9s)
Compiler warnings across all ATT-8999 modified/created production & test files: 0
```
* **Breakdown (`74` XMLs)**: `LiveWorkoutBroadcastTrackerTest` (`1` suite, `3/3`), `TrackerService*Test` (`17` suites, `110/110`), `WorkoutRepository{Projection,Stress,Telemetry}Test` (`3` suites, `25/25`), `ui.aftermath.periodlist.*` (`21` suites, `132/132`), `WorkoutSummaries*` (`2` suites, `13/13`), `ui.athleteprofile.*` (`7` suites, `38/38`), `ui.knownlocations.*` (`13` suites, `95/95`), `TranslationParityTest` (`1` suite, `10/10`), and `9` contract suites (`44/44`: `ArchitectureConformance` `11/11`, `DetailedDesignParity` `3/3`, `InterfaceSignatureDrift` `3/3`, `SystemArchitectureDesign` `9/9`, `SourceTextReadingAudit` `4/4`, `JavaCoreMigrationStrategy` `5/5`, `AdrCompleteness` `2/2`, `NonFunctionalRequirements` `3/3`, `UiReflectionOnlyTestAudit` `4/4`).
* **UI Consistency & Localization Parity**: `UI Consistency: N/A` (background tracking/state refactor with no layout changes); `Localization Parity: N/A - no UI strings`.

---

## 4. Emulator (`emulator-5554`), Static Analysis & Governance
* **Live Emulator Workout Smoke (`emulator-5554`, verified APK SHA-256 `3f769131...`, `:app:installDebug` `19s`)**: Recorded live `workoutId=7` (`2026-11-19_030058`) from `03:00:58.371` (`2` discrete start/searching-finished reloads at `03:00:59`), `45s` active recording (`0` steady-state 1-Hz `WORKOUT_UPDATED_INTENT` reloads), pause edge `03:01:44.929` (`1` reload, `0` across `19s` pause), resume edge `03:02:04.929` (`1` reload, `0` across `11s` active), stop-pause `03:02:16.920` (`1` reload), stop finalization `03:02:18.847`/`03:02:19.112`, and `.TCX` export `03:02:22.026`; `0` `PeriodsRepository` churn, `adb -s emulator-5554 logcat -b crash -d` empty (`0` crashes), physical phone untouched.
* **LoC, Baselines & Governance**: `TrackerService.java = 3,556` (`<= 3,559`), `WorkoutRepository.kt = 4,973` (`<= 5,117`), `LiveWorkoutBroadcastTracker.kt = 102`; `check_static_analysis_baselines.py --base-ref sprint/2026-11-18_03_Nacht` PASS (`detekt=9509/9509` ceiling `9516`, `lint=238/238`); `lint_cursor_safety.py`, `lint_lifecycle_safety.py`, `lint_safe_enum.py`, and `check_secrets_hygiene.py` all PASS; `verify_requirement_governance.py --repo-root . --base-ref sprint/2026-11-18_03_Nacht` exits `0`; `feature/ATT-8999` preserved intact and unmerged for Stage 6 Cold Review.
