# Stage 2: Requirement & Test Specification - ATT-2023: [Import/TCX] Workouts Imported Twice Due to Shallow Filename-Only Deduplication and Concurrent Worker Race Conditions

**Ticket**: [ATT-2023](https://rainerblind.atlassian.net/browse/ATT-2023)  
**Sub-task**: [ATT-2077](https://rainerblind.atlassian.net/browse/ATT-2077) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-529](https://rainerblind.atlassian.net/browse/ATT-529) (*Import TCX Files*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.12`  
**Requirement Mapping**: `REQ-MIG-031` (*Multi-Dimensional Workout Deduplication & Concurrent Worker Race Condition Guard in TCX/GPX Import*)  
**Test Spec ID**: `TST-MIG-028`  
**Branch**: `feature/ATT-2023`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Formal Requirement Specification

### REQ-MIG-031: Multi-Dimensional Workout Deduplication & Concurrent Worker Race Condition Guard in TCX/GPX Import

The system SHALL prevent duplicate imports of workouts across single-file and cloud bulk recovery flows through multi-dimensional deduplication, atomic synchronization, and pre-dispatch filtering (ATT-2023):

1. **Multi-Dimensional Deduplication (`isWorkoutExisting`)**:
   - In `LegacyImportEngine.kt`, the deduplication query SHALL evaluate both `WorkoutSummaries.FILE_BASE_NAME = ?` AND `WorkoutSummaries.TIME_START = ?`.
   - In addition to exact timestamp matching, the query SHALL evaluate a $\pm 30$-second epoch window (`ABS(strftime('%s', timeStart) - ?) <= 30`) to detect identical activities exported under arbitrary or altered filenames (e.g. `activity_12345.tcx`, `run (1).tcx`, or `legacy_import_*.tcx`).

2. **Atomic Synchronization & Mutex Guard**:
   - `LegacyImportEngine` SHALL enforce an atomic coroutine `Mutex` (`importMutex`) guarding the critical check-and-insert transaction in `importFromTcx()` and `importFromGpx()`.
   - Inside `importMutex.withLock`, once `firstTime` is resolved, the engine SHALL execute `isWorkoutExisting(summaryDb, baseFileName, firstTime)`. If an existing workout is found, the engine SHALL immediately skip table creation, sample insertion, and summary insertion, returning `false` without side effects.

3. **Pre-Dispatch Base Name Deduplication**:
   - In `bulkRecoverFromDropbox()`, scanned cloud metadata entries SHALL be deduplicated by normalized base filename (`it.name.substringBeforeLast(".").removeSuffix("-TMP").removeSuffix("~").lowercase()`) prior to queuing, eliminating duplicate processing of files discovered across multiple scanned directories (e.g. `/TCX` and `/apps/Workouts/TCX`).

4. **Accurate Reporting**:
   - Skipped duplicate files SHALL increment `skippedCount`, ensuring the final import summary dialog accurately reports all deduplicated sessions.

5. **Invariants**:
   - Parsing fidelity for lap structures, sensor extensions, route cluster heuristics, and optional Strava upload parameters MUST NOT be broken.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - Net-new requirement (`REQ-MIG-031`), expanding `REQ-MIG-005` (*Incremental Import*) and `REQ-MIG-016` (*Paginated & Recursive Cloud Recovery*), targeting `LegacyImportEngine.kt`.
   - Target Release: `V4.9.38`.
   - Parent Epic: `ATT-529` (*Import TCX Files*).

2. **Historical Origin & Commit Trace**:
   - Commit `917f6984` (`ATT-117`): Initial incremental import deduplication using `FILE_BASE_NAME`.
   - Commit `32585f52` (`ATT-560`): Introduced multi-folder scanning for `/TCX` and `/apps/Workouts/TCX` with `distinctBy { it.pathLower ?: it.name }`.
   - Commit `a6ebdc81` (`ATT-549`): Introduced 3 concurrent workers in `bulkRecoverFromDropbox` to unblock background importing.

3. **Root Reason for Existing Formulation**:
   - Earlier requirements assumed that all imported files strictly followed the in-app naming pattern `YYYY_MM_DD_HH_MM_SS.tcx`. Under that single-directory assumption, `FILE_BASE_NAME` was thought to uniquely identify workouts. However, real-world downloads from external platforms (e.g. `activity_123.tcx`), multi-folder cloud scans (`/TCX` and `/apps/Workouts/TCX`), and concurrent worker coroutines on `Dispatchers.IO` allowed duplicate records to bypass the filename check.

4. **Preservation of Core Invariants**:
   - Fast-path `FILE_BASE_NAME` checks remain intact. Adding timestamp evaluation and mutex guarding eliminates duplicate imports with mathematical certainty while strictly preserving all downstream parsing and analytical capabilities.

---

## 3. Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Timestamp Deduplication Across Different Filenames)**:
  - *Given* a workout already exists in `WorkoutSummaries` with start time $T_0$,
  - *When* importing a TCX or GPX file with an arbitrary filename (e.g. `activity_12345.tcx`) whose parsed start time is $T_0$,
  - *Then* `isWorkoutExisting` SHALL return `true`, skipping database insertion and sample table creation.

* **Criterion 2 (Timestamp Tolerance Deduplication Window)**:
  - *Given* an existing workout recorded at `2024-05-10 14:30:00`,
  - *When* importing a file whose start time is `2024-05-10 14:30:15` ($\Delta t \le 30\text{s}$),
  - *Then* the import SHALL detect the duplicate, skip insertion, and increment `skippedCount`.

* **Criterion 3 (Multi-Folder Cloud Scan Deduplication)**:
  - *Given* a Dropbox account containing `/TCX/session.tcx` and `/apps/Workouts/TCX/session.tcx`,
  - *When* `bulkRecoverFromDropbox` scans both paths,
  - *Then* `entries` SHALL be deduplicated by filename base before worker dispatch, queuing `session.tcx` exactly once.

* **Criterion 4 (Concurrent Worker Mutex Protection)**:
  - *Given* 3 concurrent coroutine workers processing candidate files on `Dispatchers.IO`,
  - *When* two workers process identical or duplicate activities concurrently,
  - *Then* `importMutex.withLock` SHALL synchronize the check-and-insert transaction, allowing the first worker to insert and causing the second worker to skip without creating duplicate tables or records.

* **Criterion 5 (Accurate Result Reporting)**:
  - *Given* an import session where duplicates are detected and skipped,
  - *When* the recovery finishes,
  - *Then* `RecoveryResult.skipped` SHALL accurately reflect all skipped duplicate workouts.

---

## 4. Test Case Specification

### TST-MIG-028.1: Multi-Dimensional Deduplication Unit Tests (`LegacyImportEngineDeduplicationTest.kt`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngineDeduplicationTest.kt`
* **Test Objectives**:
  - Exact `FILE_BASE_NAME` match returns `true`.
  - Dissimilar `FILE_BASE_NAME` but identical `TIME_START` returns `true`.
  - `TIME_START` within $\pm 30$ seconds returns `true`.
  - `TIME_START` beyond 30 seconds returns `false`.

### TST-MIG-028.2: Multi-Folder Cloud Scan Base Name Deduplication Tests
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngineDeduplicationTest.kt`
* **Test Objectives**:
  - Metadata entries from `/TCX/foo.tcx` and `/apps/Workouts/TCX/foo.tcx` are deduplicated to a single candidate.

### TST-MIG-028.3: Concurrent Worker Race Condition Simulation
* **Scope**: Unit Test / Coroutine Concurrency Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngineDeduplicationTest.kt`
* **Test Objectives**:
  - Simulate 3 concurrent workers attempting to import identical TCX streams under `importMutex` synchronization -> exactly 1 succeeds, 2 skip, zero duplicate database rows.

### TST-MIG-028.4: 9-Language Localization Audit
* **Scope**: Localization Parity Test
* **Target**: Verify import status and summary strings across all 9 supported locales.
* **Expected Result**: 100% parity, zero missing entries.

### TST-MIG-028.5: Clean-Room Full Suite Regression Execution
* **Command**: `./gradlew testDebugUnitTest`
* **Pass Criteria**: 100% pass rate across the full test suite with 0 failures and 0 regressions.

---

## 5. Traceability Matrix

| Test Case | Scope | Method Under Test / Target | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-MIG-028.1` | Unit | `isWorkoutExisting` multi-dimensional timestamp check | `REQ-MIG-031` | Specified |
| `TST-MIG-028.2` | Unit | Multi-folder scan base name deduplication | `REQ-MIG-031` | Specified |
| `TST-MIG-028.3` | Concurrency | Mutex-guarded concurrent import simulation | `REQ-MIG-031` | Specified |
| `TST-MIG-028.4` | Localization | Import summary strings across 9 locales | `REQ-MIG-031`, `REQ-UI-106` | Specified |
| `TST-MIG-028.5` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
