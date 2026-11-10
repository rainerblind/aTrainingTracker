# Stage 2: Requirement & Test Specification - ATT-938: TCX Structured DTO Refactor

**Ticket**: [ATT-938](https://rainerblind.atlassian.net/browse/ATT-938)  
**Parent Epic**: [ATT-281](https://rainerblind.atlassian.net/browse/ATT-281) (*Import, Export & Backup*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-938`  
**Requirement Mapping**: `REQ-DAT-040` | **Test Spec ID**: `TST-DAT-036`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Requirement Specification (REQ-DAT-040)

### 1.1 Problem Statement & Rationale
Manual XML string concatenation in `TCXFileWriter.java` and mutable parser loops in `TcxWorkoutParser.kt` tightly couple XML generation to database queries and cursors. This creates sequence fragility (`TrainingCenterDatabasev2.xsd`), escaping vulnerabilities, and field loss risks. Introducing structured, immutable Kotlin DTOs cleanly decouples serialization and parsing from persistence.

### 1.2 Functional & Architectural Requirements
1. **TCX DTO Domain Models (`REQ-DAT-040.1`)**: Define immutable data classes for the TCX schema (`TcxDocument`, `TcxActivity`, `TcxLap`, `TcxTrackpoint`, `TcxPosition`, `TcxHeartRate`, `TcxExtensions`, `TcxDevice`, `TcxApplication`) supporting standard tags and TPX v2 extensions (`Speed`, `Watts`, `RunCadence`).
2. **Streaming DTO Serializer (`REQ-DAT-040.2`)**: Implement `TcxSerializer` to serialize `TcxDocument` into schema-compliant XML enforcing strict element sequence, XML character escaping, and integer formats for Cadence/Watts (`REQ-EXP-001`).
3. **Decoupled Parser Bridge (`REQ-DAT-040.3`)**: Implement `TcxParser` to stream XML into `TcxDocument` DTOs via `XmlPullParser`, updating `TcxWorkoutParser` to delegate to it while preserving `att:ActivityExtension`, `att:LapExtension`, and bracket fallback naming.
4. **Bidirectional Round-Trip Fidelity (`REQ-DAT-040.4`)**: Guarantee bit-exact round-trip preservation of timestamps, names, notes, laps, and sensor streams.
5. **Memory & Streaming Safety (`REQ-DAT-040.5`)**: Retain streaming/batch memory efficiency to handle 50,000+ trackpoints without OOM.

### 1.3 Acceptance Criteria (Given-When-Then)
* *Given* a workout with custom metadata, laps, and power/cadence telemetry,
  * *When* exported to TCX format via `TCXFileWriter` and `TcxSerializer`,
  * *Then* the XML is fully valid against Garmin schema, elements follow mandatory sequence, and special characters are escaped.
* *Given* an exported or third-party TCX XML stream,
  * *When* parsed via `TcxWorkoutParser` and `TcxParser`,
  * *Then* it maps to structured DTOs and `ParsedWorkoutData` without telemetry corruption.
* *Given* a synthetic workout exported to TCX and re-imported via `TcxWorkoutParser`,
  * *When* compared against original workout data,
  * *Then* all metadata, laps, and trackpoint streams match within floating-point tolerance.

### 1.4 System Invariants
Zero SQLite schema modifications; zero regression in FIT/GPX pipelines; 100% test pass rate.

---

## 2. Test Specification (TST-DAT-036)

### Test Case 1: DTO Serialization & Schema Compliance Contract Test (`TST-DAT-036.1`)
* **Scope**: Unit Test (`TcxSerializerTest.kt`).
* **Assertions**: Strict TCX v2 sequence, proper XML character escaping (`&`, `<`, `>`, `"`, `'`), integer formatting of `<Watts>` and `<Cadence>`.

### Test Case 2: DTO Streaming Parser Decoupling Test (`TST-DAT-036.2`)
* **Scope**: Unit Test (`TcxParserTest.kt`).
* **Assertions**: Parses full TCX documents; preserves `att:ActivityExtension`, `att:LapExtension`, and bracketed titles; handles optional fields safely.

### Test Case 3: Bidirectional Round-Trip Test (`TST-DAT-036.3`)
* **Scope**: Integration Test (`TCXRoundTripTest.kt`).
* **Assertions**: Serializes synthetic workout to TCX, re-parses it, asserts 1:1 identity of workout attributes, laps, and sensor streams.

### Test Case 4: Existing Exporter & Parser Regression Suite (`TST-DAT-036.4`)
* **Scope**: Regression Test (`TCXFileWriterWorkoutNameTest`, `TCXFileWriterLapTest`, `TcxWorkoutParserTest`).
* **Assertions**: 100% pass across existing exporter and parser tests.

### Test Case 5: Tier 1 Targeted Regression Suite (`TST-DAT-036.5`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.tcx.*" --tests "com.atrainingtracker.trainingtracker.exporter.*" --tests "com.atrainingtracker.trainingtracker.migration.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Assertions**: 100% pass across touched packages and architectural build rules.

---

## 3. Traceability Matrix

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-DAT-036.1]` | Unit | `TcxSerializerTest`: Sequence, escaping, integer formatting | `REQ-DAT-040.1`, `REQ-DAT-040.2` | Planned |
| `[TST-DAT-036.2]` | Unit | `TcxParserTest`: Parsing DTOs, custom extensions, fallback tags | `REQ-DAT-040.1`, `REQ-DAT-040.3` | Planned |
| `[TST-DAT-036.3]` | Integration | `TCXRoundTripTest`: Export -> Import round-trip fidelity | `REQ-DAT-040.4` | Planned |
| `[TST-DAT-036.4]` | Regression | `TCXFileWriter*`, `TcxWorkoutParserTest` backward compatibility | `REQ-DAT-040.3` | Planned |
| `[TST-DAT-036.5]` | Regression | Tier 1 targeted suite (`tcx.*`, `exporter.*`, `migration.*`, `build.*`) | `REQ-PRO-001`, `REQ-DAT-040.5` | Planned |
