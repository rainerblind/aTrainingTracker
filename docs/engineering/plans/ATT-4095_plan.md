# Stage 3: Implementation Plan - ATT-4095: [Medium] Reuse in-memory LiveWorkoutSession on RESUME_BY_USER and publish session only after rehydration

**Ticket**: [ATT-4095](https://atrainingtracker.atlassian.net/browse/ATT-4095)  
**Sub-task**: [ATT-4143](https://atrainingtracker.atlassian.net/browse/ATT-4143) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-1153](https://atrainingtracker.atlassian.net/browse/ATT-1153) (*Sensor Architecture & Concurrency Stability*)  
**Target Release**: `Next Minor Release` (V4.9.40)  
**Active Sprint**: `2026-42.10`  
**Requirement Mapping**: `REQ-TRK-036` (*In-Memory LiveWorkoutSession Reuse on User Resume and Safe Publication after Rehydration*)  
**Test Mapping**: `TST-TRK-029` (*In-Memory LiveWorkoutSession Reuse on User Resume and Safe Publication Verification Suite*)  
**Branch**: `feature/ATT-4095`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Problem Description & Background

In Sprint `2026-42.9`, `ATT-3987` (commit `8123bb6f`) implemented `LiveWorkoutSession` stream and running statistics rehydration upon workout resumption (`REQ-TRK-031.3`). During the sprint review of `ATT-3987`, two architectural optimization opportunities were flagged in `TrackerService.applyResumedState(...)` and `rehydrateLiveSession(...)`:
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

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-TRK-036` (*In-Memory LiveWorkoutSession Reuse on User Resume and Safe Publication after Rehydration*)
  * `REQ-TRK-036.1`: In-Memory Session Reuse on `RESUME_BY_USER` preserving 1 Hz `rawLatLngs`
  * `REQ-TRK-036.2`: Atomic Construction, Rehydration & Publication on `mDbExecutor`
* **Test Mapping**: `TST-TRK-029` (`TST-TRK-029.1` – `TST-TRK-029.4`)

---

## 3. System Invariants & Preserved Behavior

1. **Stream Rehydration Continuity**: Full stream and extrema rehydration from SQLite MUST occur whenever `mLiveSession == null` or when `mLiveSession.getWorkoutId() != state.workoutId`.
2. **Running Extrema Continuity**: Running min, max, average, and sample counts MUST remain continuous across pauses and restarts.
3. **Non-Blocking Main Thread**: Database queries and stream decodings during rehydration MUST execute on `mDbExecutor` without blocking the Android main thread.
4. **Initialization Barrier**: `mTableInitializationFuture` MUST complete only after rehydration (or reuse verification) is finished.
5. **UI Consistency (Rule 23)**: No UI changes are introduced by this service concurrency and state recovery ticket.
6. **Clean-Room Regression Invariant**: 100% of existing unit tests must pass without failure.

---

## 4. Proposed Architectural Changes (SWE.2)

### Component 1: `TrackerService.java`
* In `applyResumedState(@NonNull ResumedWorkoutState state)`:
  * Check if `mLiveSession != null && mLiveSession.getWorkoutId() == state.workoutId && !mIsRehydrating`.
  * If true:
    - Log reuse: `Log.i(TAG, "applyResumedState: Reusing existing in-memory LiveWorkoutSession for workoutId=" + mWorkoutID);`.
    - Complete `mTableInitializationFuture` if present: `if (mTableInitializationFuture != null && !mTableInitializationFuture.isDone()) mTableInitializationFuture.complete(null);`.
    - Broadcast started: `if (mWorkoutID > 0) notifyTrackingStarted(mWorkoutID);`.
    - Return immediately, bypassing SQLite rehydration task.
  * If false:
    - Set `mIsRehydrating = true;`.
    - Package the rehydration task for `mDbExecutor`:
      ```java
      Runnable rehydrateTask = () -> {
          try {
              LiveWorkoutSession candidate = new LiveWorkoutSession(resumedWorkoutId, IMPORTANT_SENSOR_TYPES);
              rehydrateLiveSession(candidate, resumedWorkoutId, resumedActiveTimeS);
              mLiveSession = candidate;
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
      ```
    - Execute on `mDbExecutor` if active, otherwise run inline.
* In `rehydrateLiveSession(@NonNull LiveWorkoutSession session, long workoutId, int activeTimeSeconds)`:
  * Update signature to accept `@NonNull LiveWorkoutSession session` directly instead of referencing `mLiveSession`.
  * Populate `session.rehydrateStreams(...)` and `session.rehydrateSensorStats(...)`.
  * Provide overload `void rehydrateLiveSession(long workoutId, int activeTimeSeconds)` for backward-compatibility with tests or internal callers, delegating to `if (mLiveSession != null) rehydrateLiveSession(mLiveSession, workoutId, activeTimeSeconds);`.

---

## 5. Atomic Implementation Steps (SWE.3)

### Step 1: Update `rehydrateLiveSession` Signature in `TrackerService.java`
* Add `@VisibleForTesting void rehydrateLiveSession(@NonNull LiveWorkoutSession session, long workoutId, int activeTimeSeconds)`.
* Retain `void rehydrateLiveSession(long workoutId, int activeTimeSeconds)` as a delegating overload.
* Target: `app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`.

### Step 2: Refactor `applyResumedState` in `TrackerService.java`
* Add condition to reuse `mLiveSession` if `mLiveSession != null && mLiveSession.getWorkoutId() == state.workoutId && !mIsRehydrating`.
* In the recreate path, instantiate `LiveWorkoutSession candidate` locally in `rehydrateTask`, pass `candidate` to `rehydrateLiveSession(candidate, ...)`, and assign `mLiveSession = candidate;` upon completion before `mIsRehydrating = false`.
* Target: `app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`.

### Step 3: Implement Unit Tests in `TrackerServiceResumeTest.kt`
* Add `testResumedSession_whenServiceRemainsAlive_reusesLiveSessionAndPreservesRawLatLngs()` (`TST-TRK-029.1`).
* Add `testResumedSession_whenServiceRecreated_constructsNewSessionAndRehydratesFromDb()` (`TST-TRK-029.2`).
* Add `testResumedSession_publicationSafety_assignsLiveSessionOnlyAfterRehydrationCompletes()` (`TST-TRK-029.3`).
* Target: `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceResumeTest.kt`.

### Step 4: Verification & Clean-Room Regression
* Run targeted tests: `./gradlew testDebugUnitTest --tests "*TrackerServiceResumeTest*"`.
* Run full test suite: `./gradlew testDebugUnitTest`.

---

## 6. Review Gate 3 Preparation
- Subtask: `ATT-4143` (`[Impl-Plan]`)
- Verification: Gate 3 audit via `python3 tools/review_agent.py audit ATT-4143`.
