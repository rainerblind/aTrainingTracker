# Stage 5: Walkthrough & Verification - ATT-6725: Cache Cardio-Power baseline outside 1-Hz loop, add in-ride minute 8-25 calibration, and scope LIVE_HR_DRIFT to power ActivityTypes

**Ticket**: [ATT-6725](https://rainerblind.atlassian.net/browse/ATT-6725)  
**Parent Epic**: [ATT-3903](https://rainerblind.atlassian.net/browse/ATT-3903) (*Dynamical Power-to-Heart-Rate System Identification*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-REV-012` (*Real-Time Live HR Drift Metric & Tracking Cockpit Integration*)  
**Test Mapping**: `TST-REV-013` (*Real-Time Live HR Drift & Cockpit Telemetry Verification Suite*)  
**Branch**: `feature/ATT-6725`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-11  

---

## 1. Executive Summary & Verification Overview
Eliminated 1-Hz SQLite query (`db.getCardioPowerDynamicsFitnessTrendSamples(0.50)`) and blocking DataStore read (`SettingsDataStoreJavaHelper.getTuningConfig(ctx)`) from `CyclingPowerMetricsDevice.calculateMetrics()`. Cached athlete 30-day rolling baseline and enablement flag inside `resetSession()`. Added active telemetry buffering during minutes 8–25 ($480\text{--}1500\text{ s}$) and in-ride model calibration updating the live session baseline via `InRideCalibrator` / `PowerHrDynamicsCalculator.fitWindow()` when $R^2 \ge 0.50$. Scoped `SensorType.LIVE_HR_DRIFT` in `ActivityType.java` strictly to power-capable `ActivityType.BIKE_POWER`. Aligned cockpit zone color thresholds in `TrackingViewModel.kt` ($\le 5.0\text{ bpm}$ Zone 2/green, $5..10\text{ bpm}$ Zone 3/amber, $> 10\text{ bpm}$ Zone 4/red).

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-REV-012.2b` | `TST-REV-013.2` | Automated Unit Test (`testZeroQueryLoop_baselineAndEnablementCachedAtSessionReset`) | **PASSED** | `Verified` |
| `REQ-REV-012.2b` | `TST-REV-013.2` | Automated Unit Test (`testCardioBaselineProvider_30DayRollingAverageResolution`) | **PASSED** | `Verified` |
| `REQ-REV-012.2d` | `TST-REV-013.3` | Automated Unit Test (`testInRideMinute8To25Calibration_updatesBaselineWhenR2Qualifies`) | **PASSED** | `Verified` |
| `REQ-REV-012.2d` | `TST-REV-013.3` | Automated Unit Test (`testInRideMinute8To25Calibration_retainsBaselineWhenR2BelowThreshold`) | **PASSED** | `Verified` |
| `REQ-REV-012.2d` | `TST-REV-013.3` | Automated Unit Test (`testDefaultInRideCalibrator_fitsSyntheticStepSignal`) | **PASSED** | `Verified` |
| `REQ-REV-012.1c` | `TST-REV-013.4` | Automated Unit Test (`testGetSensorTypeArray_liveHrDriftScopedStrictlyToBikePower`) | **PASSED** | `Verified` |
| `REQ-REV-012.3b` | `TST-REV-013.6` | Automated Unit Test (`TrackingViewModelLiveDriftTest.kt`) | **PASSED** | `Verified` |
| `REQ-PRO-041.5` | `TST-PRO-041.5` | Governance Audit (`python3 tools/verify_requirement_governance.py`) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Unit Test Suite (Tier 1 Targeted Verification)
```text
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 1m 4s
33 actionable tasks: 2 executed, 31 up-to-date
17 tests completed, 0 failed (100% PASS)
```

### Living Governance Audit (`verify_requirement_governance.py`)
```text
PASS: Living documentation master tables match all 9 domain shards with 100% parity.
PASS: Zero unmounted Composable islands detected in git diff.
PASS: Repository encapsulation verified (zero leaky DatabaseManager/Cursor members).
PASS: Zero architecture or system specification modifications detected.
Modified/Altered existing requirement(s) detected: REQ-REV-012.
PASS: Requirement Archaeology & Chesterton's Fence Audit successfully verified with all 4 mandatory fields.
```

---

## 4. Hardware / Physical Verification (Pixel 10)
Non-visual backend telemetric filtering and tracking ViewModel logic; physical UI layout unchanged. Zone color mapping aligns with standard theme Zone 2, 3, and 4 colors. Android emulator execution retired per human directive (`REQ-TST-004`).

---

## 5. Invariant & Governance Verification
1. **Zero 1-Hz I/O Blocking**: Verified via unit test that `calculateMetrics()` triggers 0 queries to SQLite or DataStore.
2. **Preservation of 1st-Order Lag Dynamics**: Discrete-time state $x_k = \alpha x_{k-1} + (1-\alpha)P_k$ and 30-sample rolling FIFO smoothing are preserved.
3. **Null Safety**: Absence of `POWER` or `HR` sensors continues to suppress drift emission to `null`.
