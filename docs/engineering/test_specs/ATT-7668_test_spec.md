# Stage 2: Requirement & Test Specification - ATT-7668: Fix Health Connect ExerciseRoute Crash on Duplicate Timestamps & End-Time Boundary

**Ticket**: [ATT-7668](https://rainerblind.atlassian.net/browse/ATT-7668) | **Parent Epic**: [ATT-162](https://rainerblind.atlassian.net/browse/ATT-162)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5` | **Branch**: `feature/ATT-7668`  
**Requirement Mapping**: `REQ-SET-101` | **Test Spec ID**: `TST-SET-101`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-13  

---

## 1. Requirement Specification (`REQ-SET-101`)

### 1.1 Attributes & Problem Statement
- **Priority**: High | **Type**: Functional | **Verification Method**: Unit
- **Implementation**: `HealthConnectRecordMapper.kt`, `HealthConnectManager.kt`
- When synchronizing outdoor GPS workouts to Health Connect, duplicate 1-second sample timestamps in `WorkoutSamples.db` cause `ExerciseRoute` construction to crash with `IllegalArgumentException: Failed requirement.`. Furthermore, when a sample timestamp coincides with `endTime` (`startTime + totalTimeSec`), `ExerciseSessionRecord` crashes with `IllegalArgumentException: route can not be out of parent time range.`.

### 1.2 Functional & Architectural Requirements
1. Route sample points in `HealthConnectRecordMapper.extractRecords()` SHALL be strictly monotonically increasing in time (`sortedRoute[i].time < sortedRoute[i+1].time`). When multiple GPS samples share the same whole-second timestamp, duplicate timestamps SHALL be deduplicated, retaining only the first valid point per instant.
2. The `endTime` of `ExerciseSessionRecord` and associated series records SHALL be strictly after the latest route point timestamp (`maxRouteTime < sessionRecord.endTime`). When `maxRouteTime >= endTime`, `endTime` SHALL be extended to at least `maxRouteTime.plusSeconds(1)`.
3. Series telemetry samples (`HeartRateRecord`, `PowerRecord`, `SpeedRecord`, `CyclingPedalingCadenceRecord`, `StepsCadenceRecord`) SHALL likewise deduplicate or preserve monotonicity per timestamp to prevent redundant same-second records.

### 1.3 Acceptance Criteria (Given-When-Then)
- *Given* a workout in `WorkoutSamples.db` containing duplicate GPS samples with identical 1-second timestamps, *When* `extractRecords()` runs, *Then* route locations are deduplicated so that `ExerciseRoute` is created without throwing `IllegalArgumentException`.
- *Given* a workout where a GPS sample timestamp equals or exceeds `startTime + totalTimeSec`, *When* `extractRecords()` constructs `ExerciseSessionRecord`, *Then* `maxRouteTime.isBefore(endTime)` is satisfied and no `IllegalArgumentException` is thrown.
- *Given* an indoor workout with no GPS points, *When* `extractRecords()` runs, *Then* `exerciseRoute` remains null and `endTime` calculation remains unaffected.

### Requirement Archaeology & Chesterton's Fence Audit
- *Original Requirement ID & Target*: Amends `REQ-SET-101` (`REQ-SET-101.1`, `REQ-SET-101.2`), targeting `HealthConnectRecordMapper.kt`.
- *Historical Origin & Commit Trace*: Introduced in `ATT-3052` (commit `8a09a58f`), hardened in `ATT-6711` (commit `8abca7ef`).
- *Root Reason for Existing Formulation*: `ATT-6711` filtered sample timestamps with `sampleTime.isAfter(endTime)`, which permitted `sampleTime == endTime`. However, AndroidX Health Connect SDK enforces strict monotonicity on `ExerciseRoute` (`time < next.time`) and strict range bounds on `ExerciseSessionRecord` (`maxTime.isBefore(endTime)`).
- *Preservation of Core Invariants*: Retains full GPS coordinate fidelity, trackless indoor session parity (`REQ-SET-102`), series chunking at $\le 2000$ bound (`REQ-SET-101.3`), and numerical calorie extraction.

---

## 2. Test Specification (`TST-SET-101`)

### Test Cases
- `[TST-SET-101.6]` (Unit - `HealthConnectRecordMapperTest.kt`): Verify duplicate whole-second GPS sample timestamps are deduplicated and strictly ascending, ensuring `ExerciseRoute` constructs without `IllegalArgumentException`.
- `[TST-SET-101.7]` (Unit - `HealthConnectRecordMapperTest.kt`): Verify workouts with GPS points at or beyond `startTime + durationSec` extend `sessionRecord.endTime` to satisfy `maxRouteTime.isBefore(endTime)` without `IllegalArgumentException`.
- `[TST-SET-101.8]` (Unit - `HealthConnectRecordMapperTest.kt`): Verify trackless indoor workouts retain null route and standard duration envelope without regressions (`REQ-SET-102`).

---

## 3. Traceability Matrix

| Test Case | Scope | Target Component & Method | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-SET-101.6]` | Unit | `HealthConnectRecordMapper.extractRecords` | `REQ-SET-101.2` | Specified |
| `[TST-SET-101.7]` | Unit | `HealthConnectRecordMapper.extractRecords` / `buildExerciseSessionRecord` | `REQ-SET-101.1`, `REQ-SET-101.2` | Specified |
| `[TST-SET-101.8]` | Unit | `HealthConnectRecordMapper.extractRecords` | `REQ-SET-102.1` | Specified |
