# Stage 3: Implementation Plan - ATT-938: TCX Structured DTO Refactor

**Ticket**: [ATT-938](https://rainerblind.atlassian.net/browse/ATT-938)  
**Parent Epic**: [ATT-281](https://rainerblind.atlassian.net/browse/ATT-281) (*Import, Export & Backup*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-938`  
**Requirement Mapping**: `REQ-DAT-040` (`REQ-DAT-040.1` - `REQ-DAT-040.5`)  
**Test Mapping**: `TST-DAT-036` (`TST-DAT-036.1` - `TST-DAT-036.5`)  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Description & Background
TCX export (`TCXFileWriter.java`) and import (`TcxWorkoutParser.kt` / `TcxTrackpointParser.kt`) are tightly coupled to database cursors and flat lists via manual string concatenation and mutable pull-parser loops. This plan details the architectural construction of strongly-typed Kotlin DTOs, a streaming schema-compliant `TcxSerializer`, a decoupled `TcxParser`, and a bidirectional `TCXRoundTripTest` suite without breaking existing database persistence or import pathways.

---

## 2. Traceability & Requirements Mapping
* **Requirements**: `REQ-DAT-040.1` (DTO models), `REQ-DAT-040.2` (Streaming serializer), `REQ-DAT-040.3` (Decoupled parser bridge), `REQ-DAT-040.4` (Round-trip fidelity), `REQ-DAT-040.5` (Streaming/memory safety).
* **Test Mapping**: `TST-DAT-036.1` (`TcxSerializerTest.kt`), `TST-DAT-036.2` (`TcxParserTest.kt`), `TST-DAT-036.3` (`TCXRoundTripTest.kt`), `TST-DAT-036.4` (Backward compatibility suite), `TST-DAT-036.5` (Tier 1 targeted regression suite).

---

## 3. System Invariants & Preserved Behavior
1. **Schema Sequence Invariance**: TCX v2 sequence (`Id` -> `Lap`* -> `Notes`? -> `Creator`? -> `Extensions`? -> `Author`?) strictly enforced (`TrainingCenterDatabasev2.xsd`).
2. **Zero Telemetry Loss**: Workout names, descriptions, lap names, lap notes, and all sensor channels (HR, Cadence, Speed, Power, RunCadence) preserved bit-exact.
3. **Third-Party Compatibility**: Bracket notation fallback (`[Name] Description`) and unextended TCX files parse seamlessly.
4. **Code Lock (Rule 4)**: No production modifications in `app/src/main/` until Gate 3 is approved.
5. **UI Consistency (Rule 23)**: No UI changes.

---

## 4. Proposed Architectural Changes
### Component 1: `com.atrainingtracker.trainingtracker.tcx.model` (DTO Layer)
* Define immutable data classes: `TcxDocument`, `TcxActivity`, `TcxLap`, `TcxTrackpoint`, `TcxPosition`, `TcxHeartRate`, `TcxExtensions` (`LapExtension`, `ActivityExtension`, `TrackpointExtension`), `TcxDevice`, `TcxApplication`.

### Component 2: `com.atrainingtracker.trainingtracker.tcx.serializer.TcxSerializer`
* Pure streaming serializer accepting `TcxDocument` and writing to `Writer`/`OutputStream`.
* Enforces Garmin TCX v2 sequence ordering, automated XML character escaping (`escapeXml`), and integer conversions for Watts and Cadence (`REQ-EXP-001`).

### Component 3: `com.atrainingtracker.trainingtracker.tcx.parser.TcxParser`
* Streaming `XmlPullParser` engine deserializing XML input into `TcxDocument` DTOs.
* Handles proprietary namespaces (`att:LapExtension`, `att:ActivityExtension`), standard TPX extensions, and bracket title fallbacks.

### Component 4: Integration in `TCXFileWriter.java` & `TcxWorkoutParser.kt`
* `TCXFileWriter`: Maps database entities/cursors to `TcxActivity` DTO stream and delegates serialization to `TcxSerializer`.
* `TcxWorkoutParser`: Delegates XML ingestion to `TcxParser` and maps `TcxDocument` to `ParsedWorkoutData`, preserving `LegacyImportEngine` compatibility.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
### Step 1: TCX DTO Domain Models (`REQ-DAT-040.1`)
* Create `app/src/main/java/com/atrainingtracker/trainingtracker/tcx/model/TcxModels.kt`.

### Step 2: Streaming DTO Serializer & Unit Test (`REQ-DAT-040.2`, `TST-DAT-036.1`)
* Implement `TcxSerializer.kt` in `tcx.serializer`; verify with `TcxSerializerTest.kt`.

### Step 3: Decoupled DTO Parser & Unit Test (`REQ-DAT-040.3`, `TST-DAT-036.2`)
* Implement `TcxParser.kt` in `tcx.parser`; verify with `TcxParserTest.kt`.

### Step 4: Exporter & Parser Bridge Integration (`REQ-DAT-040.2`, `REQ-DAT-040.3`)
* Route `TCXFileWriter.java` and `TcxWorkoutParser.kt` through DTO serializer and parser; verify existing tests (`TCXFileWriterWorkoutNameTest`, `TCXFileWriterLapTest`, `TcxWorkoutParserTest`).

### Step 5: Bidirectional Round-Trip Integration Test & Suite Verification (`TST-DAT-036.3` - `TST-DAT-036.5`)
* Implement `TCXRoundTripTest.kt` verifying export -> import -> re-export equivalence.
* Execute targeted regression suite:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.tcx.*" --tests "com.atrainingtracker.trainingtracker.exporter.*" --tests "com.atrainingtracker.trainingtracker.migration.*" --tests "com.atrainingtracker.trainingtracker.build.*"
  ```

---

## 6. Verification & Rollback Plan
* **Verification**: `TcxSerializerTest`, `TcxParserTest`, `TCXRoundTripTest`, and clean-room unit test suite.
* **Rollback**: `git checkout feature/ATT-938 -- .` reverts all branch changes cleanly.
