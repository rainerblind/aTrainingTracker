# Stage 3: Implementation Plan - ATT-4020: Fix MainActivity service binding leaks, unscoped coroutines and main-thread DB access

**Ticket**: [ATT-4020](https://rainerblind.atlassian.net/browse/ATT-4020)  
**Sub-task**: `[Impl-Plan]` | **Parent Epic**: None  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.5`  
**Requirement Mapping**: `REQ-STB-019`, `REQ-UI-356`  
**Test Mapping**: `TST-STB-019` (`TST-STB-019.1`–`TST-STB-019.7`)  
**Branch**: `feature/ATT-4020`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`) | **Date**: 2026-10-21  

---

## 1. Problem Description & Background

`MainActivityWithNavigation` exhibits 5 lifecycle/concurrency defects: leaking `ServiceConnection` across configuration changes, leaking handler runnables for 5 minutes, launching unscoped coroutines capturing the Activity, dropping battery alert deduplication on rotation / stopping monitoring on pause, running main-thread SQLite queries during startup/resume/binding, and leaking the Play Services raw dialog window.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-STB-019` (*MainActivity Service Binding Safety, Lifecycle Scoping, In-Ride Battery State Retention & Off-Main-Thread DB Execution*), amending `REQ-UI-356`.
* **Test Mapping**: `TST-STB-019` (`TST-STB-019.1`–`TST-STB-019.7` in `MainActivityLifecycleTest.kt` & suite).

---

## 3. System Invariants & Preserved Behavior

1. **Interface Contract**: `MainActivityWithNavigation` MUST implement `BANALService.GetBanalServiceInterface` (`getBanalServiceComm()`).
2. **Alert Deduplication**: Sensors must not emit duplicate warnings within the same severity tier per session.
3. **Thread Safety**: Zero SQLite operations on the main UI thread during startup, resume, or binding.
4. **Clean-Room Integrity**: 100% test pass rate across existing unit and contract test suites.

---

## 4. Proposed Architectural Changes

### Component 1: `MainActivityWithNavigation.kt` Lifecycle & Concurrency
* **Unbinding & Handler**: Unconditional `disconnectFromBANALService()` and `mHandler.removeCallbacks(mDisconnectFromBANALServiceRunnable)` in `onDestroy()`.
* **Structured Coroutines**: Replace unmanaged `CoroutineScope(Dispatchers.IO)` with `lifecycleScope.launch(Dispatchers.IO)`.
* **Retained Deduplication**: Persist `inRideAlertedSensorIds` and `inRideCriticalAlertedSensorIds` in companion/session state across configuration changes; keep monitoring collector running through `onPause()` when `isTracking() == true`.
* **Off-Thread DB**: Dispatch `getAllFilterData()`, `checkUnfinishedWorkout()`, `chooseStart()`, `chooseResume()`, and `checkGpsEnabledIfPermitted()` to `Dispatchers.IO`.
* **Dialog Lifecycle**: Retain `mPlayServicesErrorDialog` and invoke `dismiss()` in `onDestroy()`.

### UI Consistency (Rule 23)
* No UI changes (Activity lifecycle, service binding, coroutine scoping, and threading safety only).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

* **Step 1: Unconditional Service Teardown & Runnable Removal (`REQ-STB-019.1`)**: In `MainActivityWithNavigation.kt`, remove `!isChangingConfigurations` check in `onDestroy()` before calling `disconnectFromBANALService()`; call `mHandler.removeCallbacks(mDisconnectFromBANALServiceRunnable)`.
* **Step 2: Replace Unscoped Coroutines with lifecycleScope (`REQ-STB-019.2`)**: In `MainActivityWithNavigation.kt`, change `CoroutineScope(Dispatchers.IO).launch` to `lifecycleScope.launch(Dispatchers.IO)` in `checkSensorBatteryAfterWorkout`, `checkSensorBatteryOnStart`, and `startInRideBatteryMonitoring`.
* **Step 3: Retain Battery Deduplication State & Screen-Off Tracking Monitoring (`REQ-STB-019.3`)**: In `MainActivityWithNavigation.kt`, move alerted sensor sets to companion/session scope; avoid cancelling `mInRideBatteryMonitoringJob` in `onPause()` while `TrainingApplication.isTracking()` is true; clear sets on tracking stopped/started.
* **Step 4: Asynchronous Off-Main-Thread Database Execution (`REQ-STB-019.4`)**: In `MainActivityWithNavigation.kt`, dispatch `getAllFilterData` in `onServiceConnected`, `hasUnfinishedWorkout` in `onResume`, `discardOrFinishUnfinishedWorkout` in `chooseStart`, and `checkGpsEnabledIfPermitted` to `Dispatchers.IO`.
* **Step 5: Google Play Services Dialog Dismissal in onDestroy (`REQ-STB-019.5`)**: In `MainActivityWithNavigation.kt`, store dialog reference in `mPlayServicesErrorDialog` and dismiss in `onDestroy()`.
* **Step 6: Unit & Contract Tests (`TST-STB-019.1`–`TST-STB-019.5`)**: Create `app/src/test/java/com/atrainingtracker/trainingtracker/activities/MainActivityLifecycleTest.kt` verifying unbinding, runnable cleanup, scoping, deduplication survival, background DB dispatch, and dialog dismissal. Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.activities.MainActivityLifecycleTest"`.

---

## 6. Verification & Rollback Plan

* **Verification**: Run targeted tests, `TranslationParityTest`, and `./gradlew testDebugUnitTest` (~3,660+ tests).
* **Rollback**: Atomic revert of `feature/ATT-4020` branch if regressions occur.
