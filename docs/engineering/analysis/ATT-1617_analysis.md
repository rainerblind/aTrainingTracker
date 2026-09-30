# Stage 1 Analysis: ATT-1617

**Ticket**: [ATT-1617](https://atrainingtracker.atlassian.net/browse/ATT-1617)  
**Sub-task**: [ATT-1694](https://atrainingtracker.atlassian.net/browse/ATT-1694) (`[Analysis]`)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.5`  
**Branch**: `feature/ATT-1617`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-30  

---

## 1. Executive Summary & Problem Domain

### 1.1 Context & Traceability
In `aTrainingTracker`, "Lieblingsorte" (known start locations stored in `StartLocation2Altitude.db`) perform automated barometric altimeter calibration and geofencing via `AltitudeFromPressureDevice.java` and `KnownLocationsDatabaseManager.java`. 

Ticket [ATT-1399](https://atrainingtracker.atlassian.net/browse/ATT-1399) (`REQ-UI-183`) introduced the cockpit status badge `LocationCalibrationBadge.kt` to give athletes visual confirmation that their starting location has been identified and the barometric altimeter calibrated.

* **Existing Requirements Mapping**:
  - `REQ-UI-183`: Lieblingsorte: Live Cockpit Location & Barometer Calibration Feedback (verified by `TST-UI-136`).
  - `REQ-CON-011`: Barometric Cold-Start Baseline Protection.
  - `REQ-CON-013`: Barometric Altitude Sensor Initialization & Null-Safe Correction Dispatch.
  - `REQ-DAT-014`: Internet DEM Reference Altitude Retrieval, Spatial Caching & Legacy Location Healing.
  - `REQ-DAT-015`: Decoupled Barometric Altimeter Calibration and Authoritative Workout Start Counting.
* **Requirement Refinement Target**:
  - Refine `REQ-UI-183` to eliminate false calibration claims and establish bidirectional synchronization between `LocationCalibrationBadge` and `AltitudeFromPressureDevice`.

### 1.2 Problem Statement & Defect Manifestation
When an athlete opens the app at a recognized favorite location (e.g., "Zu Hause", 436 m), the cockpit status badge prominently displays:
`📍 Zu Hause (436 m kalibriert)`

This gives the athlete the impression that the barometric altimeter has been calibrated to 436 m. However, inspecting the telemetry source dialog ("Höhe") reveals:
* **Luftdruck (barometric altimeter)**: **366 m** (uncalibrated standard atmosphere: 1013.25 hPa default!)
* **Smartphone GPS (Satellit)**: 489 m (nicht verbunden)
* **Google Standort (Fused)**: 486 m (connected)

There is a severe 70 m discrepancy between what the UI claims ("436 m kalibriert") and what the actual barometric sensor telemetry measures ("366 m").

---

## 2. Forensic Root Cause Analysis (RCA)

A forensic investigation across the presentation, repository, and sensor device layers revealed three compounding defects:

### 2.1 Unidirectional Geofence Detection Without Calibration Dispatch
In `TrackingTabsViewModel.kt` (lines 110–150):
```kotlin
val locationCalibrationStatus: StateFlow<LocationCalibrationStatus?> = ...
    closestItem?.let {
        LocationCalibrationStatus(
            locationId = it.id,
            locationName = it.name,
            referenceAltitude = it.altitude,
            isCalibrated = true,
            source = it.source
        )
    }
```
`TrackingTabsViewModel` combines `banalServiceRepository.currentLocation` with `knownLocationsRepo.locationsFlow`. If the athlete's coordinates fall within the geofence radius of a known location, `LocationCalibrationStatus` is constructed with `isCalibrated = true` hardcoded.
* It **never dispatches a calibration command** to `AltitudeFromPressureDevice`, `DeviceManager`, or `BANALService`.
* It assumes that finding a matching geofence means the sensor is calibrated, without verifying whether `AltitudeFromPressureDevice` has actually applied the offset.

### 2.2 Startup Race Condition & Location Availability Gap in `AltitudeFromPressureDevice.java`
In `AltitudeFromPressureDevice.java` (lines 77–81, 125–158):
```java
private final BroadcastReceiver mGPSProviderEnabledReceiver = new BroadcastReceiver() {
    public void onReceive(Context context, Intent intent) {
        AltitudeFromPressureDevice.this.initPressureSensor();
    }
};

private void initPressureSensor() {
    if (!Double.isNaN(mLastRawAltitude)
            && mMySensorManager.getSensor(SensorType.LATITUDE) != null
            && mMySensorManager.getSensor(SensorType.LONGITUDE) != null
            && mMySensorManager.getSensor(SensorType.LATITUDE).getValue() != null
            && mMySensorManager.getSensor(SensorType.LONGITUDE).getValue() != null
            && mAltitudeSensor != null) {
        mPressureSensorInitialized = true;
        ...
```
1. `AltitudeFromPressureDevice` only listens for `BANALService.LOCATION_AVAILABLE_INTENT`. It does not listen to `BANALService.NEW_LOCATION_INTENT`.
2. In `SpeedAndLocationDevice.java` (lines 136–146):
   ```java
   if (location.getAccuracy() <= accuracyThreshold) {
       LocationAvailable();
       mLongitudeSensor.newValue(location.getLongitude());
       mLatitudeSensor.newValue(location.getLatitude());
   ```
   `LocationAvailable()` broadcasts `LOCATION_AVAILABLE_INTENT` **before** `mLatitudeSensor.newValue()` is invoked.
3. When `AltitudeFromPressureDevice` receives the broadcast, `mMySensorManager.getSensor(SensorType.LATITUDE).getValue()` is still `null`.
4. `initPressureSensor()` aborts. Because `LocationAvailable` is now set to `true`, `LOCATION_AVAILABLE_INTENT` is never broadcast again.
5. In addition, when `Google Standort (Fused)` provides the location while `GPS (Satellit)` is searching indoors, `mMySensorManager.getSensor(SensorType.LATITUDE)` points to the uninitialized GPS device (due to `LOCATION_PRIORITY`), returning `null` even though `Google Standort` has valid coordinates.
6. As a result, `AltitudeFromPressureDevice` remains with `mAltitudeCorrection = 0.0` and `mPressureSensorInitialized = false`.

### 2.3 UI Badge Disregards `isCalibrated` State
In `LocationCalibrationBadge.kt` (lines 83–87):
```kotlin
val text = stringResource(
    R.string.location_calibrated_format,
    status.locationName,
    formattedAltitude
)
```
Even though `LocationCalibrationStatus` includes the boolean field `isCalibrated` (defaulting to `true`), `LocationCalibrationBadge.kt` completely ignores `status.isCalibrated` and unconditionally formats the string as:
`%1$s (%2$s kalibriert)` / `%1$s (%2$s calibrated)`
There is no distinction between "Location recognized / sensor uncalibrated or warming up" and "Altimeter baseline calibrated to ground truth".

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Objectives**:
  1. **Direct Calibration Dispatch**:
     - Expose `calibrateAltimeter(referenceAltitude: Double): Boolean` through `BANALServiceComm`, `BANALService`, `DeviceManager`, and `AltitudeFromPressureDevice`.
     - In `TrackingTabsViewModel.kt`, whenever a known location geofence is recognized, invoke `banalServiceRepository.calibrateAltimeter(closestItem.altitude)`.
  2. **Pending Calibration Handling on Sensor Warmup**:
     - In `AltitudeFromPressureDevice.java`, support pending reference altitude (`mPendingReferenceAltitude`). If `calibrate()` is invoked before the first pressure measurement arrives (`Double.isNaN(mLastRawAltitude)`), cache the target elevation and apply it atomically as soon as the first pressure reading arrives in `handlePressureMeasurement()`.
  3. **Verification of Calibration State**:
     - Expose `isAltimeterCalibrated: StateFlow<Boolean>` in `BANALServiceRepository.kt`.
     - In `AltitudeFromPressureDevice.java`, maintain an explicit `mIsCalibrated` flag that is set to `true` when `setAltitudeCorrection()` executes, and exposed via `isCalibrated()`.
     - In `TrackingTabsViewModel.kt`, populate `LocationCalibrationStatus.isCalibrated` using `isAltimeterCalibrated`.
  4. **Standby vs. Calibrated Badge UI Presentation**:
     - In `LocationCalibrationBadge.kt`, check `status.isCalibrated`:
       - If `true`: display `location_calibrated_format` ("Zu Hause (436 m kalibriert)").
       - If `false`: display `location_detected_format` ("Zu Hause erkannt" / "Home detected").
  5. **Localization Parity Across 9 Locales**:
     - Add `location_detected_format` across all 9 application locales: English (values), German (values-de), Spanish (values-es), French (values-fr), Italian (values-it), Japanese (values-ja), Dutch (values-nl), Polish (values-pl), Portuguese (values-pt).
  6. **Comprehensive Unit Testing**:
     - Unit tests verifying calibration dispatch, pending warmup application, state flow synchronization, and UI badge rendering.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  - Modifying SQLite table schema in `KnownLocationsDatabaseManager.java`.
  - Mutating workout start hit counting logic (`REQ-DAT-015` in `TrackerService.java`).
  - Altering route clustering, auto-naming (`ATT-1398`), or map polyline rendering.

---

## 4. Chesterton's Fence & Requirement Archaeology

### 4.1 Archaeology Trace
1. **Original Requirement ID & Target**: `REQ-UI-183` (*Lieblingsorte: Live Cockpit Location & Barometer Calibration Feedback*), implemented in `TrackingTabsViewModel.kt`, `LocationCalibrationBadge.kt`, `ControlTrackingScreen.kt`.
2. **Historical Origin & Commit Trace**: `ATT-1399` (`feat(cockpit): add live favorite location feedback badge and calibration status (ATT-1399, ATT-1572)`).
3. **Root Reason for Existing Formulation**: The original implementation treated the cockpit badge purely as an advisory UI view reacting to GPS coordinates matching `KnownLocationsRepository`, assuming the sensor service was already handling calibration autonomously under the hood.
4. **Refinement Reason**: Eliminate false calibration claims by establishing a closed-loop control and verification pipeline: geofence recognition dispatches calibration to `AltitudeFromPressureDevice`, and the badge only claims "kalibriert" once the altimeter has confirmed the offset application.
5. **Preservation of Core Invariants**:
   - `LocationCalibrationBadge` placement on `ControlTrackingScreen.kt` and exclusion from data tabs is preserved.
   - Smooth `AnimatedVisibility` enter/exit transitions are preserved.
   - Metric/imperial unit formatting is preserved.
   - Single-thread SQLite concurrency in `KnownLocationsDatabaseManager` is preserved.
   - Broadcast intent `ALTITUDE_CORRECTION_INTENT` with `ALTITUDE_CORRECTION_VALUE` is preserved.

---

## 5. Architectural Strategy & Interaction Diagram

```
┌────────────────────────────────────────────────────────────────────────────────┐
│                           Tracking Cockpit (UI Layer)                          │
├────────────────────────────────────────────────────────────────────────────────┤
│  LocationCalibrationBadge.kt                                                   │
│    ├── status.isCalibrated == true  -> "%1$s (%2$s kalibriert)"               │
│    └── status.isCalibrated == false -> "%1$s erkannt" (Standby / Warmup)       │
└───────────────────────────────────────▲────────────────────────────────────────┘
                                        │
                                        │ StateFlow<LocationCalibrationStatus?>
┌───────────────────────────────────────┴────────────────────────────────────────┐
│  TrackingTabsViewModel.kt                                                      │
│    ├── Combines: currentLocation + locationsFlow + isAltimeterCalibrated       │
│    └── On geofence match: banalServiceRepository.calibrateAltimeter(altitude) │
└───────────────────────────────────────┬────────────────────────────────────────┘
                                        │
                                        │ calibrateAltimeter(referenceAltitude)
┌───────────────────────────────────────▼────────────────────────────────────────┐
│  BANALServiceRepository.kt & BANALService.java                                 │
│    ├── Exposes: isAltimeterCalibrated: StateFlow<Boolean>                      │
│    └── Delegates: serviceBinder.calibrateAltimeter(referenceAltitude)         │
└───────────────────────────────────────┬────────────────────────────────────────┘
                                        │
                                        │ calibrateAltimeter(referenceAltitude)
┌───────────────────────────────────────▼────────────────────────────────────────┐
│  DeviceManager.java -> AltitudeFromPressureDevice.java                         │
│    ├── calibrate(referenceAltitude):                                           │
│    │     - If mLastRawAltitude ready: apply mAltitudeCorrection immediately,   │
│    │       mIsCalibrated = true, broadcast ALTITUDE_CORRECTION_INTENT          │
│    │     - If mLastRawAltitude NaN (warmup): set mPendingReferenceAltitude     │
│    └── handlePressureMeasurement(): apply pending calibration on first reading │
└────────────────────────────────────────────────────────────────────────────────┘
```

---

## 6. Next Steps & Stage 2 Transition
Upon Gate 1 audit approval of this analysis deliverable:
1. Transition sub-task [ATT-1694](https://atrainingtracker.atlassian.net/browse/ATT-1694) to `Review` and run `tools/review_agent.py audit ATT-1694`.
2. Move to Stage 2 (Requirement & Test Specification):
   - Update `REQ-UI-183` in `docs/requirements.md` (or specify refined `REQ-UI-199`).
   - Specify test cases in `docs/tests.md` (`TST-UI-153`).
   - Define exact Given-When-Then acceptance criteria and 9-language localization strings.
