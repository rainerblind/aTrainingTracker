# Stage 3: Implementation Plan - ATT-3543: Lossless WorkoutRrIntervals, Centered Recompute & Garmin FIT HrvMesg

**Ticket**: [ATT-3543](https://rainerblind.atlassian.net/browse/ATT-3543)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-3543`  
**Requirement Mapping**: `REQ-PER-034` (`REQ-PER-034.1`..`REQ-PER-034.6`)  
**Test Mapping**: `TST-PER-042` (`TST-PER-042.1`..`TST-PER-042.6`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Description & Background
Live workout tracking calculates DFA $\alpha_1$ and $\text{rMSSD}$ via causal trailing windows (~60s delay) and discards raw beat-to-beat intervals upon session completion. The implementation provides: (1) dedicated SQLite persistence table `workout_rr_intervals` (`WorkoutRrDatabaseManager` / `WorkoutRrRepository`) storing raw intervals with monotonic UTC epoch timestamps, (2) `HrvPostWorkoutRecomputer` executing centered-window DFA $\alpha_1$ ($t \pm 60\text{s}$) and $\text{rMSSD}$ ($t \pm 30\text{s}$) recomputation via Lipponen & Tarvainen centered 91-beat filtering, and (3) native Garmin FIT `HrvMesg` bidirectional export (`FitFileWriter`) and import (`FitWorkoutParser`, `LegacyImportEngine`).

---

## 2. Traceability & Requirements Mapping
* **Requirements**: `REQ-PER-034.1` (Lossless persistence), `REQ-PER-034.2` (Monotonic timestamp reconstruction), `REQ-PER-034.3` (Centered-window recompute), `REQ-PER-034.4` (Garmin FIT export), `REQ-PER-034.5` (Garmin FIT import), `REQ-PER-034.6` (Non-HRV invariant & async execution).
* **Test Mapping**: `TST-PER-042.1` (Database CRUD & schema integrity), `TST-PER-042.2` (Timestamp reconstruction & drift resync), `TST-PER-042.3` (Centered-window recomputation), `TST-PER-042.4` (FIT export interleaving), `TST-PER-042.5` (FIT import & recompute trigger), `TST-PER-042.6` (Tier 1 targeted regression suite).

---

## 3. System Invariants & Preserved Behavior
1. **Timestamp Monotonicity**: `beat_timestamp_ms` strictly recorded in UTC epoch milliseconds aligned with `WorkoutSamples`.
2. **Schema & Migration Parity**: `WorkoutRrDbHelper` enforces `onCreate`/`onUpgrade` parity (`docs/coding_standards.md` §2.3) and `DatabaseSchemaRegistry` version constant parity (`Rule 62`).
3. **Architecture Conformance**: UI never directly references raw `WorkoutRrDatabaseManager` or `WorkoutRrDbHelper` (`ArchitectureConformanceContractTest.kt` from `ATT-6182`).
4. **Code Lock Until Gate 3**: No production source code in `app/src/main/` modified until Gate 3 is approved (`Rule 4`).

---

## 4. Proposed Architectural Changes
* **Database & Repository (`WorkoutRrDatabaseManager.kt`, `WorkoutRrRepository.kt`)**: Dedicated `WorkoutRr.db` with table `workout_rr_intervals` (`workout_id, beat_index, beat_timestamp_ms, rr_ms, artifact_flag, gap_beats`), indices, symmetric DTO cursor extraction (`Rule 20`), deletion cascade in `WorkoutDeletionHelper`, and `DatabaseSchemaRegistry` registration.
* **Live Ingestion (`TrackerService.java`)**: Consumes `RrBeatListener` stream, buffers beats in memory, and writes batched SQLite transaction at workout finalization.
* **Centered Recomputer (`HrvPostWorkoutRecomputer.kt`)**: Post-workout background evaluator (`Dispatchers.IO`) running `RrArtifactFilter.filterBidirectional`, populating lag-free centered DFA $\alpha_1$ ($t \pm 60\text{s}$) and $\text{rMSSD}$ ($t \pm 30\text{s}$) into `WorkoutSamples` and extrema into `WorkoutSummaries`.
* **Garmin FIT Export & Import (`FitFileWriter.java`, `FitWorkoutParser.kt`, `LegacyImportEngine.kt`)**: Interleaves `HrvMesg` (up to 5 intervals in seconds) during export; extracts `HrvMesg`, reconstructs timestamps, persists beats, and triggers recomputation during import.
* **SWE.2 Architecture Maintenance (`docs/architecture.md`)**: Allocate `WorkoutRrRepository`, `WorkoutRrDatabaseManager`, and `HrvPostWorkoutRecomputer` under Data / HRV subsystem.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Implement WorkoutRrDatabaseManager, DTO & Registry Registration
* Files: `WorkoutRrInterval.kt`, `WorkoutRrDbHelper.kt`, `WorkoutRrDatabaseManager.kt`, `WorkoutRrRepository.kt`, `DatabaseSchemaRegistry.kt`, `WorkoutDeletionHelper.java`
* Implement table schema, indices, batch operations, cascade deletion, and `DatabaseSchemaRegistry` constant binding.

### Step 2: Implement Live Ingestion in TrackerService
* Files: `TrackerService.java`
* Register `RrBeatListener`, buffer events, and persist batch to `WorkoutRrDatabaseManager` during finalization.

### Step 3: Implement HrvPostWorkoutRecomputer
* Files: `HrvPostWorkoutRecomputer.kt`
* Implement centered 120s/60s window filtering via `filterBidirectional`, updating `WorkoutSamples` and `WorkoutSummaries`.

### Step 4: Implement Garmin FIT HrvMesg Export and Import
* Files: `FitFileWriter.java`, `FitWorkoutParser.kt`, `ParsedWorkoutData.kt`, `LegacyImportEngine.kt`
* Export `HrvMesg` interleaved with `RecordMesg`; import `HrvMesg`, reconstruct timestamps, persist beats, and recompute.

### Step 5: Unit Tests, Architecture Conformance & Regression Execution
* Files: `WorkoutRrDatabaseManagerTest.kt`, `HrvPostWorkoutRecomputerTest.kt`, `FitFileWriterHrvTest.kt`, `LegacyImportEngineFitHrvTest.kt`, `docs/architecture.md`
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.hrv.*" --tests "com.atrainingtracker.trainingtracker.exporter.writer.*" --tests "com.atrainingtracker.trainingtracker.migration.*" --tests "com.atrainingtracker.trainingtracker.build.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Unit tests covering DB CRUD, timestamp reconstruction, centered recomputation, FIT export/import round-trip, and `ArchitectureConformanceContractTest`.
* **Rollback**: Branch isolation (`feature/ATT-3543`) allows full revert without impacting `sprint/2026-46.1`.
