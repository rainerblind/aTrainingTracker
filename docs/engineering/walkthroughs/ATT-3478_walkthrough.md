# Stage 5 Walkthrough - ATT-3478: Fix automated background synchronization for Strava segments, routes and equipment

**Ticket**: [ATT-3478](https://rainerblind.atlassian.net/browse/ATT-3478)  
**Stage 5 Subtask**: [ATT-3517](https://rainerblind.atlassian.net/browse/ATT-3517) (`[Test]`)  
**Parent Epic**: [ATT-597](https://rainerblind.atlassian.net/browse/ATT-597) (*Strava Support*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.3`  
**Requirement Mapping**: `REQ-EXP-019` (`Verified`)  
**Test Mapping**: `TST-EXP-016` (`Verified`)  
**Branch**: `feature/ATT-3478`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-14  

---

## 1. Executive Summary & Verification Overview

In `aTrainingTracker`, automated background synchronization of Strava data (starred segments, routes, and athlete equipment) suffered from four interconnected defects:
1. **Startup Cancellation Race Condition**: In earlier app startup sequences, worker scheduling was evaluated before `cSharedPreferences` was initialized, causing `TrainingApplication.getStravaAccessToken()` to return `null` and actively cancelling scheduled periodic WorkManager jobs on cold launch.
2. **Missing Periodic Equipment Sync Worker**: Equipment sync (`StravaEquipmentSynchronizeThread`) was only executed ad-hoc upon manual user interaction in settings or equipment screens, leaving athlete bikes and running shoes unsynchronized in the background.
3. **Hardcoded Schedulers Ignoring Athlete Preferences**: `StravaSegmentsSyncWorker` and `StravaRoutesSyncWorker` hardcoded 1-day intervals and ignored the user's interval preferences (`strava_segments_sync_interval_days`, `strava_routes_sync_interval_days`) and enable toggles (`automated_strava_segments_sync`, `automated_strava_routes_sync`). In `TrainingApplication.java`, getters hardcoded `return true;` and `return "1";` without querying `SharedPreferences`.
4. **Token Expiration Vulnerability**: Workers inspected raw access tokens without proactively refreshing credentials via `StravaHelper.getRefreshedAccessToken()`, causing background jobs to fail with HTTP 401 Unauthorized after the 6-hour OAuth token lifetime.

ATT-3478 resolves these issues end-to-end:
- **Startup Lifecycle Invariant**: Guaranteed `PreferenceManager.setDefaultValues()` and `cSharedPreferences` initialization occur strictly before `initWorkManager()` and worker scheduling in `TrainingApplication.onCreate()`.
- **Net-New Equipment Sync Worker (`StravaEquipmentSyncWorker.kt`)**: Implemented periodic `CoroutineWorker` registered as `automated_strava_equipment_sync_work` using `ExistingPeriodicWorkPolicy.UPDATE`, connected network constraints, and exponential backoff retry criteria. Executes `StravaEquipmentSynchronizeThread` wrapped inside `withContext(Dispatchers.IO)`.
- **Preference-Aware Schedulers & Getters**: Upgraded `TrainingApplication.java`, `StravaSegmentsSyncWorker.kt`, and `StravaRoutesSyncWorker.kt` to read configured intervals (defaulting to 1 day) and cancellation toggles from `SharedPreferences`.
- **Proactive Token Refresh**: All three workers invoke `StravaHelper.getRefreshedAccessToken()` prior to remote synchronization, eliminating 401 Unauthorized failures.
- **Data Purge Invariant**: Extended `StravaDataPurgeManager.kt` to cancel `automated_strava_equipment_sync_work` alongside segments and routes upon disconnect or purge.
- **100% 9-Language Localization Parity**: Externalized and translated `automated_strava_equipment_sync`, `automated_strava_equipment_sync_summary`, and `strava_equipment_sync_interval` across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-EXP-019` | `TST-EXP-016.1` (Startup Contract) | Automated Unit Test (`TrainingApplicationStravaSyncTest`) | **PASSED** | `Verified` |
| `REQ-EXP-019` | `TST-EXP-016.2` (Preference Parity) | Automated Unit Test (`TrainingApplicationStravaSyncTest`) | **PASSED** | `Verified` |
| `REQ-EXP-019` | `TST-EXP-016.3` (Equipment Worker) | Automated Unit Test (`StravaEquipmentSyncWorkerTest`) | **PASSED** | `Verified` |
| `REQ-EXP-019` | `TST-EXP-016.4` (Segments/Routes Schedulers) | Automated Unit Tests (`StravaSegmentsSyncWorkerTest`, `StravaRoutesSyncWorkerTest`) | **PASSED** | `Verified` |
| `REQ-EXP-019` | `TST-EXP-016.5` (Purge Invariant) | Automated Unit Test (`StravaDataPurgeManagerTest`) | **PASSED** | `Verified` |
| `REQ-EXP-019` | `TST-EXP-016.6` (9-Language Parity) | Automated Parity Test (`TranslationParityTest`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-EXP-016.7` (Full Regression) | Clean-Room `./gradlew testDebugUnitTest` | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Targeted Unit Test Suites
Command:
```bash
./gradlew testDebugUnitTest \
  --tests "com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaEquipmentSyncWorkerTest" \
  --tests "com.atrainingtracker.trainingtracker.segments.StravaSegmentsSyncWorkerTest" \
  --tests "com.atrainingtracker.trainingtracker.routes.StravaRoutesSyncWorkerTest" \
  --tests "com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaDataPurgeManagerTest" \
  --tests "com.atrainingtracker.trainingtracker.TrainingApplicationStravaSyncTest"
```
Output:
```text
BUILD SUCCESSFUL in 34s
32 actionable tasks: 1 executed, 31 up-to-date
```

All 33 targeted unit tests passed:
- `StravaEquipmentSyncWorkerTest` (11 tests passed):
  - `schedule_enqueuesPeriodicWork_whenStravaConnectedAndSyncEnabled`
  - `schedule_cancelsWork_whenStravaNotConnected`
  - `schedule_cancelsWork_whenSyncDisabledInPreferences`
  - `schedule_respectsConfiguredInterval_whenIntervalIsThreeDays`
  - `doWork_returnsSuccess_whenSyncSucceeds`
  - `doWork_returnsSuccess_withoutSyncing_whenStravaNotConnected`
  - `doWork_returnsSuccess_withoutSyncing_whenSyncDisabledInPreferences`
  - `doWork_returnsRetry_whenSyncThrowsException`
  - `doWork_returnsRetry_whenRefreshedAccessTokenIsNull`
  - `doWork_refreshesAccessToken_beforeSyncing`
  - `doWork_handlesThreadInitializationCrash_gracefullyAndReturnsRetry`
- `StravaSegmentsSyncWorkerTest` (8 tests passed):
  - `schedule_enqueuesPeriodicWork_withConfiguredInterval_whenSyncEnabled`
  - `schedule_cancelsWork_whenSyncDisabledInPreferences`
  - `schedule_cancelsWork_whenStravaNotConnected`
  - `doWork_returnsSuccess_whenSyncSucceeds`
  - `doWork_returnsSuccess_withoutSyncing_whenSyncDisabled`
  - `doWork_returnsSuccess_withoutSyncing_whenStravaNotConnected`
  - `doWork_returnsRetry_whenSyncThrowsException`
  - `doWork_refreshesAccessToken_beforeSyncing`
- `StravaRoutesSyncWorkerTest` (8 tests passed):
  - `schedule_enqueuesPeriodicWork_withConfiguredInterval_whenSyncEnabled`
  - `schedule_cancelsWork_whenSyncDisabledInPreferences`
  - `schedule_cancelsWork_whenStravaNotConnected`
  - `doWork_returnsSuccess_whenSyncSucceeds`
  - `doWork_returnsSuccess_withoutSyncing_whenSyncDisabled`
  - `doWork_returnsSuccess_withoutSyncing_whenStravaNotConnected`
  - `doWork_returnsRetry_whenSyncThrowsException`
  - `doWork_refreshesAccessToken_beforeSyncing`
- `StravaDataPurgeManagerTest` (2 tests passed):
  - `executeLocalDataPurge_cancelsAllStravaBackgroundWorkers`
  - `executeLocalDataPurge_purgesDatabaseAndPreferences`
- `TrainingApplicationStravaSyncTest` (4 tests passed):
  - `testSegmentsSyncGettersAndSetters_readWritePreferences`
  - `testRoutesSyncGettersAndSetters_readWritePreferences`
  - `testEquipmentSyncGettersAndSetters_readWritePreferences`
  - `testSafeFallbacks_whenPreferencesUninitialized`

### Clean-Room Regression Suite (`./gradlew testDebugUnitTest`)
* Command: `./gradlew testDebugUnitTest`
* Duration: 2m 37s
* Result: **100% Tests Passed** (0 failures, 0 errors, 0 regressions across all modules).

---

## 4. Hardware / Physical Verification (Pixel 10) & UI Consistency (Rule 23)

### UI Consistency & Component Reuse
* **Preference Hierarchy**: Configured within `app/src/main/res/xml/prefs_strava.xml` under new category `automated_strava_equipment_sync`.
* **Component Parity**: Reuses existing `SwitchPreferenceCompat` (`key="automated_strava_equipment_sync"`) and `ListPreference` (`key="strava_equipment_sync_interval_days"`), directly referencing the established shared interval arrays (`@array/backup_interval_entries`, `@array/backup_interval_values`).
* **Design Guidelines (§5) Check**:
  - Shapes: Native platform AndroidX Preference card style.
  - Spacing & Margins: Inherits default Material Preference styling with dependency linking (`dependency="automated_strava_equipment_sync"`).
  - Colors/Themes: Fully supports Light and Dark/AMOLED modes via standard theme attributes.
  - Typography: Matches segments and routes preference entries.
* **Deviations**: None. Reuses canonical preference components and localized string tables.

---

## 5. Invariant & Governance Verification

1. **Zero Production Regressions**: Clean-room test suite `./gradlew testDebugUnitTest` executed with 100% pass rate.
2. **Requirement Governance**: `python3 tools/verify_requirement_governance.py --base-ref sprint/2026-42.3` passed cleanly (net-new `REQ-EXP-019` detected).
3. **Living Documentation Synchronized**: Status of `REQ-EXP-019` in `docs/requirements.md` and `TST-EXP-016` in `docs/tests.md` updated from `Specified` to `Verified`.
4. **Subtask Completion**: Subtask `ATT-3517` (`[Test]`) updated with walkthrough deliverable and submitted for Gate 5 audit.
5. **Parent Ticket Review**: Parent ticket `ATT-3478` handed over to `Final Review (Human)` for final acceptance sign-off.
