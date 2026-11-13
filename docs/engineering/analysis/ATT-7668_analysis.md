# Stage 1 Analysis: ATT-7668 - Fix Health Connect ExerciseRoute Crash on Duplicate Timestamps & End-Time Boundary

**Ticket**: [ATT-7668](https://rainerblind.atlassian.net/browse/ATT-7668)  
**Parent Epic**: [ATT-162](https://rainerblind.atlassian.net/browse/ATT-162) (*Cloud Integration*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5`  
**Branch**: `feature/ATT-7668` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-13  

---

## 1. Problem Statement & Motivation
* Synchronizing completed outdoor GPS workouts to Health Connect (e.g. `Zum Bäcker #53`) fails silently with `IllegalArgumentException`, leaving sync incomplete and `healthConnectLastSync` unrecorded.
* Forensic analysis against AndroidX Health Connect Client 1.1.0 (`connect-client:1.1.0`) reveals two strict SDK invariant violations:
  1. **Duplicate 1-Second Sample Timestamps**: `WorkoutSamples.db` stores whole-second timestamps (`yyyy-MM-dd HH:mm:ss`). When multiple sample rows share a second, `HealthConnectRecordMapper.extractRecords()` appends duplicate `ExerciseRoute.Location` points with the same `Instant`. `ExerciseRoute.kt` constructor strictly enforces `require(sortedRoute[i].time.isBefore(sortedRoute[i + 1].time))`, throwing `IllegalArgumentException: Failed requirement.`.
  2. **End-Time Boundary Violation (`maxTime < endTime`)**: `extractRecords()` line 142 filters samples via `if (sampleTime.isBefore(startTime) || sampleTime.isAfter(endTime)) continue`, accepting `sampleTime == endTime`. However, `ExerciseSessionRecord.kt` line 151 enforces `require(!minTime.isBefore(startTime) && maxTime.isBefore(endTime)) { "route can not be out of parent time range." }`. When `maxTime == endTime`, it throws `IllegalArgumentException: route can not be out of parent time range.`.

---

## 2. Root Cause Analysis & Independent Peer Review (4-Augen-Prinzip)
* **Root Cause Analysis (RCA)**:
  * `HealthConnectRecordMapper.kt:155-163`: `routeLocations` collects GPS coordinates without timestamp deduplication, allowing identical `Instant` values.
  * `HealthConnectRecordMapper.kt:83-85, 124-126`: `endTime = startTime.plusSeconds(durationSec)` does not guarantee `maxTime.isBefore(endTime)`. If the final sample occurs at `endTime`, `ExerciseSessionRecord` crashes.
* **Independent Peer Review & Verification**:
  * Audited `ExerciseRoute.kt` in `connect-client-1.1.0`: confirmed pairwise `time.isBefore(next.time)` invariant. Location points must be deduplicated by `time` and sorted ascending.
  * Audited `ExerciseSessionRecord.kt` in `connect-client-1.1.0`: confirmed `!minTime.isBefore(startTime) && maxTime.isBefore(endTime)`. Extending `endTime` to `maxOf(endTime, maxRouteTime.plusSeconds(1))` preserves all recorded track points (including the athlete's finish line) while strictly satisfying `< endTime`.
  * Audited series records: Deduplicating series samples by `time` prevents redundant same-second sensor duplicates.

---

## 3. User Scope Grounding & Complexity Split Guardrail (ATT-1250 / REQ-PRO-029)
* **In-Scope Goals**:
  1. Deduplicate `routeLocations` (and series samples) by `Instant` (`time`) and sort ascending in `HealthConnectRecordMapper.extractRecords()`.
  2. Guarantee `endTime > maxRouteTime` in `buildExerciseSessionRecord()` and `extractRecords()` by setting `endTime = maxOf(endTime, maxRouteTime.plusSeconds(1))` when route points exist.
  3. Author targeted unit tests in `HealthConnectRecordMapperTest.kt` verifying duplicate timestamps and route points at/past `startTime + totalTimeSec` construct valid records without throwing `IllegalArgumentException`.
* **Out-of-Scope Non-Goals**: Database schema alterations, WorkManager retry logic changes, or other export formats.
* **Complexity Rating**: 1 SP (Trivial / localized to mapper and unit test suite). Splitting not required.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: Amends `REQ-SET-101` (*Exercise Session & Telemetry Series Record Mapping*), targeting `HealthConnectRecordMapper.kt`.
* **Historical Origin & Commit Trace**: Introduced in `ATT-3052` (commit `8a09a58f`), hardened in `ATT-6711` (commit `8abca7ef`).
* **Root Reason for Existing Formulation**: `ATT-6711` added boundary filtering `sampleTime.isAfter(endTime)`, but missed that `ExerciseRoute` forbids duplicate timestamps and `ExerciseSessionRecord` requires strict `< endTime` rather than `<= endTime`.
* **Preservation of Core Invariants**: Retains full GPS coordinate fidelity, trackless indoor session parity (`REQ-SET-102`), series chunking at $\le 2000$ bound (`REQ-SET-101.3`), and numerical calorie extraction.

---

## 5. Side-Effect & Cross-Caller Impact Analysis (Rule 68)
* `HealthConnectManager.kt`: Directly benefits from exception elimination in `extractRecords()`.
* `DistanceRecord`, `ElevationGainedRecord`, `TotalCaloriesBurnedRecord`: Use aligned `endTime`, remaining strictly consistent with `ExerciseSessionRecord.endTime`.
* Zero side effects on Strava, TCX/FIT export pipelines, or SQLite databases.

---

## 6. Change Impact Analysis (SUP.10)
* **Affected Requirements**: Amends `REQ-SET-101` (`REQ-SET-101.2`, `REQ-SET-101.3`) in `docs/requirements/req_settings_profiles.md`, verified by `TST-SET-101` in `docs/tests/tst_settings_profiles.md`.
* **Affected Architecture Elements**: `HealthConnectRecordMapper.kt`, `HealthConnectRecordMapperTest.kt`.
* **Verification Impact & Test Surface**: `HealthConnectRecordMapperTest` (new tests: duplicate timestamps, end-time boundary), `verify_requirement_governance.py`.
* **Risk Assessment & Invariants**: Low risk. Strictly guarantees timestamp monotonicity and boundary validity without altering coordinate values or sensor metrics.

---

## 7. Living Documentation Plan
* Update `REQ-SET-101` in `docs/requirements/req_settings_profiles.md` and `TST-SET-101` in `docs/tests/tst_settings_profiles.md` to document route timestamp uniqueness and `maxTime < endTime` session envelope.
