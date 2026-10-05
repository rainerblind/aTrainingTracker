# Stage 2: Requirement & Test Specification - ATT-2387: Map Origin Source Attribute in WorkoutDataMapper Batch fromCursor to Display Source Badges

**Ticket**: [ATT-2387](https://rainerblind.atlassian.net/browse/ATT-2387)  
**Sub-task**: [ATT-2495](https://rainerblind.atlassian.net/browse/ATT-2495) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-281](https://rainerblind.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Active Sprint**: `2026-41.1`  
**Requirement Mapping**: `REQ-DAT-021` (*Workout Origin Source Batch Mapping Symmetry and Cursor Snapshot Propagation*)  
**Test Spec ID**: `TST-DAT-016` (*Workout Origin Source Batch Mapping Symmetry Verification*)  
**Branch**: `feature/ATT-2387`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-05  

---

## 1. Requirement Specification (`REQ-DAT-021`)

### 1.1 Problem Statement & Rationale
During on-device testing of imported TCX/GPX workouts (e.g. `Runter in die Stadt #1`), the header origin badge (`TCX` / `GPX`) was completely missing despite `WorkoutSummaries.SOURCE = 'TCX'` persisting correctly in SQLite. In `WorkoutDataMapper.kt`, the single-row overload `fromCursor(cursor)` mapped `source`, but the batch-loading pipeline (`readCursorSnapshot(cursor)` and `fromSnapshot(snapshot, batch)`) introduced in `ATT-2309` omitted the `source` attribute entirely. Because `WorkoutData.source` defaults to `WorkoutSource.TRACKED`, all workouts loaded in lists and detail screens silently defaulted to `TRACKED`, causing `WorkoutHeader.kt` to suppress the origin badge. In accordance with ASPICE **Rule 20 (Database & DTO Mapping Symmetry)**, all mapping pathways MUST map entity and database fields symmetrically.

### 1.2 Functional & Architectural Requirements
The system SHALL map the `WorkoutSummaries.SOURCE` attribute symmetrically across all cursor mapping pathways in `WorkoutDataMapper.kt` (ATT-2387):
1. **Raw Cursor Snapshot Provenance Support (`WorkoutDataMapper.RawCursorSnapshot`)**:
   - `RawCursorSnapshot` SHALL include property `val source: WorkoutSource = WorkoutSource.TRACKED`.
2. **Deterministic Cursor Extraction (`WorkoutDataMapper.readCursorSnapshot`)**:
   - In `readCursorSnapshot(cursor: Cursor)`, the system SHALL extract `WorkoutSummaries.SOURCE` via:
     ```kotlin
     val source = cursor.getColumnIndex(WorkoutSummaries.SOURCE).takeIf { it >= 0 }?.let {
         WorkoutSource.fromString(cursor.getString(it))
     } ?: WorkoutSource.TRACKED
     ```
   - The extracted `source` SHALL be passed to `RawCursorSnapshot(..., source = source)`.
3. **Symmetric Batch & Snapshot Enrichment (`WorkoutDataMapper.fromSnapshot` & `fromCursor`)**:
   - In `fromSnapshot(snapshot: RawCursorSnapshot, batch: BatchMetadata)`, the system SHALL explicitly pass `source = snapshot.source` when instantiating `WorkoutData`.
   - `fromCursor(cursor: Cursor, batch: BatchMetadata)` SHALL inherit identical mapping via its delegation to `fromSnapshot(readCursorSnapshot(cursor), batch)`.
4. **Architectural Mapping Symmetry Enforcement (Rule 20)**:
   - An architectural contract test SHALL verify that all cursor column properties mapped in `fromCursor(cursor)` are also declared in `RawCursorSnapshot`, extracted in `readCursorSnapshot`, and mapped into `WorkoutData` in `fromSnapshot`.
5. **UI Header Badge Presentation for Batch-Loaded Workouts**:
   - When workouts are batch-loaded via `loadAllWorkouts()`, `PeriodsRepository`, or `WorkoutClusterRepository`, workouts originating from `TCX`, `GPX`, or `FIT` SHALL preserve `data.source != WorkoutSource.TRACKED` and display their origin source badge in `WorkoutHeader` and workout list cards.

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Refines Clause 4 of `REQ-DAT-017` (*Workout Origin Source Provenance Attribute*) in `docs/requirements.md`, targeting `WorkoutDataMapper.kt` and `WorkoutRepository.kt`.
2. *Historical Origin & Commit Trace*:
   - `ATT-2186` (Sprint `2026-40.14`, commit `4cb82b75`): Introduced `REQ-DAT-017`, but only mapped `source` in single-item `fromCursor(cursor)`.
   - `ATT-2309` (Sprint `2026-40.16`, commit `3e710f3c`): Introduced `RawCursorSnapshot` and single-pass streaming batch mapping, inadvertently omitting the `source` field.
3. *Root Reason for Existing Formulation*:
   - Clause 4 of `REQ-DAT-017` stated: *"WorkoutDataMapper.fromCursor SHALL extract SOURCE safely with fallback to WorkoutSource.TRACKED."* It did not explicitly mandate that batch overloads (`fromCursor(cursor, batch)`, `readCursorSnapshot`, and `fromSnapshot`) must map all attributes symmetrically.
4. *Preservation of Core Invariants*:
   - Existing live-tracked workouts continue to have `source = TRACKED` and omit the badge.
   - Historical workouts prior to schema v24 evaluate safely to `TRACKED`.
   - Batch streaming performance (`ATT-2309` zero cursor-seeking invariant) is preserved with zero overhead.

### 1.4 Acceptance Criteria (Given-When-Then)
* **AC-1 (Batch Ingestion Symmetrical Mapping)**:
  * *Given* a SQLite cursor row containing `source = 'TCX'` (or `'GPX'`, `'FIT'`),
  * *When* mapped via `WorkoutDataMapper.fromCursor(cursor, batch)`,
  * *Then* the resulting `WorkoutData.source` SHALL equal `WorkoutSource.TCX` (or `GPX`, `FIT`), and `WorkoutData.headerData.source` SHALL equal `WorkoutSource.TCX`.
* **AC-2 (Raw Cursor Snapshot Extraction)**:
  * *Given* a SQLite cursor row containing `source = 'TCX'`,
  * *When* `readCursorSnapshot(cursor)` executes,
  * *Then* the returned `RawCursorSnapshot.source` SHALL equal `WorkoutSource.TCX`.
* **AC-3 (Snapshot to WorkoutData Enrichment)**:
  * *Given* a `RawCursorSnapshot` with `source = WorkoutSource.TCX`,
  * *When* `fromSnapshot(snapshot, batch)` executes,
  * *Then* the resulting `WorkoutData.source` SHALL equal `WorkoutSource.TCX`.
* **AC-4 (Default Fallback on Missing or Corrupted Column)**:
  * *Given* a cursor row where `source` is `null`, empty, or unrecognized (`'CORRUPTED'`),
  * *When* mapped via `readCursorSnapshot`, `fromSnapshot`, or `fromCursor(cursor, batch)`,
  * *Then* `WorkoutData.source` SHALL safely evaluate to `WorkoutSource.TRACKED`.
* **AC-5 (UI Badge Display in Workout Header)**:
  * *Given* a batch-loaded workout with `source = WorkoutSource.TCX`,
  * *When* rendered in `WorkoutHeader`,
  * *Then* the header displays the origin badge (`TCX`) in Row A.

---

## 2. Test Specification (`TST-DAT-016`)

### Test Case 1: `testWorkoutDataMapper_batchAndSnapshot_mapsSourceSymmetrically` (`TST-DAT-016.1`)
* **Scope**: Unit & Domain Mapping Test (`WorkoutDataSourceMappingTest.kt`)
* **Goal**:
  1. Verify `readCursorSnapshot(cursor)` extracts valid strings (`TCX`, `GPX`, `FIT`, `TRACKED`) into `RawCursorSnapshot.source`.
  2. Verify `fromSnapshot(snapshot, batch)` maps `snapshot.source` to `WorkoutData.source` and `WorkoutData.headerData.source`.
  3. Verify `fromCursor(cursor, batch)` yields identical `WorkoutSource` values as `fromCursor(cursor)`.
  4. Verify fallback to `WorkoutSource.TRACKED` for null, empty, or unknown strings across both batch and snapshot methods.
* **Expected Result**: PASS.

### Test Case 2: `testWorkoutDataMapper_cursorMappingSymmetryContract` (`TST-DAT-016.2`)
* **Scope**: Architectural Contract Test (`WorkoutDataMappingSymmetryTest.kt`)
* **Goal**:
  1. Enforce ASPICE Rule 20.
  2. Verify that every cursor column mapped in `fromCursor(cursor)` (including `source`, `race`, `clusterId`, `finished`, etc.) has a matching field in `RawCursorSnapshot`.
  3. Verify that every field in `RawCursorSnapshot` is forwarded into `WorkoutData` within `fromSnapshot`.
* **Expected Result**: PASS with 0 asymmetric fields.

### Test Case 3: Clean-Room Full Suite Regression (`TST-DAT-016.3`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Full test suite executes with 0 failures across all modules.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Method | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-DAT-016.1` | Unit | `WorkoutDataSourceMappingTest.kt` | `REQ-DAT-021` | Specified |
| `TST-DAT-016.2` | Architectural Contract | `WorkoutDataMappingSymmetryTest.kt` | `REQ-DAT-021` | Specified |
| `TST-DAT-016.3` | Regression | Full `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
