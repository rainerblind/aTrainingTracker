# Stage 1 Analysis: ATT-4476 - Make PeriodSummaries.db self-contained and eliminate N+1 tempId queries in getPeriodsByType()

**Ticket**: [ATT-4476](https://rainerblind.atlassian.net/browse/ATT-4476)  
**Parent Epic**: [ATT-4474](https://rainerblind.atlassian.net/browse/ATT-4474) (*Periods 2.0: Self-Contained Persistence, Full-History Map Previews & Timeline Analytics*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `sprint/2026-43.9`  
**Branch**: `feature/ATT-4476`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-23  

---

## 1. Problem Statement & Motivation
During app startup and period list navigation, `PeriodSummariesDatabaseManager.getPeriodsByType()` triggers thousands of N+1 SQLite queries. It temporarily overwrites `SportStats.longestWorkout` with a synthetic ID pointer to link `PeriodDetailedStats`, then executes individual `SELECT` queries in a nested loop for every single sport entry across Day, Week, Month, and Year records. Furthermore, `PeriodSportStats` omits `longest_workout_name`, `longest_distance`, and `longest_ascent`, forcing `PeriodsRepository.enrich()` to join against in-memory workouts or execute fallback queries against `WorkoutSummaries.db`. Additionally, `checkIntegrityAndSync()` only checks `finishedWorkouts > periodWorkouts`, missing deleted workouts, while `init` scans 66 legacy DB files on every cold start.

---

## 2. Root Cause Analysis & Independent Peer Review
* **Independent Peer Review & Live Code Verification (ATT-3534)**:
  1. *N+1 Query Storm (`PeriodSummariesDatabaseManager.kt`, lines 183–234)*: Confirmed that line 193 overwrites `stats.longestWorkout = LongestWorkout(sId, "", 0, 0.0, 0)`. Lines 221–231 iterate through each `SportStats` row and execute `db.query(SportStatsContract.TABLE_NAME, ... "_id = ?")` to restore `longest_workout_id` and `longest_duration`, multiplying queries by $O(N)$ periods $\times$ sports.
  2. *Incomplete Schema in `SportStatsContract`*: Only stores `longest_workout_id` and `longest_duration`. `LongestWorkout` requires `name`, `distanceMeters`, and `ascentMeters`, causing `PeriodsRepository.enrich()` (lines 1009–1053) to query `WorkoutSummaries` when workouts are not in RAM.
  3. *Asymmetric Integrity Check (`PeriodsRepository.kt`, line 379)*: Confirmed `if (finishedWorkouts > periodWorkouts)` ignores out-of-band workout deletions (`finishedWorkouts < periodWorkouts`).
  4. *Redundant File Scans (`PeriodSummariesDatabaseManager.init`, lines 52–75)*: 66 `File.exists()` checks execute on every single app initialization without caching.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Eliminate the `tempId` pointer hack and N+1 queries in `getPeriodsByType()` using an in-memory `sportStatsIdToStatsMap`, reducing queries to strictly 3 batch operations total.
  * Persist full `LongestWorkout` metadata (`longest_workout_name`, `longest_distance`, `longest_ascent`) in `PeriodSportStats` with `DB_VERSION = 27`, enforcing safe `DEFAULT` values and `onCreate`/`onUpgrade` parity (Coding Standards §2.1, §2.2, §2.3).
  * Fix `checkIntegrityAndSync()` to test `finishedWorkouts != periodWorkouts` for bidirectional self-healing.
  * Gate legacy experimental database file cleanup behind a `SharedPreferences` flag (`legacy_period_dbs_cleaned`).
* **Out-of-Scope Non-Goals**:
  * Modifying `WorkoutSummaries.db` schema or backup registry contracts.
  * Modifying map rendering or graph UI components (covered under companion tickets ATT-4477/ATT-4478).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-MIG-026` (*O(1) Self-Healing Period Synchronization*); introduces `REQ-PER-020`.
* **Historical Origin & Commit Trace**: Sprint 2026-40.16 (ATT-346 / commit `7a192b0c` for `tempId` linkage) and Sprint 2026-41.2 (ATT-909 for `>` integrity check).
* **Root Reason for Existing Formulation**: `tempId` was a temporary workaround when relational detailed stats were first separated; `>` was assumed because workouts were recorded incrementally and deletion was rare.
* **Preservation of Core Invariants**: Foreign key constraints, cascading deletes, normalized aggregations, and DST-safe time boundaries remain 100% strictly preserved.

---

## 5. Architectural Strategy & High-Level Solution
1. **Schema Upgrade (`PeriodSummaries.db` v27)**:
   * Add `longest_workout_name TEXT DEFAULT ''`, `longest_distance REAL DEFAULT 0.0`, `longest_ascent INTEGER DEFAULT 0` to `PeriodSportStats`.
   * Update `onCreate()` and `onUpgrade()` with symmetrical column definitions and safe defaults.
2. **Zero-N+1 In-Memory Batch Linking**:
   * Directly populate `stats.longestWorkout` during initial cursor read. Index `SportStats` by `sportStatsId` in RAM to link `PeriodDetailedStats` in $O(1)$ time without database roundtrips.
3. **Self-Healing Sync & Once-Only Cleanup**:
   * Change condition to `finishedWorkouts != periodWorkouts`.
   * Cache legacy file cleanup in SharedPreferences.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Zero data loss on migration; strict $O(1)$ startup query budget (3 batch queries); 100% clean-room test pass rate; parent ticket human decision gate enforced.
* **Risk Rating**: **LOW** (additive schema migration with non-null defaults, pure algorithmic optimization).
