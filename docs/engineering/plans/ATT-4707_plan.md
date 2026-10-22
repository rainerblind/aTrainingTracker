# Stage 3: Implementation Plan - ATT-4707: Quote SQLite projection column identifiers in WorkoutRepository

**Ticket**: [ATT-4707](https://rainerblind.atlassian.net/browse/ATT-4707) | **Sub-task**: Pending Submission (`[Impl-Plan]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.40` | **Active Sprint**: Backlog | **Branch**: `feature/ATT-4707`  
**Requirement Mapping**: `REQ-DAT-033` (`REQ-DAT-033.4a`, `REQ-DAT-033.4b`) | **Test Mapping**: `TST-DAT-028` (`TST-DAT-028.5`, `TST-DAT-028.6`, `TST-DAT-028.7`)  
**Author**: AI Agent 1 (Implementer - `subagent_pro`) | **Date**: 2026-10-22  

---

## 1. Problem Description & Background
When loading workout details in Aftermath, `TrackType` defines source-qualified location columns containing spaces and parentheses (`LATITUDE (GPS)`, `LATITUDE (Google Fused)`, `LATITUDE (Network)`). In `ATT-4003` (`6073cf43`), `WorkoutRepository.kt` passed unquoted column arrays to `db.query(...)`. Android's `SQLiteQueryBuilder.appendColumns` appends raw strings, producing invalid SQL statements (`SELECT LATITUDE (Google Fused) ...`) that crash with `SQLiteException: near "Fused": syntax error`. Wrapping projection identifiers in standard SQL double quotes (`"\"${col.replace("\"", "\"\"")}\""`) ensures valid SQL syntax while returning unquoted column names in cursor metadata.

---

## 2. Proposed Architectural Changes (SWE.2)

| Layer / Class | Architectural Responsibility & Modification |
| :--- | :--- |
| **Repository**<br>`WorkoutRepository.kt` | Add canonical `internal fun quoteSqlIdentifier(col: String): String = "\"${col.replace("\"", "\"\"")}\""` (in companion or class). Apply `.map { quoteSqlIdentifier(it) }.toTypedArray()` to projection arrays across all 4 `WorkoutSamples.db` query sites (`getWorkoutTrackPoints` L390, `loadWorkoutDetailTelemetry` L776, `getHeartRateZoneDistribution` L592, `getPowerZoneDistribution` L665). |
| **Existing Telemetry Tests**<br>`WorkoutRepositoryTelemetryTest.kt` | Update `capturedProjection` assertions in `testGetWorkoutTrackPoints_usesExplicitColumnProjection` to assert quoted column identifiers (`quoteSqlIdentifier(TrackType.BEST.latitudeColumn)`). |
| **New Real SQLite Tests**<br>`WorkoutRepositoryProjectionTest.kt` | Create JVM unit test suite running against real in-memory SQLite (`SQLiteDatabase.create(null)`). Create tables with all 4 `TrackType` column pairs (`BEST`, `GPS`, `FUSED`, `NETWORK`) and assert `loadWorkoutDetailTelemetry` and `getWorkoutTrackPoints` execute cleanly without `SQLiteException`. |

### UI Consistency (Rule 23)
* **Scope**: Pure data layer and SQLite projection fix. Zero UI, Compose, or Layout modifications.

---

## 3. Step-by-Step Implementation Sequence (Stage 4)

### Step 1: Add Quoting Helper & Quote Projections in `WorkoutRepository.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepository.kt`
* Add `internal fun quoteSqlIdentifier(col: String): String = "\"${col.replace("\"", "\"\"")}\""`.
* In `getWorkoutTrackPoints`, `loadWorkoutDetailTelemetry`, `getHeartRateZoneDistribution`, and `getPowerZoneDistribution`: map projection array items through `quoteSqlIdentifier` before passing to `db.query(tableName, projection, ...)`.

### Step 2: Update Existing Projection Assertions in `WorkoutRepositoryTelemetryTest.kt`
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepositoryTelemetryTest.kt`
* Update lines 310–311 to verify `capturedProjection.captured.contains(WorkoutRepository.quoteSqlIdentifier(TrackType.BEST.latitudeColumn))` and `longitudeColumn`.

### Step 3: Implement Real SQLite Tests in `WorkoutRepositoryProjectionTest.kt`
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepositoryProjectionTest.kt`
* Set up in-memory `SQLiteDatabase` with a samples table containing source-qualified location columns (`LATITUDE (GPS)`, `LONGITUDE (GPS)`, `LATITUDE (Google Fused)`, `LONGITUDE (Google Fused)`, `LATITUDE (Network)`, `LONGITUDE (Network)`).
* Test `WorkoutRepository.loadWorkoutDetailTelemetry` and `WorkoutRepository.getWorkoutTrackPoints` for all `TrackType` entries (`BEST`, `GPS`, `FUSED`, `NETWORK`), verifying zero `SQLiteException` and full coordinate extraction.

### Step 4: Verification & Regression Testing (`TST-DAT-028`)
* Run targeted tests: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository*"`
* Run full package tests: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*"`

---

## 4. System Invariants & Rollback Plan
* **Invariants**: Cursor column name resolution (`cursor.getColumnIndex(...)`) remains 100% functional with unquoted column names; zero database schema alterations or migrations; clean-room 100% test pass rate.
* **Rollback Plan**: Changes are completely isolated to branch `feature/ATT-4707`.
