# Stage 2: Requirement & Test Specification - ATT-8664: Period Summary Map Preview Primary Region Selection & Sport-Aware Preview Curation

**Ticket**: `ATT-8664` (`2 SP`, `Bug`, `Priority: Medium`, `severity-major`) | **Parent Epic**: `ATT-67` | **Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-20_01_Früh` (`465`) | **Branch**: `feature/ATT-8664` (base `fd9ae6ea`)  
**Requirement Mapping**: `REQ-PER-046` (`REQ-PER-046.1`–`.4`)  
**Test Spec ID**: `TST-PER-054` (`TST-PER-054.1`–`.4`) | **Author**: AI Agent 1 (`agent1`) | **Date**: 2026-11-20

---

## 1. Requirement Specification (`REQ-PER-046`)

### 1.1 Attributes
- **Priority**: High | **Type**: Functional | **Verification Method**: Unit
- **Implementation**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodData.kt`, `PeriodsRepository.kt`, `PeriodsViewModel.kt`, `PeriodMapUtils.kt`, `PeriodSummaryCard.kt`
- **Localization Parity**: `N/A - no UI strings (reuses existing 9-locale workout_periods__primary_named_region_format / workout_periods__primary_region_format)`

### 1.2 Normative Clauses
1. **Authoritative Full-Period Primary-Region Ranking (`REQ-PER-046.1`)**:
   - `PeriodsRepository.enrich(summary, groupWorkouts)` **SHALL** compute compact `regionSummaries: List<PeriodRegionPreview>` via `SpatialRegionEngine.detectRegions(groupWorkouts)` (using `startLatLng` and bounding coordinates with **zero** `PolyUtil.decode()` calls) across all GPS-valid workouts in the period.
   - Each `PeriodRegionPreview` **SHALL** retain `center`, `bounds`, per-sport `sportCounts`, `sportDistancesMeters`, `sportMostRecentTimestampS`, and bounded timestamp-ordered per-sport preview tracks (`<= MAX_SUMMARY_THUMBNAIL_TRACKS` = 8 encoded polylines per sport per region, ordered `startTimeS DESC`).
   - Both `PeriodsRepository.enrich()` and `PeriodsViewModel.filterPeriodSummary()` **SHALL** rank surviving regions (`filteredCount > 0`) by the exact `SpatialRegionEngine.detectRegions()` comparator (`workoutCount DESC`, `totalDistanceMeters DESC`, `mostRecentTimestampS DESC`), selecting the true dominant region as Primary Region (`isPrimary = true`), propagating authoritative `primaryRegionSummary` (`detectedRegionCount`, `primaryRegionWorkoutCount`, `center`, `bounds`), and curating `summary.polylines` (`<= 8`) strictly from that winning Primary Region.
