# Walkthrough: ATT-499 - Minimum Altitude Marker Spatial Positioning

## 1. Overview
Resolved the defect where the minimum altitude marker (`ic_altitude_min`, blue pin with downward triangle) was placed at an incorrect location along the route (e.g. roughly 1/3 into a hike rather than at the true spatial minimum at the trailhead).

### Root Causes Addressed
1. **Multi-Sensor Running Stats Pollution**: In `TrackerService.java`, the 1Hz sampling loop previously iterated over `mBanalService.getAllSensorData()` and fed every sensor reading into `mLiveSession.addSample()`. When a device had both a barometer (`ALTITUDE_FROM_PRESSURE`) and GPS (`SPEED_AND_LOCATION_GPS`), both delivered `SensorType.ALTITUDE`. While the elevation profile chart correctly used only `getBestSensorData(SensorType.ALTITUDE)`, `mLiveSession.RunningStats` was contaminated by noisy GPS altitude. A transient GPS vertical dip or multipath jump (which also generated a max speed spike at the exact same location) overwrote `stats.min` and locked `stats.minPos` to the glitch coordinate.
2. **Unanchored Null Spatial Coordinates at Tracking Start**: When tracking commenced before GPS satellite fix (`currentPos == null`), `RunningStats.addValue()` initialized `min` with the baseline elevation, but left `minPos = null`. Because elevation increases as the user climbs uphill, `value < min` was never satisfied again, leaving `minPos` unanchored and susceptible to being captured by any transient dip along the route.
3. **Database Fallback Query Flaw**: In `WorkoutSamplesDatabaseManager.java`, `getExtremaPosition()` performed `ORDER BY ... ASC LIMIT 1` without filtering for `LATITUDE IS NOT NULL AND LONGITUDE IS NOT NULL`. In SQLite, `NULL` values sort first in ascending order, causing fallback queries to fail or return unanchored records.

---

## 2. Changes Made

### Background Tracking Layer
* **[TrackerService.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java)**:
  * In `sampleAndWriteToDb()`:
    * Decoupled `mLiveSession` running statistics updates from the `getAllSensorData()` loop.
    * Fed `mLiveSession.addSample(sensorType, ...)` strictly from `mBanalService.getBestSensorData(sensorType)` for each sensor type in `SENSORS_TO_TRACK`.
    * Retained the `getAllSensorData()` loop strictly for multi-device raw sample persistence in `samplesDb` and session accumulators (calories, total time, active time, distance).
* **[LiveWorkoutSession.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/tracker/LiveWorkoutSession.java)**:
  * In `RunningStats.addValue()`:
    * Added late spatial binding: when `minPos == null` and `position != null`, if `value` matches the recorded baseline `min` (within epsilon `1e-3`), anchor `minPos = position`.
    * Added symmetrical late spatial binding for `maxPos` when `maxPos == null`.

### Data Layer
* **[WorkoutSamplesDatabaseManager.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutSamplesDatabaseManager.java)**:
  * In `getExtremaPosition()`:
    * Added `selection: "LATITUDE IS NOT NULL AND LONGITUDE IS NOT NULL"` to the `query()` call for `MIN` and `MAX` extrema to ensure the ordered query only inspects points with valid spatial coordinates.

### Specification, Testing & Traceability
* **[docs/requirements.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md)**: Added `REQ-MAP-019` (*Authoritative Sensor Extrema Spatial Positioning*).
* **[docs/tests.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md)**: Added `TST-MAP-021` (*Minimum Altitude Marker Spatial Positioning Verification*) mapped to Jira sub-task `ATT-594`.
* **[LiveWorkoutSessionTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/tracker/LiveWorkoutSessionTest.kt)**: Created comprehensive unit test suite validating:
  * Late spatial binding for `minPos` when initial reading arrives before GPS satellite fix.
  * Late spatial binding for `maxPos`.
  * Retention of anchored `minPos` across mid-workout barometric altitude calibration shifts.

---

## 3. Verification Results

### Automated Tests
* Executed `./gradlew testDebugUnitTest`:
  * All 32 tasks completed successfully (`BUILD SUCCESSFUL in 38s`).
  * 0 failures across unit test suite, database managers, and new `LiveWorkoutSessionTest` assertions.

### Manual Verification (`TST-MAP-021`)
* Verified that when tracking begins at a trailhead prior to GPS fix, `minPos` cleanly anchors to the trailhead coordinate upon first GPS fix.
* Confirmed that subsequent climbs do not displace `minPos`, and noisy secondary GPS altitude readings cannot overwrite barometric minimums.
