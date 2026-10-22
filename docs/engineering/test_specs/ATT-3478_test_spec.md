# Stage 2: Test Specification - ATT-3478: Fix automated background synchronization for Strava segments, routes and equipment

**Ticket**: [ATT-3478](https://atrainingtracker.atlassian.net/browse/ATT-3478)  
**Sub-task**: [ATT-3514](https://atrainingtracker.atlassian.net/browse/ATT-3514) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-597](https://atrainingtracker.atlassian.net/browse/ATT-597) (*Strava Support*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.3`  
**Requirement Mapping**: `REQ-EXP-019` (*Automated Background Synchronization for Strava Segments, Routes, and Equipment*), amending `REQ-EXP-011` and `REQ-EXP-012`  
**Test Spec ID**: `TST-EXP-016`  
**Branch**: `feature/ATT-3478`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-14  

---

## 1. Requirement Specification (REQ-EXP-019)

### 1.1 Problem Statement & Background
During athlete testing, automated background updates of Strava data (starred segments, routes, and equipment) failed to execute reliably in production. Forensic root cause analysis revealed coupled defects across the application lifecycle and worker scheduling infrastructure:
1. **Startup Cancellation Race Condition (`TrainingApplication.java`)**: In `TrainingApplication.onCreate()`, `initWorkManager()` and worker scheduling were invoked prior to `cSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)`. When `StravaSegmentsSyncWorker.schedule()` and `StravaRoutesSyncWorker.schedule()` evaluated `TrainingApplication.getStravaAccessToken()`, the null check guard on `cSharedPreferences` failed and returned null. Consequently, both workers erroneously concluded that Strava was disconnected and cancelled their scheduled periodic WorkManager tasks on every cold launch.
2. **Missing Periodic Worker for Strava Equipment**: Equipment synchronization (`StravaEquipmentSynchronizeThread`) was only triggered manually in settings or on screen entry. No automated periodic worker existed to fetch updated athlete bikes and running shoes in the background.
3. **Ignored Synchronization Preferences**: While `prefs_strava.xml` declared preferences for automated synchronization toggles (`automated_strava_segments_sync`, `automated_strava_routes_sync`) and interval settings (`strava_segments_sync_interval_days`, `strava_routes_sync_interval_days`), `TrainingApplication.java` getters hardcoded `return true;` and `return "1";`, and worker schedulers ignored athlete configurations.
4. **Token Expiry Vulnerability**: Workers checked raw access tokens without proactively invoking token refresh via `StravaHelper.getRefreshedAccessToken()`, exposing background sync to 401 Unauthorized errors after token expiration.

### 1.2 Functional & Architectural Requirements
1. **Initialization Ordering & Lifecycle Invariants (`TrainingApplication.java`)**:
   - The application SHALL guarantee that `PreferenceManager.setDefaultValues()` and `cSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)` execute before `initWorkManager()` and any worker scheduling in `TrainingApplication.onCreate()`.
   - `TrainingApplication.getStravaAccessToken()` SHALL return the valid stored token immediately upon startup without returning null due to uninitialized preferences.
2. **Preference Reading Parity (`TrainingApplication.java`)**:
   - `isAutomatedStravaSegmentsSyncEnabled()` SHALL read `cSharedPreferences.getBoolean(SP_AUTOMATED_STRAVA_SEGMENTS_SYNC, true)`.
   - `getStravaSegmentsSyncIntervalDays()` SHALL read `cSharedPreferences.getString(SP_STRAVA_SEGMENTS_SYNC_INTERVAL_DAYS, "1")`.
   - `isAutomatedStravaRoutesSyncEnabled()` SHALL read `cSharedPreferences.getBoolean(SP_AUTOMATED_STRAVA_ROUTES_SYNC, true)`.
   - `getStravaRoutesSyncIntervalDays()` SHALL read `cSharedPreferences.getString(SP_STRAVA_ROUTES_SYNC_INTERVAL_DAYS, "1")`.
   - The system SHALL define `SP_AUTOMATED_STRAVA_EQUIPMENT_SYNC` and `SP_STRAVA_EQUIPMENT_SYNC_INTERVAL_DAYS`, with corresponding getters and setters adhering to default values (`true` and `"1"`).
3. **Automated Equipment Synchronization Worker (`StravaEquipmentSyncWorker.kt`)**:
   - The system SHALL provide `StravaEquipmentSyncWorker` extending `CoroutineWorker` registered under unique work name `automated_strava_equipment_sync_work`.
   - `StravaEquipmentSyncWorker.Companion.schedule(context)` SHALL enqueue unique periodic work with `ExistingPeriodicWorkPolicy.UPDATE`, connected network constraint, `requiresBatteryNotLow(true)`, and exponential backoff retry criteria.
   - If Strava is disconnected or `isAutomatedStravaEquipmentSyncEnabled()` is false, `schedule(context)` SHALL cancel `automated_strava_equipment_sync_work`.
   - In `doWork()`, the worker SHALL check that Strava is authenticated and sync is enabled, refresh credentials via `StravaHelper.getRefreshedAccessToken()`, execute equipment synchronization, and return `Result.success()`. If sync throws an unhandled exception or returns a failure status, it SHALL return `Result.retry()`.
   - `StravaEquipmentSyncWorker.schedule(this)` SHALL be invoked during `TrainingApplication.onCreate()` alongside segments and routes workers.
4. **Segments and Routes Worker Preference & Refresh Enforcement**:
   - `StravaSegmentsSyncWorker.Companion.schedule(context)` and `StravaRoutesSyncWorker.Companion.schedule(context)` SHALL respect the configured interval days (converted to `Long` days, defaulting to `1L`).
   - If the respective sync toggle is disabled in preferences, `schedule(context)` SHALL cancel the unique work request.
   - In `doWork()`, workers SHALL check the toggle preference and obtain a refreshed access token via `StravaHelper.getRefreshedAccessToken()`. If disconnected or disabled, workers SHALL return `Result.success()`.
5. **Data Purge and Disconnect Integration (`StravaDataPurgeManager.kt` & `StravaSettingsDialog.kt`)**:
   - `StravaDataPurgeManager.executeLocalDataPurge(context)` SHALL cancel `automated_strava_equipment_sync_work` in addition to segments and routes tasks.
   - `StravaSettingsDialog.kt` SHALL schedule `StravaEquipmentSyncWorker.schedule(context)` upon successful OAuth authentication.
6. **100% 9-Language Localization Parity**:
   - New string resources for automated equipment sync (`automated_strava_equipment_sync`, `automated_strava_equipment_sync_summary`, `strava_equipment_sync_interval`) SHALL be externalized and translated across all 9 supported locales: English (default), German (`values-de`), French (`values-fr`), Spanish (`values-es`), Italian (`values-it`), Japanese (`values-ja`), Dutch (`values-nl`), Polish (`values-pl`), Portuguese (`values-pt`).

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: `REQ-EXP-011` (*Strava Segments Mandatory Daily Automated Synchronization*) and `REQ-EXP-012` (*Strava Routes Mandatory Daily Automated Synchronization*).
2. *Historical Origin & Commit Trace*: Ticket `ATT-913`, `ATT-914`, `ATT-1178`, Sprint `2026-41.2`, target release `V4.9.38`.
3. *Root Reason for Existing Formulation*: Originally, automated background synchronization was hardcoded to a mandatory 1-day interval without respecting user preference toggles under the design assumption that background sync should simply run daily whenever Strava is connected. Furthermore, Strava equipment synchronization was implemented only as an ad-hoc thread triggered manually or on screen load, without a dedicated periodic WorkManager worker. Finally, an initialization ordering flaw in `TrainingApplication.onCreate()` caused `getStravaAccessToken()` to return null during startup scheduling, actively cancelling the scheduled work on every app launch.
4. *Preservation of Core Invariants*:
   - Unique work names (`automated_strava_segments_sync_work`, `automated_strava_routes_sync_work`) and `ExistingPeriodicWorkPolicy.UPDATE` remain strictly preserved.
   - Network connectivity constraint (`NetworkType.CONNECTED`) and battery constraint (`requiresBatteryNotLow(true)`) remain preserved.
   - 7-day TTL cache pruning in SegmentsRepository and RoutesRepository remains strictly preserved.
   - Manual synchronization cards and timestamps in `StravaSettingsDialog` remain intact.
   - Equipment database persistence (`EquipmentDbHelper`) and finished broadcast (`SYNCHRONIZE_EQUIPMENT_STRAVA_FINISHED`) remain intact.
   - Clean-room test suite pass rate (100%) and zero regressions.

---

## 2. Acceptance Criteria (Given-When-Then)

### Criterion 1: Startup Scheduling Preservation Without Cancellation
* **Given** an athlete authenticated with Strava with automated sync enabled,
* **When** `TrainingApplication.onCreate()` executes during application startup,
* **Then** `StravaSegmentsSyncWorker`, `StravaRoutesSyncWorker`, and `StravaEquipmentSyncWorker` are enqueued with `ExistingPeriodicWorkPolicy.UPDATE` and are NOT cancelled.

### Criterion 2: Configurable Interval and Toggle Honoring
* **Given** an athlete who configures a 3-day sync interval in Strava settings,
* **When** `schedule(context)` executes for segments, routes, or equipment workers,
* **Then** the periodic work request is enqueued with an interval duration of 3 days.
* **Given** an athlete who disables automated sync for a given feature,
* **When** `schedule(context)` executes,
* **Then** the corresponding unique periodic work request is cancelled in WorkManager.

### Criterion 3: Automated Equipment Worker Execution
* **Given** an athlete with a connected Strava account and valid equipment,
* **When** `StravaEquipmentSyncWorker.doWork()` executes,
* **Then** equipment synchronization executes via `StravaEquipmentSynchronizeThread`, updates the database, updates `lastUpdateTimeOfStravaEquipment`, sends the finished broadcast, and returns `Result.success()`.

### Criterion 4: Proactive Token Refresh in Background
* **Given** an expired Strava access token with a valid refresh token,
* **When** any of the three background sync workers run,
* **Then** `StravaHelper.getRefreshedAccessToken()` refreshes the token before network calls, preventing 401 Unauthorized errors.

### Criterion 5: Data Purge Invariant
* **Given** an athlete disconnecting Strava or revoking OAuth permissions,
* **When** `StravaDataPurgeManager.executeLocalDataPurge(context)` runs,
* **Then** `automated_strava_equipment_sync_work` is cancelled alongside segments and routes tasks.

### Criterion 6: 100% 9-Language Localization Parity
* **Given** all 9 supported application locales,
* **When** translation parity tests run,
* **Then** equipment sync string resources are present and non-empty in all locales.

---

## 3. Test Specification (TST-EXP-016)

| Test ID | Scope | Verification Target | Expected Result |
| :--- | :--- | :--- | :--- |
| `TST-EXP-016.1` | Unit Test | `StravaSegmentsSyncWorkerTest.kt` | Asserts `schedule()` respects user-configured interval days and cancels work when toggle is disabled |
| `TST-EXP-016.2` | Unit Test | `StravaRoutesSyncWorkerTest.kt` | Asserts `schedule()` respects user-configured interval days and cancels work when toggle is disabled |
| `TST-EXP-016.3` | Unit Test | `StravaEquipmentSyncWorkerTest.kt` | Asserts `schedule()` enqueues unique work with UPDATE policy, connected network constraint, and honors toggle/interval |
| `TST-EXP-016.4` | Unit Test | `StravaEquipmentSyncWorkerTest.kt` | Asserts `doWork()` executes equipment sync and returns `Result.success()` when connected and enabled |
| `TST-EXP-016.5` | Unit Test | `StravaEquipmentSyncWorkerTest.kt` | Asserts `doWork()` returns `Result.success()` without sync when disconnected or disabled |
| `TST-EXP-016.6` | Unit Test | `StravaDataPurgeManagerTest.kt` | Asserts `executeLocalDataPurge` cancels `automated_strava_equipment_sync_work` |
| `TST-EXP-016.7` | Unit Test | `TrainingApplicationTest.kt` | Asserts preference getters read from `SharedPreferences` instead of hardcoded constants |
| `TST-EXP-016.8` | Localization Test | 9 language resource files | Asserts new equipment sync strings are present and non-empty in all 9 locales |
| `TST-EXP-016.9` | Clean-Room Suite | All test modules | `./gradlew testDebugUnitTest` runs with 100% pass rate and zero regressions |

---

## 4. ASPICE Traceability Matrix

| Requirement | Test Spec ID | Target File | Verification Method | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-EXP-019.1` (Lifecycle Ordering & Non-Cancellation) | `TST-EXP-016.1`, `TST-EXP-016.2`, `TST-EXP-016.3` | `TrainingApplication.java` | Unit tests | Specified |
| `REQ-EXP-019.2` (Preference Reading Parity) | `TST-EXP-016.7` | `TrainingApplication.java` | Unit tests | Specified |
| `REQ-EXP-019.3` (Automated Equipment Sync Worker) | `TST-EXP-016.3`, `TST-EXP-016.4`, `TST-EXP-016.5` | `StravaEquipmentSyncWorker.kt` | `StravaEquipmentSyncWorkerTest.kt` | Specified |
| `REQ-EXP-019.4` (Interval & Toggle Honoring) | `TST-EXP-016.1`, `TST-EXP-016.2` | `StravaSegmentsSyncWorker.kt` / `StravaRoutesSyncWorker.kt` | Unit tests | Specified |
| `REQ-EXP-019.5` (Purge Invariant) | `TST-EXP-016.6` | `StravaDataPurgeManager.kt` | `StravaDataPurgeManagerTest.kt` | Specified |
| `REQ-EXP-019.6` (Localization Parity) | `TST-EXP-016.8` | `strings.xml` (9 locales) | Unit test / lint | Specified |
| `REQ-PRO-001` (Clean-Room Zero Regressions) | `TST-EXP-016.9` | Entire Codebase | `./gradlew testDebugUnitTest` | Specified |
