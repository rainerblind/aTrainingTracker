# Stage 2: Requirement & Test Specification - ATT-4476: Make PeriodSummaries.db Self-Contained and Eliminate N+1 Queries

**Ticket**: [ATT-4476](https://rainerblind.atlassian.net/browse/ATT-4476)  
**Parent Epic**: [ATT-4474](https://rainerblind.atlassian.net/browse/ATT-4474) (*Periods 2.0: Self-Contained Persistence, Full-History Map Previews & Timeline Analytics*)  
**Target Release**: Sprint 2026-43.9 | **Active Sprint**: `2026-43.9` | **Branch**: `feature/ATT-4476`  
**Requirement Mapping**: `REQ-PER-020` (refining `REQ-MIG-026`, `REQ-PER-011`)  
**Test Spec ID**: `TST-PER-026`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-23  

---

## 1. Requirement Specification (`REQ-PER-020`)

### 1.1 Problem Statement & Rationale
`PeriodSummariesDatabaseManager.getPeriodsByType()` triggers thousands of N+1 SQLite queries during startup by temporarily setting `longestWorkout.id` to a synthetic `sport_stats_id` and then executing per-row `SELECT` queries in a loop. Furthermore, `PeriodSportStats` omits `longest_workout_name`, `longest_distance`, and `longest_ascent`, forcing in-memory joining or fallback queries to `WorkoutSummaries.db`. In addition, `checkIntegrityAndSync()` checks `>` instead of `!=`, ignoring workout deletions, and cold start performs 66 redundant legacy file checks.

### 1.2 Functional & Architectural Requirements
1. **Zero-N+1 Batch Loading (`REQ-PER-020.1`)**: `getPeriodsByType()` SHALL execute strictly 3 batch SQLite queries total (`PeriodSummaries`, `PeriodSportStats`, `PeriodDetailedStats`). Synthetic pointer mutation of `longestWorkout` SHALL be eliminated, joining detailed stats via an in-memory index map (`Map<Long, SportStats>`).
2. **Self-Contained `LongestWorkout` Persistence (`REQ-PER-020.2`)**: `SportStatsContract` SHALL persist `longest_workout_name TEXT NOT NULL DEFAULT ''`, `longest_distance REAL NOT NULL DEFAULT 0.0`, and `longest_ascent INTEGER NOT NULL DEFAULT 0`. `DB_VERSION` SHALL increment to 27 with symmetrical `onCreate`/`onUpgrade` evolution (Coding Standards §2.1–§2.3, Rule 64) and registration in `DatabaseSchemaRegistry.kt` (Rule 62).
3. **Bidirectional Deletion Healing (`REQ-PER-020.3`)**: `PeriodsRepository.checkIntegrityAndSync()` SHALL evaluate `if (finishedWorkouts != periodWorkouts)`, resynchronizing when workouts are deleted out-of-band (`finishedWorkouts < periodWorkouts`).
4. **Gated Legacy File Cleanup (`REQ-PER-020.4`)**: The 66 legacy DB file existence checks in `init` SHALL execute strictly once per installation, guarded by a `SharedPreferences` boolean flag.
5. **Localization & Migration Invariants (`REQ-PER-020.5`)**: 9-language localization preserved (no new user-facing strings). Migration preserves 100% existing data with automated contract verification.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-MIG-026` (*O(1) Self-Healing Period Synchronization*) and `REQ-PER-011` (*Longest Workout Metric Display*). Targets `PeriodSummariesDatabaseManager.kt`, `PeriodData.kt`, `PeriodsRepository.kt`, `DatabaseSchemaRegistry.kt`.
* **Historical Origin & Commit Trace**: Sprint 2026-40.16 (`ATT-346`, commit `7a192b0c` for `tempId` pointer) and Sprint 2026-41.2 (`ATT-909` for `>` integrity check).
* **Root Reason for Existing Formulation**: `tempId` pointer was an expedient hack when detailed stats split from sport stats; `>` was assumed because workouts were recorded incrementally; incomplete schema deferred a database version bump.
* **Preservation of Core Invariants**: Normalized relational structure, sort key indexing (`REQ-MIG-026`), zero data loss, startup query budget ($\le 3$ batch queries), and 100% clean-room test pass rate remain strictly preserved.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Zero-N+1 Queries)**: *Given* hundreds of periods and sports in SQLite, *When* `getPeriodsByType()` executes, *Then* exactly 3 batch queries execute with zero per-row queries.
* **Criterion 2 (Migration Parity)**: *Given* an existing v26 database upgraded to v27, *When* `onUpgrade()` runs, *Then* schema matches fresh `onCreate()` with non-null defaults and zero data loss.
* **Criterion 3 (Deletion Healing)**: *Given* deleted workouts (`finishedWorkouts < periodWorkouts`), *When* `checkIntegrityAndSync()` runs, *Then* resynchronization is triggered.
* **Criterion 4 (Gated File Scans)**: *Given* subsequent application launches, *When* `init` runs, *Then* legacy file checks are bypassed.

---

## 2. Test Specification (`TST-PER-026`)

### Test Cases
* `TST-PER-026.1` (**Zero-N+1 Batch Query Budget**): Assert `getPeriodsByType()` executes exactly 3 batch queries and populates `longestWorkout` and `detailedStatsMap` without per-row queries.
* `TST-PER-026.2` (**Self-Contained LongestWorkout Persistence**): Assert saving and reading a period preserves complete `LongestWorkout` metadata without RAM workout dependencies.
* `TST-PER-026.3` (**SQLite v26->v27 Migration Parity Contract**): Assert upgraded v26 database produces identical schema structure to fresh v27 `onCreate()`.
* `TST-PER-026.4` (**Bidirectional Deletion Integrity Healing**): Assert `checkIntegrityAndSync()` triggers resync on `finishedWorkouts < periodWorkouts`.
* `TST-PER-026.5` (**Gated Legacy File Cleanup**): Assert 66 legacy DB file checks execute once and are bypassed when preference is set.
* `TST-PER-026.6` (**Clean-Room Regression Suite Execution**): Run `./gradlew testDebugUnitTest` verifying 100% test pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Class / Function Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PER-026.1` | Unit | `PeriodSummariesDatabaseManager.kt` / `getPeriodsByType` | `REQ-PER-020.1` | Planned |
| `TST-PER-026.2` | Unit | `PeriodSummariesDatabaseManager.kt` / `savePeriod`, `getPeriodsByType` | `REQ-PER-020.2` | Planned |
| `TST-PER-026.3` | Contract | `PeriodSummariesMigrationTest.kt` / `onCreate`, `onUpgrade` | `REQ-PER-020.2`, `REQ-PER-020.5` | Planned |
| `TST-PER-026.4` | Unit | `PeriodsRepository.kt` / `checkIntegrityAndSync` | `REQ-PER-020.3` | Planned |
| `TST-PER-026.5` | Unit | `PeriodSummariesDatabaseManager.kt` / `init` | `REQ-PER-020.4` | Planned |
| `TST-PER-026.6` | Regression | Full Test Suite (`testDebugUnitTest`) | `REQ-PER-020.5` | Planned |
