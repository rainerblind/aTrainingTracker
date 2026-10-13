# Stage 5: Verification & Walkthrough - ATT-3310: Configure default sensor data filtering on initial install

**Ticket**: [ATT-3310](https://rainerblind.atlassian.net/browse/ATT-3310)  
**Sub-task**: [ATT-3382](https://rainerblind.atlassian.net/browse/ATT-3382) (`[Test]`)  
**Parent Epic**: [ATT-2561](https://rainerblind.atlassian.net/browse/ATT-2561) (*Sensors: BLE / ANT+ Connectivity, Calibration & Telemetry*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-UI-198` (Clause 1)  
**Test Mapping**: `TST-UI-152`  
**Branch**: `feature/ATT-3310`  
**Author**: AI Agent 1 (Verification & Test Engineer)  
**Date**: 2026-10-13  

---

## 1. Executive Summary & Verification Overview

Ticket [ATT-3310](https://rainerblind.atlassian.net/browse/ATT-3310) configures sensible default moving average smoothing (2.0-second moving average) for **Speed** (`SensorType.SPEED_mps`) and **Cadence** (`SensorType.CADENCE`) on initial application installation and when editing sensor fields.

This eliminates 1Hz GPS quantization noise and crank rotation timing flutter in cockpit readouts without requiring manual configuration, while preserving instantaneous direct reporting for Heart Rate and accumulators, and preserving existing user database records without schema changes.

---

## 2. Verification Results Matrix

| Test Suite / Procedure | Scope | Result | Details |
| :--- | :--- | :--- | :--- |
| `SensorFilterDefaultsTest` | Centralized domain presets registry | **PASSED** (100%) | Asserts Speed (2.0s MA), Cadence (2.0s MA), Power (3.0s MA), Pace (5.0s MA), VAM (15.0s MA), Slope (5.0s MA), HR (1.0s direct), and Accumulators (1.0s direct). |
| `ConfigureFilterDialogTest` | Dynamic filter configuration & preset mapping | **PASSED** (100%) | Asserts Speed resolves 2.0s MA (`FilterPreset.CUSTOM`), HR resolves instantaneous (`FilterPreset.DIRECT`), and Distance accumulator resolves instantaneous (`FilterPreset.DIRECT`). |
| `EditSensorFieldViewModelTest` | Cockpit field editor ViewModel | **PASSED** (100%) | Verified dynamic synchronization across all sensor types. |
| Clean-Room Unit Suite | `./gradlew testDebugUnitTest` | **PASSED** (100%) | 2,507 tests executed cleanly in 2m 26s with zero regressions. |

---

## 3. Requirement & Test Acceptance Criteria Verification

### Criterion 1: Default Speed & Cadence Smoothing (`REQ-UI-198`)
- **Given**: A query to `SensorFilterDefaults.getDefaultFilterConfig` for `SensorType.SPEED_mps` or `SensorType.CADENCE`.
- **When**: Configuration is resolved.
- **Then**: `FilterType.MOVING_AVERAGE_TIME` with `filterConstant = 2.0` and `unit = "sec"` is returned.
- **Verification**: Verified by `testSpeedSensor_defaultsToTwoSecondMovingAverage` and `testCadenceSensor_defaultsToTwoSecondMovingAverage`.

### Criterion 2: Heart Rate Instantaneous Protection (`REQ-UI-198`)
- **Given**: Querying default filter configuration for `SensorType.HR`.
- **When**: Configuration is resolved.
- **Then**: `FilterType.INSTANTANEOUS` with constant `1.0` is strictly returned.
- **Verification**: Verified by `testContinuousMetrics_defaultToInstantaneous`.

### Criterion 3: Existing User Database Preservation (`REQ-UI-198`)
- **Given**: An existing installed user database.
- **When**: App upgrades.
- **Then**: Zero migration scripts are run against `ROWS_TABLE`, leaving customized user views completely untouched.
- **Verification**: Verified by `TrackingViewsDatabaseManager` contract inspection (no upgrade migration added).

---

## 4. Traceability & Sign-Off

- **Requirement `REQ-UI-198`**: Advanced from `Specified` to `Verified` in [docs/requirements.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md).
- **Test Specification `TST-UI-152`**: Advanced from `Specified` to `Verified` in [docs/tests.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md).

---

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**: `REQ-UI-167` (*Intuitive Smoothing Presets, Simplified Filter Configuration & Smart Athletic Defaults*), targeting `EditSensorFieldViewModel.kt` and `TrackingViewsDatabaseManager.java`.
2. **Historical Origin & Commit Trace**: Commit `2b3d819e` (Ticket `ATT-1276`, Sprint `2026-40.4`); amended in `ATT-1624` (Sprint `2026-40.10`); amended in `ATT-3310` (Sprint `2026-42.1`).
3. **Root Reason for Existing Formulation**: `ATT-1276` added a hardcoded 3s moving average default exclusively for `SensorType.POWER` to fix volatile pedal stroke dead-spots, and `ATT-1624` added presets for Pace, VAM, and Slope. Speed and cadence were left raw, leading to display chatter under 1Hz GPS quantization noise and crank rotation timing flutter. Configuring a 2-second moving average default stabilizes readouts without introducing perceptible lag.
4. **Preservation of Core Invariants**: Full customization freedom in `ConfigureFilterDialog`, immutability of existing saved views, BANALService filtering mathematics, and `HR`/accumulator `INSTANTANEOUS` protection remain 100% intact.

