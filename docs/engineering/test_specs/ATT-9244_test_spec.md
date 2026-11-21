# Stage 2: Requirement & Test Specification - ATT-9244: Stop PebbleService When appMessages Unsupported & Close PebbleView.db Cleanly

**Ticket**: `ATT-9244` (`1 SP`, `Bug`, `severity-major`) | **Parent Epic**: `ATT-235` (*No Crashes & App Stability*) | **Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-20_03_Nacht` | **Requirement Mapping**: `REQ-STB-024` | **Test Spec ID**: `TST-STB-024`  
**Branch**: `feature/ATT-9244` | **Author**: AI Agent 1 (`Flash`) | **Date**: 2026-11-21

## 1. Requirement Specification (`REQ-STB-024`)

### 1.1 Problem Statement & Rationale
* On devices without the Pebble companion app (`PebbleKit.areAppMessagesSupported(this) == false`), `PebbleService` stayed active as `START_STICKY` running a 1-Hz `mUpdatePebbleReceiver` loop and leaked 4 `SQLiteConnection` objects on `PebbleView.db` from unclosed per-call `new PebbleDbHelper(this)` instances and an unclosed `PebbleDatabaseManager` singleton.

### 1.2 Attributes
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit

### 1.3 Functional & Architectural Requirements
1. **`REQ-STB-024.1` (Immediate Unsupported Exit)**: `PebbleService` SHALL check `isAppMessagesSupported()` (wrapping `PebbleKit.areAppMessagesSupported(this)`) at the start of `onCreate()`, `onStartCommand()`, and `startPebbleWatchApp()`. When `false`: `onCreate()` skips `bindService(BANALService)` and all 8 receiver registrations and calls `stopSelf()`; `onStartCommand()` calls `stopSelf(startId)` and returns `Service.START_NOT_STICKY` before touching `PebbleDatabaseManager` or `startPebbleWatchApp()`; `startPebbleWatchApp()` calls `stopSelf()` and returns before `updateSensorTypeList()`.
2. **`REQ-STB-024.2` (Guarded Teardown & Receiver Reset)**: `PebbleService` MUST track `mIsBanalBound` and `mBaseReceiversRegistered` so `onDestroy()` calls `unbindService(mBanalConnection)` only when bound (no `IllegalArgumentException`) and unregisters receivers via `LifecycleUtils.safeUnregisterReceiver(...)`. `stopPebbleWatchApp()` MUST gate `closeAppOnPebble` and PebbleKit receiver unregistration on `mReceiversRegistered` and reset `mReceiversRegistered = false`.
3. **`REQ-STB-024.3` (Singleton `PebbleView.db` & Clean Closure)**: `PebbleService.updateSensorTypeList()` SHALL call `PebbleDatabaseManager.getInstance(this).getSensorTypeList(mViewId)` (preserving `mRows = mSensorTypeList.size()`, eliminating raw `new PebbleDbHelper(this)`). `PebbleDatabaseManager` SHALL use a `synchronized` `isOpen()` reopen check in `getDatabase()` and expose `close()` / `closeIfInitialized()` invoked from `PebbleService.onDestroy()` so zero `SQLiteConnectionPool` leaks occur while `ConfigPebbleView*` callers transparently reopen `PebbleView.db`.
4. **`REQ-STB-024.4` (Scope & Compatibility)**: Supported Pebble (`areAppMessagesSupported == true`, `REQ-WEA-001`) MUST remain intact; `TrainingApplication.java` stays untouched (`<= 2,135` LoC); `0` `SYS.2`/`SYS.3` impact (`REQ-PRO-131.4`); `Localization Parity: N/A - no UI strings`.

### 1.4 Acceptance Criteria (Given-When-Then)
* **AC-1 (Unsupported Host Immediate Exit)**: *Given* `isAppMessagesSupported() == false`, *When* `onCreate()` + `onStartCommand()` + `onDestroy()` run, *Then* `stopSelf()` runs, `onStartCommand()` returns `START_NOT_STICKY`, `BANALService` is not bound, `mUpdatePebbleReceiver` is not registered, `PebbleView.db` is not opened, and `onDestroy()` does not throw.
* **AC-2 (Supported Host, `mRows` Parity & Teardown)**: *Given* `isAppMessagesSupported() == true` and rows in `PebbleView.db`, *When* `PebbleService` runs `onCreate()`, `onStartCommand()`, `updateSensorTypeList()`, `stopPebbleWatchApp()`, and `onDestroy()`, *Then* `START_STICKY` is returned, `mSensorTypeList` & `mRows` match `PebbleDatabaseManager.getSensorTypeList(mViewId)`, `mReceiversRegistered` resets to `false`, and `PebbleDatabaseManager.closeIfInitialized()` runs.
* **AC-3 (`PebbleDatabaseManager` Close & Reopen)**: *Given* `PebbleDatabaseManager` closed via `close()` / `closeIfInitialized()`, *When* `ConfigPebbleView*` queries `getName()` / `getSensorTypeList()`, *Then* `getDatabase()` transparently reopens `PebbleView.db` without `IllegalStateException` and subsequent `close()` leaves zero open handles.

### 1.5 System Invariants
* `REQ-WEA-001` supported Pebble path unchanged; `TrainingApplication.java <= 2,135` LoC untouched; `PebbleServiceBuildIn.java` & `DatabaseSchemaRegistry.kt` untouched; `0` `src/main/` reads in tests; `Localization Parity: N/A - no UI strings`.

## 2. Test Specification (`TST-STB-024`)

### Test Case 1: `PebbleServiceLifecycleTest` (`TST-STB-024.1`, `TST-STB-024.2`)
* **Scope**: Robolectric Unit Test (`app/src/test/java/com/atrainingtracker/trainingtracker/smartwatch/pebble/PebbleServiceLifecycleTest.kt`)
* **Checks**: Unsupported-host `stopSelf()` + `START_NOT_STICKY` + zero bind/receivers/DB open + crash-free `onDestroy()`; mid-session `startPebbleWatchApp()` unsupported exit; supported-host `START_STICKY` + `updateSensorTypeList()` `mRows` parity + `mReceiversRegistered = false` reset + `closeIfInitialized()` on destroy.

### Test Case 2: `PebbleDatabaseManagerTest` (`TST-STB-024.3`)
* **Scope**: Robolectric SQLite Unit Test (`app/src/test/java/com/atrainingtracker/trainingtracker/smartwatch/pebble/PebbleDatabaseManagerTest.kt`)
* **Checks**: `close()` / `closeIfInitialized()` closes `SQLiteDatabase` (`isOpen == false`), subsequent `getName()` / `getSensorTypeList()` transparently reopens `PebbleView.db` without `IllegalStateException`, and final `close()` leaves zero open handles.

### Test Case 3: Localization Parity & Governance (`TST-STB-024.4`)
* **Scope**: Contract & Governance Audit (`Localization Parity: N/A - no UI strings` — zero `strings*.xml` edits)
* **Command**: `./gradlew :app:testDebugUnitTest --rerun --tests "com.atrainingtracker.trainingtracker.smartwatch.pebble.*" --tests "com.atrainingtracker.architecture.LifecycleServiceReceiverSafetyAuditTest" --tests "com.atrainingtracker.architecture.TryWithResourcesCursorAuditTest" --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest"` + `python3 tools/verify_requirement_governance.py`

## 3. Traceability Matrix

| Test Case | Scope | Method / Class Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-STB-024.1` | Robolectric Unit | `PebbleService` unsupported lifecycle | `REQ-STB-024.1`, `REQ-STB-024.2` | Specified |
| `TST-STB-024.2` | Robolectric Unit | `PebbleService` supported, `mRows`, teardown | `REQ-STB-024.2`, `REQ-STB-024.3` | Specified |
| `TST-STB-024.3` | Robolectric SQLite | `PebbleDatabaseManager.close/reopen` | `REQ-STB-024.3` | Specified |
| `TST-STB-024.4` | Contract & Gov | Lifecycle/Cursor/LoC/`verify_requirement_governance.py` | `REQ-STB-024.4`, `REQ-STB-016` | Specified |
