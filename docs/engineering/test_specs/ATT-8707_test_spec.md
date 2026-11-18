# Stage 2: Requirement & Test Specification - ATT-8707

**Ticket**: [ATT-8707](https://rainerblind.atlassian.net/browse/ATT-8707) | **Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `sprint/2026-11-17_03_Nacht` | **Branch**: `feature/ATT-8707`  
**Requirement Mapping**: `REQ-FIL-024` (refining `REQ-FIL-012.5`/`.6`) | **Test Spec ID**: `TST-FIL-024` | **Date**: 2026-11-17  

---

## 1. Requirement Specification (`REQ-FIL-024`)

### 1.1 Attributes & Rationale
- **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit
- **Rationale**: When `AltitudeAndSlopeDevice_ATTFused` (Rank 0) or `AltitudeFromPositionDevice` (Rank 1) outranks `AltitudeFromPressureDevice` (Rank 2) in `MySensorManager.ALTITUDE_PRIORITY`, background barometric calibration broadcasts (`ALTITUDE_CORRECTION_INTENT`) unconditionally shifted `VerticalSpeedAndSlopeDevice` filters, causing `+96.4 m` phantom `DESCENT` and `-203%` `SLOPE` at startup. Missing `onAccumulatorsReset()`, unsynchronized `calculateMetrics()`, and missing same-device step re-seeding compounded startup artifacts (`ATT-8707`).

### 1.2 Functional & Architectural Requirements
1. **`REQ-FIL-024.1` (Barometric Broadcast Source Guard)**: `VerticalSpeedAndSlopeDevice.mAltitudeCorrectionReceiver` SHALL unwrap `mMySensorManager.getSensor(SensorType.ALTITUDE)` (`ProxySensor.getBestSensor()`) and apply `ALTITUDE_CORRECTION_INTENT` `delta` shifts (`mLastAltitudeSuperFiltered`, `mLastAltitude`, `mAltitudeHistory`, `cAltitudeFilter`, `cAltitudeSuperFilter`) ONLY when the active `SensorType.ALTITUDE` source device has `DeviceType.ALTITUDE_FROM_PRESSURE`. Non-barometric canonical sources SHALL ignore the broadcast without shifting filters or baselines.
2. **`REQ-FIL-024.2` (Synchronized Workout-Start Reset)**: `VerticalSpeedAndSlopeDevice` SHALL override `onAccumulatorsReset()` (`synchronized` on `VerticalSpeedAndSlopeDevice.this`) to reset `mLastAltitudeSuperFiltered`, `mLastAltitude`, `mLastAltitudeSourceDevice`, and `mAltitudeHistoryHead`/`mAltitudeHistoryCount` (`mAltitudeHistory` zeroed) on `RESET_ACCUMULATORS_INTENT`. `calculateMetrics()` MUST synchronize on `VerticalSpeedAndSlopeDevice.this` consistently with `mAltitudeCorrectionReceiver` and `reseedAltitudeFilters()`.
3. **`REQ-FIL-024.3` (Same-Device Step / EKF Re-Anchor Guard)**: `VerticalSpeedAndSlopeDevice` SHALL detect large same-device raw `SensorType.ALTITUDE` step discontinuities (e.g., `ATTFused` initial DEM/GPS anchor snap while `currentDevice == mLastAltitudeSourceDevice`) relative to `mLastAltitude` / active baseline and reseed via `reseedAltitudeFilters(rawAlt)` without integrating phantom `ASCENT`, `DESCENT`, or `SLOPE`.
4. **`REQ-FIL-024.4` (Steady-State & Baro Parity Preservation)**: Steady-state ascent/descent/slope integration, `N=15` linear regression VAM, accuracy/low-speed gating, `[-3000, 3000] m/h` clamping, and barometric calibration shifts when `ALTITUDE_FROM_PRESSURE` IS the active `SensorType.ALTITUDE` source (`TST-FIL-004.5`) SHALL remain intact (`Localization Parity: N/A - no UI strings`).

### 1.3 Acceptance Criteria (Given-When-Then) & Invariants
- *Given* `SensorType.ALTITUDE` backed by `ALTITUDE_AND_SLOPE_ATT_FUSED` (or `ALTITUDE_FROM_POSITION`), *When* `ALTITUDE_CORRECTION_INTENT` emits `delta = +100.0 m`, *Then* filters and `mLastAltitudeSuperFiltered` remain unshifted and `DESCENT`/`SLOPE` stay `0.0 m`/`0%`.
- *Given* pre-workout `mLastAltitudeSuperFiltered` and `mAltitudeHistory` state, *When* `onAccumulatorsReset()` executes, *Then* all baseline/history fields reset under `VerticalSpeedAndSlopeDevice.this` lock.
- *Given* same-device `ATTFused` step snap (`538.0 m -> 438.0 m`), *When* `calculateMetrics()` runs, *Then* `reseedAltitudeFilters(438.0)` executes with `0.0 m` phantom `DESCENT`/`ASCENT`, while steady climbs and active-baro shifts remain accurate.
- **Invariants**: `REQ-FIL-012` VAM `N=15` regression, gating, `[-3000, 3000] m/h` clamp; `TST-FIL-004.5` active-baro shift parity; `REQ-FIL-018` `TrackerService`; zero DB/UI changes; `N/A - no UI strings`.

---

## 2. Test Specification (`TST-FIL-024`)

- **`TST-FIL-024.1` (`VerticalSpeedAndSlopeDeviceTest.kt` — Non-Baro Guard & Active-Baro Parity)**:
  - Back `SensorType.ALTITUDE` with `DeviceType.ALTITUDE_AND_SLOPE_ATT_FUSED` (and `ALTITUDE_FROM_POSITION`), fire `ALTITUDE_CORRECTION_INTENT` (`delta = +100.0 m`), assert `mLastAltitudeSuperFiltered` unchanged and `shiftFilter` called `0` times.
  - Back `SensorType.ALTITUDE` with `DeviceType.ALTITUDE_FROM_PRESSURE`, fire `delta = +25.0 m`, assert `mLastAltitudeSuperFiltered` shifts `+25.0 m` and `shiftFilter` called for `21.0s` and `300.0s` filters (`TST-FIL-004.5` parity).
- **`TST-FIL-024.2` (`VerticalSpeedAndSlopeDeviceTest.kt` — Synchronized `onAccumulatorsReset()`)**:
  - Populate `mLastAltitudeSuperFiltered`, `mLastAltitude`, `mLastAltitudeSourceDevice`, and `mAltitudeHistory`, invoke `onAccumulatorsReset()`, assert all cleared to `null` / `0` and startup ticks emit `0.0 m` `DESCENT`/`ASCENT`.
- **`TST-FIL-024.3` (`VerticalSpeedAndSlopeDeviceTest.kt` — Same-Device Step Re-Seeding & Steady-State)**:
  - Keep `SensorType.ALTITUDE` on same `ATTFused` device; inject `538.0 m -> 438.0 m` (`-100.0 m`) step in `calculateMetrics()`, assert `reseedAltitudeFilters(438.0)` triggers with `0.0 m` `DESCENT`/`ASCENT` and no `-203%` `SLOPE` spike; then feed steady climb and descent (`±0.5 m/s` at `5.0 m/s`) and assert accurate `ASCENT`/`DESCENT`/`VAM`/`SLOPE`.
- **`TST-FIL-024.4` (Targeted Regression)**:
  - `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.VerticalSpeedAndSlopeDeviceTest" --tests "com.atrainingtracker.banalservice.devices.AltitudeAndSlopeDevice_ATTFusedTest"` (`REQ-PRO-049.1`: zero `src/main` source reads).

---

## 3. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-FIL-024.1` | Unit | `VerticalSpeedAndSlopeDevice.mAltitudeCorrectionReceiver` | `REQ-FIL-024.1`, `REQ-FIL-024.4` | Specified |
| `TST-FIL-024.2` | Unit | `VerticalSpeedAndSlopeDevice.onAccumulatorsReset` / sync | `REQ-FIL-024.2` | Specified |
| `TST-FIL-024.3` | Unit | `VerticalSpeedAndSlopeDevice.calculateMetrics` / reseed | `REQ-FIL-024.3`, `REQ-FIL-024.4` | Specified |
| `TST-FIL-024.4` | Regression | `VerticalSpeedAndSlopeDeviceTest`, `AltitudeAndSlopeDevice_ATTFusedTest` | `REQ-PRO-001`, `REQ-PRO-049.1` | Specified |
