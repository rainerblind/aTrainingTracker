# Stage 2: Requirement & Test Specification - ATT-6698: True Repository Encapsulation & SQLite Decoupling (Phase B Hardening)

**Ticket**: [ATT-6698](https://rainerblind.atlassian.net/browse/ATT-6698) | **Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `sprint/2026-46.2` | **Branch**: `feature/ATT-6698`  
**Requirement Mapping**: `REQ-DAT-042` | **Test Spec ID**: `TST-DAT-038`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-11  

---

## 1. Requirement Specification (REQ-DAT-042)

### 1.1 Problem Statement & Rationale
`ATT-6426` decoupled Phase B background engines from direct `*DatabaseManager.getInstance()` calls, but repositories retained public and internal backdoor getters exposing raw database managers, `SQLiteDatabase` handles, and `Cursor` instances. Callers (`FitFileWriter`, `TCXFileWriter`, `BaseFileWriter`, `LegacyImportEngine`, `WorkoutDeletionHelper`, `StravaUploader`, `WorkoutClusterEngine`) continued executing low-level SQLite queries through these backdoors, violating `docs/coding_standards.md` §2.5. Low-level persistence, Cursor lifecycles, and transactions SHALL be strictly encapsulated inside domain repositories.

### 1.2 Functional & Architectural Requirements
* **REQ-DAT-042.1** (*Elimination of Repository Backdoor Getters*): `WorkoutRepository`, `WorkoutClusterRepository`, `ExportStatusRepository`, and `RoutesRepository` SHALL make all internal database managers, database open helpers, `SQLiteDatabase` handles, and raw `Cursor` getters `private`. Zero public or internal backdoor getters (`workoutSummariesDatabaseManager`, `workoutSamplesDatabaseManager`, `lapsDatabaseManagerInstance`, `getWorkoutCursor`, `getCursorForAllWorkouts`, `databaseManager`, `stravaHelper`, `getDatabase`) SHALL be exposed to engine, exporter, or UI consumers.
* **REQ-DAT-042.2** (*Encapsulated Sample Streaming API for Exporters*): `WorkoutRepository` SHALL provide a memory-bounded streaming API (e.g. `streamSamples(fileBaseName: String, consumer: SampleConsumer)`) allowing `FitFileWriter` and `TCXFileWriter` to iterate over high-frequency workout sample records without materializing full sample lists in JVM heap memory ($O(1)$ memory overhead).
* **REQ-DAT-042.3** (*Encapsulated Exporter & Uploader Domain Queries*): `WorkoutRepository` SHALL provide strongly-typed domain DTO getters for export header extraction (`BaseFileWriter`), Strava duplicate checking metadata (`StravaUploader`), and lap summaries (`FitFileWriter`, `TCXFileWriter`), eliminating raw `Cursor` projections and SQLite column lookups in exporter layers.
* **REQ-DAT-042.4** (*Transactional Batch Encapsulation for LegacyImportEngine*): `WorkoutRepository` and `ExportStatusRepository` SHALL encapsulate all multi-table insert and update operations required during GPX/TCX file imports (workout summaries, lap insertions, dynamic sample table creation and bulk insertion) within repository-managed transactional boundaries (`beginTransaction`/`endTransaction`) on `Dispatchers.IO`.
* **REQ-DAT-042.5** (*Encapsulated Cascade Deletion Orchestration*): Multi-table workout cascade deletion (samples table drop, laps purge, export status cleanup, climb links, summaries deletion) SHALL be fully encapsulated within `WorkoutRepository.deleteWorkoutCascade()` and `WorkoutRepository.deleteOldWorkoutsCascade()`, deprecating and removing external database manager injections in `WorkoutDeletionHelper`.
* **REQ-DAT-042.6** (*Architectural AST and Reflection Governance Contract*): `RepositoryEngineCleanlinessContractTest` SHALL enforce: (a) zero `android.database.Cursor`, `SQLiteDatabase`, or `*DatabaseManager` imports across Phase B target callers (`FitFileWriter`, `TCXFileWriter`, `BaseFileWriter`, `LegacyImportEngine`, `WorkoutDeletionHelper`, `StravaUploader`, `WorkoutClusterEngine`); and (b) reflection verification that zero public methods on domain repositories return `Cursor`, `SQLiteDatabase`, or `*DatabaseManager` instances.

### 1.3 Acceptance Criteria (Given-When-Then) & Invariants
* *Given* domain repositories `WorkoutRepository`, `WorkoutClusterRepository`, `ExportStatusRepository`, and `RoutesRepository`,
* *When* inspecting public and internal member declarations via reflection and AST,
* *Then* zero public getters or methods return `Cursor`, `SQLiteDatabase`, or `*DatabaseManager` types.
* *Given* high-frequency recorded workouts with up to 20,000+ sample points,
* *When* exported to FIT or TCX via `FitFileWriter` and `TCXFileWriter`,
* *Then* samples are streamed via encapsulated repository callbacks with $O(1)$ heap memory overhead and 100% bitwise export fidelity.
* *Given* a multi-sport TCX/GPX backup archive,
* *When* imported via `LegacyImportEngine`,
* *Then* multi-table sample and summary batch operations persist atomically via repository transaction methods without leaking `SQLiteDatabase` handles.
* *Given* single or batch workout deletion requests,
* *When* triggered through `WorkoutRepository` cascade methods,
* *Then* all associated sample tables, laps, climbs, and export statuses are cleaned up symmetrically in correct dependency order.
* *Invariants*: Phase C (`TrackerService`, `BANALService`) strictly untouched; zero SQLite schema alterations; 100% test pass rate.

---

## 2. Test Specification (TST-DAT-038)

### Test Case 1: Repository Reflection Encapsulation Test (`TST-DAT-038.1`)
* **Scope**: Reflection Contract Test (`RepositoryEngineCleanlinessContractTest.kt`)
* **Assertion**: Verify reflectively that `WorkoutRepository`, `WorkoutClusterRepository`, `ExportStatusRepository`, and `RoutesRepository` expose zero public methods returning `Cursor`, `SQLiteDatabase`, or `*DatabaseManager`.

### Test Case 2: Phase B AST Cleanliness & Cursor Import Prohibition Test (`TST-DAT-038.2`)
* **Scope**: AST Contract Test (`RepositoryEngineCleanlinessContractTest.kt`)
* **Assertion**: Verify zero imports of `android.database.Cursor`, `android.database.sqlite.SQLiteDatabase`, or `*DatabaseManager` across all Phase B caller source files.

### Test Case 3: Memory-Bounded Sample Streaming Exporter Test (`TST-DAT-038.3`)
* **Scope**: Exporter Unit Tests (`FitFileWriterTest.kt`, `TCXRoundTripTest.kt`)
* **Assertion**: Verify samples are streamed row-by-row via domain callbacks and output FIT and TCX files match expected binary/XML outputs bit-for-bit.

### Test Case 4: Transactional Legacy Import Atomicity Test (`TST-DAT-038.4`)
* **Scope**: Import Engine Unit Test (`LegacyImportEngineTest.kt`)
* **Assertion**: Verify multi-table atomic persistence of summaries, samples, and laps via repository methods, and verify compensatory rollback on simulated I/O errors.

### Test Case 5: Encapsulated Cascade Deletion Parity Test (`TST-DAT-038.5`)
* **Scope**: Deletion Unit Tests (`WorkoutDeletionHelperTest.kt`, `WorkoutRepositoryTest.kt`)
* **Assertion**: Verify complete cascade deletion with identical order and WorkManager job cancellation.

### Test Case 6: Tier 1 Targeted Regression Suite (`TST-DAT-038.6`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.RepositoryEngineCleanlinessContractTest" --tests "com.atrainingtracker.trainingtracker.database.*" --tests "com.atrainingtracker.trainingtracker.exporter.*" --tests "com.atrainingtracker.trainingtracker.migration.*" --tests "com.atrainingtracker.trainingtracker.tcx.*"`
* **Assertion**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-DAT-038.1` | Contract (Reflection) | `RepositoryEngineCleanlinessContractTest.kt` | `REQ-DAT-042.1`, `REQ-DAT-042.6` | Specified |
| `TST-DAT-038.2` | Contract (AST) | `RepositoryEngineCleanlinessContractTest.kt` | `REQ-DAT-042.1`, `REQ-DAT-042.6` | Specified |
| `TST-DAT-038.3` | Unit (Exporter) | `FitFileWriterTest.kt`, `TCXRoundTripTest.kt` | `REQ-DAT-042.2`, `REQ-DAT-042.3` | Specified |
| `TST-DAT-038.4` | Unit (Import) | `LegacyImportEngineTest.kt` | `REQ-DAT-042.4` | Specified |
| `TST-DAT-038.5` | Unit (Deletion) | `WorkoutDeletionHelperTest.kt` | `REQ-DAT-042.5` | Specified |
| `TST-DAT-038.6` | Regression | `database.*`, `exporter.*`, `migration.*`, `tcx.*` | `REQ-PRO-001` | Specified |
