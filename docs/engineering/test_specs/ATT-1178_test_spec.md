# Test Specification: Mandatory Daily Synchronization for Strava Segments & Routes (ATT-1178)

## 1. Traceability & Requirements Mapping

* **Ticket Key**: `ATT-1178`
* **Sub-Task Key**: `ATT-1186` (`[Test-Spec]`)
* **Target Version**: `V4.9.37`
* **Target Branch**: `feature/ATT-1178`
* **Mapped Requirements**: `REQ-EXP-011` (Strava Segments Daily Sync), `REQ-EXP-012` (Strava Routes Daily Sync) (documented in `docs/requirements.md`)
* **Verification Test IDs**: `TST-EXP-008`, `TST-EXP-009` (documented in `docs/tests.md`)

---

## 2. Requirement Specification (REQ-EXP-011 & REQ-EXP-012 Revisions)

### Functional Specification
Under the Strava API Agreement Section 6.2 and cache freshness requirements, the system SHALL synchronize starred segments and routes with Strava servers every day automatically without requiring or permitting user configuration:

1. **Mandatory Daily Periodic Background Scheduling**:
   - Both `StravaSegmentsSyncWorker` and `StravaRoutesSyncWorker` SHALL be scheduled with a fixed periodic interval of **1 day** (`1, TimeUnit.DAYS`) via Android `WorkManager` whenever the athlete is authenticated with Strava (`TrainingApplication.getStravaAccessToken() != null`).
   - The system SHALL NOT allow the synchronization interval to be configured to values greater than 1 day (e.g., 3 days, 7 days, 30 days).
   - Work requests SHALL enforce constraints: `NetworkType.CONNECTED` and `requiresBatteryNotLow(true)`.

2. **Automated Cancellation upon Disconnect**:
   - If Strava is disconnected or the access token is cleared, both `StravaSegmentsSyncWorker.schedule(context)` and `StravaRoutesSyncWorker.schedule(context)` SHALL immediately cancel the respective periodic work (`cancelUniqueWork`).
   - If a periodic work request triggers while disconnected, `doWork()` SHALL return `Result.success()` without making network calls.

3. **Streamlined UI in `StravaSettingsDialog` ("No Need to Ask the User")**:
   - The user-facing configuration toggles (`automated_strava_segments_sync`, `automated_strava_routes_sync`) and the interval selection dropdowns (`strava_segments_sync_interval`, `strava_routes_sync_interval`) SHALL be removed from `StravaSettingsDialog.kt`.
   - The dialog SHALL retain the `OutlinedCard` for manual synchronization:
     - `updateStravaEquipment` (Equipment sync)
     - `updateStravaRoutes` (Routes sync with last update timestamp)
     - `updateStravaSegments` (Segments sync with last update timestamp)
   - Athletes retain the ability to perform manual, on-demand synchronization at any time by tapping these cards or through pull-to-refresh in list views.

4. **Background Execution Flow & TTL Pruning**:
   - When periodic work triggers in `StravaSegmentsSyncWorker.doWork()` and `StravaRoutesSyncWorker.doWork()`:
     - The worker SHALL first execute 7-day TTL cache eviction (`pruneExpiredSegments()` / `pruneExpiredRoutes()`).
     - If authenticated, the worker SHALL execute remote synchronization (`syncStarredSegments(BSportType.UNKNOWN)` / `syncRoutesFromStrava()`).
     - Upon completion, the worker SHALL record the formatted last update timestamp in SharedPreferences.

5. **System Invariants**:
   - Native recorded workouts (`WorkoutSummaries.db`, `WorkoutSamples.db`, `Laps.db`), local GPX/workout routes, sensor equipment links, and selective telemetry upload toggles (GPS, Altitude, HR, Power, Cadence) MUST NOT be altered or regressed.

---

## 3. Acceptance Criteria (Given-When-Then)

* **AC-1 (Daily Interval Scheduling for Segments Worker)**:
  * *Given* an authenticated Strava connection (`getStravaAccessToken() != null`),
  * *When* `StravaSegmentsSyncWorker.schedule(context)` is called,
  * *Then* `WorkManager` SHALL have unique periodic work `automated_strava_segments_sync_work` enqueued with a fixed interval of 1 day (`1, TimeUnit.DAYS`), `NetworkType.CONNECTED`, and `requiresBatteryNotLow(true)`.
