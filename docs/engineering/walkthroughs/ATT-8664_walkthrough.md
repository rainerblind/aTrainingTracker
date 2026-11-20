# Stage 5: Verification & Walkthrough - ATT-8664: Authoritative Period Primary Region Selection & Sport-Aware Preview Curation

**Ticket**: `ATT-8664` (`2 SP`, `Bug`, `Priority: High`, `severity-major`) | **Parent Epic**: `ATT-67` | **Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-20_01_Früh` (`465`) | **Branch**: `feature/ATT-8664` (preserved intact & unmerged for Stage 6 Cold Final Review per `REQ-PRO-125`)  
**Verified Production SHA (Gate 4 `ATT-9311`)**: `a68fb43a` (`git diff a68fb43a..HEAD -- app/` = EMPTY, `Rule 7.5` doc-only Stage 5)  
**Requirement / Test IDs**: `REQ-PER-046` (`REQ-PER-046.1`–`.4`) [Verified] ↔ `TST-PER-054` (`TST-PER-054.1`–`.4`) [Verified]  
**Author**: AI Agent 1 (`agent1`, Gemini 4 Neon) | **Date**: 2026-11-20

---

## 1. Root Cause & Delivered Fixes (`REQ-PER-046` / `TST-PER-054`)

1. **Authoritative Full-Period Primary-Region Ranking & Extrema Outlier Immunity (`REQ-PER-046.1` / `TST-PER-054.1`)**:
   * **Root Cause**: After `ATT-7666` (`REQ-PER-039`) bounded heap usage by setting `PeriodSummary.workoutIdToAllPolylinesMap = emptyMap()`, `PeriodsViewModel.filterPeriodSummary()` fell back to the `<= 5` spatial extrema anchors (`workoutIdToPolylineMap`: `northId`, `southId`, `eastId`, `westId`, `longestId`). In multi-region periods (e.g., `159` home rides in Stuttgart vs `2` vacation rides in Scotland holding `northId`/`westId`), `PeriodMapUtils.prepareCardMapPreviewSync()` ran `detectRegionsFromPaths()` on those `<= 5` skewed anchor tracks and crowned the 2-workout outlier excursion as Primary Region instead of the true dominant home region.
   * **Fix**:
     - `PeriodData.kt` (`288 / 300` LoC): Added compact `TimestampedPreviewPolyline`, `PeriodRegionPreview`, and `PrimaryRegionSummary` models, defaulted `regionSummaries` and `primaryRegionSummary` on `PeriodSummary`, and implemented pure helpers `buildRegionPreviews(groupWorkouts)` (delegating to `SpatialRegionEngine.detectRegions(groupWorkouts)` using `startLatLng`/bounding boxes with `0` `PolyUtil.decode()` calls and capping per-sport previews to `<= MAX_SUMMARY_THUMBNAIL_TRACKS` = 8 sorted `startTimeS DESC`) and `resolvePrimaryRegionForSports(regionSummaries, sports)` (ranking surviving regions strictly by `(workoutCount DESC, totalDistanceMeters DESC, mostRecentTimestampS DESC)` matching `SpatialRegionEngine.detectRegions()` 1:1).
     - `PeriodsRepository.kt` (`1,211 / 1,500` LoC, `+0` net LoC): Wired `buildRegionPreviews()` and `resolvePrimaryRegionForSports(regionPreviews, sports = null)` inside `enrich()`, curating `summary.polylines` (`<= 8`) strictly from the winning Primary Region while preserving all `REQ-PER-039` memory bounds (`workoutIdToPathMap == emptyMap()`, `workoutIdToAllPolylinesMap == emptyMap()`, `workoutIdToPolylineMap.size <= 5`).
   * *Proof*: `PeriodPrimaryRegionSelectionTest#testMultiRegionExtremaOutliers_selectsTrueHomePrimaryRegionWithTrueCount_tstPer054_1`.
