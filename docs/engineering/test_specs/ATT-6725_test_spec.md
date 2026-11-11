# Stage 2: Requirement & Test Specification - ATT-6725: Cache Cardio-Power baseline outside 1-Hz loop, add in-ride minute 8-25 calibration, and scope LIVE_HR_DRIFT to power ActivityTypes

**Ticket**: [ATT-6725](https://rainerblind.atlassian.net/browse/ATT-6725)  
**Sub-task**: [ATT-6811](https://rainerblind.atlassian.net/browse/ATT-6811) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-3903](https://rainerblind.atlassian.net/browse/ATT-3903) (*Dynamical Power-to-Heart-Rate System Identification*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-REV-012` (*Real-Time Live HR Drift Metric & Tracking Cockpit Integration*)  
**Test Spec ID**: `TST-REV-013` (*Real-Time Live HR Drift & Cockpit Telemetry Verification Suite*)  
**Branch**: `feature/ATT-6725`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-11  

---

## 1. Requirement Specification (REQ-REV-012)

### 1.1 Problem Statement & Rationale
`CyclingPowerMetricsDevice.calculateMetrics()` executes synchronous full-table SQLite joins and blocking DataStore reads every second. Initial seeding lacks 30-day rolling averaging and in-ride adaptation (minutes 8–25). Furthermore, `LIVE_HR_DRIFT` is leaked into non-power activities and cockpit color thresholds diverge from specified decoupling zones.

### 1.2 Functional & Architectural Requirements
1. **1-Hz Zero-Query Loop (`REQ-REV-012.2b`)**:
   - `calculateMetrics()` SHALL execute zero SQLite queries and zero blocking DataStore reads.
   - Initial 30-day rolling baseline $(\tau, \text{HR}_0, K)$ and `enableCardioPowerDynamics` SHALL be cached during `resetSession()`.
2. **In-Ride Model Calibration (`REQ-REV-012.2d`)**:
   - The device SHALL buffer active 1-Hz paired $(P_k, \text{HR}_k)$ readings during minutes 8–25.
   - Upon accumulating $\ge 600\text{ s}$ of qualifying active samples, invoke `PowerHrDynamicsCalculator.fitWindow()`. If $R^2 \ge 0.50$, update session baseline parameters.
3. **Sport Scoping (`REQ-REV-012.1c`)**:
   - `SensorType.LIVE_HR_DRIFT` SHALL only be present for power-capable `ActivityType.BIKE_POWER`.
4. **Cockpit Decoupling Color Thresholds (`REQ-REV-012.3b`)**:
   - Drift color mapping in `TrackingViewModel.kt` SHALL enforce: $\le +5.0\text{ bpm}$ (Zone 2/green), $+5.0 < \text{drift} \le +10.0\text{ bpm}$ (Zone 3/amber), and $> +10.0\text{ bpm}$ (Zone 4/red).

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Zero-Query Loop)**:
  * *Given* active cycling power and heart rate tracking,
  * *When* `calculateMetrics()` executes at 1 Hz,
  * *Then* zero SQLite queries and zero DataStore reads are executed.
* **Criterion 2 (In-Ride Calibration)**:
  * *Given* active tracking reaching minute 25 with dynamic power variation,
  * *When* `fitWindow()` computes $R^2 \ge 0.50$,
  * *Then* the live session baseline adapts to today's identified physiology.
* **Criterion 3 (Scoping & Thresholds)**:
  * *Given* `ActivityType.RUN_SPEED_AND_CADENCE`, `LIVE_HR_DRIFT` is absent; given `BIKE_POWER`, drift is present and colors switch at $+5\text{ bpm}$ and $+10\text{ bpm}$.

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: `REQ-REV-012` (*Real-Time Live HR Drift Metric & Tracking Cockpit Integration*), targeting `CyclingPowerMetricsDevice.java`, `ActivityType.java`, and `TrackingViewModel.kt`.
* `Historical Origin & Commit Trace`: Added in `ATT-3907` (commit `11197f2b`, Sprint 2026-46.1).
* `Root Reason for Existing Formulation`: Initial prototype queried providers synchronously on every tick for quick verification, appended the sensor to all activity types, and used preliminary $+3/+8\text{ bpm}$ zone cutoffs before field validation.
* `Preservation of Core Invariants`: 1st-order discrete lag compensation ($x_k = \alpha x_{k-1} + (1-\alpha)P_k$, $\widehat{\text{HR}}_k = \text{HR}_0 + K x_k$), 30s rolling smoothing, and null safety on absent sensors are fully preserved while eliminating 1-Hz thread I/O blocking.

---

## 2. Test Specification (TST-REV-013)

### Test Case 1: 1-Hz Loop Zero-Query & 30-Day Baseline Cache (`TST-REV-013.2`)
* **Scope**: Unit Test (`CyclingPowerMetricsDeviceLiveHrDriftTest.kt`)
* **Assertion**: Verify provider calls occur strictly within `resetSession()` and zero times during repeated `calculateMetrics()` invocations.

### Test Case 2: In-Ride Minute 8–25 Model Calibration (`TST-REV-013.3`)
* **Scope**: Unit Test (`CyclingPowerMetricsDeviceLiveHrDriftTest.kt`)
* **Assertion**: Feed active (Power, HR) series spanning minutes 8–25; assert baseline parameters update dynamically when $R^2 \ge 0.50$.

### Test Case 3: ActivityType Power Scoping (`TST-REV-013.4`)
* **Scope**: Contract Test (`ActivityTypeTest.kt` / `SensorCategoryAndSelectorContractTest.kt`)
* **Assertion**: Assert `LIVE_HR_DRIFT` exists in `ActivityType.BIKE_POWER` and is absent from non-power activity types.

### Test Case 4: Cockpit Zone Threshold Color Mapping (`TST-REV-013.6`)
* **Scope**: ViewModel Test (`TrackingViewModelLiveDriftTest.kt`)
* **Assertion**: Test boundary values $-3.0$, $+5.0$ (Zone 2), $+5.1$, $+10.0$ (Zone 3), and $+10.1$ (Zone 4).

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-REV-013.2]` | Unit | `CyclingPowerMetricsDevice.calculateMetrics()` | `REQ-REV-012.2b` | Specified |
| `[TST-REV-013.3]` | Unit | `CyclingPowerMetricsDevice.calculateMetrics()` | `REQ-REV-012.2d` | Specified |
| `[TST-REV-013.4]` | Contract | `ActivityType.getSensorTypeArray()` | `REQ-REV-012.1c` | Specified |
| `[TST-REV-013.6]` | Unit | `TrackingViewModel.calculateZoneColor()` | `REQ-REV-012.3b` | Specified |
