# Stage 2: Requirement & Test Specification - ATT-4968: Dynamic SQLite Schema Discovery via PRAGMA table_info & Decoupled Summary Telemetry

**Ticket**: [[ATT-4968]](https://rainerblind.atlassian.net/browse/ATT-4968)  
**Sub-task**: [[ATT-5180]](https://rainerblind.atlassian.net/browse/ATT-5180) (`[Req & Test Spec]`)  
**Parent Epic**: [[ATT-115]](https://rainerblind.atlassian.net/browse/ATT-115) (*Robust, fault-tolerant workout recording*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.1`  
**Requirement Mapping**: `REQ-TRK-051` (`REQ-TRK-051.1`–`REQ-TRK-051.4`)  
**Test Spec ID**: `TST-TRK-051` (`TST-TRK-051.1`–`TST-TRK-051.4`)  
**Branch**: `feature/ATT-4968`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03  

---

## 1. Requirement Specification (REQ-TRK-051)

### 1.1 Problem Statement & Rationale
On Android $\le 14$ (API $\le 34$), `SQLiteConnection.mPreparedStatementCache` retains compiled `SELECT * ... LIMIT 1` statements across dynamic `ALTER TABLE ... ADD COLUMN` DDL. In `TrackerService.handleMissingColumns()`, querying via `samplesDb.query(..., "1")` yields a stale cursor missing columns added on earlier ticks, causing false negatives in column checks and throwing `SQLiteException: duplicate column name`. Furthermore, in `sampleAndWriteToDb()`, `if (!sampleInserted) return;` aborts live `WorkoutSummaries.db` updates when a sample write fails, freezing summary duration at 1s and distance at 0.00 km. In `TrackOnMapAftermathViewModel`, Phase 4 replaces valid summary fast tracks with single-point sample tracks. Discovering schema via uncached `PRAGMA table_info`, decoupling summary updates from sample insert errors, and gating polyline replacement ensures uninterrupted, fault-tolerant workout persistence.

### 1.2 Functional & Architectural Requirements
1. **Uncached Real-Time Schema Discovery via PRAGMA table_info (`REQ-TRK-051.1`)**: In `TrackerService.java`, `handleMissingColumns()` SHALL discover existing table columns using `PRAGMA table_info("<tableName>")` mapped into a case-insensitive `Set<String>` (with synchronized in-memory caching), bypassing `mPreparedStatementCache` staleness across all API levels.
2. **Decoupled Summary Persistence & Fault-Tolerant Recording (`REQ-TRK-051.2`)**: In `sampleAndWriteToDb()`, sample insertion failures into `WorkoutSamples.db` SHALL log rate-limited warnings and increment an atomic error counter, but SHALL NOT abort execution; the service SHALL proceed to update `WorkoutSummaries.db`.
3. **Map Polyline Fallback Protection (`REQ-TRK-051.3`)**: In `TrackOnMapAftermathViewModel.kt`, Phase 4 SHALL require at least two points (`fullTracks.any { it.path.size >= 2 }`) before replacing `_uiState.value.tracks`, retaining summary `fastTrack` when sample points $< 2$.
4. **Transaction Integrity & Stability (`REQ-TRK-051.4`)**: Service thread execution, SQLite transaction boundaries, and rate-limited logging SHALL remain 100% resilient without unhandled exceptions.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-TRK-029` (*Safe SQLite Column Escaping & Sample Insert Telemetry*) and `REQ-DAT-033` (*Fast-Track Map Telemetry*), Parent Epic `ATT-115`. Targets `TrackerService.java` and `TrackOnMapAftermathViewModel.kt`.
* **Historical Origin & Commit Trace**: `ATT-3980` introduced dynamic column addition via `handleMissingColumns()`; commit `f4650d74` added `if (!sampleInserted) return;` in `TrackerService.java`.
* **Root Reason for Existing Formulation**: `query(..., "1")` was assumed to return fresh schema; `if (!sampleInserted) return;` assumed sample write failures rendered summary updates invalid.
* **Preservation of Core Invariants**: Dynamic sensor additions, double-quoted column escaping, rate-limited logging, and summary transaction finalization remain strictly preserved.

### 1.4 Acceptance Criteria (Given-When-Then) & System Invariants
* **Criterion 1 (Dynamic Column Staleness Immunity)**: *Given* an active workout on Android <= 14 where dynamic sensors join on Tick 2+, *When* `handleMissingColumns()` runs, *Then* `PRAGMA table_info` reflects previously added columns, preventing duplicate `ALTER TABLE` crashes.
* **Criterion 2 (Decoupled Summary Update)**: *Given* an isolated sample insert failure, *When* `sampleAndWriteToDb()` runs, *Then* `WorkoutSummaries.db` updates elapsed time and distance without freezing.
* **Criterion 3 (Map Polyline Fallback)**: *Given* a workout with $< 2$ sample points but valid summary `mapPolyline`, *When* Phase 4 loads sample tracks, *Then* multi-point `fastTrack` is preserved on the map.
* **Invariants**: Zero unhandled service crashes, transaction boundary safety, rate-limited error logging, 100% clean-room test pass rate.

---

## 2. Test Specification (TST-TRK-051)

### Test Case 1: `testPragmaTableInfo_discoversDynamicColumnsAccurately` (`[TST-TRK-051.1]`)
* **Scope**: Unit Test (`TrackerServicePragmaSchemaTest.kt`)
* **Action**: Execute `PRAGMA table_info` after dynamic `ALTER TABLE ... ADD COLUMN`; assert newly added columns are returned in case-insensitive set without relying on `query(..., "1")`.

### Test Case 2: `testSampleWriteFailure_doesNotAbortSummaryUpdate` (`[TST-TRK-051.2]`)
* **Scope**: Unit Test (`TrackerServiceDecoupledSummaryTest.kt` / `TrackerServiceTest.java`)
* **Action**: Simulate sample insertion failure; assert `updateWorkoutSummary()` executes and updates active time and distance.

### Test Case 3: `testPhase4LoadTracks_retainsFastTrackWhenSamplesSparse` (`[TST-TRK-051.3]`)
* **Scope**: Unit Test (`TrackOnMapAftermathViewModelFallbackTest.kt`)
* **Action**: Feed Phase 4 sample tracks with 1 point; assert `uiState.tracks` retains initial $\ge 2$-point `fastTrack`.

---

## 3. Traceability Matrix

| Test Case | Scope | Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-TRK-051.1]` | Unit | `TrackerService.java` | `REQ-TRK-051.1` | Specified |
| `[TST-TRK-051.2]` | Unit | `TrackerService.java` | `REQ-TRK-051.2` | Specified |
| `[TST-TRK-051.3]` | Unit | `TrackOnMapAftermathViewModel.kt` | `REQ-TRK-051.3` | Specified |
| `[TST-TRK-051.4]` | Targeted Suite | `com.atrainingtracker.trainingtracker.tracker.*` | `REQ-PRO-001` | Specified |
