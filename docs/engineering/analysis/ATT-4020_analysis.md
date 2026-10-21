# Stage 1 Analysis: ATT-4020 - Fix MainActivity service binding leaks, unscoped coroutines and main-thread DB access

**Ticket**: [ATT-4020](https://rainerblind.atlassian.net/browse/ATT-4020)  
**Parent Epic**: None | **Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.5` | **Branch**: `feature/ATT-4020`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`) | **Date**: 2026-10-21  

---

## 1. Problem Domain & Root Cause Analysis

Forensic code inspection confirmed all 5 reported reviewer findings in `MainActivityWithNavigation.kt` and `TrainingApplication.java`:
1. **Service Connection & Runnable Leak**: In `onDestroy()` (line 683), `disconnectFromBANALService()` is bypassed when `isChangingConfigurations == true`, leaking `mBanalConnection` (`ServiceConnectionLeaked`). In `onPause()` (line 675), `mDisconnectFromBANALServiceRunnable` is posted with a 5-minute delay (`300000L`) to `mHandler`, but `onDestroy()` never removes callbacks, retaining the destroyed `MainActivityWithNavigation` instance for 5 minutes in the MessageQueue.
2. **Unscoped Coroutines**: Lines 822 (`checkSensorBatteryAfterWorkout`), 880 (`checkSensorBatteryOnStart`), and 923 (`startInRideBatteryMonitoring`) launch via unmanaged `CoroutineScope(Dispatchers.IO)`. They capture `this` (Activity) via `getString()` and method references, are not bound to `lifecycleScope`, and outlive the Activity upon finish/destroy.
3. **Activity-Coupled In-Ride Battery State**: Alert deduplication sets (`mInRideAlertedSensorIds`, `mInRideCriticalAlertedSensorIds`, lines 874–875) are Activity instance fields. Screen rotation clears them, causing duplicate alerts upon next telemetry. Furthermore, `stopInRideBatteryMonitoring()` runs in `onPause()` (line 672), killing the collector when the screen is off (the primary riding scenario).
4. **Main-Thread SQLite Access**: Synchronous DB calls on the main thread:
   - `mBanalConnection.onServiceConnected` (line 387): `TrackingViewsDatabaseManager.getAllFilterData()`.
   - `onResume()` $\rightarrow$ `checkUnfinishedWorkout` (line 576): `WorkoutSummariesDatabaseManager.hasUnfinishedWorkout()`.
   - Dialog clicks (lines 1071, 1084): `discardOrFinishUnfinishedWorkout()` and `hasUnfinishedWorkout()`.
   - `onCreate()` (line 453) $\rightarrow$ `checkGpsEnabledIfPermitted()` (line 1027) $\rightarrow$ `TrainingApplication.trackLocation()`: 3 synchronous `isPaired()` queries to `DevicesDatabaseManager`.
5. **Raw Dialog Window Leak**: Lines 455–464 show `GooglePlayServicesUtil.getErrorDialog()` directly as a raw `Dialog` without dismissal in `onDestroy()`, triggering `WindowLeaked` on rotation or exit.

---

## 2. Call Site & Blast Radius Audit

| Component | Target File | Nature of Change & Impact |
| :--- | :--- | :--- |
| **Activity Lifecycle** | `MainActivityWithNavigation.kt` | Always unbind `mBanalConnection` and clear `mHandler` runnables in `onDestroy`; dismiss or wrap Play Services dialog. |
| **Coroutine Scoping** | `MainActivityWithNavigation.kt` | Replace unscoped `CoroutineScope(Dispatchers.IO)` with `lifecycleScope.launch(Dispatchers.IO)`. |
| **Battery Monitoring** | `MainActivityWithNavigation.kt` / `DeviceDataRepository.kt` | Preserve in-ride deduplication state across configuration changes; keep monitoring active during tracking when paused/screen-off. |
| **Off-Thread DB** | `MainActivityWithNavigation.kt`, `TrainingApplication.java` | Dispatch DB calls (`getAllFilterData`, `hasUnfinishedWorkout`, `trackLocation`) to `Dispatchers.IO` / async workers. |
| **Verification Tests** | `activities/MainActivityLifecycleTest.kt` | Contract tests for service unbinding on rotation, runnable removal, coroutine scoping, and non-blocking DB dispatch. |

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Guaranteed `mBanalConnection` unbind and `mDisconnectFromBANALServiceRunnable` removal in `onDestroy()`.
  2. Replace unmanaged `CoroutineScope(Dispatchers.IO)` in `MainActivityWithNavigation` with `lifecycleScope` / structured scopes.
  3. Preserve in-ride battery alert deduplication across configuration changes; maintain active monitoring while tracking is running (screen-off).
  4. Offload main-thread DB reads/writes (`hasUnfinishedWorkout`, `getAllFilterData`, dialog actions, and `trackLocation`) to background dispatchers.
  5. Prevent `WindowLeaked` on Google Play Services error dialog.
* **Out-of-Scope Non-Goals**:
  1. No changes to `BANALService` hardware communications or sensor protocol engines.
  2. No changes to `TrackerService` GPS foreground tracking loop.
  3. No modification of SQLite database schemas or helper table contracts.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

* **Original Requirement ID & Target**: Amends `REQ-UI-356` (*In-Ride Low Sensor Battery Alert Snackbars & Deduplication*) and establishes `REQ-STB-019` (*MainActivity Service Binding Safety, Lifecycle Scoping & Off-Main-Thread DB Execution*).
* **Historical Origin & Commit Trace**: `ATT-3313` / `ATT-3451` (Sprint 2026-42.1) implemented in-ride battery alert snackbars; legacy service binding predates `BANALServiceRepository`.
* **Root Reason for Existing Formulation**: In-ride battery alerting was placed directly in `MainActivityWithNavigation` for easy HUD snackbar access; `isChangingConfigurations` guard attempted to optimize service rebinding across rotations but violated Android's requirement that destroying activities unbind their registered connections.
* **Preservation of Core Invariants**: 20% warning / 10% critical thresholds, session deduplication, `GetBanalServiceInterface` contract, and 100% clean-room test pass rate are strictly preserved.

---

## 5. System Invariants & Risk Assessment

* **Core Invariants**:
  1. `MainActivityWithNavigation` MUST continue to implement `BANALService.GetBanalServiceInterface` and provide `getBanalServiceComm()`.
  2. Low battery alert deduplication MUST NOT fire duplicate alerts for the same sensor within the same severity tier during a workout session.
  3. Zero database queries on the main application thread during cold start, resume, or service binding.
  4. 100% test pass rate across all existing unit tests.
* **Risk Rating**: **MEDIUM** — Touches central Activity lifecycle, service binding, coroutines, and startup permission/location initialization.
