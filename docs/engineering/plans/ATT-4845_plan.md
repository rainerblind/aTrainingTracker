# Stage 3: Implementation Plan - ATT-4845: Manual Altitude Calibration with Known Location Persistence and Retroactive Linear Drift Correction

**Ticket**: [ATT-4845](https://rainerblind.atlassian.net/browse/ATT-4845) | **Sub-task**: Pending (`[Impl-Plan]`) | **Parent Epic**: ATT-166 (Filtering to improve sensor values)  
**Target Release**: `V4.9.41` | **Active Sprint**: `2026-44.2` | **Branch**: `feature/ATT-4845`  
**Requirement Mapping**: `REQ-TRK-047` (`REQ-TRK-047.1`..`4`) | **Test Mapping**: `TST-TRK-047` (`TST-TRK-047.1`..`5`)  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-01  

---

## 1. Problem Description & Background
Barometric altimeters drift over long outdoor sessions due to weather pressure shifts. When passing known elevation markers, athletes need to enter reference altitude from the tracking cockpit, persist it to `KnownLocations.db`, and immediately recalibrate the altimeter. Crucially, the recorded elevation history must be corrected retroactively: via uniform shift if previously uncalibrated (Case A), or piecewise-linear drift interpolation between prior and current calibration points (Case B), eliminating step discontinuities and false ascent/descent spikes.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-TRK-047` (`REQ-TRK-047.1` Cockpit Calibration UI, `.2` Retroactive Elevation Engine, `.3` IPC Contract, `.4` Localization)
* **Test Mapping**: `TST-TRK-047` (`TST-TRK-047.1` Dialog UI, `.2` DB Upsert, `.3` Case A Shift, `.4` Case B Drift, `.5` Localization Parity)
* **Standards**: Rule 36 (Canonical `AltitudeFormatter` & unit parity), Rule 66 (Zero snapshot state writes during composition, test isolation).

---

## 3. System Invariants & Preserved Behavior
1. **$C^0$ Continuity**: Linear drift interpolation over $[t_0, t_1]$ guarantees zero vertical jumps or false ascent/descent spikes.
2. **IPC Contract & Limiting**: `ALTITUDE_CORRECTION_INTENT` retains `ALTITUDE_CORRECTION_VALUE` ($\Delta h$) guarded by $|\Delta h| \le 500\text{ m}$ (`REQ-CON-017`), adding timestamp extras `PREV_CALIBRATION_TIME_MS_EXTRA` and `CALIBRATION_TIME_MS_EXTRA`.
3. **Unit Parity & Storage**: Raw meters in SQLite and IPC; user preferences (`m`/`ft`) evaluated exclusively via `AltitudeFormatter`.
4. **UI Design Tokens**: Preserves `SensorSourceDialogContractTest` styling tokens (no route tokens) and Compose snapshot safety.

---

## 4. Proposed Architectural Changes
* **Sensor Driver Layer (`AltitudeFromPressureDevice.java`)**: Track `mLastCalibrationTimeMs`. Broadcast `ALTITUDE_CORRECTION_INTENT` with `deltaOffset`, `PREV_CALIBRATION_TIME_MS_EXTRA` ($t_0$), and `CALIBRATION_TIME_MS_EXTRA` ($t_1$). Update published sensor reading.
* **Tracking & Session Engine (`TrackerService.java`, `LiveWorkoutSession.java`, `BANALServiceRepository.kt`)**:
  * In `LiveWorkoutSession.java`, implement `applyRetroactiveAltitudeCorrection(deltaOffset, t0Ms, t1Ms)`: Case A shifts all sampled altitudes and extrema; Case B applies linear interpolation $\Delta h \cdot \frac{t - t_0}{t_1 - t_0}$ over $[t_0, t_1]$, updating running stats and recomputing ascent/descent.
  * In `TrackerService.java`, execute atomic SQLite updates on `WorkoutSamples.db` (Case A uniform shift vs. Case B piecewise-linear update) on `mDbExecutor`. Update `WorkoutSummaries.db` (`ASCENDING`, `DESCENDING`, extrema, and stream).
  * In `BANALServiceRepository.kt`, apply matching elevation correction to `_currentPathPoints` for live cockpit elevation profile parity.
* **Cockpit UI & Known Locations (`SensorSourceDialog.kt`, `TrackingTabsViewModel.kt`)**:
  * In `SensorSourceDialog.kt`, add "Calibrate Altitude" section when `sensorType == SensorType.ALTITUDE`: pre-fill formatted elevation, location name, and save toggle.
  * In `TrackingTabsViewModel.kt`, handle calibration callback: invoke `KnownLocationsDatabaseManager.upsertLocationByGeofence` with `ElevationSource.MANUAL_USER` (`isLocked = true`) and dispatch `banalServiceRepository.calibrateAltimeter`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Timestamped Calibration Intent in Sensor Driver (`REQ-TRK-047.3`)
* Files: `AltitudeFromPressureDevice.java`
* Add `PREV_CALIBRATION_TIME_MS_EXTRA` and `CALIBRATION_TIME_MS_EXTRA` constants. Maintain `mLastCalibrationTimeMs`. Include timestamps in `ALTITUDE_CORRECTION_INTENT`.

### Step 2: Retroactive Drift Engine in Live Session & Service (`REQ-TRK-047.2`)
* Files: `LiveWorkoutSession.java`, `TrackerService.java`, `WorkoutSummariesDatabaseManager.java`, `BANALServiceRepository.kt`
* Implement Case A (uniform shift) and Case B (piecewise-linear interpolation) across `LiveWorkoutSession`, `samplesTable`, `_currentPathPoints`, and recomputed `ASCENDING` / `DESCENDING` summary extrema.

### Step 3: Cockpit Header Altitude Calibration Dialog UI (`REQ-TRK-047.1`, `REQ-TRK-047.4`)
* Files: `SensorSourceDialog.kt`, `SensorStatus.kt`, `TrackingTabsScreen.kt`, `TrackingTabsViewModel.kt`
* Add calibration section to `SensorSourceDialog`, wiring callbacks to `KnownLocationsDatabaseManager.upsertLocationByGeofence` and `calibrateAltimeter`. Enforce Rule 66 (no state writes in composition).

### Step 4: Unit & Contract Tests (`TST-TRK-047.1`..`TST-TRK-047.5`)
* Files: `SensorSourceDialogAltitudeCalibrationTest.kt`, `KnownLocationsManualCalibrationTest.kt`, `LiveWorkoutSessionAltitudeDriftTest.kt`, `AltitudeFromPressureDeviceTest.kt`
* Author comprehensive tests verifying UI dispatch, DB persistence, Case A/B math, and IPC contracts.

---

## 6. Verification & Test Plan
* `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.tracker.LiveWorkoutSessionAltitudeDriftTest"`
* `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.KnownLocationsManualCalibrationTest"`
* `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.SensorSourceDialog*"`
* `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.AltitudeFromPressureDeviceTest"`
* `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`
* `python3 tools/verify_requirement_governance.py`
