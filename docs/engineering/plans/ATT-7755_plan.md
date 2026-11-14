# Stage 3: Implementation Plan - ATT-7755: MyBTLEDevice Mid-Workout BLE Disconnect Reconnection Resilience

**Ticket**: [ATT-7755](https://rainerblind.atlassian.net/browse/ATT-7755)  
**Sub-task**: None (`[Impl-Plan]`)  
**Parent Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910) (*Equipment, Sensors, Sport Types & Athlete Profile*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.6`  
**Requirement Mapping**: `REQ-CON-036` (`REQ-CON-036.1`..`REQ-CON-036.4`)  
**Test Mapping**: `TST-CON-036` (`TST-CON-036.1`..`TST-CON-036.4`)  
**Branch**: `feature/ATT-7755`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-14  

---

## 1. Problem Description & Background
Mid-workout RF dropouts (e.g. Polar H10 HRM, outdoor run #2175, body-shadowing LSTO `HCI status = 8`) permanently strand `MyBTLEDevice` when advertisements are missed during a single 5s search window. `startSearching()` cancels `mReconnectRunnable`, and `mSearchTimeoutRunnable` tears down GATT and search without rescheduling retries, leaving `mBluetoothGatt = null` for the rest of the workout (38+ min loss).

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-CON-036` (*Resilient Mid-Workout BLE Continuous Reconnection, Hardware-Offloaded AutoConnect & Scan Quota Protection Architecture*)
* **Test Mapping**: `TST-CON-036` (`BLEReconnectRobustnessTest.kt` unit suite and bluetooth_le package tests)

---

## 3. System Invariants & Preserved Behavior
1. **Startup Battery Conservation Invariant**: Initial startup discovery for never-seen offline sensors continues to terminate cleanly after `mSearchTimeoutRunnable` and `mMyRemoteDeviceTries`, preventing infinite background search loops.
2. **Thread Safety & GATT State Lock**: All state transitions (`mState`), runnable postings, and GATT handles remain strictly synchronized under `mGattLock` and `mHandler` looper.
3. **Parent Human Decision Gate**: Parent ticket completion remains strictly reserved for human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `MyBTLEDevice.java`
- Track session connection state: `private volatile boolean mHasConnectedInSession = false;` (set `true` on `STATE_CONNECTED` / `CONNECTED_WITH_SERVICE`, reset on `shutDown()`).
- Add state check `isMidSessionOrWorkoutActive()` combining `mHasConnectedInSession` and `TrainingApplication.getTrackingMode()`.
- Update `mSearchTimeoutRunnable`: if `!isReceivingData()` and `isMidSessionOrWorkoutActive() && !mIsShutDown`, stop scan assistance, reschedule `mReconnectRunnable` with exponential backoff (1s..16s), and maintain/establish controller-level `autoConnect = true`.
- Implement sliding-window scan quota limiter (max 4 scans / 30s) and immediate `connectGatt` fallback on quota saturation or `onScanFailed`.

### Component 2: `BLEReconnectRobustnessTest.kt`
- Add unit tests verifying: (1) mid-workout search timeout persists reconnect backoff and recovers upon peripheral return; (2) offline startup search terminates cleanly without infinite retries; (3) scan quota throttle and scan failure cleanly fall back to direct/auto `connectGatt`.

### UI Consistency (Rule 23)
No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: `MyBTLEDevice.java` State Machine & Reconnect Persistence (`REQ-CON-036.1`, `REQ-CON-036.2`)
* Add `mHasConnectedInSession`, `isMidSessionOrWorkoutActive()`.
* Update `mSearchTimeoutRunnable` to branch on session/workout state, rescheduling `mReconnectRunnable` on timeout instead of abandoning.

### Step 2: Hardware AutoConnect & Scan Quota Limiter (`REQ-CON-036.3`, `REQ-CON-036.4`)
* Add sliding-window scan quota limiter (max 4 scans / 30s).
* Route mid-session reconnects to `connectGatt(..., /* autoConnect = */ true, ...)`.
* Add fallback to direct/auto `connectGatt` in `onScanFailed` and on scan quota saturation.

### Step 3: Unit Verification in `BLEReconnectRobustnessTest.kt` (`TST-CON-036.1`..`TST-CON-036.3`)
* Add tests for mid-workout disconnect recovery across search timeouts, startup termination parity, and scan quota fallback.
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.*" --tests "com.atrainingtracker.trainingtracker.build.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted package tests followed by full living doc verification `python3 tools/verify_requirement_governance.py`.
* **Rollback**: Atomic revert of `feature/ATT-7755` restores previous `MyBTLEDevice.java` implementation without residual effects.
