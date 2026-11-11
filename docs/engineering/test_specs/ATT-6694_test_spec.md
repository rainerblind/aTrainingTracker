# Stage 2: Requirement & Test Specification - ATT-6694: TcxSerializer & TcxParser: Add streaming trackpoint export and unify with TcxTrackpointParser

**Ticket**: [ATT-6694](https://rainerblind.atlassian.net/browse/ATT-6694)  
**Parent Epic**: [ATT-281](https://rainerblind.atlassian.net/browse/ATT-281) (*Import, Export & Backup (Data Sovereignty & Migration)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.2`  
**Requirement Mapping**: `REQ-DAT-040` (`REQ-DAT-040.1`, `REQ-DAT-040.2`, `REQ-DAT-040.3`, `REQ-DAT-040.5`), `REQ-MIG-037` (`REQ-MIG-037.2`)  
**Test Spec ID**: `TST-DAT-036` (`TST-DAT-036.1`, `TST-DAT-036.2`, `TST-DAT-036.3`, `TST-DAT-036.5`)  
**Branch**: `feature/ATT-6694`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Requirement Specification (`REQ-DAT-040`, `REQ-MIG-037`)

### 1.1 Problem Statement & Rationale
`TCXFileWriter` buffers all trackpoints of an entire workout into heap memory before invoking `TcxSerializer.serialize()`, causing large transient allocations on multi-hour recordings. Concurrently, `tcx/parser/TcxParser.kt` and `routes/parsers/TcxTrackpointParser.kt` duplicate ~300 lines of XML pull parser logic for activities, courses, and waypoints (violating Rule 36 DRY).

### 1.2 Functional & Architectural Requirements
1. `REQ-DAT-040.1` (Structured TCX DTO Domain Models):
   - `TcxDocument` SHALL support `courses: List<TcxCourse> = emptyList()`, and the system SHALL define `TcxCourse` and `TcxCoursePoint` DTOs.
2. `REQ-DAT-040.2` & `REQ-DAT-040.5` (Streaming TCX Serializer):
   - `TcxSerializer` SHALL provide a streaming writer API (`startDocument`, `startActivity`, `startLap`, `writeTrackpoint`, `endLap`, `endActivity`, `endDocument`) allowing incremental emission to a `Writer` with $O(1)$ heap overhead.
   - `TCXFileWriter` SHALL stream trackpoints on-the-fly without buffering them in memory.
3. `REQ-DAT-040.3` & `REQ-MIG-037.2` (Unified XML Parser Bridge):
   - `TcxParser` SHALL parse `<Courses>`, `<Course>`, and `<CoursePoint>` into `TcxDocument.courses`.
   - `TcxTrackpointParser.parse()` SHALL delegate to `TcxParser.parse()` and map results into `ParsedTcxData`, eliminating duplicated XML parser loops while preserving full backward compatibility.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Streaming Serializer Fidelity)**:
  * *Given* a workout with multiple laps and trackpoints streamed incrementally via `TcxSerializer`,
  * *When* serialized to TCX XML,
  * *Then* the output is bit-for-bit and schema-order identical to full-document serialization.
* **Criterion 2 (O(1) Heap Memory Stream in TCXFileWriter)**:
  * *Given* a multi-hour workout exported through `TCXFileWriter`,
  * *When* exported,
  * *Then* trackpoints are written directly to `BufferedWriter` without buffering thousands of `TcxTrackpoint` instances in memory.
* **Criterion 3 (Course & Waypoint Parsing via TcxParser)**:
  * *Given* a TCX Course XML containing `<Course>` trackpoints and `<CoursePoint>` waypoints,
  * *When* parsed via `TcxParser.parse()`,
  * *Then* `document.courses` contains the course name, trackpoints, and course points with exact coordinates and types.
* **Criterion 4 (TcxTrackpointParser DRY Delegation)**:
  * *Given* existing tests calling `TcxTrackpointParser.parse()`,
  * *When* executed,
  * *Then* 100% of activity and course test cases pass with identical `ParsedTcxData` mappings.

### 1.4 System Invariants
- Zero database schema modifications.
- Strict Garmin TCX v2 sequence and schema compliance (`TrainingCenterDatabasev2.xsd`).
- 100% backward compatibility for all existing route import pipelines.

### 1.5. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-DAT-040` (items `REQ-DAT-040.2`, `REQ-DAT-040.3`, `REQ-DAT-040.5`), amending `TcxSerializer.kt`, `TcxParser.kt`, `TCXFileWriter.java`, and `TcxTrackpointParser.kt` (`REQ-MIG-037`).
* **Historical Origin & Commit Trace**:
  * `ATT-3362` (`REQ-MIG-037`, Sprint 2026-42.3): Created `TcxTrackpointParser.kt` for route courses.
  * `ATT-938` (`REQ-DAT-040`, Sprint 2026-46.1): Created `TcxParser.kt` and `TcxSerializer.kt` for activity DTOs.
* **Root Reason for Existing Formulation**: `TcxParser` and `TcxTrackpointParser` were authored in separate sprints for activities and courses respectively; `TcxSerializer` focused on schema-compliant batch DTO output.
* **Preservation of Core Invariants**:
  * 100% round-trip fidelity for activities and courses.
  * Zero regressions in route import (`TcxCourseParser`) or existing contract tests (`TrackpointXmlParserTest`).
  * Garmin TCX v2 sequence ordering and XML character escaping maintained.

---

## 2. Test Specification (`TST-DAT-036`)

### Test Case 1: Streaming vs Batch Serializer Parity (`TST-DAT-036.1`)
* **Scope**: Unit Test (`TcxSerializerTest.kt`)
* **Action**: Serialize a multi-lap activity with TPX extensions using both batch `serialize()` and incremental streaming methods.
* **Expected Result**: Both outputs produce identical XML character streams.

### Test Case 2: Unified Parser Course & CoursePoint Test (`TST-DAT-036.2`)
* **Scope**: Unit Test (`TcxParserTest.kt`)
* **Action**: Ingest sample TCX course file containing multiple trackpoints and `<CoursePoint>` elements.
* **Expected Result**: Assert `document.courses.first().coursePoints` parsed with correct name, pointType, and coordinates.

### Test Case 3: TcxTrackpointParser Delegation Regression Suite (`TST-DAT-036.3`)
* **Scope**: Regression / Contract Test (`TrackpointXmlParserTest.kt`, `TcxLeadingWhitespaceSanitizationTest.kt`)
* **Action**: Run existing course and activity parser tests against delegated `TcxTrackpointParser`.
* **Expected Result**: 100% test pass rate with zero changes to caller assertions.

### Test Case 4: 9-Language Localization Audit (`TST-DAT-036.Localization`)
* **Scope**: Localization Parity Test
* **Goal**: Confirm zero UI or localized string changes required.
* **Expected Result**: `git diff res/values*` is clean; 100% parity across all 9 locales.

### Test Case 5: Tier 1 Targeted Regression Suite (`TST-DAT-036.5`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.tcx.*" --tests "com.atrainingtracker.trainingtracker.exporter.*" --tests "com.atrainingtracker.trainingtracker.migration.*" --tests "com.atrainingtracker.trainingtracker.routes.*"`
* **Expected Result**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-DAT-036.1` | Unit | `TcxSerializer.startLap / writeTrackpoint` | `REQ-DAT-040.2`, `REQ-DAT-040.5` | Specified |
| `TST-DAT-036.2` | Unit | `TcxParser.parse` (Courses) | `REQ-DAT-040.1`, `REQ-DAT-040.3` | Specified |
| `TST-DAT-036.3` | Contract | `TcxTrackpointParser.parse` | `REQ-MIG-037.2`, `REQ-DAT-040.3` | Specified |
| `TST-DAT-036.Loc` | Audit | `TranslationParityTest` | `REQ-UI-106` | Specified |
| `TST-DAT-036.5` | Regression | Targeted Gradle Suite | `REQ-PRO-001` | Specified |
