# Stage 1 Analysis: ATT-4095 — Reuse in-memory LiveWorkoutSession on RESUME_BY_USER and publish session only after rehydration

## 1. Executive Summary
- **Ticket**: `ATT-4095` — `[Verbesserung] [Medium] Reuse in-memory LiveWorkoutSession on RESUME_BY_USER and publish session only after rehydration` (Epic: `ATT-1153` *Sensor Architecture & Concurrency Stability*)
- **Classification**: State Recovery, In-Memory Session Preservation & Thread-Safe Rehydration (Defect Prevention / Data Fidelity)
- **Root Cause**: In Sprint `2026-42.9`, `ATT-3987` (commit `8123bb6f`) implemented `LiveWorkoutSession` stream and running statistics rehydration upon workout resumption (`REQ-TRK-031.3`). During the sprint review of `ATT-3987`, two architectural optimization opportunities were flagged in `TrackerService.applyResumedState(...)` and `rehydrateLiveSession(...)`:
  1. **Disruption of High-Resolution 1 Hz Points on In-Memory User Resumption (`RESUME_BY_USER`)**:
     When the user pauses tracking and subsequently resumes (`RESUME_BY_USER`), if `TrackerService` has remained continuously alive in memory (`mLiveSession != null && mLiveSession.getWorkoutId() == state.workoutId && !mIsRehydrating`), `applyResumedState()` unconditionally destroys the existing `mLiveSession` instance via `mLiveSession = new LiveWorkoutSession(mWorkoutID, IMPORTANT_SENSOR_TYPES);` and schedules an asynchronous rehydration task from SQLite.
     Because SQLite only persists 20-second downsampled stream points (`MAP_POLYLINE`, `ALTITUDE_STREAM`, `DISTANCE_STREAM`), replacing the live in-memory session irreversibly discards all high-fidelity 1 Hz raw GPS trackpoints (`rawLatLngs`) collected prior to the pause. Consequently, when the workout is stopped, Douglas-Peucker route simplification (`finalizeLiveSession`) is forced to fall back to the 20-second sampled points instead of operating on the high-resolution 1 Hz track.
     Reusing the existing `mLiveSession` when the service process was not killed and the session is already active for the same `workoutId` preserves full 1 Hz track fidelity and avoids redundant database queries and stream decodings.
  2. **Race Hazard: Publishing Incomplete Session Before Asynchronous Rehydration Completes**:
     When a new `LiveWorkoutSession` *must* be constructed (e.g. after process death / `RESUME_SERVICE_RECREATION`, or when `mLiveSession == null`), `applyResumedState()` currently assigns `mLiveSession = new LiveWorkoutSession(mWorkoutID, IMPORTANT_SENSOR_TYPES);` to the `volatile` instance variable on the calling thread, and then submits `rehydrateLiveSession(resumedWorkoutId, resumedActiveTimeS)` to `mDbExecutor`.
     While `mIsRehydrating = true` guards 1 Hz tick updates in `sampleAndWriteToDb()` (`if (mLiveSession != null && !mIsRehydrating)`), other concurrent receivers—specifically `mAltitudeCorrectionReceiver`—inspect `session = mLiveSession; if (session != null) session.applyAltitudeCorrection(...)` without checking `mIsRehydrating`.
     Furthermore, assigning `mLiveSession` before rehydration completes creates a window where callers inspecting `getLiveSession()` or concurrent background tasks could observe an un-rehydrated session with empty streams and unpopulated extrema.
     Constructing and rehydrating the new session in a local reference on `mDbExecutor`, and only assigning it to `volatile mLiveSession` once rehydration completes (while maintaining `mIsRehydrating = true` throughout), guarantees strict publishing safety and atomicity.

---

## 2. Problem Domain & Concurrency Topology

### 2.1 Concurrency & Lifecycle Topology
Workout resumption in `TrackerService` occurs across two distinct lifecycle flows:
1. **User Manual Pause & Resume (`RESUME_BY_USER`)**:
   - The user taps Pause in the UI, then taps Resume.
   - `TrainingApplication.pauseTracking(...)` updates pause state; `TrackerService` continues running as an Android Foreground Service.
   - When resumed, an Intent with action `RESUME_BY_USER` is delivered to `onStartCommand()`.
   - The service process and its heap remain completely intact: `mLiveSession` already contains all 1 Hz `rawLatLngs`, `sampledLatLngs`, `sampledAltitudes`, `sampledDistances`, and current `sensorStats` (running min, max, avg, counts).
