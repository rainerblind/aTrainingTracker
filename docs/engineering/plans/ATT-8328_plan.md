# Stage 3 Implementation Plan: ATT-8328 — Settings & Profiles 43-TST Verification Plan

**Ticket**: [ATT-8328](https://rainerblind.atlassian.net/browse/ATT-8328) (`3 SP`, Low) | **Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) | **Release**: `V5.0.0`  
**Scope**: Executable Verification for `39` Automatable TSTs (`8` Bucket A + `15` Bucket B1 + `16` Bucket B2) + `4` Bucket C Retained `[Implemented]`  
**Branch**: `feature/ATT-8328` | **Author**: Lane A Flash (`agent1`) | **Date**: 2026-11-20

---

## 1. SWE.2 Architecture, Guardrails & Scope
- **Layering & Test Isolation**: Pure test additions under `app/src/test/` exercising domain (`WorkoutClusterEngine`, `WorkoutClusterDatabaseManager`, `WorkoutClusterRepository`), ViewModel (`WorkoutClustersViewModel`, `EditWorkoutViewModel`), and Jetpack Compose UI (`createComposeRule()` with `LocalInspectionMode` / ViewModel fakes). Zero `app/src/main/` edits.
- **Hard Guardrails**:
  1. `0` `app/src/main/` edits (if a genuine defect is uncovered, file a Backlog Bug under `Rule 5` and retain `[Implemented]`).
  2. `0` `app/src/main/` file reads in `app/src/test/` (`REQ-PRO-133`, `app/source-reading-baseline.txt == 105`, `LEGITIMATE_GOVERNANCE_GUARDS == 22`).
  3. `0` reflection-only `Class.forName("...Kt")` UI stubs (`REQ-PRO-132`, `app/ui-reflection-baseline.txt == 189`): all `16` Bucket B2 Compose UI tests use Robolectric `createComposeRule()`.
  4. `0` fabricated manual/device evidence (`K1`/`M1`): Bucket C (`TST-SET-006`, `TST-SET-011`, `TST-SET-013`, `TST-SET-058`) stays `[Implemented]`.
- **UI Consistency (`docs/design_guidelines.md`) & `SYS.2`/`SYS.3` (`REQ-PRO-131.4`)**: `N/A - no app/src/main/ UI, architecture, or system-interface changes`. `Localization Parity: N/A - no UI strings`.

---

## 2. Atomic Stage 4 Implementation Steps

| Step | Target File(s) | Atomic Action | Covers TSTs |
| :--- | :--- | :--- | :--- |
| **0** | Jira Gate 3 Lock | Run `python3 /home/rainer/AndroidStudioProjects/aTrainingTracker/tools/jira_util.py --as agent1 check-gate <IMPL_PLAN_SUBTASK_KEY>` (`exit 0`, `Rule 4`) before any `app/` edit (`0` edits to `app/` in Stage 3). | Gate 3 Lock |
| **1** | Existing suites in `app/src/test/` (Bucket A) | Add explicit hyphenated `// TST-SET-xxx` comments and focused assertions: `WorkoutClustersViewModelTest.kt` (`TST-SET-002` `heatmapPaths`, `TST-SET-014` `memberMarkers`+peek), `ClusterItemThumbnailComposeTest.kt` (`TST-SET-004` `ClusterItem` header/metadata/map), `CollapsingAppBarNestedScrollConnectionTest.kt` (`TST-SET-017`), `WorkoutClusterPreviewPersistenceTest.kt` (`TST-SET-026` `<=5` previews), `EditWorkoutClusterTest.kt` (`TST-SET-028` hit count sync), `PathPreviewMapBoundsTest.kt` (`TST-SET-032` anchored via `createComposeRule()`/bounds — no reflection), `WorkoutRepositorySaveClusteringTest.kt` (`TST-SET-042` cluster name isolation). | `TST-SET-002`, `004`, `014`, `017`, `026`, `028`, `032`, `042` (`8`) |
| **2** | Engine, DB & ViewModel unit tests (`app/src/test/`, Bucket B1) | Add deterministic tests (`0` `src/main` reads): `WorkoutClusterEngineLearningTest.kt` (`TST-SET-001` agnostic clustering/majority/centroids, `TST-SET-005` `0.5x` name bonus, `TST-SET-007` unique `var 2`, `TST-SET-025` `#2` isolation, `TST-SET-033` route/workout apex parity, `TST-SET-034` sport toggle `+5.0`/`0.0`, `TST-SET-035` `UNKNOWN` `+2.0`, `TST-SET-047` idempotent `"Route A"`); `WorkoutClusterEngineRouteSyncTest.kt` (`TST-SET-022`); `WorkoutClustersViewModelTest.kt` (`TST-SET-023` `linkedRoute`); `WorkoutClusterDatabaseConnectionTest.kt` (`TST-SET-027` `getDatabase().isOpen` + reopen); `UnifiedSpatialMarkersTest.kt` (`TST-SET-031` `createSensorMarker`); `WorkoutClustersSelectableMarkersTest.kt` (`TST-SET-045` `ClusterMarkerType` DataStore persistence); `EditWorkoutViewModelTest.kt` (`TST-SET-046` `updateSportName` debounced `fetchClusterSuggestions`); `EditWorkoutNewClusterSyncTest.kt` (`TST-SET-061` `stripHitCount`, `forceIdentity`, custom name). | `TST-SET-001`, `005`, `007`, `022`, `023`, `025`, `027`, `031`, `033`, `034`, `035`, `045`, `046`, `047`, `061` (`15`) |
| **3** | Robolectric `createComposeRule()` UI tests (`app/src/test/`, Bucket B2) | Add behavioral `createComposeRule()` Compose suites (`0` `src/main` reads, `0` `Class.forName("...Kt")` stubs): `ClusterTuningUiBehaviorTest.kt` (`TST-SET-003` `ClusterTuningContent`/`ClusterTuningSection` + recalc); `WorkoutClusterDialogsUiTest.kt` (`TST-SET-008` `EditWorkoutClusterIdentityDialog`, `TST-SET-009` `WorkoutClusterSelectionDialog`, `TST-SET-010` `EditWorkoutClusterDialog`, `TST-SET-012` peek+Move `onChangeCluster`, `TST-SET-044` synced `hitCount=5`); `ClusterImperialAndLocaleUiTest.kt` (`TST-SET-015` `MyUnits.IMPERIAL` `mi`/`ft` & `METER_PER_MILE`, `TST-SET-030` German `"5,5"` distance input); `WorkoutClustersTabsUiTest.kt` (`TST-SET-016` `→` equipment, `TST-SET-017` companion scroll collapse, `TST-SET-018` 5-tab `BSportType` filter, `TST-SET-019` hoisted `allListState` scroll persistence, `TST-SET-020` `FloatingActionButton`, `TST-SET-029` `UnclusteredWorkoutsList` click, `TST-SET-040` unclustered mini-map+Move, `TST-SET-041` `"X recordings"` -> `getFilteredWorkouts`); `WorkoutClusterDeleteTest.kt` (`TST-SET-024` long-click Delete + `deleteCluster` DB link clearing). | `TST-SET-003`, `008`, `009`, `010`, `012`, `015`, `016`, `018`, `019`, `020`, `024`, `029`, `030`, `040`, `041`, `044` (`16`) |
| **4** | Locked Verification & Stage 5 Promotion | Run `python3 tools/parallel_sprint_lock.py --timeout 1800 ./gradlew :app:testDebugUnitTest --rerun --tests ...` covering ALL modified/new suites PLUS `SourceTextReadingAuditContractTest`, `UiReflectionOnlyTestAuditContractTest`, `ArchitectureConformanceContractTest`, `DeadCodeEliminationContractTest` (NEVER `--rerun-tasks`, NEVER `-PstampGitHash=true`). Verify `verify_requirement_governance.py --check-verified-evidence --test-results-dir ...` (`exit 0`). In Stage 5, promote the `39` verified `TST-SET-*` and unblocked `REQ-SET-*` headings (keeping `TST-SET-006`, `011`, `013`, `058` and `REQ-SET-014`, `019`, `021`, `069` `[Implemented]`; checking multi-TST guards `REQ-SET-010`, `018`, `034`, `043`) and sync master indexes. | All `39` (`+4` `[Impl]`) |

---

## 3. Invariants & Rollback
- **Invariants**: `0` `app/src/main/` edits; `app/source-reading-baseline.txt == 105`; `app/ui-reflection-baseline.txt == 189`; `5` user-owned dirty/untracked paths (`tools/ble_radar_simulator.py`, `tools/test_ble_radar_simulator.py`, `tools/jira_created_vs_resolved.py`, `tools/test_jira_created_vs_resolved.py`, `docs/presentations/vorgehensmodell/`) untouched.
- **Rollback**: Isolated purely to `app/src/test/` and living doc files on `feature/ATT-8328`.
