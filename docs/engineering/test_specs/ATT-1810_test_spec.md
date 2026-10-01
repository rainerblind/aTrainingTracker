# Stage 2: Requirement & Test Specification - ATT-1810: Super implausible altitude

**Ticket**: [ATT-1810](https://rainerblind.atlassian.net/browse/ATT-1810)  
**Sub-task**: [ATT-1892](https://rainerblind.atlassian.net/browse/ATT-1892) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*) / Location & Altimeter Tracking  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.7`  
**Requirement Mapping**: `REQ-CON-017` (*Barometric Altimeter Idempotent Calibration, Delta Shift Dispatch, Physical Sanity Limiting & Historical Workout Data Sanitization*)  
**Test Spec ID**: `TST-CON-008`  
**Branch**: `feature/ATT-1810`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Requirement Specification (`REQ-CON-017`)

### 1.1 Problem Statement & Rationale
In workout *"Kurz zum Bäcker #14"* on Pixel 10 (a 2.05 km ride in Schönaich), the recorded Aftermath elevation profile displayed an extreme, non-physical elevation artifact starting at ~7300 m and dropping in steps to 507 m. Forensic investigation identified a compound feedback defect:
1. In `AltitudeFromPressureDevice.java`, `setAltitudeCorrection` calculated `mAltitudeCorrection = correctAltitude - currentAltitude` using `mAltitudeSensor.getValue()`, which was already corrected. This caused `mAltitudeCorrection` to oscillate between the delta and 0.0 on alternate pressure events.
2. In `TrackingTabsViewModel.kt`, `banalServiceRepository.calibrateAltimeter` was invoked inside the `combine` flow on every GPS location update (~1 Hz) whenever within a known location geofence.
3. Every oscillation broadcasted `ALTITUDE_CORRECTION_INTENT` with a positive offset to `TrackerService`, which repeatedly shifted all historical samples and streams in SQLite, causing runaway accumulation up to 7300 m on early trackpoints.
4. Historical SQLite tables were permanently mutated with 7300 m values, affecting Aftermath rendering, extrema, and GPX/TCX exports.

### 1.2 Functional & Architectural Requirements
The system SHALL eliminate barometric calibration oscillations, prevent runaway retroactive sample accumulation, enforce physical climb rate sanity limits, and sanitize contaminated historical workouts (`REQ-CON-017`):

1. **Absolute Baseline Correction Formulation (`AltitudeFromPressureDevice.java`)**:
   - In `AltitudeFromPressureDevice.setAltitudeCorrection(double correctAltitude)`, `mAltitudeCorrection` SHALL be computed strictly relative to raw barometric pressure:
     $$\text{newCorrection} = \text{correctAltitude} - \text{mLastRawAltitude}$$
   - The method SHALL compute $\text{deltaOffset} = \text{newCorrection} - \text{mAltitudeCorrection}$, update $\text{mAltitudeCorrection} = \text{newCorrection}$, set $\text{mIsCalibrated} = \text{true}$, and publish $\text{mAltitudeSensor.newValue}(\text{mLastRawAltitude} + \text{mAltitudeCorrection})$.
   - `ALTITUDE_CORRECTION_INTENT` SHALL be broadcast containing `ALTITUDE_CORRECTION_VALUE` = `deltaOffset` IF AND ONLY IF $|\text{deltaOffset}| \ge 0.1\text{ m}$. If $|\text{deltaOffset}| < 0.1\text{ m}$, the broadcast MUST be suppressed.
2. **Idempotent Altimeter Calibration Guard (`calibrate`)**:
   - In `AltitudeFromPressureDevice.calibrate(double referenceAltitude)`, if `isCalibrated()` is true and current calibrated altitude is within $0.5\text{ m}$ of `referenceAltitude` ($|(\text{mLastRawAltitude} + \text{mAltitudeCorrection}) - \text{referenceAltitude}| < 0.5$), the method SHALL return `true` immediately without recomputing correction or broadcasting intents.
3. **Geofence Transition Calibration Gating (`TrackingTabsViewModel.kt`)**:
   - In `TrackingTabsViewModel.kt`, altimeter calibration dispatch SHALL be gated on geofence transitions. The ViewModel SHALL maintain `lastCalibratedLocationId: Long?` and trigger `banalServiceRepository.calibrateAltimeter(closestItem.altitude)` IF AND ONLY IF `closestItem.id != lastCalibratedLocationId` or `!isCalibrated`.
   - When exiting all geofences (`closestItem == null`), `lastCalibratedLocationId` SHALL be reset to `null`.
4. **Physical Climb/Descent Sanity Limiting & Atomic DB Transactions (`TrackerService.java`, `LiveWorkoutSession.java`)**:
   - Retroactive sample shifting in `TrackerService.onReceive` SHALL be encapsulated within an explicit atomic SQLite transaction (`beginTransaction()` / `setTransactionSuccessful()` / `endTransaction()`).
   - In `LiveWorkoutSession.applyAltitudeCorrection` and `TrackerService`, single retroactive shift offsets exceeding $|offset| > 500.0\text{ m}$ SHALL be rejected or clamped with an advisory warning.
   - In `LiveWorkoutSession.recordStreamPoint` and `TrackerService`, altitude values outside human physical limits ($-500\text{ m} \dots +9000\text{ m}$) or vertical rate changes exceeding $|dh/dt| > 30\text{ m/s}$ ($108\text{ km/h}$) SHALL be clamped to the previous valid altitude sample.
5. **Historical Workout Data Sanitization & Export Resilience (`WorkoutSummariesDatabaseManager.java`, `GPXFileWriter.java`, `TCXFileWriter.java`, `ElevationProfile.kt`)**:
   - `WorkoutSummariesDatabaseManager` SHALL provide `sanitizeCorruptedAltitudeWorkouts()`: scanning existing workouts with feedback contamination (`maxAltitude > 4000.0m` and `minAltitude < 1000.0m`), adjusting corrupted leading trackpoints in `ALTITUDE_STREAM`, updating `TABLE_EXTREMA_VALUES` (MIN, MAX, AVG), and sanitizing corresponding `samplesTable` rows within an atomic SQLite transaction.
   - `GPXFileWriter.java` and `TCXFileWriter.java` SHALL clamp non-physical altitude samples ($> 9000\text{ m}$ or vertical step spikes $> 100\text{ m}$) to the previous valid sample.
   - In `ElevationProfile.kt` (`calculateElevationBounds`), non-physical start outliers SHALL be filtered from bounds computation, ensuring graph rendering auto-scales cleanly to real terrain (~480–530 m).
6. **Preservation of Core Invariants**:
   - Null safety on cold start (`REQ-CON-013`), DEM elevation caching (`REQ-DAT-014`), start location counting (`REQ-DAT-015`), cockpit calibration badge feedback (`REQ-UI-199`), and 9-language localization parity MUST NOT be broken.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Absolute Correction & Delta Broadcast)**:
  * *Given* an `AltitudeFromPressureDevice` with `mLastRawAltitude = 450.0m` and default `mAltitudeCorrection = 0.0m`,
  * *When* `setAltitudeCorrection(507.0)` is invoked,
  * *Then* `mAltitudeCorrection` SHALL be set to $+57.0\text{ m}$, `ALTITUDE_CORRECTION_INTENT` SHALL be broadcast with extra value $57.0$, and the sensor value SHALL equal $507.0\text{ m}$.
* **Criterion 2 (Oscillation Immunity under Repeated Calls)**:
  * *Given* an `AltitudeFromPressureDevice` calibrated to 507.0m (`mAltitudeCorrection = 57.0m`),
  * *When* `setAltitudeCorrection(507.0)` is invoked repeatedly interleaved with incoming pressure sensor measurements,
  * *Then* `mAltitudeCorrection` SHALL remain $+57.0\text{ m}$ (zero oscillation to 0.0), sensor reading SHALL remain locked at $507.0\text{ m}$, and zero additional broadcasts SHALL be emitted.
* **Criterion 3 (Idempotent Calibration Dispatch)**:
  * *Given* `AltitudeFromPressureDevice.calibrate(507.0)` executed successfully,
  * *When* `calibrate(507.0)` is invoked again while within the same geofence,
  * *Then* it SHALL return `true` immediately with zero broadcasts and zero DB shifts.
* **Criterion 4 (Cockpit Calibration Gating)**:
  * *Given* an athlete inside a known location geofence emitting GPS updates every second,
  * *When* `TrackingTabsViewModel.locationCalibrationStatus` evaluates sequential location emissions,
  * *Then* `calibrateAltimeter` SHALL be invoked on initial geofence entry and SHALL NOT be dispatched repeatedly on continuous updates within the same location.
* **Criterion 5 (Physical Sanity Clamping)**:
  * *Given* incoming live tracking samples with an implausible vertical spike ($dh/dt > 30\text{ m/s}$) or single retroactive shift $> 500\text{ m}$,
  * *Then* the system SHALL clamp the spike and reject runaway offsets.
* **Criterion 6 (Historical Workout Sanitization)**:
  * *Given* a stored workout with max altitude 7300m and min altitude 507m (*"Kurz zum Bäcker #14"*),
  * *When* `sanitizeCorruptedAltitudeWorkouts()` is executed,
  * *Then* `ALTITUDE_STREAM` and `TABLE_EXTREMA_VALUES` SHALL be re-aligned to real terrain (~480–530 m), and Aftermath rendering SHALL auto-scale cleanly.

---

## 2. Test Specification (`TST-CON-008`)

### Test Case 1: `testSetAltitudeCorrection_absoluteOffsetAndDeltaBroadcast` (`[TST-CON-008.1]`)
* **Scope**: Unit Test (`AltitudeFromPressureDeviceTest.kt`)
* **Preconditions**: Device instantiated with mock context and sensor manager; `lastRawAltitude = 450.0m`.
* **Action**: Invoke `device.setAltitudeCorrection(507.0)`.
* **Expected Result**: `device.altitudeCorrection` equals 57.0; broadcast sent with value 57.0; sensor value equals 507.0m.

### Test Case 2: `testSetAltitudeCorrection_repeatedCalls_doesNotOscillate` (`[TST-CON-008.2]`)
* **Scope**: Unit Test (`AltitudeFromPressureDeviceTest.kt`) - Replicating *"Kurz zum Bäcker #14"* defect loop.
* **Preconditions**: Device calibrated to 507.0m with `lastRawAltitude = 450.0m` (`altitudeCorrection = 57.0m`).
* **Action**: Simulate 10 sequential iterations of: `handlePressureMeasurement(pressureHpa)` followed by `setAltitudeCorrection(507.0)`.
* **Expected Result**: `altitudeCorrection` remains strictly 57.0 across all 10 iterations; sensor value remains strictly 507.0m; zero additional broadcasts dispatched.

### Test Case 3: `testCalibrate_idempotency_skipsRedundantWork` (`[TST-CON-008.3]`)
* **Scope**: Unit Test (`AltitudeFromPressureDeviceTest.kt`)
* **Preconditions**: Device with valid raw altitude calibrated to 507.0m.
* **Action**: Invoke `device.calibrate(507.0)` a second time.
* **Expected Result**: Returns `true` immediately; zero broadcasts emitted.

### Test Case 4: `testTrackingTabsViewModel_calibrationGating_dispatchesOnlyOnGeofenceTransition` (`[TST-CON-008.4]`)
* **Scope**: ViewModel Unit Test (`TrackingTabsViewModelLocationTest.kt`)
* **Preconditions**: Athlete within known location geofence (Zu Hause, 507m).
* **Action**: Emit 5 consecutive GPS location fixes inside the same geofence.
* **Expected Result**: `banalServiceRepository.calibrateAltimeter(507.0)` is called exactly once.

### Test Case 5: `testHistoricalSanitizer_repairsCorruptedAltitudeWorkouts` (`[TST-CON-008.5]`)
* **Scope**: Database Unit Test (`WorkoutSummariesDatabaseManagerTest.kt`)
* **Preconditions**: Create workout summary with `ALTITUDE_STREAM` containing 7300m staircase profile descending to 507m; `extrema_max = 7300.0`, `extrema_min = 507.0`.
* **Action**: Execute `sanitizeCorruptedAltitudeWorkouts()`.
* **Expected Result**: `extrema_max` is updated to $\le 550.0\text{ m}$; decoded `ALTITUDE_STREAM` initial points match terrain baseline; Aftermath bounds scale cleanly.

### Test Case 6: `testPhysicalSanityLimits_clampsSpikes` (`[TST-CON-008.6]`)
* **Scope**: Unit Test (`LiveWorkoutSessionTest.kt` / `TrackerServiceTest.kt`)
* **Preconditions**: Live session recording.
* **Action**: Feed altitude samples with instantaneous vertical rate $> 30\text{ m/s}$ or retroactive offset $> 500\text{ m}$.
* **Expected Result**: Spikes are clamped; non-physical retroactive offset is rejected.

### Test Case 7: Clean-Room Regression Suite (`[TST-CON-008.7]`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: 100% pass rate across all modules with zero regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-CON-008.1]` | Unit | `AltitudeFromPressureDevice.setAltitudeCorrection` | `REQ-CON-017` (item 1) | Specified |
| `[TST-CON-008.2]` | Unit | `AltitudeFromPressureDevice.setAltitudeCorrection` | `REQ-CON-017` (item 1) | Specified |
| `[TST-CON-008.3]` | Unit | `AltitudeFromPressureDevice.calibrate` | `REQ-CON-017` (item 2) | Specified |
| `[TST-CON-008.4]` | ViewModel | `TrackingTabsViewModel.locationCalibrationStatus` | `REQ-CON-017` (item 3) | Specified |
| `[TST-CON-008.5]` | DB | `WorkoutSummariesDatabaseManager.sanitizeCorruptedAltitudeWorkouts` | `REQ-CON-017` (item 5) | Specified |
| `[TST-CON-008.6]` | Unit | `LiveWorkoutSession.applyAltitudeCorrection` / `recordStreamPoint` | `REQ-CON-017` (item 4) | Specified |
| `[TST-CON-008.7]` | Full Suite | `./gradlew testDebugUnitTest` | `REQ-PRO-001`, `REQ-CON-017` | Specified |
