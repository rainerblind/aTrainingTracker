# Stage 2: Requirement & Test Specification - ATT-4845: Manual altitude calibration from header Altitude icon with permanent Known Location persistence and retroactive linear drift correction

**Ticket**: [ATT-4845](https://rainerblind.atlassian.net/browse/ATT-4845) | **Sub-task**: Pending (`[Req & Test Spec]`) | **Parent Epic**: ATT-166 (Filtering to improve sensor values)  
**Target Release**: `V4.9.41` | **Active Sprint**: `2026-44.2` | **Branch**: `feature/ATT-4845`  
**Requirement Mapping**: `REQ-TRK-047` (*Cockpit Manual Altitude Calibration & Piecewise-Linear Drift Correction*)  
**Test Spec ID**: `TST-TRK-047` | **Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-01  

---

## 1. Requirement Specification (`REQ-TRK-047`)

### 1.1 Problem Statement & Rationale
During outdoor workouts, barometric altimeters drift as weather and ambient atmospheric pressure change. When athletes pass a landmark with a known reference altitude, they need a fast way to enter that altitude from the tracking cockpit, permanently save it as a Known Location (`KnownLocations.db`), recalibrate `AltitudeFromPressureDevice`, and retroactively correct workout elevation history smoothly without step discontinuities or false ascent/descent spikes.

### 1.2 Functional & Architectural Requirements
1. **Cockpit Header Altitude Dialog Calibration (`REQ-TRK-047.1`)**:
   * In `SensorSourceDialog.kt`, when `sensorType == SensorType.ALTITUDE`, render a "Calibrate Altitude" section pre-filled with the current reading via canonical `AltitudeFormatter` (Rule 36).
   * Provide an optional location name field (defaulting to geocoded/coordinate label) and a toggle to save as a Known Location.
   * On confirmation, invoke `KnownLocationsDatabaseManager.upsertLocationByGeofence` with `ElevationSource.MANUAL_USER` and `isLocked = true`, and dispatch calibration to `AltitudeFromPressureDevice.calibrate(referenceAltitudeMeters)`.
2. **Retroactive Workout Elevation Correction (`REQ-TRK-047.2`)**:
   * Let $t_1$ be calibration timestamp, $h_{\text{ref}}$ reference elevation, $h_{\text{baro}}(t_1)$ current reading, and $\Delta h = h_{\text{ref}} - h_{\text{baro}}(t_1)$:
   * *Case A ($t_0 = \text{none}$, uncalibrated start)*: Uniformly shift all recorded samples in $t \in [t_{\text{start}}, t_1]$ by $+\Delta h$ ($h_{\text{corr}}(t) = h(t) + \Delta h$) across `WorkoutSamples.db`, `LiveWorkoutSession`, and `BANALServiceRepository._currentPathPoints`.
   * *Case B ($t_0 < t_1$, recalibration)*: Apply piecewise-linear drift interpolation across $t \in [t_0, t_1]$ ($h_{\text{corr}}(t) = h(t) + \Delta h \cdot \frac{t - t_0}{t_1 - t_0}$), updating $t_0 \leftarrow t_1$ and cleanly recomputing running `ASCENDING` / `DESCENDING` and min/max extrema.
3. **IPC Contract & Drift Protection (`REQ-TRK-047.3`)**:
   * Broadcast `ALTITUDE_CORRECTION_INTENT` with $\Delta h$ and timestamp $t_1$, guarded by $|\Delta h| \le 500\text{ m}$ per `REQ-CON-017`.
4. **9-Language Localization Parity (`REQ-TRK-047.4`)**:
   * All 6 calibration UI strings maintain 100% translation parity across all 9 supported locales.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Cockpit Calibration & Persistence)**: Given an active tracking session, When tapping the ALTITUDE icon, entering reference altitude (1215 m), and confirming with save enabled, Then the altimeter calibrates to 1215 m and the landmark is persisted in `KnownLocations.db` with `ElevationSource.MANUAL_USER`.
