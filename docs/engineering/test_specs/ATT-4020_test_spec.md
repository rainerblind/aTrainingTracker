# Stage 2: Requirement & Test Specification - ATT-4020: Fix MainActivity service binding leaks, unscoped coroutines and main-thread DB access

**Ticket**: [ATT-4020](https://rainerblind.atlassian.net/browse/ATT-4020)  
**Parent Epic**: None | **Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.5`  
**Requirement Mapping**: `REQ-STB-019` (*MainActivity Service Binding Safety, Lifecycle Scoping, In-Ride Battery State Retention & Off-Main-Thread DB Execution*)  
**Test Spec ID**: `TST-STB-019` | **Branch**: `feature/ATT-4020`  
**Author**: AI Agent 1 (Implementer - `subagent_flash`) | **Date**: 2026-10-21  

---

## 1. Requirement Specification (REQ-STB-019)

### 1.1 Problem Statement & Rationale
Forensic analysis revealed `MainActivityWithNavigation` leaks `ServiceConnection` and handler runnables on rotation, uses unscoped coroutines, drops in-ride battery deduplication on rotation / stops monitoring on pause, runs synchronous SQLite queries on the main thread, and leaks the Google Play Services error dialog.

### 1.2 Functional & Architectural Requirements
1. **Unconditional Service & Runnable Teardown**: `onDestroy()` SHALL call `disconnectFromBANALService()` unconditionally (removing `!isChangingConfigurations` check) and `mHandler.removeCallbacks(mDisconnectFromBANALServiceRunnable)`.
2. **Structured Coroutine Scoping**: Unmanaged `CoroutineScope(Dispatchers.IO)` SHALL be replaced with `lifecycleScope.launch(Dispatchers.IO)`.
3. **In-Ride Battery State Retention & Screen-Off Monitoring**: Deduplication state (`mInRideAlertedSensorIds`, `mInRideCriticalAlertedSensorIds`) SHALL be retained across configuration changes; in-ride battery monitoring SHALL remain active during tracking while paused/screen-off.
4. **Off-Main-Thread SQLite Execution**: `getAllFilterData()`, `hasUnfinishedWorkout()`, dialog actions, and `TrainingApplication.trackLocation()` SHALL execute on `Dispatchers.IO`.
5. **Dialog Window Leak Prevention**: Play Services error dialog SHALL be dismissed in `onDestroy()`.

### 1.3 Acceptance Criteria (Given-When-Then)
* *Given* `MainActivityWithNavigation` recreating on rotation (`isChangingConfigurations == true`), *When* `onDestroy()` executes, *Then* the service connection is unbound and the delayed disconnect runnable is removed without leaking.
* *Given* a low battery alert emitted at 15% during tracking, *When* the screen is rotated, *Then* deduplication state is preserved and duplicate alert snackbars are not shown.
* *Given* active tracking with screen off (`onPause()`), *When* a sensor drops to critical battery ($\le 10\%$), *Then* in-ride monitoring continues collecting and triggers the critical alert.
* *Given* cold start, resume, or service connection, *When* database queries execute, *Then* all queries run off the main thread on `Dispatchers.IO`.
* *Given* a visible Google Play Services error dialog, *When* the activity is destroyed or rotated, *Then* the dialog is safely dismissed without `WindowLeaked`.

### 1.4 System Invariants
* Invariants: `GetBanalServiceInterface` contract preserved; 20% warning / 10% critical thresholds preserved; zero SQLite schema migrations; 100% clean-room test pass rate.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: Amends `REQ-UI-356` (*In-Ride Low Sensor Battery Alert Snackbars & Deduplication*) and establishes `REQ-STB-019` (*MainActivity Service Binding Safety, Lifecycle Scoping & Off-Main-Thread DB Execution*).
* **Historical Origin & Commit Trace**: Sprint 2026-42.1 (`ATT-3313`, `ATT-3451`) implemented in-ride battery alert snackbars; legacy service binding predates `BANALServiceRepository`.
* **Root Reason for Existing Formulation**: In-ride battery alerting was placed directly in `MainActivityWithNavigation` for easy HUD snackbar access; `isChangingConfigurations` guard attempted to optimize service rebinding across rotations but violated Android's requirement that destroying activities unbind their registered connections.
* **Preservation of Core Invariants**: 20% warning / 10% critical thresholds, session deduplication, `GetBanalServiceInterface` contract, and 100% clean-room test pass rate are strictly preserved.

---

## 3. Test Specification (TST-STB-019)
* **TST-STB-019.1** (Unit): `MainActivityLifecycleTest.kt` asserts `disconnectFromBANALService()` and `mHandler.removeCallbacks` are called in `onDestroy()` under `isChangingConfigurations == true`.
* **TST-STB-019.2** (Contract): `MainActivityLifecycleTest.kt` verifies coroutines are scoped to `lifecycleScope` and cancelled upon destruction.
* **TST-STB-019.3** (Unit): `MainActivityLifecycleTest.kt` verifies battery alert deduplication survives recreation and monitoring continues in `onPause()` while `isTracking() == true`.
* **TST-STB-019.4** (Unit): `MainActivityLifecycleTest.kt` verifies `getAllFilterData`, `hasUnfinishedWorkout`, dialog actions, and `trackLocation` execute off the main thread.
* **TST-STB-019.5** (Unit): `MainActivityLifecycleTest.kt` verifies Play Services error dialog is safely dismissed in `onDestroy()`.
* **TST-STB-019.6** (Localization): `TranslationParityTest` verifies 100% 9-locale translation parity.
* **TST-STB-019.7** (Regression): `./gradlew testDebugUnitTest` passes 100%.

---

## 4. Traceability Matrix

| Test Case | Scope | Target / Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-STB-019.1` | Unit | `MainActivityWithNavigation.onDestroy` | `REQ-STB-019.1` | Specified |
| `TST-STB-019.2` | Contract | `MainActivityWithNavigation.lifecycleScope` | `REQ-STB-019.2` | Specified |
| `TST-STB-019.3` | Unit | In-ride battery monitoring & deduplication retention | `REQ-STB-019.3`, `REQ-UI-356` | Specified |
| `TST-STB-019.4` | Unit | Off-main-thread DB dispatch (`getAllFilterData`, `hasUnfinishedWorkout`) | `REQ-STB-019.4` | Specified |
| `TST-STB-019.5` | Unit | Google Play Services error dialog dismissal | `REQ-STB-019.5` | Specified |
| `TST-STB-019.6` | Localization | `TranslationParityTest` | `REQ-STB-019`, `REQ-UI-106` | Specified |
| `TST-STB-019.7` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
