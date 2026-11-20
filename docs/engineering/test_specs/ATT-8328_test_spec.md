# Stage 2: Requirement & Test Specification — ATT-8328

**Ticket**: [ATT-8328](https://rainerblind.atlassian.net/browse/ATT-8328) (`3 SP`, Low) | **Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) | **Release**: `V5.0.0`  
**Scope**: Executable Verification & Reconciliation for `43` Legacy TSTs (`docs/tests/tst_settings_profiles.md`, `ATT-8145`)  
**Branch**: `feature/ATT-8328` | **Author**: Lane A Flash (`agent1`) | **Date**: 2026-11-20

---

## 1. Attributes & Governance Invariants
- **Priority**: High | **Type**: Functional / Non-Functional | **Verification Method**: Unit / Integration / Review
- **`SYS.2` / `SYS.3` Impact (`REQ-PRO-131.4`)**: None (`0` `app/src/main/`, hardware sensor, HAL, or external API changes).
- **Localization Parity**: `N/A - no UI strings`.
- **Gate 1 Non-Blocking Advisories Incorporated**:
  1. `TST-SET-046` (Bucket B1): Targets `EditWorkoutViewModel.updateSportName(newSportName: String)` (`EditWorkoutViewModel.kt:207`).
  2. `TST-SET-032` (Bucket A): Anchored in `PathPreviewMapBoundsTest.kt` (`createComposeRule()` / bounds) rather than legacy reflection-based `RouteItemWaypointContractTest.kt` (`REQ-PRO-132`).
  3. `TST-SET-017` (Bucket A): Tags existing `CollapsingAppBarNestedScrollConnectionTest.kt` with a companion behavioral Compose scroll test in net-new `WorkoutClustersTabsUiTest.kt` (Bucket B2).
- **Anti-Fabrication & Guardrail Compliance (`K1`/`M1`, `REQ-PRO-132`, `REQ-PRO-133`)**:
  1. Zero fabricated manual/physical/GoogleMap-gesture evidence: Bucket C (`4` TSTs) stays honestly `[Implemented]`.
  2. Zero `app/src/main/` source-text reads in any new/modified `app/src/test/` file (`app/source-reading-baseline.txt == 105`).
  3. Zero reflection-only `Class.forName("...Kt")` UI stubs (`app/ui-reflection-baseline.txt == 189`): all `16` Bucket B2 Compose UI tests use Robolectric `createComposeRule()`.
  4. All `43` TSTs and linked `REQ-SET-*` headings remain `[Implemented]` until Stage 5 post-pass promotion.
- **Multi-TST Parent REQ Guard**:
  - **Hold `[Implemented]` (Bucket C)**: `REQ-SET-014` (`TST-SET-006`), `REQ-SET-019` (`TST-SET-011`), `REQ-SET-021` (`TST-SET-013`), `REQ-SET-069` (`TST-SET-058`).
  - **Group-Promotion Guard**: `REQ-SET-010` (`TST-SET-003` + `034`), `REQ-SET-018` (`TST-SET-010` + `046`), `REQ-SET-034` (`TST-SET-025` + `042`), `REQ-SET-043` (`TST-SET-033` + `034` + `035`) promote in Stage 5 only if every linked TST passes.

---

## 2. Acceptance Criteria (`Given-When-Then`) Across All 43 TSTs

### Bucket A — Existing Tests Tagged & Augmented (`8` TSTs)
| TST ID | REQ(s) | Target Test Suite (`app/src/test/`) | Given-When-Then Acceptance Contract |
| :--- | :--- | :--- | :--- |
| `TST-SET-002` | `REQ-SET-009` | `WorkoutClustersViewModelTest.kt` | **Given** a cluster with member workouts, **When** `selectCluster(cluster)` runs, **Then** `mapState.heatmapPaths`, `tracks`, and `memberMarkers` are populated for the heatmap overlay. |
| `TST-SET-004` | `REQ-SET-012` | `ClusterItemThumbnailComposeTest.kt` | **Given** a `WorkoutCluster`, **When** `ClusterItem` renders via `createComposeRule()`, **Then** top header (sport icon + name), metadata row, and right-aligned `PathPreviewMap` thumbnail render cleanly. |
| `TST-SET-014` | `REQ-SET-022` | `WorkoutClustersViewModelTest.kt` | **Given** a selected cluster with members, **When** `selectCluster` and `selectWorkoutForPeek` execute, **Then** `mapState.memberMarkers` contains `START`/`END`/`DISTANCE` markers and peek loads individual workout markers. |
| `TST-SET-017` | `REQ-SET-025` | `CollapsingAppBarNestedScrollConnectionTest.kt` (+ `WorkoutClustersTabsUiTest.kt`) | **Given** `CollapsingAppBarNestedScrollConnection(300)` & `WorkoutClustersTabsScreen`, **When** scrolling up/down, **Then** `appBarOffset` collapses/expands monotonically within `[-headerHeightPx, 0]`. |
| `TST-SET-026` | `REQ-SET-035` | `WorkoutClusterPreviewPersistenceTest.kt` (+ `ClusterItemThumbnailComposeTest.kt`) | **Given** clusters with linked route or `>5` workout polylines, **When** `refreshClusters` enriches previews, **Then** at most 5 `previewPaths` persist in SQLite and render in `ClusterItem`. |
| `TST-SET-028` | `REQ-SET-037` | `EditWorkoutClusterTest.kt` | **Given** a workout reassigned or unassigned between clusters, **When** `assignClusterToWorkout`/`unassignClusterFromWorkout` runs, **Then** SQLite `CLUSTER_ID` & cluster `hitCount` update atomically and 0-hit source cluster is preserved. |
| `TST-SET-032` | `REQ-SET-042` | `PathPreviewMapBoundsTest.kt` | **Given** `PathPreviewMap` (`showApexMarker=true` for workout vs `false` for route) and explicit bounds, **When** composed with `createComposeRule()`, **Then** `buildValidatedBounds` frames the track and workout/route marker toggles match `REQ-SET-042`. |
| `TST-SET-042` | `REQ-SET-034`, `044` | `WorkoutRepositorySaveClusteringTest.kt` | **Given** a workout matched to `"Park Loop"` renamed to `"Morning Run"`, **When** `saveWorkout` completes, **Then** the linked `WorkoutCluster.name` remains `"Park Loop"` untouched. |

### Bucket B1 — Net-New Deterministic Engine / DB / ViewModel Unit Tests (`15` TSTs)
| TST ID | REQ(s) | Target Test Suite (`app/src/test/`) | Given-When-Then Acceptance Contract |
| :--- | :--- | :--- | :--- |
| `TST-SET-001` | `REQ-SET-006..008` | `WorkoutClusterEngineLearningTest.kt` | **Given** repeated routes with spatial fingerprints, **When** `learnFromWorkout`/`suggestCluster` run, **Then** same track matches agnostically, majority sport/override wins, and centroids refine while preserving non-default names (updating only default names). |
| `TST-SET-005` | `REQ-SET-013` | `WorkoutClusterEngineLearningTest.kt` | **Given** two workouts with identical name `"Home Loop"` and slight spatial drift, **When** `calculateSimilarity` runs, **Then** normalized name parity applies the `0.5x` score bonus over unnamed candidates. |
| `TST-SET-007` | `REQ-SET-015` | `WorkoutClusterEngineLearningTest.kt` | **Given** an existing cluster `"Home"` and a spatially distinct route also named `"Home"`, **When** `learnFromWorkout` / `findUniqueClusterName` runs, **Then** the new cluster is named `"Home var 2"` without overwriting `"Home"`. |
| `TST-SET-022` | `REQ-SET-031` | `WorkoutClusterEngineRouteSyncTest.kt` | **Given** an imported `RouteWithPath`, **When** `learnFromRoute` and `onRouteSyncedClusterRename` execute, **Then** a linked `WorkoutCluster` is created/matched and route rename updates the cluster's name. |
| `TST-SET-023` | `REQ-SET-032` | `WorkoutClustersViewModelTest.kt` | **Given** a cluster with `routeId > 0`, **When** `selectCluster(cluster)` executes, **Then** `viewModel.linkedRoute` emits the matching `RouteWithPath` from `RoutesRepository.getRouteByClusterId`. |
| `TST-SET-025` | `REQ-SET-034` | `WorkoutClusterEngineLearningTest.kt` | **Given** cluster `"Park Loop"` (`hitCount = 1`), **When** `assignClusterToWorkout` assigns a 2nd workout, **Then** workout name becomes `"Park Loop #2"` while `cluster.name` remains `"Park Loop"`. |
| `TST-SET-027` | `REQ-SET-036` | `WorkoutClusterDatabaseConnectionTest.kt` | **Given** Robolectric `WorkoutClusterDatabaseManager`, **When** sequential reads/writes run via `getDatabase()`, **Then** `db.isOpen` stays `true` without closing and auto-reopens cleanly if closed externally. |
| `TST-SET-031` | `REQ-SET-041` | `UnifiedSpatialMarkersTest.kt` | **Given** `ApplicationProvider` context and mocked `BitmapDescriptorFactory`, **When** `createSensorMarker` is invoked for `START`, `END`, and `APEX`, **Then** `TTColor.StartPoint`/`control_start`, `TTColor.EndPoint`/`control_stop`, and `TTColor.ApexPoint`/`ic_distance` produce valid markers. |
| `TST-SET-033` | `REQ-SET-043` | `WorkoutClusterEngineLearningTest.kt` | **Given** a `RouteWithPath` and a workout recorded on the identical polyline, **When** `findApexFromPoints` and `learnFromRoute` compute fingerprints, **Then** Start, End, Apex, and Distance match identically (`score == 0.0`). |
| `TST-SET-034` | `REQ-SET-010`, `043` | `WorkoutClusterEngineLearningTest.kt` | **Given** a `BIKE` cluster and a `RUN` workout on the same path, **When** `useSportTypeForClustering()` is `true` vs `false`, **Then** `true` adds `+5.0` penalty (`suggestCluster == null`) while `false` omits penalty and matches. |
| `TST-SET-035` | `REQ-SET-043` | `WorkoutClusterEngineLearningTest.kt` | **Given** an `UNKNOWN` cluster and a `RUN` workout with `useSportTypeForClustering() == true`, **When** `calculateSimilarity`/`suggestCluster` run, **Then** `+2.0` penalty is applied (`score >= 2.0`, rejected). |
| `TST-SET-045` | `REQ-SET-060` | `WorkoutClustersSelectableMarkersTest.kt` | **Given** `WorkoutClustersViewModel` + `MyPreferenceManager` DataStore, **When** `toggleMarkerType(ClusterMarkerType.START)` disables/enables `START`, **Then** `enabledMarkerTypes` updates, persists across VM re-instantiation, and does not alter `PeriodMarkerType` prefs. |
| `TST-SET-046` | `REQ-SET-018` | `EditWorkoutViewModelTest.kt` | **Given** a `RUN` workout on a `BIKE` cluster (`score > 5.0`), **When** `viewModel.updateSportName("Road Cycling")` (`EditWorkoutViewModel.kt:207`) runs + `advanceTimeBy(600)`, **Then** debounced `fetchClusterSuggestions` recalculates and drops score `< 1.0`. |
| `TST-SET-047` | `REQ-SET-061` | `WorkoutClusterEngineLearningTest.kt` | **Given** multiple imported workouts sharing route name `"Route A"`, **When** `learnFromWorkout` executes sequentially, **Then** normalized-name matching reuses and updates the existing `"Route A"` cluster without creating `"Route A var 2"`. |
| `TST-SET-061` | `REQ-SET-072` | `EditWorkoutNewClusterSyncTest.kt`, `EditWorkoutViewModelTest.kt` | **Given** `"Hausrunde #3"` in `EditWorkoutClusterDialog` (`createComposeRule()`) and `WorkoutClusterEngine.createNewClusterFromWorkout`, **When** creating `"Neue Runde"`, **Then** dialog pre-fills `"Hausrunde"`, DB updates `WORKOUT_NAME="Neue Runde"` (`forceIdentity=true`), and `EditWorkoutViewModel.createNewCluster` preserves user-typed custom names. |

### Bucket B2 — Net-New Robolectric `createComposeRule()` UI Behavioral Tests (`16` TSTs)
| TST ID | REQ(s) | Target Test Suite (`app/src/test/`) | Given-When-Then Acceptance Contract |
| :--- | :--- | :--- | :--- |
| `TST-SET-003` | `REQ-SET-010`, `011` | `ClusterTuningUiBehaviorTest.kt` | **Given** `ClusterTuningContent` & `ClusterTuningSection` under `createComposeRule()`, **When** adjusting master/detailed controls and tapping Recalculate, **Then** detailed sliders expand, callbacks fire, confirmation `AlertDialog` opens, and `recalculateClusters` runs. |
| `TST-SET-008` | `REQ-SET-016` | `WorkoutClusterDialogsUiTest.kt` | **Given** `EditWorkoutClusterIdentityDialog` via `createComposeRule()`, **When** entering a new cluster name, toggling `hasCounter`, and tapping Save, **Then** `onConfirm` emits updated name, sport ID, and counter flag. |
| `TST-SET-009` | `REQ-SET-017` | `WorkoutClusterDialogsUiTest.kt` | **Given** `WorkoutClusterSelectionDialog` with candidate `(cluster, score)` pairs, **When** tapping a candidate row, **Then** `onSelect` emits that `WorkoutCluster` for `moveWorkout`. |
| `TST-SET-010` | `REQ-SET-018` | `WorkoutClusterDialogsUiTest.kt` | **Given** `EditWorkoutClusterDialog` with scored candidates, **When** tapping a suggested cluster, **Then** `onSelectCluster` fires with that cluster (driving `applyClusterIdentity`). |
| `TST-SET-012` | `REQ-SET-020` | `WorkoutClusterDialogsUiTest.kt`, `WorkoutClustersViewModelTest.kt` | **Given** a peeked workout in `TrackOnMapScreen` / `WorkoutHeader` with `onChangeCluster`, **When** tapping Move-to-Cluster and choosing target cluster, **Then** `onChangeCluster` triggers and `moveWorkout` reassigns the workout. |
| `TST-SET-015` | `REQ-SET-023` | `ClusterImperialAndLocaleUiTest.kt` | **Given** `MyUnits.IMPERIAL`, **When** `ClusterTuningContent` and `TuningSubtitleFormatter` render via `createComposeRule()`, **Then** labels display `mi`/`ft` and `ManualClusterScreen` distance conversion multiplies by `BANALService.METER_PER_MILE`. |
| `TST-SET-016` | `REQ-SET-024` | `WorkoutClustersTabsUiTest.kt` | **Given** a cluster whose majority sport links to `"Canyon Aeroad"`, **When** `WorkoutClusterDetails` renders via `createComposeRule()`, **Then** `"→ Canyon Aeroad"` (`cluster_equipment_mapping_format`) is displayed. |
| `TST-SET-018` | `REQ-SET-026` | `WorkoutClustersTabsUiTest.kt` | **Given** `WorkoutClustersTabsScreen` with mixed `BIKE`/`RUN`/`UNKNOWN` clusters, **When** rendering and clicking tabs (`All`, `Bike`, `Run`, `Other`, `Unclustered`), **Then** all 5 tabs appear and filter items by `BSportType`. |
| `TST-SET-019` | `REQ-SET-027` | `WorkoutClustersTabsUiTest.kt` | **Given** `WorkoutClustersScreen` with hoisted `allListState` scrolled to index `> 0`, **When** `selectedCluster` toggles non-null then `null`, **Then** `allListState.firstVisibleItemIndex` remains preserved upon returning to list. |
| `TST-SET-020` | `REQ-SET-028` | `WorkoutClustersTabsUiTest.kt` | **Given** `WorkoutClustersTabsScreen` via `createComposeRule()`, **When** rendered, **Then** `FloatingActionButton` (`contentDescription = R.string.text_new`) is displayed and clicking it invokes `onAddClick`. |
| `TST-SET-024` | `REQ-SET-033` | `WorkoutClusterDeleteTest.kt` | **Given** `WorkoutClusterSummaryHeader` (`createComposeRule()`) and Robolectric SQLite `WorkoutClusterRepository.deleteCluster`, **When** long-clicking header -> Delete -> confirm, **Then** cluster is deleted and `WorkoutSummaries`/`Routes` `CLUSTER_ID` resets to `-1` without deleting workouts. |
| `TST-SET-029` | `REQ-SET-040` | `WorkoutClustersTabsUiTest.kt` | **Given** `WorkoutClustersTabsScreen` with unclustered workouts, **When** selecting the `Unclustered` tab and tapping a workout item, **Then** `UnclusteredWorkoutsList` renders and invokes `onWorkoutClick(workout)`. |
| `TST-SET-030` | `REQ-SET-039` | `ClusterImperialAndLocaleUiTest.kt` | **Given** `Locale.GERMAN` and `ManualClusterScreen` (or distance parser + field) via `createComposeRule()`, **When** entering `"5,5"` into the distance field, **Then** `"5,5"` is accepted and parses to `5.5`. |
| `TST-SET-040` | `REQ-SET-056` | `WorkoutClustersTabsUiTest.kt` | **Given** `UnclusteredWorkoutsList` item with `PathPreviewMap`, **When** rendered & clicked and `TrackOnMapScreen` Move action invoked, **Then** layout matches `ClusterItem` and opens `WorkoutClusterSelectionDialog`. |
| `TST-SET-041` | `REQ-SET-057` | `WorkoutClustersTabsUiTest.kt`, `WorkoutSummariesViewModelFilterTest.kt` | **Given** `ClusterItem` / `WorkoutClusterDetails` with `"5 recordings"`, **When** tapping the recordings text, **Then** `onHitCountClick(cluster)` fires and `getFilteredWorkouts(clusterId)` returns only that cluster's sessions. |
| `TST-SET-044` | `REQ-SET-038` | `WorkoutClusterDialogsUiTest.kt`, `WorkoutClusterRepositoryTest.kt` | **Given** a cluster with 5 workouts in `WorkoutSummaries` (even with stale SQLite `hit_count`), **When** `refreshClusters` runs and `WorkoutClusterSelectionDialog` renders, **Then** the dialog displays `5` recordings accurately. |

### Bucket C — Non-Automatable / Superseded / Live GoogleMap (`4` TSTs — Retained `[Implemented]`)
| TST ID | REQ(s) | Honest Retention Rationale (Zero Fabricated Evidence per `K1`/`M1`) |
| :--- | :--- | :--- |
| `TST-SET-006` | `REQ-SET-014` | **Superseded by `REQ-SET-075`** (`ATT-3452`); Favorite Routes header tuning & auto-dismiss flow removed (`ClusterTuningScreen` `@Deprecated`). |
| `TST-SET-011` | `REQ-SET-019` | Interactive 3-point `GoogleMap(onMapClick)` tap on `ManualClusterScreen.kt` requires live Play Services Maps rendering & touch coordinates on device. |
| `TST-SET-013` | `REQ-SET-021` | Interactive dragging/tapping of live `GoogleMap` `Marker`s (`Start`/`End`/`Apex`) in `WorkoutClusterHeatmapScreen.kt` requires device Maps runtime. |
| `TST-SET-058` | `REQ-SET-069` | Physical drag-to-`SheetValue.Expanded` visual inspection against system status bar icons (clock, battery, Wi-Fi) across live `GoogleMap` screens. |

---

## 3. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
- **Original Requirement ID & Target**: `TST-SET-003` (`REQ-SET-010`, `011`), `TST-SET-006` (`REQ-SET-014`), and `TST-SET-027` (`REQ-SET-036`) in `docs/tests/tst_settings_profiles.md`.
- **Historical Origin & Commit Trace**: Authored in Sprint `2026-40.x` prior to `ATT-3452` (`REQ-SET-075`), `ATT-8145` (`REQ-PRO-099`), `ATT-8198` (`REQ-PRO-132`), and `ATT-8199` (`REQ-PRO-133`).
- **Root Reason for Existing Formulation**: `TST-SET-003`/`006` predated `REQ-SET-075` moving cluster tuning from Favorite Routes header into Advanced Settings (`ClusterTuningSection`/`ClusterTuningContent`); `TST-SET-027` named Android Studio Database Inspector without describing the `Unit / Review` `WorkoutClusterDatabaseManager.getDatabase().isOpen` invariant (`ATT-289`).
- **Preservation of Core Invariants**: Reconciled descriptions in `docs/tests/tst_settings_profiles.md` (`v1.0.7 -> v1.0.8`) preserve all symmetric `Requirement(s):` ↔ `Tests:` links, retain all 43 TSTs at `[Implemented]` pending Stage 4/5 execution, and preserve `REQ-SET-075` centralization (`ClusterTuningContractTest.workoutClusters_hasNoTuningActionOrScreen`).

---

## 4. Tier 1 & Governance Verification Commands
- **Tier 1 Targeted Tests**: `python3 tools/parallel_sprint_lock.py --timeout 1800 ./gradlew :app:testDebugUnitTest --rerun --tests "..."` (covering Bucket A + B1 + B2 + `SourceTextReadingAuditContractTest` + `UiReflectionOnlyTestAuditContractTest`).
- **Governance Verification**: `python3 tools/verify_requirement_governance.py --repo-root . --base-ref refs/heads/sprint/2026-11-20_01_Früh --check-verified-evidence`.
