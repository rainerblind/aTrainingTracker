# Stage 1 Analysis: ATT-6427 - Access to Database only via Repository (Phase C: Live Recording Service)

**Ticket**: [ATT-6427](https://rainerblind.atlassian.net/browse/ATT-6427)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Branch**: `feature/ATT-6427`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation
Following Phase A (UI & ViewModels under `ATT-432`, `ATT-6696`, `ATT-6697`) and Phase B (Engines & Exporters under `ATT-6426`, `ATT-6698`), the live recording foreground services (`TrackerService.java`, `BANALService.java`) and the hardware coordinator (`DeviceManager.java`) still directly instantiate SQLite database managers via `*DatabaseManager.getInstance(...)` and `*DbHelper.getInstance(...)`.
Furthermore, `TrackerService` and `DeviceManager` perform raw SQLite queries, manual transactions, and cursor extractions directly, violating SWE.2 architecture boundaries and coding standards (§1.12, §2.5, §5.9).

---

## 2. Root Cause Analysis & Independent Peer Review (4-Augen-Prinzip)
* **Root Cause**: Live tracking and hardware coordinator services predate repository abstractions and directly manage SQLite transactions, table creation, and raw queries.
* **Independent Peer Review & Verification**:
  - *Call-Site Audit Across 3 Target Files*:
    1. `TrackerService.java` (27 direct calls across 7 managers/helpers):
       - `WorkoutSummariesDatabaseManager`: resume state query, extrema updates, streams, finished flag, errors.
       - `WorkoutSamplesDatabaseManager`: table creation, 1-Hz sample writes, missing column handling, altitude drift correction.
       - `LapsDatabaseManager`: `saveLap(...)`.
       - `SportTypeDatabaseManager`: `getUIName(...)`, `getBSportType(...)`.
       - `ActiveDevicesDbHelper`: `addActiveDevice(...)`.
       - `WorkoutRrDatabaseManager`: `insertIntervals(...)`.
       - `KnownLocationsDatabaseManager`: `recordWorkoutStart(...)`, `getMyLocation(...)`.
    2. `BANALService.java` (7 direct calls to `SportTypeDatabaseManager`):
       - `getDefaultSportTypeId()`, `getSportTypeId(...)`, `getBSportType(...)`, `getSportTypesIdList(...)`.
    3. `DeviceManager.java` (18 direct calls to `DevicesDatabaseManager` + 1 raw cursor query on `DevicesDbHelper.DEVICES` in `createPairedRemoteDevices`):
       - ANT/BTLE device insertion, lookup, pairing checks, manufacturer/battery updates, and paired device querying.
  - *Repository Mapping*:
    - `TrackerService`: Routes through `WorkoutRepository`, `SportTypesRepository`, `DeviceDataRepository`, `KnownLocationsRepository`, and `WorkoutRrRepository`.
    - `BANALService`: Routes through `SportTypesRepository`.
    - `DeviceManager`: Routes through `DeviceDataRepository` (encapsulating paired remote device querying without raw Cursors).

---

## 3. Scope Grounding & Complexity Split (ATT-1250, REQ-PRO-029)
* **In-Scope Goals (Phase C — 3 SP)**:
  - Decouple `TrackerService.java`, `BANALService.java`, and `DeviceManager.java` from all direct `*DatabaseManager.getInstance()` and `*DbHelper.getInstance()` calls.
  - Expose required domain methods on `WorkoutRepository`, `DeviceDataRepository`, and `SportTypesRepository` with zero leaky SQLite/Cursor types.
  - Add `RepositoryServiceCleanlinessContractTest` verifying zero direct database manager instantiations across the service layer.
  - Preserve 100% backward compatibility for background threading (`mDbExecutor`), 1-Hz high-frequency writes, crash-resume, and foreground service lifecycle.
* **Out-of-Scope Non-Goals**:
  - Rewriting services from Java to Kotlin (maintains Android Service platform stability).
  - Schema modifications or Room migrations (preserves existing `SQLiteOpenHelper` stability).
  - Standalone virtual sensor device internal refactoring (`AltitudeFromPositionDevice` etc.).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Net-New Requirement**: Formulates `REQ-TRK-066` (*Repository-Only Live Recording Service Database Access Layer (Phase C)*) in `docs/requirements/req_tracking_cockpit.md` (tested by `TST-TRK-066` in `docs/tests/tst_tracking_cockpit.md`; resolving tentative ID `REQ-TRK-055` to avoid collision with `ATT-6257`).
* **Preservation of Core Invariants**:
  - `REQ-TRK-030.1`: Unconditional `FINISHED = 1` committed in dedicated minimal transaction before auxiliary metadata updates.
  - `REQ-TRK-026.4` / `REQ-TRK-051.1`: Mid-workout dynamic column addition / PRAGMA table info self-healing during sample writes.
  - `REQ-TRK-031.3` / `REQ-TRK-036.2`: Bit-exact crash-resume and live session stream rehydration.
  - 1-Hz telemetry cadence and zero dropped samples during background recording.

---

## 5. Architectural Strategy & High-Level Solution
* Inject / resolve domain repositories (`WorkoutRepository`, `SportTypesRepository`, `DeviceDataRepository`, `KnownLocationsRepository`, `WorkoutRrRepository`) via overloaded constructors or singleton accessors in `onCreate()`.
* Encapsulate live sample recording within `WorkoutRepository.recordLiveSample(...)` or transactional helper methods, preserving execution on `mDbExecutor`.
* Encapsulate paired remote device query in `DeviceDataRepository.getPairedRemoteDevices(protocol)` returning strongly-typed `PairedRemoteDeviceDto`s, eliminating raw `Cursor` and `DevicesDbHelper` usage in `DeviceManager`.
* Expose `getDefaultSportTypeId()` and `getSportTypesIdList()` on `SportTypesRepository`.

---

## 6. Side-Effect, High-Frequency I/O & Risk Assessment
* **Side-Effect & I/O Audit (Rule 68)**:
  - Live 1-Hz write loop runs on `mDbExecutor`; repository delegates must remain lightweight with zero unnecessary allocations or coroutine context-switch overhead.
  - Strict adherence to docs/coding_standards.md §2.5: Repositories must not expose backdoor getters or raw Cursors to service callers.
* **Risk Rating**: **MEDIUM** (Touches core live recording path `TrackerService` and device coordinator `DeviceManager`; mitigated by 100% test coverage, comprehensive existing unit tests, and dedicated contract tests).
