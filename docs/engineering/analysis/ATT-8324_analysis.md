# Stage 1 Problem Analysis: `ATT-8324`

**Ticket**: `ATT-8324` — `[Verbesserung] Add executable/attested verification for Aftermath Periods (14 TSTs from ATT-8145)`  
**Parent Epic**: `ATT-8138` | **Story Points**: `2 SP` | **Priority**: `Low` | **Target Release**: `V5.0.0`

---

## 1. Executive Summary & Scope Boundaries
`ATT-8145` downgraded 14 legacy Aftermath/Periods specs in `docs/tests/tst_aftermath_periods.md` from `[Verified]` to `[Implemented]` due to missing KDoc `TST-*` test evidence or executable assertions. Code inspection confirms all 12 mapped requirements (`Verification Method`: `Unit` or `Unit, Integration`) are **already implemented** in production code and **zero physical-hardware-only deferrals** are required.
- **Zero `app/src/main/` edits**: Pure test & living-doc verification remediation (`0` production files modified).
- **Zero `src/main/` source-text reads (`ATT-8199` / `REQ-PRO-133`)**: All Compose/UI and repository checks assert runtime behavior via JUnit/MockK/Robolectric/Compose test rules (`composeTestRule`), never scanning `src/main/` text.
- **SYS.2 / SYS.3 & Localization Impact**: No new architecture elements (`docs/architecture.md` unchanged) and no new UI strings (`Localization Parity: N/A`; existing `no_periods_available*` parity verified across all 9 locales).

---

## 2. Classification Matrix of All 14 `TST-*` Specifications

| TST ID | Requirement | Classification & Existing Coverage | Planned Executable Verification (`app/src/test/`) |
| :--- | :--- | :--- | :--- |
| `TST-PERF-001` | `REQ-MAP-017` | Partially Covered (`PeriodVertexBudgetTest`, `PeriodsRepositoryMemoryTest`, `MapPolylineMemoryStabilityTest`) | Add 500+ workout memory/vertex-budget scalability test in `PeriodsRepositoryMemoryTest` & `PeriodVertexBudgetTest`; tag `TST-PERF-001`. |
| `TST-PERF-002` | `REQ-PER-001` | Partially Covered (`WorkoutClustersViewModelTest.selectClusterById_*`) | Add >30-workout async `selectCluster` loading & prior-job cancellation test in `WorkoutClustersViewModelTest` (`TST-PERF-002`). |
| `TST-PERF-003` | `REQ-PER-003` | Missing (`WorkoutClusterRepositoryTest` lacks `migrationStatus` check) | Assert initial load / empty-DB bootstrap `MigrationStatus` phases in `WorkoutClusterRepositoryTest` & `WorkoutClustersViewModelTest`. |
| `TST-PERF-004` | `REQ-PER-001`, `REQ-DAT-003` | Missing (`PeriodsRepositorySyncTest` lacks `resyncAllPeriods` emissions) | Robolectric test on `PeriodsRepository.resyncAllPeriods()` verifying per-month incremental `_groupedPeriods` growth. |
| `TST-PERF-005` | `REQ-PER-004` | Missing (`MigrationStatus` phase monotonicity untested) | Assert monotonic `Phase 1 (Reading)` -> `Phase 1 (1.0f) + Phase 2 (Syncing [Month])` -> `Finalizing` -> `null` without regression. |
| `TST-PERF-006` | `REQ-PER-005` | Missing (UI progress card rendering untested) | Robolectric Compose UI test (`PeriodsProgressAndEmptyStateUiTest`) asserting multi-phase card visibility, phase labels, and dismissal. |
| `TST-PERF-007` | `REQ-PER-006` | Missing (`recalculateClustersWithProgress` status untested) | Assert multi-phase `MigrationStatus`, `isRecalculating`, and `recalculationFinished` in `WorkoutClusterRepositoryTest` & `WorkoutClustersViewModelTest`. |
| `TST-PERF-008` | `REQ-PER-007` | Partially Covered (`PeriodsViewModelTest.testShowPeriodMap_*`) | Add `showPeriodMap(partialSummary)` test proving direct `getWorkoutsForRange()` hydration and `isLoading` transition before rollup completes. |
| `TST-PERF-009` | `REQ-PER-006` | Missing (`needsEnrichment == false` silent path untested) | Verify `refreshClusters()` leaves `migrationStatus == null` when `previewPaths` persisted vs non-null on `recalculateClustersWithProgress()` / `forceShowProgress = true`. |
| `TST-PERF-010` | `REQ-PER-006` | Missing (restart SQLite persistence of `previewPaths` untested) | Robolectric SQLite test persisting `previewPaths` via `refreshClusters()` and verifying `needsEnrichment == false` on fresh repository instance. |
| `TST-PER-009` | `REQ-PER-008` | Missing (period temporal + sport filter integration untested) | Assert `WorkoutSummariesViewModel.getFilteredWorkouts(bSportType, startTimeS, endTimeS)` & `PeriodsFragment.startWorkoutSummaryList` argument contract. |
| `TST-PER-010` | `REQ-UI-120` | Missing (no UI check for absent heatmap toggle) | Robolectric Compose test verifying no `Whatshot`/heatmap toggle action in `PeriodsTabsScreen`/`PeriodMapScreen` while `heatmapPaths` remain populated. |
| `TST-PER-012` | `REQ-PER-009` | Partially Covered (`PeriodsViewModelTest` `TST-PER-032_1`/`_2`) | Tag existing `PeriodsViewModelTest` sport-filtering suites and verify `toggleSportFilter` updates tracks, markers, and heatmaps symmetrically. |
| `TST-PER-017` | `REQ-UI-125` | Missing (`PeriodList` empty state Compose UI untested) | Robolectric Compose test (`PeriodsProgressAndEmptyStateUiTest`) on `PeriodList(emptyList())` asserting `no_periods_available*` + `TranslationParityTest` 9-locale check. |

