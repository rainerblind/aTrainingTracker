# Stage 3: Implementation Plan - ATT-4968: Dynamic SQLite Schema Discovery & Decoupled Summary Telemetry

**Ticket**: [ATT-4968](https://rainerblind.atlassian.net/browse/ATT-4968)  
**Sub-task**: [ATT-5181](https://rainerblind.atlassian.net/browse/ATT-5181) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-115](https://rainerblind.atlassian.net/browse/ATT-115) (*Robust, fault-tolerant workout recording*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.1`  
**Requirement Mapping**: `REQ-TRK-051` (`REQ-TRK-051.1`–`REQ-TRK-051.4`)  
**Test Mapping**: `TST-TRK-051` (`TST-TRK-051.1`–`TST-TRK-051.4`)  
**Branch**: `feature/ATT-4968`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03  

---

## 1. Problem Description & Background
On Android $\le 14$ (API $\le 34$), `SQLiteConnection.mPreparedStatementCache` caches compiled `SELECT * ... LIMIT 1` statements across `ALTER TABLE ... ADD COLUMN` DDL. In `TrackerService.handleMissingColumns()`, querying via `samplesDb.query(..., "1")` returns a stale cursor missing columns added on Tick 1. When a new sensor value (e.g. `SLOPE`, `PACE`) joins on Tick 2+, `hasColumnInCursor()` returns false for existing columns, causing duplicate `ALTER TABLE` crashes (`duplicate column name`). Furthermore, `if (!sampleInserted) return;` aborts `WorkoutSummaries.db` updates, freezing duration and distance at 1s/0.00 km, while `TrackOnMapAftermathViewModel` Phase 4 replaces valid summary `fastTrack` with 1-point sample tracks.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-TRK-051` (*Fault-Tolerant Sample Schema Discovery & Decoupled Summary Recording*)
  * `REQ-TRK-051.1`: Uncached real-time schema discovery via `PRAGMA table_info`.
  * `REQ-TRK-051.2`: Decoupled `WorkoutSummaries.db` updates on sample write failure.
  * `REQ-TRK-051.3`: Map polyline fallback protection when sample points $< 2$.
  * `REQ-TRK-051.4`: Transaction integrity and rate-limited logging.
* **Test Mapping**: `TST-TRK-051` (`TST-TRK-051.1`–`TST-TRK-051.4`) across `TrackerServicePragmaSchemaTest.kt`, `TrackerServiceDecoupledSummaryTest.kt`, and `TrackOnMapAftermathViewModelFallbackTest.kt`.

---

## 3. System Invariants & Preserved Behavior
1. **Transaction Safety**: `samplesDb` transaction commits only if `sampleInserted == true`; `summariesDb` transaction commits independently.
2. **Double-Quoting Invariant**: Device-qualified column names with apostrophes and quotes (`REQ-TRK-029`) remain safely escaped.
3. **No Uncaught Service Crashes**: Database exceptions are caught, logged with rate-limiting, and never crash `TrackerService`.
4. **Fast-Track Preservation**: Multi-point summary polyline is never replaced with $< 2$ sample points (`REQ-DAT-033`).

---

## 4. Proposed Architectural Changes

### Component 1: Dynamic Schema Discovery (`TrackerService.java`)
* Replace `samplesDb.query(tableName, null, ..., "1")` in `handleMissingColumns()` with `getTableColumnNames(SQLiteDatabase, String)` querying `PRAGMA table_info("<tableName>")`.
* Build a case-insensitive `Set<String>` of column names, bypassing `mPreparedStatementCache` on Android $\le 14$.
* Update `existingColumns` in-memory as missing columns are added via `ALTER TABLE`.

### Component 2: Decoupled Summary Updates (`TrackerService.java`)
* In `sampleAndWriteToDb()`, if `!sampleInserted`, increment failure count and log rate-limited warning, but proceed to write Extrema and execute `summariesDb.update(...)`.
* Guard `samplesDb.setTransactionSuccessful()` with `if (sampleInserted)`.

### Component 3: Polyline Fallback Protection (`TrackOnMapAftermathViewModel.kt`)
* In Phase 4, guard `_uiState.value = _uiState.value.copy(tracks = fullTracks)`: only overwrite if `fullTracks.any { it.path.size >= 2 }` or `_uiState.value.tracks.isEmpty()`.

### UI Consistency (Rule 23)
* No UI visual changes; preserves existing map polyline and workout card telemetry.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: `PRAGMA table_info` Schema Inspection (`TrackerService.java`)
* Add `@VisibleForTesting static Set<String> getTableColumnNames(SQLiteDatabase, String)` and refactor `handleMissingColumns()`.

### Step 2: Decouple Summary Update from Sample Write Failure (`TrackerService.java`)
* In `sampleAndWriteToDb()`, remove early `return;` on `!sampleInserted` and conditionally commit `samplesDb`.

### Step 3: Guard Map Track Replacement (`TrackOnMapAftermathViewModel.kt`)
* In Phase 4, gate track update on `fullTracks.any { it.path.size >= 2 }`.

### Step 4: Unit & Contract Tests
* Files: `TrackerServicePragmaSchemaTest.kt`, `TrackerServiceDecoupledSummaryTest.kt`, `TrackOnMapAftermathViewModelFallbackTest.kt`.
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.tracker.*" --tests "com.atrainingtracker.trainingtracker.ui.map.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Execute targeted unit tests, `verify_requirement_governance.py`, and inspect transaction logs.
* **Rollback**: Branch isolation (`feature/ATT-4968`); clean revert if needed.
