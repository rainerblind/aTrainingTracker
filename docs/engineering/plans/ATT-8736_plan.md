# Stage 3: Implementation Plan - ATT-8736: Seed PathPreviewMap Camera Bounds Immediately and Memoize Track Decoding in WorkoutSummary

**DOC-ID**: `DOC-PLN-ATT-8736` | **Ticket**: [ATT-8736](https://atrainingtracker.atlassian.net/browse/ATT-8736) (`2 SP`) | **Parent Epic**: [ATT-68](https://atrainingtracker.atlassian.net/browse/ATT-68)  
**Target Release**: `V5.0.0` | **Active Sprint**: `sprint/2026-11-18_01_Früh` | **Branch**: `feature/ATT-8736`  
**Requirement Mapping**: `REQ-MAP-068` (`REQ-MAP-068.1`–`REQ-MAP-068.4`) | **Test Mapping**: `TST-MAP-071` (`TST-MAP-071.1`–`TST-MAP-071.5`)  
**Author**: `--as agent1` | **Date**: 2026-11-18

---

## 1. Traceability, Layering & System Invariants
* **Requirements & Tests**: `REQ-MAP-068` (`docs/requirements/req_maps_navigation.md`) $\leftrightarrow$ `TST-MAP-071` (`docs/tests/tst_maps_navigation.md`); `SYSREQ-TRK-004`.
* **Architecture Layering (SWE.2)**: Pure Compose UI + pure JVM geometry helpers in `ui/map/PathPreviewMap.kt` and `ui/aftermath/workoutlist/WorkoutSummary.kt`. Zero ViewModel, Repository (`WorkoutRepository.kt <= 5,117` LoC untouched), SQLite, DataStore, or `strings*.xml` changes (`docs/architecture.md` & `detailed_design/` unaffected).
* **Preserved Invariants**:
  1. `GoogleMapOptions().liteMode(true)` (`REQ-STB-010`, `REQ-PRO-130.1`, `MapPreviewLiteModeTest`), `DarkMapStyle.resolveMapProperties` & `DarkMapAntiFlashOverlay` (`DarkMapStyleResolutionTest`, `DarkMapAntiFlashOverlayTest`), `MapUiSettings` non-interactive flags, `20 px` padding, and `RouteWaypointsLayer` (`RouteItemWaypointContractTest`).
  2. Contract-tested source tokens in `PathPreviewMap.kt`: `paths: List<MappablePath> = emptyList()`, `path: MappablePath? = null`, `allPaths`, `allPaths.forEach`, `boundsBuilder.include(it)` (`WorkoutClusterSplitMapPreviewTest.kt:71-80`), and `apexIconRes: Int = R.drawable.ic_distance`, `createSensorMarker(context, apexIconRes, apexColor)` (`WorkoutLapsIconContractTest.kt:83,87`).
  3. Caller preservation: `RouteItem.kt` keeps `targetBounds = null` (`ATT-2463`/`TST-MAP-032`); `CropWorkoutScreen`, `WorkoutClusterSplitSheet`, `CreateLapOnMapSheet`, `MoveLapBoundaryOnMapSheet`, `KnownLocationsScreen` (`ATT-8749`), and `WorkoutClusterComponents` (`ATT-8748`) remain unaffected.
  4. Code/Test Hygiene: `0` inline `Color(0x...)` in composables (`ThemeColorCentralizationAuditTest`), `private` `@Preview`s only (`DeadCodeEliminationContractTest`), `0` new `src/main` reads in tests (`SourceTextReadingAuditContractTest`), and `Rule 8.5` Light/Dark PNG verification.

---

## 2. UI Consistency (`docs/design_guidelines.md`)
* **Canvas & Anti-Flash (§5.4, §6)**: Retain `TTColor.DarkMapCanvas` / `Color.White` backing + `DarkMapAntiFlashOverlay`, and render a `LocalInspectionMode.current` schematic polyline/marker preview so Light/Dark `@Preview`s & Robolectric PNG captures (`Rule 8.5`) display cleanly. No new UI strings (`Localization Parity: N/A - no UI strings`), buttons, or settings controls.
* **Layout Safety (`ATT-8748` lesson)**: Measure container dimensions via `Modifier.onSizeChanged { containerSize = it }` — never `BoxWithConstraints` (`SubcomposeLayout`) — so `PathPreviewMap` remains safe even inside `IntrinsicSize.Min` parents.

---

## 3. Atomic Step Sequence (Stage 4 Construction — Post-Gate 3 `PASS` Only per `Rule 4.1`)

### Step 1: Synchronous Bounds, Web Mercator Camera Seeding & `MapEffect` Retry (`PathPreviewMap.kt` — `REQ-MAP-068.1`, `.2`, `.4`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/PathPreviewMap.kt`
* **Changes**:
  1. Add named constants (`PREVIEW_BOUNDS_PADDING_PX = 20`, `WORLD_TILE_DP = 256.0`, `MIN_ESTIMATED_ZOOM = 1f`, `MAX_ESTIMATED_ZOOM = 17f`, `SINGLE_POINT_DEFAULT_ZOOM = 15f`, `MAX_CAMERA_RETRY_ATTEMPTS = 3`, `CAMERA_RETRY_DELAY_MS = 16L`, `DEFAULT_PREVIEW_WIDTH_DP = 360f`, `DEFAULT_PREVIEW_HEIGHT_DP = 200f`) and pure `internal` helpers:
     * `buildValidatedBounds(minLat, minLng, maxLat, maxLng)`: rejects `null`, `!isFinite()`, lat `!in -90.0..90.0`, lng `!in -180.0..180.0`, or `minLat > maxLat`; returns `LatLngBounds` (allowing antimeridian `minLng > maxLng`).
     * `validateBounds(bounds: LatLngBounds?)` & `resolvePreviewBounds(targetBounds, allPaths, start, end, apex)`: resolves synchronously in priority order (validated `targetBounds` -> valid `MappablePath` bounds -> composite `allPaths.forEach` + `boundsBuilder.include(it)` + markers).
     * `estimateInitialCameraPosition(bounds, widthDp, heightDp, paddingPx, density)`: Web Mercator aspect-aware zoom (`256 dp` tile base) clamped to `[1f, 17f]`, antimeridian-safe center/span, and single-point zero-span fallback `15f` (no `ln(0)` / `-Infinity`).
     * `createLiteModeMapOptions(initialCameraPosition)`: builds `GoogleMapOptions().liteMode(true)` with `.camera(initialCameraPosition)` when non-null.
  2. Compute `allPaths`, `actualStart`, `actualEnd`, `actualApex`, `resolvedBounds`, and `initialCameraPosition` before `rememberCameraPositionState`. Seed `rememberCameraPositionState { initialCameraPosition?.let { position = it } }`, sync `cameraPositionState.position` if `resolvedBounds` updates while unpositioned (or on recycled `LazyColumn` items), and pass `createLiteModeMapOptions(initialCameraPosition)` in `googleMapOptionsFactory`.
  3. Track `containerSize` via `Modifier.onSizeChanged` on outer `Box`; add `LocalInspectionMode.current` schematic preview branch; replace `LaunchedEffect(..., isMapLoaded)` + silent `catch (e: Exception)` with `MapEffect(resolvedBounds, containerSize, isMapLoaded, retryCount)` running `map.moveCamera(CameraUpdateFactory.newLatLngBounds(resolvedBounds, PREVIEW_BOUNDS_PADDING_PX))` as soon as `containerSize.width > 0 && containerSize.height > 0` and retrying boundedly (`retryCount < MAX_CAMERA_RETRY_ATTEMPTS` after `delay(CAMERA_RETRY_DELAY_MS)`) on `IllegalStateException`.
  4. Add `private fun` Light & Dark `@Preview` composables (`Rule 8.5`).

### Step 2: 9-Key `toMapTrack()` Memoization & Validated `targetBounds` (`WorkoutSummary.kt` — `REQ-MAP-068.3`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummary.kt`
* **Changes**:
  1. Extract shared `internal fun WorkoutPathPreviewMap(workoutData, startTitle, endTitle, modifier, onMapClick, trackDecoder = { it.toMapTrack() })` memoizing `trackDecoder(workoutData)` with `remember(workoutData.id, workoutData.bSportType, workoutData.mapPolyline, workoutData.encodedAltitudes, workoutData.encodedDistances, workoutData.minLat, workoutData.minLng, workoutData.maxLat, workoutData.maxLng)` and building `targetBounds = remember(workoutData.minLat, workoutData.minLng, workoutData.maxLat, workoutData.maxLng) { buildValidatedBounds(...) }`.
  2. Call `WorkoutPathPreviewMap(...)` from both `WorkoutSectionType.MAP` (`L367`) and `private fun WorkoutMediaSection` (`L753`), keeping `WorkoutSummary(...)` signature unchanged.

### Step 3: Detekt Baseline Hygiene (`PathPreviewMap.kt`)
* **File**: `app/detekt-baseline.xml`
* **Changes**: Remove obsolete `PathPreviewMap.kt` baseline entries (`MagicNumber$20`, `SwallowedException`, `TooGenericExceptionCaught`, and `LongMethod`/`CyclomaticComplexMethod` if resolved by helper extraction) verified via Detekt.

### Step 4: Pure Unit & Behavioral Compose UI Tests + `Rule 8.5` Preview Capture (`TST-MAP-071.1`–`.4`)
* **Files** (**0** `src/main` file reads per `REQ-PRO-049.1`):
  * Create `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/PathPreviewMapBoundsTest.kt` (`TST-MAP-071.1`–`.3`): verify bounds priority, `null`/`NaN`/`Infinity`/out-of-range/`minLat > maxLat` rejection, antimeridian wrap, single-point `15f` zoom guard, aspect-aware `[1f, 17f]` clamping, `GoogleMapOptions().liteMode(true).camera(...)` parity, `onSizeChanged` non-zero tracking (no `BoxWithConstraints`), bounded `IllegalStateException` retry, and Light/Dark `@Preview` PNG capture (`docs/engineering/walkthroughs/img/ATT-8736_path_preview_map.png`).
  * Create `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummaryMapMemoizationTest.kt` (`TST-MAP-071.4`): Robolectric `createComposeRule()` test verifying `WorkoutPathPreviewMap` & `WorkoutSummary` memoize `toMapTrack()` across unrelated recompositions, invalidate on each of the 9 keys, and forward validated `targetBounds`.

### Step 5: Batched Tier 1 Regression Verification (`TST-MAP-071.5`, `Rule 7.5`)
* **Command** (single batched run under `python3 tools/parallel_sprint_lock.py --timeout 1800`):
  `./gradlew :app:testDebugUnitTest --max-workers=1 --rerun-tasks --tests "com.atrainingtracker.trainingtracker.ui.map.PathPreviewMapBoundsTest" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutSummaryMapMemoizationTest" --tests "com.atrainingtracker.trainingtracker.ui.clusters.WorkoutClusterSplitMapPreviewTest" --tests "com.atrainingtracker.trainingtracker.ui.components.workoutlaps.WorkoutLapsIconContractTest" --tests "com.atrainingtracker.trainingtracker.ui.map.DarkMapAntiFlashOverlayTest" --tests "com.atrainingtracker.trainingtracker.ui.map.DarkMapStyleResolutionTest" --tests "com.atrainingtracker.trainingtracker.ui.map.MapPreviewLiteModeTest" --tests "com.atrainingtracker.trainingtracker.ui.routes.RouteItemWaypointContractTest" --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.DeadCodeEliminationContractTest" --tests "com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest"`
* **Rollback**: Isolated to `feature/ATT-8736`; `sprint/2026-11-18_01_Früh` untouched until Stage 6 `PASS`.
