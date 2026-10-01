# Stage 3: Implementation Plan - ATT-1810: Super implausible altitude

**Ticket**: [ATT-1810](https://rainerblind.atlassian.net/browse/ATT-1810)  
**Sub-task**: [ATT-1893](https://rainerblind.atlassian.net/browse/ATT-1893) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*) / Location & Altimeter Tracking  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.7`  
**Requirement Mapping**: `REQ-CON-017` (*Barometric Altimeter Idempotent Calibration, Delta Shift Dispatch, Physical Sanity Limiting & Historical Workout Data Sanitization*)  
**Test Mapping**: `TST-CON-008`  
**Branch**: `feature/ATT-1810`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Problem Description & Background

On a Google Pixel 10 running Android 16, workout *"Kurz zum Bäcker #14"* (a 2.05 km ride in Schönaich) generated an extreme, non-physical elevation artifact: the elevation profile started at ~7300 m and descended in distinct staircase plateaus to 507 m.

Forensic root cause analysis identified a self-inflicted positive-feedback oscillation:
1. **Incremental Offset Defect in `AltitudeFromPressureDevice.java`**:
   `setAltitudeCorrection(double correctAltitude)` calculated `mAltitudeCorrection = correctAltitude - currentAltitude`, where `currentAltitude` was obtained from `mAltitudeSensor.getValue()`. Since `mAltitudeSensor.getValue()` was already corrected ($mLastRawAltitude + mAltitudeCorrection_{old}$), this computed the *incremental difference* instead of the absolute offset from raw barometric pressure. When invoked again with the same reference altitude, it evaluated to $507 - 507 = 0.0$. On subsequent pressure sensor updates (5–10 Hz), the reading fell back to raw altitude (e.g. 450 m). On the next calibration, it evaluated to $507 - 450 = +57\text{ m}$ and broadcasted `ALTITUDE_CORRECTION_INTENT` with $+57\text{ m}$.
2. **High-Frequency Polling in `TrackingTabsViewModel.kt`**:
   `banalServiceRepository.calibrateAltimeter` was invoked inside the `combine` flow of `locationCalibrationStatus`, executing on every 1 Hz GPS location fix whenever the athlete was located inside a known location geofence.
3. **Cumulative Retroactive Shift Accumulation in `TrackerService.java`**:
   Each `ALTITUDE_CORRECTION_INTENT` broadcast triggered `UPDATE samplesTable set ALTITUDE = ALTITUDE + 57` and `shiftAltitudeData(workoutId, 57)`. During a 2-minute warmup at home before departure, ~30 oscillations $\times$ 57 m accumulated $+1710\text{ m}$ to $+7300\text{ m}$ of cumulative retroactive shifts on early samples. Samples recorded after moving away escaped subsequent shifts. Arriving at the bakery geofence triggered a second burst of shifts, forming the exact descending staircase observed on device.
4. **Permanent Database Mutation & Visualization Impact**:
   Because `samplesTable`, `TABLE_EXTREMA_VALUES`, and `ALTITUDE_STREAM` were permanently written with 7300 m values, Aftermath profile rendering collapsed, min/max extrema became corrupted, and third-party GPX/TCX exports received non-physical altitudes.

This implementation plan details the atomic engineering steps to fix the barometric correction formula, make calibration idempotent, gate ViewModel calibration dispatch on geofence transitions, enforce physical sanity limits in tracking services, repair contaminated historical sessions in SQLite, and add defensive bounds filtering.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-CON-017` (*Barometric Altimeter Idempotent Calibration, Delta Shift Dispatch, Physical Sanity Limiting & Historical Workout Data Sanitization*)
* **Test Mapping**: `TST-CON-008`
  * `[TST-CON-008.1]`: Absolute baseline correction and delta broadcast in `AltitudeFromPressureDeviceTest.kt`
  * `[TST-CON-008.2]`: Repeated calls oscillation immunity (replicating "Kurz zum Bäcker #14" loop) in `AltitudeFromPressureDeviceTest.kt`
  * `[TST-CON-008.3]`: `calibrate` idempotency skipping redundant work in `AltitudeFromPressureDeviceTest.kt`
  * `[TST-CON-008.4]`: Geofence transition calibration gating in `TrackingTabsViewModelLocationTest.kt`
  * `[TST-CON-008.5]`: Historical corrupted workout sanitization in `WorkoutSummariesDatabaseManagerTest.kt`
  * `[TST-CON-008.6]`: Physical sanity limits clamping spikes and runaway retroactive offsets in `LiveWorkoutSessionTest.kt` / `TrackerServiceTest.kt`
  * `[TST-CON-008.7]`: Clean-room full suite regression (`./gradlew testDebugUnitTest`)

---

## 3. System Invariants & Preserved Behavior

1. **Cold-Start Null Safety (`REQ-CON-013`)**:
   Handling of uninitialized barometric sensor (`mLastRawAltitude` is NaN, `mAltitudeSensor.getValue()` is null) must continue to cache pending reference altitudes without NullPointerExceptions or SIGABRT crashes.
2. **Topographic DEM Reference & Immutability (`REQ-DAT-014`, `REQ-DAT-007`)**:
   Internet DEM elevation retrieval, 5-decimal quantization, spatial 200m caching, and locked location immutability (`is_locked = 1`) must remain fully intact.
3. **Cockpit Calibration Badge (`REQ-UI-199`)**:
   Visual feedback ("Zu Hause erkannt" vs "Zu Hause (436 m kalibriert)") must continue to reactively display calibration status based on `isAltimeterCalibrated`.
4. **Aftermath Synchronization & Bounds (`REQ-UI-201`, `REQ-UI-206`)**:
   Multi-metric scrubbing, cursor placement, and zoom mechanics must not be disrupted by bounds filtering.
5. **Human Decision Gate**:
   Subtask `ATT-1893` transitions to `Erledigt` upon Gate 3 approval. Terminal completion of parent ticket `ATT-1810` remains strictly reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `AltitudeFromPressureDevice.java` (Sensor Telemetry & Calibration)
1. **Absolute Offset Formulation in `setAltitudeCorrection`**:
   ```java
   public void setAltitudeCorrection(double correctAltitude) {
       if (Double.isNaN(mLastRawAltitude)) {
           Log.w(TAG, "Cannot set altitude correction: mLastRawAltitude is NaN");
           mPendingReferenceAltitude = correctAltitude;
           return;
       }
       // 1. Calculate new correction strictly relative to raw barometric pressure
       double newCorrection = correctAltitude - mLastRawAltitude;
       
       // 2. Calculate delta shift to broadcast to ongoing session
       double deltaOffset = newCorrection - mAltitudeCorrection;
       
       // 3. Commit absolute correction state
       mAltitudeCorrection = newCorrection;
       mIsCalibrated = true;
       
       // 4. Update sensor published value
       mAltitudeSensor.newValue(mLastRawAltitude + mAltitudeCorrection);
       
       // 5. Broadcast delta shift only if significant (>= 0.1m)
       if (Math.abs(deltaOffset) >= 0.1) {
           sendAltitudeCorrectionBroadcast(deltaOffset);
       }
   }
   ```
2. **Idempotency Guard in `calibrate`**:
   ```java
   public boolean calibrate(double referenceAltitude) {
       if (Double.isNaN(referenceAltitude) || referenceAltitude <= -500.0 || referenceAltitude >= 9000.0) {
           return false;
       }
       if (isCalibrated() && !Double.isNaN(mLastRawAltitude)) {
           double currentAltitude = mLastRawAltitude + mAltitudeCorrection;
           if (Math.abs(currentAltitude - referenceAltitude) < 0.5) {
               return true; // Already calibrated to this reference
           }
       }
       if (Double.isNaN(mLastRawAltitude)) {
           mPendingReferenceAltitude = referenceAltitude;
           return false;
       }
       setAltitudeCorrection(referenceAltitude);
       return true;
   }
   ```

### Component 2: `TrackingTabsViewModel.kt` (Cockpit Lifecycle Gating)
* Maintain `lastCalibratedLocationId: Long? = null` in ViewModel scope.
* In `locationCalibrationStatus` combine block:
  ```kotlin
  if (closestItem != null) {
      if (closestItem.id != lastCalibratedLocationId || !isCalibrated) {
          banalServiceRepository.calibrateAltimeter(closestItem.altitude)
          lastCalibratedLocationId = closestItem.id
      }
  } else {
      lastCalibratedLocationId = null
  }
  ```

### Component 3: `TrackerService.java` & `LiveWorkoutSession.java` (Session Storage & Sanity Limiting)
1. **Atomic Transaction Wrapper**:
   In `TrackerService.onReceive` for `ALTITUDE_CORRECTION_INTENT`:
   ```java
   mDb.beginTransaction();
   try {
       mDb.execSQL("UPDATE " + SAMPLES_TABLE + " SET " + ALTITUDE + " = " + ALTITUDE + " + " + correction + " WHERE " + WORKOUT_ID + " = " + workoutId);
       shiftAltitudeData(workoutId, correction);
       mDb.setTransactionSuccessful();
   } finally {
       mDb.endTransaction();
   }
   ```
2. **Retroactive Shift Clamping**:
   In `applyAltitudeCorrection(double offset)`:
   Reject or clamp single offsets where `Math.abs(offset) > 500.0` with warning log.
3. **Physical Rate of Climb Clamping**:
   In `LiveWorkoutSession.recordStreamPoint`:
   Clamp incoming altitudes if outside $[-500.0, 9000.0]$ or if $|dh/dt| > 30.0\text{ m/s}$ ($108\text{ km/h}$).

### Component 4: `WorkoutSummariesDatabaseManager.java` (Historical Data Healing)
* Implement `sanitizeCorruptedAltitudeWorkouts()`:
  - Query workouts where `extrema_max > 4000.0` and `extrema_min < 1000.0`.
  - For each matching workout:
    - Decode `ALTITUDE_STREAM`.
    - Detect leading runaway samples ($> 2000.0\text{ m}$ descending toward real ground $< 1000.0\text{ m}$).
    - Replace corrupted leading points with the true ground baseline.
    - Re-encode stream via `NumericalEncodingUtils`.
    - Update `TABLE_EXTREMA_VALUES` (recomputing MIN, MAX, AVG).
    - Sanitize `samplesTable` records inside an atomic SQLite transaction.

### Component 5: `ElevationProfile.kt` & Exporters (`GPXFileWriter.java`, `TCXFileWriter.java`)
1. In `ElevationProfile.kt` (`calculateElevationBounds`):
   Filter out non-physical start outliers ($> 4000\text{ m}$ when overall median $< 1000\text{ m}$) so rendering auto-scales to ground truth (~480–530 m).
2. In `GPXFileWriter.java` and `TCXFileWriter.java`:
   Defensively clamp altitude values $> 9000\text{ m}$ or vertical step jumps $> 100\text{ m}$ to the previous valid sample.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Refactor `AltitudeFromPressureDevice.java`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/devices/AltitudeFromPressureDevice.java`
* **Actions**:
  - Implement absolute correction calculation (`newCorrection = correctAltitude - mLastRawAltitude`).
  - Calculate `deltaOffset = newCorrection - mAltitudeCorrection`.
  - Guard broadcast dispatch with `Math.abs(deltaOffset) >= 0.1`.
  - Implement idempotency guard in `calibrate()`.

### Step 2: Gate Altimeter Calibration in `TrackingTabsViewModel.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/TrackingTabsViewModel.kt`
* **Actions**:
  - Add `lastCalibratedLocationId: Long?` property.
  - Gate `calibrateAltimeter` dispatch on geofence transition (`closestItem.id != lastCalibratedLocationId || !isCalibrated`).
  - Reset `lastCalibratedLocationId = null` when leaving geofence.

### Step 3: Enforce Atomic Transactions & Physical Limits in Tracking Service
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/service/TrackerService.java`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/service/LiveWorkoutSession.java`
* **Actions**:
  - Enclose sample shift SQLite statements in `beginTransaction()` / `setTransactionSuccessful()` / `endTransaction()`.
  - Clamp single retroactive shifts exceeding $500\text{ m}$.
  - Clamp vertical climb rates exceeding $30\text{ m/s}$ in stream recording.

### Step 4: Implement Historical Data Sanitization in `WorkoutSummariesDatabaseManager.java`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutSummariesDatabaseManager.java`
* **Actions**:
  - Add `sanitizeCorruptedAltitudeWorkouts()`.
  - Detect and repair 7300m staircase profiles in `ALTITUDE_STREAM` and `TABLE_EXTREMA_VALUES`.

### Step 5: Implement Defensive Outlier Filtering in `ElevationProfile.kt` & Exporters
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/export/GPXFileWriter.java`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/export/TCXFileWriter.java`
* **Actions**:
  - Filter leading non-physical spikes in `calculateElevationBounds`.
  - Clamp non-physical spikes in GPX and TCX exporters.

### Step 6: Update & Author Unit Tests
* **Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/devices/AltitudeFromPressureDeviceTest.kt`:
    Update `testSetAltitudeCorrection_whenSensorValuePresent_calculatesDeltaFromCurrentValue` to assert absolute raw offset formula.
    Add `testSetAltitudeCorrection_repeatedCalls_doesNotOscillate`.
    Add `testCalibrate_idempotency_skipsRedundantWork`.
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/TrackingTabsViewModelLocationTest.kt`:
    Add `testInsideGeofence_dispatchesCalibrateAltimeterOnlyOnGeofenceTransition`.
  - `app/src/test/java/com/atrainingtracker/trainingtracker/database/WorkoutSummariesDatabaseManagerTest.kt`:
    Add `testSanitizeCorruptedAltitudeWorkouts_repairsStaircaseProfile`.

### Step 7: Execute Targeted Unit Tests
* **Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "*AltitudeFromPressureDeviceTest*" --tests "*TrackingTabsViewModelLocationTest*" --tests "*WorkoutSummariesDatabaseManagerTest*"
  ```

---

## 6. Verification & Rollback Plan

* **Verification**:
  - Targeted unit tests (`AltitudeFromPressureDeviceTest`, `TrackingTabsViewModelLocationTest`, `WorkoutSummariesDatabaseManagerTest`) will prove elimination of calibration oscillations, idempotency of calibration calls, geofence gating, and historical data repair.
  - Full clean-room test suite (`./gradlew testDebugUnitTest`) will confirm zero regressions across all modules.
* **Rollback Plan**:
  - All changes reside on isolated feature branch `feature/ATT-1810`. Checking out `sprint/2026-40.7` restores pristine code.
