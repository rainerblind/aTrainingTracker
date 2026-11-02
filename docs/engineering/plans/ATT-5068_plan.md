# Stage 3: Implementation Plan - ATT-5068: [Bug] W' values shown although no power available

**Ticket**: [[ATT-5068]](https://rainerblind.atlassian.net/browse/ATT-5068)  
**Sub-task**: [[ATT-5092]](https://rainerblind.atlassian.net/browse/ATT-5092) (`[Impl-Plan]`)  
**Parent Epic**: None  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.1`  
**Requirement Mapping**: `REQ-TRK-038` (`REQ-TRK-038.1`, `REQ-TRK-038.4`, `REQ-TRK-038.5`), `REQ-UI-399` (`REQ-UI-399.3`)  
**Test Mapping**: `TST-TRK-031` (`TST-TRK-031.1`, `TST-TRK-031.2`, `TST-TRK-031.10`, `TST-TRK-031.9`)  
**Branch**: `feature/ATT-5068`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-02  

---

## 1. Problem Description & Background
Paired power sensors retained in `MySensorManager` previously caused `CyclingPowerMetricsDevice` to seed full capacity to `mWPrimeBalSensor` on startup without waiting for numeric power telemetry. Consequently, 20.0 kJ samples were persisted at 1 Hz on power-less bike rides, and `TelemetryMetricUtils.hasWPrimeBalData()` rendered flat horizontal W'-Bilanz charts across all workout views.

---

## 2. Traceability & Requirements Mapping
| Requirement | Summary | Component / Method | Test Mapping |
| :--- | :--- | :--- | :--- |
| `REQ-TRK-038.1` | Telemetry-gated W'bal sensor emission (Rule 51 & 52) | `CyclingPowerMetricsDevice.resetSession` / `calculateMetrics` | `TST-TRK-031.1`, `TST-TRK-031.2` |
| `REQ-TRK-038.4` | Paused reconstitution gated on active effort | `CyclingPowerMetricsDevice.calculateMetrics` (suspended) | `TST-TRK-031.1` |
| `REQ-TRK-038.5` / `REQ-UI-399.3` | Aftermath chart gated on power data presence | `TelemetryMetricUtils.hasWPrimeBalData` | `TST-TRK-031.10` |
| `REQ-PRO-001` | Full clean-room test suite verification | Clean-room Gradle run | `TST-TRK-031.9` |

---

## 3. System Invariants & Preserved Behavior
1. **Rule 51 Constructor State Seeding Parity**: Internal accumulator `mCurrentWPrimeBalJoules` initializes to `wPrimeKj * 1000.0` upon device creation.
2. **Rule 52 Virtual Sensor Gating**: `mWPrimeBalSensor` publishes `null` until actual numeric power samples arrive (`mActivePowerDurationSec > 0`).
3. **Stationary/Paused W/kg Alignment**: `REQ-TRK-038.2` logic and tests remain untouched.
4. **Backward Compatibility**: Workouts previously recorded without power cleanly suppress the W'-Bilanz chart without requiring SQLite table migrations.

---

## 4. Proposed Architectural Changes
* **Virtual Sensor Driver (`CyclingPowerMetricsDevice.java`)**:
  * `resetSession()`: Seed `mCurrentWPrimeBalJoules = wPrimeKj * 1000.0` but invoke `mWPrimeBalSensor.newValue(null)`.
  * `calculateMetrics()` suspended: Step reconstitution and publish if and only if `mCurrentWPrimeBalJoules != null && mActivePowerDurationSec > 0`.
  * `calculateMetrics()` active: On valid power sample, increment `mActivePowerDurationSec`, step W'bal, and publish `mWPrimeBalSensor.newValue(...)`.
* **Telemetry Presentation Utility (`TelemetryMetricUtils.kt`)**:
  * `hasWPrimeBalData(path)`: Gate on `hasPowerData(path) && path.any { it.wPrimeBalKj != null && it.wPrimeBalKj >= 0.0 }`.
* **UI Consistency**: No UI layout changes (leverages existing conditional rendering in `MapDetailLayout`, `TrackOnMapScreen`, `WorkoutSummary`).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Virtual Sensor Emission & Reconstitution Gating
* **Files**: `app/src/main/java/com/atrainingtracker/banalservice/devices/CyclingPowerMetricsDevice.java`
* **Changes**: Emit `null` in `resetSession()`; condition suspended reconstitution on `mActivePowerDurationSec > 0`.

### Step 2: Aftermath Chart Power Gating
* **Files**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricUtils.kt`
* **Changes**: Require `hasPowerData(path)` inside `hasWPrimeBalData(path)`.

### Step 3: Unit Test Updates & Contract Hardening
* **Files**: `app/src/test/java/com/atrainingtracker/banalservice/devices/CyclingPowerMetricsDeviceTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricUtilsTest.kt`
* **Changes**: Update startup assertions to expect `null` before first sample; assert activation upon sample; add `hasWPrimeBalData` power-check test.
* **Targeted Verification**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.CyclingPowerMetricsDeviceTest" --tests "com.atrainingtracker.trainingtracker.ui.map.TelemetryMetricUtilsTest"
  ```

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted package tests, `verify_requirement_governance.py`, followed by full clean-room suite in Stage 5.
* **Rollback**: `git checkout .` on isolated branch `feature/ATT-5068`.
