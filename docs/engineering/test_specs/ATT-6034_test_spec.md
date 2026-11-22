# Stage 2: Requirement & Test Specification - ATT-6034

**Ticket**: [ATT-6034](https://issue-tracker/ATT-6034) | **Parent Epic**: [ATT-6033](https://issue-tracker/ATT-6033)  
**Target Release**: `V5.0.0` (`Rule 8.3`) | **Active Sprint**: `2026-11-22_01_Früh` (Sprint 471) | **Risk Tier**: `tier-a` (`5 SP`)  
**Requirement ID**: `REQ-TRK-094` (`REQ-TRK-094.1`–`.4`, reserved atomically) | **Test Spec ID**: `TST-TRK-094` (`TST-TRK-094.1`–`.4`)  
**Living Docs Shards**: `docs/requirements/req_tracking_cockpit_part3.md` (`v1.0.3`), `docs/tests/tst_tracking_cockpit_part2.md` (`v1.0.6`)  
**Branch**: `feature/ATT-6034` | **Author**: Lane 1 Author (`--as agent1`) | **Date**: 2026-11-22

---

## 1. Formal Requirement Specification (`REQ-TRK-094`)

### 1.1 Attributes (`REQ-PRO-077`, `REQ-PRO-131.4`)
- **System Requirement(s)**: `SYSREQ-TRK-004`, `SYSREQ-TRK-005` (zero `SYS.2`/`SYS.3` changes required)
- **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit
- **Implementation**: `TrackerService.java`, `PostWorkoutFinalizer.kt`, `LapTelemetryExtractor.kt`, `BANALService.java`, `TrainingApplication.java`, `StravaSyncScheduler.kt`, `StravaEquipmentSyncWorker.kt`, `StravaRoutesSyncWorker.kt`, `StravaSegmentsSyncWorker.kt`

### 1.2 Normative Clauses (`REQ-TRK-094.1`–`.4`)
1. **Parameterized `TrackerService` Shutdown & Type-Safe Lap Extraction (`REQ-TRK-094.1`)**:
   - `TrackerService.handleActionStop()` and `TrackerService.onDestroy()` SHALL delegate shared controller teardown (`stopTrackingControllersAndTransientState(boolean fromOnDestroy)`) and async `mDbExecutor` finalization dispatch (`enqueueAsyncFinalization(boolean fromOnDestroy, @Nullable Integer stoppingGeneration)`) to parameterized helpers while preserving `onDestroy()`'s non-blocking `mIsStopping` early return (`ATT-6178`, `REQ-TRK-034.4`), `mTableInitializationFuture` cancellation, `sInstance` reset, `mRadarAlertManager` release, executor shutdown deferral (`REQ-TRK-076.1`), and `mSessionGeneration`-gated `performIdempotentMainThreadTeardown(true)` (`REQ-TRK-076.3`).
   - `LapTelemetryExtractor.extract(lapNrData, lapTimeData, lapDistanceData)` (`com.atrainingtracker.banalservice.sensor`) SHALL replace all 3 duplicated lap extraction blocks (`TrackerService.BanalFinalizationSnapshot.capture`, `TrackerService.createNewLap`, `BANALService.newLap`), extracting every numeric sensor value strictly via `instanceof Number` (`Number.toInt()` / `Number.toDouble()` with `isFinite()` guard), preserving fallback strings (`"??:??:??"`, `"0"`) and safe `lapSpeedMps` (`0.0` when `lapTimeSeconds <= 0`).
2. **Extracted `PostWorkoutFinalizer` Without Behavior Drift (`REQ-TRK-094.2`)**:
   - `PostWorkoutFinalizer` (`com.atrainingtracker.trainingtracker.tracker`, Kotlin `object`) SHALL encapsulate the 7 fault-isolated `finalizeLiveSession` stages (map/streams, start/end/extrema/apex, aerobic efficiency/decoupling/power-HR dynamics gated by `deferHrPhysiologyToPostProcessing` + immediate NP/IF/TSS/Work/Wkg, `guessCommuteAndTrainer`, identity/clustering/auto-naming/milestones, converged wheel circumferences, diagnostic error JSON) and `guessCommuteAndTrainer(repository, workoutId)` without holding references to `TrackerService` fields or raw `*DatabaseManager`/`Cursor` handles (`coding_standards.md` §2.5).
   - `TrackerService` SHALL preserve all existing protected `finalizeLiveSession` (0-/1-/4-arg) and `guessCommuteAndTrainer` (0-/1-arg) method signatures as thin delegations, propagating returned `MilestoneCelebrationData` to `mFirstJubileeCelebrationData`.
3. **Order-Preserving `TrainingApplication` Static Teardown Helpers (`REQ-TRK-094.3`)**:
   - `TrainingApplication` SHALL extract `private static void resetPauseLocks(@Nullable Context context)` and `private static void resetActiveTrackingState(@Nullable Context context, boolean broadcastResetAccumulators)` reused across `stopTracking()`, `resetTrackingStateOnAbort()`, and `onTrackingAborted()`.
   - `stopTracking()` SHALL invoke `resetPauseLocks(this)` strictly **before** `ACTION_STOP`/`stopService` dispatch and `resetActiveTrackingState(this, false)` strictly **after** dispatch; `trackingStopped()` SHALL remain semantically untouched (no pause-lock or `cTrackingMode` resets).
4. **Parameterized Strava Sync Preferences & Worker Scheduling (`REQ-TRK-094.4`)**:
   - `TrainingApplication` SHALL parameterize the 18 duplicated Strava `Equipment`/`Routes`/`Segments` preference getters/setters via a private `StravaSyncPref` enum (carrying each entity's exact `"never"` string resource ID and keys) while retaining 100% public static API compatibility and null `cSharedPreferences`/`cAppContext` guards (`coding_standards.md` §5.5).
   - `StravaSyncScheduler.schedulePeriodicSync(...)` (`com.atrainingtracker.trainingtracker.onlinecommunities.strava`) SHALL deduplicate `schedule(context)` across `StravaEquipmentSyncWorker`, `StravaRoutesSyncWorker`, and `StravaSegmentsSyncWorker`, preserving per-worker `WORK_NAME`, `TAG`, `entityLabel`, WorkManager availability guard, cancellation when disconnected/disabled, network/battery constraints, exponential backoff, and `ExistingPeriodicWorkPolicy.UPDATE`.
   - `ArchitectureConformanceContractTest` SHALL ratchet downward the monolith LoC baselines for `TrackerService.java` (`3,559 -> <= 3,220`) and `TrainingApplication.java` (`2,135 -> <= 2,050`).

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC 1 (`TST-TRK-094.1`)**: *Given* mixed `Number` subtypes (`Long`, `Float`, `Double`, `Integer`, `NaN`, non-numeric `String`, `null`) in lap `SensorData`, *When* `LapTelemetryExtractor.extract(...)` runs, *Then* zero `ClassCastException` occurs, valid `Number` values convert cleanly, and `handleActionStop()`/`onDestroy()` preserve all `ATT-6178`/`REQ-TRK-033`/`REQ-TRK-034`/`REQ-TRK-076` invariants.
* **AC 2 (`TST-TRK-094.2`)**: *Given* a `LiveWorkoutSession` with route points, power/speed/HR streams, and `deferHrPhysiologyToPostProcessing` toggled `true`/`false`, *When* `PostWorkoutFinalizer.finalizeLiveSession(...)` and `guessCommuteAndTrainer(...)` execute, *Then* all 7 stages persist identical outputs to pre-extraction behavior, isolate per-stage exceptions into `WorkoutRecordingErrorCollector`, and return `MilestoneCelebrationData`.
* **AC 3 (`TST-TRK-094.3`, `TST-TRK-094.4`)**: *Given* `TrainingApplication` teardown paths and Strava `Equipment`/`Routes`/`Segments` sync preferences & workers, *When* tracking stops/aborts or Strava sync is configured/scheduled, *Then* `stopTracking()` preserves pre/post-dispatch reset ordering, `trackingStopped()` stays untouched, all 18 Strava pref APIs honor their distinct keys/`"never"` defaults, and all 3 `Strava*SyncWorker.schedule(context)` calls preserve exact `WORK_NAME`/`TAG`/constraints.

---

## 2. Test Specification (`TST-TRK-094`) & Localization Parity
* **9-Language Localization Parity**: `N/A - no UI strings`.
* **Automated Test Cases**:
  1. `TST-TRK-094.1` (`LapTelemetryExtractorTest.kt`, `TrackerServiceFinalizationTest.kt`): verifies `Number` subtypes (`Integer`, `Long`, `Float`, `Double`), non-numeric/`NaN`/`Infinity`/`null` safety, zero-duration speed guard, display string defaults, and `handleActionStop()`/`onDestroy()` shared shutdown + `ATT-6178` non-blocking parity.
  2. `TST-TRK-094.2` (`PostWorkoutFinalizerTest.kt`, `TrackerServiceFinalizationTest.kt`, `TrackerServiceResumeTest.kt`): characterization & unit tests covering all 7 stages of `PostWorkoutFinalizer.finalizeLiveSession`, `deferHrPhysiologyToPostProcessing` toggle, per-stage fault isolation, `guessCommuteAndTrainer` XOR truth table, and `TrackerService` delegation parity.
  3. `TST-TRK-094.3` (`TrainingApplicationTrackingTest.kt`): verifies `stopTracking()` ordering (pause locks reset before `ACTION_STOP`/`stopService`, `READY`/`sActiveWorkoutId`/`clearBreadcrumbs` after), `resetTrackingStateOnAbort()` static null-context safety, `onTrackingAborted()`, and `trackingStopped()` untouched semantics.
  4. `TST-TRK-094.4` (`StravaSyncSchedulerTest.kt`, `StravaEquipmentSyncWorkerTest.kt`, `StravaRoutesSyncWorkerTest.kt`, `StravaSegmentsSyncWorkerTest.kt`, `TrainingApplicationTrackingTest.kt`, `ArchitectureConformanceContractTest.kt`): verifies all 18 Strava pref getters/setters + distinct `"never"` fallbacks + null-pref safety, shared WorkManager scheduling/cancellation/tags across all 3 workers, and downward LoC ratchets on `TrackerService.java` & `TrainingApplication.java`.

## 3. Traceability Matrix

| Requirement ID | Normative Contract | Test Case ID | Test File(s) |
| :--- | :--- | :--- | :--- |
| `REQ-TRK-094.1` | Parameterized `TrackerService` shutdown & `Number`-safe `LapTelemetryExtractor` | `TST-TRK-094.1` | `LapTelemetryExtractorTest.kt`, `TrackerServiceFinalizationTest.kt` |
| `REQ-TRK-094.2` | Extracted `PostWorkoutFinalizer` (7 stages + `guessCommuteAndTrainer`) | `TST-TRK-094.2` | `PostWorkoutFinalizerTest.kt`, `TrackerServiceFinalizationTest.kt`, `TrackerServiceResumeTest.kt` |
| `REQ-TRK-094.3` | Static `resetPauseLocks` & `resetActiveTrackingState` preserving `stopTracking` & `trackingStopped` | `TST-TRK-094.3` | `TrainingApplicationTrackingTest.kt` |
| `REQ-TRK-094.4` | Parameterized Strava prefs enum, `StravaSyncScheduler`, & monolith LoC ratchets | `TST-TRK-094.4` | `StravaSyncSchedulerTest.kt`, `Strava*SyncWorkerTest.kt`, `TrainingApplicationTrackingTest.kt`, `ArchitectureConformanceContractTest.kt` |