2. **Process Recreation / Crash Recovery (`RESUME_SERVICE_RECREATION`)**:
   - The Android OS terminates `TrackerService` under memory pressure (LMK) or device restart while a workout is active.
   - Upon restart, `TrainingApplication` detects the unfinished workout and starts `TrackerService` with `RESUME_SERVICE_RECREATION`.
   - In this scenario, `TrackerService` is a fresh instance; `mLiveSession` is `null`.
   - A new `LiveWorkoutSession` must be constructed and populated from SQLite (`WorkoutSummariesDatabaseManager.getEncodedStreams()` and `getExtremaForWorkouts()`).

### 2.2 Forensic Inspection of `TrackerService.java`

Currently in `TrackerService.java:1392-1414`:
```java
// Problem 1: Unconditional new instance overwrites alive in-memory session on RESUME_BY_USER
mLiveSession = new LiveWorkoutSession(mWorkoutID, IMPORTANT_SENSOR_TYPES);
mIsRehydrating = true;
final long resumedWorkoutId = mWorkoutID;
final int resumedActiveTimeS = mTimeActive_s;
Runnable rehydrateTask = () -> {
    try {
        rehydrateLiveSession(resumedWorkoutId, resumedActiveTimeS);
    } finally {
        mIsRehydrating = false;
        if (mTableInitializationFuture != null && !mTableInitializationFuture.isDone()) {
            mTableInitializationFuture.complete(null);
        }
        if (resumedWorkoutId > 0) {
            notifyTrackingStarted(resumedWorkoutId);
        }
    }
};
if (mDbExecutor != null && !mDbExecutor.isShutdown()) {
    mDbExecutor.execute(rehydrateTask);
} else {
    rehydrateTask.run();
}
```

And in `rehydrateLiveSession()`:
```java
void rehydrateLiveSession(long workoutId, int activeTimeSeconds) {
    final LiveWorkoutSession session = mLiveSession;
    if (session == null || workoutId <= 0) {
        return;
    }
    // queries db and calls session.rehydrateStreams(...) & session.rehydrateSensorStats(...)
}
```

### 2.3 Defects & Race Conditions Identified

1. **Destruction of Pre-Pause 1 Hz Trackpoints (`rawLatLngs`)**:
   - `LiveWorkoutSession` accumulates every 1 Hz position in `rawLatLngs` (`LiveWorkoutSession.java:225`).
   - Every 20 steps (20 seconds), a sampled point is appended to `sampledLatLngs`, delta-encoded, and persisted to `WorkoutSummaries.MAP_POLYLINE`.
   - When `mLiveSession = new LiveWorkoutSession(...)` is called on `RESUME_BY_USER`, all `rawLatLngs` collected up to that moment are garbage collected.
   - When rehydration finishes, `rawLatLngs` only contains the decoded 20-second points (`session.rehydrateStreams(decodedLatLngs, ...)` fills `rawLatLngs` from `preResumeLatLngs`).
   - Later, in `finalizeLiveSession()`, `pointsToSimplify = mLiveSession.getRawLatLngs()` simplifies from a 20-second downsampled path rather than the full 1 Hz GPS trajectory!
   - By preserving `mLiveSession` when `mLiveSession != null && mLiveSession.getWorkoutId() == state.workoutId && !mIsRehydrating`, the 1 Hz track is 100% retained.

