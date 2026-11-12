# Stage 5 Verification & Walkthrough: SportingJourneyRepository & Aggregation Bugfixes (ATT-7321)

## 1. Executive Summary & Verification Context
* **Ticket Key**: `ATT-7321`
* **Summary**: `[Verbesserung] SportingJourneyRepository & Aggregation: Fix recurring route cluster math, sport filtering, historical year range, and lifetime evaluation`
* **Parent Epic**: `ATT-67` (Zeiträume / Periods & Stats)
* **Target Release Version**: `V5.0.0`
* **Stage Sub-Task**: `[Test] SportingJourneyRepository & Aggregation Bugfixes`
* **Requirement Tracing**: `REQ-SPT-002` (`REQ-SPT-002.1`..`REQ-SPT-002.5`)
* **Test Tracing**: `TST-SPT-005` (`TST-SPT-005.1`..`TST-SPT-005.4`)

---

## 2. Implemented & Verified Changes

### 2.1 Recurring Route Cluster Math (REQ-SPT-002.1 / TST-SPT-005.1)
* **Model Layer (`SportingJourneyModels.kt`)**:
  - Added `val recurringRoutesCount: Int = 0` to `SportingHiddenGems` with backward-compatible default.
* **Repository Layer (`SportingJourneyRepository.kt`)**:
  - Eliminated arbitrary `(gems.ritualWorkoutsCount / 20)` heuristic.
  - Implemented SQLite subquery grouping clusters with repeat visits $\ge 3$ within the active query scope (`cluster_id > 0`):
    ```sql
    SELECT COUNT(*) AS recurring_cluster_count, IFNULL(SUM(cluster_workouts), 0) AS recurring_workouts_count
    FROM (
        SELECT cluster_id, COUNT(*) AS cluster_workouts
        FROM WorkoutSummaries
        WHERE $whereSql AND cluster_id > 0
        GROUP BY cluster_id
        HAVING COUNT(*) >= 3
    )
    ```
  - Mapped `heroTotals.favoriteRoutesCount` and `hiddenGems.recurringRoutesCount` directly to `recurring_cluster_count`.
  - Defined `ritualWorkoutsCount` strictly as `recurring_workouts_count`, and `explorerWorkoutsCount = totalWorkouts - ritualWorkoutsCount`.
  - Updated `local_legend` badge condition to evaluate against `recurringRoutesCount >= 5 || ritualCount >= 10`.
* **Presentation Layer (`SportingJourneyComponents.kt`)**:
  - Passed `gems.recurringRoutesCount` directly into `R.string.sporting_journey_gem_explorer_desc`, completely eliminating the fabricated division heuristic.

### 2.2 Inclusive Sport Aggregation & Cadence Gating (REQ-SPT-002.2, REQ-SPT-002.3 / TST-SPT-005.2)
* **Repository Layer (`SportingJourneyRepository.kt`)**:
  - For `SportingSportFilter.ALL`, omitted `B_SPORT` filtering entirely, ensuring finished sessions across all sports (`SWIM`, `UNKNOWN`, `WALK`, etc.) are fully aggregated.
  - For `SportingSportFilter.RUNNING`, suppressed pedal revolutions (`totalRevolutions = 0L`, `cadenceRidesCount = 0`).
  - Under `ALL`, restricted cadence revolutions strictly to cycling sessions (`w.B_SPORT = 'BIKE'`).

### 2.3 Full Historical Year Range & Lifetime Evaluation (REQ-SPT-002.4, REQ-SPT-002.5 / TST-SPT-005.3, TST-SPT-005.4)
* **Components Layer (`SportingJourneyComponents.kt`)**:
  - Removed artificial `.take(5)` on `availableYears.forEach` so athletes with 14-year histories can access every recorded year.
* **Repository Layer (`SportingJourneyRepository.kt`)**:
  - Evaluated lifetime history globally via `SELECT MIN(timeStart) FROM WorkoutSummaries WHERE finished = 1 AND timeStart IS NOT NULL`, preventing annual wrapped horizons from artificially resetting inception dates and locking the 365-day `pioneer_athlete` badge.
* **ViewModel Layer (`SportingJourneyViewModel.kt`)**:
  - Implemented immediate UI state keying on `(horizon, sportFilter)` with `isLoading = true` and cancellation of preceding in-flight jobs.

---

## 3. Verification Evidence & Test Results

### 3.1 Automated Test Execution
Executed via `tools/parallel_sprint_lock.py` under POSIX flock:
* `SportingJourneyRepositoryTest`:
  - `testAllTimeHorizon_aggregatesBothWorkouts`: **PASSED** (verifies singletons are not counted as favorite routes)
  - `testYearHorizon_2026_filtersAnnualWrappedWithoutSqlError`: **PASSED**
  - `testSportFilter_running_filtersCyclingOut`: **PASSED**
  - `testGetAvailableYears_returnsDistinctDescendingYears`: **PASSED**
  - `testRecurringRouteClusterMath_onlyCountsClustersWithRepeatVisitsGte3`: **PASSED** (TST-SPT-005.1)
  - `testSportFilterAll_includesAllSportsWithoutDroppingUnknownOrSwim`: **PASSED** (TST-SPT-005.2)
  - `testGlobalLifetimeEvaluation_retainsHistoryAcrossYearsAndUnlocksPioneer`: **PASSED** (TST-SPT-005.3)
* `SportingJourneyViewModelTest`:
  - `testInitialLoad_callsRepositoryWithDefaults`: **PASSED**
  - `testSetHorizon_immediatelyKeysStateAndRefreshes`: **PASSED** (TST-SPT-005.4)
  - `testSetSportFilter_immediatelyKeysStateAndRefreshes`: **PASSED** (TST-SPT-005.4)
* `SportingJourneyCalculatorTest`: 8 tests **PASSED**
* `SportingJourneyShareHelperTest`: 3 tests **PASSED**

### 3.2 Governance & Non-Regression Audit
* `verify_requirement_governance.py`: **PASSED** (100% parity across living requirement and test shards).
* Zero SQLite schema changes (`DB_VERSION` unchanged).
* Full 9-language localization parity maintained.
