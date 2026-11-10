# Stage 1 Analysis: ATT-6381 - BTLERunSpeedDevice: Thread Leak, Stale Distance Accumulation & State Hygiene (Teilpaket B)

**Ticket**: [ATT-6381](https://rainerblind.atlassian.net/browse/ATT-6381)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-6033](https://rainerblind.atlassian.net/browse/ATT-6033) (*Code Architecture, Deduplication & Clean Code Refactoring*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.1`  
**Branch**: `feature/ATT-6381`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-10  

---

## 1. Problem Statement & Motivation
Under approved complexity split (REQ-PRO-029), ATT-6381 addresses **Teilpaket B** (1 SP) for BLE Running Speed and Cadence (RSC) devices in BANALService:
1. **ScheduledExecutorService Thread Leak in `BTLERunSpeedDevice.shutDown()`**: `mScheduler` (`Executors.newScheduledThreadPool(1)`) is started at instantiation. When `shutDown()` is called, only `mDistanceCalculatorHandle.cancel(true)` is executed; `mScheduler.shutdownNow()` is never invoked, permanently leaking a worker thread per device instance.
2. **Stale Speed & Unbounded Distance Inflation**: When BLE RSC devices omit hardware distance (`mDistancePresent == false`), distance is calculated every 1 second via `mDistance += mSpeed`. When BLE packets stop (runner pauses or device disconnects), `mSpeed` is never reset. The scheduler continuously accumulates stale speed into distance indefinitely.
3. **Data Race / Visibility Defects**: `mDistance`, `mSpeed`, and `mDistancePresent` are accessed concurrently from the BLE main-thread GATT callback and the scheduler background thread without memory visibility or synchronization guarantees.
4. **Forensic Field Audit**: The backlog hypothesized `protected String mAddress` field shadowing and a dead `mTotalDistance` state. Forensic audit confirms `mAddress` is NOT shadowed (cleanly inherited from `MyBTLEDevice`), while the distance defect is actual runaway accumulation of `mDistance`.

---

## 2. Root Cause Analysis & Independent Peer Review
* **Root Cause**:
  - `BTLERunSpeedDevice.java:169-175`: Missing `mScheduler.shutdownNow()` in `shutDown()`.
  - `BTLERunSpeedDevice.java:50-61`: Unconditional periodic addition `mDistance += mSpeed` without checking packet freshness / timeout threshold ($> 5.0\text{ s}$).
  - Missing `volatile` / synchronization on shared cross-thread fields (`mSpeed`, `mDistance`, `mDistancePresent`).
* **Independent Peer Review & Verification (4-Augen-Prinzip)**:
  - Audited `MyBTLEDevice.java:73`: `mAddress` is declared once in `MyBTLEDevice`; no duplicate field exists in `BTLERunSpeedDevice`.
  - Audited `distanceCalculator` vs `measurementCharacteristicUpdate`: Validated that without a timestamp check or timeout reset, disconnecting the BLE footpod inflates workout distance indefinitely.

---

## 3. Scope Grounding & Non-Goals (ATT-1250)
* **In-Scope Goals (Teilpaket B - 1 SP)**:
  - Invoke `mScheduler.shutdownNow()` in `BTLERunSpeedDevice.shutDown()`.
  - Introduce 5-second packet freshness guard/timeout resetting `mSpeed = 0.0` when no RSC packet is received.
  - Ensure memory visibility and thread safety for cross-thread state.
  - Author dedicated unit tests in `BTLERunSpeedDeviceTest.kt`.
* **Out-of-Scope Non-Goals**:
  - `ATT-6046` (Teilpaket A: ANT+ device fixes — completed in commit `80991160`).
  - `ATT-6382` (Teilpaket C: `MaxValueFilter` & `ExponentialSmoothingFilter` pre-start seeding).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Refined Requirements**: Formulates `REQ-CON-026` (*BLE Run Speed Device Lifecycle & Telemetry Hygiene*) in living shard `docs/requirements/req_tracking_cockpit.md` and `TST-CON-026` in `docs/tests/tst_tracking_cockpit.md` (positioned immediately after `REQ-CON-025` / `TST-CON-025`).
* **Historical Origin**: Added during BLE RSC support implementation.
* **Root Reason for Existing Formulation**: Assumption that device disconnection immediately triggers garbage collection; omission of BLE packet timeout handling.
* **Preservation of Core Invariants**: GATT packet parsing for speed, cadence, stride length, and hardware distance remains 100% compliant with Bluetooth SIG RSC profile.

---

## 5. System Invariants & Risk Assessment
* **Core Invariants**: Zero thread leaks upon `shutDown()`; zero runaway distance accumulation when stationary/disconnected; 100% test pass rate.
* **Risk Rating**: **LOW** (Encapsulated entirely within `BTLERunSpeedDevice.java`; no schema or service-level breaking changes).
