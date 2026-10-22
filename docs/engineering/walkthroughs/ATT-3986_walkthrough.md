# Stage 5: Walkthrough & Verification - ATT-3986: [High] Workout finalization can leave FINISHED unset and skip map and streams

**Ticket**: [ATT-3986](https://rainerblind.atlassian.net/browse/ATT-3986)  
**Sub-task**: [ATT-4073](https://rainerblind.atlassian.net/browse/ATT-4073) (`[Test]`)  
**Parent Epic**: [ATT-3978](https://rainerblind.atlassian.net/browse/ATT-3978) (*Core Tracking Reliability & Data Integrity*)  
**Target Release**: `V4.9.40` (Assigned at Sprint Review per Rule 14)  
**Active Sprint**: `2026-42.9`  
**Requirement Mapping**: `REQ-TRK-030`  
**Test Mapping**: `TST-TRK-022`  
**Branch**: `feature/ATT-3986`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Executive Summary & Verification Overview

This ticket resolves four fragility points in the workout stop/finalization path of [`TrackerService.java`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java) and [`MySensorManager.java`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/sensor/MySensorManager.java):

1. **Dedicated `FINISHED = 1` Transaction First in `endWorkout()` (`REQ-TRK-030.1`)**:
   - [`TrackerService.endWorkout()`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java#L1664-L1766) now commits `WorkoutSummaries.FINISHED = 1` first in its own dedicated, minimal SQLite transaction on `WorkoutSummaries.TABLE` (`WHERE C_ID = mWorkoutID`) before running auxiliary summary updates.
   - `saveAccumulatedSensorTypes(summaryDb)` and the `GC_DATA` / `SPORT_ID` / `B_SPORT` update each execute in independent `try / catch (Throwable t)` blocks, ensuring a failure in any auxiliary step or a disconnected `BANALService` never leaves `FINISHED = 0`.
2. **Synchronized Defensive Copy in `MySensorManager.getAccumulatedSensorTypeSet()` (`REQ-TRK-030.2`)**:
   - [`MySensorManager.getAccumulatedSensorTypeSet()`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/sensor/MySensorManager.java#L221-L223) now returns `new HashSet<>(mAccumulatedSensorTypeSet)` while holding the `synchronized` monitor on `MySensorManager`, and [`notifyNewSensor(MySensor)`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/sensor/MySensorManager.java#L225-L234) is declared `synchronized`, eliminating any possibility of `ConcurrentModificationException` during `saveAccumulatedSensorTypes()`.
3. **Zero-Duration & Non-Finite Average Speed Guard (`REQ-TRK-030.3`)**:
   - [`TrackerService.averageSpeedCalculateable()`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java#L1401-L1403) now verifies `mTimeActive_s > 0 && Double.isFinite(mDistanceTotal_m) && mDistanceTotal_m >= 0.0`, and [`TrackerService.getAverageSpeed()`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java#L1405-L1412) returns `0.0` whenever `!averageSpeedCalculateable()` or when `mDistanceTotal_m / mTimeActive_s` is non-finite (`NaN` / `Infinity`).
   - Additionally, `finalizeLiveSession()` guards `LiveWorkoutSession.RunningStats.min` (`!= Double.MAX_VALUE`) and `RunningStats.max` (`!= -Double.MAX_VALUE`) so uninitialized sensor extrema are never persisted.
4. **Local `BANALServiceComm` Snapshot & Reordered, Independently Guarded Steps in `finalizeLiveSession()` (`REQ-TRK-030.4`)**:
   - [`TrackerService.finalizeLiveSession()`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java#L1768-L1953) snapshots `final BANALServiceComm banalService = mBanalService;` once at entry and executes five independently `try / catch (Throwable t)`-guarded steps with **Step 1 (Map Polyline & Elevation/Distance Streams)** executed first, followed by **Step 2 (Start/End Positions, Sensor Extrema & Max Line Distance)**, **Step 3 (Aerobic Efficiency / Decoupling & Power Training Metrics)**, **Step 4 (`guessCommuteAndTrainer()`)**, and **Step 5 (Identity Arbitration, Route Clustering & Auto-Naming)**.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-TRK-030.1`, `REQ-TRK-030.3` | `TST-TRK-022.1` | Automated Unit Test (`TrackerServiceFinalizationTest`) | **PASSED** | `Verified` |
| `REQ-TRK-030.1`, `REQ-TRK-030.4` | `TST-TRK-022.2` | Automated Unit Test (`TrackerServiceFinalizationTest`) | **PASSED** | `Verified` |
| `REQ-TRK-030.2` | `TST-TRK-022.3` | Automated Concurrency & Defensive Copy Unit Test (`TrackerServiceFinalizationTest`) | **PASSED** | `Verified` |
| `REQ-TRK-030` | `TST-TRK-022.4` | 9-Language Localization Audit (Zero string changes) | **PASSED** (N/A) | `Verified` |
| `REQ-PRO-001` | `TST-TRK-022.5` | Full Clean-Room `./gradlew testDebugUnitTest` | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Clean-Room Regression Suite (`./gradlew testDebugUnitTest`)
```text
> Task :app:testDebugUnitTest

BUILD SUCCESSFUL in 3m 33s
32 actionable tasks: 12 executed, 20 up-to-date
```

### Targeted Unit & Integration Tests (`TrackerServiceFinalizationTest`)
```text
> Task :app:testDebugUnitTest
TrackerServiceFinalizationTest > onDestroy_AlwaysFinalizesWorkoutSummary_AndFiresFinishedBroadcast_EvenWhenInterrupted PASSED
TrackerServiceFinalizationTest > endWorkout_CommitsFinishedInTransaction_AndExecutesBroadcastInFinally PASSED
TrackerServiceFinalizationTest > startForegroundSafely_RequestsHealthAndConnectedDevice_OnlyWhenPermissionsGranted PASSED
TrackerServiceFinalizationTest > startForegroundSafely_StripsRestrictedFgsTypes_WhenRuntimePermissionsMissing PASSED
TrackerServiceFinalizationTest > TST_TRK_022_1_endWorkout_WithZeroActiveTime_ComputesFiniteZeroAverageSpeed_AndMarksFinished PASSED
TrackerServiceFinalizationTest > TST_TRK_022_2_endWorkout_PersistsFinishedAndMapAndStreams_WhenAuxiliaryStepsThrowOrBanalServiceIsNull PASSED
TrackerServiceFinalizationTest > TST_TRK_022_3_getAccumulatedSensorTypeSet_ReturnsDefensiveCopy_AndSurvivesConcurrentRegistration PASSED

BUILD SUCCESSFUL in 23s
```

---

## 4. Hardware / Physical Verification (Pixel 10)

Pure backend service lifecycle and SQLite persistence fault-isolation fix in `TrackerService.java` and `MySensorManager.java` with zero UI layout, theme, or composable changes; verified via Robolectric SQLite integration tests (`TrackerServiceFinalizationTest`) and full clean-room regression suite (`./gradlew testDebugUnitTest`). On-device installation onto Google Pixel 10 occurs at the start of Ceremony 2 (Sprint Review) per Rule 10.

### Visual Consistency (Rule 23 — mandatory if UI is added or changed)
No UI changes.

---

## 5. Invariant & Governance Verification

1. **Zero Production Regressions**: Clean-room unit test suite (`./gradlew testDebugUnitTest`) executed with 100% pass rate in `3m 33s`.
2. **Living Documentation Synchronized**: Status of `REQ-TRK-030` in `docs/requirements.md` and `TST-TRK-022` in `docs/tests.md` updated to `Verified`.
3. **Subtask Completion**: Stage 5 subtask `ATT-4073` updated with walkthrough, audited via Gate 5 (`review_agent.py`), and transitioned to `Erledigt` via `freigabe`.
4. **Continuous Sprint Integration & Parent Ticket Final Review**: `feature/ATT-3986` merged into `sprint/2026-42.9` (`--no-ff`), feature branch deleted, and parent ticket `ATT-3986` transitioned to `Final Review (Human)` for final release sign-off.
