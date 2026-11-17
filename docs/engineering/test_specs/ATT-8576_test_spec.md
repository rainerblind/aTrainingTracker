# Stage 2: Requirement & Test Specification - ATT-8576: att_fused GNSS Jump Gating, Standstill Slope Clearing & Low-Speed Slope Damping

**Ticket**: `ATT-8576` (`2 SP`, Epic `ATT-166`) | **Branch**: `feature/ATT-8576` | **Date**: 2026-11-17  
**Requirement**: `REQ-FIL-023` (`REQ-FIL-023.1`..`REQ-FIL-023.3`) | **Test Spec**: `TST-FIL-023` (`TST-FIL-023.1`..`TST-FIL-023.4`)  
**Author**: AI Agent 1 (`--as agent1`) | **Localization Parity**: `N/A - no UI strings`

---

## 1. Requirement Specification (`REQ-FIL-023`)

### 1.1 Attributes
- **Priority**: High | **Type**: Functional | **Verification Method**: Unit | **System Requirement(s)**: `SYSREQ-TRK-003`, `SYSREQ-TRK-004`

### 1.2 Functional & Architectural Requirements
1. **Cold GNSS Convergence Gating & Accuracy Preservation (`REQ-FIL-023.1`)**:
   - `SpeedAndLocationDevice_ATTFused.onLocationChanged()` SHALL defer `refLat`/`refLon` anchoring and suppress `onNewLocation()` emission on initial degraded fixes (`acc > MAX_GNSS_ACCURACY_METERS = 20.0 m`), anchoring cleanly on the first valid fix without synthetic displacement.
   - `SpeedAndLocationDevice_ATTFused` SHALL apply speed/ZUPT updates before `ekf.updateGnssPosition()` and preserve `location.accuracy >= acc`, preventing `SpeedAndLocationDevice.onNewLocation()` from shrinking `escapeRadius` (`REQ-TRK-056.1`).
   - `UnicycleOdometryEkf.updateGnssPosition()` SHALL gate stationary/cold GNSS innovation jumps while preserving `REQ-FIL-009` tunnel-exit re-acquisition when moving.
2. **Standstill Slope Decay & 0% Cockpit Clearing (`REQ-FIL-023.2`)**:
   - During sustained standstill (`speedMps < minSpeed`, default `0.5 m/s`), `KinematicAltitudeSlopeEkf` SHALL decay `slope` toward `0.0` (preserving `dh = 0`, `Q_g = 0`, and `REQ-FIL-022` `baroBias`), and `AltitudeAndSlopeDevice_ATTFused.publishAltitudeAndMetrics()` SHALL publish the cleared `0%` `SLOPE` to `mSlopeSensor` (`ATT-8473`/`ATT-8474` coherence).
3. **Low-Speed Slope Coupling Damping & Grade Bounds (`REQ-FIL-023.3`)**:
   - Below `~1.2 m/s`, `KinematicAltitudeSlopeEkf` SHALL damp slope-innovation gain over small `deltaDist` steps and clamp extreme physical grades, preventing `+29%` / `-31%` spikes and `ASCENT` inflation while keeping real `+8%` climbs (`>= 1.5 m/s`) and `ExtendedRts*Smoother` intact.

### 1.3 Acceptance Criteria (Given-When-Then)
* *Given* `SpeedAndLocationDevice_ATTFused` receiving `25.1 m` (`0 m/s`) then `16.0 m` (`25.2 m` jump, `0 m/s`), *When* `onLocationChanged()` runs, *Then* `mSpeedSensor < 2.0 m/s`, `mDistanceSensor < 5.0 m`, and `location.accuracy >= 16.0f`.
* *Given* `AltitudeAndSlopeDevice_ATTFused` / `KinematicAltitudeSlopeEkf` at `-21%` slope before stopping, *When* `speed = 0.0 m/s` holds (`>= 10 s`), *Then* `ekf.slopePercent` and `mSlopeSensor` clear to `0%` with `0.0 m` ascent/descent.
* *Given* `0.85 m/s` roll-away over flat DEM with `±0.35 m` 5-Hz baro noise (`30 s`), *When* EKF + device run, *Then* `SLOPE` avoids `+29%` spikes and `ASCENT` stays near `0.0 m`, while a `+8%` climb at `5.0 m/s` tracks accurately.

### 1.4 Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new `REQ-FIL-023` refining `REQ-FIL-007`, `REQ-SEN-028.3`, `REQ-FIL-016.1`/`.2`, and `REQ-FIL-022` (`bc4a4109`, `75c1497e`, `8b0eba41`, `e9766625`, `196b3e67`).
* **Preservation of Core Invariants**: Preserves `SpeedAndLocationDevice.java` `REQ-TRK-056`, `REQ-FIL-009`, `REQ-FIL-020.1`/`.2`, `REQ-FIL-022` `baroBias` / 2-state `p00`, `@Synchronized` safety, and `ExtendedRts*Smoother.kt` (`N/A - no UI strings`).

---

## 2. Test Specification (`TST-FIL-023`)

1. **`TST-FIL-023.1` (`SpeedAndLocationDevice_ATTFusedTest.kt` & `UnicycleOdometryEkfTest.kt`)**: Cold `25.1 m -> 16.0 m` (`25.2 m` offset, `0 m/s`) convergence gating, `location.accuracy >= 16.0f`, and moving tunnel-exit re-acquisition (`REQ-FIL-009`).
2. **`TST-FIL-023.2` (`KinematicAltitudeSlopeEkfTest.kt` & `AltitudeAndSlopeDevice_ATTFusedTest.kt`)**: Pre-stop `-21%` slope decays to `0%` (`mSlopeSensor == 0`) over sustained standstill with `0.0 m` climb and preserved `baroBias`.
3. **`TST-FIL-023.3` (`KinematicAltitudeSlopeEkfTest.kt` & `AltitudeAndSlopeDevice_ATTFusedTest.kt`)**: Flat `0.85 m/s` roll-away with `±0.35 m` 5-Hz baro noise suppresses `+29%` / `ASCENT` inflation while preserving `+8%` climb at `5.0 m/s`.
4. **`TST-FIL-023.4` (Targeted Regression)**: `SpeedAndLocationDevice_ATTFusedTest`, `UnicycleOdometryEkfTest`, `AltitudeAndSlopeDevice_ATTFusedTest`, `KinematicAltitudeSlopeEkfTest`, `SpeedAndLocationDeviceStationaryJitterTest`, `OdometrySimulationTest` (`REQ-PRO-049.1` behavioral only).

---

## 3. Traceability Matrix

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-FIL-023.1` | Unit | Cold GNSS `25.1->16.0 m` gating, accuracy & tunnel exit | `REQ-FIL-023.1` | Planned |
| `TST-FIL-023.2` | Unit | Standstill slope decay & `0%` `mSlopeSensor` clearing | `REQ-FIL-023.2` | Planned |
| `TST-FIL-023.3` | Unit | Low-speed slope damping, bounds & true climb tracking | `REQ-FIL-023.3` | Planned |
| `TST-FIL-023.4` | Regression | 6 targeted odometry & altitude-slope suites | `REQ-FIL-023.1`..`REQ-FIL-023.3` | Planned |
