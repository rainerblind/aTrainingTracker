# Stage 3 Implementation Plan - ATT-7668: Fix Health Connect ExerciseRoute Crash on Duplicate Timestamps & End-Time Boundary

**Ticket**: [ATT-7668](https://rainerblind.atlassian.net/browse/ATT-7668)  
**Parent Epic**: [ATT-162](https://rainerblind.atlassian.net/browse/ATT-162) (*Cloud Integration*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5`  
**Requirement Mapping**: `REQ-SET-101` (`REQ-SET-101.1`, `REQ-SET-101.2`) | **Test Spec ID**: `TST-SET-101` (`TST-SET-101.6`, `TST-SET-101.7`, `TST-SET-101.8`)  
**Branch**: `feature/ATT-7668` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-13  

---

## 1. Architecture & Component Decomposition (SWE.2)
- **Component & Layer Responsibilities**:
  - `cloud/healthconnect/HealthConnectRecordMapper.kt`:
    - `extractRecords()`: Deduplicates route locations by `Instant` (`time`) and sorts ascending before constructing `ExerciseRoute`. Similarly deduplicates series samples (`hrSamples`, `powerSamples`, etc.) to guarantee strictly monotonic sensor series.
    - `buildExerciseSessionRecord()`: Inspects `route` and computes session `endTime = maxOf(startTime.plusSeconds(durationSec), (maxRouteTime ?: startTime).plusSeconds(1))`, guaranteeing `maxRouteTime.isBefore(endTime)` per Health Connect SDK requirements.
    - Synchronizes `endTime` across session record and parent-bounded series records (`DistanceRecord`, `ElevationGainedRecord`, `TotalCaloriesBurnedRecord`).
  - `cloud/healthconnect/HealthConnectManager.kt`:
    - Consumes mapped records from `extractRecords()`. Benefits from zero unhandled `IllegalArgumentException` on outdoor GPS workouts with timestamp collisions or boundary samples.

---

## 2. Atomic Implementation Steps
1. **Step 1 - Route & Series Timestamp Deduplication (`REQ-SET-101.2`)**:
   - In `HealthConnectRecordMapper.extractRecords()`, deduplicate `routeLocations` by `it.time` and sort ascending: `val distinctRouteLocations = routeLocations.distinctBy { it.time }.sortedBy { it.time }`.
   - Ensure series samples (`hrSamples`, `powerSamples`, `speedSamples`, `cyclingCadenceSamples`, `stepsCadenceSamples`) are deduplicated by `time` to prevent redundant same-second records.
2. **Step 2 - Session EndTime Envelope Expansion (`REQ-SET-101.1`)**:
   - In `HealthConnectRecordMapper.buildExerciseSessionRecord()`, inspect `route.route` (if present) and extend `endTime` to `maxOf(endTime, maxRouteTime.plusSeconds(1))` whenever `!maxRouteTime.isBefore(endTime)`.
   - In `HealthConnectRecordMapper.extractRecords()`, synchronize `endTime` with the maximum route timestamp before instantiating `DistanceRecord`, `ElevationGainedRecord`, and `TotalCaloriesBurnedRecord`.
3. **Step 3 - Unit Test Suite Extension (`TST-SET-101.6`, `TST-SET-101.7`, `TST-SET-101.8`)**:
   - In `HealthConnectRecordMapperTest.kt`, add `testRouteDeduplicatesDuplicateWholeSecondTimestamps()` asserting `ExerciseRoute` constructs cleanly when `WorkoutSamples.db` contains multiple GPS fixes in the same second.
   - In `HealthConnectRecordMapperTest.kt`, add `testSessionEndTimeExtendedWhenRoutePointAtOrPastDuration()` asserting `maxRouteTime < sessionRecord.endTime` and `ExerciseSessionRecord` constructs cleanly when sample timestamp equals `startTime + totalTimeSec`.
   - Verify `testTracklessIndoorSessionExtractionParity()` maintains `exerciseRoute == null` and standard `endTime`.
4. **Step 4 - Automated Verification & Governance**:
   - Run unit tests: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.cloud.healthconnect.*"`.
   - Run requirement governance check: `python3 tools/verify_requirement_governance.py`.

---

## 3. Invariant Protection & System Properties
- **Strict Timestamp Monotonicity**: `require(sortedRoute[i].time.isBefore(sortedRoute[i + 1].time))` is guaranteed across 100% of generated `ExerciseRoute` objects.
- **Strict Range Envelope**: `require(!minTime.isBefore(startTime) && maxTime.isBefore(endTime))` is guaranteed for all `ExerciseSessionRecord` objects containing route data.
- **GPS Coordinate Fidelity**: Exact latitude, longitude, and altitude values of the first sample per second are preserved without artificial rounding or interpolation.
- **Trackless Indoor Parity (`REQ-SET-102`)**: Indoor workouts without GPS remain completely untouched with `exerciseRoute = null`.
- **Database Schema & Exporter Invariant**: Zero changes to SQLite databases (`WorkoutSamples.db`, `WorkoutSummaries.db`, `ExportStatus.db`) or file export pipelines.

---

## 4. UI Consistency & Design Guidelines Compliance
- Non-visual backend mapping bugfix. No Jetpack Compose UI or Settings screen modifications required.

---

## 5. Verification Strategy & Living Documentation
- Test Scope: `HealthConnectRecordMapperTest`, `HealthConnectSyncTest`.
- Status Lifecycle: Advance `REQ-SET-101` and `TST-SET-101` to `[Implemented]` in Stage 4 and `[Verified]` in Stage 5.
