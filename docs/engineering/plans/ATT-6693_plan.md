# Stage 3: Implementation Plan - ATT-6693: TcxWorkoutParser: Fix name-only [WorkoutName] round-trip leaking raw <Notes> into workoutNotes

**Ticket**: [ATT-6693](https://rainerblind.atlassian.net/browse/ATT-6693)  
**Sub-task**: Pending (`[Impl-Plan]`)  
**Parent Epic**: [ATT-281](https://rainerblind.atlassian.net/browse/ATT-281) (*Import, Export & Backup (Data Sovereignty & Migration)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.2`  
**Requirement Mapping**: `REQ-DAT-040` (`REQ-DAT-040.3`, `REQ-DAT-040.4`) (*TCX Structured DTO Serialization, Parsing Decoupling & Round-Trip Fidelity*)  
**Test Mapping**: `TST-DAT-036` (`TST-DAT-036.3`, `TST-DAT-036.5`)  
**Branch**: `feature/ATT-6693`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Problem Description & Background

In `ATT-938`, `TcxWorkoutParser.kt` (lines 75–77) implemented DTO mapping for imported TCX documents:
```kotlin
val workoutName = activity.extensions?.customName?.takeIf { it.isNotBlank() }
val workoutNotes = activity.extensions?.customDescription?.takeIf { it.isNotBlank() }
    ?: activity.notes?.takeIf { it.isNotBlank() }
```
When a workout has a custom `workoutName` and no description, `TCXFileWriter` writes `<Notes>[WorkoutName]</Notes>` alongside `<att:ActivityExtension><att:Name>WorkoutName</att:Name></att:ActivityExtension>`.
On re-import, `TcxParser` sets `activity.extensions.customDescription = null` and `activity.notes = "[WorkoutName]"`.
Because `customDescription` is `null`, the Elvis operator `?: activity.notes` evaluates, incorrectly populating `workoutNotes = "[WorkoutName]"` instead of remaining `null`.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-DAT-040` (*TCX Structured DTO Serialization, Parsing Decoupling & Round-Trip Fidelity*)
  * `REQ-DAT-040.3`: Decoupled DTO XML Parser Bridge — When `activity.extensions != null`, `workoutNotes` SHALL be derived strictly from `activity.extensions.customDescription?.takeIf { it.isNotBlank() }` without fallback to raw `activity.notes`. Fallback to `activity.notes` is permitted only when `activity.extensions == null`.
  * `REQ-DAT-040.4`: Bidirectional Round-Trip Fidelity — Workouts with custom name and empty/null description SHALL round-trip with `workoutNotes` strictly `null`.
* **Test Mapping**: `TST-DAT-036` (*TCX Structured DTO Serialization, Parsing Decoupling & Round-Trip Verification Suite*)
  * `TST-DAT-036.3`: Bidirectional round-trip unit tests in `TCXRoundTripTest.kt`.
  * `TST-DAT-036.5`: Tier 1 targeted regression suite for TCX, exporter, and migration modules.

---

## 3. System Invariants & Preserved Behavior

1. **Schema & Telemetry Invariance**: 100% preservation of all sensor channels (HR, Cadence, Power/Watts, Speed, Altitude, Lat/Lng) across TCX round-trip.
2. **Third-Party TCX Resilience**: Legacy files lacking `<att:ActivityExtension>` or parsed bracket metadata continue to populate `workoutNotes` via `activity.notes` when `activity.extensions == null`.
3. **Lap Notes Isolation**: Lap names and lap descriptions remain strictly isolated without leaking container strings.
4. **Zero Schema Alterations**: No database schema migrations or changes to SQLite tables.

---

## 4. Proposed Architectural Changes

### Component 1: `TcxWorkoutParser.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/parsers/TcxWorkoutParser.kt`
* **Change**: In `parse()`, replace the unconditional Elvis fallback on `activity.notes` with guarded resolution based on `activity.extensions != null`:
  ```kotlin
  val workoutName = activity.extensions?.customName?.takeIf { it.isNotBlank() }
  val workoutNotes = if (activity.extensions != null) {
      activity.extensions.customDescription?.takeIf { it.isNotBlank() }
  } else {
      activity.notes?.takeIf { it.isNotBlank() }
  }
  ```

### UI Consistency (Rule 23)
* No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update `TcxWorkoutParser.kt` Notes Resolution (`REQ-DAT-040.3`, `REQ-DAT-040.4`)
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/parsers/TcxWorkoutParser.kt`
* Guard `workoutNotes` on `activity.extensions != null` so `activity.notes` is never evaluated when extensions exist.

### Step 2: Expand `TCXRoundTripTest.kt` Verification Suite (`TST-DAT-036.3`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/tcx/TCXRoundTripTest.kt`
* Add targeted tests:
  1. `testNameOnlyRoundTripPreservesNullWorkoutNotes()`: Assert `workoutNotes == null` when workout name is present and description is empty.
  2. `testDescriptionOnlyRoundTripPreservesWorkoutNotes()`: Assert `workoutNotes` preserves description without name pollution.
  3. `testMultiLapCustomLapNamePreservesEmptyLapNotes()`: Assert lap description remains null when only lap name is defined.
  4. `testLegacyThirdPartyUnbracketedNotes()`: Assert unbracketed third-party TCX notes parse properly into notes.

### Step 3: Targeted Verification & Regression Suite (`TST-DAT-036.5`)
* Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.tcx.*" --tests "com.atrainingtracker.trainingtracker.exporter.*" --tests "com.atrainingtracker.trainingtracker.migration.*"`
* Assert 100% test pass rate with zero regressions.
