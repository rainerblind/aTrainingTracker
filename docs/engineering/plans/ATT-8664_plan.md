# Stage 3: Implementation Plan - ATT-8664: Authoritative Period Summary Primary Region Selection & Sport-Aware Preview Curation

**Ticket**: `ATT-8664` (`2 SP`, `Bug`, `Priority: Medium`, `severity-major`) | **Parent Epic**: `ATT-67` | **Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-20_01_Früh` (`465`) | **Branch**: `feature/ATT-8664` | **Author**: AI Agent 1 (`Pro` / Neon) | **Date**: 2026-11-20  
**Requirement Mapping**: `REQ-PER-046` (`REQ-PER-046.1`–`.4`) | **Test Mapping**: `TST-PER-054` (`TST-PER-054.1`–`.4`)

---

## 1. Architecture, Memory & LoC Budget (`SWE.2` / `SWE.3`)
* **Layer Boundaries & Memory Contract (`REQ-PER-039`, `REQ-PER-046.4`)**:
  * Pure in-memory analytics projection inside `ui/aftermath/periodlist/` (`PeriodData.kt` -> `PeriodsRepository.kt` -> `PeriodsViewModel.kt` -> `PeriodMapUtils.kt` -> `PeriodSummaryCard.kt`).
  * Zero SQLite schema bumps (`PeriodSummaries.db` unchanged), zero `PolyUtil.decode()` inside `PeriodsRepository.enrich()`, zero decoded `LatLng` lists retained on `PeriodSummary`, `workoutIdToPathMap == emptyMap()`, `workoutIdToAllPolylinesMap == emptyMap()`, `workoutIdToPolylineMap.size <= 5`, `summary.polylines.size <= MAX_SUMMARY_THUMBNAIL_TRACKS (8)`, `periodCardMapStateCache <= 64`.
  * Zero `TrackerService` / `BANALService` / `MyBTLEDevice` edits (`REQ-PRO-114` N/A); `Localization Parity: N/A - no UI strings`.
* **Touched Production Files & Exact LoC Budgets (All Well Below `1,500` Cap)**:
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodData.kt`: `178 -> ~255 / 1,500` LoC (`PeriodRegionPreview`, `PrimaryRegionSummary`, `TimestampedPreviewPolyline`, `buildRegionPreviews()`, `resolvePrimaryRegionForSports()`).
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsRepository.kt`: `1,211 -> ~1,235 / 1,500` LoC (delegates region-preview curation to `PeriodData.kt` helper, staying well under `1,300 / 1,500`).
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsViewModel.kt`: `423 -> ~455 / 1,500` LoC.
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodMapUtils.kt`: `431 -> ~485 / 1,500` LoC.
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodSummaryCard.kt`: `789 -> ~792 / 1,500` LoC.
* **Untouched Files & Protected Monoliths (`0` LoC Edits)**:
  * `SpatialRegionEngine.kt` (`604`), `PeriodsScreen.kt` (`210`), `PeriodMapScreen.kt`, `WorkoutRepository.kt`, `WorkoutSummariesDatabaseManager.java`, `TrackerService.java`, `DeviceManager.java`, `DevicesDatabaseManager.java`.

---

## 2. UI Consistency (`docs/design_guidelines.md`)
* **N/A — Zero layout/component/string changes**: `PeriodSummaryCard.kt` continues rendering the existing Material 3 multi-region badge (`workout_periods__primary_named_region_format` / `workout_periods__primary_region_format` across all 9 locales); only the `produceState` key array is extended with `summary.primaryRegionSummary` so badge text/visibility refreshes immediately on sport-filter toggles.

---

## 3. System Invariants & Preserved Contracts
1. **1:1 Ranking Parity with `SpatialRegionEngine.detectRegions()`**: Surviving regions (`filteredCount > 0`) are ranked strictly by `(workoutCount DESC, totalDistanceMeters DESC, mostRecentTimestampS DESC)`, matching `PeriodMapScreen`.
2. **Strict `REQ-PER-039` Memory Bounds**: `workoutIdToPathMap == emptyMap()`, `workoutIdToAllPolylinesMap == emptyMap()` (including post-`filterPeriodSummary()` when input is empty), `workoutIdToPolylineMap.size <= 5`, `polylines.size <= 8`, `periodCardMapStateCache <= 64`, `0` `PolyUtil.decode()` inside `enrich()`.
3. **Indoor Sport Isolation & Legacy Fallback**: 100%-indoor filtered projections set `primaryRegionSummary = null` and `polylines = emptyList()` (zero leaked tracks from other sports); synthetic `PeriodSummary` fixtures with `regionSummaries = emptyList()` / `primaryRegionSummary = null` retain legacy `detectRegionsFromPaths()` and polyline-map filtering behavior.
4. **Hygiene**: Gate 3 Code Lock (`0` edits under `app/` in Stage 3); explicit hyphenated `// REQ-PER-046.x / TST-PER-054.x` tags; `0` new Kotlin compiler warnings; `0` `src/main` reads (`REQ-PRO-049.1`).

