# Stage 2: Requirement & Test Specification - ATT-6265: Barometer Altitude Pocket-Compression Spike Rejection & DB Sync

**Ticket**: [ATT-6265](https://rainerblind.atlassian.net/browse/ATT-6265) | **Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6265`  
**Requirement Mapping**: `REQ-TRK-057` (`REQ-TRK-057.1`..`REQ-TRK-057.3`) | **Test Spec ID**: `TST-TRK-057` (`TST-TRK-057.1`..`TST-TRK-057.5`)  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Requirement Specification (`REQ-TRK-057`)

### 1.1 Functional Scope & Technical Invariants
* **Causal Non-Linear Pocket-Compression Spike Rejection (`REQ-TRK-057.1`)**: In `AltitudeFromPressureDevice.java`, raw barometric altitude derived from `SensorManager.getAltitude()` SHALL be filtered through a causal non-linear pipeline (`BarometricAltitudeFilter`) before publishing to `mAltitudeSensor`:
  1. *Physical Vertical Slew-Rate Limiter*: Single-step rate-of-climb SHALL be clamped to $|\Delta h| \le v_{\max} \cdot \Delta t$ ($v_{\max} = 1.2\text{ m/s}$).
  2. *Sliding Median Window*: A 7-sample ring buffer SHALL reject isolated 1–2s transient compression pulses ($\pm 20\text{--}96\text{ m}$).
  3. *Exponential Moving Average (EMA)*: A low-pass stage ($\alpha = 0.18$) SHALL smooth residual quantization noise.
  Genuine climbs up to $0.83\text{ m/s}$ (3,000 m/h) MUST pass without attenuation ($< 0.5\text{ m}$ steady-state lag).
* **Calibration Baseline Stabilization (`REQ-TRK-057.2`)**: `AltitudeFromPressureDevice.setAltitudeCorrection()` and `calibrate()` SHALL compute `mAltitudeCorrection = referenceAltitude - mFilteredRawAltitude` strictly relative to the outlier-filtered baseline rather than a single raw pressure sample, preventing pocket-compression spikes at workout start from permanently corrupting the activity calibration offset.
* **Sourced-Column Retroactive Altitude Drift Synchronization (`REQ-TRK-057.3`)**: In `WorkoutSamplesDatabaseManager.applyAltitudeDriftCorrection()`, when shifting `SensorType.ALTITUDE.name()`, the database manager SHALL dynamically discover and apply the identical uniform (Case A) or piecewise-linear (Case B) shift to all existing source-attributed altitude columns matching `^ALTITUDE($| \(.*\)$)` (e.g. `"ALTITUDE (Höhe aus Luftdruck)"`) within the same atomic SQLite transaction.
* **Localization Impact**: N/A (pure algorithmic and database backend fix; no user-facing UI or string resources modified).

### 1.2 Given-When-Then Acceptance Criteria
* **AC-1 (Pocket Compression Pulse Rejection)**: *Given* a 1-Hz barometric altitude stream with an isolated 1-second $+50\text{ m}$ pocket-compression pulse, *When* processed by `AltitudeFromPressureDevice`, *Then* the single-step output change is clamped $\le 1.2\text{ m/s}$, the spike is rejected by the sliding median, and total phantom elevation gain remains $\le 1.0\text{ m}$.
* **AC-2 (High-Gradient Mountain Climb Preservation)**: *Given* a continuous 300-second mountain ascent at $0.80\text{ m/s}$ (2,880 m/h, total $+240.0\text{ m}$ climb), *When* processed, *Then* the final elevation matches ground truth within $\pm 0.5\text{ m}$ with zero gradient attenuation.
* **AC-3 (Calibration Baseline Spike Immunity)**: *Given* a barometric sensor experiencing a $+40\text{ m}$ pocket-compression spike, *When* `calibrate(500.0)` is invoked, *Then* `mAltitudeCorrection` anchors to the filtered baseline, differing from true ground truth by $< 1.5\text{ m}$ rather than $40\text{ m}$.
* **AC-4 (Sourced-Column Database Synchronization)**: *Given* a workout sample table containing both `ALTITUDE` and `"ALTITUDE (Höhe aus Luftdruck)"`, *When* `applyAltitudeDriftCorrection()` executes with `deltaOffset = +20.0 m`, *Then* both `ALTITUDE` and `"ALTITUDE (Höhe aus Luftdruck)"` are shifted by $+20.0\text{ m}$ across all target rows.

---

## 2. Test Specification & Traceability Matrix (`TST-TRK-057`)

| Req ID | Test Case | Target Component | Verification Scope |
| :--- | :--- | :--- | :--- |
| `REQ-TRK-057.1` | `TST-TRK-057.1` | `BarometricAltitudeFilterTest` | Pocket-compression pulse rejection: transient $\pm 50\text{ m}$ spikes clamped $\le 1.2\text{ m/s}$ and rejected by median filter. |
| `REQ-TRK-057.1` | `TST-TRK-057.2` | `BarometricAltitudeFilterTest` | High-gradient climb preservation: continuous $0.80\text{ m/s}$ ascent tracks within $0.5\text{ m}$ with zero slope attenuation. |
| `REQ-TRK-057.2` | `TST-TRK-057.3` | `AltitudeFromPressureDeviceTest` | Calibration baseline spike immunity: calibration during a $+40\text{ m}$ pulse anchors to filtered baseline, rejecting transient offset. |
| `REQ-TRK-057.3` | `TST-TRK-057.4` | `WorkoutSamplesDatabaseManagerTest` | Sourced-column synchronization: `applyAltitudeDriftCorrection` updates both `ALTITUDE` and `"ALTITUDE (Höhe aus Luftdruck)"` symmetrically for Case A and Case B. |
| `REQ-TRK-057` | `TST-TRK-057.5` | Targeted Test Suite | Targeted test execution: `com.atrainingtracker.banalservice.devices.*` and `com.atrainingtracker.trainingtracker.database.*`. |
