# Stage 2: Requirement & Test Specification - ATT-5373

**Ticket**: [ATT-5373](https://rainerblind.atlassian.net/browse/ATT-5373)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.5` | **Branch**: `feature/ATT-5373`  
**Requirement Mapping**: `REQ-FIL-012` (*Stabilized VAM Metric & Altitude Transition Synchronization*)  
**Test Spec ID**: `TST-FIL-004` | **Author**: AI Agent 1 (Flash Implementer) | **Date**: 2026-11-04  

---

## 1. Requirement Specification (REQ-FIL-012)
* `REQ-FIL-012.5` (**Active Altitude Source Transition Synchronization & Re-seeding**): `VerticalSpeedAndSlopeDevice` SHALL monitor the active source device backing `SensorType.ALTITUDE` by unwrapping `ProxySensor.getBestSensor()` / `getSourceSensor()` (Rule 68). When the active `ALTITUDE` source device transitions (e.g. `SPEED_AND_LOCATION_GPS` $\rightarrow$ `ALTITUDE_FROM_POSITION` or `ALTITUDE_FROM_PRESSURE`), the device SHALL immediately re-seed and synchronize `cAltitudeFilter` (21s), `cAltitudeSuperFilter` (300s), `mLastAltitudeSuperFiltered`, and populate the 15-second `mAltitudeHistory` circular buffer uniformly with the initial altitude emitted by the new authoritative source. Standstill descent accumulation (`mDescentSensor`) SHALL remain strictly `0.0 m` and slope (`mSlopeSensor`) and vertical speed (`mVerticalSpeedSensor`) SHALL NOT exhibit transient negative spikes upon source transition.
* `REQ-FIL-012.6` (**Filter Shift & Barometric Calibration Parity**): `TimedMovingAverageFilter` and `FilterManager` SHALL support uniform vertical translation (`shiftValues(delta)` and `shiftFilter(filterData, delta)`). Upon receiving `AltitudeFromPressureDevice.ALTITUDE_CORRECTION_INTENT`, `VerticalSpeedAndSlopeDevice` SHALL shift `cAltitudeFilter` and `cAltitudeSuperFilter` in `FilterManager` alongside `mLastAltitudeSuperFiltered` and `mAltitudeHistory` by `deltaOffset`, preventing rolling-average drift divergence during barometric recalibration.

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: `REQ-FIL-012` (*Stabilized VAM Metric*), `REQ-SEN-025` (*Pairable Position-Based Elevation Sensor*), and `REQ-SEN-021` (*Proxy Sensors*). Targets `VerticalSpeedAndSlopeDevice.java`, `TimedMovingAverageFilter.java`, `FilterManager.java`, and `BANALService.java`.
* `Historical Origin & Commit Trace`: `ATT-1621` / `ATT-1738` (VAM linear regression & gating), `ATT-4845` (`31553134`, initial `mAltitudeCorrectionReceiver`), `ATT-4859` / `ATT-5199` / `ATT-5312` (`REQ-SEN-025`, DEM elevation device).
* `Root Reason for Existing Formulation`: `ATT-4845` shifted local variables on barometric calibration but lacked access to internal filters in `FilterManager`. `ATT-4859` introduced DEM altitude as higher priority than GPS, causing `ProxySensor` to switch sources abruptly without triggering source-transition synchronization in `VerticalSpeedAndSlopeDevice`.
* `Preservation of Core Invariants`: Stationary gating ($|\text{raw\_vam}| < 150\text{ m/h}$), low-speed drift deadbands, genuine ski-lift/elevator motion ($\ge 150\text{ m/h}$), linear regression slope calculation, and outlier clamping $[-3000, 3000]\text{ m/h}$ remain 100% intact.

---

## 2. Acceptance Criteria & System Invariants
* **Criterion 1 (Standstill Source Transition Parity)**:
  * *Given* an athlete at standstill (`distance = 0.0 m`, `speed = 0.0 m/s`) with GPS ellipsoid altitude (e.g. 492.0 m) streaming during initial lock,
  * *When* `AltitudeFromPositionDevice` resolves DEM elevation (e.g. 438.0 m) and registers as the active `ALTITUDE` source,
  * *Then* `VerticalSpeedAndSlopeDevice` re-seeds `cAltitudeFilter`, `cAltitudeSuperFilter`, `mLastAltitudeSuperFiltered`, and `mAltitudeHistory` to 438.0 m,
  * *And* `mDescentSensor` accumulation remains `0.0 m`, `mVerticalSpeedSensor` remains `0 m/h`, and `mSlopeSensor` exhibits no negative spike when motion begins.
* **Criterion 2 (Barometric Calibration Shift Parity)**:
  * *Given* an active tracking session with barometric altitude in `cAltitudeFilter` and `cAltitudeSuperFilter`,
  * *When* `ALTITUDE_CORRECTION_INTENT` is broadcast with `deltaOffset = +20.0 m` (or `-20.0 m`),
  * *Then* `cAltitudeFilter` and `cAltitudeSuperFilter` buffered timestamped samples are uniformly shifted by `deltaOffset` alongside `mLastAltitudeSuperFiltered` and `mAltitudeHistory`.
* **System Invariants**: REQ-FIL-012 deadbands, ski-lift/elevator detection ($\ge 150$ m/h), thread-safe monitor synchronization in filters, and 100% clean-room test pass rate.

---

## 3. Test Specification (TST-FIL-004)
* `[TST-FIL-004.4]` (**Standstill Altitude Source Transition Artifact Suppression**, Unit Test): `VerticalSpeedAndSlopeDeviceTest.kt` simulates Workout #455 trace (GPS 492.0 m at $t = 1..2$ s at standstill, transitioning at $t = 3$ s to DEM 438.0 m, followed by first 20m movement at $t = 11..16$ s). Asserts `mDescentSensor` accumulation is strictly `0.0 m` at standstill and `mSlopeSensor` / `mVerticalSpeedSensor` exhibit no -59% slope or -3.0 m/s artifacts.
* `[TST-FIL-004.5]` (**Barometric Recalibration Filter Shift Synchronization**, Unit Test): `VerticalSpeedAndSlopeDeviceTest.kt` and `TimedMovingAverageFilterTest.kt` verify that broadcasting `ALTITUDE_CORRECTION_INTENT` with $\pm 20.0$ m shifts `cAltitudeFilter` and `cAltitudeSuperFilter` buffered samples, immediately updating `getFilteredValue()` without transient decay or false descent/ascent.
* `[TST-REG-001]` (**Clean-Room Regression Suite**): `./gradlew testDebugUnitTest` runs cleanly with 100% pass rate.

---

## 4. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-FIL-004.4]` | Unit | `VerticalSpeedAndSlopeDevice.calculateMetrics` & source re-seed | `REQ-FIL-012.5` | Specified |
| `[TST-FIL-004.5]` | Unit | `TimedMovingAverageFilter.shiftValues` & `FilterManager.shiftFilter` | `REQ-FIL-012.6` | Specified |
| `[TST-REG-001]` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
