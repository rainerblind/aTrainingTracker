# Stage 3: Implementation Plan - ATT-8707: VerticalSpeedAndSlopeDevice +96.4 m Phantom Descent Fix

**Ticket**: [ATT-8707](https://rainerblind.atlassian.net/browse/ATT-8707) (`2 SP`) | **Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `sprint/2026-11-17_03_Nacht` | **Branch**: `feature/ATT-8707`  
**Requirement Mapping**: `REQ-FIL-024` (`REQ-FIL-024.1`–`REQ-FIL-024.4`, refining `REQ-FIL-012.5`/`.6`) | **Test Mapping**: `TST-FIL-024`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-17

---

## 1. Problem Description & Background
* `VerticalSpeedAndSlopeDevice` logged `+96.37 m` phantom `DESCENT` (`+75.23 -> +89.61 -> +96.37 m`) and `-203%` `SLOPE` in rows 1–3 of Pixel 10 run `Kurze Runde var 2 #52` (`workoutID=2188`) because: (1) `mAltitudeCorrectionReceiver` shifted filters unconditionally when `ALTITUDE_FROM_PRESSURE` was not the active `SensorType.ALTITUDE` source (`ATTFused` Rank 0 / `DEM` Rank 1); (2) missing `onAccumulatorsReset()` and unsynchronized `calculateMetrics()` carried stale pre-workout baselines across `RESET_ACCUMULATORS_INTENT`; and (3) `checkAltitudeSourceTransition()` missed same-device EKF re-anchor steps.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-FIL-024` (*Barometric Broadcast Source Guard, Synchronized Workout-Start Reset & Same-Device Step Re-Seeding*)
* **Test Mapping**: `TST-FIL-024` (`TST-FIL-024.1`–`TST-FIL-024.4`)

---

## 3. System Invariants & Preserved Behavior
1. **Steady-State & VAM Parity**: `N=15` linear regression VAM, accuracy (`20.0 m`) / low-speed (`1.2 m/s`) gating, `[-3000, 3000] m/h` clamp, and genuine climb/descent/slope integration remain intact (`REQ-FIL-012`).
2. **Active-Baro Calibration Shift**: When `DeviceType.ALTITUDE_FROM_PRESSURE` backs `SensorType.ALTITUDE`, `ALTITUDE_CORRECTION_INTENT` continues shifting filters and baselines (`TST-FIL-004.5`).
3. **Thread Safety & Zero Lock Inversion**: Synchronize `calculateMetrics()`, `onAccumulatorsReset()`, `mAltitudeCorrectionReceiver`, and `reseedAltitudeFilters()` solely on `VerticalSpeedAndSlopeDevice.this` without holding external service locks.
4. **No Schema/UI Changes & Human Gate**: Zero SQLite/UI/strings changes (`N/A - no UI strings`); parent ticket ends at `Final Review (Human)`.

---

## 4. Proposed Architectural Changes
* **Component — `VerticalSpeedAndSlopeDevice.java`**:
  * Add `resolveActiveAltitudeSensor()` helper unwrapping `ProxySensor.getBestSensor()` and `ALTITUDE_STEP_RESEED_THRESHOLD_METERS = 15.0`.
  * Gate `mAltitudeCorrectionReceiver.onReceive()` on active `DeviceType.ALTITUDE_FROM_PRESSURE` and shift all valid ring-buffer slots safely (`(startIndex + k) % VAM_WINDOW_SIZE`).
  * Override `onAccumulatorsReset()` (`synchronized`) to clear `mLastAltitudeSuperFiltered`, `mLastAltitude`, `mLastAltitudeSourceDevice`, `mAltitudeHistoryHead/Count`, and zero `mAltitudeHistory`.
  * Synchronize `calculateMetrics()` and enhance `checkAltitudeSourceTransition()` to reseed on non-physical same-device `rawAlt` jumps (`|rawAlt - mLastAltitude| >= 15.0 m`).
* **UI Consistency**: No UI changes (`N/A - no UI strings`).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
### Step 1: Gate `mAltitudeCorrectionReceiver` & Synchronize State (`REQ-FIL-024.1`, `REQ-FIL-024.2`)
* Files: `app/src/main/java/com/atrainingtracker/banalservice/devices/VerticalSpeedAndSlopeDevice.java`
* Changes: Extract `resolveActiveAltitudeSensor()` (shared by `mAltitudeCorrectionReceiver` and `checkAltitudeSourceTransition()`); ignore `ALTITUDE_CORRECTION_INTENT` unless active `SensorType.ALTITUDE` device is `DeviceType.ALTITUDE_FROM_PRESSURE`; override `onAccumulatorsReset()` (`synchronized`) clearing baseline/history fields; wrap `calculateMetrics()` in `synchronized (this)`.

### Step 2: Same-Device Step / Re-Anchor Reseed Guard (`REQ-FIL-024.3`, `REQ-FIL-024.4`)
* Files: `app/src/main/java/com/atrainingtracker/banalservice/devices/VerticalSpeedAndSlopeDevice.java`
* Changes: In `checkAltitudeSourceTransition()`, read `rawAlt` for both `currentDevice != mLastAltitudeSourceDevice` and `currentDevice == mLastAltitudeSourceDevice`; if `mLastAltitude != null && Math.abs(rawAlt - mLastAltitude) >= ALTITUDE_STEP_RESEED_THRESHOLD_METERS`, invoke `reseedAltitudeFilters(rawAlt)` before filter sampling; otherwise update `mLastAltitude = rawAlt` (adding `@VisibleForTesting` accessor if needed for deterministic test setup).

### Step 3: Unit Tests (`TST-FIL-024.1`–`TST-FIL-024.4`, `Rule 5`, `REQ-PRO-049.1`)
* Files: `app/src/test/java/com/atrainingtracker/banalservice/devices/VerticalSpeedAndSlopeDeviceTest.kt`
* Changes: Add behavioral MockK unit tests (zero `src/main` reads) for: (a) non-baro canonical (`ATTFused`/`DEM`) ignoring `ALTITUDE_CORRECTION_INTENT` + active-baro shift parity; (b) `onAccumulatorsReset()` state reset + `0.0 m` startup; (c) same-device `ATTFused` `538.0 m -> 438.0 m` snap reseeding with `0.0 m` `DESCENT`/`ASCENT`, no `-203%` `SLOPE`, and steady-state `±0.5 m/s` climb/descent preservation.
* Command: `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.VerticalSpeedAndSlopeDeviceTest" --tests "com.atrainingtracker.banalservice.devices.AltitudeAndSlopeDevice_ATTFusedTest"`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted Tier-1 unit tests in Stage 4/5 and `verify_requirement_governance.py`; Gate 3 `check-gate` enforced before Stage 4 edits (`Rule 4.1`).
* **Rollback**: Isolated on `feature/ATT-8707`; revertible cleanly without affecting `sprint/2026-11-17_03_Nacht` or `develop`.
