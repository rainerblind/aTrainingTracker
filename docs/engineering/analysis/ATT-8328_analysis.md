# Stage 1 Analysis: ATT-8328 — Settings & Profiles 43-TST Verification Triage

**Ticket**: [ATT-8328](https://rainerblind.atlassian.net/browse/ATT-8328) (`3 SP`, Low) | **Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) | **Release**: `V5.0.0`  
**Active Sprint**: `2026-11-20_01_Früh` (`465`) | **Branch**: `feature/ATT-8328` (off `sprint/2026-11-20_01_Früh` @ `6b4ac2c5`)  
**Author**: Lane A Flash (`agent1`) | **Date**: 2026-11-20

---

## 1. Problem Statement & Motivation
Under ASPICE 4.0 Finding `M1`, `ATT-8145` (`docs/engineering/test_evidence/ATT-8145_unbacked_triage.csv`) downgraded `43` legacy `TST-SET-*` specifications in `docs/tests/tst_settings_profiles.md` (and their linked `REQ-SET-*` headings in `docs/requirements/req_settings_profiles.md`) from `[Verified]` to `[Implemented]` because they lacked hyphenated `// TST-SET-...` tags or passing JUnit XML / attested manual evidence. `ATT-8328` (`3 SP`) classifies all `43` TSTs against live `app/src/main/` and `app/src/test/` suites so Stage 4 adds genuine deterministic unit/Robolectric-Compose verification without fabricating manual results (`K1`/`M1`) or modifying `app/src/main/`.

---

## 2. Forensic 43-TST Triage & Independent Peer Review & Verification
* **Independent Peer Review & Verification**:
  * Verified all `43` `TST-SET-*` specs in `docs/tests/tst_settings_profiles.md` (`lines 10–473` in CSV, all currently `[Implemented]`) and all linked `REQ-SET-*` entries in `docs/requirements/req_settings_profiles.md` (`0` external sibling TSTs outside these `43`).
  * **Governance Guards**: Every new/modified test MUST use `createComposeRule()` for Compose UI (`REQ-PRO-132`, `0` new `Class.forName("...Kt")` stubs), `0` `app/src/main/` file reads (`REQ-PRO-133`), `0` `app/src/main/` production edits, and `0` modifications to `app/source-reading-baseline.txt` or `app/ui-reflection-baseline.txt`.
  * **Bucket Summary (`8 + 31 + 4 = 43`)**:
    * **Bucket A (`8` TSTs — Existing Tests + Hyphenated `TST-SET-*` Tag / Minor Behavioral Assertions)**: Existing suites already exercise the core behavior and only lack canonical `TST-SET-*` tags and minor focused assertions.
    * **Bucket B (`31` TSTs — Net-New Deterministic Unit / Robolectric-SQLite / `createComposeRule()` Tests)**: `15` Engine/DB/ViewModel unit suites (`B1`) + `16` Robolectric `createComposeRule()` UI suites (`B2`).
    * **Bucket C (`4` TSTs — Superseded or Live GoogleMap/System-Insets Manual Only; Retain `[Implemented]`)**: `TST-SET-006` (superseded by `REQ-SET-075`/`ATT-3452`), `TST-SET-011` (`ManualClusterScreen` 3-point live `GoogleMap(onMapClick)` tap), `TST-SET-013` (`WorkoutClusterHeatmapScreen` live `GoogleMap` `Marker` drag/tap), `TST-SET-058` (`REQ-SET-069` physical drag-to-`SheetValue.Expanded` visual check against system status bar icons across live `GoogleMap` screens).

### Complete 43-TST Classification Ledger
| TST ID | Linked REQ(s) | Bucket | Target Test File(s) & Verification Scope | Multi-TST REQ Guard |
|---|---|---|---|---|
| `TST-SET-002` | `REQ-SET-009` | **A** | `WorkoutClustersViewModelTest.kt`: tag `selectCluster`; assert `mapState.heatmapPaths` & fingerprint | Single-TST REQ |
| `TST-SET-004` | `REQ-SET-012` | **A** | `ClusterItemThumbnailComposeTest.kt`: `ClusterItem` header, metadata & right `PathPreviewMap` | Single-TST REQ |
| `TST-SET-014` | `REQ-SET-022` | **A** | `WorkoutClustersViewModelTest.kt`: `mapState.memberMarkers` (`START`/`END`/`DISTANCE`) & peek | Single-TST REQ |
| `TST-SET-017` | `REQ-SET-025` | **A** | `CollapsingAppBarNestedScrollConnectionTest.kt` + `WorkoutClustersTabsUiTest.kt` scroll collapse | Single-TST REQ |
| `TST-SET-026` | `REQ-SET-035` | **A** | `WorkoutClusterPreviewPersistenceTest.kt` + `ClusterItemThumbnailComposeTest.kt` (`<=5` previews) | Single-TST REQ |
| `TST-SET-028` | `REQ-SET-037` | **A** | `EditWorkoutClusterTest.kt`: `assignClusterToWorkout` / `unassignClusterFromWorkout` hit count sync | Single-TST REQ |
| `TST-SET-032` | `REQ-SET-042` | **A** | `PathPreviewMapBoundsTest.kt` + `RouteItemWaypointContractTest.kt`: Start/End/Apex vs Route & bounds | Single-TST REQ |
| `TST-SET-042` | `REQ-SET-034`, `044` | **A** | `WorkoutRepositorySaveClusteringTest.kt`: custom workout rename never mutates cluster name | `REQ-SET-034` shared w/ `025` |
| `TST-SET-001` | `REQ-SET-006..008` | **B1** | `WorkoutClusterEngineLearningTest.kt`: `learnFromWorkout`/`suggestCluster`, majority & default-name update | Single-TST REQs |
| `TST-SET-005` | `REQ-SET-013` | **B1** | `WorkoutClusterEngineLearningTest.kt`: `calculateSimilarity` `* 0.5` name-match bonus | Single-TST REQ |
| `TST-SET-007` | `REQ-SET-015` | **B1** | `WorkoutClusterEngineLearningTest.kt`: `findUniqueClusterName` appends `var 2` on distinct collision | Single-TST REQ |
| `TST-SET-022` | `REQ-SET-031` | **B1** | `WorkoutClusterEngineRouteSyncTest.kt`: `learnFromRoute` + `onRouteSyncedClusterRename` | Single-TST REQ |
| `TST-SET-023` | `REQ-SET-032` | **B1** | `WorkoutClustersViewModelTest.kt`: `selectCluster` loads `linkedRoute` via `RoutesRepository` | Single-TST REQ |
| `TST-SET-025` | `REQ-SET-034` | **B1** | `WorkoutClusterEngineLearningTest.kt`: workout `"Park Loop #2"` preserves `cluster.name == "Park Loop"` | Shared w/ `042` |
| `TST-SET-027` | `REQ-SET-036` | **B1** | `WorkoutClusterDatabaseConnectionTest.kt`: `WorkoutClusterDatabaseManager.getDatabase().isOpen` & reopen | Single-TST REQ (`Unit/Review`) |
| `TST-SET-031` | `REQ-SET-041` | **B1** | `UnifiedSpatialMarkersTest.kt`: `createSensorMarker` + `TTColor.StartPoint/EndPoint/ApexPoint` parity | Single-TST REQ |
| `TST-SET-033` | `REQ-SET-043` | **B1** | `WorkoutClusterEngineLearningTest.kt`: `findApexFromPoints` + `learnFromRoute` fingerprint parity | `REQ-SET-043` (`033,034,035`) |
| `TST-SET-034` | `REQ-SET-010`, `043` | **B1** | `WorkoutClusterEngineLearningTest.kt`: `useSportTypeForClustering` ON (`+5.0`) vs OFF (`0.0`) | `010` (`003,034`), `043` |
| `TST-SET-035` | `REQ-SET-043` | **B1** | `WorkoutClusterEngineLearningTest.kt`: `RUN` vs `BSportType.UNKNOWN` `+2.0` penalty (`suggestCluster==null`) | `REQ-SET-043` (`033,034,035`) |
| `TST-SET-045` | `REQ-SET-060` | **B1** | `WorkoutClustersSelectableMarkersTest.kt`: `toggleMarkerType` + `MyPreferenceManager` persistence | Single-TST REQ |
| `TST-SET-046` | `REQ-SET-018` | **B1** | `EditWorkoutViewModelTest.kt`: `onSportTypeSelected` debounced `fetchClusterSuggestions` (`>5.0` -> `<1.0`) | `REQ-SET-018` (`010,046`) |
| `TST-SET-047` | `REQ-SET-061` | **B1** | `WorkoutClusterEngineLearningTest.kt`: repeated `"Route A"` matches normalized name (no `var 2`) | Single-TST REQ |
| `TST-SET-061` | `REQ-SET-072` | **B1** | `EditWorkoutNewClusterSyncTest.kt` + `EditWorkoutViewModelTest.kt`: `stripHitCount`, `forceIdentity`, custom name | Single-TST REQ |
| `TST-SET-003` | `REQ-SET-010`, `011` | **B2** | `ClusterTuningUiBehaviorTest.kt`: `createComposeRule()` on `ClusterTuningContent` & `ClusterTuningSection` + recalc | `REQ-SET-010` (`003,034`) |
| `TST-SET-008` | `REQ-SET-016` | **B2** | `WorkoutClusterDialogsUiTest.kt`: `createComposeRule()` on `EditWorkoutClusterIdentityDialog` + `updateClusterIdentity` | Single-TST REQ |
| `TST-SET-009` | `REQ-SET-017` | **B2** | `WorkoutClusterDialogsUiTest.kt`: `createComposeRule()` on `WorkoutClusterSelectionDialog` -> `moveWorkout` | Single-TST REQ |
| `TST-SET-010` | `REQ-SET-018` | **B2** | `WorkoutClusterDialogsUiTest.kt`: `createComposeRule()` on `EditWorkoutClusterDialog` -> `applyClusterIdentity` | `REQ-SET-018` (`010,046`) |
| `TST-SET-012` | `REQ-SET-020` | **B2** | `WorkoutClusterDialogsUiTest.kt` + `WorkoutClustersViewModelTest.kt`: peek + `onChangeCluster` / `moveWorkout` | Single-TST REQ |
| `TST-SET-015` | `REQ-SET-023` | **B2** | `ClusterImperialAndLocaleUiTest.kt`: `MyUnits.IMPERIAL` in `ClusterTuningContent` & `ManualClusterScreen` (`mi`) | Single-TST REQ |
| `TST-SET-016` | `REQ-SET-024` | **B2** | `WorkoutClustersTabsUiTest.kt`: `createComposeRule()` on `WorkoutClusterDetails` (`→` equipment row) | Single-TST REQ |
| `TST-SET-018` | `REQ-SET-026` | **B2** | `WorkoutClustersTabsUiTest.kt`: `createComposeRule()` on `WorkoutClustersTabsScreen` 5 tabs & `BSportType` filter | Single-TST REQ |
| `TST-SET-019` | `REQ-SET-027` | **B2** | `WorkoutClustersTabsUiTest.kt`: `createComposeRule()` verifying hoisted `allListState` scroll index preserved | Single-TST REQ |
| `TST-SET-020` | `REQ-SET-028` | **B2** | `WorkoutClustersTabsUiTest.kt`: `createComposeRule()` verifying `FloatingActionButton` (`R.string.text_new`) | Single-TST REQ |
| `TST-SET-024` | `REQ-SET-033` | **B2** | `WorkoutClusterDeleteTest.kt`: `WorkoutClusterSummaryHeader` long-click Delete + `deleteCluster` DB link clearing | Single-TST REQ |
| `TST-SET-029` | `REQ-SET-040` | **B2** | `WorkoutClustersTabsUiTest.kt`: `UnclusteredWorkoutsList` tab selection & workout click callback | Single-TST REQ |
| `TST-SET-030` | `REQ-SET-039` | **B2** | `ClusterImperialAndLocaleUiTest.kt`: German locale `"5,5"` distance input parsing in `ManualClusterScreen` | Single-TST REQ |
| `TST-SET-040` | `REQ-SET-056` | **B2** | `WorkoutClustersTabsUiTest.kt`: `UnclusteredWorkoutItem` mini-map parity, click -> peek & Move action | Single-TST REQ |
| `TST-SET-041` | `REQ-SET-057` | **B2** | `WorkoutClustersTabsUiTest.kt` + `WorkoutSummariesViewModelFilterTest.kt`: click `"X recordings"` -> filtered list | Single-TST REQ |
| `TST-SET-044` | `REQ-SET-038` | **B2** | `WorkoutClusterDialogsUiTest.kt` + `WorkoutClusterRepositoryTest.kt`: selection dialog displays synced `hitCount` | Single-TST REQ |
| `TST-SET-006` | `REQ-SET-014` | **C** | **Superseded by `REQ-SET-075`** (`ATT-3452`); `ClusterTuningScreen` deprecated/removed from tabs header. Retain `[Impl]` | Blocked (`[Impl]`) |
| `TST-SET-011` | `REQ-SET-019` | **C** | Interactive 3-point tap on live `GoogleMap(onMapClick)` in `ManualClusterScreen.kt` requires device Maps SDK | Blocked (`[Impl]`) |
| `TST-SET-013` | `REQ-SET-021` | **C** | Interactive drag/tap of live `GoogleMap` `Marker`s in `WorkoutClusterHeatmapScreen.kt` requires device Maps SDK | Blocked (`[Impl]`) |
| `TST-SET-058` | `REQ-SET-069` | **C** | Physical `SheetValue.Expanded` drag vs system status bar icons on live `GoogleMap` screens (`K1`/`M1`) | Blocked (`[Impl]`) |

---

## 3. User Scope Grounding (ATT-1250) & Complexity Audit
* **In-Scope Goals (`3 SP`)**: Tag `8` existing suites (Bucket A), implement `31` deterministic JVM/Robolectric/Compose `createComposeRule()` verifications (Bucket B), honestly retain `4` non-automatable/superseded TSTs at `[Implemented]` (Bucket C), and promote only truthfully verified `TST-SET-*` and unblocked `REQ-SET-*` headings in Stage 5.
* **Out-of-Scope Non-Goals**: `0` `app/src/main/` edits; `0` edits to `app/source-reading-baseline.txt` / `app/ui-reflection-baseline.txt`; `0` fabricated manual attestations (`K1`/`M1`); `0` edits to the 5 user-owned dirty/untracked `tools/` / `docs/presentations/vorgehensmodell/` paths.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: `REQ-SET-014` / `TST-SET-006` (*Auto-Dismiss Tuning UI*) and legacy `TST-SET-001..061` in `docs/tests/tst_settings_profiles.md` & `docs/requirements/req_settings_profiles.md`.
* **Historical Origin & Commit Trace**: Authored during early Sprint `2026-40.x` cluster feature build-out prior to `ATT-3452` (`REQ-SET-075`), `ATT-8145` (`REQ-PRO-099`), `ATT-8198` (`REQ-PRO-132`), and `ATT-8199` (`REQ-PRO-133`).
* **Root Reason for Existing Formulation**: `REQ-SET-014` assumed cluster tuning lived in the Favorite Routes header and auto-dismissed on completion; `ATT-3452` (`REQ-SET-075`) intentionally excised header tuning (`ClusterTuningScreen` `@Deprecated`) and centralized tuning in Advanced Settings (`ClusterTuningSection.kt`) without auto-dismissal. `TST-SET-027` (`REQ-SET-036`) cites Android Studio Database Inspector alongside `Verification Method: Unit / Review` for persistent `mDatabase` connection lifecycle (`WorkoutClusterDatabaseManager.getDatabase()`).
* **Preservation of Core Invariants**: Preserve `REQ-SET-075` centralization (`ClusterTuningContractTest.workoutClusters_hasNoTuningActionOrScreen`), `REQ-PRO-132` (`ui-reflection-baseline == 189`), `REQ-PRO-133` (`source-reading-baseline == 105`), and truthful `[Implemented]` vs `[Verified]` parity (`M1`).

---

## 5. Change-Request Impact Analysis
* **Affected Requirements**: `39` `TST-SET-*` specs (`8` Bucket A + `31` Bucket B) and their unblocked `REQ-SET-*` headings in `docs/tests/tst_settings_profiles.md` & `docs/requirements/req_settings_profiles.md` (eligible for `[Verified]` only after Stage 4/5 JUnit XML pass; multi-TST guards apply to `REQ-SET-010`, `018`, `034`, `043`). `4` Bucket C TSTs (`TST-SET-006`, `011`, `013`, `058`) and their linked REQs (`REQ-SET-014`, `019`, `021`, `069`) strictly remain `[Implemented]`. **`SYS.2`/`SYS.3` Impact (`REQ-PRO-131.4`)**: **None** (`0` `app/src/main/`, hardware sensor, HAL, or external API changes).
* **Affected Architecture Elements**: `app/src/test/` unit/Robolectric/Compose test suites only (`0` `app/src/main/` components modified); `docs/tests/tst_settings_profiles.md`, `docs/requirements/req_settings_profiles.md`, and master index tables.
* **Verification Impact & Test Surface**: Incremental `./gradlew :app:testDebugUnitTest --rerun --tests ...` under `python3 tools/parallel_sprint_lock.py`, `SourceTextReadingAuditContractTest`, `UiReflectionOnlyTestAuditContractTest`, and `python3 tools/verify_requirement_governance.py --check-verified-evidence`. `Localization Parity: N/A - no UI strings`.
* **Risk Assessment & Invariants**: **LOW** (`3 SP`, test-only remediation). Zero production blast radius; `GoogleMap` composables isolated via `LocalInspectionMode` / ViewModel & dialog composables; all `4` non-automatable/superseded specs honestly kept `[Implemented]`.
