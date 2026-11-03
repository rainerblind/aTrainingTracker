# Stage 1 Analysis: ATT-4968 - Not Tracked Workout: PreparedStatementCache Staleness & Summary Freeze

**Ticket**: [ATT-4968](https://rainerblind.atlassian.net/browse/ATT-4968)  
**Sub-task**: [ATT-5179](https://rainerblind.atlassian.net/browse/ATT-5179) (`[Analysis]`)  
**Parent Epic**: [ATT-115](https://rainerblind.atlassian.net/browse/ATT-115) (*Robust, fault-tolerant workout recording*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.1`  
**Branch**: `feature/ATT-4968`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03  

---

## 1. Problem Statement & Motivation
* During workouts on Android $\le 14$ (API $\le 34$), active tracking appears normal live, but upon completion:
  1. `WorkoutSamples.db` freezes at 1 (or few) rows, resulting in single-point TCX/GPX/CSV exports.
  2. In V4.9.40+, `WorkoutSummaries.db` freezes at 1 second active time and 0.00 km distance on the workout card.
  3. In `TrackOnMapAftermathFragment`, the initial full GPS map route (`fastTrack` from summary `mapPolyline`) is overwritten and disappears once Phase 4 loads the 1-point sample track.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Primary Cause (Android SQLite `mPreparedStatementCache` Staleness)**:
  * In `TrackerService.handleMissingColumns()` (`TrackerService.java:2504`), columns are checked using `samplesDb.query(tableName, null, null, null, null, null, null, "1")`.
  * On Android $\le 14$ (API $\le 34$), `SQLiteConnection.mPreparedStatementCache` does NOT invalidate compiled `SELECT * ... LIMIT 1` statements upon `ALTER TABLE ... ADD COLUMN` DDL.
  * When a dynamic sensor value (e.g. `SLOPE`, `PACE`, or delayed GPS fix) joins on Tick 2+, `samplesDb.query()` returns the stale Tick 1 cursor (missing the previously added columns). `hasColumnInCursor()` returns `false` for already existing columns, triggering `ALTER TABLE ... ADD COLUMN` and throwing `SQLiteException: duplicate column name: ...`.
* **Secondary Cause (`WorkoutSummaries.db` Freeze in V4.9.40+)**:
  * Line 2281 has `if (!sampleInserted) return;`, aborting the `WorkoutSummaries.db` live update whenever a sample insert fails, freezing duration and distance at Tick 1.
* **Tertiary Cause (Map Track Disappearance in `TrackOnMapAftermathViewModel`)**:
  * Phase 4 replaces valid multi-point `fastTrack` with `fullTracks` even when `fullTracks` contains fewer than 2 points (`size < 2`), rendering a blank polyline.
* **Independent Hypothesis Verification (ATT-3534)**:
  * Verified: `PRAGMA table_info` is statement type `STATEMENT_PRAGMA`, never cached in `mPreparedStatementCache`, and returns real-time schema.
  * Verified: Decoupling summary updates prevents card freeze even during sample SQLite errors.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Inspect existing columns via `PRAGMA table_info` in `handleMissingColumns()` with case-insensitive column caching.
  2. Decouple `WorkoutSummaries.db` live updates from `WorkoutSamples.db` row insert failures in `sampleAndWriteToDb()`.
  3. Prevent `TrackOnMapAftermathViewModel` Phase 4 from overwriting a valid $\ge 2$-point `fastTrack` when sample points $< 2$.
* **Out-of-Scope Non-Goals**:
  1. Changing sensor acquisition pipelines, GPS filtering, or `LiveWorkoutSession` in-memory accumulation.
  2. Modifying table creation DDL or `SensorType` enum serialization.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: Amends `REQ-TRK-029` (*Safe SQLite Column Escaping & Sample Insert Telemetry*) and `REQ-DAT-033` (*Fast-Track Map Telemetry*).
* **Historical Origin & Commit Trace**: `ATT-3980` introduced `handleMissingColumns()`; `ATT-3985` added column escaping; `f4650d74` added `if (!sampleInserted) return;`.
* **Root Reason for Existing Formulation**: `query(..., "1")` was assumed to be lightweight and reactive to DDL; `if (!sampleInserted) return;` intended to avoid orphan summary updates.
* **Preservation of Core Invariants**: Transaction boundaries, rate-limited logging, and dynamic sensor registration remain intact.

---

## 5. Architectural Strategy & High-Level Solution
* **`TrackerService.java`**:
  * Replace `samplesDb.query(..., "1")` with `PRAGMA table_info("<tableName>")` via a helper method `getTableColumnNames(SQLiteDatabase, String)` with an in-memory set cache per active table.
  * In `sampleAndWriteToDb()`, log sample insertion failure and increment `mSampleInsertFailureCount`, but proceed to update `summariesDb` with current live metrics.
* **`TrackOnMapAftermathViewModel.kt`**:
  * Check `if (fullTracks.any { it.path.size >= 2 })` before replacing `_uiState.value.tracks`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero unhandled service crashes; foreground service thread stability preserved.
  2. 1 Hz sample persistence without dropped rows across mid-workout sensor additions.
  3. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **MEDIUM** — Touches `TrackerService.sampleAndWriteToDb` transaction loop; verified via Robolectric API 34 tests.
