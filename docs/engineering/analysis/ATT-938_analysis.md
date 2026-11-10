# Stage 1 Analysis: Refactor TCX/XML Import/Export via Structured DTOs (ATT-938)

**Ticket**: [ATT-938](https://rainerblind.atlassian.net/browse/ATT-938)  
**Parent Epic**: [ATT-281](https://rainerblind.atlassian.net/browse/ATT-281) (*Import, Export & Backup*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-938`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Statement & Motivation
Currently, `TCXFileWriter.java` (exporter) and `TcxTrackpointParser.kt` / `TcxWorkoutParser.kt` / `LegacyImportEngine.kt` (importers) manage TCX training data interchange through manual XML string concatenation and mutable `XmlPullParser` state loops tightly coupled to SQLite cursors and `ContentValues`. Past extensions (e.g. `ATT-741`, `ATT-922`, `ATT-892` for lap metadata and workout tags) revealed this architecture's fragility: lack of schema encapsulation creates risks of schema sequence violations (`TrainingCenterDatabasev2.xsd`), escaping bugs, and silent field drops during import/export.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Hypothesis Audit**: The ticket references `TCXImporter.kt` and `TCXExporter.kt`; living code inspection verifies the actual components are `TCXFileWriter.java` (export) and `TcxTrackpointParser.kt` / `TcxWorkoutParser.kt` (import).
* **Tight DB-XML Coupling**: `TCXFileWriter` writes XML tags directly inside cursor iteration loops; `TcxTrackpointParser` parses XML directly into generic flat lists and `ParsedWorkoutData`. Neither uses an intermediate structured domain object.
* **Schema Fragility**: Garmin TCX v2 mandates a rigid sequence (`Id` -> `Lap`* -> `Notes`? -> `Creator`? -> `Extensions`? -> `Author`?). Manual string writes make sequence compliance brittle whenever new extension tags are introduced.
* **Independent Verification**: Introducing structured TCX Data Transfer Objects (DTOs) decouples XML serialization/deserialization from database persistence (`DB <-> DTO <-> XML`), establishing strict schema contracts, centralizing XML escaping, and enabling comprehensive round-trip testability (`TCXRoundTripTest`).

---

## 3. User Scope Grounding (ATT-1250) & Complexity Split Guardrail
* **In-Scope Goals**:
  * Define structured, immutable Kotlin DTOs for TCX Activity schema (`TcxDocument`, `TcxActivity`, `TcxLap`, `TcxTrackpoint`, `TcxPosition`, `TcxHeartRate`, `TcxExtensions`, `TcxDevice`, `TcxApplication`).
  * Implement structured DTO serializer (`TcxSerializer`) and parser bridge (`TcxParser` / `TcxWorkoutParser`).
  * Refactor `TCXFileWriter` and `TcxWorkoutParser` to route through DTOs while preserving legacy import engine compatibility.
  * Implement comprehensive bidirectional round-trip test suite (`TCXRoundTripTest`).
  * Update living-doc shard `docs/requirements/req_data_migration_export.md` (`REQ-DAT-040`) and `docs/tests/tst_data_migration_export.md` (`TST-DAT-036`).
* **Out-of-Scope Non-Goals**:
  * Modifying GPX or FIT export/import engines (isolated under separate tickets).
  * Altering SQLite database schemas (`WorkoutSummaries`, `WorkoutSamples`, `Laps`).
  * Modifying `.attbackup` zip archiving logic.
* **Complexity Rating**: **3 SP (Complex)**. Core data interchange refactoring with comprehensive round-trip verification.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new `REQ-DAT-040` (*TCX Structured DTO Serialization & Round-Trip Fidelity*), extending `REQ-DAT-012` (Lap Name/Desc), `REQ-DAT-013` (Workout Name/Desc), and `REQ-MIG-037`/`REQ-MIG-038` (Streaming TCX Engine).
* **Historical Origin & Commit Trace**: `ATT-892` (`276fc8c3`, TCX Lap metadata), `ATT-922` (`ad978340`, TCX workout name/desc), `ATT-741` (`7cf80cb4`, unclustered import preservation).
* **Root Reason for Existing Formulation**: Manual string concatenation and flat cursor loops were initially adopted to avoid JVM object allocations during high-frequency GPS tracking streams.
* **Preservation of Core Invariants**:
  1. TCX v2 schema sequence and Garmin XML namespace declarations MUST be 100% compliant.
  2. Proprietary extensions (`att:LapExtension`, `att:ActivityExtension`) and TPX sensor extensions (`Speed`, `Watts`, `RunCadence`) MUST be bit-exact.
  3. Third-party fallback heuristics (bracket parsing `[Name] Description`) MUST remain operational.
  4. Memory footprint MUST remain streaming-friendly to support 50,000+ sample workouts without OOM.

---

## 5. Architectural Strategy & High-Level Solution
* **DTO Layer (`com.atrainingtracker.trainingtracker.tcx.model`)**: Structured data classes modeling `TrainingCenterDatabasev2.xsd`.
* **Serialization & Parsing**: Streaming XML parser producing DTOs; streaming DTO serializer writing schema-compliant XML with automated XML character escaping.
* **Repository/Exporter Integration**: `TCXFileWriter` builds DTO from database models and invokes serializer; `TcxWorkoutParser` consumes DTO and outputs `ParsedWorkoutData`.
* **Round-Trip Test Suite**: `TCXRoundTripTest` validating synthetic and recorded activities: `Workout -> TCX -> Import -> SQLite -> TCX` preserves all metadata, lap boundaries, and telemetry channels.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: 100% Garmin schema validity; zero regression in workout/lap names, notes, and sensor telemetry; zero memory leak; 100% clean-room test suite pass.
* **Risk Rating**: **MEDIUM** (Critical data export/import path; mitigated by strict DTO type safety, preservation of schema invariants, and extensive round-trip tests).