2. **Sport-Toggle Re-Ranking, Indoor Sport Isolation & `filterPeriodSummary()` Hygiene (`REQ-PER-046.2` / `TST-PER-054.2`)**:
   * **Fix**: Updated `PeriodsViewModel.filterPeriodSummary()` (`PeriodsViewModel.kt`, `430 / 1,500` LoC) to re-rank `regionSummaries` across active `sports` via `resolvePrimaryRegionForSports()`, merge up to `8` newest genuine matching-sport polylines (`startTimeS DESC`) from the winning Primary Region, set `primaryRegionSummary = null`, `polylines = emptyList()`, and `workoutIdToPolylineMap = emptyMap()` for 100%-indoor filtered sports (zero leaked GPS tracks), preserve `workoutIdToAllPolylinesMap = emptyMap()` whenever input is empty (while filtering explicit test maps symmetrically for `TST-PER-032.1` parity), and preserve legacy fallback when `regionSummaries.isEmpty()`.
   * *Proof*: `PeriodPrimaryRegionSelectionTest#testSportToggleRerankingAndIndoorIsolation_updatesPrimaryRegionAndPolylines_tstPer054_2` & `PeriodsViewModelTest` (`16/16`).
3. **Preview Cache Key, Compose `produceState` Invalidation & Geocoding Center Parity (`REQ-PER-046.3` / `TST-PER-054.3`)**:
   * **Fix**:
     - `PeriodMapUtils.kt` (`474 / 1,500` LoC): Extracted shared `buildCardMapCacheKey(summary)` incorporating `summary.primaryRegionSummary?.hashCode() ?: 0` used by both `prepareCardMapPreviewSync()` and `prepareCardMapPreview()`. When `summary.primaryRegionSummary != null`, renders curated primary-region `budgetEnforcedPaths` with tight `activeBounds` (falling back to `primarySummary.bounds`), sets `regionCount = detectedRegionCount`, `primaryRegionPathsCount = primaryRegionWorkoutCount`, `activeRegionId = "region_0"`, and passes `primarySummary.center` to BOTH `findNearestKnownLocationName()` (`prepareCardMapPreviewSync`) and `resolvePlaceName()` (`prepareCardMapPreview`); falls back cleanly to `detectRegionsFromPaths()` when `primaryRegionSummary == null`.
     - `PeriodSummaryCard.kt` (`792 / 1,500` LoC): Updated `produceState` (`:259-266`) to `keys = arrayOf(summary.sortKey, summary.workoutIdToPolylineMap.hashCode(), summary.polylines.hashCode(), summary.primaryRegionSummary)`.
   * *Proof*: `PeriodPrimaryRegionSelectionTest#testCacheInvalidationOnRegionCountChangeAndCenterGeocodingParity_tstPer054_3`, `PeriodCardMapStateTest` (`11/11`), & `PeriodSummaryCardUiTest` (`1/1`).
4. **Strict `REQ-PER-039` Memory, LoC & Layering Invariants (`REQ-PER-046.4` / `TST-PER-054.4`)**:
   * *Proof*: `PeriodPrimaryRegionSelectionTest#testHighVolume520Workouts_preservesReqPer039MemoryBoundsAndLegacyFallback_tstPer054_4`, `PeriodsRepositoryMemoryTest` (`6/6`), `SpatialRegionEngineTest` (`8/8`), and all 3 governance contract suites.

---

## 2. Independent Verification Evidence (`Rule 7.5` Reuse of Gate 4 `ATT-9311` on `a68fb43a`)

Zero files under `app/` were modified after Gate 4 verified commit `a68fb43a` (`git diff a68fb43a..HEAD -- app/` is empty).

