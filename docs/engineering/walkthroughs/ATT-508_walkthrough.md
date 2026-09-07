# Walkthrough: Altitude Bounds Sanitization & Extrema Self-Healing (ATT-508)

## 1. Overview
This change resolves the unplausible altitude bounds (-63 m and +60 m) observed in workout "Einkaufstour (Ca Savio) #9" (04.08.2026, 19:39) in Ca' Savio (Italy, sea level).

---

## 2. Key Changes

### Component 1: `ElevationProfile.kt` (Presentation Layer)
* Extracted bounds resolution to `calculateElevationBounds(pathPoints, minAltitudeOverride, maxAltitudeOverride, outlierToleranceMeters = 15.0, minSpanMeters = 20.0)` returning `ElevationBounds(min, max, range)`.
* **Outlier Clamping**: If `minAltitudeOverride` or `maxAltitudeOverride` deviates by >15 m from the recorded stream envelope, it is clamped to the actual stream bounds.
* **Aesthetic Minimum Span**: Enforces a 20 m minimum span centered around the route elevation, preventing flat coastal and velodrome routes from compressing into hairlines or magnifying sub-meter noise.
* **Axis Label Alignment**: Axis labels and canvas coordinates use the sanitized bounds, guaranteeing that rendered points are never outside the vertical scale.

### Component 2: `WorkoutDataMapper.kt` (Self-Healing Data Layer)
* Implemented `reconcileAltitudeExtrema(workoutId, encodedAltitudes, recordedMin, recordedMax)`.
* When mapping workouts with non-empty `ALTITUDE_STREAM`, compares persisted SQLite extrema against the decoded stream points.
* If corruption is detected (>15 m divergence from stream), reconciles to the true stream min/max and persists to `extrema_values` in SQLite.
* Ensures both the summary table and the elevation profile chart display identical, true extrema (`REQ-UI-013`).

### Component 3: `AltitudeFromPressureDevice.java` (Sensor Driver Layer)
* Reordered `onSensorChanged()`: executes `initPressureSensor()` before emitting `mAltitudeSensor.newValue(mLastRawAltitude + mAltitudeCorrection)`, ensuring the first reading uses reference calibration when known locations are available.

### Component 4: Automated Unit Tests
* `ElevationProfileBoundsTest.kt`: Validates flat coastal profiles with corrupted overrides (-63 m, +60 m) clamping to 20 m span (-9 m to +11 m), mountain routes preserving full dynamic range, and empty point fallbacks.
* `WorkoutDataMapperAltitudeTest.kt`: Validates self-healing of corrupted legacy extrema, non-destructive behavior for clean records, and empty stream handling.

---

## 3. Verification Results

### Automated Unit Tests
```bash
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.ElevationProfileBoundsTest" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDataMapperAltitudeTest"
```
* **Result**: `BUILD SUCCESSFUL in 9s` (32 actionable tasks, 5 executed, 27 up-to-date; all tests passed green with 0 errors).
