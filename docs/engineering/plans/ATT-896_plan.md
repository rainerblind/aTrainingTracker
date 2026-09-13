# Implementation Plan: Workout Lap Recording Lifecycle, Pause Neutrality & Zero-Duration Phantom Prevention (ATT-896)

## 1. Executive Summary & Objective
The objective of **ATT-896** is to completely eliminate the creation and presentation of zero-duration phantom laps (`00:00:00`, `0.00 km`, `NaN` speed) during workout tracking, session termination, and data viewing:
1. **Pause/Resume Neutrality**: Stop broadcasting `REQUEST_NEW_LAP` when pausing or resuming tracking (`TrainingApplication.java`). Pausing is an interruption of tracking, not an athletic lap split; the active lap must continue seamlessly across pauses.
2. **Termination Guards in TrackerService**: Ensure `TrackerService.createNewLap()` in `endWorkout()` and `mLapSummaryReceiver` only persist a final lap if `lapTime_s > 0` or `lapDistance > 0.0`.
3. **Database Input Guard**: Enforce a strict integrity check in `LapsDatabaseManager.saveLap(...)` rejecting any attempt to insert splits where `lapTime <= 0 && lapDistance <= 0.0`.
4. **Historical Query Sanitization**: Update `LapsDatabaseManager.getLaps()` and `getLapsForWorkouts()` to filter out legacy zero-duration rows (`TIME_TOTAL_s > 0 OR DISTANCE_TOTAL_m > 0`), ensuring historical corrupted rows never render in UI summaries (`WorkoutLaps.kt`) or export files (`TCXFileWriter.java`, `GPXFileWriter.java`).
5. **Manual Lap Integrity**: Ensure athlete-initiated manual lap splits via tracking UI (`TrackingViewsRepository.requestNewLap()`) and smartwatch (`PebbleService`) remain 100% operational.

---

## 2. Traceability & Mapped Entities

| Artifact | Identifier | Details |
| :--- | :--- | :--- |
| **Requirement** | `REQ-TRK-002` | Workout Lap Recording Lifecycle, Manual Splitting & Pause/Resume Neutrality (`docs/requirements.md`) |
| **Test Specification** | `TST-TRK-002` | Workout Lap Lifecycle, Pause Neutrality & Zero-Duration Phantom Prevention (`docs/tests.md`) |
| **Parent Ticket** | `ATT-896` | `[Bug] There is always a lap with zero duration added.` (Epic: `ATT-826`) |
| **Analysis Sub-task** | `ATT-963` | `[Analysis] There is always a lap with zero duration added.` (`Erledigt` - Gate 1 Approved) |
| **Test Sub-task** | `ATT-964` | `[Test-Spec] There is always a lap with zero duration added.` (`Erledigt` - Gate 2 Approved) |
| **Plan Sub-task** | `ATT-965` | `[Impl-Plan] There is always a lap with zero duration added.` (`In Bearbeitung` - Stage 3) |

---

## 3. Detailed Software Design & File Changes (SWE.2 / SWE.3)

### 3.1 Tracking Lifecycle & Pause Neutrality ([TrainingApplication.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/TrainingApplication.java))
* **Remove `REQUEST_NEW_LAP` in `pauseTracking()`**:
  ```java
  protected void pauseTracking() {
      if (DEBUG) Log.d(TAG, "pause tracking");

      // ATT-896: Do NOT broadcast REQUEST_NEW_LAP on pause.
      // Pausing is a session interruption, not an athletic lap split.

      cTrackingMode = TrackingMode.PAUSED;
      notifyTrackingStateChanged();
  }
  ```
* **Remove `REQUEST_NEW_LAP` in `resumeFromPaused()`**:
  ```java
  protected void resumeFromPaused() {
      if (DEBUG) Log.d(TAG, "resume tracking");

      if (TrainingApplication.startSearchWhenResumeFromPaused()) {
          sendBroadcast(new Intent(REQUEST_START_SEARCH_FOR_PAIRED_DEVICES)
                  .setPackage(getPackageName()));
      }

      // ATT-896: Do NOT broadcast REQUEST_NEW_LAP on resume.
      // The active lap resumes accumulation transparently.

      cTrackingMode = TrackingMode.TRACKING;
      notifyTrackingStateChanged();
  }
  ```

### 3.2 Service Layer Termination & Receiver Guards ([TrackerService.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java))
* **Guard in `createNewLap()`**:
  ```java
  protected void createNewLap() {
      if (DEBUG) Log.i(TAG, "createNewLap");

      if (mBanalService == null) {
          mCreateNewLapWhenConnectedToBanalService = true;
      } else {
          SensorData sensorData;

          int prevLapNr = 0;
          sensorData = mBanalService.getBestSensorData(SensorType.LAP_NR);
          if (sensorData != null) {
              prevLapNr = (Integer) sensorData.getValue();
          }

          int lapTime_s = 0;
          sensorData = mBanalService.getBestSensorData(SensorType.TIME_LAP);
          if (sensorData != null && sensorData.getValue() != null) {
              lapTime_s = (Integer) sensorData.getValue();
          }

          double lapDistance = 0.0;
          sensorData = mBanalService.getBestSensorData(SensorType.DISTANCE_m_LAP);
          if (sensorData != null && sensorData.getValue() != null) {
              lapDistance = (Double) sensorData.getValue();
          }

          // ATT-896: Guard against saving zero-duration / zero-distance phantom laps
          if (lapTime_s <= 0 && lapDistance <= 0.0) {
              if (DEBUG) Log.i(TAG, "Discarding zero-duration/zero-distance lap in createNewLap");
              return;
          }

          double lapSpeed = (lapTime_s > 0) ? (lapDistance / lapTime_s) : 0.0;
          saveLap(prevLapNr, lapTime_s, lapDistance, lapSpeed);
      }
  }
  ```
