# Stage 3: Implementation Plan - ATT-3907: Real-Time Live HR Drift Sensor Metric

**Ticket**: [ATT-3907](https://rainerblind.atlassian.net/browse/ATT-3907)  
**Sub-task**: Pending (`[Impl-Plan]`)  
**Parent Epic**: [ATT-3903](https://rainerblind.atlassian.net/browse/ATT-3903) (*Dynamical Power-to-Heart-Rate System Identification*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-3907`  
**Requirement Mapping**: `REQ-REV-012` (*Real-Time Live HR Drift Metric & Tracking Cockpit Integration*)  
**Test Mapping**: `TST-REV-013`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Description & Background

Athletes cycling outdoors need real-time feedback on cardiac decoupling ($\text{HR}_{\text{actual}} - \text{HR}_{\text{pred}}$) to detect dehydration, heat stress, and autonomic fatigue. Static ratios fail on variable terrain due to cardiorespiratory response latency ($\tau \approx 25\text{--}45\text{ s}$). `ATT-3907` integrates 1 Hz lag-compensated `LIVE_HR_DRIFT` into `CyclingPowerMetricsDevice.java` and the tracking cockpit.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-REV-012` (Live HR Drift Metric, 1st-order state propagation, sensor gating, cockpit integration)
* **Test Mapping**: `TST-REV-013` (`TST-REV-013.1` to `TST-REV-013.5` across unit, governance, contract, and UI tests)

---

## 3. System Invariants & Preserved Behavior

1. **Rule 51 Parity**: Constructor and `resetSession()` seed identical baseline state; `mLiveHrDriftSensor.newValue(null)` on reset.
2. **Rule 52 Gating**: Strict sensor-presence gating; emit `null` unless BOTH `SensorType.POWER` and `SensorType.HR` are active and `enableCardioPowerDynamics == true`.
3. **Rule 40 Growing Window**: 30s residual smoothing window averages over available samples ($1 \le k < 30$) to prevent 30s blank `"--"` delay.
4. **Pause Flush**: Long pauses ($> 30\text{ s}$) flush residual smoothing FIFO and reset filter state.
5. **Zero Schema Migration**: Pure in-memory virtual sensor; no database DDL modifications.

---

## 4. Proposed Architectural Changes

### Component 1: Sensor & Formatter (`banalservice/sensor/`)
* Create `SignedIntegerFormatter.java` in `banalservice.sensor.formater` (`+5`, `0`, `-2`, null/NaN `"--"`).
* Register `SensorType.LIVE_HR_DRIFT` (`HEART_RATE_AND_ENERGY`, `INTEGER`, `SignedIntegerFormatter`, `ic_heart_rate`).

### Component 2: Telemetry Device (`CyclingPowerMetricsDevice.java`)
* Add `mLiveHrDriftSensor`, state variable `mDriftStateX`, and 30-sample ArrayDeque `mDriftResidualWindow30s`.
* Inject `CardioPowerDynamicsEnabledProvider` and `CardioPowerBaselineProvider` with testable defaults.
* In 1 Hz `calculateMetrics()`: propagate state $x_k = \alpha x_{k-1} + (1-\alpha) P_k$, compute $\text{residual}_k = \text{HR}_k - (\text{HR}_0 + K x_k)$, smooth over growing window, and publish signed integer.

### Component 3: Cockpit UI & Semantic Zones (`TrackingViewModel.kt`, `SensorFieldState.kt`)
* Gate visibility via `enableCardioPowerDynamics` in `SensorFieldState.kt` and `EditSensorFieldViewModel.kt`.
* In `TrackingViewModel.kt`: map drift $\le +3\text{ bpm}$ to `TTColor.Zone2`, $+4\dots+8\text{ bpm}$ to `TTColor.Zone3`, and $\ge +9\text{ bpm}$ to `TTColor.Zone4`.

### UI Consistency (Rule 23)
* **Reference Component**: `SensorFieldView.kt` / `SensorFieldState.kt` (standard cockpit telemetry tile).
* **Reused Components**: `SensorFieldView`, `LocalMetricFormatter`, `TTColor` tokens (`Zone2`, `Zone3`, `Zone4`).
* **Theme Tokens**: `RoundedCornerShape(8.dp)`, semantic theme colors, zero raw hex literals.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Create `SignedIntegerFormatter` & Register `SensorType.LIVE_HR_DRIFT`
* Files: `SignedIntegerFormatter.java`, `SensorType.java`
* Implement signed formatting with `+` prefix for positive values and add enum constant with icon/category.

### Step 2: Implement 1 Hz Dynamical Drift in `CyclingPowerMetricsDevice.java`
* Files: `CyclingPowerMetricsDevice.java`
* Add `mLiveHrDriftSensor`, baseline resolution, state propagation, 30s growing-window residual, Rule 51/52 gating, and pause flush.

### Step 3: Wire Cockpit Tile Visibility & Color Accents
* Files: `SensorFieldState.kt`, `EditSensorFieldViewModel.kt`, `TrackingViewModel.kt`
* Implement `isSensorTypeVisibleForTuning`, `shouldIncludeSensorType`, and drift semantic zone color mapping.

### Step 4: Unit & Governance Test Verification
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.*" --tests "com.atrainingtracker.banalservice.sensor.*" --tests "com.atrainingtracker.trainingtracker.ui.tracking.*" --tests "com.atrainingtracker.trainingtracker.build.*"`

---

## 6. Verification & Rollback Plan

* **Verification**: Execute targeted test suite in Stage 4, confirm 100% pass rate, and verify 9-locale parity via `TranslationParityTest`.
* **Rollback**: Branch isolation on `feature/ATT-3907` ensures clean discard via `git checkout sprint/2026-46.1` if needed.
