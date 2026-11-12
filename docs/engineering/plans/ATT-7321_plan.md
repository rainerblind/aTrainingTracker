# Stage 3 Implementation Plan: SportingJourneyRepository & Aggregation Bugfixes (ATT-7321)

## 1. Architecture & Layering Overview (SWE.2)
* **Domain & Model Layer**:
  - `SportingJourneyModels.kt`: Add `val recurringRoutesCount: Int = 0` to `SportingHiddenGems` data class for backward-compatible exposure of signature loops.
* **Repository & Aggregation Layer**:
  - `SportingJourneyRepository.kt`:
    1. Recurring Route Cluster Math:
       - Query recurring clusters via SQLite subquery:
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
       - Set `heroTotals.favoriteRoutesCount = recurring_cluster_count`.
       - Set `hiddenGems.recurringRoutesCount = recurring_cluster_count`.
       - Set `ritualCount = recurring_workouts_count` and `explorerCount = totalWorkouts - ritualCount`.
    2. Sport Inclusivity (`SportingSportFilter.ALL`):
       - Under `ALL`, do NOT add `B_SPORT IN ('BIKE', 'RUN')` filter (all finished workouts included).
    3. Cadence Sport Gating:
       - When `sportFilter == SportingSportFilter.RUNNING`, skip cadence aggregation (`totalRevolutions = 0L`, `cadenceRidesCount = 0`). Under `ALL`, filter cadence by `w.B_SPORT = 'BIKE'`.
    4. Global Lifetime History:
       - Query global start date across all finished records:
         `SELECT MIN(timeStart) FROM WorkoutSummaries WHERE finished = 1 AND timeStart IS NOT NULL`
       - Calculate `daysSinceFirstWorkout` and evaluate `pioneer_athlete` badge from this global minimum timestamp.
* **UI & Presentation Layer**:
  - `SportingJourneyComponents.kt`:
    - Remove fabricated `(gems.ritualWorkoutsCount / 20).coerceAtLeast(1)` in `SportingJourneyComponents.kt:386` and pass `gems.recurringRoutesCount`.
    - Remove artificial `.take(5)` on `availableYears.forEach` in `SportingJourneyComponents.kt:107` to render the full historical year range.
* **ViewModel Layer**:
  - `SportingJourneyViewModel.kt`:
    - In `loadData()`, cancel previous `loadJob`, immediately set `_uiState.value = _uiState.value.copy(horizon = currentHorizon, sportFilter = currentSportFilter, isLoading = true)`, and update with repository data.

---

## 2. Atomic Implementation Steps

| Step | Scope | Target File | Action & Invariants | Verification Command |
| :--- | :--- | :--- | :--- | :--- |
| **1** | Model | `SportingJourneyModels.kt` | Add `val recurringRoutesCount: Int = 0` to `SportingHiddenGems`. | `./gradlew compileDebugKotlin` |
| **2** | Repo Math | `SportingJourneyRepository.kt` | Implement recurring route SQL (`COUNT(*) >= 3`), sport filter fix, running cadence suppression, and global lifetime evaluation. | `./gradlew testDebugUnitTest --tests "*SportingJourneyRepositoryTest*"` |
| **3** | UI & VM | `SportingJourneyComponents.kt`, `SportingJourneyViewModel.kt` | Replace heuristic with `gems.recurringRoutesCount`, remove `.take(5)`, key ViewModel state and cancel jobs. | `./gradlew testDebugUnitTest --tests "*SportingJourney*"` |
| **4** | Unit Tests | `SportingJourneyRepositoryTest.kt`, `SportingJourneyViewModelTest.kt` | Add `TST-SPT-005..008` unit tests verifying cluster repeat counts, sport filtering, cadence gating, and lifetime evaluation. | `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.sportingjourney.*"` |

---

## 3. UI Consistency & Invariants
* **Design Token Compliance**: Reuses existing `TTColor.VaultAmber`, `TTColor.VaultExplorerEmerald`, and standard Material 3 typography. No layout shifts or new ad-hoc styles.
* **System Invariants**: Zero SQLite schema changes; no database migrations; exact distance/elevation metrics preserved; backward compatibility maintained across all models.
