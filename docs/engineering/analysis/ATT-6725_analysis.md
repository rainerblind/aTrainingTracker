# Stage 1 Analysis: ATT-6725 - Cache Cardio-Power baseline outside 1-Hz loop, add in-ride minute 8-25 calibration, and scope LIVE_HR_DRIFT to power ActivityTypes

**Ticket**: [ATT-6725](https://rainerblind.atlassian.net/browse/ATT-6725)  
**Sub-task**: [ATT-6810](https://rainerblind.atlassian.net/browse/ATT-6810) (`[Analysis]`)  
**Parent Epic**: [ATT-3903](https://rainerblind.atlassian.net/browse/ATT-3903) (*Dynamical Power-to-Heart-Rate System Identification*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Branch**: `feature/ATT-6725`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation
During the Sprint 2026-46.1 review of `ATT-3907` (`11197f2b`), three architectural deficiencies in the `LIVE_HR_DRIFT` feature were identified:
1. `CyclingPowerMetricsDevice.calculateMetrics()` executes `db.getCardioPowerDynamicsFitnessTrendSamples(0.50)` (full-table SQLite join) and `SettingsDataStoreJavaHelper.getTuningConfig(ctx)` (blocking DataStore read) every single second (3,600 times/hour) on the 1-Hz tracking thread.
2. The initial baseline reads only the single latest historical workout (`samples.get(samples.size() - 1)`), rather than a 30-day rolling average, and lacks in-ride model calibration over active minutes 8–25 when athlete physiology diverges or historical workouts are absent.
3. `SensorType.LIVE_HR_DRIFT` is unconditionally appended to all non-power ActivityTypes in `ActivityType.java:212`, and cockpit zone color thresholds in `TrackingViewModel.kt` diverge from the specified $\le 5.0$ bpm, $5..10$ bpm, $>10$ bpm bands.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Root Cause 1 (1-Hz I/O Overhead)**: `calculateMetrics()` in `CyclingPowerMetricsDevice.java` lines 550 and 744 queries external providers synchronously on every scheduler tick instead of caching session baseline values in `resetSession()`.
* **Root Cause 2 (Baseline Bias & Zero In-Ride Adaptation)**: Solely relying on the last workout makes the model vulnerable to outlier rides; missing in-ride calibration prevents tracking today's actual fresh physiology.
* **Root Cause 3 (Unscoped Activity Sensor & Zone Threshold Drift)**: `ActivityType.java:212` appends `LIVE_HR_DRIFT` blindly to the return array of `getSensorTypeArray()`; `TrackingViewModel.kt:785-787` uses legacy $+3/+8\text{ bpm}$ thresholds instead of $+5/+10\text{ bpm}$.
* **Independent Peer Review & Verification**:
  Verified in live codebase: `CyclingPowerMetricsDevice.java:550, 744` indeed runs DB and DataStore reads per tick; `ActivityType.java:212` appends to every sport; `TrackingViewModel.kt:785` checks `drift <= 3.0` and `drift <= 8.0`.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Cache 30-day rolling baseline `(tau, HR_0, K)` and `enableCardioPowerDynamics` in `resetSession()`; eliminate 1-Hz SQLite and DataStore I/O.
  2. Implement in-ride calibration: buffer active (Power, HR) pairs during minutes 8–25; invoke `PowerHrDynamicsCalculator.fitWindow()` to update live baseline when $R^2 \ge 0.50$.
  3. Scope `SensorType.LIVE_HR_DRIFT` in `ActivityType.java` strictly to power-capable `ActivityType.BIKE_POWER`.
  4. Align cockpit zone thresholds in `TrackingViewModel.kt` ($\le 5.0$ bpm fresh/green, $5..10$ bpm moderate/amber, $>10$ bpm high/red).
* **Out-of-Scope Non-Goals**:
  - Modifying the core 1st-order OE dynamical mathematical equations in `PowerHrDynamicsCalculator.kt`.
  - Adding new sensors or changing database schema in `WorkoutSummariesDatabaseManager`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-REV-012` (*Real-Time Live HR Drift Metric & Tracking Cockpit Integration*), `TST-REV-013`.
* **Historical Origin & Commit Trace**: Added in `ATT-3907` (commit `11197f2b`, Sprint 2026-46.1).
* **Root Reason for Existing Formulation**: Initial implementation wired the prototype provider directly to `calculateMetrics()` for quick verification; thresholds and sensor array inclusions were drafted using preliminary values.
* **Preservation of Core Invariants**: 1st-order discrete lag compensation ($x_k = \alpha x_{k-1} + (1-\alpha)P_k$, $\widehat{\text{HR}}_k = \text{HR}_0 + K x_k$), 30s rolling smoothing, and null safety on absent sensors are fully preserved.

---

## 5. Architectural Strategy & High-Level Solution
1. **`CyclingPowerMetricsDevice.java`**:
   - In `resetSession()`: compute 30-day rolling baseline `(tau, HR_0, K)` from `CardioPowerTrendSample`s; cache `mCurrentBaseline` and `mCachedCardioEnabled`.
   - In `calculateMetrics()`: use cached variables (0 DB queries); buffer active 1-Hz pairs during minutes 8–25; trigger `PowerHrDynamicsCalculator.fitWindow()` to update `mCurrentBaseline` if $R^2 \ge 0.50$.
2. **`ActivityType.java`**:
   - Move `SensorType.LIVE_HR_DRIFT` into `case BIKE_POWER:` (or append only for power-capable types).
3. **`TrackingViewModel.kt`**:
   - Update `calculateZoneColor()` thresholds to $\le 5.0$, $\le 10.0$, and $> 10.0$.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Zero SQLite queries on 1-Hz tracking thread; 100% test pass rate across `CyclingPowerMetricsDeviceLiveHrDriftTest` and `TrackingViewModelLiveDriftTest`; zero runtime crashes.
* **Risk Rating**: **MEDIUM** (Touches real-time sensor processing loop and tracking cockpit, scoped strictly to power-cycling drift metrics).
