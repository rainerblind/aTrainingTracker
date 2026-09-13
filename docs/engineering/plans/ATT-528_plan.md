# Implementation Plan - ATT-528: Wrong Max Line Distance Marker

## 1. Goal Description
Ensure that a recorded workout's Maximum Line Distance (Apex) marker (`ic_distance` ruler icon) and value (`maxDisplacement`) accurately and authoritatively reflect the geographical point along the workout's recorded track that has the maximum geodesic displacement from the workout's start location (`startLatLng`).
This resolves the bug observed on multiple tracked workouts (such as "Dingle Boardwalk Route", where the apex was misplaced at a short 300m spur instead of the 1800m turn-around point at Penrallt).

---

## 2. Requirements & Verification Traceability

| Requirement ID | Description | Implementation File(s) | Verification ID | Status |
|:---|:---|:---|:---|:---|
| **REQ-MAP-020** | Authoritative Workout Max Line Distance (Apex) Calculation & Self-Anchored Displacement Integrity | `SpeedAndLocationDevice.java`, `LiveWorkoutSession.java`, `TrackerService.java`, `WorkoutSummariesDatabaseManager.java`, `WorkoutDataMapper.kt` | `TST-MAP-022` (Jira: `ATT-611`) | In Progress |
| **REQ-SET-063** | Geometric Route Cluster Apex Integrity & Live-Session Tracking Synchronization | `WorkoutClusterEngine.kt`, `SpeedAndLocationDevice.java` | `TST-SET-049` | Verified |

---

## 3. Impact Analysis & System Invariants (SWE.1.BP.5)

### Affected Files Audit (`find_usages` & `grep`):
1. `app/src/main/java/com/atrainingtracker/banalservice/devices/SpeedAndLocationDevice.java`:
   - `mLineDistanceSensor`: Declared as `protected MySensor<Double>` on line 46. Instantiated as `MyDoubleAccumulatorSensor` on line 92.
   - Audit: No caller in `BANALService` or `SpeedAndLocationDevice` invokes accumulator-specific methods on `mLineDistanceSensor`. Changing instantiation to `new MySensor<Double>(this, SensorType.LINE_DISTANCE_m)` is purely additive/corrective and removes accumulator reset corruption.
2. `app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`:
   - Telemetry sampling loop (`sampleAndWriteToDb`): Directly computing geodesic displacement between `mLiveSession.getStartLatLng()` and `currentPos` ensures the live sensor stats are guaranteed to be anchored to the workout's actual start point.
   - Session finalization (`finalizeLiveSession`): Deterministically calculating the maximum displacement and apex coordinate from `mLiveSession.getSampledLatLngs()` and `startPos` guarantees 100% mathematical integrity before cluster suggestion and database persistence.
3. `app/src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt`:
   - Lines 544-567: Extrema persistence (`START`, `END`, `LINE_DISTANCE_m MAX`) was previously skipped if `existingClusterId != -1L`. Moving extrema calculation prior to the cluster assignment check ensures recalculation/import always establishes authoritative spatial anchors.
4. `app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutSummariesDatabaseManager.java`:
   - Provide a utility `repairWorkoutApex(long workoutId)` that inspects `mapPolyline` and `startLatLng`, recalculates the true apex if missing or corrupted, and updates `TABLE_EXTREMA_VALUES`.
5. `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDataMapper.kt`:
   - When mapping `WorkoutData`, if `maxDispLatLng` is null or points to an invalid location, leverage polyline-based apex derivation as a zero-latency fallback to self-heal existing workouts in the UI.

### System Invariants:
- **Database Schema**: No columns added or removed in raw samples tables (`WorkoutSamplesDatabaseManager`).
- **Telemetry Feeds**: `SensorType.LATITUDE`, `LONGITUDE`, `ALTITUDE`, `SPEED_mps`, `BEARING` remain unchanged.
- **Polyline Encoding & Compression**: `NumericalEncodingUtils` and Google `PolyUtil` algorithms remain unchanged.
- **Non-Target Sensor Extrema**: Altitude min/max, Speed max, Heart Rate max, Power max calculations remain unchanged.

---

## 4. Proposed Changes

### Component 1: Sensor Typology Fix (`SpeedAndLocationDevice.java`)
#### [MODIFY] [SpeedAndLocationDevice.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/devices/SpeedAndLocationDevice.java)
- Change instantiation of `mLineDistanceSensor` to:
  ```java
  mLineDistanceSensor = new MySensor<Double>(this, SensorType.LINE_DISTANCE_m);
  ```
- This prevents `resetAccumulatorsReceiver` in `MyDevice.java` from treating `mLineDistanceSensor` as an accumulator and corrupting `mInitialValue` on reset.

