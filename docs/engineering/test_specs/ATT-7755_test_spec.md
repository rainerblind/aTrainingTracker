# Stage 2: Requirement & Test Specification - ATT-7755: MyBTLEDevice Mid-Workout BLE Disconnect Reconnection Resilience

**Ticket**: [ATT-7755](https://rainerblind.atlassian.net/browse/ATT-7755) | **Parent Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.6` | **Branch**: `feature/ATT-7755`  
**Requirement Mapping**: `REQ-CON-036` (`REQ-CON-036.1`..`REQ-CON-036.4`) | **Test Spec ID**: `TST-CON-036` (`TST-CON-036.1`..`TST-CON-036.4`)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-14  

---

## 1. Requirement Specification (`REQ-CON-036`)

### 1.1 Attributes & Problem Statement
- **Priority**: High | **Type**: Functional | **Verification Method**: Unit
- An unexpected BLE disconnect (e.g. Polar H10 HRM, RF body-shadowing LSTO `status = 8`) mid-workout permanently abandons reconnection if no advertisement is received during a single 5s search window. `startSearching()` cancels `mReconnectRunnable`, and `mSearchTimeoutRunnable` terminates search without rescheduling retries, leaving `mBluetoothGatt = null` for the remainder of the workout.

### 1.2 Functional & Architectural Requirements
1. `MyBTLEDevice` SHALL track session connection state (`mHasConnectedInSession`) and active workout tracking state (`TrackingMode.TRACKING` / `PAUSED`).
2. For a previously connected peripheral (`!mIsShutDown`), `mSearchTimeoutRunnable` SHALL NOT abandon reconnection upon timeout without data; it SHALL reschedule `mReconnectRunnable` with exponential backoff (1s..16s) until reconnected or shut down.
3. For known/session-connected peripherals, `MyBTLEDevice` SHALL establish persistent controller-level auto-connect (`connectGatt(..., /* autoConnect = */ true, ...)`) to reconnect immediately upon receiving peripheral advertisements without continuous active scanning.
4. `MyBTLEDevice` SHALL enforce sliding-window scan rate throttling (<= 4 scans / 30s) to prevent Android `BluetoothLeScanner` unregistration errors, falling back immediately to direct/auto `connectGatt` on quota exhaustion or `onScanFailed`.
5. Initial startup discovery for offline sensors SHALL continue to terminate after `mSearchTimeoutRunnable` and bounded `mMyRemoteDeviceTries` in `DeviceManager`, preserving battery conservation.

### 1.3 Acceptance Criteria (Given-When-Then)
- *Given* an active workout tracking session with a connected BLE sensor (e.g. Polar H10), *When* an RF dropout causes LSTO disconnect and zero advertisements during the 5s search window, *Then* `MyBTLEDevice` reschedules `mReconnectRunnable` with exponential backoff and reconnects as soon as the peripheral reappears.
- *Given* a paired sensor offline at app startup, *When* search timeout expires, *Then* `notifyStopSearching(false)` is dispatched and search terminates after `mMyRemoteDeviceTries` without infinite reconnect loops.
- *Given* > 4 scan attempts within 30s, *When* scan assist is requested, *Then* scan quota throttling bypasses `startScan` and falls back directly to `connectGatt`.

### Requirement Archaeology & Chesterton's Fence Audit
- *Original Requirement ID & Target*: Refines `REQ-CON-020` & `REQ-CON-023` in `MyBTLEDevice.java` & `DeviceManager.java`.
- *Historical Origin & Commit Trace*: `ATT-4007` (`e84383ee`), `ATT-5489` (`22d8c54d`), `ATT-6245` (`99164ce7`).
- *Root Reason for Existing Formulation*: Single search timeout called `notifyStopSearching(false)` so `DeviceManager` could cleanly count down `mMyRemoteDeviceTries` for offline sensors to avoid draining battery.
- *Preservation of Core Invariants*: Startup offline discovery bounding remains 100% preserved; only session-connected / active-workout peripherals persist reconnection.

---

## 2. Test Specification (`TST-CON-036`)

### Test Cases
- `[TST-CON-036.1]` (Unit - `BLEReconnectRobustnessTest.kt`): Mid-workout disconnect simulation with multiple consecutive search timeouts; assert `mReconnectRunnable` rescheduled with exponential backoff and clean sensor re-registration upon reconnect.
- `[TST-CON-036.2]` (Unit - `BLEReconnectRobustnessTest.kt`): Offline startup sensor search timeout; assert `notifyStopSearching(false)` dispatched and zero infinite retry loops scheduled.
- `[TST-CON-036.3]` (Unit - `BLEReconnectRobustnessTest.kt`): Scan rate quota limiter; assert maximum 4 scans per 30s window and immediate fallback to `connectGatt` on throttle or `onScanFailed`.
- `[TST-CON-036.4]` (Tier 1 Regression): Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.*" --tests "com.atrainingtracker.trainingtracker.build.*"` asserting 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Component & Method | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-CON-036.1]` | Unit | `MyBTLEDevice` (reconnect backoff across timeouts) | `REQ-CON-036.1`, `REQ-CON-036.2`, `REQ-CON-036.4` | Specified |
| `[TST-CON-036.2]` | Unit | `MyBTLEDevice` & `DeviceManager` (startup bounding) | `REQ-CON-036.1` | Specified |
| `[TST-CON-036.3]` | Unit | `MyBTLEDevice` (scan quota limiter & fallback) | `REQ-CON-036.3` | Specified |
| `[TST-CON-036.4]` | Regression | `com.atrainingtracker.banalservice.devices.bluetooth_le.*` | `REQ-PRO-001` | Specified |
