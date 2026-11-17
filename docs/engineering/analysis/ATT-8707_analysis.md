# Stage 1 Analysis: ATT-8707 - VerticalSpeedAndSlopeDevice: +96.4 m Phantom Descent in First 3 Seconds of Workout Startup

**Ticket**: [ATT-8707](https://rainerblind.atlassian.net/browse/ATT-8707) (`2 SP`, `severity-minor`)  
**Sub-task**: Assigned upon Stage 1 submission (`[Analysis]`)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-11-17_03_Nacht`  
**Branch**: `feature/ATT-8707`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-17

---

## 1. Problem Statement & Motivation
* During Pixel 10 run `Kurze Runde var 2 #52` (`2026-11-17_173305`, `workoutID=2188`), `VerticalSpeedAndSlopeDevice` logged a `+96.37 m` phantom `DESCENT` spike over rows 1–3 (`17:33:07`: `+75.23 m`, `17:33:08`: `+14.38 m -> 89.61 m`, `17:33:09`: `+6.76 m -> 96.37 m`) and `SLOPE` hit `-203%`, while `ALTITUDE (Höhe aus Luftdruck)` was steady (`437.88 -> 437.59 -> 438.32 -> 439.19 m`).
* End-of-workout totals were skewed to `+48.93 m / -149.01 m` (vs. `att_fused_altitude_slope` `+76.53 m / -75.32 m`). Expected: `DESCENT` and `SLOPE` stay `0.0 m` / noise-free across startup calibrations, source re-anchors, and workout accumulator resets.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Primary Cause 1 — Unconditional `ALTITUDE_CORRECTION_INTENT` Filter Shift (`VerticalSpeedAndSlopeDevice.java:85-105`)**:
  * `mAltitudeCorrectionReceiver` shifts `mLastAltitudeSuperFiltered`, `mLastAltitude`, `mAltitudeHistory`, `cAltitudeFilter` (21s), and `cAltitudeSuperFilter` (300s) whenever `AltitudeFromPressureDevice.ALTITUDE_CORRECTION_INTENT` broadcasts—without checking whether `AltitudeFromPressureDevice` actually backs `SensorType.ALTITUDE`.
  * Because `att_fused_altitude_slope` (Rank 0) and `AltitudeFromPositionDevice` (Rank 1) outrank `AltitudeFromPressureDevice` (Rank 2) in `MySensorManager.ALTITUDE_PRIORITY` (`MySensorManager.java:83-90`), background baro calibration (`delta ~ +100 m`, e.g. high-pressure raw `~338 m -> 438 m`) shifts `ATTFused`-backed filters from `438 m -> 538 m`. Subsequent 5-Hz `ATTFused` samples (`438 m`) pull `cAltitudeSuperFilter` back down exponentially over 3s (`+75.23`, `+14.38`, `+6.76 m`) and `cAltitudeFilter` down (`-203%` slope).
* **Primary Cause 2 — Same-Device Altitude Step / EKF Re-Anchor (`VerticalSpeedAndSlopeDevice.java:339-385`)**:
  * `checkAltitudeSourceTransition()` only calls `reseedAltitudeFilters()` when `currentDevice != mLastAltitudeSourceDevice`. When `AltitudeAndSlopeDevice_ATTFused.kt:323-325,349-351` re-anchors `ekf.altitude` from uncalibrated baro to first DEM/GPS fix (`KinematicAltitudeSlopeEkf.kt:350-366`), it resets only its own `resetAscentDescentAnchor()`; `VerticalSpeedAndSlopeDevice` sees the same `MyDevice` and integrates the step decay.
* **Primary Cause 3 — Missing `onAccumulatorsReset()` & Thread-Safety Gap (`VerticalSpeedAndSlopeDevice.java:257-332`, `MyDevice.java:60-74`, `FilterManager.kt:250-269`)**:
  * On `RESET_ACCUMULATORS_INTENT` (`TrainingApplication.java:1885`), `FilterManager.resetAllFilters()` clears `cAltitudeFilter` and `cAltitudeSuperFilter`, and `MyDevice` resets `mAscentSensor`/`mDescentSensor`, but `VerticalSpeedAndSlopeDevice` does not override `onAccumulatorsReset()`. Stale pre-start `mLastAltitudeSuperFiltered`, `mAltitudeHistory`, and `mLastAltitudeSourceDevice` survive into `t=0`, and `calculateMetrics()` accesses them unsynchronized vs. `mAltitudeCorrectionReceiver`.
* **Independent Peer Review & Verification**:
  * Ticket hypothesis ("uncalibrated initial altitude / GPS-to-Baro or DEM step before reference settles") is partially confirmed and refined: `TrackerService.java:801,853-867` already guards baro shifts via `isBarometricCanonicalAltitude()` (`REQ-FIL-018.2`), whereas `VerticalSpeedAndSlopeDevice.java:85-105` lacked that source guard; `VerticalSpeedAndSlopeDevice.java:64` declares `mLastAltitude` (updated in `:96,378`) but never reads it.
  * Sibling `AltitudeAndSlopeDevice_ATTFused.kt:268,366-389` is unaffected because it isolates its own 2.0 m hysteresis anchor and resets it on re-anchor (`ATT-8575`).

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Gate `mAltitudeCorrectionReceiver` in `VerticalSpeedAndSlopeDevice` so barometric `ALTITUDE_CORRECTION_INTENT` shifts apply only when `DeviceType.ALTITUDE_FROM_PRESSURE` is the active `SensorType.ALTITUDE` source.
  * Override `onAccumulatorsReset()` in `VerticalSpeedAndSlopeDevice` (synchronized) to clear stale baseline/history state on workout start, and handle same-device startup/re-anchor step discontinuities cleanly without accumulating phantom ascent/descent or `-203%` slope.
* **Out-of-Scope Non-Goals**: Modifying `AltitudeAndSlopeDevice_ATTFused.kt`, `KinematicAltitudeSlopeEkf.kt`, `AltitudeFromPressureDevice.java`, `FilterManager.kt` APIs, `WorkoutSamples.db` schema, or UI strings.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-FIL-012` (`REQ-FIL-012.5`, `REQ-FIL-012.6`) & `TST-FIL-004` (`docs/requirements/req_tracking_cockpit.md:220-270`, `docs/tests/tst_tracking_cockpit.md:416-437`). Targets `VerticalSpeedAndSlopeDevice.java`.
* **Historical Origin & Commit Trace**: `f91352e3` (`ATT-1621`, VAM regression), `fd97792e` (`ATT-1738`, GPS wander/accuracy gating), `31553134` (`ATT-4845`, `mAltitudeCorrectionReceiver`), `8d45376e` (`ATT-5373`, `checkAltitudeSourceTransition` + `shiftFilter`).
* **Root Reason for Existing Formulation**: `ATT-5373` assumed `ALTITUDE_CORRECTION_INTENT` always applied to the active `ALTITUDE` filter and that large altitude jumps only occurred across `MyDevice` switches (before `ATT-6279` added `ATTFused` at Rank 0).
* **Preservation of Core Invariants**: Linear regression VAM (`N=15`), accuracy/low-speed gating, `[-3000, 3000] m/h` clamp, true barometric calibration shift parity when `ALTITUDE_FROM_PRESSURE` IS active (`TST-FIL-004.5`), and steady-state `ASCENT`/`DESCENT`/`SLOPE` accumulation.

---

## Change Impact Analysis
* **Affected Requirements**: Net-new sub-requirement / refinement under `REQ-FIL-012` (`REQ-FIL-012.5`/`.6`) and `TST-FIL-004` (`docs/requirements/req_tracking_cockpit.md`, `docs/tests/tst_tracking_cockpit.md`).
* **Affected Architecture Elements**: `VerticalSpeedAndSlopeDevice.java` (`mAltitudeCorrectionReceiver`, `onAccumulatorsReset()`, `checkAltitudeSourceTransition()` / `calculateMetrics()` synchronization) and `VerticalSpeedAndSlopeDeviceTest.kt`.
* **Verification Impact & Test Surface**: `VerticalSpeedAndSlopeDeviceTest` (`TST-FIL-004`, including regression tests for non-baro canonical `ALTITUDE_CORRECTION_INTENT` ignore, `onAccumulatorsReset()` state clearing, and same-device re-anchor step handling). `N/A - no UI strings`.
* **Risk Assessment & Invariants**: **LOW** (`2 SP`). Localized to `VerticalSpeedAndSlopeDevice.java`; preserves `TST-FIL-004.1-.5`, `REQ-FIL-018` `TrackerService` handling, thread-safe synchronization, and zero DB/UI/submodule changes.

---

## 6. Architectural Strategy & System Invariants
1. **Barometric Broadcast Source Guard (`mAltitudeCorrectionReceiver`)**: Unwrap `mMySensorManager.getSensor(SensorType.ALTITUDE)` (`ProxySensor.getBestSensor()`) and apply `delta` shifts only when the active source device has `DeviceType.ALTITUDE_FROM_PRESSURE` (preserving `TST-FIL-004.5` when baro is canonical, ignoring shifts when `ATTFused` or `DEM` is canonical).
2. **Synchronized Workout-Start Reset (`onAccumulatorsReset()`)**: Synchronize `calculateMetrics()` with `mAltitudeCorrectionReceiver`/`reseedAltitudeFilters()` on `VerticalSpeedAndSlopeDevice.this`; override `onAccumulatorsReset()` to reset `mLastAltitudeSuperFiltered`, `mLastAltitude`, `mLastAltitudeSourceDevice`, and `mAltitudeHistory*` counters cleanly at workout start.
3. **Same-Device Step / Re-Anchor Guard**: Detect non-physical instantaneous jumps in raw `SensorType.ALTITUDE` (e.g., `ATTFused` initial Baro-to-Ref snap while `currentDevice == mLastAltitudeSourceDevice`) relative to `mLastAltitude` / `cAltitudeFilter` and reseed via `reseedAltitudeFilters(rawAlt)` instead of integrating calibration steps into `DESCENT`/`ASCENT`/`SLOPE`.
4. **Core Invariants**: Linear regression VAM (`N=15`), accuracy/low-speed gating, `[-3000, 3000] m/h` clamp, and steady-state `ASCENT`/`DESCENT`/`SLOPE` accumulation remain untouched.