2. **Publishing Un-Rehydrated Session (`Publication Safety`)**:
   - In `applyResumedState()`, assigning `mLiveSession = new LiveWorkoutSession(...)` immediately exposes an empty session to `mLiveSession`.
   - Even though `mIsRehydrating = true` prevents 1 Hz sampling in `sampleAndWriteToDb()`, other components:
     - `mAltitudeCorrectionReceiver` accesses `mLiveSession` without checking `mIsRehydrating`. If an altitude correction intent arrives while rehydration is running on `mDbExecutor`, it applies correction to an empty session before `rehydrateStreams` runs, leading to lost or overwritten elevation adjustments.
     - Any test or external caller querying `getLiveSession()` between `applyResumedState()` and `mTableInitializationFuture.complete(null)` observes a hollow session.
   - Solution: Create `LiveWorkoutSession candidate = new LiveWorkoutSession(mWorkoutID, IMPORTANT_SENSOR_TYPES);`, pass `candidate` into `rehydrateLiveSession(candidate, resumedWorkoutId, resumedActiveTimeS)`, execute the DB reads and stream decodings on `candidate`, and assign `mLiveSession = candidate;` **only after** rehydration is fully complete.

---

## 3. Chesterton's Fence Archaeology (`REQ-PRO-022`)

1. **Original Requirement ID & Target**:
   - Extending `REQ-TRK-031` (*LiveWorkoutSession Rehydration & Extrema Continuity on Workout Resumption*, `ATT-3987`, commit `8123bb6f`).
   - Targets `TrackerService.java`, `LiveWorkoutSession.java`, and `TrackerServiceResumeTest.kt`.
2. **Historical Origin & Commit Trace**:
   - Ticket `ATT-3987`, Sprint `2026-42.9`, commit `8123bb6f` (*"ATT-3987: Rehydrate LiveWorkoutSession streams, extrema, and sample counts on workout resume"*).
   - In `ATT-3987`, rehydration was designed primarily to solve the process-death / recreation scenario where `mLiveSession` was lost.
3. **Root Reason for Existing Formulation**:
   - `applyResumedState()` was written to unify both `RESUME_BY_USER` and `RESUME_SERVICE_RECREATION` through a single code path. Unconditionally recreating `mLiveSession` was simple and ensured that whatever was in SQLite became the session state. However, it failed to account for the fact that in `RESUME_BY_USER`, the in-memory session has strictly higher resolution data (1 Hz vs 20 s) than the database.
4. **Preservation of Core Invariants**:
   - **Stream Rehydration on Service Recreation**: If `mLiveSession == null` (or for a different workout ID), full stream and extrema rehydration from SQLite MUST occur.
   - **Running Extrema Continuity**: Min, max, average, and sample counts MUST remain continuous across pauses and restarts.
   - **Non-Blocking Main Thread**: Rehydration MUST execute on `mDbExecutor` without blocking the main thread.
   - **State Recovery Hierarchy (`REQ-TRK-018`)**: Pause state restoration across manual/auto pause MUST NOT be broken.
   - **Initialization Barrier (`mTableInitializationFuture`)**: `mTableInitializationFuture` MUST complete only after rehydration (or reuse verification) is finished.

---

## 4. User Scope Grounding (`ATT-1250`)

### In-Scope:
1. **Reuse In-Memory `LiveWorkoutSession` on `RESUME_BY_USER`**:
   - Check if `mLiveSession != null && mLiveSession.getWorkoutId() == state.workoutId && !mIsRehydrating`.
   - If true: reuse existing `mLiveSession`. Bypass database rehydration of streams and extrema (as they are already live and up-to-date in memory).
   - Complete `mTableInitializationFuture` and invoke `notifyTrackingStarted(mWorkoutID)`.
2. **Safe Publication of `LiveWorkoutSession` on Recreation / Rehydration**:
   - When a new session must be created (`mLiveSession == null || mLiveSession.getWorkoutId() != state.workoutId`):
   - Set `mIsRehydrating = true`.
   - In `rehydrateTask` on `mDbExecutor`: instantiate `LiveWorkoutSession newSession = new LiveWorkoutSession(resumedWorkoutId, IMPORTANT_SENSOR_TYPES);`.
   - Execute `rehydrateLiveSession(newSession, resumedWorkoutId, resumedActiveTimeS)`.
   - Assign `mLiveSession = newSession;` only after rehydration completes.
   - Reset `mIsRehydrating = false`, complete `mTableInitializationFuture`, and invoke `notifyTrackingStarted(resumedWorkoutId)`.
