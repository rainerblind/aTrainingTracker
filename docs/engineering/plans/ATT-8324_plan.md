# Stage 3 Implementation Plan: `ATT-8324`

**Ticket**: `ATT-8324` — `[Verbesserung] Add executable/attested verification for Aftermath Periods (14 TSTs from ATT-8145)`  
**Target Release**: `V5.0.0` | **Risk Rating**: `LOW` | **Scope**: Pure Test Construction (`0` `app/src/main/` Edits, `0` `src/main/` Test Reads)

---

## 1. Architecture, Gate 2 Guards & `UI Consistency`
- **Production Architecture (`SWE.2` / `SWE.3`)**: Zero changes to production Kotlin/Java, SQLite schemas, `docs/architecture.md`, or `docs/system/*`.
- **`UI Consistency`**: `N/A` (no UI changes). Compose tests configure `@Config(sdk = [34], application = Application::class)` and render `PeriodsTabsScreen(..., isPlayServiceAvailable = false)` / `PeriodList(...)` so Play Services `GoogleMap(...)` never initializes under Robolectric, while `PeriodMapScreen` deterministic heatmap state (`workoutIdToHeatmapPathMap`) is verified in `PeriodsViewModelTest`.
- **Multi-TST REQ Promotion Guard (Stage 5 Preview)**: `TST-BUG-002` (`REQ-PER-001`, `REQ-DAT-003`), `TST-MAN-001` (`REQ-DAT-003`), and `TST-UNT-012`/`TST-UNT-013` (`REQ-PER-008`) remain `[Implemented]` outside `ATT-8324`. Therefore Stage 5 will promote all **14** `TST-*` specs and **9** self-contained REQs (`REQ-MAP-017`, `REQ-PER-003..007`, `REQ-PER-009`, `REQ-UI-120`, `REQ-UI-125`) to `[Verified]`, keeping `REQ-PER-001`, `REQ-DAT-003`, and `REQ-PER-008` as `[Implemented]` until their remaining sibling TSTs are backed.

---

## 2. Step-by-Step Implementation & Traceability Matrix

