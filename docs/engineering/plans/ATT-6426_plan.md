# Stage 3: Implementation Plan - ATT-6426: Repository-Only Engine & Exporter Database Layer (Phase B)

**Ticket**: [ATT-6426](https://rainerblind.atlassian.net/browse/ATT-6426) | **Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) | **Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.1` | **Branch**: `feature/ATT-6426` | **Req/Test**: `REQ-DAT-041` / `TST-DAT-037`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Description & Architecture Rationale
Core background processing engines, exporters, and migration helpers still call SQLite database managers directly via `*DatabaseManager.getInstance(...)`. We replace these calls with delegates on domain repositories (`WorkoutRepository`, `SportTypesRepository`, `EquipmentRepository`, `WorkoutClusterRepository`, `KnownLocationsRepository`, `RoutesRepository`, `SegmentsRepository`, `ExportStatusRepository`). `BackupManager.kt` already routes through `DatabaseConnectionManager` and remains clean.

---

## 2. System Invariants & Preserved Behavior
1. **Strict Scope Bounding**: Phase C (`ATT-6427`: Live Recording Service `TrackerService` & `BANALService`) strictly untouched.
2. **Database Schema & Helper Stability**: Zero SQLite schema alterations (`SQLiteOpenHelper` databases preserved without Room retrofitting).
3. **Test & Caller Compatibility**: Existing unit test constructors in Java file writers and deletion helper are preserved.
4. **Export & Cascade Parity**: FIT/TCX/GPX formats, Strava API payloads, cluster centroids, and deletion cascades remain 100% bit-exact.

---

## 3. UI Consistency (Design Guidelines)
* **Architectural Decoupling**: No UI changes; pure backend pipeline and exporter refactoring.

---

## 4. Step-by-Step Implementation Sequence

### Step 1: Repository Batch & Query Delegates (`REQ-DAT-041.6`)
* **Files**: `WorkoutRepository.kt`, `WorkoutClusterRepository.kt`, `ExportStatusRepository.kt`
* **Changes**: Expose delegates on `Dispatchers.IO` for extrema positions, deletion cascades, and export status mutations.

### Step 2: Refactor WorkoutClusterEngine (`REQ-DAT-041.1`)
* **File**: `database/WorkoutClusterEngine.kt`
* **Changes**: Route lookups to `WorkoutClusterRepository`, `SportTypesRepository`, `KnownLocationsRepository`, `WorkoutRepository`, and `RoutesRepository`.

### Step 3: Refactor LegacyImportEngine (`REQ-DAT-041.2`)
* **File**: `migration/LegacyImportEngine.kt`
* **Changes**: Route workout, lap, sample, cluster, and export status persistence to `WorkoutRepository`, `SportTypesRepository`, `WorkoutClusterRepository`, and `ExportStatusRepository`.

### Step 4: Refactor StravaUploader (`REQ-DAT-041.3`)
* **File**: `exporter/uploader/StravaUploader.kt`
* **Changes**: Route queries and status updates to `WorkoutRepository`, `SportTypesRepository`, `EquipmentRepository`, `SegmentsRepository`, and `ExportStatusRepository`.

### Step 5: Refactor FitFileWriter & TCXFileWriter (`REQ-DAT-041.4`)
* **Files**: `exporter/writer/FitFileWriter.java`, `exporter/writer/TCXFileWriter.java`
* **Changes**: Retrieve lap, sample, and sport type telemetry via `WorkoutRepository` and `SportTypesRepository`. Retain constructor overloads for unit tests.

### Step 6: Refactor WorkoutDeletionHelper (`REQ-DAT-041.5`)
* **File**: `database/WorkoutDeletionHelper.java`
* **Changes**: Route default constructor instantiation to `WorkoutRepository` and `ExportStatusRepository`.

### Step 7: AST Cleanliness & Regression Test Suite (`TST-DAT-037.1` – `TST-DAT-037.4`)
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/RepositoryEngineCleanlinessContractTest.kt`
* **Changes**: Assert zero `*DatabaseManager.getInstance(...)` calls across all Phase B target files; run targeted test suite.

### Step 8: Living Documentation Verification (`REQ-DAT-041`, `TST-DAT-037`)
* **Files**: `docs/requirements/req_data_migration_export.md`, `docs/tests/tst_data_migration_export.md`
* **Changes**: Update status tags from `[Planned]` to `[Verified]`.