| Verification Suite / Check | Scope | Result |
| :--- | :--- | :--- |
| `PeriodPrimaryRegionSelectionTest` (`app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodPrimaryRegionSelectionTest.kt`) | `TST-PER-054.1`–`.4` (159 vs 2 extrema outlier immunity, sport-toggle re-ranking & 100%-indoor isolation, LRU cache invalidation & sync/async center geocoding parity, 520-workout `REQ-PER-039` memory bounds & legacy fallback) | **4/4 PASS** |
| `PeriodsRepositoryMemoryTest` | `REQ-PER-039` heap bounds (`workoutIdToPathMap`, `workoutIdToAllPolylinesMap`, `<= 5` anchors, `<= 8` thumbnails) | **6/6 PASS** |
| `PeriodsRepositorySyncTest` | Repository period rollup & sync non-regression | **8/8 PASS** |
| `PeriodCardMapStateTest` | `PeriodMapUtils` preview state, LRU caching & corridor calibration non-regression | **11/11 PASS** |
| `PeriodsViewModelTest` | `TST-PER-032` sport filtering & ViewModel projection non-regression | **16/16 PASS** |
| `SpatialRegionEngineTest` | `REQ-PER-013` Haversine clustering (`75 km`/`60 km`), ranking & antimeridian non-regression | **8/8 PASS** |
| `PeriodSummaryCardUiTest` | Compose `PeriodSummaryCard` rendering non-regression | **1/1 PASS** |
| `PeriodVertexBudgetTest` | `TST-PER-034` dual vertex budget (`<= 2,000` card / `<= 8,000` detail) non-regression | **6/6 PASS** |
| `PeriodMapVisualsTest` | Map preview visuals non-regression | **7/7 PASS** |
| `PeriodMapAdaptiveLayeringTest` | `REQ-PER-012` adaptive layering non-regression | **9/9 PASS** |
| `ArchitectureConformanceContractTest` | LoC budgets & `0` monolith edits | **11/11 PASS** |
| `DeadCodeEliminationContractTest` | Zero dead helpers/methods | **6/6 PASS** |
| `SourceTextReadingAuditContractTest` | `REQ-PRO-049.1` (`0` new `src/main` reads) | **6/6 PASS** |
| **Total Unit & Contract Suites (`--rerun`, Gate 4 `ATT-9311` @ `a68fb43a`)** | **13 suites + `:app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:assembleDebug` (`0` new Kotlin warnings)** | **99/99 PASS** (`0` fail, `0` err, `0` skip) |
| **Python Safety Linters (4/4)** | `lint_safe_enum.py`, `lint_cursor_safety.py`, `lint_lifecycle_safety.py`, `check_secrets_hygiene.py` | **4/4 PASS (`exit 0`)** |
| **Governance & Clean-Checkout Evidence** | `verify_requirement_governance.py --base-ref` & `--check-verified-evidence` (empty test dir) | **PASS (`exit 0`)** |

---

## 3. Architecture, LoC Budget & Branch Preservation
* `PeriodData.kt`: **`288 / 300` LoC** | `PeriodsRepository.kt`: **`1,211 / 1,500` LoC** (`+0` net LoC) | `PeriodsViewModel.kt`: **`430 / 1,500` LoC** | `PeriodMapUtils.kt`: **`474 / 1,500` LoC** | `PeriodSummaryCard.kt`: **`792 / 1,500` LoC** | `PeriodPrimaryRegionSelectionTest.kt`: **`534` LoC**.
* Protected Monoliths & Sibling Files: **`0` edits** (`SpatialRegionEngine.kt`, `PeriodsScreen.kt`, `PeriodMapScreen.kt`, `WorkoutRepository.kt`, `WorkoutSummariesDatabaseManager.java`, `TrackerService.java`, `DeviceManager.java`, `DevicesDatabaseManager.java`).
* SQLite schema migrations: `0` | Localization Parity: `N/A - no UI strings`.
* **Branch Preservation (`REQ-PRO-125`)**: `feature/ATT-8664` preserved intact and unmerged for Stage 6 Cold Final Review (`--merge-ticket` deferred strictly post-Stage 6 PASS).
