# Stage 2: Requirement & Test Specification - ATT-4095: [Medium] Reuse in-memory LiveWorkoutSession on RESUME_BY_USER and publish session only after rehydration

**Ticket**: [ATT-4095](https://atrainingtracker.atlassian.net/browse/ATT-4095)  
**Sub-task**: [ATT-4142](https://atrainingtracker.atlassian.net/browse/ATT-4142) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-1153](https://atrainingtracker.atlassian.net/browse/ATT-1153) (*Sensor Architecture & Concurrency Stability*)  
**Target Release**: `Next Minor Release` (V4.9.40)  
**Active Sprint**: `2026-42.10`  
**Requirement Mapping**: `REQ-TRK-036` (*In-Memory LiveWorkoutSession Reuse on User Resume and Safe Publication after Rehydration*)  
**Test Spec ID**: `TST-TRK-029`  
**Branch**: `feature/ATT-4095`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Requirement Specification (`REQ-TRK-036`)

### 1.1 Problem Statement & Rationale
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

### 1.2 Functional & Architectural Requirements (`REQ-TRK-036`)

1. **In-Memory Session Reuse on `RESUME_BY_USER` (`REQ-TRK-036.1`)**:
   - In `TrackerService.applyResumedState(@NonNull ResumedWorkoutState state)`, when `mLiveSession != null && mLiveSession.getWorkoutId() == state.workoutId && !mIsRehydrating`, `TrackerService` SHALL reuse the existing `mLiveSession` instance and SHALL NOT overwrite it with a new `LiveWorkoutSession` or trigger stream rehydration from SQLite.
   - Reusing the in-memory session SHALL preserve all 1 Hz pre-pause trackpoints in `mLiveSession.getRawLatLngs()`, enabling high-fidelity Douglas-Peucker route simplification upon workout stop.
   - In this reuse path, `TrackerService` SHALL complete `mTableInitializationFuture` (`complete(null)`) and broadcast `notifyTrackingStarted(mWorkoutID)` immediately.

2. **Atomic Construction, Rehydration & Publication on `mDbExecutor` (`REQ-TRK-036.2`)**:
   - When a new session must be created (`mLiveSession == null || mLiveSession.getWorkoutId() != state.workoutId` or `mIsRehydrating == true`), `TrackerService` SHALL set `mIsRehydrating = true` prior to task submission.
   - In the rehydration task on `mDbExecutor`: `LiveWorkoutSession newSession = new LiveWorkoutSession(resumedWorkoutId, IMPORTANT_SENSOR_TYPES);`.
   - `rehydrateLiveSession(newSession, resumedWorkoutId, resumedActiveTimeS)` SHALL populate the candidate session directly.
   - The newly rehydrated session SHALL be published to the `volatile mLiveSession` reference (`mLiveSession = newSession;`) strictly **after** database stream and extrema decoding completes.
   - In `finally`: `mIsRehydrating = false`, `mTableInitializationFuture.complete(null)`, and `notifyTrackingStarted(resumedWorkoutId)`.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
1. **Original Requirement ID & Target**: Net-new requirement `REQ-TRK-036`, extending `REQ-TRK-031` (`ATT-3987`). Targets `TrackerService.java`, `LiveWorkoutSession.java`, and `TrackerServiceResumeTest.kt`.
2. **Historical Origin & Commit Trace**: Ticket `ATT-4095`, Sprint `2026-42.10`, following Sprint `2026-42.9` code review of `ATT-3987` (commit `8123bb6f`).
3. **Root Reason for Existing Formulation**: `ATT-3987` added rehydration to recover sessions after process death, but unconditionally recreated `mLiveSession` even on simple user pause/resume, losing 1 Hz pre-pause trackpoints to 20s downsampled DB points and exposing empty sessions during asynchronous rehydration.
4. **Preservation of Core Invariants**: Full stream and extrema rehydration on service recreation/process death, `mTableInitializationFuture` barrier, non-blocking main-thread resume, and 100% clean-room test pass rate strictly preserved.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (In-Memory Session Reuse & 1 Hz Preservation)**:
  * *Given* an active `TrackerService` with `mLiveSession` holding 1 Hz `rawLatLngs` for `workoutId = 100`,
  * *When* the user pauses and resumes via `RESUME_BY_USER`,
  * *Then* `applyResumedState()` preserves the identical `mLiveSession` instance and its 1 Hz `rawLatLngs` without database rehydration.
* **Criterion 2 (Process Recreation Fresh Instance Construction & DB Rehydration)**:
  * *Given* `TrackerService` starting after process death (`mLiveSession == null`),
  * *When* `applyResumedState()` executes,
  * *Then* a new `LiveWorkoutSession` is created and rehydrated on `mDbExecutor`, and `mLiveSession` is published only after rehydration completes.
* **Criterion 3 (Publication Safety on Rehydration)**:
  * *Given* `TrackerService` executing asynchronous rehydration on `mDbExecutor`,
  * *When* inspected prior to rehydration task completion,
  * *Then* un-rehydrated sessions are never exposed via `mLiveSession` to concurrent callers, and `isRehydrating()` evaluates to `true` throughout rehydration.

### 1.5 System Invariants
* **Non-Blocking Main Thread**: Database queries and stream decodings during rehydration MUST execute on `mDbExecutor`.
* **Clean-Room Regression Suite**: 100% pass rate across the full test suite (`./gradlew testDebugUnitTest`).
* **Localization Invariant**: No user-facing text strings are altered.

---

## 2. Test Specification (`TST-TRK-029`)

### Test Case 1: In-Memory Session Reuse & 1 Hz `rawLatLngs` Preservation (`TST-TRK-029.1`)
* **Scope**: Unit test / Resumption lifecycle
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceResumeTest.kt`
* **Preconditions**: `TestableTrackerService` initialized with an active `LiveWorkoutSession` for `workoutId = 100` containing 5 pre-pause 1 Hz `rawLatLngs` points.
* **Action**:
  1. Construct `ResumedWorkoutState` with `workoutId = 100`.
  2. Invoke `service.applyResumedState(state)`.
* **Expected Result**:
  - `service.liveSession` is the exact same instance (`assertSame(existingSession, service.liveSession)`).
  - `service.liveSession.rawLatLngs.size == 5`.
  - Zero calls to `mockDbManager.getEncodedStreams(100)` or `getExtremaForWorkouts(...)`.
  - `service.tableInitializationFuture.isDone == true`.
  - `service.notifiedWorkoutId == 100L`.

### Test Case 2: Process Recreation Fresh Instance Construction & DB Rehydration (`TST-TRK-029.2`)
* **Scope**: Unit test / Recreation lifecycle
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceResumeTest.kt`
* **Preconditions**: `TestableTrackerService` with `mLiveSession = null`. Mocked `EncodedStreams` in `WorkoutSummariesDatabaseManager`.
* **Action**:
  1. Construct `ResumedWorkoutState` with `workoutId = 101`.
  2. Invoke `service.applyResumedState(state)`.
* **Expected Result**:
  - `service.liveSession != null`.
  - `service.liveSession.workoutId == 101L`.
  - `mockDbManager.getEncodedStreams(101)` is invoked.
  - `service.liveSession.sampledLatLngs` contains decoded stream points.
  - `service.tableInitializationFuture.isDone == true`.

### Test Case 3: Publication Safety & Atomic Assignment on `mDbExecutor` (`TST-TRK-029.3`)
* **Scope**: Unit test / Concurrency contract
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceResumeTest.kt`
* **Preconditions**: `TestableTrackerService` with `mLiveSession = null` and a custom queue-capturing `ExecutorService`.
* **Action**:
  1. Call `applyResumedState(state)`.
  2. Before running the captured rehydrate task, assert `service.liveSession == null` and `service.isRehydrating() == true`.
  3. Execute the rehydrate task.
  4. Assert `service.liveSession != null`, `service.isRehydrating() == false`, and `service.tableInitializationFuture.isDone == true`.
* **Expected Result**: Un-rehydrated sessions are never prematurely published to `mLiveSession`.

### Test Case 4: Clean-Room Full Suite Regression (`TST-TRK-029.4`)
* **Scope**: Full clean-room regression
* **Command**: `./gradlew testDebugUnitTest`
* **Expected Result**: 100% tests pass (3,200+ tests, 0 failures, 0 regressions).

---

## 3. Traceability Matrix

| Requirement | Test Spec | Scope | Test Target | Result | Status |
| :--- | :--- | :--- | :--- | :---: | :---: |
| `REQ-TRK-036.1` | `TST-TRK-029.1` | Unit Contract | `TrackerService` (In-memory session reuse, 1 Hz `rawLatLngs` retention) | **TBD** | `Specified` |
| `REQ-TRK-036.2` | `TST-TRK-029.2` | Unit Contract | `TrackerService` (Process recreation rehydration from SQLite) | **TBD** | `Specified` |
| `REQ-TRK-036.2` | `TST-TRK-029.3` | Concurrency Contract | `TrackerService` (Publication safety on `mDbExecutor`, `mIsRehydrating` guard) | **TBD** | `Specified` |
| `REQ-TRK-036` | `TST-TRK-029.4` | Clean-Room Regression | Full test suite (`./gradlew testDebugUnitTest`) | **TBD** | `Specified` |