3. **Unit & Concurrency Tests**:
   - Add tests to `TrackerServiceResumeTest.kt` verifying:
     - On `RESUME_BY_USER` with alive `mLiveSession`, the identical session instance is preserved, and pre-pause 1 Hz `rawLatLngs` are retained.
     - On `RESUME_SERVICE_RECREATION` (or `mLiveSession == null`), a new session is constructed, rehydrated, and published only after DB rehydration finishes.
     - `mIsRehydrating` is correctly guarded.

### Out-of-Scope:
- Modifying `LiveWorkoutSession` stream encoding math or Douglas-Peucker epsilon algorithms.
- Changing `WorkoutSummariesDatabaseManager` schema or projections.
- Changing `AutoPauseManager` state transitions.

---

## 5. Architectural Design (SWE.2 & SWE.3 Preview)

### Refactoring `TrackerService.applyResumedState()`:
```java
@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
void applyResumedState(@NonNull ResumedWorkoutState state) {
    mBaseFileName = state.baseFileName;
    mSamplesTableName = resolveSamplesTableNameSafely(mBaseFileName);
    mWorkoutID = state.workoutId;
    mTimeTotal_s = state.timeTotal_s;
    mTimeActive_s = state.timeActive_s;
    mDistanceTotal_m = state.distanceTotal_m;

    if (mSamplesTableName == null) {
        Log.e(TAG, "applyResumedState: Invalid baseFileName (" + mBaseFileName + ") for workoutId=" + mWorkoutID);
        if (mTableInitializationFuture != null && !mTableInitializationFuture.isDone()) {
            mTableInitializationFuture.completeExceptionally(
                    new IllegalStateException("Invalid baseFileName on resume: " + mBaseFileName));
        }
        mTrackingInterrupted = true;
        showTrackingInterruptedNotification();
        performStopSelf();
        return;
    }

    BANALService.setInitialSensorValue(SensorType.TIME_TOTAL, mTimeTotal_s);
    BANALService.setInitialSensorValue(SensorType.TIME_ACTIVE, mTimeActive_s);
    BANALService.setInitialSensorValue(SensorType.CALORIES, state.calories);
    BANALService.setInitialSensorValue(SensorType.LAP_NR, state.lapNr);
    BANALService.setInitialSensorValue(SensorType.DISTANCE_m, mDistanceTotal_m);

    final long resumedWorkoutId = mWorkoutID;
    final int resumedActiveTimeS = mTimeActive_s;

    // Check if in-memory LiveWorkoutSession can be reused (RESUME_BY_USER with alive session)
    if (mLiveSession != null && mLiveSession.getWorkoutId() == resumedWorkoutId && !mIsRehydrating) {
        Log.i(TAG, "applyResumedState: Reusing existing in-memory LiveWorkoutSession for workoutId=" + resumedWorkoutId);
        if (mTableInitializationFuture != null && !mTableInitializationFuture.isDone()) {
            mTableInitializationFuture.complete(null);
        }
        if (resumedWorkoutId > 0) {
            notifyTrackingStarted(resumedWorkoutId);
        }
        return;
    }

    // Otherwise, construct and rehydrate a new session on mDbExecutor, publishing upon completion
    mIsRehydrating = true;
    Runnable rehydrateTask = () -> {
        try {
            LiveWorkoutSession newSession = new LiveWorkoutSession(resumedWorkoutId, IMPORTANT_SENSOR_TYPES);
            rehydrateLiveSession(newSession, resumedWorkoutId, resumedActiveTimeS);
            mLiveSession = newSession;
        } finally {
            mIsRehydrating = false;
            if (mTableInitializationFuture != null && !mTableInitializationFuture.isDone()) {
                mTableInitializationFuture.complete(null);
            }
            if (resumedWorkoutId > 0) {
                notifyTrackingStarted(resumedWorkoutId);
            }
        }
    };

    if (mDbExecutor != null && !mDbExecutor.isShutdown()) {
        mDbExecutor.execute(rehydrateTask);
    } else {
        rehydrateTask.run();
    }
}
```

### Signature Update for `rehydrateLiveSession()`:
```java
@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
void rehydrateLiveSession(@NonNull LiveWorkoutSession session, long workoutId, int activeTimeSeconds)
```
Accepting the target `LiveWorkoutSession` instance directly ensures complete isolation from `volatile mLiveSession` until fully populated.
