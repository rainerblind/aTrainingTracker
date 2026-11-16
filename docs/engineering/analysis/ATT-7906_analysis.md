# Stage 1 Analysis: ATT-7906 - Java Core Migration: Migrate streaming file writers (FitFileWriter, TCXFileWriter) to Kotlin

**Ticket**: [ATT-7906](https://rainerblind.atlassian.net/browse/ATT-7906)  
**Sub-task**: [ATT-8294](https://rainerblind.atlassian.net/browse/ATT-8294) (`[Analysis]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `Sprint 2026-46.8`  
**Branch**: `feature/ATT-7906`  
**Author**: Lane 2 Pro Agent (`agent1`)  
**Date**: 2026-11-16  

---

## 1. Problem Statement & Motivation
In accordance with ADR-001 (ATT-7566), workout exporters stream serialized sessions to disk. `BaseFileWriter.java` (148 LOC), `TCXFileWriter.java` (436 LOC), and `FitFileWriter.java` (1,052 LOC) suffer from platform type (`T!`) NPE hazards and lack Kotlin's structured resource management (`.use {}`). Migrating these core streaming writers to idiomatic Kotlin delivers compile-time null safety, modern streaming I/O, and zero resource leaks while preserving 100% byte-for-byte and XML schema parity.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Hypothesis Evaluation**: Migrating `FitFileWriter` and `TCXFileWriter` to Kotlin eliminates platform types and improves resource safety without breaking export fidelity.
* **Independent Codebase Verification**:
  * Verified: `FitFileWriter.java` (1,052 LOC) and `TCXFileWriter.java` (436 LOC) inherit from `BaseFileWriter.java` (148 LOC) and are instantiated in `ExportManager.java` (`FileFormat.FIT`, `TCX`, `STRAVA`).
  * Verified latest sprint merges on `sprint/2026-46.8`: `ATT-8196` added cycling pedal dynamics (`PedalDynamicsAccumulator`, L/R balance, TE, PS) via $O(1)$ streaming without SQLite queries (`REQ-DAT-042.5`); `ATT-7999` added swimming pool length (`setPoolLength`); `ATT-7747` added metadata developer fields.
  * Verified contract tests: `RepositoryEngineCleanlinessContractTest.kt` (L113, L134, L222) and `ExceptionHandlingHygieneTest.kt` (L45) hardcode paths to `.java` files and must be updated to `.kt` (or support both) during migration.
  * Verified remaining writers: `GPXFileWriter.java`, `GCFileWriter.java`, and `CSVFileWriter.java` still query `WorkoutSamplesDatabaseManager` directly. Scoping ATT-7906 strictly to `BaseFileWriter`, `TCXFileWriter`, and `FitFileWriter` (1,636 LOC) respects the 3 SP ceiling; `open class BaseFileWriter` preserves full Java interop.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Migrate `BaseFileWriter.java`, `TCXFileWriter.java`, and `FitFileWriter.java` to idiomatic Kotlin (`.kt`).
  * Enforce Kotlin `.use {}` structured resource management for all file encoders, writers, and streams.
  * Preserve 100% binary FIT protocol V2.0, XML schema compliance, and lossless round-trip parity.
  * Maintain Java interop (`open class`, `@JvmOverloads`, `@JvmField` where needed) for `ExportManager` and remaining writers.
  * Update architectural contract tests (`RepositoryEngineCleanlinessContractTest`, `ExceptionHandlingHygieneTest`).
* **Out-of-Scope Non-Goals**:
  * Migrating legacy cursor-based writers (`GPXFileWriter`, `GCFileWriter`, `CSVFileWriter`) — deferred to separate follow-up ticket to prevent exceeding 3 SP.
  * Rewriting `TcxSerializer` or `FitWorkoutParser` (already Kotlin).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-ARC-005` (Tier 2 Java Migration), `REQ-EXP-001` (Interoperability), `REQ-EXP-022` (TCX XML), `REQ-EXP-042` (Radar Developer Fields), `REQ-EXP-043` (Pedal Dynamics), `REQ-DAT-042.5` (Zero-SQLite Exporter Invariant).
* **Historical Origin & Commit Trace**: ADR-001 (`ATT-7566`), `fdb444c0` (`ATT-8196`), `1b0be3ee` (`ATT-7999`), `807dcd1f` (`ATT-7747`).
* **Root Reason for Existing Formulation**: Java exporters evolved incrementally over years, adding radar, swimming, and pedal dynamics while maintaining strict binary compatibility and $O(1)$ memory consumption.
* **Preservation of Core Invariants**: 100% preservation of Garmin FIT SDK V2.0 serialization, zero SQLite imports in `FitFileWriter`/`TCXFileWriter` (`REQ-DAT-042.5`), and exact XML schema ordering.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-ARC-005.1` (Tier 2 Exporter Migration), `REQ-EXP-001`, `REQ-EXP-022`, `REQ-EXP-042`, `REQ-EXP-043`, `REQ-DAT-042.5`.
* **Affected Architecture Elements**: `BaseFileWriter.kt`, `TCXFileWriter.kt`, `FitFileWriter.kt`, `ExportManager.java`, `RepositoryEngineCleanlinessContractTest.kt`, `ExceptionHandlingHygieneTest.kt`.
* **Verification Impact & Test Surface**: `FitFileWriterTest`, `FitFileWriterRadarTest`, `FitFileWriterHrvTest`, `FitLosslessRoundTripTest`, `TCXFileWriter*Test`, `TCXRoundTripTest`, `RepositoryEngineCleanlinessContractTest`.
* **Risk Assessment & Invariants**: Medium risk (binary serialization fidelity); mitigated by existing extensive golden master tests. Invariants: zero regression in FIT/TCX export output, zero SQLite imports, Java caller compatibility.

---

## 6. Architectural Strategy & High-Level Solution
* Convert `BaseFileWriter.java` -> `BaseFileWriter.kt` as `open class` with protected properties accessible to Java subclasses.
* Convert `TCXFileWriter.java` -> `TCXFileWriter.kt` leveraging `TcxSerializer` and `.use {}` for BufferedWriter.
* Convert `FitFileWriter.java` -> `FitFileWriter.kt` preserving `FileEncoder.use {}`, `PedalDynamicsAccumulator`, `setPoolLength`, radar fields, and developer data IDs.
* Update contract tests to recognize migrated `.kt` paths.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**:
  1. 100% byte-level and schema parity with FIT Protocol V2.0 and Garmin TCX V2 schema.
  2. $O(1)$ streaming memory overhead via `WorkoutRepository.streamSamples` and zero SQLite imports (`REQ-DAT-042.5`).
  3. Seamless Java interop for `ExportManager` and unmigrated legacy file writers.
* **Risk Rating**: **MEDIUM** (Serialization protocol fidelity; mitigated by existing characterisation test suite).
