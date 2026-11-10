# Stage 1 Analysis: ATT-6426 - Access to Database only via Repository (Phase B: Engines, Exporters & Migration Layer)

**Ticket**: [ATT-6426](https://rainerblind.atlassian.net/browse/ATT-6426)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.1`  
**Branch**: `feature/ATT-6426`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-10  

---

## 1. Problem Statement & Motivation
Following Phase A (UI & ViewModel decoupling under `ATT-432`), core processing engines, exporters, and migration helpers still bypass domain repositories and instantiate raw SQLite database helpers directly via `*DatabaseManager.getInstance(...)`.
This breaks architectural boundaries (SWE.2), complicates unit testing with real or mocked SQLite instances, and duplicates database query/mutation logic across exporters and engines.

---

## 2. Root Cause Analysis & Independent Peer Review (4-Augen-Prinzip)
* **Root Cause**: Engines and exporters were implemented as low-level background processors before the introduction of domain repositories, relying directly on database singletons.
* **Independent Peer Review & Verification**:
  - *Call-Site Audit Across 7 Target Files*:
    1. `WorkoutClusterEngine.kt`: Calls `WorkoutClusterDatabaseManager`, `SportTypeDatabaseManager`, `KnownLocationsDatabaseManager`, `WorkoutSummariesDatabaseManager`, `RoutesDatabaseManager`.
    2. `LegacyImportEngine.kt`: Calls `WorkoutSummariesDatabaseManager`, `WorkoutSamplesDatabaseManager`, `LapsDatabaseManager`, `SportTypeDatabaseManager`, `WorkoutClusterDatabaseManager`, `ExportStatusDatabaseManager`, `StravaUploadDbHelper`.
    3. `BackupManager.kt`: Verified that no `*DatabaseManager.getInstance()` calls exist (uses `DatabaseConnectionManager` for checkpoints/locks).
    4. `StravaUploader.kt`: Calls `StravaUploadDbHelper`, `WorkoutSummariesDatabaseManager`, `SportTypeDatabaseManager`, `EquipmentDbHelper`, `SegmentsDatabaseManager`.
    5. `FitFileWriter.java`: Calls `SportTypeDatabaseManager`, `WorkoutSamplesDatabaseManager`, `LapsDatabaseManager`, `WorkoutSummariesDatabaseManager`.
    6. `TCXFileWriter.java`: Recently refactored in `ATT-938` to DTO architecture; calls `LapsDatabaseManager`, `WorkoutSamplesDatabaseManager`, `SportTypeDatabaseManager`.
    7. `WorkoutDeletionHelper.java`: Default constructor calls `WorkoutSummariesDatabaseManager`, `LapsDatabaseManager`, `WorkoutSamplesDatabaseManager`, `ExportStatusDatabaseManager`, `StravaUploadDbHelper`.
  - *Repository Mapping*: Operations map cleanly to existing domain repositories: `WorkoutRepository`, `SportTypesRepository`, `EquipmentRepository`, `WorkoutClusterRepository`, `KnownLocationsRepository`, `RoutesRepository`, `SegmentsRepository`, and `ExportStatusRepository`.

---

## 3. Scope Grounding & Complexity Split (ATT-1250, REQ-PRO-029)
* **In-Scope Goals (Phase B — 3 SP)**:
  - Route all direct database manager calls in `WorkoutClusterEngine.kt`, `LegacyImportEngine.kt`, `StravaUploader.kt`, `FitFileWriter.java`, `TCXFileWriter.java`, and `WorkoutDeletionHelper.java` through domain repositories.
  - Expose any missing batch/query delegates on `WorkoutRepository` and companion repositories.
  - Preserve 100% backward compatibility for all external callers and unit tests.
* **Out-of-Scope Non-Goals**:
  - Phase C (`ATT-6427`: Live Recording Service `TrackerService` & `BANALService`).
  - Database schema alterations or Room retrofitting (preserves `SQLiteOpenHelper` stability).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Net-New Requirement**: Proposes `REQ-DAT-041` (*Repository-Only Engine & Exporter Database Access Layer*) in `docs/requirements/req_data_migration_export.md` (tested by `TST-DAT-037` in `docs/tests/tst_data_migration_export.md`). No existing requirement modified.
* **Preservation of Core Invariants**: File formats (FIT, TCX, GPX), Strava API payloads, cluster centroid heuristics, and deletion cascades remain 100% bit-exact.

---

## 5. Architectural Strategy & High-Level Solution
* Inject or resolve domain repositories in engines and writers using overloaded constructors or default parameter expressions.
* For Java classes (`FitFileWriter`, `TCXFileWriter`, `WorkoutDeletionHelper`), provide repository-backed delegation methods while preserving existing constructor overloads used by unit tests.
* Ensure database transactions and multi-threaded background dispatches execute on `Dispatchers.IO` / repository executors.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: 100% regression test pass rate across all engine and exporter tests; zero schema alterations; zero changes to export file formats or Strava upload behaviors.
* **Risk Rating**: **LOW** (Encapsulation of existing database calls behind domain repository delegates; zero schema or protocol changes).