---

## 4. Atomic Stage 4 Implementation Sequence

### Step 0: Gate 3 Code Lock Verification (`Rule 4`)
* Run `python3 /home/rainer/AndroidStudioProjects/aTrainingTracker/tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>` and confirm `GATE_PASSED` (exit `0`) before modifying any file under `app/src/`.

### Step 1: Compact Region Preview DTOs & Shared Ranking/Curation Helpers (`REQ-PER-046.1`, `.4` -> `TST-PER-054.1`, `.4`)
* **Target**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodData.kt` (`178 -> ~255` LoC).
* Add lightweight DTOs (zero decoded `LatLng` lists):
  * `data class TimestampedPreviewPolyline(val startTimeS: Long, val encodedPolyline: String)`
  * `data class PeriodRegionPreview(val center: LatLng, val bounds: LatLngBounds, val sportCounts: Map<BSportType, Int>, val sportDistancesMeters: Map<BSportType, Double>, val sportMostRecentTimestampS: Map<BSportType, Long>, val sportPreviewPolylines: Map<BSportType, List<TimestampedPreviewPolyline>>)`
  * `data class PrimaryRegionSummary(val detectedRegionCount: Int, val primaryRegionWorkoutCount: Int, val center: LatLng, val bounds: LatLngBounds)`
* Add defaulted fields to `PeriodSummary`:
  * `val regionSummaries: List<PeriodRegionPreview> = emptyList()`
  * `val primaryRegionSummary: PrimaryRegionSummary? = null`
* Add pure helper functions (eliminating duplication & keeping `PeriodsRepository.kt < 1,300` LoC — Gate 2 Tip #2):
  * `internal fun buildRegionPreviews(groupWorkouts: List<WorkoutData>): List<PeriodRegionPreview>`: calls `SpatialRegionEngine.detectRegions(groupWorkouts)` (zero `PolyUtil.decode()`), indexes `groupWorkouts` by `id`, and per detected region computes per-sport `sportCounts`, `sportDistancesMeters`, `sportMostRecentTimestampS`, and bounded `sportPreviewPolylines` (`<= MAX_SUMMARY_THUMBNAIL_TRACKS` = 8 non-empty polylines per sport sorted `startTimeS DESC`).
  * `internal fun resolvePrimaryRegionForSports(regionSummaries: List<PeriodRegionPreview>, sports: Set<BSportType>? = null): Pair<PrimaryRegionSummary, List<String>>?`: computes per-region filtered count/distance/mostRecent across `sports` (all sports if `null`), keeps `filteredCount > 0`, returns `null` if none survive (indoor-only isolation), ranks strictly by `(filteredCount DESC, filteredDistance DESC, filteredMostRecent DESC)`, merges matching-sport `TimestampedPreviewPolyline` entries from the winning Primary Region sorted `startTimeS DESC` capped at `MAX_SUMMARY_THUMBNAIL_TRACKS` (`8`), and returns `PrimaryRegionSummary(ranked.size, winnerCount, winner.center, winner.bounds) to curatedPolylines`.

### Step 2: Authoritative Enrichment in `PeriodsRepository.enrich()` (`REQ-PER-046.1`, `.4` -> `TST-PER-054.1`, `.4`)
* **Target**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsRepository.kt` (`1,211 -> ~1,235` LoC).
* Inside `enrich(summary, groupWorkouts)`:
  * Compute `regionPreviews = if (groupWorkouts.isNotEmpty()) buildRegionPreviews(groupWorkouts) else summary.regionSummaries`.
  * Compute `resolvedPrimary = resolvePrimaryRegionForSports(regionPreviews, sports = null)`.
  * Populate `polylines` from `resolvedPrimary?.second` (when non-empty; falling back cleanly to `<= 8` non-empty `groupWorkouts` / `summary.polylines.take(MAX_SUMMARY_THUMBNAIL_TRACKS)` only if no GPS region was detected), keeping `workoutIdToPolylineMap` scoped to `<= 5` spatial anchors, `workoutIdToAllPolylinesMap = emptyMap()`, `workoutIdToPathMap = emptyMap()`, and storing `regionSummaries = regionPreviews` & `primaryRegionSummary = resolvedPrimary?.first`.

