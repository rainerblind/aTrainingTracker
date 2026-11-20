# Stage 3: Implementation Plan - ATT-9159: Persist Actual UTC Lap Start Timestamp in `TrackerService.saveLap`

**Ticket**: `ATT-9159` (`1 SP`, `Bug`) | **Epic**: `ATT-826: Laps` | **Target Release**: `V5.0.0`  
**Sprint**: `465` (`2026-11-20_01_Früh`) | **Branch**: `feature/ATT-9159` | **Req/Test**: `REQ-TRK-090` / `TST-TRK-090`  
**Author**: AI Agent 1 (`agent1`) | **Date**: 2026-11-20

---

## 1. Architecture Rationale & Layering (SWE.2)
- `MultisportLapCoordinator` already tracks `currentLapStartAnchor.timestampMs` (`START_NORMAL`, `applyResumedState`, non-pause `LAP_SUMMARY`) and `formatUtcTimestamp(epochMs)` (`yyyy-MM-dd HH:mm:ss` UTC). Capturing the closing lap's start timestamp **pre-mutation** inside `onLapSummaryIntent()`, resolving `consumeLapStartTimeUtc(lapTimeS)` inside `TrackerService.saveLap()`, and adding a UTC `now - lapTime * 1000L` fallback in `LapsDatabaseManager.saveLap()` preserves 4-/5-arg APIs, avoids `database -> tracker` dependencies, and keeps `TrackerService.java` at `3,539 / 3,559` LoC (`+1` LoC).

## 2. System Invariants & LoC Budgets
1. **UTC Timestamp Contract**: All persisted `Laps.TIME_START` values use canonical UTC `yyyy-MM-dd HH:mm:ss` matching SQLite `CURRENT_TIMESTAMP` and `FitFileWriter.parseUtcTimestamp()` (never local TZ).
2. **API & Test-Subclass Preservation**: 4-/5-arg `TrackerService.saveLap`, 5-/6-/8-arg `LapsDatabaseManager.saveLap`, and `saveUnnamedLap` dispatch in `MultisportLapCoordinator` remain intact (`MultisportLapCoordinatorTest`, `TrackerServiceLapTest`, `TrackerServiceFinalizationTest`, `PolarVeritySenseOfflineControllerTest`).
3. **LoC & Schema Guardrails**: `TrackerService.java <= 3,559` (`3,538 -> 3,539`), `MultisportLapCoordinator.kt` (`526 -> ~546`), `LapsDatabaseManager.java` (`612 -> ~626`), `WorkoutRepository.kt` (`4,931 / 5,117`, `0` edits); `Laps.db` `DB_VERSION = 3` unchanged; `Localization Parity: N/A - no UI strings`.
4. **UI Consistency**: `N/A` (zero UI files or strings modified).

## 3. Atomic Implementation Steps

### Step 1: Pre-Mutation Capture & `consumeLapStartTimeUtc` in `MultisportLapCoordinator.kt` (`REQ-TRK-090.1`)
- **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/tracker/MultisportLapCoordinator.kt` (`526 -> ~546` LoC)
- **Changes**: Add `@GuardedBy`/synchronized `pendingUnnamedLapStartUtc: String? = null` (cleared in `resetForSession`). In `onLapSummaryIntent()`, inside `if (!isPause) { synchronized(this) { ... } }`, capture `val previousLapStartMs = currentLapStartAnchor.timestampMs` **before** updating `currentLapStartAnchor`; if `closingStartUtc == null && closingName == null && previousLapStartMs > 0L`, store `pendingUnnamedLapStartUtc = formatUtcTimestamp(previousLapStartMs)` while still invoking `saveUnnamedLap(...)` so test overrides (`MultisportLapCoordinatorTest:293`) stay green. Add `@Synchronized fun consumeLapStartTimeUtc(lapTimeS: Int): String?` returning `pendingUnnamedLapStartUtc` (consumed & cleared), else `formatUtcTimestamp(currentLapStartAnchor.timestampMs)` when `> 0L`, else `formatUtcTimestamp(System.currentTimeMillis() - lapTimeS * 1000L)` when `lapTimeS > 0`, else `null` (`// REQ-TRK-090`).

