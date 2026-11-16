# Stage 2: Requirement & Test Specification - ATT-7877: Rehydrate Cockpit Telemetry After Process Restart

**Ticket**: [ATT-7877](https://rainerblind.atlassian.net/browse/ATT-7877)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.8`  
**Requirement Mapping**: `REQ-TRK-080` (*Mid-Workout Process Restart Cockpit Telemetry, Live Map Track & Sparkline Rehydration*)  
**Test Spec ID**: `TST-TRK-080` (`TST-TRK-080.1`..`TST-TRK-080.5`)  
**Branch**: `feature/ATT-7877`  
**Author**: AI Agent 2 (Specification / Subagent Flash)  
**Date**: 2026-11-16  

---

## 1. Requirement Specification (REQ-TRK-080 [Planned])
- **Priority**: High | **Type**: Functional | **Verification Method**: Unit
- **REQ-TRK-080.1 (Asynchronous Rehydration Dispatch & State Gating)**: When `BANALServiceRepository` observes `TrackingMode.TRACKING` or `TrackingMode.PAUSED` for an active workout session with valid `baseFileName` / `workoutId` while `_currentTrack.value.isEmpty()`, it SHALL dispatch an asynchronous rehydration task on `Dispatchers.IO` to load persisted coordinates and numeric sensor histories from `WorkoutSamples.db`. Rehydration SHALL NOT execute during fresh workout starts (`StartType.START_NORMAL`). UI telemetry emissions SHALL preserve `Lifecycle.State.STARTED` gating (`REQ-TRK-079.3`).
- **REQ-TRK-080.2 (Live Map Track & PathPoint Rehydration)**: Persisted GPS samples (`LATITUDE`, `LONGITUDE`, `DISTANCE_m`, `ALTITUDE`) ordered chronologically by `TIME` SHALL be retrieved from `WorkoutSamples.db` and converted into `List<LatLng>` and `List<PathPoint>`. `_currentTrack` and `_currentPathPoints` SHALL be atomically populated using `SnapshotChunkedList.from()`. Any live GPS updates arriving concurrently during async query SHALL be merged monotonically at the tail without dropping coordinates or generating out-of-order vertices.
- **REQ-TRK-080.3 (LiveSparklineBuffer Horizon Historical Backfill)**: For registered `sparklineBuffers`, `LiveSparklineBuffer` SHALL support `backfillHistoricalSamples(samples: List<Float>)` under thread-safe synchronization (`synchronized(lock)`). Query results for the corresponding numeric sensor column (`HR`, `SPEED_mps`, `POWER_W`, `CADENCE`, `ALTITUDE`) across the past `horizonSeconds` window SHALL populate the buffer up to `maxCapacity`. Live samples appended concurrently via `updateSparklineBuffers()` SHALL be preserved monotonically.
- **REQ-TRK-080.4 (Localization Neutrality & System Invariants)**: Rehydration operates exclusively within the data and repository layer; zero new UI string resources or layout changes are required across all 9 supported locales. Main thread execution MUST NOT be blocked. Memory allocations MUST remain strictly bounded via `SnapshotChunkedList` and `LiveSparklineBuffer` capacities.

### Acceptance Criteria & System Invariants
- **Given-When-Then**:
  1. *Given* an active workout with 15 minutes of persisted 1-Hz telemetry in `WorkoutSamples.db` and configured Cockpit sparklines, *When* the app recovers from background process restart (`RESUME_SERVICE_RECREATION`) or UI reconnection, *Then* `_currentTrack`, `_currentPathPoints`, and registered `sparklineBuffers` backfill asynchronously on `Dispatchers.IO`, restoring map routes and telemetry trends immediately.
  2. *Given* an in-flight rehydration query on `Dispatchers.IO`, *When* new 1-Hz GPS or sensor ticks arrive from `BANALService`, *Then* incoming samples merge monotonically at the tail with zero data loss and zero race conditions.
  3. *Given* a new workout initialized via `StartType.START_NORMAL`, *When* `BANALServiceRepository` starts, *Then* rehydration is omitted and track collections remain empty.
- **System Invariants**: Zero main-thread blocking (`Dispatchers.IO`); bounded memory via `SnapshotChunkedList` and `LiveSparklineBuffer`; monotonic timestamp ordering; clean-room test pass rate.

---

## 2. Test Specification & Traceability (TST-TRK-080 [Planned])

| Test ID | Scope | Target Component / Procedure | Traces To | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-080.1` | Unit | `BANALServiceRepositoryTest`: verify observing `TrackingMode.TRACKING`/`PAUSED` triggers async rehydration on `Dispatchers.IO`; verify `STOPPED` and `START_NORMAL` omit rehydration. | `REQ-TRK-080.1` | Specified |
| `TST-TRK-080.2` | Unit | `BANALServiceRepositoryTest`: mock `WorkoutSamplesDatabaseManager` returning historical GPS rows; verify `_currentTrack` and `_currentPathPoints` populated via `SnapshotChunkedList.from()`. | `REQ-TRK-080.2` | Specified |
| `TST-TRK-080.3` | Unit | `LiveSparklineBufferTest`: call `backfillHistoricalSamples` with oversized sample list; assert buffer contains newest `maxCapacity` samples and `currentSpanSeconds` updates accurately. | `REQ-TRK-080.3` | Specified |
| `TST-TRK-080.4` | Concurrency | `LiveSparklineBufferTest` & `BANALServiceRepositoryTest`: simulate concurrent live `addSample()` / location update during async backfill; assert thread safety and monotonic order. | `REQ-TRK-080.2`, `REQ-TRK-080.3` | Specified |
| `TST-TRK-080.5` | Suite | Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.repositories.BANALServiceRepositoryTest" --tests "com.atrainingtracker.trainingtracker.ui.sparkline.LiveSparklineBufferTest"`. | `REQ-TRK-080.4` | Specified |
