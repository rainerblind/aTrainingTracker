# Implementation Plan: Altitude Bounds Sanitization, Extrema Self-Healing & Cold-Start Protection (ATT-508)

## 1. Problem Statement & Architectural Context
In workout "Einkaufstour (Ca Savio) #9" (04.08.2026, 19:39) recorded in Ca' Savio (Italy, sea level ~0-3 m elevation), the elevation profile chart displayed unplausible bounds of **-63 m** and **+60 m** (123 m span), compressing a flat 0 m route to a horizontal line in the center.

Stage 1 Root Cause Analysis identified three root causes:
1. **Unchecked Extrema Overrides in `ElevationProfile.kt`**: `minAltitudeOverride` and `maxAltitudeOverride` (introduced for `REQ-UI-013` to match summary tables) unconditionally override the actual stream points (`pathPointsDownsampled`), even when deviating by >60 meters.
2. **Historical Pre-ATT-499 Database Corruption**: In workouts recorded prior to commit `fe0a6e42` (03.09.2026), `TrackerService` fed noisy GPS startup samples into `LiveWorkoutSession` running stats, polluting SQLite `extrema_values` with cold-start GPS spikes (-63 m and +60 m) while `ALTITUDE_STREAM` recorded the smooth barometric sensor (~0-3 m).
3. **Barometric Cold-Start Calculation Offset**: In `AltitudeFromPressureDevice.java`, standard atmosphere altitude at 1020.8 hPa equals precisely -63.0 m. If emitted before calibration, it cements -63.0 m as the minimum.

Stage 2 formulated formal requirements `REQ-UI-126`, `REQ-DAT-009`, and `REQ-CON-011` with test cases `TST-UI-079`, `TST-DAT-003`, and `TST-CON-002`.

---

## 2. User Review Required

> [!NOTE]
> **Extrema Self-Healing**: When opening or mapping an existing workout that has an encoded altitude stream (`ALTITUDE_STREAM`), if the stored SQLite extrema deviate from the actual stream by >15 m (e.g. Min = -63 m vs stream 0 m), `WorkoutDataMapper` will automatically reconcile the database entry to match the true stream extrema. This fixes all historical workouts permanently in SQLite without requiring a global database migration.

> [!TIP]
> **Aesthetic Minimum Vertical Span**: For flat coastal or velodrome routes (e.g. elevation 0 m to 2 m), `ElevationProfile` will enforce a minimum vertical span of 20 meters centered around the route elevation. This prevents minor sub-meter barometric noise from appearing as massive vertical spikes while ensuring flat routes look flat.

---

## 3. Proposed Changes

### Component 1: `ElevationProfile.kt` (UI Layer)
**File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt`

#### [MODIFY] `ElevationProfile.kt`
* Extract bounds resolution into a dedicated, pure, unit-testable helper function:
  ```kotlin
  data class ElevationBounds(val min: Double, val max: Double, val range: Double)
  ```
* Implement bounds sanitization logic:
  1. Compute `streamMin` and `streamMax` from `pathPointsDownsampled`.
  2. If `minAltitudeOverride` is provided:
     * If `minAltitudeOverride` deviates from `streamMin` by more than `OUTLIER_TOLERANCE_METERS` (15.0 m), clamp it to `streamMin`.
     * Otherwise, use `minAltitudeOverride` (preserving `REQ-UI-013` table synchronization).
  3. If `maxAltitudeOverride` is provided:
     * If `maxAltitudeOverride` deviates from `streamMax` by more than `OUTLIER_TOLERANCE_METERS` (15.0 m), clamp it to `streamMax`.
     * Otherwise, use `maxAltitudeOverride`.
  4. Enforce minimum vertical span (`MIN_SPAN_METERS = 20.0`):
     * If `(sanitizedMax - sanitizedMin) < MIN_SPAN_METERS`:
       * Calculate center: `mid = (sanitizedMin + sanitizedMax) / 2.0`.
       * Expand bounds symmetrically: `effectiveMin = mid - MIN_SPAN_METERS / 2.0`, `effectiveMax = mid + MIN_SPAN_METERS / 2.0`.
  5. Use `effectiveMin` and `effectiveMax` for rendering the curve, computing Y positions, and displaying Y-axis labels.

---

### Component 2: `WorkoutDataMapper.kt` (Data Mapping & Self-Healing Layer)
**File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDataMapper.kt`