---

## 3. Chesterton's Fence & System Invariants
- **`Whatshot` Unused Import Removal Caution (`TST-PER-010`)**: `PeriodsTabsScreen.kt` and `PeriodMapScreen.kt` retain unused `Whatshot` imports, while all interactive toggle UI was deliberately removed so heatmaps are always active (`REQ-UI-120`). Tests verify runtime Compose semantics and ViewModel state with zero edits to production files.
- **Singleton & Robolectric Isolation**: Tests touching `PeriodsRepository`, `PeriodSummariesDatabaseManager`, `WorkoutClusterRepository`, or `WorkoutClusterDatabaseManager` MUST reset singletons and close DB helpers in `@Before`/`@After` using `@Config(sdk = [34], application = Application::class)` so `TrainingApplication.onCreate()` background migrations never race test SQLite tables.

---

## 4. Change Impact Analysis (`REQ-PRO-064` / ASPICE SUP.10)
1. **Affected Requirements**: `REQ-MAP-017`, `REQ-PER-001`, `REQ-DAT-003`, `REQ-PER-003`, `REQ-PER-004`, `REQ-PER-005`, `REQ-PER-006`, `REQ-PER-007`, `REQ-PER-008`, `REQ-PER-009`, `REQ-UI-120`, `REQ-UI-125` (status remains `[Implemented]` until Stage 5 `[Verified]`; `0` requirement semantics altered).
2. **Affected Architecture Elements**: None in production (`0` `app/src/main/` edits; `SYS.2`/`SYS.3`/`docs/architecture.md` unchanged). Test additions strictly within `app/src/test/` under existing `periodlist/`, `clusters/`, `workoutlist/`, and `database/` packages.
3. **Verification Impact & Test Surface**: Adds/enhances unit, Robolectric SQLite, and Compose UI tests across `PeriodsRepositoryMemoryTest`, `PeriodVertexBudgetTest`, `PeriodsRepositorySyncTest`, `PeriodsViewModelTest`, `WorkoutClusterRepositoryTest`, `WorkoutClustersViewModelTest`, `WorkoutSummariesViewModelFilterTest`, and new `PeriodsProgressAndEmptyStateUiTest` / `PeriodsNavigationFilterTest`.
4. **Risk Assessment & Invariants**: **LOW Risk**. Zero production code or schema changes; isolated to additive deterministic tests and traceability restoration in `docs/tests/tst_aftermath_periods.md` + `docs/requirements/`. Precondition: `SourceTextReadingAuditContractTest` must remain green (`0` `src/main/` reads).
