# Stage 1 Analysis: ATT-4845 - Manual Altitude Calibration with Known Location Persistence and Retroactive Linear Drift Correction

**Ticket**: [ATT-4845](https://rainerblind.atlassian.net/browse/ATT-4845)  
**Sub-task**: Pending (`[Analysis]`)  
**Parent Epic**: ATT-166 (Filtering to improve sensor values)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Branch**: `feature/ATT-4845`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-01  

---

## 1. Problem Statement & Motivation
During outdoor workouts, barometric altimeters either begin uncalibrated or accumulate multi-hour atmospheric pressure drift. When athletes pass a landmark with a known reference altitude, they need to enter that altitude from the tracking cockpit, permanently store it as a Known Location (`KnownLocations.db`), immediately calibrate `AltitudeFromPressureDevice`, and retroactively correct recorded elevation history without step discontinuities or false ascent/descent spikes.

---

## 2. Architectural Gap Analysis & Independent Hypothesis Verification
* **Forensic Gap Analysis**:
  * `SensorSourceDialog.kt` & `SensorStatus.kt`: Tapping `SensorType.ALTITUDE` displays connected devices but lacks manual reference elevation input, geocoded naming, or `KnownLocations.db` persistence hooks.
  * `AltitudeFromPressureDevice.java`: Supports `calibrate(referenceAltitude)`, but only broadcasts an un-timestamped scalar `deltaOffset` via `ALTITUDE_CORRECTION_INTENT`.
  * `TrackerService.java`: `mAltitudeCorrectionReceiver` applies a naive uniform shift across the entire workout database (`UPDATE samplesTable SET ALTITUDE = ALTITUDE + delta`) regardless of prior calibrations, distorting already-calibrated starting altitudes and ignoring linear weather drift.
  * `BANALServiceRepository.kt`: `_currentPathPoints` (feeding the live cockpit elevation profile in `TrackingViewModel.kt`) is completely bypassed during altitude corrections, leaving live elevation profile visuals stale.
* **Independent Hypothesis Verification (ATT-3534)**:
  * Verified: `KnownLocationsDatabaseManager.java` (`upsertLocationByGeofence`) already supports atomic upsert with `ElevationSource.MANUAL_USER` and `isLocked = true`.
  * Verified: `LiveWorkoutSession.java` and `WorkoutSummariesDatabaseManager.java` (`shiftAltitudeData`) support shifting extrema and sampled streams, but currently lack piecewise-linear drift interpolation and ascent/descent recomputation across calibration timestamps $[t_0, t_1]$.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Objectives**:
  1. Header Altitude Dialog (`SensorSourceDialog.kt`): Input section pre-filled with current altitude in user's unit (`m` or `ft` via canonical `AltitudeFormatter` per Rule 36), optional location name (geocoded default), and toggle to persist to `KnownLocations.db`.
  2. Active Altimeter Recalibration: Immediate invocation of `AltitudeFromPressureDevice.calibrate(referenceAltitudeMeters)`.
  3. Retroactive Correction Engine in `TrackerService` / `LiveWorkoutSession`:
     * *Case A ($t_0 = \text{none}$)*: Uniform shift $h_{\text{corr}}(t) = h(t) + \Delta h$ for $t \in [t_{\text{start}}, t_1]$ across `WorkoutSamples.db`, `LiveWorkoutSession`, and `_currentPathPoints`.
     * *Case B ($t_0 < t_1$)*: Piecewise-linear drift interpolation $h_{\text{corr}}(t) = h(t) + \Delta h \cdot \frac{t - t_0}{t_1 - t_0}$ across $t \in [t_0, t_1]$, updating $t_0 \leftarrow t_1$ and recomputing `ASCENDING` / `DESCENDING` and min/max extrema.
* **Out-of-Scope Non-Goals**:
  1. Post-workout RTS smoothing (ATT-131 / future ticket).
  2. Modifying GNSS sensor fusion or hardware baseband providers.
  3. Automatic internet DEM retrieval on manual entry (manual user input is authoritative).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Requirement Reference**: Net-new `REQ-TRK-046` (Cockpit Manual Altitude Calibration & Piecewise-Linear Drift Correction), extending `REQ-UI-199` (Altimeter Calibration Dispatch) and `REQ-CON-017` (Barometric Calibration Limiting & IPC).
* **Chesterton's Fence**:
  * `REQ-CON-017` protects against runaway shifts ($|\Delta h| > 500\text{ m}$) and preserves `ALTITUDE_CORRECTION_INTENT` IPC contract.
  * `REQ-UI-199` maintains standby vs. calibrated state and geofence auto-calibration.
  * Invariant: Both existing broadcast semantics and automatic Known Location geofence calibration must remain 100% operational, simply recording $t_0$ upon auto-calibration.

---

## 5. Architectural Strategy & High-Level Solution
* **Cockpit UI & Persistence Layer**:
  * `SensorSourceDialog.kt`: When `sensorType == SensorType.ALTITUDE`, render calibration card. Pre-fill value with canonical `AltitudeFormatter`. On submit, call `KnownLocationsDatabaseManager.upsertLocationByGeofence` (if enabled) and `BANALServiceRepository.calibrateAltimeter`.
* **IPC & Service Layer**:
  * Extend `ALTITUDE_CORRECTION_INTENT` with calibration timestamp $t_1$ (or capture $t_1$ upon broadcast arrival in `TrackerService`).
  * In `TrackerService.java`, track `mLastCalibrationTimeSec` ($t_0$). If $t_0$ is null, apply Case A (uniform shift) and set $t_0 = t_1$. If $t_0$ is set, apply Case B (linear drift over $[t_0, t_1]$) in `WorkoutSamples.db` and update $t_0 = t_1$.
  * In `LiveWorkoutSession.java` & `WorkoutSummariesDatabaseManager.java`: Shift stream points and recompute running `ASCENDING` / `DESCENDING` and extrema over $[t_0, t_1]$.
  * In `BANALServiceRepository.kt`: Expose method to adjust `_currentPathPoints` retroactively.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Continuity & Monotonicity: Linear interpolation guarantees $C^0$ continuity at $t_0$ and $t_1$ with zero vertical steps or false ascent spikes.
  2. Rule 36 Unit Parity: Canonical conversion between metric and imperial; raw meters in databases and IPC.
  3. Concurrency Safety: Database updates execute on single-threaded `mDbExecutor` within atomic transactions.
* **Risk Rating**: **MEDIUM** — Touches live workout tracking, samples database updates, and cockpit UI. Mitigated by isolated transaction blocks and targeted unit tests.
