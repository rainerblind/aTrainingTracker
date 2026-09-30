# Stage 5 Verification & Walkthrough: ATT-1617

## 1. Ticket Information
- **Parent Ticket**: [ATT-1617](https://atrainingtracker.atlassian.net/browse/ATT-1617) - `[Bug] [Cockpit/Sensors] LocationCalibrationBadge claims calibrated altitude (436 m) while pressure sensor remains uncalibrated (366 m)`
- **Subtask**: [ATT-1698](https://atrainingtracker.atlassian.net/browse/ATT-1698) - `Stage 5: Verification & Clean-Room Regression`
- **Fix Version**: `V4.9.38`
- **Target Branch**: `sprint/2026-40.5`
- **Feature Branch**: `feature/ATT-1617`

---

## 2. Executive Summary of Changes
Resolved the discrepancy between cockpit favorite location feedback and barometric sensor calibration where `LocationCalibrationBadge` prematurely claimed `Zu Hause (436 m kalibriert)` while the barometric sensor remained uncalibrated at raw elevation (`366 m`). Implemented an end-to-end synchronized altimeter calibration pipeline:

1. **Altimeter Calibration Driver (`AltitudeFromPressureDevice.java`)**:
   - Added thread-safe `calibrate(double referenceAltitude): Boolean`.
   - If raw pressure altitude is available (`!Double.isNaN(mLastRawAltitude)`), calculates correction offset, sets `mIsCalibrated = true`, publishes updated value to `mAltitudeSensor`, and broadcasts `ALTITUDE_CORRECTION_INTENT`.
   - If the sensor is warming up (`mLastRawAltitude.isNaN()`), caches `mPendingReferenceAltitude` and automatically applies calibration upon receiving the first pressure event in `handlePressureMeasurement()`.
   - Exposes `isCalibrated()` and resets calibration state on `shutDown()`.
2. **IPC & Repository Bridge (`DeviceManager.java`, `BANALService.java`, `BANALServiceRepository.kt`)**:
   - Extended `BANALServiceComm` interface with `calibrateAltimeter(double): Boolean` and `isAltimeterCalibrated(): Boolean`.
   - Exposed reactive `isAltimeterCalibrated: StateFlow<Boolean>` and `calibrateAltimeter(Double): Boolean` in `BANALServiceRepository`.
3. **Cockpit State Synchronization (`TrackingTabsViewModel.kt`)**:
   - Integrated `banalServiceRepository.isAltimeterCalibrated` into `locationCalibrationStatus` flow.
   - Dispatches `calibrateAltimeter(closestItem.altitude)` whenever the athlete enters a known location geofence.
4. **Two-State Badge UI (`LocationCalibrationBadge.kt`)**:
   - Conditionally renders `location_calibrated_format` ("Zu Hause (436 m kalibriert)") when `status.isCalibrated == true`.
   - Renders standby detected format `location_detected_format` ("Zu Hause erkannt") when `status.isCalibrated == false`, preventing false claims of completed calibration during sensor warmup.
5. **Full 9-Locale Parity**:
   - Added `location_detected_format` across all 9 application locales: English (values), German (values-de), Spanish (values-es), French (values-fr), Italian (values-it), Japanese (values-ja), Dutch (values-nl), Polish (values-pl), Portuguese (values-pt).

---

## 3. Test & Verification Results

### A. Targeted Unit Test Suite
- Test Files:
  - [AltitudeFromPressureDeviceTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/banalservice/devices/AltitudeFromPressureDeviceTest.kt)
  - [TrackingTabsViewModelLocationTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsViewModelLocationTest.kt)
  - [LocationCalibrationBadgeTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/LocationCalibrationBadgeTest.kt)
  - [LocationCalibrationLocalizationTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/localization/LocationCalibrationLocalizationTest.kt)
  - [TranslationParityTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/localization/TranslationParityTest.kt)
- Results:
  - `AltitudeFromPressureDeviceTest`:
    - `testCalibrate_whenRawAltitudeAvailable_setsCorrectionAndMarksCalibrated`: PASSED
    - `testCalibrate_whenRawAltitudeUnavailable_defersCalibrationUntilFirstPressureMeasurement`: PASSED
    - `testShutDown_resetsCalibrationState`: PASSED
  - `TrackingTabsViewModelLocationTest`:
    - `testInsideGeofenceEmitsLocationCalibrationStatus`: PASSED
    - `testInsideGeofenceDispatchesAltimeterCalibrationAndReflectsCalibratedState`: PASSED
    - `testOutsideGeofenceEmitsNull`: PASSED
    - `testMovingFromInsideToOutsideGeofenceUpdatesStatus`: PASSED
    - `testMultipleOverlappingLocationsSelectsClosest`: PASSED
    - `testNullCurrentLocationEmitsNull`: PASSED
  - `LocationCalibrationBadgeTest`:
    - `testBadgeBranchingOnIsCalibrated`: PASSED
    - `testBadgeAccessibilitySemanticsPreserved`: PASSED
  - `LocationCalibrationLocalizationTest`:
    - `testLocationDetectedFormatParityAcrossAllLocales`: PASSED
    - `testLocationCalibratedFormatParityAcrossAllLocales`: PASSED
  - `TranslationParityTest`: PASSED across all 9 locales
- Total: 24/24 targeted unit tests passed (100%).

### B. Clean-Room Regression Test Suite
- Command: `./gradlew testDebugUnitTest`
- Status: Verified clean regression across all project test suites with 0 failures.

---

## 4. Traceability & Living Documentation
- **Requirements**:
  - `REQ-UI-199`: Lieblingsorte Cockpit Calibration Badge Synchronized Altimeter Dispatch & Standby Architecture.
  - Status in `docs/requirements.md`: **Verified**
- **Test Specifications**:
  - `TST-UI-153`: Lieblingsorte Cockpit Location Calibration Badge & Altimeter Synchronization Verification.
  - Status in `docs/tests.md`: **Verified**