### Step 2: UTC Start Resolution in `TrackerService.saveLap` (`REQ-TRK-090.2`)
- **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java` (`3,538 -> 3,539 / 3,559` LoC, `+1` LoC)
- **Changes**: In 5-arg `saveLap(long workoutId, long lapNr, int lapTime, double lapDistance, double averageSpeed)` (`:2226-2231`), resolve `String timeStart = mMultisportLapCoordinator.consumeLapStartTimeUtc(lapTime);` and call `getWorkoutRepository().saveLap(workoutId, lapNr, timeStart, lapTime, lapDistance, averageSpeed, null, null);`. Preserves 4-/5-arg signatures, `mBroadcastTracker.onDiscreteEdge`, in-process resume anchor preservation, and post-process-death `applyResumedState` (`:1906`) anchor.

### Step 3: UTC `now - lapTime` Fallback in `LapsDatabaseManager.saveLap` (`REQ-TRK-090.2`)
- **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/database/LapsDatabaseManager.java` (`612 -> ~626` LoC)
- **Changes**: In 8-arg `saveLap(...)` (`:135-171`), after the `ATT-896` phantom check (`lapTime <= 0 && lapDistance <= 0.0`), when `timeStart == null && lapTime > 0`, compute `Math.max(0L, System.currentTimeMillis() - lapTime * 1000L)` formatted strictly via UTC `DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)` + `ZoneOffset.UTC` (no reverse `database -> tracker` import). Preserves 5-/6-/8-arg signatures and `NaN`/infinite speed guards.

### Step 4: Unit & Integration Tests (`TST-TRK-090.1`–`.2`) + Living Docs
- **Files**: `MultisportLapCoordinatorTest.kt`, `LapsDatabaseManagerTest.kt`, `docs/architecture/detailed_design/tracker_service.md`, `docs/requirements/req_tracking_cockpit_part2.md`, `docs/tests/tst_tracking_cockpit.md`, `docs/requirements.md`, `docs/tests.md`
- **Changes**:
  - Add `TST-TRK-090.1` tests in `MultisportLapCoordinatorTest.kt`: single-sport `resetForSession(T0)` -> `IS_PAUSE=true` (no mutation) -> `LAP_SUMMARY` (`isPause=false`) at `T1` -> `saveFinalLap()` at `T2` verifying Lap 1 `timeStart == formatUtcTimestamp(T0)` and Lap 2 `timeStart == formatUtcTimestamp(T1)`, plus `Europe/Berlin` TZ test restoring `TimeZone.setDefault(prevTz)` in `try/finally`.
  - Add `TST-TRK-090.2` tests in `LapsDatabaseManagerTest.kt`: `saveLap(..., null, 300, ...)` under `Europe/Berlin` (restoring `TimeZone.setDefault` in `try/finally`) asserting UTC `now - 300_000 ms` in `TIME_START`, plus explicit `timeStart` preservation.
  - Tag tests with hyphenated `// REQ-TRK-090` / `// TST-TRK-090`, `0` `src/main` file reads (`REQ-PRO-049.1`), `0` compiler warnings; sync `tracker_service.md` (`3,539 LOC`) and promote `REQ-TRK-090` / `TST-TRK-090` to `[Implemented]` (Stage 4) and `[Verified]` (Stage 5).

### Step 5: Locked Incremental Verification & Governance (`Rule 7.5`)
- **Commands**:
  - `python3 tools/parallel_sprint_lock.py --timeout 1800 ./gradlew :app:testDebugUnitTest --rerun --tests "com.atrainingtracker.trainingtracker.tracker.MultisportLapCoordinatorTest" --tests "com.atrainingtracker.trainingtracker.database.LapsDatabaseManagerTest" --tests "com.atrainingtracker.trainingtracker.tracker.TrackerServiceLapTest" --tests "com.atrainingtracker.trainingtracker.tracker.TrackerServiceFinalizationTest" --tests "com.atrainingtracker.trainingtracker.tracker.TrackerServiceStateMachineCharacterisationTest" --tests "com.atrainingtracker.trainingtracker.tracker.TrackerServiceResumeTest" --tests "com.atrainingtracker.trainingtracker.integration.FileExportersIntegrationTest" --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest" --tests "com.atrainingtracker.trainingtracker.build.DetailedDesignParityContractTest" --tests "com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest"`
  - `python3 tools/verify_requirement_governance.py --repo-root . --base-ref refs/heads/sprint/2026-11-20_01_Früh`
