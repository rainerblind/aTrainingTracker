# Stage 3 Implementation Plan: ATT-1617

**Ticket**: [ATT-1617](https://atrainingtracker.atlassian.net/browse/ATT-1617)  
**Sub-task**: [ATT-1696](https://atrainingtracker.atlassian.net/browse/ATT-1696) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.5`  
**Branch**: `feature/ATT-1617`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-30  

---

## 1. Architecture Overview (SWE.2)

To eliminate false altimeter calibration claims and ensure strict synchronization between `LocationCalibrationBadge` and `AltitudeFromPressureDevice`, this plan establishes a closed-loop control and state observation pipeline across five architectural tiers:

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Presentation (UI) Tier                          │
│  LocationCalibrationBadge.kt                                           │
│    • isCalibrated == true  -> location_calibrated_format ("...kalibriert")│
│    • isCalibrated == false -> location_detected_format ("...erkannt") │
└───────────────────────────────────▲────────────────────────────────────┘
                                    │ StateFlow<LocationCalibrationStatus?>
┌───────────────────────────────────┴────────────────────────────────────┐
│                       ViewModel State Machine                          │
│  TrackingTabsViewModel.kt                                              │
│    • Combines: currentLocation + locationsFlow + isAltimeterCalibrated │
│    • Geofence Match: triggers banalServiceRepository.calibrateAltimeter│
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ calibrateAltimeter(referenceAltitude)
┌───────────────────────────────────▼────────────────────────────────────┐
│                          Repository Layer                              │
│  BANALServiceRepository.kt                                             │
│    • Exposes: isAltimeterCalibrated: StateFlow<Boolean>                │
│    • Method:  calibrateAltimeter(altitude: Double): Boolean            │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ BANALServiceComm Binder
┌───────────────────────────────────▼────────────────────────────────────┐
│                    Service & Device Management Tier                    │
│  BANALService.java -> DeviceManager.java                               │
│    • Delegates: calibrateAltimeter(referenceAltitude)                  │
│    • Queries:   isAltimeterCalibrated()                                │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
┌───────────────────────────────────▼────────────────────────────────────┐
│                      Sensor Hardware Driver Tier                       │
│  AltitudeFromPressureDevice.java                                       │
│    • calibrate(referenceAltitude):                                     │
│      - If mLastRawAltitude available: compute correction, update sensor│
│        value, set mIsCalibrated = true, broadcast ALTITUDE_CORRECTION  │
│      - If warmup (NaN): cache mPendingReferenceAltitude                │
│    • handlePressureMeasurement(): apply pending calibration on 1st tick│
│    • isCalibrated(): returns boolean mIsCalibrated                     │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Invariants & Rollback Safety

1. **Decoupled Placement Invariant**: `LocationCalibrationBadge` remains strictly anchored on `ControlTrackingScreen.kt` and excluded from live data grid tabs.
2. **Animation Invariant**: `AnimatedVisibility` (fade and expand/shrink) transitions remain smooth and non-blocking.
3. **Unit Formatting Invariant**: Imperial (feet) and Metric (meters) unit formatting must be preserved.
4. **Single-Thread SQLite Invariant**: All queries and mutations in `KnownLocationsDatabaseManager` execute strictly on `KnownLocationsDB-Thread`.
5. **Broadcast Contract Invariant**: `ALTITUDE_CORRECTION_INTENT` with `ALTITUDE_CORRECTION_VALUE` must be dispatched whenever calibration adjusts the barometric offset.

---

## 3. Atomic Implementation Steps

### Task 1: Sensor Driver Enhancement (`AltitudeFromPressureDevice.java`)
- **Target File**: `app/src/main/java/com/atrainingtracker/banalservice/devices/AltitudeFromPressureDevice.java`
- **Modifications**:
  1. Add calibration tracking fields:
     - `private double mPendingReferenceAltitude = Double.NaN;`
     - `private boolean mIsCalibrated = false;`
  2. Implement public synchronized method:
     ```java
     public synchronized boolean calibrate(double referenceAltitude) {
         if (!Double.isNaN(mLastRawAltitude)) {
             setAltitudeCorrection(referenceAltitude);
             mPressureSensorInitialized = true;
             mIsCalibrated = true;
             mPendingReferenceAltitude = Double.NaN;
             if (mAltitudeSensor != null) {
                 mAltitudeSensor.newValue(mLastRawAltitude + mAltitudeCorrection);
             }
             return true;
         } else {
             mPendingReferenceAltitude = referenceAltitude;
             return false;
         }
     }
     ```
  3. Implement public synchronized getter:
     ```java
     public synchronized boolean isCalibrated() {
         return mIsCalibrated;
     }
     ```
  4. In `handlePressureMeasurement(float pressureHpa)`:
     - Compute `mLastRawAltitude`.
     - If `!Double.isNaN(mPendingReferenceAltitude)`: invoke `calibrate(mPendingReferenceAltitude)`.
     - Else if `!mPressureSensorInitialized`: invoke `initPressureSensor()`.
     - Update `mAltitudeSensor.newValue(mLastRawAltitude + mAltitudeCorrection)`.
  5. In `setAltitudeCorrection(double correctAltitude)`:
     - Set `mIsCalibrated = true;`.
  6. In `shutDown()`:
     - Reset `mPendingReferenceAltitude = Double.NaN;`, `mIsCalibrated = false;`.

### Task 2: Service & Device Manager Delegation (`DeviceManager.java`, `BANALService.java`)
- **Target Files**:
  - `app/src/main/java/com/atrainingtracker/banalservice/devices/DeviceManager.java`
  - `app/src/main/java/com/atrainingtracker/banalservice/BANALService.java`
- **Modifications**:
  1. In `DeviceManager.java`:
     - `public boolean calibrateAltimeter(double referenceAltitude)`: delegates to `mAltitudeFromPressureDevice.calibrate(referenceAltitude)`.
     - `public boolean isAltimeterCalibrated()`: returns `mAltitudeFromPressureDevice != null && mAltitudeFromPressureDevice.isCalibrated()`.
  2. In `BANALService.java` (`BANALServiceComm` inner class):
     - `public boolean calibrateAltimeter(double referenceAltitude)`: delegates to `cDeviceManager.calibrateAltimeter(...)`.
     - `public boolean isAltimeterCalibrated()`: delegates to `cDeviceManager.isAltimeterCalibrated()`.

### Task 3: Reactive Repository Stream (`BANALServiceRepository.kt`)
- **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/repositories/BANALServiceRepository.kt`
- **Modifications**:
  1. Add StateFlow:
     - `private val _isAltimeterCalibrated = MutableStateFlow(false)`
     - `val isAltimeterCalibrated: StateFlow<Boolean> = _isAltimeterCalibrated.asStateFlow()`
  2. In the 1-second pulse observation loop:
     - Update `_isAltimeterCalibrated.value = binder.isAltimeterCalibrated()`
  3. Add dispatch method:
     ```kotlin
     fun calibrateAltimeter(altitude: Double): Boolean {
         val binder = serviceBinder ?: return false
         val success = binder.calibrateAltimeter(altitude)
         _isAltimeterCalibrated.value = binder.isAltimeterCalibrated()
         return success
     }
     ```

### Task 4: Closed-Loop ViewModel Calibration Dispatch (`TrackingTabsViewModel.kt`)
- **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsViewModel.kt`
- **Modifications**:
  1. Combine `banalServiceRepository.currentLocation`, `knownLocationsRepo.locationsFlow`, and `banalServiceRepository.isAltimeterCalibrated`.
  2. When `closestItem != null`:
     - Invoke `banalServiceRepository.calibrateAltimeter(closestItem.altitude)`.
     - Emit `LocationCalibrationStatus(..., isCalibrated = isAltimeterCalibrated, ...)`.
  3. When `closestItem == null`:
     - Emit `null`.

### Task 5: Two-State UI Presentation (`LocationCalibrationBadge.kt`)
- **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/LocationCalibrationBadge.kt`
- **Modifications**:
  1. Branch on `status.isCalibrated`:
     - If `true`: format with `R.string.location_calibrated_format` (e.g. `"%1$s (%2$s kalibriert)"`).
     - If `false`: format with `R.string.location_detected_format` (e.g. `"%1$s erkannt"`).

### Task 6: 9-Language Localization Parity
- **Target Files**:
  - `app/src/main/res/values/strings.xml` (`"%1$s detected"`)
  - `app/src/main/res/values-de/strings.xml` (`"%1$s erkannt"`)
  - `app/src/main/res/values-es/strings.xml` (`"%1$s detectado"`)
  - `app/src/main/res/values-fr/strings.xml` (`"%1$s détecté"`)
  - `app/src/main/res/values-it/strings.xml` (`"%1$s rilevato"`)
  - `app/src/main/res/values-ja/strings.xml` (`"%1$s を検出"`)
  - `app/src/main/res/values-nl/strings.xml` (`"%1$s gedetecteerd"`)
  - `app/src/main/res/values-pl/strings.xml` (`"%1$s wykryty"`)
  - `app/src/main/res/values-pt/strings.xml` (`"%1$s detectado"`)

### Task 7: Comprehensive Unit Testing (`TST-UI-153`)
- **Target Test Files**:
  1. `app/src/test/java/com/atrainingtracker/banalservice/devices/AltitudeFromPressureDeviceTest.kt`:
     - Test direct calibration when raw altitude is available.
     - Test pending calibration during sensor warmup.
     - Test `isCalibrated()` state transitions.
  2. `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsViewModelLocationTest.kt`:
     - Test geofence entry emits `isCalibrated = false` when altimeter is uncalibrated.
     - Test geofence entry dispatches `calibrateAltimeter(altitude)`.
     - Test `isAltimeterCalibrated = true` transition updates status.
  3. `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/LocationCalibrationBadgeTest.kt`:
     - Test rendered text for calibrated status.
     - Test rendered text for standby (detected) status.
  4. `app/src/test/java/com/atrainingtracker/trainingtracker/localization/LocationCalibrationLocalizationTest.kt`:
     - Test 9-locale parity for `location_detected_format`.

---

## 4. Verification & Clean-Room Regression Command
```bash
./gradlew testDebugUnitTest
```
Assert 100% test pass rate with zero regressions across all modules.
