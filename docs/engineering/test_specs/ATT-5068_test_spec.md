# Stage 2: Requirement & Test Specification - ATT-5068: [Bug] W' values shown although no power available

**Ticket**: [[ATT-5068]](https://rainerblind.atlassian.net/browse/ATT-5068)  
**Sub-task**: [[ATT-5091]](https://rainerblind.atlassian.net/browse/ATT-5091) (`[Req & Test Spec]`)  
**Parent Epic**: None  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.1`  
**Requirement Mapping**: `REQ-TRK-038` (`REQ-TRK-038.1`, `REQ-TRK-038.4`, `REQ-TRK-038.5`), `REQ-UI-399` (`REQ-UI-399.3`)  
**Test Spec ID**: `TST-TRK-031` (`TST-TRK-031.1`, `TST-TRK-031.2`, `TST-TRK-031.10`)  
**Branch**: `feature/ATT-5068`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-02  

---

## 1. Requirement Specification (REQ-TRK-038 & REQ-UI-399.3)

### 1.1 Problem Statement & Rationale
On cycling workouts recorded without an active power meter (e.g. *Zum Bäcker #45*), a paired power sensor in `MySensorManager` previously caused `CyclingPowerMetricsDevice` to publish full 20.0 kJ capacity to `mWPrimeBalSensor` from second zero, persisting phantom samples at 1 Hz and rendering a flat horizontal chart in `MapDetailLayout`, `TrackOnMapScreen`, and `WorkoutSummary`. The system must gate live `W_PRIME_BAL` emissions on received numeric power telemetry (`mActivePowerDurationSec > 0`) and gate aftermath chart display in `TelemetryMetricUtils.hasWPrimeBalData()` on `hasPowerData(path)`.

### 1.2 Functional & Architectural Requirements
1. **Telemetry-Gated W'bal Emission (`REQ-TRK-038.1`, Rule 51 & Rule 52)**:
   - `CyclingPowerMetricsDevice.resetSession()` SHALL seed internal capacity `mCurrentWPrimeBalJoules = wPrimeKj * 1000.0` (Rule 51) but publish `mWPrimeBalSensor.newValue(null)` unconditionally, even when a paired power sensor is registered in `MySensorManager`.
   - `CyclingPowerMetricsDevice.calculateMetrics()` SHALL NOT emit non-null `mWPrimeBalSensor` values during active or paused tracking until at least one valid numeric power sample (`power >= 0.0`) has been received (`mActivePowerDurationSec > 0`).
2. **Paused Reconstitution Gating (`REQ-TRK-038.4`, Rule 52)**:
   - During `isSuspended == true`, exponential reconstitution of $W'_{\text{bal}}$ at 0W SHALL step and publish if and only if `mCurrentWPrimeBalJoules != null` AND `mActivePowerDurationSec > 0`.
3. **Aftermath Chart Power Gating (`REQ-TRK-038.5` / `REQ-UI-399.3`)**:
   - `TelemetryMetricUtils.hasWPrimeBalData(path)` SHALL require `hasPowerData(path) && path.any { it.wPrimeBalKj != null && it.wPrimeBalKj >= 0.0 }`, suppressing phantom flat-line W'-Bilanz charts across all workout views on activities lacking power telemetry.

### 1.3 Acceptance Criteria (Given-When-Then) & System Invariants
* **Criterion 1 (Live Emission Gating)**: *Given* paired power sensor with zero samples, *When* `calculateMetrics()` runs, *Then* `wPrimeBalSensor.sensorData.value` remains `null`.
* **Criterion 2 (First Sample Activation)**: *Given* $W' = 20.0\text{ kJ}$, $CP = 250\text{ W}$, *When* first power sample ($200\text{ W}$) received, *Then* `wPrimeBalSensor` emits $20.0\text{ kJ}$ and steps thereafter.
* **Criterion 3 (Aftermath Phantom Chart Suppression)**: *Given* trackpoints with `wPrimeBalKj = 20.0` but no power data, *When* `hasWPrimeBalData(path)` evaluated, *Then* returns `false`, hiding chart.
* **Invariants**: Rule 51 constructor state seeding preserved; Rule 52 virtual sensor telemetry gating enforced; W/kg pause alignment (`REQ-TRK-038.2`) untouched.

---

## 2. Test Specification (TST-TRK-031)

### Test Case 1: `testStartup_pairedPowerSensorWithoutPowerSamples_wPrimeBalSensorEmitsNull` (`[TST-TRK-031.1]`)
* **Scope**: Unit Test (`CyclingPowerMetricsDeviceTest.kt`)
* **Preconditions / Action**: Paired power sensor in `sensorManager`, `powerSensorData.value == null`. Instantiate and run `calculateMetrics()`.
* **Expected Result**: `device.wPrimeBalSensor.sensorData.value` remains `null`.

### Test Case 2: `testCalculateMetrics_firstPowerSampleReceived_activatesWPrimeBalSensor` (`[TST-TRK-031.2]`)
* **Scope**: Unit Test (`CyclingPowerMetricsDeviceTest.kt`)
* **Preconditions / Action**: Device initialized with $W' = 20.0\text{ kJ}$, zero prior samples. Inject $250\text{ W}$ sample and run `calculateMetrics()`.
* **Expected Result**: `device.wPrimeBalSensor.sensorData.value` emits non-null numeric value ($\approx 20.0\text{ kJ}$).

### Test Case 3: `testHasWPrimeBalData_requiresPowerData` (`[TST-TRK-031.10]`)
* **Scope**: Unit Test (`TelemetryMetricUtilsTest.kt`)
* **Preconditions / Action**: Evaluate `hasWPrimeBalData` with `wPrimeBalKj = 20.0` but no power; then with power = 200W.
* **Expected Result**: Returns `false` for power-less points; returns `true` when power data exists.

### Test Case 4: Clean-Room Regression Suite (`[TST-TRK-031.9]`)
* **Command**: `./gradlew testDebugUnitTest` (full pass with zero regressions).

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-TRK-031.1]` | Unit | `CyclingPowerMetricsDevice.calculateMetrics` / `resetSession` | `REQ-TRK-038.1` | Specified |
| `[TST-TRK-031.2]` | Unit | `CyclingPowerMetricsDevice.calculateMetrics` | `REQ-TRK-038.1` | Specified |
| `[TST-TRK-031.10]` | Unit | `TelemetryMetricUtils.hasWPrimeBalData` | `REQ-TRK-038.5`, `REQ-UI-399.3` | Specified |
| `[TST-TRK-031.9]` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |

---

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-TRK-038` (`REQ-TRK-038.1`, `REQ-TRK-038.4`), `REQ-UI-399` (`REQ-UI-399.3`). Targets `CyclingPowerMetricsDevice.java` and `TelemetryMetricUtils.kt`.
* **Historical Origin & Commit Trace**: `ATT-4283` (Rule 51 constructor parity), `ATT-4368` (Rule 52 sensor-presence gating, commit `9fce3cd7`), `ATT-3862` (W'bal telemetry charts).
* **Root Reason for Existing Formulation**: `ATT-4368` checked `getSensor(SensorType.POWER) != null` on device reset, assuming power presence implied active data packets. `REQ-UI-399.3` assumed `wPrimeBalKj` presence implied recorded power.
* **Preservation of Core Invariants**: Retains Rule 51 internal accumulator seeding while enforcing Rule 52 telemetry gating. Existing genuine power workouts continue displaying full charts; power-less activities cleanly suppress flat lines.
