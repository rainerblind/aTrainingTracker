# Implementation Plan: ATT-499 - Minimum Altitude Marker Spatial Positioning

## 1. Goal Description
Resolve the defect where the minimum altitude marker (`ic_altitude_min`, blue pin with downward triangle) is rendered at an incorrect location along the route (e.g. in the middle of a climb rather than at the true spatial minimum at the start of the hike).

### Root Causes
1. **Non-Authoritative Sensor Pollution**: In `TrackerService.java`, the 1Hz sampling loop previously iterated over `mBanalService.getAllSensorData()` and fed every sensor reading into `mLiveSession.addSample()`. When both a barometer (`ALTITUDE_FROM_PRESSURE`) and GPS (`SPEED_AND_LOCATION_GPS`) were active, both delivered `SensorType.ALTITUDE`. While the elevation profile chart correctly used only `mBanalService.getBestSensorData(SensorType.ALTITUDE)`, `mLiveSession.RunningStats` was contaminated by noisy GPS altitude. A transient GPS vertical dip or multipath jump (which also generated a max speed spike at the exact same location) overwrote `stats.min` and locked `stats.minPos` to the glitch coordinate.
2. **Unanchored Null Spatial Coordinates at Tracking Start**: When tracking starts before satellite lock (`currentPos == null`), `RunningStats.addValue()` initialized `min` with the baseline elevation, but left `minPos = null`. Because elevation increases as the user climbs uphill, `value < min` was never satisfied again, leaving `minPos` unanchored and susceptible to being captured by any transient dip later in the session.
3. **Database Fallback Query Flaw**: In `WorkoutSamplesDatabaseManager.java`, `getExtremaPosition()` performed `ORDER BY ... ASC LIMIT 1` without filtering for `LATITUDE IS NOT NULL AND LONGITUDE IS NOT NULL`. In SQLite, `NULL` values sort first in ascending order, causing fallback queries to fail or return unanchored records.

---

## 2. User Review Required
> [!NOTE]
> No breaking database schema migrations are required. The changes sanitize in-memory live tracking statistics, anchor spatial coordinates to valid initial GPS fixes, and harden legacy sample queries.

---

## 3. Proposed Changes

### Component 1: `app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`
#### [MODIFY] [TrackerService.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java)
- In `sampleAndWriteToDb()`:
  - Decouple `mLiveSession` running statistics updates from the `getAllSensorData()` loop.
  - Feed `mLiveSession.addSample(sensorType, ...)` strictly from `mBanalService.getBestSensorData(sensorType)` for each sensor type in `SENSORS_TO_TRACK`.
  - Retain `getAllSensorData()` loop strictly for multi-device raw sample persistence in `samplesDb` and session accumulators (calories, total time, active time, distance).

### Component 2: `app/src/main/java/com/atrainingtracker/trainingtracker/tracker/LiveWorkoutSession.java`
#### [MODIFY] [LiveWorkoutSession.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/tracker/LiveWorkoutSession.java)
- In `RunningStats.addValue()`:
  - Add spatial coordinate late-binding: when `minPos == null` and `position != null`, if `value` matches the recorded baseline `min` (within epsilon `1e-3`), anchor `minPos = position`.
  - Apply symmetrical late-binding for `maxPos` when `maxPos == null`.

### Component 3: `app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutSamplesDatabaseManager.java`
#### [MODIFY] [WorkoutSamplesDatabaseManager.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutSamplesDatabaseManager.java)
- In `getExtremaPosition()`:
  - Add `selection: "LATITUDE IS NOT NULL AND LONGITUDE IS NOT NULL"` to the `query()` call for `MIN` and `MAX` extrema to ensure the ordered query only inspects points with valid spatial coordinates.

### Component 4: Specifications & Test Traceability
#### [MODIFY] [requirements.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md)
- Formalize `REQ-MAP-019` (*Authoritative Sensor Extrema Spatial Positioning*).
#### [MODIFY] [tests.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md)
- Formalize `TST-MAP-021` (*Minimum Altitude Marker Spatial Positioning Verification*) mapped to Jira sub-task `ATT-594`.

---

## 4. Verification Plan

### Automated Tests
- Run `./gradlew testDebugUnitTest` to verify no regressions across tracking lifecycle, session statistics, and spatial calculations.
- Add unit test in `LiveWorkoutSessionTest` verifying:
  1. Authoritative isolation: multiple sensor values for the same type do not cross-contaminate.
  2. Late spatial binding: starting tracking with `position == null` followed by valid `position` correctly anchors `minPos`.

### Manual Verification (`TST-MAP-021`)
1. Record a workout with altitude variation (or inspect sample hike route).
2. Verify in map view that `ic_altitude_min` is positioned at the lowest geographic elevation point (e.g. trailhead), matching the minimum point on the elevation chart.
3. Verify that `ic_altitude_max` is positioned at the peak elevation point.
