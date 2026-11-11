# Stage 3: Implementation Plan - ATT-6725: Cache Cardio-Power baseline outside 1-Hz loop, add in-ride minute 8-25 calibration, and scope LIVE_HR_DRIFT to power ActivityTypes

**Ticket**: [ATT-6725](https://rainerblind.atlassian.net/browse/ATT-6725)  
**Sub-task**: [ATT-6813](https://rainerblind.atlassian.net/browse/ATT-6813) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-3903](https://rainerblind.atlassian.net/browse/ATT-3903) (*Dynamical Power-to-Heart-Rate System Identification*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-REV-012` (*Real-Time Live HR Drift Metric & Tracking Cockpit Integration*)  
**Test Mapping**: `TST-REV-013` (*Real-Time Live HR Drift & Cockpit Telemetry Verification Suite*)  
**Branch**: `feature/ATT-6725`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-11  

---

## 1. Problem Description & Background
`CyclingPowerMetricsDevice.calculateMetrics()` executes synchronous full-table SQLite joins and blocking DataStore reads at 1 Hz. The initial baseline reads only the single latest historical workout without 30-day averaging or in-ride model adaptation over active minutes 8–25. Furthermore, `SensorType.LIVE_HR_DRIFT` is present across non-power activity types and cockpit color thresholds diverge from specified decoupling zones.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-REV-012` (*Real-Time Live HR Drift Metric & Tracking Cockpit Integration*)
* **Test Mapping**: `TST-REV-013` (`CyclingPowerMetricsDeviceLiveHrDriftTest.kt`, `TrackingViewModelLiveDriftTest.kt`, `SensorCategoryAndSelectorContractTest.kt`)

---

## 3. System Invariants & Preserved Behavior
1. **Zero 1-Hz I/O Blocking**: Zero SQLite queries and zero blocking DataStore reads on the 1-Hz tracking loop.
2. **Dynamical Model Equivalence**: 1st-order Output-Error discrete state dynamics ($x_k = \alpha x_{k-1} + (1-\alpha)P_k$, $\widehat{\text{HR}}_k = \text{HR}_0 + K x_k$) and 30s FIFO smoothing are preserved.
3. **Null Safety & Sensor Presence**: Absence of `POWER` or `HR` sensors continues to emit `null` gracefully (Rule 52).
4. **Human Decision Gate**: Parent completion strictly reserved for `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `CyclingPowerMetricsDevice.java`
* Cache `mCachedCardioEnabled` and `mCurrentBaseline` in `resetSession()`.
* In `mCardioBaselineProvider` default resolution, average qualified rides ($R^2 \ge 0.50$) within the last 30 days.
* In `calculateMetrics()`: use cached state; buffer active 1-Hz $(P, \text{HR})$ pairs during active minutes 8–25 ($480\text{--}1500\text{ s}$).
* Trigger `PowerHrDynamicsCalculator.fitWindow()` once $\ge 600\text{ s}$ of qualifying active samples exist; update `mCurrentBaseline` if $R^2 \ge 0.50$.

### Component 2: `ActivityType.java`
* Scope `SensorType.LIVE_HR_DRIFT` to `case BIKE_POWER:` (or when `activityType == BIKE_POWER`), removing it from generic/running/walking activity types.

### Component 3: `TrackingViewModel.kt`
* Align `calculateZoneColor()` thresholds for `SensorType.LIVE_HR_DRIFT`:
  - $\text{drift} \le 5.0\text{ bpm} \rightarrow \text{Zone 2 (green / fresh)}$
  - $5.0 < \text{drift} \le 10.0\text{ bpm} \rightarrow \text{Zone 3 (amber / moderate)}$
  - $\text{drift} > 10.0\text{ bpm} \rightarrow \text{Zone 4 (red / elevated)}$

### UI Consistency (Rule 23)
* Uses existing theme zone colors (`zoneColors.getOrElse(1)`, `zoneColors.getOrElse(2)`, `zoneColors.getOrElse(3)`). No new UI styling introduced.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Cache 30-Day Baseline & Remove 1-Hz Synchronous I/O
* Files: `app/src/main/java/com/atrainingtracker/banalservice/devices/CyclingPowerMetricsDevice.java`
* Changes: Compute 30-day rolling baseline; cache `mCurrentBaseline` and `mCachedCardioEnabled` in `resetSession()`; remove provider calls from `calculateMetrics()`.

### Step 2: In-Ride Calibration Buffer & Live Model Adaptation
* Files: `app/src/main/java/com/atrainingtracker/banalservice/devices/CyclingPowerMetricsDevice.java`
* Changes: Buffer active pairs during minutes 8–25; fit OE model via `PowerHrDynamicsCalculator.fitWindow()`; update `mCurrentBaseline` on $R^2 \ge 0.50$.

### Step 3: Scope LIVE_HR_DRIFT to Power-Capable ActivityTypes
* Files: `app/src/main/java/com/atrainingtracker/banalservice/ActivityType.java`
* Changes: Move `SensorType.LIVE_HR_DRIFT` into `case BIKE_POWER:`.

### Step 4: Align Decoupling Zone Thresholds
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/TrackingViewModel.kt`
* Changes: Update `drift <= 5.0` and `drift <= 10.0` in `calculateZoneColor()`.

### Step 5: Unit Tests & Verification
* Files: `CyclingPowerMetricsDeviceLiveHrDriftTest.kt`, `TrackingViewModelLiveDriftTest.kt`, `SensorCategoryAndSelectorContractTest.kt`
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.CyclingPowerMetricsDeviceLiveHrDriftTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.TrackingViewModelLiveDriftTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield.SensorCategoryAndSelectorContractTest"`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit and contract tests during Stage 4/5; `verify_requirement_governance.py`.
* **Rollback**: Branch discard via atomic squash merge.
