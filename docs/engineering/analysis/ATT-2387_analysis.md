# Stage 1 Analysis: ATT-2387 - Map Origin Source Attribute in WorkoutDataMapper Batch fromCursor to Display Source Badges

**Ticket**: [ATT-2387](https://rainerblind.atlassian.net/browse/ATT-2387)  
**Sub-task**: [ATT-2494](https://rainerblind.atlassian.net/browse/ATT-2494) (`[Analysis]`)  
**Parent Epic**: [ATT-281](https://rainerblind.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Active Sprint**: `2026-41.1`  
**Branch**: `feature/ATT-2387`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-05  

---

## 1. Problem Statement & Motivation

During the Sprint `2026-40.15` Joint Review of [ATT-2186](https://rainerblind.atlassian.net/browse/ATT-2186) (*Add Origin Source Attribute to Workouts*), imported TCX and GPX workout sessions (such as `Runter in die Stadt #1`) were inspected on a physical Google Pixel 10 device. While the workout correctly displayed its sport (`Radeln`) and equipment (`endurance`) in Row A of `WorkoutHeader`, the origin source badge (`TCX` / `GPX`) was completely missing (captured in attachment `tcx_label_missing.png`).

Direct inspection of the SQLite database (`WorkoutSummaries.db`) confirmed that the import pipeline and database migration were fully functional:
* The database row for ID 5023 (`Runter in die Stadt #1`) contained `source = 'TCX'`.

However, in the user interface (both list and detail views), the workout rendered as if it were a live-tracked workout without any origin badge.

---

## 2. Root Cause Analysis (Forensic Investigation)

Forensic trace of git history and the data pipeline revealed:

1. **Incomplete Mapping in ATT-2186 (Commit `4cb82b75`)**:
   - In [WorkoutDataMapper.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDataMapper.kt), there were two overloads of `fromCursor`:
     - Single-item overload `fromCursor(cursor: Cursor)`: correctly extracted `source`:
       ```kotlin
       source = cursor.getColumnIndex(WorkoutSummaries.SOURCE).takeIf { it >= 0 }?.let {
           WorkoutSource.fromString(cursor.getString(it))
       } ?: WorkoutSource.TRACKED
       ```
     - Batch overload `fromCursor(cursor: Cursor, batch: BatchMetadata)`: omitted the `source` parameter entirely when constructing `WorkoutData`.
2. **Batch Decoupling in ATT-2309 (Commit `3e710f3c`)**:
   - In Sprint `2026-40.16`, `ATT-2309` introduced `RawCursorSnapshot`, `readCursorSnapshot(cursor)`, and `fromSnapshot(snapshot, batch)` to enable single-pass streaming in `WorkoutRepository.loadAllWorkouts()`.
   - Because the existing batch mapping logic already lacked `source`, `RawCursorSnapshot` was created without a `source` field, `readCursorSnapshot` omitted reading `WorkoutSummaries.SOURCE`, and `fromSnapshot` omitted passing `source` to `WorkoutData(...)`.
3. **Silent Fallback via Default Constructor Arguments**:
   - In `WorkoutData.kt`, the parameter is declared with a default value:
     ```kotlin
     val source: WorkoutSource = WorkoutSource.TRACKED
     ```
   - Consequently, the Kotlin compiler generated zero warnings or errors when `source` was omitted in `fromSnapshot`.
4. **Impact on UI Navigation**:
   - In `WorkoutRepository.loadAllWorkouts()`, all workouts are loaded via `readCursorSnapshot` + `fromSnapshot`.
   - In `WorkoutSummariesTabbedScreen.kt`, tapping a workout retrieves it from `workouts.find { it.id == id }`, passing the batch-loaded `WorkoutData` instance into `TrackOnMapScreen` and `WorkoutHeader`.
   - In `WorkoutHeader.kt`, the condition:
     ```kotlin
     if (data.source != WorkoutSource.TRACKED) { ... }
     ```
     evaluated to `false`, silently suppressing the origin badge.
5. **ASPICE Governance Violation**:
   - This defect directly motivated the formulation of **Rule 20 (Database & DTO Mapping Symmetry)** in `.agents/rules/aspice_governance.md`.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Add `val source: WorkoutSource = WorkoutSource.TRACKED` to `WorkoutDataMapper.RawCursorSnapshot`.
  2. In `WorkoutDataMapper.readCursorSnapshot(cursor: Cursor)`, safely extract `WorkoutSummaries.SOURCE` via `cursor.getColumnIndex(WorkoutSummaries.SOURCE)` and parse with `WorkoutSource.fromString(...)`, falling back to `WorkoutSource.TRACKED`.
  3. In `WorkoutDataMapper.fromSnapshot(snapshot: RawCursorSnapshot, batch: BatchMetadata)`, pass `source = snapshot.source` into `WorkoutData(...)`.
  4. Ensure `WorkoutDataMapper.fromCursor(cursor: Cursor, batch: BatchMetadata)` inherits symmetrical mapping via its delegation to `fromSnapshot(readCursorSnapshot(cursor), batch)`.
  5. Expand `WorkoutDataSourceMappingTest.kt` to explicitly test `fromCursor(cursor, batch)`, `readCursorSnapshot(cursor)`, and `fromSnapshot(snapshot, batch)` for all `WorkoutSource` variants (`TCX`, `GPX`, `FIT`, `TRACKED`, `null`, invalid).
  6. Introduce an architectural mapping symmetry test to guard against future field omissions between `Cursor`, `RawCursorSnapshot`, and `WorkoutData` (enforcing Rule 20).
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  - Modifying SQLite schema v24 or `WorkoutSummariesDatabaseManager.java` (already fully verified in ATT-2186).
  - Altering `WorkoutSource` enum definition or parsing logic.
  - Modifying badge rendering styles or layout in `WorkoutHeader.kt` (already implemented).
  - Changes to `WorkoutFilterCriteria.kt` or filter bottom sheet (handled in ATT-2303).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

* **Original Requirement ID & Target**:
  - `REQ-DAT-017` (*Workout Origin Source Provenance Attribute* - introduced in `ATT-2186`).
* **Historical Origin & Commit Trace**:
  - `ATT-2186` (Sprint `2026-40.14`, commit `4cb82b75`): Added `WorkoutSource` and single-row cursor mapping.
  - `ATT-2309` (Sprint `2026-40.16`, commit `3e710f3c`): Introduced `RawCursorSnapshot` and single-pass streaming batch mapping, inadvertently propagating the missing `source` field.
* **Root Reason for Existing Formulation**:
  - Clause 4 of `REQ-DAT-017` stated: *"WorkoutDataMapper.fromCursor SHALL extract SOURCE safely with fallback to WorkoutSource.TRACKED."*
  - The requirement did not explicitly mandate that batch overloads (`fromCursor(cursor, batch)`, `readCursorSnapshot`, and `fromSnapshot`) must map all attributes with strict symmetry to the single-item overload.
* **Preservation of Core Invariants**:
  - Existing live-tracked workouts remain tagged as `TRACKED` and display no badge.
  - Historical workouts prior to schema v24 continue to evaluate safely to `TRACKED`.
  - Batch streaming performance (`ATT-2309` zero cursor-seeking invariant) is preserved with zero overhead.

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 Symmetric Mapping in `WorkoutDataMapper.kt`

```kotlin
// 1. In RawCursorSnapshot
data class RawCursorSnapshot(
    val workoutId: Long,
    ...
    val race: Boolean,
    val source: WorkoutSource = WorkoutSource.TRACKED,
    ...
)

// 2. In readCursorSnapshot
val source = cursor.getColumnIndex(WorkoutSummaries.SOURCE).takeIf { it >= 0 }?.let {
    WorkoutSource.fromString(cursor.getString(it))
} ?: WorkoutSource.TRACKED

return RawCursorSnapshot(
    ...
    race = race,
    source = source,
    ...
)

// 3. In fromSnapshot
return WorkoutData(
    ...
    race = snapshot.race,
    source = snapshot.source,
    ...
)
```

### 5.2 Verification Strategy
1. **Unit Test Coverage (`WorkoutDataSourceMappingTest.kt`)**:
   - Assert `fromCursor(cursor)` maps `source`.
   - Assert `readCursorSnapshot(cursor)` captures `source`.
   - Assert `fromSnapshot(snapshot, batch)` preserves `source`.
   - Assert `fromCursor(cursor, batch)` yields identical `source` as `fromCursor(cursor)`.
2. **Architectural Symmetry Test**:
   - Verify that all columns mapped in `fromCursor(cursor)` are also present in `RawCursorSnapshot` and mapped in `fromSnapshot`.
3. **Full Clean-Room Regression**:
   - Run `./gradlew testDebugUnitTest` verifying 0 failures.
