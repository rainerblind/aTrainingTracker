# Stage 1 Analysis: ATT-8081 - Normalize GPS WGS84 altitude to MSL and apply ascent hysteresis in AltitudeAndSlopeDevice_ATTFused

**Ticket**: [ATT-8081](https://rainerblind.atlassian.net/browse/ATT-8081)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `Sprint 2026-47.2`  
**Branch**: `feature/ATT-8081`  
**Author**: AI Agent 1 (Implementer, Lane 1 Flash)  
**Date**: 2026-11-16  

---

## 1. Problem Statement & Motivation
During outdoor field telemetry of `AltitudeAndSlopeDevice_ATTFused` (`ATT-6279`) on real-world workouts (e.g. `Zum Bäcker #54`, 2,038 m loop in Schönaich with ~20–26 m real ascent), moving telemetry showed a +11 m vertical offset and 94.6 m Ascent / 95.9 m Descent (~4.5x inflated climbing):
* **WGS84 Ellipsoid vs. MSL Geoid Tug-of-War**: In Central Europe, WGS84 ellipsoidal height sits $N \approx +47.5\text{ m}$ above orthometric Mean Sea Level (MSL). When both DEM (`AltitudeFromPositionDevice`, MSL) and GPS (`SpeedAndLocationDevice`, WGS84) are enabled, `AltitudeAndSlopeDevice_ATTFused.kt` feeds both into `ekf.updateReferenceAltitude()`. Their inverse-variance compromise shifts fused elevation +11 m above true ground, while alternating ±48 m innovations inject opposing Kalman kicks every second, causing slope oscillations (-28% .. +13%) and VAM swings.
* **Zero-Hysteresis 5-Hz Rectification**: `publishAltitudeAndMetrics()` executes on every ~5 Hz hardware pressure event, summing raw `deltaAlt > 0` directly into `mAscentSensor` and `deltaAlt < 0` into `mDescentSensor` without deadband, rectifying barometric micro-ripples into phantom ascent.
* **Locale-Dependent SQLite Columns**: `getName()` returns localized strings (`R.string.devices_altitude_slope_ATT_fused`), persisting language-dependent column names in `WorkoutSamples.db`.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
1. **Unnormalized GPS vs. DEM Innovations**: `AltitudeAndSlopeDevice_ATTFused.kt:298-332` (`checkReferenceAltitudeFromSensors()`) queries `demSensor` and `gpsSensor` independently, feeding uncorrected WGS84 GPS altitude into the EKF even when fresh MSL DEM data is active.
2. **Noise Rectification without Hysteresis**: Lines 349-357 in `publishAltitudeAndMetrics()` accumulate raw single-step delta differences at 5 Hz instead of applying an anchor-based hysteresis deadband (1.5–2.0 m).
3. **Localized Device Name in Sample Attribution**: Lines 168-169 override `getName()` with localized string resource rather than a canonical technical identifier (e.g. `att_fused_altitude_slope`).
* **Independent Peer Review & Verification**:
  * Confirmed that `SpeedAndLocationDevice.java:777` publishes raw `location.getAltitude()`. AndroidX `LocationCompat.hasMslAltitude(location)` and `LocationCompat.getMslAltitudeMeters(location)` can provide genuine MSL altitude on compatible Android platforms.
  * Verified that DEM from `AltitudeFromPositionDevice` is always orthometric MSL. When fresh DEM is present, it must take precedence over unnormalized GPS altitude.

---

## 3. Scope Grounding & Non-Goals
* **In-Scope Goals**:
  1. In `AltitudeAndSlopeDevice_ATTFused`, establish reference precedence: when fresh DEM is available, bypass raw WGS84 GPS altitude; if GPS altitude is used, normalize to MSL via `LocationCompat` when available.
  2. Implement an anchor-based elevation hysteresis deadband (1.5–2.0 m) for cumulative ascent/descent calculation.
  3. Change `AltitudeAndSlopeDevice_ATTFused.getName()` to return a locale-invariant identifier (`att_fused_altitude_slope`) for SQLite persistence while maintaining localized display names in `DevicesDatabaseManager`.
* **Out-of-Scope Non-Goals**:
  1. Modifying core EKF state transitions in `KinematicAltitudeSlopeEkf.kt`.
  2. Modifying `ElevationService` or `ElevationCacheDatabaseManager`.
  3. Modifying `GpxRouteImporter` or other offline import engines.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-FIL-016` (*Coupled Kinematic Elevation & Slope Filter Virtual Device*).
* **Historical Origin & Commit Trace**: `ATT-6279` (Sprint 2026-46.6).
* **Root Reason for Existing Formulation**: `ATT-6279` verified DEM and GPS modes in separate unit tests with artificial zero-geoid coordinates, overlooking the real-world $N \approx +47.5\text{ m}$ datum conflict and 5 Hz pressure noise rectification.
* **Preservation of Core Invariants**: Standstill clamping ($v < 0.3\text{ m/s}$), 2-State / 3-State EKF observability switching, and column whitelist invariants remain preserved.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Net-new `REQ-FIL-020` (*MSL Normalization, Reference Precedence, and Elevation Hysteresis for ATT Fused Altitude*, refined from `SYSREQ-TRK-003`, `SYSREQ-TRK-004`).
* **Affected Architecture Elements**: `AltitudeAndSlopeDevice_ATTFused.kt`, `SpeedAndLocationDevice.java`.
* **Verification Impact & Test Surface**: Net-new `TST-FIL-020` unit tests covering DEM/GPS tug-of-war elimination, 5-Hz barometric noise rejection with 2.0 m hysteresis, and locale-invariant column persistence.
* **Risk Assessment & Invariants**: Low risk; bounded within virtual sensor driver with live fallback.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Standstill clamping produces 0.0 m drift during pauses.
  2. `DevicesDatabaseManager` retains user-visible localized names.
  3. Fused altitude converges to true MSL ground elevation.
* **Risk Rating**: `LOW` — pure algorithmic and reference gating enhancement.
