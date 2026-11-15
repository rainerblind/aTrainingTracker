# Stage 3: Implementation Plan - ATT-7876: Epoch-Bounded Barometric Calibration

**Ticket**: [ATT-7876](https://rainerblind.atlassian.net/browse/ATT-7876)  
**Sub-task**: Stage 3 (`[Impl-Plan]`)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.7`  
**Requirement Mapping**: `REQ-FIL-018`  
**Test Mapping**: `TST-FIL-018`  
**Branch**: `feature/ATT-7876`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-15

---

## 1. Problem Description & Background
Post-crash `RESUME_SERVICE_RECREATION` barometric calibration fires with `prevCalTimeMs == -1L`, causing unbounded Case A uniform shifts across pre-crash calibrated rows/streams, shifting non-barometric (`DEM`, `GPS`, `Google Fused`, `ATT Fused`) columns and DEM canonical rows, and dividing Case B `AVG ALTITUDE` by ~20x due to summing 20s `sampledAltitudes` against 1-Hz `altStats.count`.

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-FIL-018` (`REQ-FIL-018.1`..`REQ-FIL-018.3`)
* **Test Mapping**: `TST-FIL-018` (`TST-FIL-018.1`..`TST-FIL-018.5`)

## 3. System Invariants & Preserved Behavior
1. **Zero Unintended Regressions**: $|\Delta h| \le 500\text{ m}$ guard (`REQ-CON-017`), Case B piecewise-linear interpolation (`REQ-TRK-047.2`), and existing tests continue passing cleanly.
2. **Thread Safety & Dispatcher Affinity**: Database operations stay on `mDbExecutor` single thread inside atomic SQLite transactions; `onOpen()` remains strictly passive.
3. **Subtask Self-Sufficiency & Human Gate**: Subtasks close to `Erledigt` via `freigabe`; parent stops at `Final Review (Human)`.
4. **Concurrent Lane Hygiene**: Minimal surgical changes to `TrackerService.java`; preserve `ANT-Android-SDKs` gitlink `160000 0e8cea714a`.

## 4. Proposed Architectural Changes
* **`WorkoutSamplesDatabaseManager`**: Resolve barometric column names via `DevicesDatabaseManager` (`ALTITUDE_FROM_PRESSURE`) + current/9-locale/legacy pressure names; reject DEM/GPS/Google Fused/ATT Fused. Update canonical `ALTITUDE` first (`WHERE baroCol IS NOT NULL AND ABS(ALTITUDE - baroCol) < 0.01`), then shift `baroColumns`; bound Case A by `TIME >= floor(sessionEpochStartTimeMs/1000)` and `TIME <= t1Ms/1000.0`.
* **`LiveWorkoutSession`**: Pad `0L` timestamps for rehydrated `sampledAltitudes`, track `sessionEpochStartTimeMs` and `epochStartAltCount` to protect pre-crash Case A state, and update Case B `altStats.sum += meanCorrection * altStats.count` while leaving 1-Hz `altStats.count` intact.
* **`TrackerService`, `WorkoutRepository`, `WorkoutSummariesDatabaseManager`**: Track `mSessionEpochStartTimeMs` on start/resume/recreation, pass to `WorkoutRepository.applyAltitudeDriftCorrection` & `LiveWorkoutSession`, guard `LiveWorkoutSession`/`WorkoutSummaries` shifts with barometric canonical source check, and keep stream/extrema consistent on post-crash Case A.
* **UI Consistency (Rule 23)**: No UI changes.

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Verify Gate 3 Lock**: `python3 tools/jira_util.py check-gate <IMPL_PLAN_KEY>`.
2. **`WorkoutSamplesDatabaseManager.java`**: Implement `resolveBarometricAltitudeColumns`, row-level canonical source match guard (before barometric column update), and `[sessionEpochStartTimeMs, t1Ms]` Case A SQL bounds (5-arg overload + 4-arg delegator).
3. **`LiveWorkoutSession.java`**: Add `sessionEpochStartTimeMs` + `epochStartAltCount` tracking, `0L` timestamp padding on `rehydrateStreams`, Case A epoch/upper-bound guards, and Case B `sum += meanCorrection * count` fix.
4. **`WorkoutSummariesDatabaseManager.java`, `WorkoutRepository.kt`, `TrackerService.java`**: Propagate `sessionEpochStartTimeMs`, guard non-barometric canonical updates in `mAltitudeCorrectionReceiver`, and preserve pre-crash stream/extrema points on Case A resume calibration.
5. **Tests & Living Docs**: Add tests in `WorkoutSamplesDatabaseManagerTest.kt` and `LiveWorkoutSessionAltitudeDriftTest.kt`; update `REQ-FIL-018`/`TST-FIL-018` to `[Implemented]` and run `verify_requirement_governance.py --sync-indexes`.
6. **Targeted Tier-1 Verification**: Execute targeted unit/contract tests (`WorkoutSamplesDatabaseManager*`, `LiveWorkoutSession*`, `AltitudeFromPressure*`, `TrackerServiceCrashRecoveryCharacterisationTest`, `SourceTextReadingAuditContractTest`, `AspiceSweAuditContractTest`), `:app:lintDebug`, and scoped `:app:detekt`.

## 6. Verification & Rollback Plan
* **Verification**: Targeted Tier-1 unit tests + contract tests + `:app:lintDebug` + scoped `:app:detekt` in Stage 4; clean-room full regression deferred to Stage 5 per streamlined lock policy.
* **Rollback**: Branch `feature/ATT-7876` isolated from `sprint/2026-46.7`.
