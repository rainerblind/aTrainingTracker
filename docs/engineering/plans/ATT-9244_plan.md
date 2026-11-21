# Stage 3: Implementation Plan - ATT-9244: Stop PebbleService When appMessages Unsupported & Close PebbleView.db Cleanly

**Ticket**: `ATT-9244` (`1 SP`, `Bug`, `severity-major`) | **Parent Epic**: `ATT-235` (*No Crashes & App Stability*) | **Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-20_03_Nacht` | **Requirement Mapping**: `REQ-STB-024` | **Test Mapping**: `TST-STB-024`  
**Branch**: `feature/ATT-9244` | **Author**: AI Agent 1 (`Flash`) | **Date**: 2026-11-21

## 1. Problem Description & Background
* When Pebble companion app is absent (`PebbleKit.areAppMessagesSupported(this) == false`), `PebbleService` stayed alive as `START_STICKY` running the 1-Hz `mUpdatePebbleReceiver` loop and leaked 4 `SQLiteConnection` objects on `PebbleView.db` from unclosed per-call `new PebbleDbHelper(this)` instances and an unclosed `PebbleDatabaseManager` singleton.

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-STB-024` (`REQ-STB-024.1`–`REQ-STB-024.4`, `docs/requirements/req_process_stability_part2.md`)
* **Test Mapping**: `TST-STB-024` (`TST-STB-024.1`–`TST-STB-024.4`, `docs/tests/tst_process_stability_part2.md`)

## 3. System Invariants & Preserved Behavior
1. **Supported Pebble Parity (`REQ-WEA-001`)**: When `isAppMessagesSupported()` is `true`, `START_STICKY`, all 8 receivers, PebbleKit handlers, and `BANALService` binding remain intact.
2. **Transparent DB Reopen (`ConfigPebbleView*`)**: Closing `PebbleDatabaseManager` in `PebbleService.onDestroy()` never breaks subsequent `ConfigPebbleViewsActivity`/`ConfigPebbleViewFragment` opens (`isOpen()` reopen guard).
3. **LoC & Scope Ceilings**: `TrainingApplication.java` stays untouched (`<= 2,135` LoC); `PebbleServiceBuildIn.java` and `DatabaseSchemaRegistry.kt` stay untouched; `Localization Parity: N/A - no UI strings`.
4. **SWE.2 / SWE.3 Architecture (`Rule 8.8`, `REQ-PRO-114`)**: No new subsystem, FGS, repository, or requirement prefix is introduced, and `TrackerService`/`BANALService`/`DeviceManager`/`MyBTLEDevice` are untouched; no `docs/architecture*.md` update required.

## 4. Proposed Architectural Changes
* **Component 1 (`PebbleDatabaseManager.java`)**: Add `mDatabase` cache with `synchronized` `isOpen()` reopen guard in `getDatabase()` (`TrackingViewsDatabaseManager` pattern) + synchronized `close()`, `closeIfInitialized()`, and `@VisibleForTesting resetInstanceForTesting()`.
* **Component 2 (`PebbleService.java`)**: Add testable `isAppMessagesSupported()` hook, early unsupported exit in `onCreate()` / `onStartCommand()` (`START_NOT_STICKY`) / `startPebbleWatchApp()`, `mIsBanalBound` + `mBaseReceiversRegistered` + `mReceiversRegistered` teardown guards, `PebbleDatabaseManager.getSensorTypeList(mViewId)` delegation preserving `mRows`, and `PebbleDatabaseManager.closeIfInitialized()` on destroy.
* **UI Consistency**: No UI changes.

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
### Step 1: Harden `PebbleDatabaseManager` Connection Lifecycle (`REQ-STB-024.3`)
* **Files**: `app/src/main/java/com/atrainingtracker/trainingtracker/smartwatch/pebble/PebbleDatabaseManager.java`
* **Changes**: Cache `mDatabase`; update `getDatabase()` with `synchronized` `isOpen()` check & `cPebbleDbHelper.close()` before reopening; add `synchronized void close()`, `static void closeIfInitialized()`, and `@VisibleForTesting static void resetInstanceForTesting()`.
### Step 2: Harden `PebbleService` Unsupported Exit & Teardown (`REQ-STB-024.1`, `REQ-STB-024.2`, `REQ-STB-024.3`)
* **Files**: `app/src/main/java/com/atrainingtracker/trainingtracker/smartwatch/pebble/PebbleService.java`
* **Changes**: Add `isAppMessagesSupported()`, `mIsBanalBound`, `mBaseReceiversRegistered`; in `onCreate()` & `onStartCommand()` return `START_NOT_STICKY` + `stopSelf` before `bindService`/receivers/`PebbleDatabaseManager`; in `startPebbleWatchApp()` check `isAppMessagesSupported()` before `updateSensorTypeList()` and guard `registerReceivers()` with `!mReceiversRegistered`; switch `updateSensorTypeList()` to `PebbleDatabaseManager.getInstance(this).getSensorTypeList(mViewId)` setting `mRows = result.size()` (remove `new PebbleDbHelper(this)` & unused imports); in `onDestroy()` guard `unbindService` & 8 `LifecycleUtils.safeUnregisterReceiver` calls, reset `mReceiversRegistered = false` in `stopPebbleWatchApp()` (skipping `closeAppOnPebble` if unregistered), and call `PebbleDatabaseManager.closeIfInitialized()`.
### Step 3: Author Robolectric Lifecycle & SQLite Closure Tests (`TST-STB-024.1`–`TST-STB-024.4`)
* **Files**: `app/src/test/java/com/atrainingtracker/trainingtracker/smartwatch/pebble/PebbleServiceLifecycleTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/smartwatch/pebble/PebbleDatabaseManagerTest.kt`
* **Changes**: Tag all test specs with `TST-STB-024` / `TST-STB-024.1`–`TST-STB-024.4`; verify unsupported exit (`stopSelf`, `START_NOT_STICKY`, 0 binds/receivers/DB opens, safe `onDestroy()`), mid-session unsupported `startPebbleWatchApp()`, supported `START_STICKY` + `mRows` + `mReceiversRegistered` reset + `closeIfInitialized()`, and `PebbleDatabaseManager` close + transparent reopen.
### Step 4: Targeted Tier 1 Verification & Governance (`TST-STB-024.4`)
* **Command**: `./gradlew :app:testDebugUnitTest --rerun --tests "com.atrainingtracker.trainingtracker.smartwatch.pebble.*" --tests "com.atrainingtracker.architecture.LifecycleServiceReceiverSafetyAuditTest" --tests "com.atrainingtracker.architecture.TryWithResourcesCursorAuditTest" --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest"` + `python3 tools/verify_requirement_governance.py --repo-root . --base-ref sprint/2026-11-20_03_Nacht`

## 6. Verification & Rollback Plan
* **Verification**: Tier 1 targeted Robolectric & architecture contract tests in Stage 4/5 (zero `--rerun-tasks`); live `emulator-5554` verification in Stage 6.
* **Rollback**: Isolated on `feature/ATT-9244`; full revert leaves `sprint/2026-11-20_03_Nacht` unaffected.
