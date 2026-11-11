# Stage 2: Requirement & Test Specification - ATT-6693: Fix name-only [WorkoutName] round-trip leaking raw <Notes> into workoutNotes

**Ticket**: [ATT-6693](https://rainerblind.atlassian.net/browse/ATT-6693)  
**Parent Epic**: [ATT-281](https://rainerblind.atlassian.net/browse/ATT-281) (*Import, Export & Backup (Data Sovereignty & Migration)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.2`  
**Requirement Mapping**: `REQ-DAT-040` (`REQ-DAT-040.3`, `REQ-DAT-040.4`) (*TCX Structured DTO Serialization, Parsing Decoupling & Round-Trip Fidelity*)  
**Test Spec ID**: `TST-DAT-036` (`TST-DAT-036.3`, `TST-DAT-036.4`, `TST-DAT-036.5`)  
**Branch**: `feature/ATT-6693`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Requirement Specification (`REQ-DAT-040`)

### 1.1 Problem Statement & Rationale
When a workout with a custom name and no description is exported to TCX, `TCXFileWriter` writes `<Notes>[WorkoutName]</Notes>` and `<att:ActivityExtension><att:Name>WorkoutName</att:Name></att:ActivityExtension>`. On re-import, `TcxWorkoutParser` falls back via `?: activity.notes` when `activity.extensions.customDescription` is null, leaking raw `"[WorkoutName]"` into `workoutNotes`.

### 1.2 Functional & Architectural Requirements
1. `REQ-DAT-040.3` (Decoupled DTO XML Parser Bridge):
   - When `activity.extensions != null`, `TcxWorkoutParser` SHALL resolve `workoutNotes` strictly from `activity.extensions.customDescription?.takeIf { it.isNotBlank() }` and SHALL NOT fall back to `activity.notes`.
   - When `activity.extensions == null`, `TcxWorkoutParser` SHALL fall back to `activity.notes?.takeIf { it.isNotBlank() }` for backward compatibility with third-party TCX files lacking extensions.
2. `REQ-DAT-040.4` (Bidirectional Round-Trip Fidelity):
   - Exporting and re-importing workouts with custom names and blank/null descriptions SHALL preserve `workoutNotes = null`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Name-Only Workout Round-Trip)**:
  * *Given* a workout with `workoutName = "Morning Commute"` and `description = null`,
  * *When* exported via `TCXFileWriter` and parsed via `TcxWorkoutParser`,
  * *Then* `parsedWorkout.name` is `"Morning Commute"` and `parsedWorkout.notes` is `null` (not `"[Morning Commute]"`).
* **Criterion 2 (Description-Only Workout Round-Trip)**:
  * *Given* a workout with default name (null/blank) and `description = "Felt great"`,
  * *When* exported and parsed via `TcxWorkoutParser`,
  * *Then* `parsedWorkout.notes` is `"Felt great"`.
* **Criterion 3 (Lap-Level Notes Isolation)**:
  * *Given* a multi-lap workout where lap 1 has custom lap name and empty lap notes,
  * *When* round-tripped through TCX export and import,
  * *Then* lap 1 has `lapNotes == null`.
* **Criterion 4 (Legacy Unbracketed Third-Party Notes)**:
  * *Given* a TCX document without extensions where `<Notes>Pure note without brackets</Notes>`,
  * *When* parsed via `TcxWorkoutParser`,
  * *Then* `parsedWorkout.notes` resolves to `"Pure note without brackets"`.

### 1.4 System Invariants
- Zero database schema modifications or migrations.
- Zero disturbance to FIT or GPX exporter/importer pipelines.
- Strict Garmin TCX v2 schema compliance (`TrainingCenterDatabasev2.xsd`).

### 1.5. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-DAT-040` (specifically `REQ-DAT-040.3` and `REQ-DAT-040.4`), targeting `TcxWorkoutParser.kt` and `TCXRoundTripTest.kt`.
* **Historical Origin & Commit Trace**: `ATT-938` (`d1e744ac`, Sprint 2026-46.1): "Refactor TCX/XML import and export using structured DTO serialization".
* **Root Reason for Existing Formulation**: `?: activity.notes` was added defensively to ensure legacy TCX files lacking `<att:ActivityExtension>` still extracted notes from `<Notes>`, but overlooked that `TCXFileWriter` writes bracketed names to `<Notes>` even when description is empty.
* **Preservation of Core Invariants**:
  * Legacy third-party TCX files lacking extensions continue to populate notes via fallback when `activity.extensions == null`.
  * 100% round-trip fidelity for all telemetry channels (HR, cadence, watts, speed, lat/lng, altitude) is strictly preserved (`REQ-DAT-040.4`).
  * Zero database schema modifications or migrations.

---

## 2. Test Specification (`TST-DAT-036`)

### Test Case 1: Round-Trip Name & Notes Isolation (`TST-DAT-036.3`)
* **Scope**: Unit Test / Integration Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/tcx/TCXRoundTripTest.kt`
* **Preconditions**: In-memory test environment with `TcxSerializer` and `TcxWorkoutParser`.
* **Action**:
  1. `roundTrip_customNameOnly_preservesNullNotes()`: export workout with name `"Tempo Run"`, notes `null`, re-parse.
  2. `roundTrip_descriptionOnly_preservesNotes()`: export workout with blank name, notes `"Endurance pace"`, re-parse.
  3. `roundTrip_multiLap_customLapNameNullNotes()`: export multi-lap workout with lap-level custom name, empty lap notes, re-parse.
  4. `parseLegacyTcx_unbracketedNotes_populatesNotesCorrectly()`: parse TCX string without extensions having `<Notes>Unbracketed text</Notes>`.
* **Expected Result**: Assert `workoutNotes == null` for name-only, `workoutNotes == "Endurance pace"` for description-only, and no bracket leaking.

### Test Case 2: 9-Language Localization & Specifier Audit (`TST-DAT-036.Localization`)
* **Scope**: Localization Parity Test
* **Goal**: Confirm zero UI or localized string changes required for this parser fix.
* **Expected Result**: `git diff res/values*` is clean; 100% parity across all 9 locales.

### Test Case 3: Tier 1 Targeted Regression Suite (`TST-DAT-036.5`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.tcx.*" --tests "com.atrainingtracker.trainingtracker.exporter.*" --tests "com.atrainingtracker.trainingtracker.migration.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Expected Result**: 100% test pass rate across all TCX, exporter, and migration tests.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-DAT-036.3a` | Unit | `TcxWorkoutParser.parse(TcxDocument)` (Name Only) | `REQ-DAT-040.3`, `REQ-DAT-040.4` | Specified |
| `TST-DAT-036.3b` | Unit | `TcxWorkoutParser.parse(TcxDocument)` (Desc Only) | `REQ-DAT-040.3`, `REQ-DAT-040.4` | Specified |
| `TST-DAT-036.3c` | Unit | `TcxWorkoutParser.parse(TcxDocument)` (Lap Notes) | `REQ-DAT-040.3`, `REQ-DAT-040.4` | Specified |
| `TST-DAT-036.3d` | Unit | `TcxWorkoutParser.parse(TcxDocument)` (Unbracketed) | `REQ-DAT-040.3` | Specified |
| `TST-DAT-036.Loc` | Audit | `TranslationParityTest` | `REQ-UI-106` | Specified |
| `TST-DAT-036.5` | Regression | Targeted Gradle Suite | `REQ-PRO-001` | Specified |
