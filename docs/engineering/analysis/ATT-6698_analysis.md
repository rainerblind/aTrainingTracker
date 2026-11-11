# Stage 1 Analysis: ATT-6698 - [High] WorkoutRepository, Exporters & LegacyImportEngine: Eliminate public DatabaseManager/Cursor backdoor getters and encapsulate SQLite access

**Ticket**: [ATT-6698](https://rainerblind.atlassian.net/browse/ATT-6698)  
**Sub-task**: [ATT-6752](https://rainerblind.atlassian.net/browse/ATT-6752) (`[Analysis]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.2`  
**Branch**: `feature/ATT-6698`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation
During `ATT-6426` (Phase B), background engines and exporters were decoupled from direct `*DatabaseManager.getInstance()` calls. However, `WorkoutRepository`, `WorkoutClusterRepository`, `ExportStatusRepository`, and `RoutesRepository` exposed public `*DatabaseManager`, `SQLiteDatabase`, and raw `Cursor` backdoor getters (`workoutSummariesDatabaseManager`, `workoutSamplesDatabaseManager`, `lapsDatabaseManagerInstance`, `getWorkoutCursor`, `getCursorForAllWorkouts`, `databaseManager`, `stravaHelper`). Callers (`FitFileWriter`, `TCXFileWriter`, `BaseFileWriter`, `LegacyImportEngine`, `WorkoutDeletionHelper`, `StravaUploader`, `WorkoutClusterEngine`) continued executing raw SQLite queries and cursor loops through these getters, defeating true repository encapsulation (`docs/coding_standards.md` §2.5).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Root Cause**: `RepositoryEngineCleanlinessContractTest` only verified `\b\w*(DatabaseManager|DbHelper)\.getInstance\(`. Under sprint pressure, heavy cursor consumers were bridged by exposing underlying managers and cursors directly on Repositories, leaking SQLite schemas, projections, and cursors into engine/exporter layers.
* **Independent Peer Review & Verification (ATT-3534)**:
  * Audit of `WorkoutRepository.kt`: verified public getters `workoutSummariesDatabaseManager`, `workoutSamplesDatabaseManager`, `lapsDatabaseManagerInstance`, `getWorkoutCursor()`, and `getCursorForAllWorkouts()`.
  * Audit of callers: `FitFileWriter.java` (samples/summaries cursor loops), `TCXFileWriter.java` (samples/laps cursor queries), `BaseFileWriter.java` (header summary query), `LegacyImportEngine.kt` (summary/sample/lap batch insertions & queries), `WorkoutDeletionHelper.java` (direct manager constructor), `StravaUploader.kt` (metadata queries), and `WorkoutClusterEngine.kt` (cluster/route/summary manager queries).
  * Also identified `WorkoutClusterRepository.databaseManager`, `fromCursorBatchKeys(Cursor)`, `ExportStatusRepository.databaseManager` / `stravaHelper`, and `RoutesRepository.databaseManager` / `getDatabase()`.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Add domain streaming/batch methods on `WorkoutRepository` (sample streaming callback for `FitFileWriter`/`TCXFileWriter`, export header DTO for `BaseFileWriter`, duplicate check DTO for `StravaUploader`, atomic import persistence for `LegacyImportEngine`).
  2. Encapsulate cascade deletion inside `WorkoutRepository` (`deleteWorkoutCascade`, `deleteOldWorkoutsCascade`), removing external manager injections in `WorkoutDeletionHelper`.
  3. Make all `*DatabaseManager`, `*DbHelper`, `SQLiteDatabase`, and `Cursor` references `private` in `WorkoutRepository`, `WorkoutClusterRepository`, `ExportStatusRepository`, and `RoutesRepository`.
  4. Harden `RepositoryEngineCleanlinessContractTest` with AST checks (no DB manager/Cursor imports in Phase B callers) and reflection checks (no public manager/cursor getters on Repositories).
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No modifications to Phase C components (`TrackerService`, `BANALService`).
  * No SQLite database schema migrations or changes to table definitions.
  * No changes to UI/presentation layer screens or ViewModels.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: `REQ-DAT-041` (`REQ-DAT-041.1` – `REQ-DAT-041.6`, Phase B Repository Access).
* **Historical Origin & Commit Trace**: Introduced in `ATT-6426` (commit `6206c9a`) to decouple engines and exporters.
* **Root Reason for Existing Formulation**: Backdoors were introduced because streaming high-frequency samples (up to 20,000+ points) and executing transactional multi-table imports required low-level SQLite access that had not yet been designed as domain abstractions.
* **Preservation of Core Invariants**: Retain streaming callback execution to prevent large in-memory list allocations (OOM guard); preserve strict SQLite transaction demarcation (`beginTransaction`/`endTransaction`) in import/deletion; maintain deletion cascade ordering (`getBaseFileName` before summary deletion); ensure 100% bitwise export fidelity.

---

## 5. Architectural Strategy & High-Level Solution
1. **Sample Streaming**: Introduce `WorkoutRepository.streamSamples(fileBaseName: String, consumer: (SampleRecord) -> Unit)` (or Java-friendly `SampleConsumer` functional interface) to stream rows directly from the SQLite cursor to `FitFileWriter` and `TCXFileWriter` without holding entire workout point sets in RAM.
2. **Metadata & Header DTOs**: Create `WorkoutExportHeader` on `WorkoutRepository` replacing raw cursor queries in `BaseFileWriter`, and `WorkoutDuplicateCheckMetadata` replacing raw queries in `StravaUploader`.
3. **Encapsulated Import Persistence**: Expose domain methods on `WorkoutRepository` for summary insertion, sample table creation/batch insertion, and stats calculation, preserving mutex locking and transactional boundaries.
4. **Encapsulated Cascade Deletion**: Move deletion orchestration directly into `WorkoutRepository` (or package-private helper), deprecating/removing public manager constructors on `WorkoutDeletionHelper`.
5. **Contract Hardening**: Extend `RepositoryEngineCleanlinessContractTest` to check zero `android.database.Cursor`, `SQLiteDatabase`, or `*DatabaseManager` imports in Phase B engines/exporters, and reflectively inspect all Repositories for forbidden public return types.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Memory invariant: Exporter sample streaming must remain $O(1)$ memory overhead without materializing bulk point collections.
  2. Transactional integrity: `LegacyImportEngine` batch sample insertions and compensatory cleanups must remain strictly atomic.
  3. Deletion invariant: Deletion sequencing in `WorkoutDeletionHelper` (`getBaseFileName` pre-query, WorkManager job cancellation, multi-table cascade) must remain invariant.
  4. Contract invariant: 100% pass rate on all existing unit and contract tests.
* **Risk Rating**: **HIGH** (Modifies core persistence abstractions, bulk export pipelines, import transactions, and cascade deletion across multi-table SQLite databases).
* **Complexity Rating**: **3 SP** (Complex refactoring across multiple persistence and exporter boundaries, but strictly bounded to Phase B and Repositories with no UI or service coupling).
