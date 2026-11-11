# Stage 3: Implementation Plan - ATT-6694: TcxSerializer & TcxParser: Add streaming trackpoint export and unify with TcxTrackpointParser

**Ticket**: [ATT-6694](https://rainerblind.atlassian.net/browse/ATT-6694)  
**Sub-task**: Pending (`[Impl-Plan]`)  
**Parent Epic**: [ATT-281](https://rainerblind.atlassian.net/browse/ATT-281) (*Import, Export & Backup (Data Sovereignty & Migration)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.2`  
**Requirement Mapping**: `REQ-DAT-040` (`REQ-DAT-040.1`, `REQ-DAT-040.2`, `REQ-DAT-040.3`, `REQ-DAT-040.5`), `REQ-MIG-037` (`REQ-MIG-037.2`)  
**Test Mapping**: `TST-DAT-036` (`TST-DAT-036.1`, `TST-DAT-036.2`, `TST-DAT-036.3`, `TST-DAT-036.5`)  
**Branch**: `feature/ATT-6694`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Problem Description & Background

In `ATT-938`, structured DTO serialization was introduced for TCX activities. However:
1. `TCXFileWriter.java` buffers all `TcxTrackpoint` and `TcxLap` instances across the entire workout into in-memory `ArrayList`s before serializing, creating excessive heap overhead for 30,000+ second workouts.
2. `routes/parsers/TcxTrackpointParser.kt` and `tcx/parser/TcxParser.kt` duplicate ~300 lines of XML pull parser state machine logic for activities, courses, and course points (Rule 36 DRY violation).

---

## 2. Traceability & Requirements Mapping

* **Requirements**:
  * `REQ-DAT-040.1`: Introduce `TcxCourse` and `TcxCoursePoint` DTOs in `TcxModels.kt`.
  * `REQ-DAT-040.2` & `REQ-DAT-040.5`: Streaming TCX serializer API on `TcxSerializer.kt` enabling $O(1)$ memory streaming in `TCXFileWriter.java`.
  * `REQ-DAT-040.3` & `REQ-MIG-037.2`: Extend `TcxParser.kt` to parse `<Courses>` and delegate `TcxTrackpointParser.kt` to `TcxParser.kt`.
* **Test Mapping**: `TST-DAT-036` (`TST-DAT-036.1` streaming parity, `TST-DAT-036.2` course parsing, `TST-DAT-036.3` contract regression, `TST-DAT-036.5` targeted unit suite).

---

## 3. System Invariants & Preserved Behavior

1. **Bit-for-Bit TCX Serialization Parity**: Output TCX XML emitted by `TCXFileWriter` must remain schema-compliant and byte-for-byte equivalent to batch output.
2. **Route Import Parity**: `TcxCourseParser` must produce identical `RouteImportResult` polylines and waypoints.
3. **Contract Test Backward Compatibility**: `TrackpointXmlParserTest` and `TcxLeadingWhitespaceSanitizationTest` must continue to pass with 100% success.
4. **Zero Schema Modifications**: No SQLite schema or table alterations.

---

## 4. Proposed Architectural Changes

### Component 1: `TcxModels.kt`
* Add `TcxCourse` and `TcxCoursePoint` immutable data classes.
* Add `courses: List<TcxCourse> = emptyList()` to `TcxDocument`.

### Component 2: `TcxSerializer.kt`
* Expose streaming methods: `startDocument`, `startActivityHeader`, `startLapHeader`, `endLap`, `endActivity`, `endDocument`.
* Retain `serialize(document, writer)` batch overload as a streaming delegation wrapper.

### Component 3: `TCXFileWriter.java`
* Refactor sample loop: open `BufferedWriter`, emit document/activity/lap headers, write each `TcxTrackpoint` directly via `writeTrackpoint()`, and close laps/activity incrementally.
* Eliminate `currentTrackpointsHolder` ArrayList heap buffer.

### Component 4: `TcxParser.kt` & `TcxTrackpointParser.kt`
* Add `<Courses>`, `<Course>`, `<CoursePoint>` parsing logic to `TcxParser.kt`.
* Refactor `TcxTrackpointParser.parse()` into an adapter mapping `TcxDocument` into `ParsedTcxData`, retiring duplicated parser loop.

### UI Consistency (Rule 23)
* No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Add Course Models in `TcxModels.kt` (`REQ-DAT-040.1`)
* Define `TcxCourse` and `TcxCoursePoint`; add `courses` to `TcxDocument`.

### Step 2: Implement Streaming Methods in `TcxSerializer.kt` (`REQ-DAT-040.2`, `REQ-DAT-040.5`)
* Implement `startDocument`, `startActivityHeader`, `startLapHeader`, `endLap`, `endActivity`, `endDocument`.

### Step 3: Stream Trackpoints in `TCXFileWriter.java` (`REQ-DAT-040.5`)
* Refactor `streamSamples` callback to write trackpoints directly to `BufferedWriter`.

### Step 4: Add Course Parsing to `TcxParser.kt` (`REQ-DAT-040.3`)
* Parse `<Courses>`, `<Course>`, `<CoursePoint>`, mapping into `TcxCourse` and `TcxCoursePoint`.

### Step 5: Delegate `TcxTrackpointParser.kt` to `TcxParser.kt` (`REQ-MIG-037.2`)
* Replace duplicate XML pull loop with `TcxParser.parse()` and map to `ParsedTcxData`.

### Step 6: Verify Tests & Regressions (`TST-DAT-036.5`)
* Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.tcx.*" --tests "com.atrainingtracker.trainingtracker.exporter.*" --tests "com.atrainingtracker.trainingtracker.routes.*"`
