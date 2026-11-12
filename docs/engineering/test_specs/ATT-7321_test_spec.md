# Stage 2 Test Specification: SportingJourneyRepository & Aggregation Bugfixes (ATT-7321)

## 1. Requirement Specification (REQ-SPT-002, REQ-SPT-003)

* **REQ-SPT-002.1** (*Recurring Route Cluster Math*):
  - The retrospective aggregation engine SHALL count as signature loops / favorite routes only recurring route clusters where `COUNT(*) >= 3` within the filtered scope:
    $$\text{favoriteRoutesCount} = \left|\left\{ c \in \text{Clusters} \mid c > 0 \land \text{COUNT}(*)_c \ge 3 \right\}\right|$$
  - The engine SHALL eliminate fabricated heuristics (`gems.ritualWorkoutsCount / 20`) and expose the true recurring route cluster count via `SportingHeroTotals.favoriteRoutesCount` and `SportingHiddenGems.recurringRoutesCount`.
  - All workouts belonging to clusters with $\text{COUNT}(*) \ge 3$ SHALL be counted as ritual workouts (`ritualWorkoutsCount`), while all remaining workouts ($< 3$ cluster visits or unclustered) SHALL be classified as explorer workouts (`explorerWorkoutsCount = \text{totalWorkouts} - \text{ritualWorkoutsCount}`).

* **REQ-SPT-002.2** (*Sport Inclusivity Under SportingSportFilter.ALL*):
  - When `sportFilter == SportingSportFilter.ALL`, the engine SHALL include all finished workouts across all sports (`FINISHED = 1`), without filtering by `B_SPORT IN ('BIKE', 'RUN')`.

* **REQ-SPT-002.3** (*Sport-Specific Cadence Gating*):
  - When `sportFilter == SportingSportFilter.RUNNING`, the engine SHALL suppress bicycle pedal revolutions (`pedalRevolutions = 0L`, `cadenceTrackedRidesCount = 0`). Under `ALL`, cadence revolutions SHALL only be aggregated from cycling sessions (`B_SPORT = 'BIKE'`).

* **REQ-SPT-002.4** (*Full Historical Year Range*):
  - The UI and repository SHALL expose all available distinct calendar years (`availableYears`) without artificial truncation (`take(5)`).

* **REQ-SPT-002.5** (*Global Lifetime History & State Keying*):
  - `MIN(timeStart)` for athlete lifetime history and the Pioneer Athlete badge (`>= 365` days) SHALL be evaluated globally across all database records (`finished = 1`), independent of the active calendar year horizon.
  - `SportingJourneyViewModel` SHALL immediately key its UI state on `(horizon, sportFilter)` and cancel preceding in-flight jobs on filter changes.

---

## 2. Test Specification (TST-SPT-005..008)

| Test Spec ID | Target Requirement | Verification Procedure | Verification Target | Expected Result |
| :--- | :--- | :--- | :--- | :--- |
| `TST-SPT-005` | `REQ-SPT-002.1` | Insert 3 workouts for cluster 10, 2 for cluster 20, 1 unclustered. Call `loadJourneyData(AllTime, ALL)`. | `SportingJourneyRepositoryTest.kt` | `favoriteRoutesCount = 1`, `ritualWorkoutsCount = 3`, `explorerWorkoutsCount = 3`, `recurringRoutesCount = 1`. |
| `TST-SPT-006` | `REQ-SPT-002.2`, `REQ-SPT-002.3` | Insert `BIKE`, `RUN`, `SWIM`, `UNKNOWN` workouts with cadence. Query with `ALL` and `RUNNING`. | `SportingJourneyRepositoryTest.kt` | `ALL` yields 4 workouts; `RUNNING` has `totalWorkouts = 1`, `pedalRevolutions = 0L`, `cadenceTrackedRidesCount = 0`. |
| `TST-SPT-007` | `REQ-SPT-002.4`, `REQ-SPT-002.5` | Insert workouts from 2012 and 2026. Query with `SportingHorizon.Year(2026)`. | `SportingJourneyRepositoryTest.kt` | `daysSinceFirstWorkout > 365`, `pioneer_athlete` badge `isUnlocked = true`, `availableYears` contains all distinct years without `.take(5)`. |
| `TST-SPT-008` | `REQ-SPT-002.5` | Test `SportingJourneyViewModel` filter switching & cancellation. | `SportingJourneyViewModelTest.kt` | Immediate keying on `(horizon, sportFilter)` with `isLoading = true`, cancels obsolete coroutine job. |

---

## 3. Traceability Matrix & Invariants

| Requirement ID | Test Spec ID | Verification Component | Status |
| :--- | :--- | :--- | :--- |
| `REQ-SPT-002.1` | `TST-SPT-005` | `SportingJourneyRepositoryTest` | Specified |
| `REQ-SPT-002.2` | `TST-SPT-006` | `SportingJourneyRepositoryTest` | Specified |
| `REQ-SPT-002.3` | `TST-SPT-006` | `SportingJourneyRepositoryTest` | Specified |
| `REQ-SPT-002.4` | `TST-SPT-007` | `SportingJourneyRepositoryTest`, `SportingJourneyComponents` | Specified |
| `REQ-SPT-002.5` | `TST-SPT-007`, `TST-SPT-008` | `SportingJourneyRepositoryTest`, `SportingJourneyViewModelTest` | Specified |

* **Chesterton's Fence Audit**: No requirement removals. `REQ-SPT-002` and `REQ-SPT-003` are refined to repair heuristic and query deficiencies identified during physical Pixel 10 Ceremony 2 Joint Review.
* **System Invariants**: Zero SQLite schema modifications; exact physical distance, time, and elevation math preserved; 100% 9-locale string parity preserved.
