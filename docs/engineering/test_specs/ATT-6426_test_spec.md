# Stage 2: Requirement & Test Specification - ATT-6426: Repository-Only Engine & Exporter Database Layer (Phase B)

**Ticket**: [ATT-6426](https://rainerblind.atlassian.net/browse/ATT-6426) | **Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6426`  
**Requirement Mapping**: `REQ-DAT-041` | **Test Spec ID**: `TST-DAT-037`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Requirement Specification (REQ-DAT-041)

### 1.1 Problem Statement & Rationale
Following Phase A UI decoupling (`ATT-432`), background processing engines, exporters, and migration helpers still instantiate SQLite database managers directly via `*DatabaseManager.getInstance(...)`. This breaks SWE.2 architectural encapsulation and complicates unit testing. All database operations in Phase B components SHALL route through domain repositories.

### 1.2 Functional & Architectural Requirements
* **REQ-DAT-041.1** (*WorkoutClusterEngine Decoupling*): `WorkoutClusterEngine.kt` SHALL execute all cluster lookups, centroids, and naming queries through `WorkoutClusterRepository`, `SportTypesRepository`, `KnownLocationsRepository`, `WorkoutRepository`, and `RoutesRepository`.
* **REQ-DAT-041.2** (*LegacyImportEngine Decoupling*): `LegacyImportEngine.kt` SHALL execute all workout, lap, sample, cluster, and export status persistence via `WorkoutRepository`, `SportTypesRepository`, `WorkoutClusterRepository`, and `ExportStatusRepository`.
* **REQ-DAT-041.3** (*StravaUploader Decoupling*): `StravaUploader.kt` SHALL route workout metadata, segment matching, and upload state queries through `WorkoutRepository`, `SportTypesRepository`, `EquipmentRepository`, and `SegmentsRepository`.
* **REQ-DAT-041.4** (*FitFileWriter & TCXFileWriter Decoupling*): `FitFileWriter.java` and `TCXFileWriter.java` SHALL retrieve lap, sample, summary, and sport type telemetry via repository delegates while maintaining existing constructors for unit test backward compatibility.
* **REQ-DAT-041.5** (*WorkoutDeletionHelper Decoupling*): `WorkoutDeletionHelper.java` SHALL execute cascade deletions through repository delegates while preserving test compatibility.
* **REQ-DAT-041.6** (*Repository Batch Query Delegates*): Target domain repositories SHALL expose any required batch queries and mutations on `Dispatchers.IO`.

### 1.3 Acceptance Criteria (Given-When-Then) & Invariants
* *Given* `WorkoutClusterEngine`, `LegacyImportEngine`, `StravaUploader`, `FitFileWriter`, `TCXFileWriter`, and `WorkoutDeletionHelper`,
* *When* auditing source code and AST references,
* *Then* zero occurrences of `*DatabaseManager.getInstance(...)` exist across all Phase B target files.
* *Given* a workout session exported to FIT or TCX or uploaded to Strava,
* *When* processed via the refactored exporters and engines,
* *Then* file contents, Strava payloads, cluster centroids, and deletion cascades remain 100% bit-exact and functionally identical.
* *Invariants*: Phase C (`TrackerService`, `BANALService`) strictly untouched; zero schema modifications; 100% test pass rate.

---

## 2. Test Specification (TST-DAT-037)

### Test Case 1: AST DatabaseManager Prohibition Contract Test (`TST-DAT-037.1`)
* **Scope**: Automated Contract Test (`RepositoryEngineCleanlinessContractTest.kt`)
* **Assertion**: Verify zero `*DatabaseManager.getInstance()` references across all Phase B target files.

### Test Case 2: Cluster & Migration Engine Delegation Test (`TST-DAT-037.2`)
* **Scope**: Unit Tests (`WorkoutClusterEngineTest.kt`, `LegacyImportEngineTest.kt`)
* **Assertion**: Verify clustering, recurring route detection, and backup import operate identically via repositories.

### Test Case 3: Exporter & Deletion Cascade Parity Test (`TST-DAT-037.3`)
* **Scope**: Exporter Unit Tests (`FitFileWriterTest.kt`, `TCXRoundTripTest.kt`, `WorkoutDeletionHelperTest.kt`)
* **Assertion**: Verify FIT/TCX generation and workout deletion cascades execute correctly with 100% parity.

### Test Case 4: Tier 1 Targeted Regression Suite (`TST-DAT-037.4`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.*" --tests "com.atrainingtracker.trainingtracker.exporter.*" --tests "com.atrainingtracker.trainingtracker.migration.*" --tests "com.atrainingtracker.trainingtracker.tcx.*"`
* **Assertion**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-DAT-037.1` | Contract | `RepositoryEngineCleanlinessContractTest.kt` | `REQ-DAT-041.1` – `REQ-DAT-041.5` | Specified |
| `TST-DAT-037.2` | Unit | `WorkoutClusterEngineTest.kt`, `LegacyImportEngineTest.kt` | `REQ-DAT-041.1`, `REQ-DAT-041.2` | Specified |
| `TST-DAT-037.3` | Unit | `FitFileWriterTest.kt`, `TCXRoundTripTest.kt`, `WorkoutDeletionHelperTest.kt` | `REQ-DAT-041.3` – `REQ-DAT-041.5` | Specified |
| `TST-DAT-037.4` | Regression | `database.*`, `exporter.*`, `migration.*`, `tcx.*` | `REQ-PRO-001` | Specified |
