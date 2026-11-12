# Stage 1 Analysis: SportingJourneyRepository & Aggregation Bugfixes (ATT-7321)

## 1. Executive Summary & Problem Domain
* **Ticket**: `ATT-7321` (`[Verbesserung] SportingJourneyRepository & Aggregation: Fix recurring route cluster math, sport filtering, historical year range, and lifetime evaluation`)
* **Parent Epic**: `ATT-67` (Zeiträume / Periods & Stats)
* **Target Version**: `V5.0.0`
* **Defect Discovery**: During Ceremony 2 Joint Review of ATT-7022 on a physical Pixel 10 with a 14-year real-world database (2012–2026), four mathematical, SQL, and architectural data bugs were uncovered in `SportingJourneyRepository.kt` and its consumers:
  1. **Fabricated Heuristic & Route Cluster Math**:
     - `(gems.ritualWorkoutsCount / 20).coerceAtLeast(1)` in `SportingJourneyComponents.kt:386` arbitrarily fabricates the signature loops count instead of querying real cluster history.
     - `heroTotals.favoriteRoutesCount` treated all singleton clusters (`COUNT(DISTINCT CASE WHEN CLUSTER_ID > 0 THEN CLUSTER_ID END)`) as "favorite routes", misrepresenting one-off excursions as signature routes.
  2. **Sport Filter Data Loss (`SportingSportFilter.ALL`)**:
     - `SportingJourneyRepository.kt:122` restricted `ALL` to `w.B_SPORT IN ('BIKE', 'RUN')`, discarding `UNKNOWN`, `SWIM`, `WALK`, `OTHER`, and all non-bike/run sessions.
  3. **Sport-Specific Metric Contamination**:
     - Pedal revolutions and cadence rides were queried globally without sport gating, showing bicycle pedal revolutions when `SportingSportFilter.RUNNING` was selected.
  4. **Truncated Historical Year Range & Distorted Lifetime Evaluation**:
     - `SportingJourneyComponents.kt:107` artificially truncated `availableYears.take(5)`, hiding earlier years in long-term athlete histories (2012–2026).
     - Pioneer Athlete badge and `firstWorkoutDateStr` computed `MIN(timeStart)` under the horizon-filtered `whereSql`. When viewing a single year (e.g. 2026 Wrapped), lifetime history was reduced to that single year, locking the 365-day Pioneer badge for a 14-year user.
     - ViewModel state was not reactively keyed to `(horizon, sportFilter)` during loading, exposing stale combinations.

---

## 2. Independent Forensic Code Audit & Root Causes

| Defect Area | Current Flawed Implementation | Root Cause & Code Site | Remediated Design |
| :--- | :--- | :--- | :--- |
| **Recurring Route Cluster Math** | `(gems.ritualWorkoutsCount / 20).coerceAtLeast(1)` and `COUNT(DISTINCT CLUSTER_ID)` | Hardcoded heuristic in `SportingJourneyComponents.kt:386`; singleton clusters treated as favorite routes in `SportingJourneyRepository.kt:168`. | Query recurring clusters with repeat visits `COUNT(*) >= 3` via subquery `HAVING COUNT(*) >= 3`. Expose `recurringRoutesCount` in `SportingHiddenGems` and map `heroTotals.favoriteRoutesCount` to this exact count. |
| **Sport Filter Data Loss** | `whereClauses.add("B_SPORT IN ('BIKE', 'RUN')")` | Hardcoded restriction in `SportingJourneyRepository.kt:122-123` drops all other sports under `SportingSportFilter.ALL`. | For `SportingSportFilter.ALL`, omit `B_SPORT` filter completely to encompass all finished workouts. |
| **Cadence on Running** | `cadenceQuery` executed regardless of `sportFilter` | `SportingJourneyRepository.kt:215-234` queries pedal revolutions even when `sportFilter == RUNNING`. | When `sportFilter == SportingSportFilter.RUNNING`, suppress pedal revs (`totalRevolutions = 0L`, `cadenceRidesCount = 0`). Under `ALL`, count cadence revolutions only for cycling. |
| **Historical Year Range** | `availableYears.take(5).forEach` | Artificial UI truncation in `SportingJourneyComponents.kt:107`. | Remove `.take(5)` to expose all available distinct years (`availableYears.forEach`). |
| **Lifetime Pioneer History** | `MIN(timeStart)` in `heroQuery` with `WHERE $whereSql` | Bounded by horizon filter in `SportingJourneyRepository.kt:171`, resetting start date in annual views. | Evaluate lifetime start globally across all records via `SELECT MIN(timeStart) FROM WorkoutSummaries WHERE finished = 1`. |
| **State Keying & Concurrency** | `_uiState.value.copy(isLoading = true)` without filter keys | `SportingJourneyViewModel.kt:51` allows stale filter display and unmanaged concurrent coroutines. | Cancel in-flight job on filter change; update `_uiState` with target `(horizon, sportFilter)` immediately. |

---

## 3. Independent Peer Review & Verification (4-Augen-Prinzip)
* Audited live `sprint/2026-46.4` repository code at `SportingJourneyRepository.kt`, `SportingJourneyModels.kt`, `SportingJourneyComponents.kt`, and `SportingJourneyViewModel.kt`.
* Verified that SQLite table `WorkoutSummaries` indexes `cluster_id` and `time_start`. Grouping by `cluster_id` with `HAVING COUNT(*) >= 3` executes efficiently in SQLite without schema modifications.
* Verified that `SportingHiddenGems` can safely include `val recurringRoutesCount: Int = 0` without breaking backwards compatibility.

---

## 4. Change Impact Analysis (SUP.10 / REQ-PRO-064)
* **Affected Requirements**: `REQ-SPT-002` (Retrospective Filtering & Hero Totals), `REQ-SPT-003` (Hidden Gems & Achievements).
* **Affected Architecture Elements**: `SportingJourneyRepository.kt`, `SportingJourneyModels.kt`, `SportingJourneyComponents.kt`, `SportingJourneyViewModel.kt`.
* **Verification Impact & Test Surface**:
  - `SportingJourneyRepositoryTest.kt`: Add unit tests for recurring cluster count (`COUNT(*) >= 3`), `ALL` filter sport inclusivity, running pedal rev suppression, and global lifetime evaluation in annual horizon.
  - Full debug unit test suite (`./gradlew testDebugUnitTest`).
* **Risk Assessment & Invariants**:
  - **Risk Rating**: `LOW` (pure repository aggregation and component wiring; zero schema changes, zero database migrations).
  - **System Invariants**:
    - Zero schema changes on `WorkoutSummaries.db`.
    - Total workouts, distance, and duration math must remain exact.
    - Milestone clicks must continue to navigate to individual workout summaries.
