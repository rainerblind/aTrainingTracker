# Stage 3: Implementation Plan - ATT-6698: True Repository Encapsulation & SQLite Decoupling (Phase B Hardening)

**Ticket**: [ATT-6698](https://rainerblind.atlassian.net/browse/ATT-6698)  
**Sub-task**: [ATT-6754](https://rainerblind.atlassian.net/browse/ATT-6754) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `sprint/2026-46.2`  
**Requirement Mapping**: `REQ-DAT-042` (`REQ-DAT-042.1` – `REQ-DAT-042.6`)  
**Test Mapping**: `TST-DAT-038` (`TST-DAT-038.1` – `TST-DAT-038.6`)  
**Branch**: `feature/ATT-6698` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Problem Description & Background
`ATT-6426` removed direct `*DatabaseManager.getInstance()` calls from Phase B components, but exposed backdoor getters on repositories (`workoutSummariesDatabaseManager`, `workoutSamplesDatabaseManager`, `lapsDatabaseManagerInstance`, `getWorkoutCursor`, `databaseManager`, `stravaHelper`). Callers (`FitFileWriter`, `TCXFileWriter`, `BaseFileWriter`, `LegacyImportEngine`, `WorkoutDeletionHelper`, `StravaUploader`, `WorkoutClusterEngine`) continued executing raw SQLite queries and cursor loops through these getters. This plan encapsulates all low-level SQLite access, cursor lifecycles, streaming iteration, and transactions inside domain repositories (`docs/coding_standards.md` §2.5).

---

## 2. Traceability & Requirements Mapping
* **REQ-DAT-042.1 / TST-DAT-038.1**: Elimination of public `*DatabaseManager`, `SQLiteDatabase`, and `Cursor` getters from Repositories.
* **REQ-DAT-042.2 / TST-DAT-038.3**: Encapsulated sample streaming callback API for `FitFileWriter` & `TCXFileWriter` ($O(1)$ memory overhead).
* **REQ-DAT-042.3 / TST-DAT-038.3**: Domain DTO getters for export headers (`BaseFileWriter`) and duplicate checks (`StravaUploader`).
* **REQ-DAT-042.4 / TST-DAT-038.4**: Transactional batch import persistence methods in `WorkoutRepository` for `LegacyImportEngine`.
* **REQ-DAT-042.5 / TST-DAT-038.5**: Encapsulated cascade deletion in `WorkoutRepository` eliminating manager injections in `WorkoutDeletionHelper`.
* **REQ-DAT-042.6 / TST-DAT-038.2**: AST import prohibition and reflection cleanliness verification in `RepositoryEngineCleanlinessContractTest`.

---

## 3. System Invariants & Preserved Behavior
1. **$O(1)$ Streaming Memory Efficiency**: Exporter sample iteration streams rows directly via consumer callback without materializing full sample lists into heap memory.
2. **Transactional Integrity**: Multi-table sample batch insertions and compensatory cleanups in `LegacyImportEngine` remain strictly atomic.
3. **Cascade Deletion Sequencing**: Deletion order (`getBaseFileName` resolved before summary deletion, WorkManager cancellation, multi-table cascade) is strictly preserved.
4. **Contract Invariance**: Zero regression across existing test suites (`FitFileWriterTest`, `TCXRoundTripTest`, `LegacyImportEngineTest`, `WorkoutDeletionHelperTest`).
5. **UI Consistency**: No UI changes.

---

## 4. Proposed Architectural Changes (SWE.2)
* **`WorkoutRepository`**: Expose `streamSamples(fileBaseName, consumer)`, `getWorkoutExportHeader(fileBaseName)`, `getWorkoutDuplicateCheckMetadata(fileBaseName)`, `deleteWorkoutCascade(workoutId)`, `deleteOldWorkoutsCascade(...)`, and transactional import methods. Make underlying managers and cursors `private`.
* **`BaseFileWriter`, `FitFileWriter`, `TCXFileWriter`**: Remove all imports of `Cursor`, `SQLiteDatabase`, and `*DatabaseManager`. Consume `streamSamples` and domain DTOs.
* **`LegacyImportEngine`**: Route summary insertion, sample table batch writing, and stats persistence through `WorkoutRepository` and `ExportStatusRepository`.
* **`WorkoutClusterEngine` & `WorkoutDeletionHelper`**: Route cluster, route, and deletion operations through domain repository methods; make repository manager properties `private`.
* **`WorkoutClusterRepository`, `ExportStatusRepository`, `RoutesRepository`**: Make all `databaseManager`, `stravaHelper`, `getDatabase`, and `Cursor` getters `private`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
* **Step 1 (DTOs & Streaming API)**: Add `WorkoutSampleRecord`, `SampleConsumer`, `WorkoutExportHeader`, and `WorkoutDuplicateCheckMetadata` DTOs. Implement `streamSamples` and header/metadata query methods in `WorkoutRepository.kt`.
* **Step 2 (Exporters Refactoring)**: Refactor `BaseFileWriter.java`, `FitFileWriter.java`, and `TCXFileWriter.java` to use domain streaming and DTO methods; remove all Cursor/SQLite imports.
* **Step 3 (StravaUploader & WorkoutClusterEngine Refactoring)**: Refactor `StravaUploader.kt` and `WorkoutClusterEngine.kt` to use repository domain methods; remove backdoor getter usages.
* **Step 4 (Cascade Deletion Encapsulation)**: Move cascade deletion logic into `WorkoutRepository.kt` (`deleteWorkoutCascade`, `deleteOldWorkoutsCascade`); refactor `WorkoutDeletionHelper.java` to delegate to repository.
* **Step 5 (LegacyImportEngine Batch Persistence)**: Encapsulate transactional import and compensatory cleanup in `WorkoutRepository.kt`; refactor `LegacyImportEngine.kt` to use repository methods.
* **Step 6 (Repository Backdoor Elimination)**: Make all `*DatabaseManager`, `*DbHelper`, `SQLiteDatabase`, and `Cursor` getters private in `WorkoutRepository`, `WorkoutClusterRepository`, `ExportStatusRepository`, and `RoutesRepository`.
* **Step 7 (Contract Test Hardening & Targeted Tests)**: Harden `RepositoryEngineCleanlinessContractTest.kt` with AST and reflection checks (`TST-DAT-038.1`, `TST-DAT-038.2`). Run targeted suite:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.RepositoryEngineCleanlinessContractTest" --tests "com.atrainingtracker.trainingtracker.exporter.*" --tests "com.atrainingtracker.trainingtracker.database.*" --tests "com.atrainingtracker.trainingtracker.migration.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Execute `RepositoryEngineCleanlinessContractTest`, exporter tests, import tests, and deletion tests. In Stage 5, run full clean-room suite.
* **Rollback**: Atomic Git revert of `feature/ATT-6698` prior to squash-merge onto `sprint/2026-46.2`.
