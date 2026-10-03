# Stage 1 Analysis: ATT-1810 - [Bug] Super implausible altitude

**Ticket**: [ATT-1810](https://rainerblind.atlassian.net/browse/ATT-1810)  
**Sub-task**: [ATT-1891](https://rainerblind.atlassian.net/browse/ATT-1891) (`[Analysis]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*) / Location & Altimeter Tracking  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.7`  
**Branch**: `feature/ATT-1810`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Problem Statement & Motivation

During on-device testing on Pixel 10 for workout *"Kurz zum Bäcker #14"* (a 2.05 km bicycle ride in Schönaich, Germany, spanning ~6 minutes at 06:02 AM), the Aftermath elevation profile rendered an extreme, non-physical elevation anomaly:
- The workout elevation profile starts at **~7300 m** (stratospheric altitude) at 0.0 km.
- The altitude remains between 5500 m and 3200 m for the first ~1.7 km, displaying distinct step-like plateaus.
- At 2.05 km, the altitude drops down to **507 m** (the true topographic elevation of Schönaich).
- Because the Y-axis auto-scales between 507 m and 7300 m, the actual 20-30 meter elevation variation of the terrain is completely crushed into a flat line at the bottom.

### Expected Behavior
- The recorded workout trackpoints and elevation profile stream must accurately reflect actual geographic ground-truth elevations (~480 m – 530 m in Schönaich).
- Repeated altimeter calibrations and geofence detections must be strictly idempotent and must never accumulate phantom altitude offsets or cause runaway integration into historical samples.
- The system must employ physical climb/descent sanity limits (e.g. vertical speed limits $|dh/dt| \le 30\text{ m/s}$) to filter out anomalous transient readings across live tracking, exports, and analytics.
- Existing historical workouts permanently contaminated in SQLite with spurious multi-thousand-meter shifts must be sanitized so that Aftermath rendering, summary statistics, and GPX/TCX exports auto-scale and export cleanly without distortion.

---

## 2. Root Cause Analysis (Forensic Investigation)

A comprehensive forensic trace across the barometric sensor driver, location calibration pipeline, and database sample shifting revealed a severe multi-component feedback loop that causes runaway retroactive altitude accumulation:

### 2.1 The Mathematical Calibration Bug in `AltitudeFromPressureDevice.java`
In `AltitudeFromPressureDevice.java`, barometric altitude is computed from raw atmospheric pressure:
```java
mLastRawAltitude = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, pressureHpa);
mAltitudeSensor.newValue(mLastRawAltitude + mAltitudeCorrection);
```
Here, `mAltitudeCorrection` represents the **absolute offset** that must be added to `mLastRawAltitude` to yield ground truth.

However, `setAltitudeCorrection(double correctAltitude)` (introduced/modified in `ATT-1353`) was implemented as:
```java
double currentAltitude;
if (mAltitudeSensor != null && mAltitudeSensor.getValue() != null) {
    currentAltitude = mAltitudeSensor.getValue().doubleValue();
} else if (!Double.isNaN(mLastRawAltitude)) {
    currentAltitude = mLastRawAltitude;
}
mAltitudeCorrection = correctAltitude - currentAltitude;
```
When `mAltitudeSensor.getValue()` is already non-null, `currentAltitude` is **already corrected** ($mLastRawAltitude + mAltitudeCorrection_{old}$).
Consequently:
$$\text{mAltitudeCorrection}_{new} = \text{correctAltitude} - (\text{mLastRawAltitude} + \text{mAltitudeCorrection}_{old})$$
Instead of computing the offset relative to raw sensor pressure ($\text{correctAltitude} - \text{mLastRawAltitude}$), it computed the **incremental difference**, yet stored it directly into `mAltitudeCorrection`!

### 2.2 The Oscillation and Broadcast Loop
On the subsequent pressure measurement tick:
```java
mAltitudeSensor.newValue(mLastRawAltitude + mAltitudeCorrection);
```
The sensor was updated with $\text{mLastRawAltitude} + \text{mAltitudeCorrection}_{new}$.
If `setAltitudeCorrection(correctAltitude)` was called again with the identical target altitude:
1. `currentAltitude` equaled `correctAltitude`.
2. `mAltitudeCorrection` evaluated to $507.0 - 507.0 = \mathbf{0.0}$.
3. On the next pressure event (occurring at ~5–10 Hz), the sensor value reverted to $\text{mLastRawAltitude} + 0.0$ (e.g. 450 m).
4. On the subsequent calibration call, `currentAltitude` was 450 m, so `mAltitudeCorrection` jumped back to $+57.0$ m!

### 2.3 The Continuous Cockpit Calibration Side-Effect in `TrackingTabsViewModel.kt`
In `TrackingTabsViewModel.kt` (introduced in `ATT-1617` under `REQ-UI-199`):
```kotlin
kotlinx.coroutines.flow.combine(
    banalServiceRepository.currentLocation,
    knownLocationsRepo.locationsFlow,
    banalServiceRepository.isAltimeterCalibrated
) { location, knownLocations, isCalibrated ->
    ...
    closestItem?.let {
        // Trigger altimeter calibration with reference altitude (REQ-UI-199)
        banalServiceRepository.calibrateAltimeter(it.altitude)
        ...
    }
}
```
Whenever the athlete is within the geofence of a known location (such as "Zu Hause" at workout start, or "Kurz zum Bäcker" at the destination), `banalServiceRepository.currentLocation` emits every second.
Because `calibrateAltimeter` was invoked inside the `combine` flow transform on **every single GPS location update**, calibration was triggered repeatedly (~1 Hz).

### 2.4 Downstream Historical Sample Shifting in `TrackerService.java`
Whenever `AltitudeFromPressureDevice.setAltitudeCorrection` computed a non-zero `mAltitudeCorrection`, it broadcasted `ALTITUDE_CORRECTION_INTENT`:
```java
Intent intent = new Intent(ALTITUDE_CORRECTION_INTENT)
        .setPackage(mContext.getPackageName())
        .putExtra(ALTITUDE_CORRECTION_VALUE, mAltitudeCorrection);
mContext.sendBroadcast(intent);
```
In `TrackerService.java`, the receiver handled this intent:
```java
double altitudeCorrection = intent.getDoubleExtra(AltitudeFromPressureDevice.ALTITUDE_CORRECTION_VALUE, 0.0);
if (altitudeCorrection == 0.0) return;

// 1. Live Session Update
if (mLiveSession != null) {
    mLiveSession.applyAltitudeCorrection(altitudeCorrection);
}

// 2. Database Synchronization
samplesManager.getDatabase().execSQL("UPDATE " + samplesTable
        + " set " + SensorType.ALTITUDE.name() + " = " + SensorType.ALTITUDE.name() + operator + Math.abs(altitudeCorrection));
summariesManager.shiftAltitudeData(workoutId, altitudeCorrection);
```
`TrackerService` interpreted `ALTITUDE_CORRECTION_VALUE` as a **delta shift** to apply to all historical trackpoints and streams!
Because `mAltitudeCorrection` oscillated between 0 and +57 m every few seconds while standing in the "Zu Hause" geofence:
- Every oscillation broadcasted $+57\text{ m}$.
- `TrackerService` shifted **all existing samples in the database and live session** up by $+57\text{ m}$ on every cycle!
- In 2 minutes of GPS warmup / pre-ride prep inside the geofence (~60 location ticks $\times$ ~30 shifts):
  $$30 \times 57\text{ m} \approx \mathbf{1710 \text{ to } 7300\text{ m cumulative shift!}}$$

### 2.5 The Staircase Anatomy of the Recorded Profile
The visual profile from *"Kurz zum Bäcker #14"* matches this defect with mathematical precision:
- **0.0 – 0.25 km (~7300 m)**: Points recorded at the start were present in the database during *all* subsequent calibration shifts, accumulating the maximum total shift (~7300 m).
- **0.25 – 1.0 km (~5500 m)**: Points recorded as the rider rode out of the "Zu Hause" geofence escaped further shifts from the start geofence.
- **1.0 – 1.7 km (~3200 m)**: The rider reached the bakery ("Kurz zum Bäcker", another known favorite location geofence!), triggering another flurry of calibration shifts that elevated all points recorded up to that point.
- **2.05 km (507 m)**: Points recorded at the end of the ride entered the home geofence just before stopping the workout and received minimal or zero subsequent shifts, remaining at the true elevation of 507 m.

### 2.6 Persistence Impact on SQLite Database Records
Because `TrackerService` executed `UPDATE samplesTable set ALTITUDE = ALTITUDE + offset` and `shiftAltitudeData(workoutId, offset)` into SQLite, past affected workouts (including *"Kurz zum Bäcker #14"*) have permanently mutated values stored in `samplesTable`, `TABLE_EXTREMA_VALUES`, and `WorkoutSummaries.ALTITUDE_STREAM`. Without explicit sanitization:
- Reading raw samples in `GPXFileWriter` / `TCXFileWriter` exports trackpoints at 7300 m.
- Max altitude in `TABLE_EXTREMA_VALUES` remains 7300 m.
- Aftermath graph rendering auto-scales to 7300 m.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Fix the calculation of `mAltitudeCorrection` in `AltitudeFromPressureDevice.java` so that it is always calculated as $\text{referenceAltitude} - \text{mLastRawAltitude}$ (the true absolute offset from raw pressure).
  2. Modify `ALTITUDE_CORRECTION_INTENT` broadcasting in `AltitudeFromPressureDevice` so that it broadcasts `deltaOffset = newCorrection - oldCorrection`, and suppresses broadcasts when $|deltaOffset| < 0.1\text{ m}$.
  3. Ensure `calibrate(referenceAltitude)` is strictly idempotent: if the altimeter is already calibrated to within $0.5\text{ m}$ of `referenceAltitude`, exit immediately without rebroadcasting.
  4. Decouple altimeter calibration in `TrackingTabsViewModel.kt` from the raw continuous GPS location stream so calibration is dispatched once upon geofence transition or uncalibrated state, not continuously on every GPS tick.
  5. Introduce physical climb/descent sanity bounding in `TrackerService.java` / `LiveWorkoutSession.java` (e.g. clamping vertical rate spikes $> 30\text{ m/s}$ or single retroactive shifts $> 500\text{ m}$).
  6. **Historical Data Sanitization & Repair Strategy**:
     - Provide an idempotent database repair / sanitization utility in `WorkoutSummariesDatabaseManager`: detect historical workouts containing non-physical altimeter feedback corruption (e.g. `maxAltitude > 4000m` when `minAltitude < 1000m`, or initial elevation spike $\ge 2000\text{ m}$ above the end elevation).
     - For corrupted workouts, sanitize the `ALTITUDE_STREAM`, recalculate clean `MIN`, `MAX`, and `AVG` in `TABLE_EXTREMA_VALUES`, and clamp/sanitize the corresponding `samplesTable` rows so that historical database integrity is restored.
  7. **Export & Summary Pipeline Resilience**:
     - Ensure that `GPXFileWriter`, `TCXFileWriter`, and `ElevationProfile` (`calculateElevationBounds`) apply sanity clipping against non-physical altitude values ($> 4000\text{ m}$ when workout terrain is $< 1000\text{ m}$ or $|dh/dt| > 30\text{ m/s}$), guaranteeing clean exports and graph scaling.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  - Do NOT alter Google Maps lite mode thumbnail or UI cards (completed in ATT-1816).
  - Do NOT alter bottom sheet surface backgrounds (completed in ATT-1817).
  - Do NOT redesign pace decoding or scrubbing (completed in ATT-1818).
  - Do NOT alter X-axis milestone decimation or distance steps (completed in ATT-1819).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

This fix directly refines **`REQ-CON-013`** and **`REQ-UI-199`**:

### 4.1 Requirement Archaeology for `REQ-CON-013`
* **Original Requirement ID & Target**: `REQ-CON-013` (*Barometric Altitude Sensor Initialization & Null-Safe Correction Dispatch*), targeting `AltitudeFromPressureDevice.java`.
* **Historical Origin & Commit Trace**: Commit `53502c7a` (`ATT-1353`) by Rainer Blind on 2026-09-24 (*"feat(ATT-1353): implement null-safe setAltitudeCorrection and unit test suite"*).
* **Root Reason for Existing Formulation**: `ATT-1353` aimed to prevent fatal JNI/SIGABRT crashes caused by unboxing null `mAltitudeSensor.getValue()` on cold starts. In doing so, it added `currentAltitude = mAltitudeSensor.getValue().doubleValue()` fallback without realizing that `mAltitudeSensor.getValue()` is already corrected, which corrupted subsequent re-calibrations.
* **Preservation of Core Invariants**: Null safety on cold start (`mAltitudeSensor.getValue() == null` and `mLastRawAltitude.isNaN()`) is 100% strictly preserved. Calculating `mAltitudeCorrection = correctAltitude - mLastRawAltitude` and broadcasting only the delta change `newCorrection - oldCorrection` restores mathematical correctness and eliminates spurious oscillations.

### 4.2 Requirement Archaeology for `REQ-UI-199`
* **Original Requirement ID & Target**: `REQ-UI-199` (*Lieblingsorte: Cockpit Calibration Badge Synchronized Altimeter Dispatch & Standby Architecture*), targeting `TrackingTabsViewModel.kt` and `AltitudeFromPressureDevice.java`.
* **Historical Origin & Commit Trace**: Sprint `2026-40.5` (`ATT-1617`).
* **Root Reason for Existing Formulation**: `ATT-1617` added closed-loop verification between `LocationCalibrationBadge` and `AltitudeFromPressureDevice`. It placed `banalServiceRepository.calibrateAltimeter(it.altitude)` inside the `combine` flow to ensure calibration happened whenever a geofence was entered, but did not gate the call against already-calibrated status.
* **Preservation of Core Invariants**: The cockpit badge's two-state visual feedback (`detected` standby vs `calibrated`), 9-language localization, and geofence detection remain 100% intact.

---

## 5. Architectural Strategy & High-Level Solution

1. **`AltitudeFromPressureDevice.java`**:
   - `setAltitudeCorrection(double correctAltitude)`:
     - Check if `mLastRawAltitude` is available (`!Double.isNaN(mLastRawAltitude)`).
     - Calculate `newCorrection = correctAltitude - mLastRawAltitude`.
     - Calculate `deltaOffset = newCorrection - mAltitudeCorrection`.
     - Update `mAltitudeCorrection = newCorrection`.
     - Update `mIsCalibrated = true`.
     - Update `mAltitudeSensor.newValue(mLastRawAltitude + mAltitudeCorrection)`.
     - Broadcast `ALTITUDE_CORRECTION_INTENT` with `ALTITUDE_CORRECTION_VALUE = deltaOffset` **only if** $|deltaOffset| \ge 0.1\text{ m}$.
   - `calibrate(double referenceAltitude)`:
     - If already calibrated and $|(mLastRawAltitude + mAltitudeCorrection) - referenceAltitude| < 0.5\text{ m}$, return `true` immediately without doing redundant work.
2. **`TrackingTabsViewModel.kt`**:
   - Track `lastCalibratedLocationId: Long?`.
   - Only call `banalServiceRepository.calibrateAltimeter(it.altitude)` when `closestItem.id != lastCalibratedLocationId` or when `!isCalibrated`.
   - When exiting all geofences (`closestItem == null`), reset `lastCalibratedLocationId = null`.
3. **`TrackerService.java` & `LiveWorkoutSession.java`**:
   - **Atomic Transaction Boundaries**: When applying retroactive altitude shift, wrap all database statements (`samplesTable UPDATE`, `TABLE_EXTREMA_VALUES UPDATE`, and `ALTITUDE_STREAM UPDATE`) within an explicit atomic SQLite transaction:
     ```java
     db.beginTransaction();
     try {
         // Execute batch updates
         db.setTransactionSuccessful();
     } finally {
         db.endTransaction();
     }
     ```
   - In `applyAltitudeCorrection(double offset)`:
     - Guard against non-physical offsets: if $|offset| > 500.0\text{ m}$, log a warning and reject or clamp the shift.
   - In `recordStreamPoint` and `addSample`:
     - Sanity check incoming altitude values against human physical limits (altitude between $-500\text{ m}$ and $+9000\text{ m}$).
     - Guard against vertical rate spikes exceeding $30\text{ m/s}$ ($108\text{ km/h}$ vertical velocity) between consecutive samples.
4. **Historical Database Sanitizer & Export Resilience**:
   - Provide `WorkoutSummariesDatabaseManager.sanitizeCorruptedAltitudeWorkouts()`:
     - Queries workouts where `extrema_max > 4000.0` and `extrema_min < 1000.0`.
     - Inspects the decoded `ALTITUDE_STREAM`: if the initial stream segment contains a step drop $> 1000\text{ m}$ descending to legitimate terrain, shifts or clamps the contaminated leading points to match the valid terrain baseline.
     - Re-encodes the sanitized `ALTITUDE_STREAM`, recalculates `MIN`/`MAX` in `TABLE_EXTREMA_VALUES`, and updates the corresponding `samplesTable` rows within an atomic SQLite transaction.
   - In `GPXFileWriter` and `TCXFileWriter`, clamp non-physical altitude samples ($> 9000\text{ m}$ or vertical step spikes $> 100\text{ m}$) to the previous valid sample to prevent publishing invalid GPX data.
   - In `calculateElevationBounds` (`ElevationProfile.kt`), filter out non-physical start outliers so that even un-sanitized legacy workouts auto-scale cleanly to real terrain.
5. **Unit & Integration Test Coverage Mandate**:
   - Add dedicated unit tests in `AltitudeFromPressureDeviceTest.kt` simulating the exact feedback loop: repeated calls to `setAltitudeCorrection` and `calibrate(507.0)` interleaved with simulated pressure events, asserting that `mAltitudeCorrection` does NOT oscillate, zero redundant broadcasts are sent, and sensor value remains locked at 507.0 m.
   - Add tests verifying `sanitizeCorruptedAltitudeWorkouts` successfully repairs the *"Kurz zum Bäcker #14"* staircase profile.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Sensor Null Safety: Cold starts with uninitialized pressure sensor or missing GPS fixes MUST NOT crash (`REQ-CON-013`).
  2. Topographic DEM Integration: DEM internet lookup (`REQ-DAT-014`) and manual favorites (`REQ-DAT-007`) remain fully functional.
  3. Edge-to-Edge and Clean-Room Stability: All existing unit tests and clean-room regression suite MUST pass 100%.
  4. Physical Sanity Thresholds: Vertical velocity limit ($\le 30\text{ m/s}$) is physically appropriate for cycling, running, and hiking while rejecting non-physical sensor jumps.
  5. Historical Data Integrity: SQLite sanitization operates within atomic transactions and only targets mathematically proven feedback artifacts ($> 4000\text{ m}$ spike with $< 1000\text{ m}$ baseline), leaving legitimate high-altitude alpine activities intact.
* **Risk Rating**: **MEDIUM**
  - Justification: Modifies core altimeter sensor calibration, retroactive sample shifting, and provides historical data repair. The root cause is completely diagnosed and verified with concrete mathematical proof and targeted unit tests.
