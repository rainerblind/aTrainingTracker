# Stage 1 Analysis: ATT-6046 - BANALService (ANT+): PCC-Handle-Leak in ANTBikeSpeedAndCadenceDevice & Pedal-Smoothness-Bug in ANTBikePowerDevice (Teilpaket A)

**Ticket**: [ATT-6046](https://rainerblind.atlassian.net/browse/ATT-6046)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-6033](https://rainerblind.atlassian.net/browse/ATT-6033) (*Code Architecture, Deduplication & Clean Code Refactoring*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.1`  
**Branch**: `feature/ATT-6046`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-10  

---

## 1. Problem Statement & Motivation
Under the approved complexity split (REQ-PRO-029), this ticket addresses **Teilpaket A** (2 SP) in the BANALService ANT+ device driver layer:
1. **PCC Handle Leak in `ANTBikeSpeedAndCadenceDevice.java:99-105`**: In combined speed & cadence devices, `releaseAccess()` guards `super.releaseAccess()` inside `if (pccReleaseHandle2 != null)`. If cadence search fails, is cancelled, or speed disconnects before cadence connects, `super.releaseAccess()` is bypassed entirely. The primary speed handle (`pccReleaseHandle` in `MyANTDevice`) leaks, causing subsequent reconnects to fail with `DEVICE_ALREADY_IN_USE` (`REQ-CON-020.4`).
2. **Pedal Smoothness Sensor Routing Bug in `ANTBikePowerDevice.java:360`**: In `onNewPedalSmoothness(...)`, when `separatePedalSmoothnessSupport == false`, `createCombinedPedalSmoothnessSensors()` initializes `mPedalSmoothnessSensor`, leaving `mPedalSmoothnessLeftSensor` null. Line 360 checks `if (mPedalSmoothnessLeftSensor != null)` before dispatching to `mPedalSmoothnessSensor.newValue(...)`, completely dropping combined pedal smoothness telemetry (or risking NPE if switched from separate mode).
3. **Field Shadowing in `ANTBikePowerDevice.java:75`**: `final DevicesDatabaseManager mDevicesDatabaseManager;` re-declares and shadows the identical field inherited from `MyDevice.java:55`.

---

## 2. Root Cause Analysis & Independent Peer Review
* **Root Cause**:
  - `ANTBikeSpeedAndCadenceDevice.java`: Defective guard structure skips `super.releaseAccess()` when `pccReleaseHandle2 == null`; missing `try-catch-finally` leaves `pccReleaseHandle2` un-nulled.
  - `ANTBikePowerDevice.java`: Copy-paste error from the separate pedal smoothness branch left `mPedalSmoothnessLeftSensor != null` on the combined branch instead of checking `mPedalSmoothnessSensor != null`.
* **Independent Peer Review & Verification (4-Augen-Prinzip)**:
  - Audited `MyANTDevice.java:157-161`: In the `DEAD` state, polymorphic `releaseAccess()` is invoked. Calling `super.releaseAccess()` unconditionally guarantees primary handle cleanup regardless of cadence state.
  - Audited `ANTBikePowerDevice.java`: Confirmed `mPedalSmoothnessSensor` is the combined sensor (`SensorType.PEDAL_SMOOTHNESS`). Updating line 360 to `if (mPedalSmoothnessSensor != null)` restores combined telemetry.
  - Confirmed `MyDevice.java:84`: `mDevicesDatabaseManager` is already initialized in `MyDevice` constructor; removing line 75 in `ANTBikePowerDevice` is 100% safe.

---

## 3. Scope Grounding & Non-Goals (ATT-1250)
* **In-Scope Goals (Teilpaket A - 2 SP)**:
  - Fix `ANTBikeSpeedAndCadenceDevice.releaseAccess()` to close `pccReleaseHandle2` in try/finally, null the reference, and unconditionally execute `super.releaseAccess()`.
  - Fix `ANTBikePowerDevice.onNewPedalSmoothness()` to guard on `if (mPedalSmoothnessSensor != null)`.
  - Remove shadowed `mDevicesDatabaseManager` in `ANTBikePowerDevice.java:75`.
  - Author unit tests in `ANTBikeSpeedAndCadenceDeviceTest.kt` and expand `ANTBikePowerDeviceTest.kt`.
* **Out-of-Scope Non-Goals**:
  - `ATT-6381` (Teilpaket B: `BTLERunSpeedDevice` distance & address shadowing).
  - `ATT-6382` (Teilpaket C: `MaxValueFilter` & `ExponentialSmoothingFilter` pre-start seeding).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Refined Requirements**: Extends `REQ-CON-025` (*ANT+ Device Driver Resource Cleanup & Telemetry Routing Safety*) in living shard `docs/requirements/req_tracking_cockpit.md` and `TST-CON-025` in `docs/tests/tst_tracking_cockpit.md`.
* **Historical Origin**: Initial ANT+ device implementations in BANALService.
* **Root Reason for Existing Formulation**: Assumption of simultaneous dual-channel lifecycle; copy-paste across separate/combined branches.
* **Preservation of Core Invariants**: `DEVICE_ALREADY_IN_USE` prevention (`REQ-CON-020.4`), power balance calculation, and sensor database flags remain intact.

---

## 5. System Invariants & Risk Assessment
* **Core Invariants**: Unconditional release of all acquired PCC handles on disconnect; correct sensor telemetry routing; zero regression to BLE or database subsystems.
* **Risk Rating**: **LOW** (Targeted fixes to device lifecycle methods and boolean guards; zero schema or cross-service changes).
