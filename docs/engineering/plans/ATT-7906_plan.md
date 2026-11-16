# Stage 3: Implementation Plan - ATT-7906: Streaming File Writers Kotlin Migration

**Ticket**: [ATT-7906](https://rainerblind.atlassian.net/browse/ATT-7906)  
**Sub-task**: [ATT-8297](https://rainerblind.atlassian.net/browse/ATT-8297) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `Sprint 2026-46.8`  
**Requirement Mapping**: `REQ-ARC-010` (`REQ-ARC-010.1`..`REQ-ARC-010.5`)  
**Test Mapping**: `TST-ARC-010` (`TST-ARC-010.1`..`TST-ARC-010.5`)  
**Branch**: `feature/ATT-7906` (Lane 2)  
**Author**: Lane 2 Pro Agent (`agent1`)  
**Date**: 2026-11-16  

---

## 1. Problem Description & Background
In accordance with ADR-001 (Tier 2 File Exporters & Serializers, ATT-7566), workout file writers stream serialized sessions to disk. `BaseFileWriter.java` (148 LOC), `TCXFileWriter.java` (436 LOC), and `FitFileWriter.java` (1,052 LOC) suffer from platform type (`T!`) nullability hazards and lack Kotlin's structured resource management (`.use {}`). Migrating these core streaming writers to idiomatic Kotlin delivers compile-time null safety, modern streaming I/O, and zero resource leaks while preserving 100% byte-for-byte and XML schema parity.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-ARC-010` (*Streaming File Writers Kotlin Migration & Interoperability Parity*)
* **Test Mapping**: `TST-ARC-010` (*Streaming File Writers Kotlin Migration Verification Suite*)

---

## 3. System Invariants & Preserved Behavior
1. **Garmin FIT SDK Protocol V2.0 & TCX Schema Parity**: 100% byte-level and XML schema compliance.
2. **Zero-SQLite Streaming Invariant (`REQ-DAT-042.5`)**: $O(1)$ memory streaming via `WorkoutRepository.streamSamples`; zero SQLite database imports in `FitFileWriter` or `TCXFileWriter`.
3. **Feature Preservation**: Bounded online `PedalDynamicsAccumulator` (`ATT-8196`), swim pool length (`ATT-7999`), developer data IDs & lossless round-trip (`ATT-7747`), radar threat alerts (`ATT-5096`), and RPE/Feel (`ATT-6879`).
4. **Java Subclass Interop**: `open class BaseFileWriter` preserves full inheritance for `GPXFileWriter.java`, `GCFileWriter.java`, `CSVFileWriter.java`, and `ExportManager.java`.

---

## 4. Proposed Architectural Changes
* **Component 1 (`BaseFileWriter.kt`)**: Convert `BaseFileWriter.java` to `open class BaseFileWriter` in Kotlin with `@JvmField` / open protected fields (`startTime`, `totalTime`, `data`, `goal`, etc.) and downloads copy hooks. Remove `BaseFileWriter.java`.
* **Component 2 (`TCXFileWriter.kt`)**: Convert `TCXFileWriter.java` to idiomatic Kotlin using `BufferedWriter.use {}`, `TcxSerializer`, and `WorkoutRepository.streamSamples`. Remove `TCXFileWriter.java`.
* **Component 3 (`FitFileWriter.kt`)**: Convert `FitFileWriter.java` to idiomatic Kotlin using `FileEncoder.use {}`, online `PedalDynamicsAccumulator`, radar developer fields, swim pool length, and metadata IDs. Remove `FitFileWriter.java`.
* **UI Consistency (Rule 23)**: No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
### Step 1: Migrate BaseFileWriter to Kotlin (`REQ-ARC-010.1`, `REQ-ARC-010.2`)
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/exporter/writer/BaseFileWriter.kt`
* Action: Author `BaseFileWriter.kt` as `open class` with `@JvmField` on protected properties for Java subclasses (`GPXFileWriter.java`, `GCFileWriter.java`, `CSVFileWriter.java`). Delete `BaseFileWriter.java`. Verify compilation.

### Step 2: Migrate TCXFileWriter to Kotlin (`REQ-ARC-010.1`, `REQ-ARC-010.3`)
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/exporter/writer/TCXFileWriter.kt`
* Action: Author `TCXFileWriter.kt` with structured `BufferedWriter.use {}` and `TcxSerializer` DTO streaming via `WorkoutRepository.streamSamples`. Delete `TCXFileWriter.java`.

### Step 3: Migrate FitFileWriter to Kotlin (`REQ-ARC-010.1`, `REQ-ARC-010.3`)
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/exporter/writer/FitFileWriter.kt`
* Action: Author `FitFileWriter.kt` with `FileEncoder.use {}`. Preserve all feature logic: `ATT-8196` (`PedalDynamicsAccumulator` online aggregation & `LeftRightBalance100`), `ATT-7999` (`setPoolLength`), `ATT-7747` (`APP_METADATA_DEVELOPER_DATA_ID`), `ATT-5096` (radar developer fields), and `ATT-6879` (RPE/Feel). Zero SQLite imports (`REQ-DAT-042.5`). Delete `FitFileWriter.java`.

### Step 4: Update Contract Tests & Anchor TST-ARC-010 Evidence Tags (`REQ-ARC-010.4`)
* Files: `RepositoryEngineCleanlinessContractTest.kt`, `ExceptionHandlingHygieneTest.kt`, `FitFileWriterTest.kt`
* Action: Update paths in `RepositoryEngineCleanlinessContractTest.kt` and `ExceptionHandlingHygieneTest.kt` from `.java` to `.kt`. Add explicit `// TST-ARC-010` test method tags in `FitFileWriterTest.kt` to satisfy `ATT-8143` `--check-verified-evidence`.

### Step 5: Verification & Governance Verification (`REQ-ARC-010.5`)
* Command: `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.exporter.writer.*" --tests "com.atrainingtracker.trainingtracker.migration.FitLosslessRoundTripTest" --tests "com.atrainingtracker.trainingtracker.ui.RepositoryEngineCleanlinessContractTest" --tests "com.atrainingtracker.architecture.JavaCoreMigrationStrategyContractTest"`
* Action: Verify 100% test pass rate and run `python3 tools/verify_requirement_governance.py`.

---

## 6. Verification & Rollback Plan
* **Verification**: Tier 1 targeted exporter test suites followed by living doc and contract test verification.
* **Rollback**: `git checkout feature/ATT-7906 -- .` reverts all file changes cleanly.