* **Guard in `mLapSummaryReceiver`**:
  ```java
  private final BroadcastReceiver mLapSummaryReceiver = new BroadcastReceiver() {
      public void onReceive(Context context, @NonNull Intent intent) {
          if (DEBUG) Log.i(TAG, "received lap summary intent");

          int lapTime = intent.getIntExtra(BANALService.PREV_LAP_TIME_S, 0);
          double lapDistance = intent.getDoubleExtra(BANALService.PREV_LAP_DISTANCE_m, 0);

          // ATT-896: Guard against persisting empty broadcast splits
          if (lapTime <= 0 && lapDistance <= 0.0) {
              if (DEBUG) Log.i(TAG, "Ignoring zero-duration lap summary broadcast");
              return;
          }

          double lapSpeed = intent.getDoubleExtra(BANALService.PREV_LAP_SPEED_mps, 0);
          if (Double.isNaN(lapSpeed) || Double.isInfinite(lapSpeed)) {
              lapSpeed = (lapTime > 0) ? (lapDistance / lapTime) : 0.0;
          }

          saveLap(intent.getIntExtra(BANALService.PREV_LAP_NR, 0),
                  lapTime,
                  lapDistance,
                  lapSpeed);
      }
  };
  ```

### 3.3 Storage Layer Validation & Historical Filtering ([LapsDatabaseManager.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/LapsDatabaseManager.java))
* **Defensive Guard in `saveLap(...)`**:
  ```java
  public void saveLap(long workoutId, long lapNr, @Nullable String timeStart, int lapTime, double lapDistance, double averageSpeed, @Nullable String name, @Nullable String description) {
      // ATT-896: Strict rejection of zero-duration / zero-distance laps
      if (lapTime <= 0 && lapDistance <= 0.0) {
          Log.w(TAG, "Rejecting zero-duration/zero-distance lap for workoutId: " + workoutId + ", lapNr: " + lapNr);
          return;
      }

      double safeAverageSpeed = (Double.isNaN(averageSpeed) || Double.isInfinite(averageSpeed))
              ? ((lapTime > 0) ? (lapDistance / lapTime) : 0.0)
              : averageSpeed;
      ...
  ```
* **Filter in `getLaps(long workoutId)`**:
  ```java
  try (Cursor cursor = db.query(
          Laps.TABLE,
          null,
          Laps.WORKOUT_ID + " = ? AND (" + Laps.TIME_TOTAL_s + " > 0 OR " + Laps.DISTANCE_TOTAL_m + " > 0)",
          new String[]{String.valueOf(workoutId)},
          null,
          null,
          Laps.LAP_NR + " ASC, " + Laps.C_ID + " ASC"
  ))
  ```
* **Filter in `getLapsForWorkouts(@NonNull List<Long> workoutIds)`**:
  ```java
  String selection = Laps.WORKOUT_ID + " IN (" + sb.toString() + ") AND (" + Laps.TIME_TOTAL_s + " > 0 OR " + Laps.DISTANCE_TOTAL_m + " > 0)";
  ```

---

## 4. Test & Verification Plan

### 4.1 Automated Unit Tests (SWE.4)
* **1. `TrainingApplicationLapTest.kt`**:
  - Test `pauseTracking()` sets `cTrackingMode = PAUSED` and does not broadcast `REQUEST_NEW_LAP`.
  - Test `resumeFromPaused()` sets `cTrackingMode = TRACKING` and does not broadcast `REQUEST_NEW_LAP`.
* **2. `TrackerServiceLapTest.kt`**:
  - Test `createNewLap()` when `lapTime_s == 0 && lapDistance == 0.0`: verify `saveLap` is not invoked.
  - Test `createNewLap()` when `lapTime_s = 600 && lapDistance = 2000.0`: verify `saveLap` is invoked with valid metrics and no `NaN`.
  - Test `mLapSummaryReceiver` drops intents with `lapTime <= 0 && lapDistance <= 0.0`.
* **3. `LapsDatabaseManagerGuardTest.kt`**:
  - Test `saveLap` rejects `lapTime = 0, lapDistance = 0.0` (zero rows inserted into `Laps.TABLE`).
  - Test `saveLap` accepts `lapTime = 300, lapDistance = 1000.0`.
  - Test `getLaps(workoutId)` ignores legacy/raw rows in SQLite with `timeTotal_s = 0 && distanceTotal_m = 0`.
  - Test `getLapsForWorkouts(chunkIds)` ignores legacy zero-duration rows across batch queries.

### 4.2 Full Repository Clean-Room Regression (SWE.5)
* Execute `./gradlew testDebugUnitTest`: Verify 100% test pass (0 failures, 0 regressions).

---

## 5. System Invariants & Safety Audit

* **Manual Lap Splitting**: Manual lap button clicks via `TrackingViewsRepository.requestNewLap()` and Pebble `BUTTON_LAP` continue to broadcast `REQUEST_NEW_LAP`, closing the current lap and starting the next one.
* **Sensor Pause Tracking**: `ClockDevice` accumulator sensors (`mActiveTimeSensor_s`, `mLapTimeSensor_s`) have `respectPause = true`, guaranteeing they do not accumulate while paused.
* **Database Schema Integrity**: No changes to `Laps.db` table schema (`DB_VERSION = 2` preserved).
* **TCX/GPX Compatibility**: Exporting workouts will produce valid `<Lap>` structures with strictly positive durations, matching TCX v2 schema requirements.
