# Stage 3 Implementation Plan: ATT-6381 - BTLERunSpeedDevice: Thread Leak, Stale Distance Inflation & State Hygiene (Teilpaket B)

**Ticket**: [ATT-6381](https://rainerblind.atlassian.net/browse/ATT-6381)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-6033](https://rainerblind.atlassian.net/browse/ATT-6033) (*Code Architecture, Deduplication & Clean Code Refactoring*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.1`  
**Requirement Mapping**: `REQ-CON-026` (`026.1`–`026.4`)  
**Test Spec Mapping**: `TST-CON-026` (`026.1`–`026.5`)  
**Branch**: `feature/ATT-6381`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-10  

---

## 1. Technical Architecture & SWE.2 Design
* **Decomposition & Boundaries**:
  - `BTLERunSpeedDevice.java`:
    1. **Thread Pool Lifecycle**: In `shutDown()`, invoke `mScheduler.shutdownNow()` in addition to `mDistanceCalculatorHandle.cancel(true)` to prevent thread leaks (`REQ-CON-026.1`).
    2. **Stale Speed Gating & Timestamp Tracking**: Add `volatile long mLastMeasurementTimestamp` updated on valid RSC speed measurements. In `distanceCalculator`, gate distance accumulation on `now - mLastMeasurementTimestamp <= 5000L` when `!mDistancePresent` (`REQ-CON-026.2`).
    3. **Disconnect Reset**: In `unregisterSensors()`, reset `mSpeed = 0.0` and `mLastMeasurementTimestamp = 0L` (`REQ-CON-026.2`).
    4. **Memory Visibility**: Mark `mSpeed`, `mDistance`, `mDistancePresent`, and `mLastMeasurementTimestamp` as `volatile` (`REQ-CON-026.3`).
    5. **Test Hooks**: Expose `@VisibleForTesting` accessors for scheduler, timestamp, speed, and distance calculator runnable.
  - `BTLERunSpeedDeviceTest.kt`: Author comprehensive unit tests covering scheduler shutdown, 5-second freshness gating, sensor unregistration reset, and reflection audit confirming no duplicate `mAddress` / `mTotalDistance` fields (`REQ-CON-026.4`).
* **Invariants**: Bluetooth SIG RSC profile compliance preserved; zero UI / database schema changes; 100% full-suite test pass rate.

---

## 2. Atomic Implementation Steps

| Step | Component | Target File | Action & Invariants | Traceability |
| :--- | :--- | :--- | :--- | :--- |
| **1** | BLE RSC Device | `BTLERunSpeedDevice.java` | Mark shared fields `volatile`. Add `mLastMeasurementTimestamp`. In `measurementCharacteristicUpdate`, record timestamp on speed packet. | `REQ-CON-026.2-3`<br>`TST-CON-026.2` |
| **2** | BLE RSC Device | `BTLERunSpeedDevice.java` | In `distanceCalculator`, gate accumulation on packet freshness ($\le 5000\text{ ms}$). In `unregisterSensors()`, reset `mSpeed = 0.0` and timestamp. | `REQ-CON-026.2`<br>`TST-CON-026.2-3` |
| **3** | BLE RSC Device | `BTLERunSpeedDevice.java` | In `shutDown()`, invoke `mScheduler.shutdownNow()`. Add `@VisibleForTesting` test hooks. | `REQ-CON-026.1`<br>`TST-CON-026.1` |
| **4** | Unit Tests | `BTLERunSpeedDeviceTest.kt` | Author unit test suite verifying thread pool shutdown, 5s timeout gating, sensor unregistration reset, and reflection field audit. | `REQ-CON-026.1-4`<br>`TST-CON-026.1-4` |
| **5** | Verification | Test Suite | Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.*"` and requirement governance. | `REQ-PRO-001`<br>`TST-CON-026.5` |

---

## 3. System Invariants & Rollback Safety
* **Code Lock (Rule 4)**: No production source code modified until Gate 3 is marked `Erledigt` and `check-gate` exits 0.
* **Scope Boundary**: Strictly limited to Teilpaket B; Teilpaket A (`ATT-6046`) is merged; Teilpaket C (`ATT-6382`) remains untouched.
* **Risk Rating**: **LOW** (Targeted fixes to device lifecycle methods, volatility, and timeout guards; zero breaking changes).