#### [MODIFY] `WorkoutDataMapper.kt`
* Implement `reconcileAltitudeExtrema(workoutId: Long, encodedAltitudes: String, recordedMin: Double?, recordedMax: Double?): Pair<Double?, Double?>`:
  1. If `encodedAltitudes` is empty, return `Pair(recordedMin, recordedMax)`.
  2. Decode stream using `NumericalEncodingUtils.decodeDoubles(encodedAltitudes)`.
  3. Compute `streamMin = streamPoints.minOrNull()` and `streamMax = streamPoints.maxOrNull()`.
  4. Detect corruption:
     * `isMinCorrupted = recordedMin == null || recordedMin < streamMin - 15.0 || recordedMin > streamMax + 5.0`
     * `isMaxCorrupted = recordedMax == null || recordedMax > streamMax + 15.0 || recordedMax < streamMin - 5.0`
  5. If corrupted:
     * Resolve authoritative values: `authoritativeMin = if (isMinCorrupted) streamMin else recordedMin`, `authoritativeMax = if (isMaxCorrupted) streamMax else recordedMax`.
     * Persist to SQLite `extrema_values` via `workoutSummariesDatabaseManager.updateExtremaValue(...)`.
     * Return `Pair(authoritativeMin, authoritativeMax)`.
  6. In `map(cursor: Cursor)`: Call `reconcileAltitudeExtrema` to populate `minAltitude` and `maxAltitude` in `WorkoutData`.

---

### Component 3: `AltitudeFromPressureDevice.java` (Sensor Driver Layer)
**File**: `app/src/main/java/com/atrainingtracker/banalservice/devices/AltitudeFromPressureDevice.java`

#### [MODIFY] `AltitudeFromPressureDevice.java`
* In `onSensorChanged(SensorEvent event)`:
  * Check and run `initPressureSensor()` **before** emitting `mAltitudeSensor.newValue(mLastRawAltitude + mAltitudeCorrection)` when `!mPressureSensorInitialized`.
  * Ensures that if GPS coordinates / known location are already available at device start, the reference altitude correction is applied to the very first sensor event rather than publishing an uncalibrated 1013.25 hPa reading.

---

### Component 4: Verification Test Suites
**Directory**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/` and `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/`

#### [NEW] `ElevationProfileBoundsTest.kt`
* Tests `calculateElevationBounds`:
  1. Flat coastal profile (0 m to 2 m) with corrupted overrides (-63 m, 60 m) -> clamps outliers and enforces 20 m span centered at 1 m (-9 m to +11 m).
  2. Mountain profile (500 m to 1200 m) with valid overrides (498 m, 1202 m) -> preserves exact dynamic range.
  3. Empty path points -> falls back gracefully without division-by-zero or crash.

#### [NEW] `WorkoutDataMapperAltitudeTest.kt`
* Tests `reconcileAltitudeExtrema`:
  1. Corrupted legacy extrema (stream 0 to 3 m, recorded -63 m, 60 m) -> heals to (0.0, 3.0) and verifies `updateExtremaValue` call.
  2. Legitimate extrema matching stream (stream 100 to 250 m, recorded 99 m, 251 m) -> returns recorded values without DB write.
  3. Empty stream -> returns original recorded values without DB write.

---

## 4. System Invariant Checklist
* **REQ-MAP-010 (Toggleable Grade Legend)**: Grade calculation and legend drawing must remain unaltered.
* **REQ-UI-013 (Harmonized Extrema)**: When `WorkoutDataMapper` reconciles corrupted extrema in SQLite, both the summary table and the elevation profile chart display the exact same healed values.
* **Non-destructive Reconciliation**: Workouts without altitude streams or workouts with legitimate matching extrema are never modified.
* **Zero Regressions**: All existing unit tests (`./gradlew testDebugUnitTest`) must pass clean.

---

## 5. Verification Plan

### Automated Unit Tests
* Run new test suites:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.ElevationProfileBoundsTest"
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDataMapperAltitudeTest"
  ```
* Run full project regression suite:
  ```bash
  ./gradlew testDebugUnitTest
  ```
