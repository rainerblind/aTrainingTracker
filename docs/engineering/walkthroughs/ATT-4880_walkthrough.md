# Stage 5: Walkthrough & Verification - ATT-4880: Indirect Tire Pressure Monitoring (iTPMS)

**Ticket**: [ATT-4880](https://rainerblind.atlassian.net/browse/ATT-4880)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Requirement Mapping**: `REQ-TRK-050` (`REQ-TRK-050.1`..`REQ-TRK-050.5`)  
**Test Mapping**: `TST-TRK-050` (`TST-TRK-050.1`..`TST-TRK-050.5`)  
**Branch**: `feature/ATT-4880`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-01  

---

## 1. Executive Summary & Verification Overview

ATT-4880 implements an Indirect Tire Pressure Monitoring System (iTPMS) that continuously fuses GNSS 3D displacement with ANT+/BLE wheel speed revolutions to detect tire deflation without dedicated valve hardware. `IndirectTpmsEstimator` enforces strict straight-line gating ($|\Delta\text{bearing}/\Delta t| < 3.0^\circ/\text{s}$), GNSS accuracy ($\le 6.0\text{ m}$), speed ($\ge 4.0\text{ m/s}$), and 3D slope correction ($\sqrt{\Delta s_{2D}^2 + \Delta h^2}$) to reject cornering distortions and multipath jitter. The estimator locks ride-start baseline circumference $C_{\text{start}}$ at $2.0\text{ km}$, detecting multi-day low pressure reminders against pumped baseline ($\ge 0.6\%$ drop) and in-ride slow punctures using a sliding-window rolling CUSUM change-point detector ($\ge 1.0\%$ drop). Database schema is upgraded to `DB_VERSION = 8` (`Devices.db`), baseline reset action is wired in device settings, and `TpmsAlertBanner` renders semantic amber warning and red critical containers with 9-language localization parity.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Component | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-TRK-050.1` | `TST-TRK-050.1` | `IndirectTpmsEstimatorTest` | **PASSED** | `Verified` |
| `REQ-TRK-050.2` | `TST-TRK-050.2` | `IndirectTpmsEstimatorTest` | **PASSED** | `Verified` |
| `REQ-TRK-050.3` | `TST-TRK-050.3` | `IndirectTpmsEstimatorTest` | **PASSED** | `Verified` |
| `REQ-TRK-050.4` | `TST-TRK-050.4` | `DevicesDatabaseManagerTest` & `EditDeviceViewModelTest` | **PASSED** | `Verified` |
| `REQ-TRK-050.5` | `TST-TRK-050.5` | `TranslationParityTest` & `ThemeColorCentralizationAuditTest` | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Verification Suite (Rule 7 & Rule 60)
* **Explicit Test Scope**:
  - `com.atrainingtracker.banalservice.tpms.*`
  - `com.atrainingtracker.banalservice.database.*`
  - `com.atrainingtracker.banalservice.ui.devices.editdevice.*`
  - `com.atrainingtracker.trainingtracker.migration.*`
  - `com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest`
  - `com.atrainingtracker.trainingtracker.localization.TranslationParityTest`
  - `com.atrainingtracker.trainingtracker.build.*`
* **Execution**: `./gradlew testDebugUnitTest --tests "..."`
* **Result**: **BUILD SUCCESSFUL in 43s** (100% clean-room test pass).
* **ADB & Field Verification**: `adb devices` reported no attached hardware devices. Live outdoor bicycle + ANT+/BLE wheel speed sensor + GPS field verification is documented for Deferred Field Evaluation per Ceremony 1, while deterministic unit tests verify the estimator and CUSUM detector in-sprint.
* **Requirement Governance**: `python3 tools/verify_requirement_governance.py` -> 0 errors, net-new requirement `REQ-TRK-050` verified cleanly.

---

## 4. Invariant & Governance Verification

1. **Sensor Calibration Invariant**: Existing `CALIBRATION_FACTOR` remains uncorrupted and acts as the true wheel circumference source; baseline reset updates `baselineCircumference` from current calibration.
2. **Straight-Line Immunity**: Cornering, lean-angle distortion, and switchbacks are completely excluded by straight-line rate gating ($< 3.0^\circ/\text{s}$).
3. **Database Schema Parity**: `DevicesDbHelper.DB_VERSION = 8` synchronized in `DatabaseSchemaRegistry.kt` pursuant to Rule 62.
4. **9-Language Parity**: All alert titles, messages, and action strings verified across all 9 application locales.
5. **No Premature FixVersion**: Target Release `V4.9.41` maintained; `FixVersion` reserved for human Ceremony 2 sign-off.
