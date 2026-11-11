# Stage 1 Analysis: ATT-6693 - TcxWorkoutParser: Fix name-only [WorkoutName] round-trip leaking raw <Notes> into workoutNotes

**Ticket**: [ATT-6693](https://rainerblind.atlassian.net/browse/ATT-6693)  
**Sub-task**: Pending (`[Analysis]`)  
**Parent Epic**: [ATT-281](https://rainerblind.atlassian.net/browse/ATT-281) (*Import, Export & Backup (Data Sovereignty & Migration)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.2`  
**Branch**: `feature/ATT-6693`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation

In `ATT-938`, `TcxWorkoutParser.kt` introduced structured DTO parsing for Garmin TCX files. When resolving `workoutName` and `workoutNotes` from a parsed `TcxActivity` DTO (lines 75–77):
```kotlin
val workoutName = activity.extensions?.customName?.takeIf { it.isNotBlank() }
val workoutNotes = activity.extensions?.customDescription?.takeIf { it.isNotBlank() }
    ?: activity.notes?.takeIf { it.isNotBlank() }
```
When a workout has a custom `workoutName` (e.g. `"My Ride"`) and no description (`null` or blank), `TCXFileWriter` writes `<Notes>[My Ride]</Notes>` and `<att:ActivityExtension><att:Name>My Ride</att:Name></att:ActivityExtension>`.
On re-import, `TcxParser` sets `activity.extensions = TcxActivityExtensions(customName = "My Ride", customDescription = null)` and `activity.notes = "[My Ride]"`.
Because `customDescription` is `null`, `TcxWorkoutParser` falls back via `?: activity.notes`, leaking raw bracketed `"[My Ride]"` into `workoutNotes` instead of preserving `null`.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

* **Independent Peer Review & Verification**:
  1. **Elvis Fallback Bypass on Missing Description**:
     * In `TcxWorkoutParser.kt` line 77, `?: activity.notes` conflates "missing description in a parsed workout" with "absence of parsed extensions".
     * When `activity.extensions` is present (either parsed from `<att:ActivityExtension>` or populated by `TcxParser.parseBracketText`), `activity.extensions.customDescription` is the authoritative source for workout notes.
     * When `customDescription` is null, falling back to `activity.notes` re-evaluates the raw bracketed container string (`"[My Ride]"`), corrupting the entity.
  2. **Short Unbracketed Third-Party `<Notes>`**:
     * In `TcxParser.kt:439` (`parseBracketText`), unbracketed single-line notes map to `onParsed("", trimmed)`, setting `customDescription = trimmed` and leaving `customName = null`.
     * If `TcxWorkoutParser` enforces `activity.extensions != null -> activity.extensions.customDescription`, single-line notes populate `workoutNotes` without duplicating into `workoutName` or leaking raw text.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. In `TcxWorkoutParser.kt`, isolate `workoutNotes` resolution: when `activity.extensions != null`, use `activity.extensions.customDescription?.takeIf { it.isNotBlank() }` directly without falling back to `activity.notes`.
  2. Retain fallback to `activity.notes?.takeIf { it.isNotBlank() }` strictly when `activity.extensions == null` (raw legacy TCX without extensions or bracket parse).
  3. Expand `TCXRoundTripTest.kt` with comprehensive round-trip tests:
     - (a) Custom `workoutName` with empty/null description.
     - (b) Description only with default workout name.
     - (c) Multi-lap workout with lap-level custom name and empty description.
     - (d) Short unbracketed third-party `<Notes>` TCX import.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No modifications to database schemas (`WorkoutSummaries`, `WorkoutSamples`).
  * No modifications to FIT or GPX exporter/importer pipelines.
  * No UI or ViewModel alterations.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-DAT-040` (specifically `REQ-DAT-040.3` and `REQ-DAT-040.4`), targeting `TcxWorkoutParser.kt` and `TCXRoundTripTest.kt`.
* **Historical Origin & Commit Trace**: `ATT-938` (`d1e744ac`, Sprint 2026-46.1): "Refactor TCX/XML import and export using structured DTO serialization".
* **Root Reason for Existing Formulation**: `?: activity.notes` was added defensively to ensure legacy TCX files lacking `<att:ActivityExtension>` still extracted notes from `<Notes>`, but overlooked that `TCXFileWriter` writes bracketed names to `<Notes>` even when description is empty.
* **Preservation of Core Invariants**:
  * Legacy third-party TCX files lacking extensions continue to populate notes via fallback when `activity.extensions == null`.
  * 100% round-trip fidelity for all telemetry channels (HR, cadence, watts, speed, lat/lng, altitude) is strictly preserved (`REQ-DAT-040.4`).
  * Zero database schema modifications or migrations.

---

## 5. Architectural Strategy & High-Level Solution

In `TcxWorkoutParser.kt` (lines 75–78), update `workoutNotes` resolution to guard on `activity.extensions`:
```kotlin
val workoutName = activity.extensions?.customName?.takeIf { it.isNotBlank() }
val workoutNotes = if (activity.extensions != null) {
    activity.extensions.customDescription?.takeIf { it.isNotBlank() }
} else {
    activity.notes?.takeIf { it.isNotBlank() }
}
```

---

## 6. System Invariants & Risk Assessment

* **Risk Rating**: `LOW` (Pure parsing logic clarification in a single mapper file with zero schema or threading impact).
* **Invariants Maintained**:
  1. TCX Garmin v2 schema compliance (`TrainingCenterDatabasev2.xsd`).
  2. Lap-level name and description integrity (`ParsedLap`).
  3. Strict isolation between workout name and workout description.
