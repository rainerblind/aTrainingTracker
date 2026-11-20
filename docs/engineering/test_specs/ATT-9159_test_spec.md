# Stage 2: Requirement & Test Specification - ATT-9159: Persist Actual UTC Lap Start Timestamp in `TrackerService.saveLap`

**Ticket**: `ATT-9159` (`1 SP`, `Bug`, `severity-minor`) | **Parent Epic**: `ATT-826: Laps` | **Target Release**: `V5.0.0`  
**Active Sprint**: `465` (`2026-11-20_01_Früh`) | **Branch**: `feature/ATT-9159` (base `d555c4f9`)  
**Requirement Mapping**: `REQ-TRK-090` (`REQ-TRK-090.1`–`.2`)  
**Test Spec ID**: `TST-TRK-090` (`TST-TRK-090.1`–`.2`) | **Author**: AI Agent 1 (`agent1`) | **Date**: 2026-11-20

---

## 1. Requirement Specification (`REQ-TRK-090`)

### 1.1 Attributes
- **Priority**: Medium | **Type**: Non-Functional | **Verification Method**: Unit
- **System Requirement(s)**: `SYSREQ-TRK-003` (Derived SW-only lap timestamp persistence within existing `SYSREQ-TRK-003` tracking loop; zero `SYS.2`/`SYS.3` changes)
- **Implementation**: `MultisportLapCoordinator.kt`, `TrackerService.java` (`<= 3,559` LoC, `3,538` baseline), `LapsDatabaseManager.java`, `WorkoutRepository.kt` (`<= 5,117` LoC)

### 1.2 Functional & Architectural Requirements
1. **Pause-Neutral Wall-Clock Start & Pre-Mutation Anchor Capture (`REQ-TRK-090.1`)**:
   - `MultisportLapCoordinator.onLapSummaryIntent()` **SHALL** capture `previousLapStartMs = currentLapStartAnchor.timestampMs` **before** `if (!isPause)` overwrites `currentLapStartAnchor`, and for non-pause laps where `closingStartUtc == null` and `previousLapStartMs > 0L`, **SHALL** format `previousLapStartMs` as canonical UTC `"yyyy-MM-dd HH:mm:ss"` via `formatUtcTimestamp()` and pass it explicitly to `WorkoutRepository.saveLap(...)`.
   - `MultisportLapCoordinator.saveFinalLap()` **SHALL** format `currentLapStartAnchor.timestampMs` via `formatUtcTimestamp()` whenever `activeLegStartTimeUtc == null` and `currentLapStartAnchor.timestampMs > 0L`.
   - Pause/resume transitions (`IS_PAUSE == true`, `pauseTracking()`, `resumeFromPaused()`) **MUST NOT** overwrite `currentLapStartAnchor` or close/create laps (`REQ-TRK-002` parity). In-process resume preserves the exact in-memory `currentLapStartAnchor`; post-process-death recovery (`TrackerService.applyResumedState`, `TrackerService.java:1906`) anchors `currentLapStartAnchor` at `Math.max(0L, System.currentTimeMillis() - state.timeLap_s * 1000L)`.
2. **`TrackerService.saveLap` & `LapsDatabaseManager.saveLap` UTC Fallback + API/LoC Compatibility (`REQ-TRK-090.2`)**:
   - `TrackerService.saveLap` **MUST** preserve its 4-arg and 5-arg signatures (for existing test subclasses/callers) and keep `TrackerService.java <= 3,559` LoC, resolving UTC `timeStart` via `MultisportLapCoordinator.formatUtcTimestamp(anchorMs)` when `anchorMs > 0L` (or `System.currentTimeMillis() - lapTime * 1000L` UTC fallback when `lapTime > 0`) and delegating to `WorkoutRepository.saveLap(workoutId, lapNr, timeStart, lapTime, lapDistance, averageSpeed)`.
   - `LapsDatabaseManager.saveLap` **MUST** preserve its 5-arg, 6-arg, and 8-arg signatures; when invoked with `timeStart == null` and `lapTime > 0` by any legacy caller, it **SHALL** compute `System.currentTimeMillis() - (lapTime * 1000L)` formatted strictly in **UTC** `"yyyy-MM-dd HH:mm:ss"` (never local timezone) so `Laps.TIME_START` never silently records SQLite `CURRENT_TIMESTAMP` end time, while preserving `REQ-TRK-002` phantom-lap rejection (`lapTime <= 0 && lapDistance <= 0.0`) and `NaN`/infinite speed sanitization.

