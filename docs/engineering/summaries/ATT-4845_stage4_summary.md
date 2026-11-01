h2. Stage 4 Implementation Summary: ATT-4845

*Ticket*: ATT-4845 (Manual altitude calibration from header Altitude icon with permanent Known Location persistence and retroactive linear drift correction)
*Requirements*: REQ-TRK-047 (REQ-TRK-047.1, REQ-TRK-047.2, REQ-TRK-047.3, REQ-TRK-047.4, REQ-TRK-047.5)
*Tests*: TST-TRK-047 (TST-TRK-047.1, TST-TRK-047.2, TST-TRK-047.3, TST-TRK-047.4, TST-TRK-047.5)

h3. Construction Summary:
1. *Sensor Driver Layer (`AltitudeFromPressureDevice.java`)*:
- Added `PREV_CALIBRATION_TIME_MS_EXTRA` and `CALIBRATION_TIME_MS_EXTRA` to `ALTITUDE_CORRECTION_INTENT`.
- Maintained `mLastCalibrationTimeMs` for interval tracking (t0 -> t1) and implemented reset and accessor helpers.
- Updated unit test stubbing in `AltitudeFromPressureDeviceTest.kt`.

2. *Tracking & Session Engine (`LiveWorkoutSession.java`, `WorkoutSamplesDatabaseManager.java`, `WorkoutSummariesDatabaseManager.java`, `TrackerService.java`, `BANALServiceRepository.kt`)*:
- Implemented `LiveWorkoutSession.applyRetroactiveAltitudeCorrection(deltaOffset, t0Ms, t1Ms)`:
  - Case A (t0 <= 0): Uniform shift by +deltaOffset on all sampled points and extrema.
  - Case B (t0 > 0): Piecewise-linear interpolation:
    h_corr(t) = h(t) + deltaOffset * ((t - t0) / (t1 - t0)) for t in [t0, t1], and +deltaOffset for t > t1.
  - Dynamically recomputed RunningStats min, max, sum, and count over updated samples.
- Implemented `WorkoutSamplesDatabaseManager.applyAltitudeDriftCorrection(samplesTable, deltaOffset, t0Ms, t1Ms)` with atomic SQLite transaction executing uniform or CAST(strftime('%s', time) AS REAL) linear interpolation.
- Implemented `WorkoutSummariesDatabaseManager.shiftAltitudeData(workoutId, offset, t0Ms, t1Ms)` recomputing stream encoding and Min/Max extrema.
- Updated `TrackerService.java` to extract calibration timestamps and dispatch atomic DB and session updates on `mDbExecutor`.
- Exposed `BANALServiceRepository.currentAltitude: StateFlow<Double?>` and synchronized `_currentPathPoints` elevation profiles under Case A/B drift correction.

3. *Cockpit UI & Known Locations (`SensorSourceDialog.kt`, `SensorStatus.kt`, `TrackingTabsScreen.kt`, `TrackingTabsViewModel.kt`, `KnownLocationsDatabaseManager.java`)*:
- Added Altitude Calibration Card to `SensorSourceDialog.kt` when `sensorType == SensorType.ALTITUDE && onCalibrateAltitude != null`.
- Pre-filled reference altitude using canonical `AltitudeFormatter` per Rule 36, added optional location name input, and save to Known Locations toggle.
- Handled metric and imperial unit conversions (`BANALService.METER_PER_FOOT`).
- Updated `KnownLocationsDatabaseManager.upsertLocationByGeofence` to allow updating existing locked locations when source is `ElevationSource.MANUAL_USER`.
- Implemented `TrackingTabsViewModel.calibrateAltitude(refAlt, locationName, saveAsKnownLocation)` and passed it through `TrackingTabsScreen.kt` and `SensorStatus.kt` (compact landscape and portrait modes).
- Full 9-language localization parity added for all 6 new strings.

4. *Verification & Tests*:
- `LiveWorkoutSessionAltitudeDriftTest.kt`: Verified Case A uniform shift and Case B linear interpolation + RunningStats recomputation (100% pass).
- `KnownLocationsManualCalibrationTest.kt`: Verified `upsertLocationByGeofence` with `ElevationSource.MANUAL_USER`, `isLocked = true`, and existing locked location updates (100% pass).
- `SensorSourceDialogAltitudeCalibrationTest.kt`: Verified UI contract, visibility, pre-fill formatting, unit conversions, and callback dispatch (100% pass).
- `SensorSourceDialogContractTest.kt`: Contract compliance verified without regressions.
- `TranslationParityTest.kt`: 100% string parity across all 9 supported locales.
- Full Tier 1 test suite ran across all relevant modules: BUILD SUCCESSFUL (32 tasks, 0 failures).
- Requirement governance: `verify_requirement_governance.py` passed with clean net-new detection for REQ-TRK-047.
