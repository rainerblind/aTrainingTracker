# Stage 1 Analysis: ATT-6694 - TcxSerializer & TcxParser: Add streaming trackpoint export and unify with TcxTrackpointParser

**Ticket**: [ATT-6694](https://rainerblind.atlassian.net/browse/ATT-6694)  
**Sub-task**: Pending (`[Analysis]`)  
**Parent Epic**: [ATT-281](https://rainerblind.atlassian.net/browse/ATT-281) (*Import, Export & Backup (Data Sovereignty & Migration)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.2`  
**Branch**: `feature/ATT-6694`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation

Following the DTO refactoring in `ATT-938`, two architectural technical debts remain:
1. **In-Memory Trackpoint Materialization in `TCXFileWriter.java`**:
   `TCXFileWriter` accumulates all trackpoints for an entire multi-hour workout into in-memory `ArrayList`s before passing the root `TcxDocument` to `TcxSerializer.serialize()`. For 30,000+ second workouts, this causes large transient heap allocations.
2. **Duplicated XML Pull Parsing Between `TcxParser.kt` and `TcxTrackpointParser.kt` (`Rule 36` DRY)**:
   `tcx/parser/TcxParser.kt` (465 lines) and `routes/parsers/TcxTrackpointParser.kt` (339 lines) implement duplicate `XmlPullParser` state loops. Course/waypoint parsing should be unified under `TcxParser` DTO architecture.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

* **Independent Peer Review & Verification**:
  1. **All-at-Once Serialization Overhead**:
     * In `TCXFileWriter.java:161-286`, `currentTrackpointsHolder[0].add(new TcxTrackpoint(...))` runs inside `streamSamples(...)`.
     * `TcxSerializer.kt` only offers `serialize(document: TcxDocument, writer: Writer)`, requiring a fully populated document.
     * By providing a streaming writer interface on `TcxSerializer` (`startLap`, `writeTrackpoint`, `endLap`, `endDocument`), `TCXFileWriter` can serialize trackpoints on-the-fly with $O(1)$ memory.
  2. **Duplicate XML State Machines**:
     * `TcxTrackpointParser.kt` parses `<Courses><Course>` and `<CoursePoint>` into `ParsedTcxData`.
     * `TcxModels.kt` currently lacks `TcxCourse` and `TcxCoursePoint` DTOs.
     * Adding `TcxCourse` / `TcxCoursePoint` to `TcxDocument` and parsing them in `TcxParser.kt` allows `TcxTrackpointParser` to delegate to `TcxParser` (or be deprecated in favor of `TcxParser`), eliminating ~300 lines of redundant XML parsing code.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Introduce streaming export API on `TcxSerializer`: methods or helper session allowing streaming of `<Lap>`, `<Trackpoint>`, and closing metadata without buffering trackpoints in memory.
  2. Refactor `TCXFileWriter.java` to serialize trackpoints incrementally via the streaming `TcxSerializer` API.
  3. Extend `TcxModels.kt` with `TcxCourse` and `TcxCoursePoint`, and update `TcxDocument` to include `courses: List<TcxCourse> = emptyList()`.
  4. Extend `TcxParser.kt` to parse `<Courses>`, `<Course>`, and `<CoursePoint>` blocks into `TcxDocument`.
  5. Refactor `TcxTrackpointParser.kt` and `TcxCourseParser.kt` to delegate to `TcxParser`.
  6. Expand unit tests in `TcxSerializerTest.kt`, `TcxParserTest.kt`, and `TCXRoundTripTest.kt`.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No modifications to database schemas or Fit/GPX exporter classes.
  * No alterations to GPX course/waypoint parsers (`GpxTrackpointParser.kt`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

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

## 5. Architectural Strategy & High-Level Solution

1. **Streaming Serialization API (`TcxSerializer.kt`)**:
   Add `TcxWriter` / streaming methods:
   - `startDocument(writer: Writer)`
   - `startActivity(sport: String, id: String, writer: Writer)`
   - `startLap(lap: TcxLap, writer: Writer)`
   - `writeTrackpoint(tp: TcxTrackpoint, writer: Writer)`
   - `endLap(lap: TcxLap, writer: Writer)`
   - `endActivity(activity: TcxActivity, writer: Writer)`
   - `endDocument(author: TcxAuthor?, writer: Writer)`
2. **Unified TCX Parsing (`TcxModels.kt` & `TcxParser.kt`)**:
   - Add `TcxCourse` and `TcxCoursePoint` data classes.
   - Update `TcxParser` START_TAG / TEXT / END_TAG handling to recognize `Courses`, `Course`, and `CoursePoint`.
   - Forward `TcxTrackpointParser.parse()` to `TcxParser.parse()` and map to `ParsedTcxData` for backward compatibility.

---

## 6. System Invariants & Risk Assessment

* **Risk Rating**: `LOW` (Refactoring internal parser/serializer layers with 100% coverage via existing automated test suites).
* **System Invariants**:
  1. Identical TCX XML output produced by `TCXFileWriter`.
  2. Identical `RouteImportResult` produced by `TcxCourseParser`.
  3. $O(1)$ memory consumption during workout sample streaming.
