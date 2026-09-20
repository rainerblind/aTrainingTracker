# Implementation Plan: Mandatory Daily Synchronization for Strava Segments and Routes (ATT-1178)

## 1. Traceability & Scope Reference
* **Ticket Key**: `ATT-1178`
* **Sub-Task Key**: `ATT-1187` (`[Impl-Plan]`)
* **Target Version**: `V4.9.37`
* **Target Branch**: `feature/ATT-1178`
* **Requirements**: `REQ-EXP-011`, `REQ-EXP-012`
* **Test Cases**: `TST-EXP-008`, `TST-EXP-009`

---

## 2. Architecture & Design Decisions

### 2.1 UI Streamlining (`StravaSettingsDialog.kt`)
1. **Remove Automated Sync Configuration Sections**:
   - Delete the segment automated sync section (title `automated_strava_segments_sync`, summary `automated_strava_segments_sync_summary`, toggle `Switch`, and interval `DropdownSelector`).
   - Delete the route automated sync section (title `automated_strava_routes_sync`, summary `automated_strava_routes_sync_summary`, toggle `Switch`, and interval `DropdownSelector`).
2. **Remove Unused State**:
   - Remove state variables: `automatedSegmentsSync`, `segmentsSyncIntervalDays`, `automatedRoutesSync`, `routesSyncIntervalDays`.
3. **Keep Manual Synchronization Outlined Cards**:
   - `updateStravaEquipment` (Equipment synchronization with last update timestamp)
   - `updateStravaRoutes` (Routes synchronization with last update timestamp)
   - `updateStravaSegments` (Segments synchronization with last update timestamp)
4. **Update Save Action**:
   - In `AppDialogActions.SaveCancel`, remove writes to `SP_AUTOMATED_STRAVA_SEGMENTS_SYNC`, `SP_STRAVA_SEGMENTS_SYNC_INTERVAL_DAYS`, `SP_AUTOMATED_STRAVA_ROUTES_SYNC`, and `SP_STRAVA_ROUTES_SYNC_INTERVAL_DAYS`.
   - Maintain `StravaSegmentsSyncWorker.schedule(context)` and `StravaRoutesSyncWorker.schedule(context)` calls.

### 2.2 Worker Scheduling & Execution Pinned to 1 Day

#### `StravaSegmentsSyncWorker.kt`
1. **`schedule(context)`**:
   - Verify WorkManager availability via `TrainingApplication.isWorkManagerAvailable()`.
   - Check authentication: `val stravaConnected = TrainingApplication.getStravaAccessToken() != null`.
   - If `!stravaConnected`:
     - Cancel unique work: `WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)`.
     - Return early.
   - If `stravaConnected`:
     - Build work request with fixed 1-day interval:
       ```kotlin
       val workRequest = PeriodicWorkRequestBuilder<StravaSegmentsSyncWorker>(1, TimeUnit.DAYS)
           .setConstraints(constraints)
           .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
           .build()
       ```
     - Enqueue with `ExistingPeriodicWorkPolicy.UPDATE`.
2. **`doWork()`**:
   - Unconditionally invoke `SegmentsRepository.getInstance(applicationContext).pruneExpiredSegments()`.
   - Check `val stravaConnected = TrainingApplication.getStravaAccessToken() != null`. If `false`, return `Result.success()`.
   - Invoke `SegmentsRepository.getInstance(applicationContext).syncStarredSegments(BSportType.UNKNOWN)`.
   - Update timestamp in SharedPreferences.

#### `StravaRoutesSyncWorker.kt`
1. **`schedule(context)`**:
   - Verify WorkManager availability via `TrainingApplication.isWorkManagerAvailable()`.
   - Check authentication: `val stravaConnected = TrainingApplication.getStravaAccessToken() != null`.
   - If `!stravaConnected`:
     - Cancel unique work: `WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)`.
     - Return early.
   - If `stravaConnected`:
     - Build work request with fixed 1-day interval:
       ```kotlin
       val workRequest = PeriodicWorkRequestBuilder<StravaRoutesSyncWorker>(1, TimeUnit.DAYS)
           .setConstraints(constraints)
           .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
           .build()
       ```
     - Enqueue with `ExistingPeriodicWorkPolicy.UPDATE`.
2. **`doWork()`**:
   - Unconditionally invoke `RoutesRepository.getInstance(applicationContext).pruneExpiredRoutes()`.
   - Check `val stravaConnected = TrainingApplication.getStravaAccessToken() != null`. If `false`, return `Result.success()`.
   - Invoke `RoutesRepository.getInstance(applicationContext).syncRoutesFromStrava()`.
   - Return `Result.success()` or `Result.retry()`.

### 2.3 Application & Preference Accessors (`TrainingApplication.java`)
- Update `isAutomatedStravaRoutesSyncEnabled()` to return `true`.
- Update `getStravaRoutesSyncIntervalDays()` to return `"1"`.
- Update `isAutomatedStravaSegmentsSyncEnabled()` to return `true`.
- Update `getStravaSegmentsSyncIntervalDays()` to return `"1"`.
- Setters can either be no-ops or maintain binary compatibility without affecting the fixed 1-day worker schedule.

---

## 3. Invariant Safety Checklist
* [x] Native recorded workouts in `WorkoutSummaries.db`, `WorkoutSamples.db`, and `Laps.db` are completely untouched.
* [x] Athlete-owned local GPX and workout routes are never deleted or affected.
* [x] Selective telemetry upload toggles (GPS, Altitude, HR, Power, Cadence) in `StravaSettingsDialog` remain fully operational.
* [x] Manual on-demand sync from dialog cards and pull-to-refresh remains fully functional.
* [x] Battery and network constraints (`requiresBatteryNotLow(true)` and `NetworkType.CONNECTED`) remain enforced.

---

## 4. Test Implementation Plan
1. **`StravaSegmentsSyncWorkerTest.kt`**:
   - Update `testScheduleWhenConnectedEnqueuesPeriodicWork`: assert interval is 1 day.
   - Update `testScheduleWhenDisconnectedCancelsWork`: assert `cancelUniqueWork` is called.
   - Remove/update obsolete tests referencing custom intervals and toggle disable.
   - Verify `doWork` behavior when connected vs disconnected.
2. **`StravaRoutesSyncWorkerTest.kt`**:
   - Update `testScheduleWhenConnectedEnqueuesPeriodicWork`: assert interval is 1 day.
   - Update `testScheduleWhenDisconnectedCancelsWork`: assert `cancelUniqueWork` is called.
   - Remove/update obsolete tests referencing custom intervals and toggle disable.
   - Verify `doWork` behavior when connected vs disconnected.
3. **Regression Suite**:
   - Run `./gradlew testDebugUnitTest` across the entire project (100% pass).
