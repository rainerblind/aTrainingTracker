# Stage 2: Requirement & Test Specification - ATT-4707: Quote SQLite projection column identifiers in WorkoutRepository

**Ticket**: [ATT-4707](https://rainerblind.atlassian.net/browse/ATT-4707)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.40` | **Active Sprint**: Backlog | **Branch**: `feature/ATT-4707`  
**Requirement Mapping**: `REQ-DAT-033` (*High-Performance Batch Workout Mapping, Non-Blocking Cluster Equipment Resolution & Consolidated Samples DB Telemetry Loading*)  
**Test Spec ID**: `TST-DAT-028`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-22  

---

## 1. Requirement Specification (`REQ-DAT-033`)

### 1.1 Problem Statement & Rationale
In `ATT-4003` (commit `6073cf43`), `WorkoutRepository.kt` introduced explicit column projections to replace full-table scans. However, `TrackType` defines source-specific location columns containing spaces and parentheses (`LATITUDE (GPS)`, `LATITUDE (Google Fused)`, `LATITUDE (Network)`). Passing unquoted column strings to Android's `db.query(...)` results in SQLite compiling unquoted expressions (`SELECT LATITUDE (Google Fused)`), triggering fatal `SQLiteException: near "Fused": syntax error` crashes when opening Aftermath workout details. Double-quoting projection column identifiers (`"\"${col.replace("\"", "\"\"")}\""`) ensures valid SQL syntax while returning unchanged unquoted column names in cursor metadata.

### 1.2 Functional & Architectural Requirements
1. **Projection Column Quoting (`REQ-DAT-033.4a`)**: All column projection arrays passed to SQLite `db.query(tableName, projection, ...)` in `WorkoutRepository.kt` (`getWorkoutTrackPoints`, `loadWorkoutDetailTelemetry`, `getHeartRateZoneDistribution`, `getPowerZoneDistribution`) SHALL quote each column identifier using double quotes (`"\"${col.replace("\"", "\"\"")}\""`).
2. **Cursor Column Name Parity (`REQ-DAT-033.4b`)**: In SQLite, double-quoted column identifiers produce result columns whose names match the unquoted identifier string, guaranteeing that `cursor.getColumnIndex(trackType.latitudeColumn)` and `cursor.getColumnIndex(SensorType.HR.name)` resolve accurately without modifying downstream mapping code.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-DAT-033` (`REQ-DAT-033.4`), targeting `WorkoutRepository.kt` and `WorkoutRepositoryProjectionTest.kt`.
* **Historical Origin & Commit Trace**: `ATT-4003` (commit `6073cf43`, Sprint `2026-43.7`), which introduced explicit column projections in `WorkoutRepository.kt` to eliminate redundant table scans.
* **Root Reason for Existing Formulation**: `ATT-4003` passed column identifiers directly as an unquoted `Array<String>` into `db.query` assuming column names conformed to standard SQL identifiers. This assumption broke for `TrackType.GPS`, `TrackType.FUSED`, and `TrackType.NETWORK`.
* **Preservation of Core Invariants**: Consolidated single-pass table scans, telemetry coordinates, elevation calculations, power/HR zone distributions, and 100% clean-room test pass rate are strictly preserved.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Consolidated Telemetry Loading with Source Columns)**: *Given* a workout sample table containing `LATITUDE (Google Fused)`, `LONGITUDE (Google Fused)`, `LATITUDE (GPS)`, and `LATITUDE (Network)`, *When* `WorkoutRepository.loadWorkoutDetailTelemetry` executes, *Then* the query succeeds without `SQLiteException` and returns populated `PathPoint` lists for all available `TrackType` entries.
* **Criterion 2 (Track Points Loading with Source Columns)**: *Given* a workout sample table with source-qualified location columns, *When* `WorkoutRepository.getWorkoutTrackPoints(workoutId, trackType)` executes for each `TrackType` (`BEST`, `GPS`, `FUSED`, `NETWORK`), *Then* each query succeeds without `SQLiteException` and extracts valid coordinates.

---

## 2. Test Specification (`TST-DAT-028`)

### Test Cases
* `TST-DAT-028.5` (**Real SQLite Source-Qualified Location Projections**): Verify in `WorkoutRepositoryProjectionTest` that `loadWorkoutDetailTelemetry` and `getWorkoutTrackPoints` execute against an in-memory SQLite table containing `LATITUDE (Google Fused)`, `LATITUDE (GPS)`, and `LATITUDE (Network)` columns without `SQLiteException`.
* `TST-DAT-028.6` (**Zone Distribution Quoted Projections**): Verify that `getHeartRateZoneDistribution` and `getPowerZoneDistribution` execute with quoted projections and correctly compute distributions.
* `TST-DAT-028.7` (**Clean-Room Regression Suite Execution**): Run targeted tests in `com.atrainingtracker.trainingtracker.ui.aftermath.*` and `./gradlew testDebugUnitTest` verifying 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Class Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-DAT-028.5` | Integration / Unit | `WorkoutRepository.kt`, `WorkoutRepositoryProjectionTest.kt` | `REQ-DAT-033.4a`, `REQ-DAT-033.4b` | Proposed |
| `TST-DAT-028.6` | Integration / Unit | `WorkoutRepository.kt`, `WorkoutRepositoryProjectionTest.kt` | `REQ-DAT-033.4a` | Proposed |
| `TST-DAT-028.7` | Regression | `WorkoutRepositoryTelemetryTest.kt`, `WorkoutRepositoryCropTest.kt` | `REQ-DAT-033.5` | Proposed |
