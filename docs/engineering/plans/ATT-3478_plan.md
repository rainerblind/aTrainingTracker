# Stage 3: Implementation Plan - ATT-3478: Fix automated background synchronization for Strava segments, routes and equipment

**Ticket**: [ATT-3478](https://atrainingtracker.atlassian.net/browse/ATT-3478)  
**Sub-task**: [ATT-3515](https://atrainingtracker.atlassian.net/browse/ATT-3515) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-597](https://atrainingtracker.atlassian.net/browse/ATT-597) (*Strava Support*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.3`  
**Requirement Mapping**: `REQ-EXP-019` (*Automated Background Synchronization for Strava Segments, Routes, and Equipment*), amending `REQ-EXP-011` and `REQ-EXP-012`  
**Test Spec ID**: `TST-EXP-016`  
**Branch**: `feature/ATT-3478`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-14  

---

## 1. Problem Description & Root Cause Analysis

Automated background synchronization of Strava data (starred segments, routes, and athlete equipment) has failed to operate reliably in production. Forensic analysis revealed coupled root causes across the startup lifecycle, worker coverage, preference handling, and OAuth token validity:
1. **Startup Cancellation Race Condition (`TrainingApplication.java`)**: In earlier revisions of `TrainingApplication.onCreate()`, `initWorkManager()` and worker scheduling were called before initializing `cSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)`. When `StravaSegmentsSyncWorker.schedule()` and `StravaRoutesSyncWorker.schedule()` checked `TrainingApplication.getStravaAccessToken()`, the method guarded with `if (cSharedPreferences == null) return null;`. As a consequence, on every application cold launch, both workers detected Strava as disconnected and actively invoked `cancelUniqueWork()`, deleting previously scheduled jobs.
2. **Missing Periodic Worker for Strava Equipment**: Equipment synchronization (`StravaEquipmentSynchronizeThread`) was only executed ad-hoc upon explicit manual button clicks in settings or when viewing the equipment screen. No automated periodic worker existed to synchronize bikes and shoes in the background.
3. **Hardcoded Schedulers Ignoring User Preferences**: `StravaSegmentsSyncWorker` and `StravaRoutesSyncWorker` hardcoded 1-day intervals and ignored the user's interval preferences (`strava_segments_sync_interval_days`, `strava_routes_sync_interval_days`) and enable switches (`automated_strava_segments_sync`, `automated_strava_routes_sync`). In `TrainingApplication.java`, getters hardcoded `return true;` and `return "1";` without querying `SharedPreferences`.
4. **Token Expiry During Background Execution**: Workers checked raw tokens without proactively refreshing expiring credentials via `StravaHelper.getRefreshedAccessToken()`, causing background jobs to fail with HTTP 401 Unauthorized once the 6-hour token TTL expired.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-EXP-019` (*Automated Background Synchronization for Strava Segments, Routes, and Equipment*), amending `REQ-EXP-011` and `REQ-EXP-012`
* **Test Spec**: `TST-EXP-016` (*Automated Background Synchronization for Strava Segments, Routes, and Equipment Verification*)
* **Living Docs Updated**:
  - `docs/requirements.md` (`REQ-EXP-019` added)
  - `docs/tests.md` (`TST-EXP-016` added)

---

## 3. System Invariants & Preserved Behavior

1. **WorkManager Scheduling Invariant**: Unique periodic work requests SHALL be registered using `ExistingPeriodicWorkPolicy.UPDATE` for `automated_strava_segments_sync_work`, `automated_strava_routes_sync_work`, and net-new `automated_strava_equipment_sync_work`. Work constraints (`NetworkType.CONNECTED`, `requiresBatteryNotLow(true)`) and exponential backoff retry criteria remain strictly preserved.
2. **7-Day TTL Cache Pruning Invariant**: In `StravaSegmentsSyncWorker.doWork()` and `StravaRoutesSyncWorker.doWork()`, 7-day TTL cache pruning (`pruneExpiredSegments()`, `pruneExpiredRoutes()`) SHALL always execute prior to remote sync, ensuring compliance with Strava API Agreement Section 6.2.
3. **Equipment Database Persistence & Broadcast**: Equipment synchronization executes via `StravaEquipmentSynchronizeThread`, writing to `EquipmentDbHelper` and broadcasting `SYNCHRONIZE_EQUIPMENT_STRAVA_FINISHED` upon completion, preserving seamless reactivity for `EquipmentFragment` and Compose UI.
4. **Data Purge Invariant**: When an athlete disconnects or revokes Strava access, `StravaDataPurgeManager.executeLocalDataPurge(context)` cancels `automated_strava_equipment_sync_work` alongside segments and routes tasks.
5. **Clean-Room Full Suite Regression**: 100% test pass rate across all modules (`./gradlew testDebugUnitTest`).

---

## 4. Proposed Architectural Changes (SWE.2)

### 4.1 Component 1: `TrainingApplication.java`
* Path: `app/src/main/java/com/atrainingtracker/trainingtracker/TrainingApplication.java`
* **Changes**:
  1. Verify lifecycle ordering: `PreferenceManager.setDefaultValues()` and `cSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)` execute before `initWorkManager()` and worker scheduling in `onCreate()` (already verified).
  2. Register `StravaEquipmentSyncWorker.Companion.schedule(this)` inside `if (sWorkManagerAvailable)` alongside segments and routes workers.
  3. Define constants:
     - `SP_AUTOMATED_STRAVA_EQUIPMENT_SYNC = "automated_strava_equipment_sync"`
     - `SP_STRAVA_EQUIPMENT_SYNC_INTERVAL_DAYS = "strava_equipment_sync_interval_days"`
  4. Fix getter and setter implementations:
     - `isAutomatedStravaSegmentsSyncEnabled()` -> reads `cSharedPreferences.getBoolean(SP_AUTOMATED_STRAVA_SEGMENTS_SYNC, true)`
     - `getStravaSegmentsSyncIntervalDays()` -> reads `cSharedPreferences.getString(SP_STRAVA_SEGMENTS_SYNC_INTERVAL_DAYS, "1")`
     - `isAutomatedStravaRoutesSyncEnabled()` -> reads `cSharedPreferences.getBoolean(SP_AUTOMATED_STRAVA_ROUTES_SYNC, true)`
     - `getStravaRoutesSyncIntervalDays()` -> reads `cSharedPreferences.getString(SP_STRAVA_ROUTES_SYNC_INTERVAL_DAYS, "1")`
     - `isAutomatedStravaEquipmentSyncEnabled()` -> reads `cSharedPreferences.getBoolean(SP_AUTOMATED_STRAVA_EQUIPMENT_SYNC, true)`
     - `setAutomatedStravaEquipmentSyncEnabled(boolean)` -> writes to `cSharedPreferences`
     - `getStravaEquipmentSyncIntervalDays()` -> reads `cSharedPreferences.getString(SP_STRAVA_EQUIPMENT_SYNC_INTERVAL_DAYS, "1")`
     - `setStravaEquipmentSyncIntervalDays(String)` -> writes to `cSharedPreferences`

### 4.2 Component 2: `StravaEquipmentSyncWorker.kt` (Net-New)
* Path: `app/src/main/java/com/atrainingtracker/trainingtracker/onlinecommunities/strava/StravaEquipmentSyncWorker.kt`
* **Changes**:
  1. Create `StravaEquipmentSyncWorker(context, params) : CoroutineWorker(context, params)`.
  2. In `Companion`:
     - `const val WORK_NAME = "automated_strava_equipment_sync_work"`
     - `fun schedule(context: Context)`:
       - If WorkManager unavailable: log warning and return.
       - If Strava disconnected or `!TrainingApplication.isAutomatedStravaEquipmentSyncEnabled()`: cancel unique work `WORK_NAME` and return.
       - Query interval days via `TrainingApplication.getStravaEquipmentSyncIntervalDays().toLongOrNull() ?: 1L`.
       - Build `PeriodicWorkRequestBuilder<StravaEquipmentSyncWorker>(intervalDays, TimeUnit.DAYS)` with `NetworkType.CONNECTED`, `requiresBatteryNotLow(true)`, and `BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS`.
       - Enqueue with `ExistingPeriodicWorkPolicy.UPDATE`.
  3. In `doWork()`:
     - Check `TrainingApplication.isAutomatedStravaEquipmentSyncEnabled()`; if false, return `Result.success()`.
     - Obtain refreshed access token via `StravaHelper.getRefreshedAccessToken()`; if null, log and return `Result.success()`.
     - Execute equipment synchronization via `StravaEquipmentSynchronizeThread(applicationContext).run()`.
     - Inspect completion status: if successful, return `Result.success()`; on failure or exception, return `Result.retry()`.

### 4.3 Component 3: `StravaSegmentsSyncWorker.kt`
* Path: `app/src/main/java/com/atrainingtracker/trainingtracker/segments/StravaSegmentsSyncWorker.kt`
* **Changes**:
  1. In `schedule(context)`:
     - Check `val enabled = TrainingApplication.isAutomatedStravaSegmentsSyncEnabled()`. If disconnected or `!enabled`: invoke `cancelUniqueWork(WORK_NAME)` and return.
     - Query configured interval via `val intervalDays = TrainingApplication.getStravaSegmentsSyncIntervalDays().toLongOrNull() ?: 1L`.
     - Enqueue periodic work request with `intervalDays, TimeUnit.DAYS` and `ExistingPeriodicWorkPolicy.UPDATE`.
  2. In `doWork()`:
     - Enforce 7-day TTL pruning: `SegmentsRepository.getInstance(applicationContext).pruneExpiredSegments()`.
     - Check `if (!TrainingApplication.isAutomatedStravaSegmentsSyncEnabled()) return Result.success()`.
     - Call `val accessToken = StravaHelper.getRefreshedAccessToken()`. If null, return `Result.success()`.
     - Proceed with `repository.syncStarredSegments(BSportType.UNKNOWN)` and timestamp recording.

### 4.4 Component 4: `StravaRoutesSyncWorker.kt`
* Path: `app/src/main/java/com/atrainingtracker/trainingtracker/routes/StravaRoutesSyncWorker.kt`
* **Changes**:
  1. In `schedule(context)`:
     - Check `val enabled = TrainingApplication.isAutomatedStravaRoutesSyncEnabled()`. If disconnected or `!enabled`: invoke `cancelUniqueWork(WORK_NAME)` and return.
     - Query configured interval via `val intervalDays = TrainingApplication.getStravaRoutesSyncIntervalDays().toLongOrNull() ?: 1L`.
     - Enqueue periodic work request with `intervalDays, TimeUnit.DAYS` and `ExistingPeriodicWorkPolicy.UPDATE`.
  2. In `doWork()`:
     - Enforce 7-day TTL pruning: `RoutesRepository.getInstance(applicationContext).pruneExpiredRoutes()`.
     - Check `if (!TrainingApplication.isAutomatedStravaRoutesSyncEnabled()) return Result.success()`.
     - Call `val accessToken = StravaHelper.getRefreshedAccessToken()`. If null, return `Result.success()`.
     - Proceed with `repository.syncRoutesFromStrava()` and return result.

### 4.5 Component 5: `StravaDataPurgeManager.kt` & `StravaSettingsDialog.kt`
* Paths:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/onlinecommunities/strava/StravaDataPurgeManager.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/strava/StravaSettingsDialog.kt`
* **Changes**:
  1. In `StravaDataPurgeManager.executeLocalDataPurge(context)`: add `workManager.cancelUniqueWork(StravaEquipmentSyncWorker.WORK_NAME)`.
  2. In `StravaSettingsDialog.kt`:
     - In `LaunchedEffect(authState)` on `StravaAuthState.Success`: add `StravaEquipmentSyncWorker.schedule(context)`.
     - In `AppDialogActions.SaveCancel` save action: add `StravaEquipmentSyncWorker.schedule(context)`.

### 4.6 Component 6: Preference Layout & 100% 9-Language Localization Parity
* Paths:
  - `app/src/main/res/xml/prefs_strava.xml`
  - `app/src/main/res/values*/strings.xml` (all 9 supported locales: default/en, de, fr, es, it, ja, nl, pl, pt)
* **Changes**:
  1. Add `automated_strava_equipment_sync` SwitchPreferenceCompat and `strava_equipment_sync_interval_days` ListPreference to `prefs_strava.xml`.
  2. Define and translate string resources:
     - `automated_strava_equipment_sync`
     - `automated_strava_equipment_sync_summary`
     - `strava_equipment_sync_interval`

---

## 5. UI Consistency (Rule 23)

* **Closest Reference UI**: `prefs_strava.xml` existing `automated_strava_segments_sync` and `automated_strava_routes_sync` preference categories.
* **Component Reuse**: Standard AndroidX Preference components (`SwitchPreferenceCompat`, `ListPreference`) referencing shared array entries (`@array/backup_interval_entries`, `@array/backup_interval_values`).
* **Design Token Compliance**: Consistent padding, preference key naming conventions, and dependency attributes matching existing Strava settings layout.

---

## 6. Atomic Implementation Step Sequence

### Step 1: Preference Infrastructure in `TrainingApplication.java`
* Add `SP_AUTOMATED_STRAVA_EQUIPMENT_SYNC` and `SP_STRAVA_EQUIPMENT_SYNC_INTERVAL_DAYS`.
* Update getters (`isAutomatedStravaSegmentsSyncEnabled`, `getStravaSegmentsSyncIntervalDays`, `isAutomatedStravaRoutesSyncEnabled`, `getStravaRoutesSyncIntervalDays`, and equipment equivalents) to read from `cSharedPreferences` with safe null fallback.
* Target: `app/src/main/java/com/atrainingtracker/trainingtracker/TrainingApplication.java`.
* Verification: Compile check and unit tests.

### Step 2: Implement `StravaEquipmentSyncWorker.kt`
* Author `StravaEquipmentSyncWorker.kt` with `schedule()` honoring toggle/interval and `doWork()` executing equipment sync via `StravaEquipmentSynchronizeThread` with token refresh.
* Register `StravaEquipmentSyncWorker.Companion.schedule(this)` in `TrainingApplication.onCreate()`.
* Target: `app/src/main/java/com/atrainingtracker/trainingtracker/onlinecommunities/strava/StravaEquipmentSyncWorker.kt`.
* Verification: Create `StravaEquipmentSyncWorkerTest.kt`.

### Step 3: Upgrade `StravaSegmentsSyncWorker.kt` & `StravaRoutesSyncWorker.kt`
* Update `schedule()` to query toggle and interval preferences and cancel work when disabled.
* Update `doWork()` to check toggle and call `StravaHelper.getRefreshedAccessToken()`.
* Targets:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/segments/StravaSegmentsSyncWorker.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/routes/StravaRoutesSyncWorker.kt`
* Verification: Update `StravaSegmentsSyncWorkerTest.kt` and `StravaRoutesSyncWorkerTest.kt`.

### Step 4: Disconnect & Purge Synchronization
* Add `automated_strava_equipment_sync_work` cancellation in `StravaDataPurgeManager.kt`.
* Add `StravaEquipmentSyncWorker.schedule(context)` in `StravaSettingsDialog.kt`.
* Targets:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/onlinecommunities/strava/StravaDataPurgeManager.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/strava/StravaSettingsDialog.kt`
* Verification: Update `StravaDataPurgeManagerTest.kt`.

### Step 5: Preferences XML & 9-Language Localization Parity
* Add equipment sync preference category to `prefs_strava.xml`.
* Translate strings across all 9 locales: EN, DE, ES, FR, IT, JA, NL, PL, PT.
* Targets: `prefs_strava.xml`, `strings.xml` in `values/`, `values-de/`, `values-fr/`, `values-es/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
* Verification: `TranslationParityTest.kt`.

### Step 6: Targeted Unit Test Suite Execution
* Run: `./gradlew testDebugUnitTest --tests "*Strava*SyncWorker*" --tests "*StravaDataPurgeManager*" --tests "*TrainingApplicationTest*"`.
* Verification: All targeted tests pass.

### Step 7: Clean-Room Full Suite Regression Execution
* Run: `./gradlew testDebugUnitTest`.
* Verification: 100% pass rate with zero regressions across all modules.

---

## 7. ASPICE Verification Matrix

| Verification Scope | Target File | Test Class | Status |
| :--- | :--- | :--- | :--- |
| Preference Reading & Parity | `TrainingApplication.java` | `TrainingApplicationTest.kt` | Planned |
| Equipment Worker Scheduling & Execution | `StravaEquipmentSyncWorker.kt` | `StravaEquipmentSyncWorkerTest.kt` | Planned |
| Segments Worker Preference & Refresh | `StravaSegmentsSyncWorker.kt` | `StravaSegmentsSyncWorkerTest.kt` | Planned |
| Routes Worker Preference & Refresh | `StravaRoutesSyncWorker.kt` | `StravaRoutesSyncWorkerTest.kt` | Planned |
| Data Purge Equipment Cancellation | `StravaDataPurgeManager.kt` | `StravaDataPurgeManagerTest.kt` | Planned |
| 9-Language Localization Parity | `strings.xml` (all 9 locales) | `TranslationParityTest.kt` | Planned |
| Clean-Room Regression | Entire Codebase | Full Test Suite | Planned |
