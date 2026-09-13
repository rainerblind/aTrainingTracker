# Walkthrough: TCX Workout Name Preservation (ATT-922)

* **Parent Ticket**: [ATT-922](https://rainerblind.atlassian.net/browse/ATT-922) (*[Bug] TCX Export / Import must respect the name of a workout*)
* **Subtasks**:
  - [ATT-923](https://rainerblind.atlassian.net/browse/ATT-923) (*[Analysis] TCX Export / Import must respect the name of a workout* - Erledigt)
  - [ATT-924](https://rainerblind.atlassian.net/browse/ATT-924) (*[Test-Spec] TCX Export / Import must respect the name of a workout* - Erledigt)
  - [ATT-926](https://rainerblind.atlassian.net/browse/ATT-926) (*[Impl-Plan] TCX Export / Import must respect the name of a workout* - Erledigt)
  - [ATT-927](https://rainerblind.atlassian.net/browse/ATT-927) (*[Implementation] TCX Export / Import must respect the name of a workout* - Erledigt)
  - [ATT-929](https://rainerblind.atlassian.net/browse/ATT-929) (*[Test] TCX Export / Import must respect the name of a workout* - In Review)
* **Requirement**: `REQ-DAT-013` (Verified)
* **Test Specification**: `TST-DAT-007` (Verified)
* **Target Version**: `V4.9.36`
* **Branch**: `bugfix/ATT-922`

---

## 1. Executive Summary

During testing of previous export/import features (`ATT-892`), workouts exported to TCX, deleted, and re-imported lost their custom titles, reverting to the date-based `fileBaseName` or being overwritten by an auto-matched route cluster name.

We resolved this bug by implementing bidirectional serialization and deserialization of workout names and descriptions conforming to the Garmin TCX v2 schema (`TrainingCenterDatabasev2.xsd`), while immunizing imported custom names against subsequent route cluster overwrites.

Key capabilities delivered:
1. **Header Data Extraction (`BaseFileWriter.java`)**:
   - Explicitly queries and caches `WorkoutSummaries.WORKOUT_NAME` (`workoutName`) from the workout summaries database cursor.
2. **Dual TCX Activity Serialization (`TCXFileWriter.java`)**:
   - Universal Human-Readable: Serializes custom workout names and descriptions in `<Activity><Notes>[Workout Name] Description</Notes>` immediately after all `<Lap>` elements.
   - Machine-Readable Structured Extension: Serializes `<Activity><Extensions><att:ActivityExtension xmlns:att="...">` with `<att:Name>` and `<att:Description>`.
   - Strict Schema Sequence Compliance: Emits elements strictly adhering to TCX schema sequence (`Lap` $\rightarrow$ `Notes` $\rightarrow$ `Extensions` $\rightarrow$ `/Activity`).
   - Character Escaping: Guarantees full XML escaping (`&amp;`, `&lt;`, `&gt;`, `&quot;`, `&apos;`).
3. **Robust Import Deserialization (`LegacyImportEngine.kt`)**:
   - Parser Scope Isolation: Extracts workout name strictly within `<Activity>` scope, guarding against pollution from device `<Creator><Name>` or `<Author><Name>`.
   - Multi-Format Parsing: Extracts custom names from structured `<att:Name>`, standard bracket notation `[Name] Description`, and Garmin training plan `<Training><Plan><Name>`.
   - Database Persistence: Populates `WorkoutSummaries.WORKOUT_NAME` upon initial summary insertion and during `recalculateStats()`.
4. **Cluster Auto-Overwrite Immunity (`WorkoutClusterEngine.kt`)**:
   - By restoring `WorkoutSummaries.WORKOUT_NAME` to the custom name rather than `fileBaseName`, the condition in `WorkoutClusterEngine.assignClusterToWorkout()` line 627 (`if (forceIdentity || currentName.isNullOrEmpty() || currentName == fileBaseName)`) evaluates to `false`, shielding custom workout titles from route cluster overwrites.

---

## 2. Changes Summary

| Area | Component | Change | Description |
| :--- | :--- | :--- | :--- |
| **Export Core** | `BaseFileWriter.java` | [MODIFY] | Added `workoutName` field and queried `WorkoutSummaries.WORKOUT_NAME` in `getHeaderData()`. |
| **TCX Serializer** | `TCXFileWriter.java` | [MODIFY] | Added dual serialization of workout name/description into `<Notes>` and `<att:ActivityExtension>` before `</Activity>`. |
| **Import Engine** | `LegacyImportEngine.kt` | [MODIFY] | Added parser state for `workoutName`, bracket regex extraction, structured extension parsing, and persistence to `WorkoutSummaries`. |
| **Export Tests** | `TCXFileWriterWorkoutNameTest.kt` | [NEW] | 6 unit tests covering dual serialization, name only, desc only, XML escaping, and TCX schema order. |
| **Import Tests** | `LegacyImportEngineWorkoutNameTest.kt` | [NEW] | 6 unit tests covering structured extensions, bracket notes, training plans, entity unescaping, and author/creator exclusion. |
| **Regression Tests** | `TCXFileWriterLapTest.kt` | [MODIFY] | Added `WorkoutSummaries.WORKOUT_NAME` mock to summary columns in lap export tests. |
| **Requirements** | `docs/requirements.md` | [MODIFY] | Updated `REQ-DAT-013` status to `Verified`. |
| **Test Specs** | `docs/tests.md` | [MODIFY] | Updated `TST-DAT-007` status to `Verified`. |

---

## 3. Verification & Test Results

### 3.1 Automated Tests
* **Feature Tests**:
  - `TCXFileWriterWorkoutNameTest`: 6/6 tests passing.
  - `LegacyImportEngineWorkoutNameTest`: 6/6 tests passing.
* **Full Clean-Room Test Suite**:
  - Command: `./gradlew testDebugUnitTest`
  - Result: **329/329 tests passing (100% GREEN, 0 regressions)**.

### 3.2 Git Commits
* `499bb424`: `fix(export): preserve workout name across TCX export and import (ATT-922, ATT-927)`
