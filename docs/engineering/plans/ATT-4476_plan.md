# Stage 3: Implementation Plan - ATT-4476: Make PeriodSummaries.db Self-Contained and Eliminate N+1 Queries

**Ticket**: [ATT-4476](https://rainerblind.atlassian.net/browse/ATT-4476) | **Epic**: [ATT-4474](https://rainerblind.atlassian.net/browse/ATT-4474) | **Release**: `V4.9.40` | **Sprint**: `sprint/2026-43.9`  
**Branch**: `feature/ATT-4476` | **Requirement**: `REQ-PER-020` | **Test Spec**: `TST-PER-026` | **Author**: AI Agent 1 | **Date**: 2026-10-23  

---

## 1. Problem Description & Background
`PeriodSummariesDatabaseManager.getPeriodsByType()` triggers thousands of N+1 SQLite queries on startup by temporarily mutating `stats.longestWorkout` with synthetic ID pointers, then querying per-row in a loop. Additionally, `PeriodSportStats` omits `longest_workout_name`, `longest_distance`, and `longest_ascent`, forcing in-memory joins or fallback queries to `WorkoutSummaries.db`. Furthermore, `checkIntegrityAndSync()` only checks `>`, missing deletions, while cold start runs 66 redundant file existence checks.

---

## 2. Traceability & Invariants
* **Traceability**: `REQ-PER-020` (`REQ-PER-020.1`..`REQ-PER-020.5`) $\leftrightarrow$ `TST-PER-026` (`TST-PER-026.1`..`TST-PER-026.6`).
* **Invariants**: Coding Standards §2.1–§2.3 (`onCreate`/`onUpgrade` parity with safe defaults `DEFAULT ''`, `DEFAULT 0.0`, `DEFAULT 0`); non-blocking `Dispatchers.IO` thread affinity; parent Human Decision Gate strictly enforced.

---

## 3. Proposed Architectural Changes
* **Database Schema v27 (`PeriodSummariesDatabaseManager.kt`)**:
  - Add `COLUMN_LONGEST_WORKOUT_NAME TEXT NOT NULL DEFAULT ''`, `COLUMN_LONGEST_DISTANCE REAL NOT NULL DEFAULT 0.0`, and `COLUMN_LONGEST_ASCENT INTEGER NOT NULL DEFAULT 0` to `PeriodSportStats`.
  - Bump `DB_VERSION = 27` with incremental `ALTER TABLE` migration in `onUpgrade()`.
  - Map `LongestWorkout` completely in `createSportStatsContentValues()` and `mapCursorToSportStats()`.
* **Zero-N+1 In-Memory Batch Linking**:
  - In `getPeriodsByType()`, eliminate synthetic ID mutation and per-row `db.query` loops.
  - Link `PeriodDetailedStats` in memory using `Map<Long, SportStats>` (`sportStatsId -> stats`) in strictly 3 batch queries total.
* **Integrity Healing & Gated Cleanup**:
  - In `PeriodsRepository.kt`: Change `checkIntegrityAndSync()` to `finishedWorkouts != periodWorkouts`.
  - In `PeriodSummariesDatabaseManager.kt`: Guard 66 legacy DB file existence checks behind `SharedPreferences` `legacy_period_dbs_cleaned`.
* **UI Consistency (Rule 23)**: No UI changes.

---

## 4. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Schema Evolution & Persistence (`PeriodSummariesDatabaseManager.kt`)**: Add 3 columns to `SportStatsContract` with safe non-null defaults, update `CREATE_TABLE`, implement `onUpgrade()` v26 $\rightarrow$ v27, and update `createSportStatsContentValues()`.
2. **Zero-N+1 Batch Fetching (`PeriodSummariesDatabaseManager.kt`)**: Populate `LongestWorkout` directly in `mapCursorToSportStats()`, index `SportStats` by ID in memory to link `DetailedStats`, and delete lines 221–231 N+1 query loop.
3. **Repository Decoupling & Deletion Healing (`PeriodsRepository.kt`)**: Update `checkIntegrityAndSync()` condition to `finishedWorkouts != periodWorkouts` and remove redundant fallback queries in `enrich()`.
4. **Preference-Gated Legacy Cleanup (`PeriodSummariesDatabaseManager.kt`)**: Wrap 66-file cleanup in `init` with SharedPreferences gate.
5. **Contract & Unit Test Suite (`PeriodSummariesDbTest.kt`)**: Add unit/contract tests for batch query budget, v26 $\rightarrow$ v27 migration parity, deletion self-healing, and cleanup gating. Verify via `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*"`.

---

## 5. Verification & Rollback Plan
* **Verification**: Targeted package unit tests + `./gradlew processDebugResources` + `python3 tools/verify_requirement_governance.py`.
* **Rollback**: Branch isolation allows full revert to `sprint/2026-43.9` without affecting existing commits.