* **Criterion 2 (Case A Uniform Shift)**: Given a workout starting uncalibrated and running 20 minutes before calibration with $\Delta h = +35\text{ m}$, When calibration is applied, Then past samples in `WorkoutSamples.db`, `LiveWorkoutSession`, and `_currentPathPoints` shift uniformly by $+35\text{ m}$.
* **Criterion 3 (Case B Piecewise-Linear Drift)**: Given a workout calibrated at $t_0$ and recalibrated at $t_1 > t_0$ with drift $\Delta h = -18\text{ m}$, When recalibration is applied, Then recorded samples in $[t_0, t_1]$ adjust linearly by $\Delta h \cdot \frac{t - t_0}{t_1 - t_0}$ ($0\text{ m}$ at $t_0$, $-18\text{ m}$ at $t_1$).

### 1.4 System Invariants
1. $C^0$ continuity: zero vertical step discontinuities or false ascent spikes on recalibration.
2. Rule 36 unit parity: metric/imperial conversion via `AltitudeFormatter`; storage strictly in raw meters.
3. Concurrency safety: SQLite updates execute on `mDbExecutor` inside atomic transactions.

---

## 2. Test Specification (`TST-TRK-047`)

### Test Case 1: SensorSourceDialog Altitude Calibration UI & Callback (`TST-TRK-047.1`)
* **Scope**: UI / Component Unit Test | **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SensorSourceDialogAltitudeCalibrationTest.kt`
* **Action**: Assert `SensorSourceDialog` renders calibration card for `ALTITUDE`, pre-fills formatted elevation, and dispatches `onCalibrate`.

### Test Case 2: KnownLocationsDatabaseManager Manual Upsert (`TST-TRK-047.2`)
* **Scope**: DB Unit Test | **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/database/KnownLocationsManualCalibrationTest.kt`
* **Action**: Invoke `upsertLocationByGeofence` with `ElevationSource.MANUAL_USER`; verify `isLocked = true` and query matches.

### Test Case 3: Case A Uniform Retroactive Elevation Shift (`TST-TRK-047.3`)
* **Scope**: Tracker / Session Unit Test | **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/LiveWorkoutSessionAltitudeDriftTest.kt`
* **Action**: On uncalibrated session with samples, apply $+35\text{ m}$; assert all samples and extrema shift by $+35\text{ m}$ and $t_0 = t_1$.

### Test Case 4: Case B Piecewise-Linear Drift Interpolation (`TST-TRK-047.4`)
* **Scope**: Tracker / Math Unit Test | **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/LiveWorkoutSessionAltitudeDriftTest.kt`
* **Action**: Calibrate at $t_0=0$, recalibrate at $t_1=1000$ with $\Delta h=-18\text{ m}$; assert sample at $t=500$ shifts by $-9\text{ m}$ and ascent recomputes without vertical step.

### Test Case 5: 9-Language Localization Parity (`TST-TRK-047.5`)
* **Scope**: Localization Test | **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/localization/TranslationParityTest.kt`
* **Action**: Run `TranslationParityTest`; assert all 6 calibration strings pass across all 9 supported locales.

---

## 3. Traceability Matrix

| Test Case | Scope | Method / Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-047.1` | UI Unit | `SensorSourceDialog` | `REQ-TRK-047.1` | Specified |
| `TST-TRK-047.2` | DB Unit | `KnownLocationsDatabaseManager.upsertLocationByGeofence` | `REQ-TRK-047.1` | Specified |
| `TST-TRK-047.3` | Session Unit | `LiveWorkoutSession.applyRetroactiveAltitudeCorrection` (Case A) | `REQ-TRK-047.2` | Specified |
| `TST-TRK-047.4` | Math Unit | `LiveWorkoutSession.applyRetroactiveAltitudeCorrection` (Case B) | `REQ-TRK-047.2`, `REQ-TRK-047.3` | Specified |
| `TST-TRK-047.5` | Localization | `TranslationParityTest` | `REQ-TRK-047.4` | Specified |