| Step | Target File(s) (`app/src/test/`) | Exact Scope & Implementation Details | Covered `TST-*` / `REQ-*` |
| :--- | :--- | :--- | :--- |
| **1** | `periodlist/PeriodsRepositoryMemoryTest.kt`<br>`periodlist/PeriodVertexBudgetTest.kt` | Add 500+ workout memory/vertex-budget scalability tests verifying empty `workoutIdToPathMap`, `<= 8` thumbnails, `<= 64` LRU cache, and `<= 2,000` card / `<= 8,000` detail heatmap vertex caps; add KDoc `TST-PERF-001` tags. | `TST-PERF-001`<br>(`REQ-MAP-017`) |
| **2** | `database/WorkoutClusterRepositoryTest.kt`<br>`clusters/WorkoutClustersViewModelTest.kt` | Keep MockK setup isolated; add tests for: (a) >30-workout async `selectCluster` (`isLoading` transition, populated `tracks`/`heatmapPaths`/`memberMarkers`, prior-job cancellation); (b) empty/un-enriched `refreshClusters()` `MigrationStatus` -> `null`; (c) `recalculateClustersWithProgress()` + `recalculateClusters()` Phase 1->2 `MigrationStatus`, `isRecalculating`, `recalculationFinished`; (d) silent `refreshClusters(forceShowProgress = false)` (`null`) vs forced/`recalculateClustersWithProgress()`. | `TST-PERF-002` (`REQ-PER-001`)<br>`TST-PERF-003` (`REQ-PER-003`)<br>`TST-PERF-007`, `TST-PERF-009` (`REQ-PER-006`) |
| **3** | `database/WorkoutClusterPreviewPersistenceTest.kt` *(new, Robolectric)* | Dedicated `@RunWith(RobolectricTestRunner::class)` `@Config(sdk = [34], application = Application::class)` SQLite test (`@Before`/`@After` DB deletion + singleton reset). Seed workouts/cluster with empty `previewPaths`, run `refreshClusters()`, construct fresh `WorkoutClusterRepository` (`internal constructor`), and assert persisted `previewPaths` survive reload with `migrationStatus == null`. | `TST-PERF-010`<br>(`REQ-PER-006`) |
| **4** | `periodlist/PeriodSummariesDbTest.kt` | Reuse existing `@RunWith(RobolectricTestRunner::class)` SQLite setup (with `@Config(sdk = [34], application = Application::class)` / clean `PeriodsRepository.resetInstanceForTesting()` & `WorkoutSummariesDatabaseManager.setInstanceForTesting(null)` + `WorkoutRepository.resetForTesting(null)` in `@After`). Seed multi-month workouts and collect `groupedPeriods` + `migrationStatus` across `resyncAllPeriods()` verifying per-month incremental list growth and monotonic Phase 1 (`Reading`) -> Phase 1 (`1.0f`) + Phase 2 (`Syncing [Month]`) -> `Finalizing` -> `null`. | `TST-PERF-004` (`REQ-PER-001`, `REQ-DAT-003`)<br>`TST-PERF-005` (`REQ-PER-004`) |
| **5** | `periodlist/PeriodsViewModelTest.kt`<br>`workoutlist/WorkoutSummariesViewModelFilterTest.kt`<br>`periodlist/PeriodsNavigationFilterTest.kt` *(new, Robolectric)* | In `PeriodsViewModelTest`: test `showPeriodMap(partialSummary)` direct `getWorkoutsForRange()` reactive hydration (`TST-PERF-008`), deterministic `workoutIdToHeatmapPathMap` population (`TST-PER-010`), and `toggleSportFilter` symmetric marker/track/heatmap filtering (`TST-PER-012`). In `WorkoutSummariesViewModelFilterTest`: test `getFilteredWorkouts(bSportType, startTimeS, endTimeS)` boundary accuracy; in `PeriodsNavigationFilterTest`: verify `PeriodsFragment.startWorkoutSummaryList(summary, sport)` sets `ARG_BSPORT_TYPE`, `ARG_START_S`, `ARG_END_S` cleanly (`TST-PER-009`). | `TST-PERF-008` (`REQ-PER-007`)<br>`TST-PER-009` (`REQ-PER-008`)<br>`TST-PER-010` (`REQ-UI-120`)<br>`TST-PER-012` (`REQ-PER-009`) |
| **6** | `periodlist/PeriodsProgressAndEmptyStateUiTest.kt` *(new, Robolectric Compose)*<br>`localization/TranslationParityTest.kt` | `@RunWith(RobolectricTestRunner::class)` `@Config(sdk = [34], application = Application::class)` + `createComposeRule()`: render `PeriodsTabsScreen(..., isPlayServiceAvailable = false)` verifying `MigrationStatus` card phases & dismissal (`TST-PERF-006`), zero `Whatshot`/heatmap toggle semantics (`TST-PER-010`), and `PeriodList(emptyList())` `EmptyStatePlaceholder` `no_periods_available*` vs non-empty omission (`TST-PER-017`). Verify `no_periods_available` & `no_periods_available_hint` 9-locale parity in `TranslationParityTest.kt`. | `TST-PERF-006` (`REQ-PER-005`)<br>`TST-PER-010` (`REQ-UI-120`)<br>`TST-PER-017` (`REQ-UI-125`) |

---

## 3. System Invariants & Stage 4 Verification Plan (`Rule 7.5`)
- **Invariants**: Zero `app/src/main/` changes; zero `src/main/` file reads inside any test; deterministic Robolectric `@After` teardown (`PeriodsRepository.resetInstanceForTesting()`, `PeriodSummariesDatabaseManager.setInstanceForTesting(null)`, `WorkoutSummariesDatabaseManager.setInstanceForTesting(null)`, `WorkoutRepository.resetForTesting(null)`, `unmockkAll()`, `Dispatchers.resetMain()`).
- **Stage 4 Targeted Test Command (incremental `--rerun`, NEVER `--rerun-tasks`, NEVER `-PstampGitHash=true`)**:
  ```bash
  ./gradlew :app:testDebugUnitTest --rerun \
    --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodsRepositoryMemoryTest" \
    --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodVertexBudgetTest" \
    --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodSummariesDbTest" \
    --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodsViewModelTest" \
    --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodsProgressAndEmptyStateUiTest" \
    --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodsNavigationFilterTest" \
    --tests "com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutSummariesViewModelFilterTest" \
    --tests "com.atrainingtracker.trainingtracker.database.WorkoutClusterRepositoryTest" \
    --tests "com.atrainingtracker.trainingtracker.database.WorkoutClusterPreviewPersistenceTest" \
    --tests "com.atrainingtracker.trainingtracker.ui.clusters.WorkoutClustersViewModelTest" \
    --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" \
    --tests "com.atrainingtracker.architecture.SourceTextReadingAuditContractTest"
  ```
- Followed by `python3 tools/verify_requirement_governance.py --repo-root . --base-ref refs/heads/sprint/2026-11-19_01_Früh`.
