# Stage 2: Requirement & Test Specification - ATT-6682: SpeedAndLocationDevice: Require entry hysteresis and ignore sub-second duplicate callbacks before stationary pinning

**Ticket**: [ATT-6682](https://rainerblind.atlassian.net/browse/ATT-6682)  
**Sub-task**: Pending (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.2`  
**Requirement Mapping**: `REQ-TRK-074` (*Duplicate Location Callback Filtering, Asymmetric Deadband Separation & Stationary Pinning Entry Hysteresis in SpeedAndLocationDevice*)  
**Test Spec ID**: `TST-TRK-074`  
**Branch**: `feature/ATT-6682`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Requirement Specification (REQ-TRK-074)

### 1.1 Problem Statement & Rationale
During athletic tracking (particularly via `SpeedAndLocationDevice_GoogleFused`), sub-second duplicate/burst callbacks ($dt < 250\text{ ms}$ or identical coordinates $dt < 900\text{ ms}$) and low-speed movements ($v \le 15\text{--}21\text{ km/h}$ where $1\text{ s}$ displacement $\le \text{escapeRadius}$) triggered single-sample stationary pinning, zeroing velocity and stalling distance accumulation. The system SHALL ignore duplicate callbacks, evaluate entry against `deadbandRadius`, and require 3-tick entry hysteresis before pinning while moving.

### 1.2 Functional & Architectural Requirements
1. `SpeedAndLocationDevice.onNewLocation()` SHALL drop callbacks when $mPrevLocation \neq null$ and $(\Delta t_{\text{ms}} < 250\text{ ms} \lor (\text{rawDelta} == 0.0\text{ m} \land \Delta t_{\text{ms}} < 900\text{ ms}))$ without zeroing speed or mutating state.
2. Stationary entry candidate gating SHALL check $\text{rawDelta} \le \text{deadbandRadius}$ (not `escapeRadius`).
3. While moving (`!mIsStationaryPinned`), transitioning to pinned state SHALL require $\ge 3$ consecutive candidate stationary ticks (`STATIONARY_PINNING_HYSTERESIS_TICKS = 3`). Transient candidate ticks ($< 3$) SHALL fall back to geometric speed calculation without pinning.
4. When moving resume occurs ($\text{rawDelta} > \text{deadbandRadius}$ or $\text{speed} > \text{SPEED\_ZERO\_THRESHOLD}$), `mStationaryHysteresisCount` SHALL reset to 0.
5. Initial fix ($mPrevLocation == null$) with zero speed SHALL pin immediately to preserve start-line stabilization.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Duplicate Callback Guard)**:
  * *Given* `SpeedAndLocationDevice` moving at $8.0\text{ m/s}$,
  * *When* a duplicate fix arrives $100\text{ ms}$ later with $\text{rawDelta} == 0.0\text{ m}$ and $\text{speed} == 0.0\text{ m/s}$,
  * *Then* the callback is ignored, remaining unpinned with speed preserved at $8.0\text{ m/s}$.
* **Criterion 2 (Low-Speed Climbing Resilience)**:
  * *Given* an athlete climbing at $3.0\text{ m/s}$ ($\le \text{escapeRadius} = 3.75\text{ m}$),
  * *When* a single spurious Doppler zero fix arrives,
  * *Then* the device does not pin, `mStationaryHysteresisCount` reaches 1, and speed does not drop to 0.0.
* **Criterion 3 (3-Tick Entry Hysteresis at Stop Light)**:
  * *Given* an athlete halting at a red light,
  * *When* 3 consecutive $1\text{ Hz}$ updates arrive with $\text{rawDelta} \le \text{deadbandRadius}$ and $\text{speed} == 0.0\text{ m/s}$,
  * *Then* on the 3rd update the device pins to stationary anchor, clamps speed to $0.0\text{ m/s}$, and locks bearing.

### 1.4 System Invariants
* Startup start-line anchor pinning ($mPrevLocation == null$) remains immediate with zero delay.
* Zero regressions in existing stationary jitter suppression or bearing lock (`REQ-TRK-048`).
* Thread safety across sensor dispatchers preserved.

---

## 2. Test Specification (TST-TRK-074)

### Test Case 1: `testDuplicateLocationCallback_ignoredWithoutSpeedZeroing` (`TST-TRK-074.1`)
* **Scope**: Unit Test (`SpeedAndLocationDeviceStationaryJitterTest.kt`)
* **Action**: Inject moving fix (8.0 m/s), then duplicate fix 100 ms later (dt < 250, rawDelta == 0.0, speed = 0.0).
* **Expected Result**: Callback ignored; `isStationaryPinned` remains false; speed sensor remains 8.0 m/s.

### Test Case 2: `testLowSpeedClimb_singleDopplerZeroDoesNotPin` (`TST-TRK-074.2`)
* **Scope**: Unit Test (`SpeedAndLocationDeviceStationaryJitterTest.kt`)
* **Action**: Inject climbing fix (3.0 m/s), then 1 fix with displacement 3.0 m <= escapeRadius and speed = 0.0.
* **Expected Result**: Device remains unpinned; speed falls back to geometric velocity.

### Test Case 3: `testStationaryEntryHysteresis_requiresThreeConsecutiveTicks` (`TST-TRK-074.3`)
* **Scope**: Unit Test (`SpeedAndLocationDeviceStationaryJitterTest.kt`)
* **Action**: Moving fix (8.0 m/s), followed by 3 consecutive stationary fixes (dt = 1000 ms, rawDelta <= deadband, speed = 0.0).
* **Expected Result**: Unpinned on ticks 1 & 2; transitions to pinned on tick 3 with speed 0.0 m/s.

### Test Case 4: `testStationaryHysteresisStreak_resetsOnMovement` (`TST-TRK-074.4`)
* **Scope**: Unit Test (`SpeedAndLocationDeviceStationaryJitterTest.kt`)
* **Action**: Moving fix, 2 stationary fixes, followed by movement fix (rawDelta > deadband, speed = 6.0 m/s).
* **Expected Result**: Streak resets to 0; device never pins.

### Test Case 5: `testInitialFix_pinsImmediatelyWithoutHysteresis` (`TST-TRK-074.5`)
* **Scope**: Unit Test (`SpeedAndLocationDeviceStationaryJitterTest.kt`)
* **Action**: First location fix (mPrevLocation == null) with speed = 0.0 m/s.
* **Expected Result**: Pinned immediately to stationary anchor on tick 1.

### Test Case 6: Targeted Package Regression Suite (`TST-TRK-074.6`)
* **Command**: `python3 tools/parallel_sprint_lock.py -- ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.*"`
* **Expected Result**: 100% pass rate across all device unit tests.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-074.1` | Unit | `SpeedAndLocationDevice.onNewLocation` | `REQ-TRK-074.1` | Specified |
| `TST-TRK-074.2` | Unit | `SpeedAndLocationDevice.onNewLocation` | `REQ-TRK-074.2` | Specified |
| `TST-TRK-074.3` | Unit | `SpeedAndLocationDevice.onNewLocation` | `REQ-TRK-074.3` | Specified |
| `TST-TRK-074.4` | Unit | `SpeedAndLocationDevice.onNewLocation` | `REQ-TRK-074.3` | Specified |
| `TST-TRK-074.5` | Unit | `SpeedAndLocationDevice.onNewLocation` | `REQ-TRK-074.4` | Specified |
| `TST-TRK-074.6` | Regression | `banalservice.devices.*` | `REQ-PRO-001` | Specified |