---

### Component 2: Authoritative Live Anchoring & Deterministic Finalization (`TrackerService.java`)
#### [MODIFY] [TrackerService.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java)
- In `sampleAndWriteToDb()`:
  When updating live stats for `SensorType.LINE_DISTANCE_m`:
  If `currentPos != null && mLiveSession.getStartLatLng() != null`, calculate line distance directly:
  ```java
  float[] res = new float[1];
  Location.distanceBetween(
      mLiveSession.getStartLatLng().latitude, mLiveSession.getStartLatLng().longitude,
      currentPos.latitude, currentPos.longitude, res
  );
  double authoritativeLineDist = res[0];
  ```
  Pass `authoritativeLineDist` to `mLiveSession.addSample(SensorType.LINE_DISTANCE_m, authoritativeLineDist, currentPos)`.
- In `finalizeLiveSession()`:
  Before persisting extrema and calling cluster suggestion:
  Iterate over `mLiveSession.getSampledLatLngs()` to calculate the exact maximum geodesic distance and apex coordinate from `startPos`:
  ```java
  if (startPos != null && !mLiveSession.getSampledLatLngs().isEmpty()) {
      double maxDisp = 0.0;
      LatLng apex = startPos;
      float[] distResult = new float[1];
      for (LatLng pt : mLiveSession.getSampledLatLngs()) {
          Location.distanceBetween(startPos.latitude, startPos.longitude, pt.latitude, pt.longitude, distResult);
          if (distResult[0] > maxDisp) {
              maxDisp = distResult[0];
              apex = pt;
          }
      }
      summariesManager.updateExtremaValue(mWorkoutID, SensorType.LINE_DISTANCE_m, ExtremaType.MAX, maxDisp, apex);
      repository.updateExtremaValue(mWorkoutID, SensorType.LINE_DISTANCE_m, ExtremaType.MAX, maxDisp, apex);
  }
  ```

---

### Component 3: Recalculation & Self-Healing for Existing Sessions
#### [MODIFY] [LegacyImportEngine.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt)
- Move calculation and persistence of `START`, `END`, and `LINE_DISTANCE_m MAX` (lines 550-567) before the `existingClusterId != -1L` early-exit check, ensuring re-import and recalculations always populate authoritative spatial extrema.

#### [MODIFY] [WorkoutDataMapper.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDataMapper.kt)
- In single and batch workout mapping:
  If `maxDispLatLng == null && startLatLng != null && mapPolyline.isNotEmpty()`, dynamically compute the apex coordinate from the decoded polyline points:
  ```kotlin
  val resolvedApex = maxDispLatLng ?: run {
      if (startLatLng != null && mapPolyline.isNotEmpty()) {
          val points = PolyUtil.decode(mapPolyline)
          var maxD = -1.0
          var bestPt: LatLng? = null
          val res = FloatArray(1)
          points.forEach { pt ->
              Location.distanceBetween(startLatLng.latitude, startLatLng.longitude, pt.latitude, pt.longitude, res)
              if (res[0] > maxD) {
                  maxD = res[0].toDouble()
                  bestPt = pt
              }
          }
          bestPt
      } else null
  }
  ```
  Ensure `maxDisplacementLatLng` in `WorkoutData` is populated with `resolvedApex`.

---

## 5. Verification Plan

### Automated Unit & Regression Tests:
1. **New Unit Test Suite**: `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/MaxLineDistanceIntegrityTest.kt`:
   - `testLineDistanceSensorIsNotAccumulator()`: Verify `SpeedAndLocationDevice` instantiates `mLineDistanceSensor` as `MySensor<Double>` and does not modify its value on accumulator reset.
   - `testLiveWorkoutSessionAnchorsLineDistanceToStart()`: Simulate track points (start, short 300m spur, return, long 1800m branch). Verify apex is placed at the 1800m point.
   - `testDeterministicApexCalculationFromPolyline()`: Verify polyline-based apex calculation produces the exact coordinate furthest from start.
   - `testSelfHealingWorkoutDataMapper()`: Verify `WorkoutDataMapper` heals a workout missing `maxDispLatLng` by deriving it from `mapPolyline` and `startLatLng`.
2. **Full Regression Suite**:
   - Run `./gradlew testDebugUnitTest` to guarantee all existing test suites pass without regression.

### Manual Verification:
1. Open the workout details map for "Dingle Boardwalk Route" (or simulate with coordinates from the screenshot):
   Verify the `ic_distance` ruler icon is positioned at the turnaround point at Penrallt (~1.8km from start) instead of the western boardwalk spur.