## 2. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
- *Original Requirement ID & Target*: Net-new `REQ-TRK-090`, extending `REQ-TRK-002` (`docs/requirements/req_tracking_cockpit_part1.md:281-307`) and `REQ-FIL-031.3` (`MultisportLapCoordinator.kt`); targets `MultisportLapCoordinator.kt`, `TrackerService.java`, `LapsDatabaseManager.java`, `WorkoutRepository.kt`.
- *Historical Origin & Commit Trace*: `4fca7376` / `e7e02b0e` (`ATT-896`, `REQ-TRK-002` phantom-lap rejection & pause/resume neutrality); `5fb97345` (`ATT-8147`, `SYSREQ-TRK-003` linkage).
- *Root Reason for Existing Formulation*: `REQ-TRK-002` preserved the 5-arg `saveLap` call chain and relied on SQLite `DATETIME DEFAULT CURRENT_TIMESTAMP` (`LapsDatabaseManager.java:558`), which inserts UTC write time (lap end) instead of actual lap start time for single-sport and final laps.
- *Preservation of Core Invariants*: Preserves `REQ-TRK-002` phantom-lap rejection (`lapTime <= 0 && lapDistance <= 0.0`), pause/resume neutrality, 4-/5-arg `TrackerService.saveLap` and 5-arg `LapsDatabaseManager.saveLap` compatibility, `TrackerService.java <= 3,559` LoC, `WorkoutRepository.kt <= 5,117` LoC, and UTC `'yyyy-MM-dd HH:mm:ss'` matching `FitFileWriter.parseUtcTimestamp()`. `Localization Parity: N/A - no UI strings`.

## 3. Acceptance Criteria
- **AC-1 (`REQ-TRK-090.1` — Pre-Mutation Anchor Capture & Pause Neutrality)**:
  - *Given* a tracked workout starting at `T0`, pausing/resuming (`IS_PAUSE=true`), splitting Lap 1 at `T1` (`IS_PAUSE=false`), and ending Lap 2 at `T2`, *When* `MultisportLapCoordinator` / `TrackerService.saveLap()` persists each lap (under any device TZ, e.g. `Europe/Berlin`), *Then* `Laps.TIME_START` stores `T0` for Lap 1 and `T1` for Lap 2 in UTC `'yyyy-MM-dd HH:mm:ss'`, and `IS_PAUSE=true` leaves `currentLapStartAnchor` untouched.
- **AC-2 (`REQ-TRK-090.2` — UTC Fallback & Legacy Compatibility)**:
  - *Given* `LapsDatabaseManager.saveLap()` invoked with `timeStart == null` and `lapTime > 0` by any legacy caller, *When* persisted to `Laps.db`, *Then* `Laps.TIME_START` stores UTC `now - lapTime * 1000L` (`'yyyy-MM-dd HH:mm:ss'`), `lapTime <= 0 && lapDistance <= 0.0` still rejects phantom laps, and existing 4-/5-arg `TrackerService.saveLap` and 5-arg `LapsDatabaseManager.saveLap` tests/mocks remain unbroken.

## 4. Test Specification (`TST-TRK-090`)
- **`TST-TRK-090.1` (Pre-Mutation Anchor, Multi-Lap UTC `TIME_START` & Pause Neutrality — `MultisportLapCoordinatorTest.kt`)**:
  - Verify `resetForSession(T0)` -> `IS_PAUSE=true` (no write, anchor remains `T0`) -> `IS_PAUSE=false` at `T1` -> `saveFinalLap()` at `T2` under `Europe/Berlin` persists Lap 1 `timeStart == formatUtcTimestamp(T0)` and Lap 2 `timeStart == formatUtcTimestamp(T1)` matching `FitFileWriter.parseUtcTimestamp()`, and verify in-process vs post-process-death resume anchoring (`TrackerService.java:1906`).
- **`TST-TRK-090.2` (`LapsDatabaseManager` UTC `now - lapTime` Fallback, `TrackerService.saveLap` & Contracts — `LapsDatabaseManagerTest.kt`, `TrackerServiceLapTest.kt`, `TrackerServiceFinalizationTest.kt`, `TrackerServiceStateMachineCharacterisationTest.kt`, `FileExportersIntegrationTest.kt`, Contracts)**:
  - Verify `LapsDatabaseManager.saveLap(..., null, 300, ...)` under `Europe/Berlin` persists UTC `now - 300_000 ms`, preserves explicit `timeStart`, and rejects `lapTime <= 0 && lapDistance <= 0.0`.
  - Verify `TrackerServiceLapTest`, `TrackerServiceFinalizationTest`, `TrackerServiceStateMachineCharacterisationTest`, `FileExportersIntegrationTest` (FIT `LapMesg.startTime` UTC round-trip), `ArchitectureConformanceContractTest` (`TrackerService.java <= 3,559` LoC, `WorkoutRepository.kt <= 5,117` LoC), `SourceTextReadingAuditContractTest` (`0` new `src/main` reads), and `Localization Parity: N/A - no UI strings`.

## 5. Traceability Matrix

| Test Case | Scope | Test Suite(s) | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-090.1` | Pre-mutation anchor capture, multi-lap UTC `TIME_START`, pause neutrality, resume scoping | `MultisportLapCoordinatorTest` | `REQ-TRK-090.1` | Planned |
| `TST-TRK-090.2` | `LapsDatabaseManager` UTC `now - lapTime` fallback, `TrackerService.saveLap`, FIT export & LoC/source contracts | `LapsDatabaseManagerTest`, `TrackerServiceLapTest`, `TrackerServiceFinalizationTest`, `TrackerServiceStateMachineCharacterisationTest`, `FileExportersIntegrationTest`, `ArchitectureConformanceContractTest`, `SourceTextReadingAuditContractTest` | `REQ-TRK-090.2` | Planned |