* **AC-2 (Daily Interval Scheduling for Routes Worker)**:
  * *Given* an authenticated Strava connection (`getStravaAccessToken() != null`),
  * *When* `StravaRoutesSyncWorker.schedule(context)` is called,
  * *Then* `WorkManager` SHALL have unique periodic work `automated_strava_routes_sync_work` enqueued with a fixed interval of 1 day (`1, TimeUnit.DAYS`), `NetworkType.CONNECTED`, and `requiresBatteryNotLow(true)`.
* **AC-3 (Cancellation on Disconnect)**:
  * *Given* Strava is disconnected (`getStravaAccessToken() == null`),
  * *When* `StravaSegmentsSyncWorker.schedule(context)` or `StravaRoutesSyncWorker.schedule(context)` is invoked,
  * *Then* `WorkManager.cancelUniqueWork` SHALL be called for both workers, and no periodic work requests SHALL be enqueued.
* **AC-4 (UI Simplification in `StravaSettingsDialog`)**:
  * *Given* an athlete viewing `StravaSettingsDialog`,
  * *When* the dialog is rendered,
  * *Then* the automated sync switches and interval dropdown selectors for segments and routes SHALL NOT be present; the manual update cards for equipment, routes, and segments SHALL be present and show the last updated timestamp.
* **AC-5 (Execution Flow & TTL Pruning)**:
  * *Given* periodic work triggers in `StravaSegmentsSyncWorker` or `StravaRoutesSyncWorker`,
  * *When* `doWork()` executes,
  * *Then* `pruneExpired*()` SHALL be invoked prior to remote synchronization, ensuring Section 6.2 compliance even if network is degraded.
* **AC-6 (Preservation of System Invariants)**:
  * *Given* background synchronization execution,
  * *When* workers run daily,
  * *Then* recorded workout summaries, local GPX tracks, and sensor pairings MUST remain completely untouched.

---

## 4. Test Cases & Verification Procedures

### 4.1 Automated Unit Tests

#### Test Suite: `StravaSegmentsSyncWorkerTest`
1. `testScheduleWhenConnectedEnqueuesDailyPeriodicWork`:
   - Verify `PeriodicWorkRequestBuilder` is constructed with `1, TimeUnit.DAYS`.
   - Verify `ExistingPeriodicWorkPolicy.UPDATE`.
   - Verify constraints: `NetworkType.CONNECTED`, `requiresBatteryNotLow(true)`.
2. `testScheduleWhenDisconnectedCancelsWork`:
   - Mock `getStravaAccessToken()` returning `null`.
   - Invoke `StravaSegmentsSyncWorker.schedule(context)`.
   - Verify `mockWorkManager.cancelUniqueWork(WORK_NAME)`.
   - Verify no enqueue call.
3. `testDoWorkWhenDisconnectedReturnsSuccessWithoutSync`:
   - Mock disconnected state.
   - Verify `doWork()` invokes `pruneExpiredSegments()` and returns `Result.success()`.
   - Verify `syncStarredSegments` is not invoked.
4. `testDoWorkWhenConnectedInvokesSyncAndUpdateTimestamp`:
   - Mock connected state.
   - Verify `doWork()` invokes `syncStarredSegments(BSportType.UNKNOWN)`.
   - Verify `setLastUpdateTimeOfStravaSegments` is invoked with formatted timestamp.
   - Verify `Result.success()` is returned.

#### Test Suite: `StravaRoutesSyncWorkerTest`
1. `testScheduleWhenConnectedEnqueuesDailyPeriodicWork`:
   - Verify `PeriodicWorkRequestBuilder` is constructed with `1, TimeUnit.DAYS`.
   - Verify `ExistingPeriodicWorkPolicy.UPDATE`.
   - Verify constraints: `NetworkType.CONNECTED`, `requiresBatteryNotLow(true)`.
2. `testScheduleWhenDisconnectedCancelsWork`:
   - Mock `getStravaAccessToken()` returning `null`.
   - Invoke `StravaRoutesSyncWorker.schedule(context)`.
   - Verify `mockWorkManager.cancelUniqueWork(WORK_NAME)`.
   - Verify no enqueue call.
3. `testDoWorkWhenDisconnectedReturnsSuccessWithoutSync`:
   - Mock disconnected state.
   - Verify `doWork()` invokes `pruneExpiredRoutes()` and returns `Result.success()`.
   - Verify `syncRoutesFromStrava` is not invoked.
4. `testDoWorkWhenConnectedInvokesSyncAndUpdateTimestamp`:
   - Mock connected state.
   - Verify `doWork()` invokes `syncRoutesFromStrava()`.
   - Verify `Result.success()` returned on true, `Result.retry()` on false/exception.

### 4.2 Full Regression Test
- Run `./gradlew testDebugUnitTest` to verify clean-room test execution across all modules (100% pass rate).