2. **Sport-Toggle Consistency, Indoor Sport Isolation & `filterPeriodSummary()` Hygiene (`REQ-PER-046.2`)**:
   - When `PeriodsViewModel.filterPeriodSummary(summary, sports)` runs (both for default `{BIKE, RUN}` and any toggled subset `{BIKE}`, `{RUN}`, `{UNKNOWN}`), if `summary.regionSummaries.isNotEmpty()`, it **SHALL** re-rank surviving regions (`filteredCount > 0`) across `sports`, merge up to `MAX_SUMMARY_THUMBNAIL_TRACKS` (`8`) genuine matching-sport polylines from the winning region (`startTimeS DESC`), update `primaryRegionSummary`, and return `polylines = emptyList()` when all matching workouts in `sports` are indoor/no-GPS (never leaking other sports' tracks or falling back to outlier extrema anchors).
   - `filterPeriodSummary()` **MUST** preserve `workoutIdToAllPolylinesMap = emptyMap()` whenever input `summary.workoutIdToAllPolylinesMap` is empty (fixing the retention leak while maintaining backward compatibility when tests supply explicit `workoutIdToAllPolylinesMap` maps).
3. **Preview Cache Key, Compose `produceState` Invalidation & Geocoding Center Parity (`REQ-PER-046.3`)**:
   - When `summary.primaryRegionSummary != null`, `PeriodMapUtils.prepareCardMapPreviewSync()` **SHALL** render the curated primary-region `budgetEnforcedPaths`, report `regionCount = detectedRegionCount`, report `primaryRegionPathsCount = primaryRegionWorkoutCount` (so the multi-region badge displays the true total period workout count in that region, e.g., `159`, and remains visible when `detectedRegionCount > 1`), set `activeRegionId = "region_0"`, and resolve `primaryRegionName` using `primaryRegionSummary.center` in BOTH `findNearestKnownLocationName()` (`prepareCardMapPreviewSync`) and `resolvePlaceName()` (`prepareCardMapPreview`) — matching `PeriodMapScreen` 1:1.
   - When `summary.primaryRegionSummary == null` (legacy/synthetic test fixtures), `PeriodMapUtils.prepareCardMapPreviewSync()` **SHALL** fall back seamlessly to `SpatialRegionEngine.detectRegionsFromPaths(budgetEnforcedPaths)`.
   - BOTH `periodCardMapStateCache`'s key (`PeriodMapUtils.kt`) and `PeriodSummaryCard.kt`'s `produceState` keys **MUST** incorporate `summary.primaryRegionSummary` so toggling sports with unchanged preview polylines immediately refreshes badge count, multi-region visibility, and region name.
4. **Strict Memory, LoC & Layering Invariants (`REQ-PER-046.4`)**:
   - `PeriodsRepository.enrich()` **MUST** preserve `workoutIdToPathMap == emptyMap()`, `workoutIdToAllPolylinesMap == emptyMap()`, `workoutIdToPolylineMap.size <= 5`, `polylines.size <= MAX_SUMMARY_THUMBNAIL_TRACKS (8)`, `periodCardMapStateCache <= 64`, and `0` `PolyUtil.decode()` calls inside `enrich()`.
   - Zero SQLite schema changes (`PeriodSummaries.db` unchanged), zero edits to `SpatialRegionEngine.kt` / `PeriodsScreen.kt` / protected monoliths, all modified files `<= 1,500` LoC, `0` new `src/main` reads (`REQ-PRO-049.1`).

---

## 2. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
1. **Original Requirement ID & Target**: Net-new `REQ-PER-046`, refining `REQ-PER-013` (multi-region detection), `REQ-PER-019` (card preview pipeline), `REQ-PER-024.5` (sport-filtered map preview), and `REQ-PER-039` (OOM remediation & bounded caching) across `PeriodData.kt`, `PeriodsRepository.kt`, `PeriodsViewModel.kt`, `PeriodMapUtils.kt`, and `PeriodSummaryCard.kt`.
2. **Historical Origin & Commit Trace**: `074bd857` (`ATT-7666` / `REQ-PER-039`), `ATT-4935` (`REQ-PER-024.5`), `ATT-4477` (`REQ-PER-019`), `ATT-1151` (`REQ-PER-013`).
3. **Root Reason for Existing Formulation**: `ATT-7666` set `workoutIdToAllPolylinesMap = emptyMap()` and capped `polylines <= 8` to fix OOM, inadvertently causing `filterPeriodSummary()` to fall back to `<= 5` spatial extrema anchors and `PeriodMapUtils` to cluster only that outlier-biased subset.
4. **Preservation of Core Invariants**: All `REQ-PER-039` memory bounds (`workoutIdToPathMap == emptyMap()`, `workoutIdToAllPolylinesMap == emptyMap()`, `workoutIdToPolylineMap.size <= 5`, `polylines.size <= 8`, LRU cache `<= 64`, Lite Mode unchanged), `SpatialRegionEngine` `75 km`/`60 km` thresholds, `PeriodMapScreen` interactive loading, and synthetic `PeriodSummary` legacy fallback remain 100% intact.

---

## 3. Acceptance Criteria
* **AC-1 (`REQ-PER-046.1`)**: *Given* a period with 159 home-region workouts (`Stuttgart`) and 2 outlier vacation workouts (`Highland Council`) holding `northId`/`westId`, *When* `PeriodsRepository.enrich()`, `PeriodsViewModel.filterPeriodSummary(..., {BIKE, RUN})`, and `PeriodMapUtils.prepareCardMapPreviewSync()` run, *Then* the Primary Region is `Stuttgart` (`isPrimary = true`), `regionCount == 2`, `primaryRegionPathsCount == 159`, `activeRegionId == "region_0"`, and all `<= 8` preview polylines belong to `Stuttgart`.
* **AC-2 (`REQ-PER-046.2`)**: *Given* a multi-region period where `BIKE` dominates Region A (`150` vs `2`) and `RUN` dominates Region B (`10` vs `5`) plus an indoor-only `UNKNOWN` sport (`20` workouts with empty polylines), *When* sport filter toggles `{BIKE, RUN}` -> `{RUN}` -> `{UNKNOWN}`, *Then* `{BIKE, RUN}` selects Region A (`155`, `regionCount == 2`), `{RUN}` re-ranks to Region B (`10`, `regionCount == 2`, `<= 8` genuine `RUN` polylines ordered `startTimeS DESC`), `{UNKNOWN}` yields `polylines == emptyList()` without leaking bike/run tracks, and `workoutIdToAllPolylinesMap` stays `emptyMap()`.
* **AC-3 (`REQ-PER-046.3`)**: *Given* two filtered projections of the same period sharing identical preview polylines but differing `primaryRegionWorkoutCount` (`159` vs `150`) or `detectedRegionCount` (`2` vs `1`), *When* `prepareCardMapPreviewSync()` / `prepareCardMapPreview()` execute, *Then* distinct cache entries are used, badge counts update immediately, and `RegionNameResolver` receives `primaryRegionSummary.center` in both sync and async paths.
* **AC-4 (`REQ-PER-046.4`)**: *Given* 520 workouts across multiple regions, *When* `PeriodsRepository.enrich()` runs and regression/contract suites execute, *Then* `workoutIdToPathMap == emptyMap()`, `workoutIdToAllPolylinesMap == emptyMap()`, `workoutIdToPolylineMap.size <= 5`, `polylines.size <= 8`, and all unit & governance contracts pass with `0` monolith edits and `0` new `src/main` reads.

---

## 4. Test Specification
* **`TST-PER-054.1` (Authoritative Primary-Region Ranking & Extrema Outlier Immunity — `PeriodPrimaryRegionSelectionTest.kt`)**:
  - Enrich a multi-region period (`159` Stuttgart bike/run workouts + `2` Isle-of-Skye outlier workouts holding `northId`/`westId`), pass through `PeriodsViewModel.filterPeriodSummary(..., {BIKE, RUN})` and `PeriodMapUtils.prepareCardMapPreviewSync()`, and assert: primary region bounds/center are in Stuttgart (NOT Scotland), `regionCount == 2`, `primaryRegionPathsCount == 159`, `activeRegionId == "region_0"`, `polylines.size == 8` (all Stuttgart tracks), and 1:1 parity with `SpatialRegionEngine.detectRegions()`.
* **`TST-PER-054.2` (Sport-Toggle Re-Ranking, Timestamp Ordering & Indoor Isolation — `PeriodPrimaryRegionSelectionTest.kt`)**:
  - Test multi-region period where `BIKE` dominates Region A (`150` vs `2`), `RUN` dominates Region B (`10` vs `5`), and `UNKNOWN` has `12` indoor workouts (no GPS). Verify toggling `{BIKE, RUN}` -> `{BIKE}` -> `{RUN}` -> `{UNKNOWN}` selects Region A (`155`), Region A (`150`), Region B (`10`, `<= 8` newest `RUN` polylines), and empty GPS (`0` leaked tracks), while keeping `workoutIdToAllPolylinesMap == emptyMap()`.
* **`TST-PER-054.3` (LRU Cache Invalidation on Count/Region Change & Center Geocoding Parity — `PeriodPrimaryRegionSelectionTest.kt`)**:
  - Verify `prepareCardMapPreviewSync()` does NOT return stale cached `primaryRegionPathsCount` or `regionCount` when `polylines` are identical across `{BIKE, RUN}` vs `{BIKE}`, and verify both `prepareCardMapPreviewSync()` and `prepareCardMapPreview()` resolve region name from `primaryRegionSummary.center`.
* **`TST-PER-054.4` (`REQ-PER-039` Memory Bounds, Legacy Fallback & Governance Contracts — `PeriodPrimaryRegionSelectionTest.kt`, `PeriodsRepositoryMemoryTest`, `PeriodCardMapStateTest`, `PeriodsViewModelTest`, `SpatialRegionEngineTest`, Contracts)**:
  - Verify 520-workout multi-region enrichment preserves `workoutIdToPathMap == emptyMap()`, `workoutIdToAllPolylinesMap == emptyMap()`, `workoutIdToPolylineMap.size <= 5`, `polylines.size <= 8`, legacy synthetic `PeriodSummary` fallback, plus `ArchitectureConformanceContractTest`, `DeadCodeEliminationContractTest`, and `SourceTextReadingAuditContractTest`.

---

## 5. Traceability Matrix

| Test Case | Scope | Test Suite(s) | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PER-054.1` | Full-period primary-region ranking vs extrema outliers, true count & multi-region badge | `PeriodPrimaryRegionSelectionTest` | `REQ-PER-046.1` | Planned |
| `TST-PER-054.2` | Sport-toggle re-ranking, `startTimeS DESC` track merge, indoor isolation & `emptyMap()` hygiene | `PeriodPrimaryRegionSelectionTest`, `PeriodsViewModelTest` | `REQ-PER-046.2` | Planned |
| `TST-PER-054.3` | Cache/Compose invalidation on count change & `primaryRegionSummary.center` geocoding parity | `PeriodPrimaryRegionSelectionTest`, `PeriodCardMapStateTest`, `PeriodSummaryCardUiTest` | `REQ-PER-046.3` | Planned |
| `TST-PER-054.4` | `REQ-PER-039` memory bounds (520 workouts), legacy fallback & architecture/dead-code/source-read contracts | `PeriodPrimaryRegionSelectionTest`, `PeriodsRepositoryMemoryTest`, `SpatialRegionEngineTest`, Governance Contracts | `REQ-PER-046.4` | Planned |
