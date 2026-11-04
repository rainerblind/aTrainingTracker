# Stage 1 Analysis: ATT-5373 - Prevent Initial DESCENT and SLOPE Jump on Altitude Source Switch

**Ticket**: [ATT-5373](https://rainerblind.atlassian.net/browse/ATT-5373)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.5`  
**Branch**: `feature/ATT-5373`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-04  

---

## 1. Problem Statement & Motivation
During workout aftermath inspection (Workout #455, "Bäcker und Bank", 2026-11-04), `ALTITUDE` telemetry was clean (433.1–448.0 m MSL, 0.0 m start/end delta at 438.0 m), but 1-Hz samples in `WorkoutSamples.db` revealed initialization artifacts in `VerticalSpeedAndSlopeDevice`:
1. **Standstill Descent Jump**: At second 3 (`DISTANCE_m = 0.0 m`), `DESCENT` jumped by `+22.54 m` in 1s (corrupting total descent to 28.3 m vs. actual ~9.5 m) and `VERTICAL_SPEED` spiked to `-3.0 m/s`.
2. **Initial Slope Anomaly**: Between seconds 11–16 (first 20 m of motion), `SLOPE` registered `-59.0 %` to `-18.1 %` until the 15-second ring buffer (`mAltitudeHistory`) cleared.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Forensic Root Cause**:
  - `MySensorManager.java#L66-L73` assigns priority: `ALTITUDE_FROM_PRESSURE` (0) > `ALTITUDE_FROM_POSITION` (1) > `SPEED_AND_LOCATION_GPS` (2).
  - During GPS lock warmup ($t = 0..2$ s), `ProxySensor(ALTITUDE)` routes raw GPS WGS84 ellipsoid height (492.0 m) into `cAltitudeFilter` (21 s) and `cAltitudeSuperFilter` (300 s) in `VerticalSpeedAndSlopeDevice.java#L255-L316`.
  - At $t = 3$ s, `AltitudeFromPositionDevice.java#L214-L219` resolves DEM (438.0 m MSL) and registers sensors. `ProxySensor` switches source from GPS to DEM (-54 m geoid undulation jump).
  - `TimedMovingAverageFilter.java#L77-L111` averages old 492 m and new 438 m samples. `VerticalSpeedAndSlopeDevice.calculateMetrics()` computes `deltaAltitude_mps = cAltitudeSuperFilter.getValue() - mLastAltitudeSuperFiltered` ($-18$ to $-27$ m), incrementing `mDescentSensor` by `+22.54 m` while stationary.
  - `cAltitudeFilter` feeds descending values into `mAltitudeHistory` (15 s window); once speed exceeds `minSpeed` (second 11), `calculateLinearRegressionSlope` yields negative slope $\approx -2.0$ m/s, producing `slopePercentage = (-2.0 / speed) * 100 = -59%`.
  - **Latent Calibration Flaw**: `mAltitudeCorrectionReceiver` (`VerticalSpeedAndSlopeDevice.java#L82-L100`) shifts `mLastAltitudeSuperFiltered` and `mAltitudeHistory` on `ALTITUDE_CORRECTION_INTENT`, but NEVER shifts `cAltitudeFilter` or `cAltitudeSuperFilter` in `FilterManager`, creating identical divergence on barometric calibration.
* **Independent Peer Review & Verification (ATT-3534, Rules 69 & 70)**:
  - *Movement Gating Audit*: Gating `ASCENT`/`DESCENT` strictly on `speed > minSpeed` is flawed: (a) it breaks genuine vertical gains on ski lifts/cable cars/elevators (`calculateVam` explicit contract `rawVam >= 150 m/h`), and (b) the 300 s `cAltitudeSuperFilter` buffer would simply delay and leak the false -54 m descent once movement begins. Re-seeding / shifting filters directly at source switch is the sole robust fix.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  - Detect active `ALTITUDE` source device switches in `VerticalSpeedAndSlopeDevice` (via unwrapped `ProxySensor.getSourceSensor().getDevice()`).
  - Provide filter re-seed / shift capability in `TimedMovingAverageFilter`, `FilterManager`, and `BANALService`.
  - Re-seed/synchronize `cAltitudeFilter`, `cAltitudeSuperFilter`, `mLastAltitudeSuperFiltered`, and `mAltitudeHistory` on altitude source transitions.
  - Synchronize `cAltitudeFilter` and `cAltitudeSuperFilter` on `ALTITUDE_CORRECTION_INTENT` broadcasts.
* **Out-of-Scope Non-Goals**:
  - No alteration of `ALTITUDE_PRIORITY` hierarchy in `MySensorManager`.
  - No changes to Open-Meteo DEM fetching or `ElevationCache.db` logic.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement IDs & Targets**: `REQ-FIL-012` (`VerticalSpeedAndSlopeDevice.java`, `calculateVam`), `REQ-SEN-025` (`AltitudeFromPositionDevice.java`), `REQ-SEN-021` (`ProxySensor.java`, `MySensorManager.java`).
* **Historical Origin & Commit Trace**: `ATT-1621` / `ATT-1738` (`REQ-FIL-012`, VAM stabilization), `ATT-4845` (`31553134`, initial `mAltitudeCorrectionReceiver`), `ATT-4859` / `ATT-5199` (`REQ-SEN-025`, DEM device).
* **Root Reason for Existing Formulation**: `ATT-4845` added local variable shifts for barometric calibration without filter access; `ATT-5199` added DEM altitude without source transition notification.
* **Preservation of Core Invariants**: Genuine elevator/ski-lift VAM ($\ge 150$ m/h) at low speed is preserved; mathematical linear regression slope invariance under uniform vertical translation is maintained; existing barometric calibration flow remains non-destructive.

---

## 5. Architectural Strategy & High-Level Solution
1. **Filter Shifting & Re-seeding (`TimedMovingAverageFilter.java`, `FilterManager.java`, `BANALService.java`)**:
   - Add `synchronized void shiftValues(double delta)` and `synchronized void reseed(Number value)` to `TimedMovingAverageFilter`.
   - Expose `shiftFilter(FilterData, double)` and `reseedFilter(FilterData, Number)` in `FilterManager` and `BANALService`.
2. **Altitude Source Transition Detection (`VerticalSpeedAndSlopeDevice.java`)**:
   - Track `mLastAltitudeSourceDevice` by unwrapping `ProxySensor.getSourceSensor().getDevice()`.
   - Upon source change (or initial resolution), re-seed/shift `cAltitudeFilter`, `cAltitudeSuperFilter`, `mLastAltitudeSuperFiltered`, and populate `mAltitudeHistory` uniformly with the new authoritative elevation.
3. **Barometric Calibration Synchronization**:
   - In `mAltitudeCorrectionReceiver`, shift `cAltitudeFilter` and `cAltitudeSuperFilter` alongside `mLastAltitudeSuperFiltered` and `mAltitudeHistory`.
4. **Side-Effect & Cross-Caller Impact Audit (Rule 68)**:
   - `ProxySensor` / `MySensorManager`: Preserved without lock contention.
   - `TrackerService`: Telemetry recording unaffected; eliminates false initial DESCENT spikes in `WorkoutSamples.db`.
   - Unit Tests: Add comprehensive tests in `VerticalSpeedAndSlopeDeviceTest.kt` for source switching and filter shifting.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. `calculateVam` stationary deadbands, low-speed wander suppression, and outlier clamping $[-3000, 3000]$ m/h remain intact.
  2. Stationary ski-lift/elevator detection ($\ge 150$ m/h) is never suppressed.
  3. Single-threaded / monitor synchronization contracts in filters remain thread-safe.
* **Risk Rating**: **LOW-MEDIUM**. Purely internal sensor filtering synchronization in `banalservice`; no database schema or UI layout changes.
