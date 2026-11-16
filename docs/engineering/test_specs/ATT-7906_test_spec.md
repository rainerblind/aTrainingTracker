# Stage 2: Requirement & Test Specification - ATT-7906: Streaming File Writers Kotlin Migration

**Ticket**: [ATT-7906](https://rainerblind.atlassian.net/browse/ATT-7906)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `Sprint 2026-46.8`  
**Requirement Mapping**: `REQ-ARC-010` (`REQ-ARC-010.1`..`REQ-ARC-010.5`)  
**Test Spec ID**: `TST-ARC-010` (`TST-ARC-010.1`..`TST-ARC-010.5`)  
**Branch**: `feature/ATT-7906` (Lane 2)  
**Author**: Lane 2 Flash Agent (`agent2`)  
**Date**: 2026-11-16  

---

## 1. Requirement Specification (`REQ-ARC-010`)

### 1.1 Problem Statement & Rationale
In accordance with ADR-001 (Tier 2 File Exporters & Serializers, ATT-7566), `BaseFileWriter.java` (148 LOC), `TCXFileWriter.java` (436 LOC), and `FitFileWriter.java` (1,052 LOC) suffer from platform type (`T!`) nullability hazards and lack Kotlin's structured resource management (`.use {}`). Migrating these core streaming writers to idiomatic Kotlin delivers compile-time null safety, modern streaming I/O, and zero resource leaks while preserving 100% byte-for-byte and XML schema parity.

### 1.2 Attributes
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit / Integration / Contract

### 1.3 Functional & Architectural Requirements
1. **1:1 Kotlin Migration & Structured Resource Management**: Migrate `BaseFileWriter`, `TCXFileWriter`, and `FitFileWriter` to idiomatic Kotlin (`.kt`) using `.use {}` for file encoders/writers; remove legacy `.java` files (`REQ-ARC-010.1`).
2. **Java Interop Parity**: Preserve `open class BaseFileWriter` with protected properties for unmigrated Java subclasses (`GPXFileWriter.java`, `GCFileWriter.java`, `CSVFileWriter.java`) without breaking Java callers or `ExportManager.java` (`REQ-ARC-010.2`).
3. **Preservation of Core Invariants & Feature Integrations**: Preserve `REQ-DAT-042.5` zero-SQLite streaming invariant ($O(1)$ memory streaming via `WorkoutRepository.streamSamples`), `ATT-8196` (`REQ-EXP-043` pedal dynamics & `PedalDynamicsAccumulator`), `ATT-7999` (swimming sport mapping & pool length), `ATT-7747` / `REQ-EXP-041` (lossless FIT session metadata developer fields & `APP_METADATA_DEVELOPER_DATA_ID`), `ATT-5096` / `REQ-EXP-042` (radar threat alert events), `ATT-6879` (RPE/Feel), and `REQ-EXP-022` (Garmin TCX v2 sequence ordering) (`REQ-ARC-010.3`).
4. **Contract Governance & Hygiene**: Update `RepositoryEngineCleanlinessContractTest.kt` to resolve migrated `.kt` paths for zero SQLite imports, update `ExceptionHandlingHygieneTest.kt` for `FitFileWriter.kt`, and verify ADR-001 Java class count reduction (`<= 149`) (`REQ-ARC-010.4`).
5. **Bounded Test Suite Verification**: Maintain 100% pass rate across all existing exporter characterisation and regression test suites (`REQ-ARC-010.5`).

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1**: *Given* `BaseFileWriter`, `TCXFileWriter`, and `FitFileWriter`, *When* migrated to Kotlin, *Then* legacy `.java` files are removed, all 3 classes compile under idiomatic Kotlin with `.use {}` resource management, and Java subclasses continue to compile seamlessly.
* **Criterion 2**: *Given* sample streaming during FIT and TCX export, *When* executed via `WorkoutRepository.streamSamples`, *Then* zero SQLite imports or database managers are utilized (`REQ-DAT-042.5`) and heap memory overhead remains $O(1)$.
* **Criterion 3**: *Given* workouts with pedal dynamics, swimming pool length, radar alerts, or session metadata, *When* exported to `.fit` and re-imported via `FitWorkoutParser`, *Then* all developer fields, aggregates, and protocol messages are preserved with 100% lossless fidelity.
* **Criterion 4**: *Given* `RepositoryEngineCleanlinessContractTest`, `ExceptionHandlingHygieneTest`, and `JavaCoreMigrationStrategyContractTest`, *When* executed, *Then* all contract assertions pass cleanly.

### 1.5 System Invariants
Zero SQLite imports in `FitFileWriter` and `TCXFileWriter` (`REQ-DAT-042.5`); 100% binary FIT Protocol V2.0 and Garmin TCX V2 schema parity; unmigrated Java subclasses remain 100% functional; zero user-facing string regressions.

---

## 2. Test Specification (`TST-ARC-010`)

### Test Case 1: Binary FIT Protocol Serialization & Parity (`TST-ARC-010.1`)
* **Scope**: Unit Test (`FitFileWriterTest`, `FitFileWriterRadarTest`, `FitFileWriterHrvTest`)
* **Goal**: Validate FIT message encoding, `FileEncoder.use {}` scoping, developer data ID headers, and $O(1)$ sample streaming without SQLite imports.

### Test Case 2: FIT Lossless Round-Trip & Developer Field Fidelity (`TST-ARC-010.2`)
* **Scope**: Integration Test (`FitLosslessRoundTripTest`)
* **Goal**: Assert lossless round-trip through `FitWorkoutParser` for pedal dynamics (`ATT-8196`), swimming pool length (`ATT-7999`), session metadata developer fields (`ATT-7747`), radar threat alerts (`ATT-5096`), and RPE/Feel (`ATT-6879`).

### Test Case 3: TCX XML Schema & Structured DTO Serialization (`TST-ARC-010.3`)
* **Scope**: Unit Test (`TCXFileWriterLapTest`, `TCXFileWriterWorkoutNameTest`, `TCXRoundTripTest`)
* **Goal**: Assert Garmin TCX v2 sequence ordering, XML character escaping, and `TcxSerializer` structured DTO generation.

### Test Case 4: Java Subclass Interoperability Suite (`TST-ARC-010.4`)
* **Scope**: Unit Test (`GPXFileWriterTest`, `GCFileWriterTest`, `CSVFileWriterTest`)
* **Goal**: Assert that legacy Java writers inheriting from `open class BaseFileWriter` compile and function without behavioral regressions.

### Test Case 5: Contract & Exception Handling Governance Suite (`TST-ARC-010.5`)
* **Scope**: Architectural Contract Test (`RepositoryEngineCleanlinessContractTest`, `ExceptionHandlingHygieneTest`, `JavaCoreMigrationStrategyContractTest`)
* **Goal**: Assert zero SQLite imports in `BaseFileWriter`, `TCXFileWriter`, and `FitFileWriter`, zero unlogged empty catch blocks, and repository Java file count decreases while remaining `<= 149`.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Class / Method | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-ARC-010.1` | Unit | `FitFileWriter.kt` (FIT encoding, `.use {}`) | `REQ-ARC-010.1`, `REQ-ARC-010.3` | Planned (ATT-7906) |
| `TST-ARC-010.2` | Integration | `FitLosslessRoundTripTest.kt` (pedal dynamics, radar, swimming, metadata) | `REQ-ARC-010.3` | Planned (ATT-7906) |
| `TST-ARC-010.3` | Unit | `TCXFileWriter.kt` (XML schema, DTO serialization) | `REQ-ARC-010.1`, `REQ-ARC-010.3` | Planned (ATT-7906) |
| `TST-ARC-010.4` | Interop | `BaseFileWriter.kt` (`open class`, Java subclass inheritance) | `REQ-ARC-010.2` | Planned (ATT-7906) |
| `TST-ARC-010.5` | Contract | `RepositoryEngineCleanlinessContractTest`, `ExceptionHandlingHygieneTest` | `REQ-ARC-010.4` | Planned (ATT-7906) |
