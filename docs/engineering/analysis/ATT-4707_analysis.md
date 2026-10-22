# Stage 1 Analysis: ATT-4707 - Quote SQLite projection column identifiers in WorkoutRepository

**Ticket**: [ATT-4707](https://rainerblind.atlassian.net/browse/ATT-4707)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.40` | **Active Sprint**: Backlog | **Branch**: `feature/ATT-4707`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-22  

---

## 1. Problem Statement & Motivation
Opening the workout detail view (`TrackOnMapAftermathViewModel`) for any workout whose `WorkoutSamples.db` table contains source-qualified location columns (`LATITUDE (GPS)`, `LATITUDE (Google Fused)`, `LATITUDE (Network)`) crashes the app with `android.database.sqlite.SQLiteException: near "Fused": syntax error` or `no such column: GPS`.
`loadWorkoutDetailTelemetry` first fails silently (swallowed in `TrackOnMapAftermathViewModel.kt:164`), falling back to `getWorkoutTrackPoints(workoutId, type)` on line 171, which throws uncaught `SQLiteException` and crashes the application.

---

## 2. Root Cause Analysis (Forensic Investigation)
* **Source-Qualified Column Identifiers**: In `MapModels.kt:40-69`, `TrackType` defines source-specific location column names containing spaces and parentheses (`LATITUDE (GPS)`, `LATITUDE (Google Fused)`, `LATITUDE (Network)`).
* **Unquoted Projection Injection**: In `ATT-4003` (commit `6073cf43`), `WorkoutRepository.kt` replaced full-table `SELECT *` scans with explicit column projections passed to `db.query(tableName, projection, ...)`.
* **SQLite Parsing Failure**: Android's `SQLiteQueryBuilder.appendColumns` concatenates projection elements with raw comma-space separation without identifier quotes (`SELECT LATITUDE (Google Fused), ...`). SQLite parses `LATITUDE (Google Fused)` as invalid syntax (`near "Fused": syntax error`) and `LATITUDE (GPS)` as an invalid function invocation `LATITUDE(GPS)`.
* **Test Isolation Gap**: `WorkoutRepositoryTelemetryTest.kt` mocked `SQLiteDatabase.query` via MockK, bypassing actual SQLite query compilation and masking SQL syntax errors.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Quote all projection column identifiers in `WorkoutRepository.kt` across all 4 projected query sites (`getWorkoutTrackPoints` L392, `loadWorkoutDetailTelemetry` L781, `getHeartRateZoneDistribution` L595, `getPowerZoneDistribution` L668).
  * Add real SQLite unit tests in `WorkoutRepositoryProjectionTest.kt` verifying `loadWorkoutDetailTelemetry` and `getWorkoutTrackPoints` execute without `SQLiteException` across all `TrackType` variants (`BEST`, `GPS`, `FUSED`, `NETWORK`).
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No changes to `WorkoutSamples.db` schema or `TrackerService.java`.
  * No alterations to `MapModels.kt` or `TrackType` column naming contracts.
  * No modifications to `TrackOnMapAftermathViewModel` simplification or rendering pipeline.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-DAT-033` (`REQ-DAT-033.4` Consolidated Projected Samples DB Telemetry Loading) and `TST-DAT-028` (`TST-DAT-028.3`), targeting `WorkoutRepository.kt` and `TrackOnMapAftermathViewModel.kt`.
* **Historical Origin & Commit Trace**: Introduced in `ATT-4003` (commit `6073cf43`, Sprint `2026-43.7`).
* **Root Reason for Existing Formulation**: `ATT-4003` replaced unprojected `SELECT *` queries with explicit column projections to eliminate redundant full-table re-scans in Aftermath, but omitted SQL identifier quotes on column names.
* **Preservation of Core Invariants**: In SQLite, `SELECT "LATITUDE (Google Fused)"` produces a cursor whose column name matches the unquoted identifier `LATITUDE (Google Fused)`, preserving `cursor.getColumnIndex(...)` contracts, telemetry accuracy, and zone distribution computation.

---

## 5. Architectural Strategy & High-Level Solution
* **Projection Quoting Helper (`WorkoutRepository.kt`)**:
  * Apply SQL identifier double-quoting (`"\"${col.replace("\"", "\"\"")}\""` or `TrackerService.quoteSqlColumnIdentifier(col)`) to projection arrays across `getWorkoutTrackPoints`, `loadWorkoutDetailTelemetry`, `getHeartRateZoneDistribution`, and `getPowerZoneDistribution`.
* **Real SQLite Regression Suite (`WorkoutRepositoryProjectionTest.kt`)**:
  * Create in-memory SQLite tables with source-qualified location columns (`LATITUDE (GPS)`, `LATITUDE (Google Fused)`, `LATITUDE (Network)`) and assert clean query execution without `SQLiteException`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Telemetry accuracy: Unquoted cursor column names match `cursor.getColumnIndex(latName)` lookups.
  2. Schema immutability: Zero SQLite schema migrations or alterations.
  3. Clean-room test suite pass rate: 100% pass rate under `./gradlew testDebugUnitTest`.
* **Risk Rating**: **LOW** — Scoped exclusively to double-quoting projection column identifiers in `WorkoutRepository.kt` queries.
