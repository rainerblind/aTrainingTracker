# Stage 2 Requirement & Test Specification: ATT-1617

**Ticket**: [ATT-1617](https://atrainingtracker.atlassian.net/browse/ATT-1617)  
**Sub-task**: [ATT-1695](https://atrainingtracker.atlassian.net/browse/ATT-1695) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.5`  
**Branch**: `feature/ATT-1617`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-30  

---

## 1. Traceability Matrix

| Requirement ID | Test Case ID | Scope Summary | Verification Method | Status |
|:---|:---|:---|:---|:---|
| **REQ-UI-199** | **TST-UI-153** | Lieblingsorte: Cockpit Calibration Badge Synchronized Altimeter Dispatch & Standby Architecture | Unit Tests (`AltitudeFromPressureDeviceTest.kt`, `TrackingTabsViewModelLocationTest.kt`, `LocationCalibrationBadgeTest.kt`, `LocationCalibrationLocalizationTest.kt`) & Clean-Room Regression | Specified |

---

## 2. Formal Requirement Specification

### `REQ-UI-199`: Lieblingsorte Cockpit Calibration Badge Synchronized Altimeter Dispatch & Standby Architecture

The system SHALL synchronize the cockpit location calibration badge (`LocationCalibrationBadge.kt`) with the actual calibration state of the barometric altimeter (`AltitudeFromPressureDevice.java`), dispatching reference elevation on geofence entry and distinguishing uncalibrated standby from active calibration (ATT-1617):

1. **Direct Altimeter Calibration Dispatch & Pending Application**:
   - `AltitudeFromPressureDevice` SHALL provide a synchronized method `calibrate(referenceAltitude: Double): Boolean`.
   - When called:
     - If raw pressure altitude `mLastRawAltitude` is available (`!Double.isNaN(mLastRawAltitude)`): `mAltitudeCorrection` SHALL be set to `referenceAltitude - mLastRawAltitude` (or current altitude), `mPressureSensorInitialized` set to `true`, `mIsCalibrated` set to `true`, `mPendingReferenceAltitude` reset to `Double.NaN`, `mAltitudeSensor.newValue(mLastRawAltitude + mAltitudeCorrection)` published immediately, and `ALTITUDE_CORRECTION_INTENT` broadcast.
     - If raw pressure is not yet available (`Double.isNaN(mLastRawAltitude)` during sensor warmup): `mPendingReferenceAltitude` SHALL cache `referenceAltitude`. As soon as the first pressure measurement arrives in `handlePressureMeasurement(pressureHpa)`, the pending calibration SHALL be executed automatically.
   - `DeviceManager`, `BANALService` (via `BANALServiceComm`), and `BANALServiceRepository` SHALL expose `calibrateAltimeter(referenceAltitude: Double): Boolean` and `isAltimeterCalibrated: StateFlow<Boolean>`.

2. **Closed-Loop State Verification in Tracking Cockpit**:
   - In `TrackingTabsViewModel.kt`, whenever `currentLocation` matches an existing known location geofence (`closestItem != null`), the ViewModel SHALL trigger `banalServiceRepository.calibrateAltimeter(closestItem.altitude)`.
   - `LocationCalibrationStatus` SHALL evaluate `isCalibrated` directly from `banalServiceRepository.isAltimeterCalibrated`, eliminating false claims of completed calibration.

3. **Two-State Visual Feedback (Standby vs. Calibrated)**:
   - In `LocationCalibrationBadge.kt`:
     - When `status.isCalibrated == true`: The badge SHALL render `R.string.location_calibrated_format`: `"%1$s (%2$s calibrated)"` / `"%1$s (%2$s kalibriert)"`.
     - When `status.isCalibrated == false`: The badge SHALL render `R.string.location_detected_format`: `"%1$s detected"` / `"%1$s erkannt"` (standby mode), preventing false claims of completed calibration.

4. **100% 9-Language Localization Parity**:
   - The string resource `location_detected_format` SHALL be defined across all 9 application locales:
     - `values/` (EN): `"%1$s detected"`
     - `values-de/` (DE): `"%1$s erkannt"`
     - `values-es/` (ES): `"%1$s detectado"`
     - `values-fr/` (FR): `"%1$s détecté"`
     - `values-it/` (IT): `"%1$s rilevato"`
     - `values-ja/` (JA): `"%1$s を検出"`
     - `values-nl/` (NL): `"%1$s gedetecteerd"`
     - `values-pl/` (PL): `"%1$s wykryty"`
     - `values-pt/` (PT): `"%1$s detectado"`

5. **Preservation of Core Invariants**:
   - `LocationCalibrationBadge` placement on `ControlTrackingScreen.kt`, animated transitions, metric/imperial unit formatting, single-thread SQLite concurrency in `KnownLocationsDatabaseManager`, and `ALTITUDE_CORRECTION_INTENT` broadcast contracts MUST remain strictly intact.

---

## 3. Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**: `REQ-UI-183` (*Lieblingsorte: Live Cockpit Location & Barometer Calibration Feedback*), targeting `ControlTrackingScreen.kt`, `TrackingTabsViewModel.kt`, `LocationCalibrationBadge.kt`, and `AltitudeFromPressureDevice.java`.
2. **Historical Origin & Commit Trace**: Ticket `ATT-1399` (`feat(cockpit): add live favorite location feedback badge and calibration status (ATT-1399, ATT-1572)`).
3. **Root Reason for Existing Formulation**: Originally, `LocationCalibrationStatus` assumed that matching a geofence implied calibration was active, hardcoding `isCalibrated = true` without verifying or dispatching actual calibration to `AltitudeFromPressureDevice`.
4. **Refinement Reason**: Synchronize the UI badge with the actual barometric sensor state; dispatch calibration to `AltitudeFromPressureDevice` on geofence match; indicate standby/detection (e.g. "Zu Hause erkannt") during sensor warmup or uncalibrated state, and only claim "kalibriert" once `AltitudeFromPressureDevice` has actively applied the ground-truth offset.
5. **Preservation of Core Invariants**: Preservation of `LocationCalibrationBadge` placement on `ControlTrackingScreen.kt`, 9-language localization, imperial/metric conversions, smooth `AnimatedVisibility`, single-thread SQLite confinement, and `ALTITUDE_CORRECTION_INTENT` broadcast semantics.

---

## 4. Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Warmup Standby Feedback)**:
  - *Given* an athlete opens the app at a known favorite location ("Zu Hause", 436 m) while the pressure sensor is warming up or uncalibrated (`isAltimeterCalibrated == false`),
  - *When* viewing the Control Tracking screen,
  - *Then* `LocationCalibrationBadge` SHALL display `Zu Hause erkannt` (standby mode) and SHALL NOT claim that calibration has completed.

* **Criterion 2 (Calibrated Sensor Synchronization)**:
  - *Given* the athlete at a known favorite location ("Zu Hause", 436 m) and the barometric altimeter applies the reference altitude offset,
  - *When* the sensor is calibrated (`isAltimeterCalibrated == true`),
  - *Then* `LocationCalibrationBadge` SHALL display `Zu Hause (436 m kalibriert)` and the barometric altitude sensor (`Luftdruck`) in telemetry dialogs SHALL display `436 m` (± sensor noise).

* **Criterion 3 (Geofence Exit & Baseline Retention)**:
  - *Given* the athlete moves away from the favorite location beyond its geofence radius,
  - *When* `currentLocation` updates,
  - *Then* `locationCalibrationStatus` SHALL emit `null` and `LocationCalibrationBadge` SHALL animate out smoothly while the altimeter retains its calibrated barometric baseline.

---

## 5. Detailed Test Specification

### `TST-UI-153`: Lieblingsorte Cockpit Location Calibration Badge & Altimeter Synchronization Verification

#### 5.1 Altimeter Driver Calibration Unit Tests (`AltitudeFromPressureDeviceTest.kt`)
1. **Calibration Dispatch with Raw Altitude Available**:
   - Set `mLastRawAltitude = 366.0`.
   - Call `calibrate(436.0)`.
   - Verify `mAltitudeCorrection` equals `70.0` (436.0 - 366.0).
   - Verify `isCalibrated()` returns `true`.
   - Verify `mAltitudeSensor.getValue()` returns `436.0`.
   - Verify broadcast `ALTITUDE_CORRECTION_INTENT` is dispatched with value `70.0`.
2. **Pending Calibration during Sensor Warmup**:
   - Ensure `mLastRawAltitude` is `Double.NaN`.
   - Call `calibrate(436.0)`.
   - Verify `isCalibrated()` returns `false` and pending elevation is stored.
   - Invoke `handlePressureMeasurement(pressureHpa)` (yielding raw 366.0m).
   - Verify pending calibration is automatically applied, `mAltitudeCorrection = 70.0`, `isCalibrated()` returns `true`, and sensor value becomes `436.0`.
3. **Idempotent Calibration**:
   - Calling `calibrate(436.0)` when already calibrated to 436.0m does not emit redundant duplicate correction broadcasts.

#### 5.2 BANALService & Repository State Flow Unit Tests (`BANALServiceRepositoryTest.kt`)
1. **State Reflection**:
   - Verify `banalServiceRepository.isAltimeterCalibrated` reflects `serviceBinder.isAltimeterCalibrated()`.
2. **Method Delegation**:
   - Calling `banalServiceRepository.calibrateAltimeter(436.0)` delegates directly to `serviceBinder.calibrateAltimeter(436.0)`.

#### 5.3 ViewModel Dynamic Calibration Dispatch & State Verification (`TrackingTabsViewModelLocationTest.kt`)
1. **Uncalibrated Geofence Entry**:
   - Athlete location is inside "Zu Hause" (436m) geofence, but `isAltimeterCalibrated` is `false`.
   - Verify `locationCalibrationStatus` emits `LocationCalibrationStatus(locationName = "Zu Hause", referenceAltitude = 436.0, isCalibrated = false)`.
2. **Calibration Trigger**:
   - Verify entering the geofence triggers `banalServiceRepository.calibrateAltimeter(436.0)`.
3. **Calibrated Geofence Entry**:
   - When `isAltimeterCalibrated` transitions to `true`, verify `locationCalibrationStatus` emits `isCalibrated = true`.

#### 5.4 LocationCalibrationBadge Composable Unit Tests (`LocationCalibrationBadgeTest.kt`)
1. **Calibrated Rendering**:
   - Pass status with `isCalibrated = true`. Verify rendered text displays `Zu Hause (436 m kalibriert)` (or English `Zu Hause (436 m calibrated)`).
2. **Standby Rendering**:
   - Pass status with `isCalibrated = false`. Verify rendered text displays `Zu Hause erkannt` (or English `Zu Hause detected`).

#### 5.5 9-Language Localization Audit (`LocationCalibrationLocalizationTest.kt`)
1. Verify `location_detected_format` exists in all 9 resource directories (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
2. Verify all 9 definitions contain single string specifier `%1$s` (or `%s`).

#### 5.6 Clean-Room Full Suite Regression Execution
- Execute `./gradlew testDebugUnitTest` across all modules.
