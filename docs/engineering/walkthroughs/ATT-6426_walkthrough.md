# ATT-6426 Walkthrough: Access to Database only via Repository (Phase B: Engines, Exporters & Migration Layer)

**Ticket**: [ATT-6426](https://rainerblind.atlassian.net/browse/ATT-6426)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Sprint**: `2026-46.1` | **Target Release**: `V5.0.0` | **Status**: Verified  
**Requirement Mapping**: `REQ-DAT-041` | **Test Mapping**: `TST-DAT-037`  

---

## 1. Executive Summary

Historically, background engines, file writers, and migration components directly accessed SQLite database manager singletons (`WorkoutSummariesDatabaseManager`, `WorkoutSamplesDatabaseManager`, `LapsDatabaseManager`, `SportTypeDatabaseManager`, `EquipmentDbHelper`, `StravaUploadDbHelper`, `KnownLocationsDatabaseManager`, `RoutesDatabaseManager`). This bypassed repository abstractions and violated ASPICE SWE.2 / Clean Architecture boundaries.

Under `ATT-6426` (Phase B, governed by `REQ-DAT-041` / `TST-DAT-037`):
- **WorkoutClusterEngine Decoupling (`REQ-DAT-041.1`)**: Replaced direct manager calls with `WorkoutClusterRepository`, `WorkoutRepository`, `SportTypesRepository`, `KnownLocationsRepository`, and `RoutesRepository`. Refactored testing resets to cascade through repositories.
- **LegacyImportEngine Decoupling (`REQ-DAT-041.2`)**: Routed workout summaries, sample creation, lap saving, cluster attachment, and Strava opt-out checks through `WorkoutRepository`, `WorkoutClusterRepository`, and `ExportStatusRepository`.
- **StravaUploader Decoupling (`REQ-DAT-041.3`)**: Decoupled activity deduplication, gear sync, sport mapping, and segment PR ingestion to `ExportStatusRepository`, `EquipmentRepository`, `SportTypesRepository`, and `SegmentsRepository`.
- **Exporters Decoupling (`REQ-DAT-041.4`)**: Refactored `FitFileWriter.java`, `TCXFileWriter.java`, and `BaseFileWriter.java` to resolve summaries, samples, laps, and equipment exclusively through `WorkoutRepository` and `EquipmentRepository`.
- **WorkoutDeletionHelper Decoupling (`REQ-DAT-041.5`)**: Replaced direct manager deletions with `WorkoutRepository` and `WorkoutClusterRepository` operations.
- **Contract Test Cleanliness (`TST-DAT-037.1`)**: Authored `RepositoryEngineCleanlinessContractTest` verifying AST cleanliness (0 forbidden `*DatabaseManager.getInstance` / `*DbHelper.getInstance` calls across all Phase B target files).

---

## 2. Traceability Matrix

| Requirement | Test Specification | Production Component | Verification Scope | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-DAT-041.1` | `TST-DAT-037.1`, `TST-DAT-037.2` | `WorkoutClusterEngine.kt` | `WorkoutCluster*`, Cleanliness Contract | **Verified** |
| `REQ-DAT-041.2` | `TST-DAT-037.1`, `TST-DAT-037.3` | `LegacyImportEngine.kt` | `LegacyImportEngine*`, `TcxImport*` | **Verified** |
| `REQ-DAT-041.3` | `TST-DAT-037.1`, `TST-DAT-037.4` | `StravaUploader.kt` | `StravaUploaderNamingTest`, Cleanliness Contract | **Verified** |
| `REQ-DAT-041.4` | `TST-DAT-037.1`, `TST-DAT-037.5` | `FitFileWriter.java`, `TCXFileWriter.java` | `FitFileWriterTest`, `TCXFileWriterLapTest` | **Verified** |
| `REQ-DAT-041.5` | `TST-DAT-037.1`, `TST-DAT-037.6` | `WorkoutDeletionHelper.java` | `WorkoutDeletionHelperTest` | **Verified** |

---

## 3. Verification & Test Execution

- **Tier 1 Targeted Test Scope (Rule 60)**:
  - Command: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --max-workers=2 --tests "com.atrainingtracker.trainingtracker.ui.RepositoryEngineCleanlinessContractTest" --tests "com.atrainingtracker.trainingtracker.database.WorkoutCluster*" --tests "com.atrainingtracker.trainingtracker.database.WorkoutDeletionHelperTest" --tests "com.atrainingtracker.trainingtracker.exporter.*" --tests "com.atrainingtracker.trainingtracker.migration.*" --tests "com.atrainingtracker.trainingtracker.tcx.*"`
  - Scope: Architectural cleanliness contract test, cluster engine heuristics/location seeding, workout deletion cascading, Strava uploaders/parsers, FIT/TCX file writers, legacy/bulk import engines, and TCX round-trip fidelity.
  - Result: **100% PASS** (497 tests completed, 0 failures, 0 errors, 0 regressions).
- **Physical Device Execution**:
  - Backend/architecture decoupling ticket; zero UI or manifest alterations. Physical APK deployment omitted per Rule 60 / Rule 79 in favor of targeted JVM unit test and AST contract verification.
