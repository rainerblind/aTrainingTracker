# Stage 2: Requirement & Test Specification - ATT-8575: 3-State EKF Barometer Initialization & Calibration Jump Guard

**Ticket**: `ATT-8575` (`2 SP`, Epic `ATT-166`) | **Branch**: `feature/ATT-8575` | **Date**: 2026-11-17  
**Requirement**: `REQ-FIL-022` (`REQ-FIL-022.1`..`REQ-FIL-022.3`) | **Test Spec**: `TST-FIL-022` (`TST-FIL-022.1`..`TST-FIL-022.4`)  
**Author**: AI Agent 1 (`--as agent1`) | **Localization Parity**: `N/A - no UI strings`

---

## 1. Requirement Specification (`REQ-FIL-022`)

### 1.1 Attributes
- **Priority**: High | **Type**: Functional | **Verification Method**: Unit | **System Requirement(s)**: `SYSREQ-TRK-003`, `SYSREQ-TRK-004`

### 1.2 Functional & Architectural Requirements
1. **3-State Baro-before-Ref Weather-Offset Anchoring (`REQ-FIL-022.1`)**:
   - In `KinematicAltitudeSlopeEkf.kt`, when `is3StateMode == true` and `updateBarometer()` initializes before any reference anchor, the EKF SHALL assign high unanchored `p00` uncertainty (MUST NOT lock `p00 = 0.25`).
   - On the first `updateReferenceAltitude(refAltM, varRef)` (even at standstill `v < 0.3 m/s`), the EKF SHALL set `altitude = refAltM` (`p00 = varRef`), absorb the offset into `baroBias += (oldAltitude - refAltM)`, zero cross-covariances (`p01`, `p10`, `p02`, `p20`), and report initial re-anchoring.
2. **3-State Ref-before-Baro Calibration & 2-State Guard (`REQ-FIL-022.2`)**:
   - When `updateReferenceAltitude()` initializes first (`hasReferenceAnchor = true`) in 3-state mode and the first `updateBarometer(baroAltM)` arrives afterward, the EKF SHALL seed `baroBias = baroAltM - altitude` so large weather offsets (`>= 130 m`) do not pull `altitude` via Huber clamping.
   - In **2-state mode (`!is3StateMode`)**, `updateBarometer()` MUST preserve `p00 = varBaro` (`0.25`) and `ekf.p00 < 10.0` after `predict(1800.0, 5.0)`. `reset()` and `setMode()` SHALL reset anchor/baro tracking flags (`N/A - no UI strings`).
3. **Ascent/Descent Jump Guard (`REQ-FIL-022.3`)**:
   - In `AltitudeAndSlopeDevice_ATTFused.kt` (`handleReferenceAltitude()`, `checkReferenceAltitudeFromSensors()`), initial EKF reference re-anchoring MUST reset/align `mAscentDescentAnchorAltitude` and `mLastAltitude` before `publishAltitudeAndMetrics()`, preventing bogus `ASCENT`/`DESCENT` from calibration jumps.

### 1.3 Acceptance Criteria (Given-When-Then)
* *Given* 3-state EKF initialized at standstill by uncalibrated barometer `568.4 m`, *When* DEM `438.0 m` arrives followed by 5-Hz `568.4 m` barometer updates, *Then* `altitude` anchors at `438.0 ± 0.5 m` and `baroBias` absorbs `130.4 ± 0.5 m`.
* *Given* 3-state EKF initialized first by DEM `438.0 m`, *When* `568.4 m` barometer readings arrive, *Then* `baroBias` seeds `+130.4 m` and `altitude` remains near `438.0 m`.
* *Given* 2-state EKF (`!is3StateMode`), *When* `updateBarometer(200.0)` and `predict(1800.0, 5.0)` execute, *Then* `p00 = varBaro` initially and `ekf.p00 < 10.0`.
* *Given* `AltitudeAndSlopeDevice_ATTFused` starting at `568.4 m` pressure before `438.0 m` DEM/GPS lock, *When* first reference arrives, *Then* `mAltitudeSensor` reports `438.0 m` and `mAscentSensor` / `mDescentSensor` remain `0.0 m`.

### 1.4 Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new `REQ-FIL-022` refining `REQ-FIL-016.1`/`.2` (`ATT-6279`) and `REQ-FIL-020.2` (`ATT-8081`).
* **Preservation of Core Invariants**: Preserves 2-state `p00 = varBaro` (`testPredict_clampsLargeDtSec`), standstill `dh = 0`, DEM-over-GPS precedence (`REQ-FIL-020.1`), `2.0 m` hysteresis (`REQ-FIL-020.2`), and `@Synchronized` thread safety.

---

## 2. Test Specification (`TST-FIL-022`)

1. **`TST-FIL-022.1` (`KinematicAltitudeSlopeEkfTest.kt` — Baro-before-Ref `+130 m`)**: Standstill `568.4 m` baro -> single `438.0 m` DEM -> assert `altitude = 438.0 ± 0.5 m`, `baroBias = 130.4 ± 0.5 m`, unanchored `p00 > 0.25`.
2. **`TST-FIL-022.2` (`KinematicAltitudeSlopeEkfTest.kt` — Ref-before-Baro & 2-State Guard)**: `438.0 m` DEM -> `568.4 m` baro -> assert `altitude = 438.0 ± 0.5 m`, `baroBias = 130.4 ± 0.5 m`; verify 2-state `p00 = varBaro` (`< 10.0`) & `reset()`/`setMode()`.
3. **`TST-FIL-022.3` (`AltitudeAndSlopeDevice_ATTFusedTest.kt` — Ascent/Descent Jump Guard)**: Early `568.4 m` pressure -> `438.0 m` DEM/GPS -> assert `mAltitudeSensor = 438.0 ± 0.5 m` and `mAscentSensor == 0.0 m`, `mDescentSensor == 0.0 m`.
4. **`TST-FIL-022.4` (Targeted Regression)**: `KinematicAltitudeSlopeEkfTest`, `AltitudeAndSlopeDevice_ATTFusedTest`, `MeasuredNfrBenchmarkTest`.

---

## 3. Traceability Matrix

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-FIL-022.1` | Unit | `KinematicAltitudeSlopeEkfTest`: Baro-before-Ref `+130 m` anchoring & `p00` | `REQ-FIL-022.1` | Planned |
| `TST-FIL-022.2` | Unit | `KinematicAltitudeSlopeEkfTest`: Ref-before-Baro, 2-State guard, `reset`/`setMode` | `REQ-FIL-022.2` | Planned |
| `TST-FIL-022.3` | Unit | `AltitudeAndSlopeDevice_ATTFusedTest`: Calibration jump `0.0 m` ascent/descent | `REQ-FIL-022.3` | Planned |
| `TST-FIL-022.4` | Regression | `KinematicAltitudeSlopeEkfTest`, `AltitudeAndSlopeDevice_ATTFusedTest`, `MeasuredNfrBenchmarkTest` | `REQ-FIL-022.1`..`REQ-FIL-022.3` | Planned |
