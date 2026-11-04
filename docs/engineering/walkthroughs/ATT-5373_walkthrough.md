# Stage 5: Walkthrough & Verification - ATT-5373: Prevent initial DESCENT (+22.5m) and SLOPE (-59%) jump on GPS to DEM transition

**Ticket**: [ATT-5373](https://rainerblind.atlassian.net/browse/ATT-5373)  
**Sub-task**: Pending (`[Test]`)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Accurate Elevation & Slope Metrics*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.5`  
**Requirement Mapping**: `REQ-FIL-012` (`REQ-FIL-012.5`, `REQ-FIL-012.6`)  
**Test Mapping**: `TST-FIL-004` (`TST-FIL-004.4`, `TST-FIL-004.5`)  
**Branch**: `feature/ATT-5373`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-04  

---

## 1. Executive Summary & Verification Overview

Resolved the initial elevation artifact observed in Workout #455 when transitioning from GPS WGS84 ellipsoid height (492.0 m) to DEM elevation (438.0 m) during standstill:
1. **Filter Pipeline Shift & Reseed**: Implemented `shiftValues(delta)` and `reseed(value)` in `MyFilter` and `TimedMovingAverageFilter`. Exposed `shiftFilter` and `reseedFilter` through `FilterManager` and `BANALService`.
2. **Active Source Transition Tracking**: In `VerticalSpeedAndSlopeDevice`, unwrapped `ProxySensor.getBestSensor()` (Rule 68) to track `mLastAltitudeSourceDevice`. When the active altitude device changes, `cAltitudeFilter` (21s) and `cAltitudeSuperFilter` (300s) are immediately reseeded, `mLastAltitudeSuperFiltered` is updated, and `mAltitudeHistory` circular buffer is reset.
3. **Barometric Calibration Parity**: Extended `mAltitudeCorrectionReceiver` in `VerticalSpeedAndSlopeDevice` to delegate uniform `BANALService.shiftFilter()` across both 21s and 300s filters.
4. **Artifact Elimination**: Standstill descent accumulation remains strictly `0.0 m` upon DEM transition, and slope does not plunge into `-59%` upon ride start.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-FIL-012.5` | `TST-FIL-004.4` | Workout #455 trace unit test (`VerticalSpeedAndSlopeDeviceTest`) | **PASSED** | `Verified` |
| `REQ-FIL-012.6` | `TST-FIL-004.5` | Intent broadcast & filter shift unit test (`VerticalSpeedAndSlopeDeviceTest`) | **PASSED** | `Verified` |
| `REQ-FIL-012` | `TST-FIL-004` | Filter reseed & shift unit tests (`FilterResetAndPreStartTest`) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Test Scope (Rule 60)
* **Executed Scope**: Tier 1 targeted: `com.atrainingtracker.banalservice.*`, `com.atrainingtracker.trainingtracker.build.*`.
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Result**: `BUILD SUCCESSFUL (100% PASS)` across all 24 `VerticalSpeedAndSlopeDeviceTest` cases and filter test suites.
* **Governance**: `python3 tools/verify_requirement_governance.py` (0 errors, 100% PASS).
* **On-Device ADB**: Non-UI backend filter/metric pipeline change; physical UI APK deployment skipped per Rule 60.

---

## 4. Invariant & Governance Verification

1. **Zero Production Regressions**: Full `banalservice` and `build` test suites executed clean-room without failure.
2. **Living Documentation Synchronized**: `REQ-FIL-012` and `TST-FIL-004` updated to `[Verified]`.
3. **Requirement Governance**: 100% pass rate in `verify_requirement_governance.py`.
4. **Git State**: Single atomic commit prepared for `feature/ATT-5373`.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-FIL-012` (*Stabilized VAM Metric & Indoor Drift Suppression Architecture*), `REQ-SEN-025` (*Pairable Position-Based Elevation Sensor*), and `REQ-SEN-021` (*Proxy & Accumulator Sensors*). Targets `VerticalSpeedAndSlopeDevice.java`, `TimedMovingAverageFilter.java`, `FilterManager.java`, and `BANALService.java`.
* **Historical Origin & Commit Trace**: `ATT-1621` introduced 15s linear regression VAM; `ATT-1738` added accuracy and stationary gating; `ATT-5312` introduced DEM altitude sensor.
* **Root Reason for Existing Formulation**: Previously, only barometric calibration triggered filter shifts. Switching altitude sources dynamically in `ProxySensor` was unhandled, leaving old GPS ellipsoid baseline in the 300s rolling average.
* **Preservation of Core Invariants**: Linear regression slope formulas, accuracy gating ($>20$m), stationary clamping ($<150$ m/h), and thread-safe filter operations are 100% preserved.