### Step 3: Sport-Aware Primary Region Re-Ranking & Retention Leak Fix in `PeriodsViewModel.filterPeriodSummary()` (`REQ-PER-046.2`, `.4` -> `TST-PER-054.2`, `.4`)
* **Target**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsViewModel.kt` (`423 -> ~455` LoC).
* Fix `filteredAllPolylinesMap` retention leak: compute strictly from `summary.workoutIdToAllPolylinesMap` (remaining `emptyMap()` whenever input is empty, preserving backward compatibility for tests that explicitly populate `workoutIdToAllPolylinesMap`).
* When `summary.regionSummaries.isNotEmpty()`:
  * Compute `resolvedRegion = resolvePrimaryRegionForSports(summary.regionSummaries, sports)`.
  * Set `filteredPrimaryRegionSummary = resolvedRegion?.first` (`null` for 100%-indoor sports — Gate 2 Tip #1).
  * Set `filteredPolylines = resolvedRegion?.second ?: emptyList()` (up to `8` genuine matching-sport tracks from the winning Primary Region; `emptyList()` for 100%-indoor sports without leaking other sports' tracks).
* When `summary.regionSummaries.isEmpty()` (legacy/synthetic test fixtures): keep exact existing polyline fallback unchanged (`filteredAllPolylinesMap` -> `filteredPolylineMap` -> `summary.polylines`).

### Step 4: Authoritative Count, Center Geocoding Parity & Cache/Compose Invalidation (`REQ-PER-046.3` -> `TST-PER-054.3`)
* **Target 1**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodMapUtils.kt` (`431 -> ~485` LoC).
  * Add `private fun buildCardMapCacheKey(summary: PeriodSummary): String` incorporating `summary.periodType`, `summary.sortKey`, `summary.polylines.hashCode()`, `summary.workoutIdToPolylineMap.hashCode()`, and `summary.primaryRegionSummary?.hashCode() ?: 0`; use in BOTH `prepareCardMapPreviewSync()` and `prepareCardMapPreview()`.
  * In `prepareCardMapPreviewSync()`: when `summary.primaryRegionSummary != null`, use decoded `budgetEnforcedPaths` directly as `activePaths`, derive tight `activeBounds` from decoded `activePaths` (`SpatialRegionEngine.buildNormalizedBounds(allPoints)`, fallback `primarySummary.bounds` — Gate 2 Tip #2), set `regionCount = primarySummary.detectedRegionCount`, `primaryRegionPathsCount = primarySummary.primaryRegionWorkoutCount`, `activeRegionId = "region_0"`, and resolve `primaryRegionName` via `RegionNameResolver.findNearestKnownLocationName(context, primarySummary.center)`. When `summary.primaryRegionSummary == null`, preserve exact legacy `detectRegionsFromPaths()` path.
  * In `prepareCardMapPreview()` (async geocoder): resolve `center = summary.primaryRegionSummary?.center ?: state.bounds?.let { ... }` so `RegionNameResolver.resolvePlaceName(context, center)` matches `PeriodMapScreen` 1:1.
* **Target 2**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodSummaryCard.kt` (`789 -> ~792` LoC).
  * Update `produceState` (`:259-266`) to `produceState(initialValue = PeriodCardMapState(isReady = false), keys = arrayOf(summary.sortKey, summary.workoutIdToPolylineMap.hashCode(), summary.polylines.hashCode(), summary.primaryRegionSummary))` (Gate 2 Tip #1).

### Step 5: Deterministic Behavioral Unit Tests & Locked Incremental Gradle (`TST-PER-054.1`–`.4`)
* **NEW Test File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodPrimaryRegionSelectionTest.kt` (explicit hyphenated `// REQ-PER-046.1..4 / TST-PER-054.1..4` tags, `0` `src/main` reads, `@After` cache/singleton cleanup):
  1. `testMultiRegionExtremaOutliers_selectsTrueHomePrimaryRegionWithTrueCount_tstPer054_1`: `159` Stuttgart bike/run workouts + `2` Highland Council outlier workouts holding `northId`/`westId` -> `enrich()` -> `filterPeriodSummary({BIKE, RUN})` -> `prepareCardMapPreviewSync()` asserts Stuttgart primary bounds, `regionCount == 2`, `primaryRegionPathsCount == 159`, `activeRegionId == "region_0"`, `polylines.size == 8` all in Stuttgart, and 1:1 parity with `SpatialRegionEngine.detectRegions()`.
  2. `testSportToggleRerankingAndIndoorIsolation_updatesPrimaryRegionAndPolylines_tstPer054_2`: `BIKE` dominates Region A (`150` vs `2`), `RUN` dominates Region B (`10` vs `5`), `UNKNOWN` has `12` indoor workouts -> toggles `{BIKE, RUN}` -> `{BIKE}` -> `{RUN}` -> `{UNKNOWN}`, verifying re-ranking to Region B (`10`, `<= 8` newest `RUN` polylines ordered `startTimeS DESC`), indoor `primaryRegionSummary == null` & `polylines == emptyList()`, and `workoutIdToAllPolylinesMap == emptyMap()`.
  3. `testCacheInvalidationOnRegionCountChangeAndCenterGeocodingParity_tstPer054_3`: verifies distinct cache entries when `primaryRegionWorkoutCount` (`159` vs `150`) or `detectedRegionCount` (`2` vs `1`) changes under identical preview polylines, and verifies both `prepareCardMapPreviewSync()` and `prepareCardMapPreview()` pass `primaryRegionSummary.center` to `RegionNameResolver`.
  4. `testHighVolume520Workouts_preservesReqPer039MemoryBoundsAndLegacyFallback_tstPer054_4`: 520 multi-region workouts preserve `workoutIdToPathMap == emptyMap()`, `workoutIdToAllPolylinesMap == emptyMap()`, `workoutIdToPolylineMap.size <= 5`, `polylines.size <= 8`, and synthetic `PeriodSummary` with `primaryRegionSummary == null` preserves legacy `detectRegionsFromPaths()` behavior.
* **Locked Incremental Gradle Command (`Rule 7.5` — NEVER `--rerun-tasks`, NEVER `-PstampGitHash=true`)**:
  ```bash
  python3 tools/parallel_sprint_lock.py --timeout 1800 -- ./gradlew \
    :app:compileDebugKotlin :app:compileDebugUnitTestKotlin \
    :app:testDebugUnitTest --rerun \
    --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodPrimaryRegionSelectionTest" \
    --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodsRepositoryMemoryTest" \
    --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodCardMapStateTest" \
    --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodsViewModelTest" \
    --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.SpatialRegionEngineTest" \
    --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodSummaryCardUiTest" \
    --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodVertexBudgetTest" \
    --tests "*ArchitectureConformanceContractTest" \
    --tests "*DeadCodeEliminationContractTest" \
    --tests "*SourceTextReadingAuditContractTest" \
    :app:assembleDebug
  ```

### Step 6: Living Docs Promotion to `[Implemented]` & Governance Gate
* Promote `REQ-PER-046` & `TST-PER-054` to `[Implemented]` with exact test citations, bump versions + Change History rows, run `--sync-indexes`, run all 4 Python safety linters, and confirm `verify_requirement_governance.py` exits `0` with `0` new compiler warnings.

---

## 5. Requirement-to-Step & Test Traceability Matrix

| Requirement | Plan Step(s) | Target File(s) | Test Case | Test Suite |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-PER-046.1` | Steps 1, 2 | `PeriodData.kt`, `PeriodsRepository.kt` | `TST-PER-054.1` | `PeriodPrimaryRegionSelectionTest` |
| `REQ-PER-046.2` | Step 3 | `PeriodsViewModel.kt` | `TST-PER-054.2` | `PeriodPrimaryRegionSelectionTest`, `PeriodsViewModelTest` |
| `REQ-PER-046.3` | Step 4 | `PeriodMapUtils.kt`, `PeriodSummaryCard.kt` | `TST-PER-054.3` | `PeriodPrimaryRegionSelectionTest`, `PeriodCardMapStateTest`, `PeriodSummaryCardUiTest` |
| `REQ-PER-046.4` | Steps 1–6 | `PeriodData.kt`, `PeriodsRepository.kt`, `PeriodsViewModel.kt`, `PeriodMapUtils.kt`, `PeriodSummaryCard.kt` | `TST-PER-054.4` | `PeriodPrimaryRegionSelectionTest`, `PeriodsRepositoryMemoryTest`, `SpatialRegionEngineTest`, Governance Contracts |
