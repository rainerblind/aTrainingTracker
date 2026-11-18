# Stage 1 Analysis: ATT-8736 - Seed PathPreviewMap Camera Bounds Immediately and Memoize Track Decoding in WorkoutSummary

**DOC-ID**: `DOC-ANA-ATT-8736` | **Ticket**: [ATT-8736](https://atrainingtracker.atlassian.net/browse/ATT-8736) (`2 SP`)  
**Parent Epic**: [ATT-68](https://atrainingtracker.atlassian.net/browse/ATT-68) (*Workouts & Aftermath (WorkoutSummaries, Details & Graphs)*) | **Target Release**: `V5.0.0`  
**Sprint**: `sprint/2026-11-18_01_Früh` | **Branch**: `feature/ATT-8736` (`22853065`) | **Author**: `--as agent1`

---

## 1. Problem Statement & Motivation
When scrolling the `Einheiten` workout list (`WorkoutList.kt:123` -> `WorkoutSummary.kt`), `PathPreviewMap` initially renders a zoom-`0` world map centered at `(0.0, 0.0)` and delays zooming onto historical workouts—or stays stuck unzoomed. Live inspection (`WorkoutSummaries.db`, 1,904 workouts back to 2012) confirmed valid `mapPolyline` and `boundMinLat`/`boundMinLng`/`boundMaxLat`/`boundMaxLng` already exist on disk.

## 2. Independent Peer Review & Verification
Audited `PathPreviewMap.kt`, `WorkoutSummary.kt`, `MapModels.kt`, `ATrainingTrackerMap.kt`, `WorkoutData.kt`, `WorkoutDataMapper.kt`, `WorkoutRepository.kt`, all 7 `PathPreviewMap` call sites, contract tests, `app/detekt-baseline.xml`, and living doc shards on `22853065`:

* **Root Cause 1 — Unseeded Camera & `isMapLoaded` Gate (`PathPreviewMap.kt:67,79-81,154-184`)**:
  * `rememberCameraPositionState()` (`L67`) uses the default `(0.0, 0.0), zoom = 0f`, and `googleMapOptionsFactory` (`L79-81`) configures `GoogleMapOptions().liteMode(true)` without `.camera(...)`. Play Services Lite Mode therefore fetches/renders a zoom-`0` snapshot before `LaunchedEffect` (`L154`) waits on `isMapLoaded` (`L91,155`) to apply `CameraUpdateFactory.newLatLngBounds(bounds, 20)`.
  * Unlike `ATrainingTrackerMap.kt:237-245` (`ATT-440`, which applies `MapEffect` immediately once the `GoogleMap` instance exists), `PathPreviewMap` stalls behind Play Services' Lite Mode renderer queue during fast scrolling.
* **Root Cause 2 — Silent Exception Swallow Without Layout Retry (`PathPreviewMap.kt:172-181`)**:
  * `try { cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(bounds, 20)) } catch (e: Exception) { }` (`L172-181`, baselined as `SwallowedException` / `TooGenericExceptionCaught` in `app/detekt-baseline.xml:8478,8602`) silently drops transient `IllegalStateException` when `MapView` size is `0`, leaving the thumbnail permanently at zoom `0`.
  * Tracking non-zero container size via `Modifier.onSizeChanged { ... }` (avoiding `BoxWithConstraints` / `SubcomposeLayout` in case any caller embeds inside `IntrinsicSize.Min`, per `ATT-8748` lesson) + `MapEffect(bounds, containerSize, isMapLoaded)` ensures immediate framing and deterministic retry if layout settles after `GoogleMap` creation.
* **Root Cause 3 — Un-Memoized `toMapTrack()` & Omitted `targetBounds` (`WorkoutSummary.kt:367-378,753-764`)**:
  * Both `WorkoutSectionType.MAP` (`L367-368`) and unused `private fun WorkoutMediaSection` (`L736,753-754`, baselined `UnusedPrivateMember` at `app/detekt-baseline.xml:8748`) invoke `workoutData.toMapTrack()` directly in composition without `remember(...)` and omit `targetBounds`.
  * Progressive 50-item batch emissions via `emitMergedWorkouts` (`WorkoutRepository.kt:3792,3918,3923`) and `produceState` loads (`WorkoutSummary.kt:137-210`) trigger repeated recompositions on the main thread, re-running `PolyUtil.decode` and `NumericalEncodingUtils.decodeDoubles` (`MapModels.kt:466-519`).
  * **Key-Completeness Finding**: `WorkoutData.toMapTrack()` (`MapModels.kt:510-519`) reads `id`, `bSportType`, `mapPolyline`, `encodedAltitudes`, `encodedDistances`, `minLat`, `minLng`, `maxLat`, and `maxLng` (`type = TrackType.BEST` is fixed). Memoization keys MUST include all read fields (`workoutData.id, workoutData.bSportType, workoutData.mapPolyline, workoutData.encodedAltitudes, workoutData.encodedDistances, workoutData.minLat, workoutData.minLng, workoutData.maxLat, workoutData.maxLng`) so edits or bounds backfills invalidate cleanly.
* **Bounds & Math Edge Cases**:
  * `WorkoutDataMapper.kt:191-194,696-699` sets `minLat`/`minLng`/`maxLat`/`maxLng` to `null` when DB columns are SQL `NULL`. Before constructing `LatLngBounds`, validate all 4 are non-null, finite (`Double.isFinite`), in `[-90.0, 90.0]` / `[-180.0, 180.0]`, and `minLat <= maxLat` (plus fallback to `MappablePath.minLat..maxLng` or `allPaths` coordinates).
  * When estimating Web Mercator zoom from `LatLngBounds` (`256 dp` world tile base, aspect-aware via container dp/default), clamp zoom (e.g. `[1f, 17f]`) and handle zero-span single-point bounds (`latSpan == 0 && lngSpan == 0`) with a safe default zoom (e.g. `15f`) to avoid `ln(0)` / `-Infinity`, and handle antimeridian longitude wrap (`sw.longitude > ne.longitude`).
* **All 7 `PathPreviewMap` Callers & Contract-Test Constraints**:

| Caller (`file:line`) | Parameters Passed | Impact & Compatibility Notes |
| :--- | :--- | :--- |
| `WorkoutSummary.kt:367` | `path = workoutData.toMapTrack()`, `start/end/apex` | Memoize `toMapTrack()` + pass validated `targetBounds` |
| `WorkoutSummary.kt:753` | `WorkoutMediaSection` (`private`) | Memoize `toMapTrack()` + pass validated `targetBounds` |
| `RouteItem.kt:95` | `path = MapRoute(...)`, `targetBounds = null` | Must keep `targetBounds = null` (`ATT-2463`/`TST-MAP-032`); auto-gains immediate seed from `allPaths` |
| `CropWorkoutScreen.kt:209` | `path = mapTrack`, dynamic `pathPoints` | `MapEffect` must re-frame when `bounds` change on slider trim |
| `WorkoutClusterSplitSheet.kt:141` | `paths = splitVariantPaths`, `targetBounds = clusterBounds` | `WorkoutClusterSplitMapPreviewTest.kt:71-80` asserts exact source strings in `PathPreviewMap.kt`: `paths: List<MappablePath> = emptyList()`, `path: MappablePath? = null`, `allPaths`, `allPaths.forEach`, `boundsBuilder.include(it)` |
| `CreateLapOnMapSheet.kt:202` | `path = mapTrack`, `apexIconRes = ic_lap_split_scissors` | `WorkoutLapsIconContractTest.kt:83,87` asserts `apexIconRes: Int = R.drawable.ic_distance` & `createSensorMarker(context, apexIconRes, apexColor)` |
| `MoveLapBoundaryOnMapSheet.kt:259` | `path = mapTrack`, `apexIconRes = ic_lap_move_boundary` | Preserved unchanged |

* **Non-Regression of `ATT-8748` / `ATT-8749` & Contract Guards**:
  * `KnownLocationsScreen.kt:729-765` (`REQ-UI-2785`, `ATT-8749`) and `WorkoutClusterComponents.kt:668-695` (`REQ-UI-493`, `ATT-8748`) render independent `GoogleMap(..., liteMode(true))` `175×120 dp` thumbnails and do NOT call `PathPreviewMap` — zero regression risk.
  * `WorkoutRepository.kt` (`5,048` LoC `<= 5,117` ceiling in `ArchitectureConformanceContractTest.kt:118`) stays untouched. Zero inline `Color(0x...)` (`ThemeColorCentralizationAuditTest`), zero public `@Preview`s (`DeadCodeEliminationContractTest`), zero new `src/main` test reads (`SourceTextReadingAuditContractTest`), and `Rule 8.5` Light/Dark `@Preview` PNG check in Stage 5.
  * Shard headroom: `req_maps_navigation.md` (`2,802/4,000`) & `tst_maps_navigation.md` (`1,294/4,000`), or `req_aftermath_reviews.md` (`443/4,000`) & `tst_aftermath_reviews.md` (`189/4,000`).

## 3. User Scope Grounding (ATT-1250)
* **In-Scope (`2 SP` confirmed — single-feature/two-file scope, no split needed)**: Synchronous bounds & aspect-aware initial `CameraPosition` seeding (`rememberCameraPositionState` + `GoogleMapOptions().liteMode(true).camera(...)`), non-zero layout tracking (`onSizeChanged`) + immediate `MapEffect` `newLatLngBounds` framing with `IllegalStateException` retry in `PathPreviewMap.kt`; key-complete `remember` memoization of `toMapTrack()` and `targetBounds` validation in `WorkoutSummary.kt`; pure JVM unit tests + Compose UI/contract tests + living docs.
* **Out-of-Scope**: Modifying `WorkoutRepository.kt`, `RouteItem.kt`, `KnownLocationsScreen.kt` (`ATT-8749`), `WorkoutClusterComponents.kt` (`ATT-8748`), or `ATrainingTrackerMap.kt`.

### Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: `REQ-STB-010` (`docs/requirements/req_process_stability_part1.md:1186-1208`, `PathPreviewMap.kt`), `REQ-MAP-023` (`DarkMapAntiFlashOverlay` / `DarkMapStyle`), `REQ-UI-505` (`PathPreviewMap` multi-path/bounds), `REQ-UI-210` (`WorkoutSummary.kt`), and `REQ-PRO-130.1` (`docs/architecture.md:324` Lite Mode budget).
* **Historical Origin & Commit Trace**: `d81115f4` renamed `TrackOrSegmentOnMap` to `PathPreviewMap`; `8369dd5a` (`ATT-1246`/`ATT-1271`, `REQ-STB-010`) introduced `GoogleMapOptions().liteMode(true)` to eliminate RenderThread ANRs; `ea954e51` (`ATT-6318`, `REQ-UI-505`) added `paths` and `targetBounds` to `PathPreviewMap`; `bcfed94d` (`ATT-5480`) added `apexIconRes`.
* **Root Reason for Existing Formulation**: `LaunchedEffect(..., isMapLoaded)` + `try/catch` guarded against `newLatLngBounds` throwing `IllegalStateException` before `MapView` layout completed, while `liteMode(true)` prevented `GLSurfaceView` contention in `LazyColumn`. Neither seeded `CameraPosition` before first snapshot nor retried after zero-size layout failure.
* **Preservation of Core Invariants**: Preserve `GoogleMapOptions().liteMode(true)` (`REQ-STB-010`, `REQ-PRO-130.1`, `MapPreviewLiteModeTest`), `DarkMapStyle.resolveMapProperties` & `DarkMapAntiFlashOverlay` (`DarkMapStyleResolutionTest`, `DarkMapAntiFlashOverlayTest`), `MapUiSettings` non-interactive flags, `20 px` `newLatLngBounds` padding, `RouteWaypointsLayer` (`RouteItemWaypointContractTest`), `WorkoutClusterSplitMapPreviewTest` source contracts, `WorkoutLapsIconContractTest` marker contracts, and `RouteItem.kt` `targetBounds = null`.

## Change Impact Analysis
* **Affected Requirements**: Refines `REQ-STB-010.4` (spatial bounds framing in Lite Mode) and `REQ-UI-210` via net-new `REQ-MAP-*` / `TST-MAP-*` in `docs/requirements/req_maps_navigation.md` (`2,802/4,000` lines) and `docs/tests/tst_maps_navigation.md` (`1,294/4,000` lines), respecting `<= 4,000`-line shard limits and symmetric `REQ<->TST` parity.
* **Affected Architecture Elements**: `PathPreviewMap.kt` (synchronous bounds/camera estimator helper + `MapEffect` + `onSizeChanged`), `WorkoutSummary.kt` (`remember` memoization of `toMapTrack()` & validated `targetBounds` in `WorkoutSummary` and `WorkoutMediaSection`). Zero ViewModel, Repository, DB schema, DataStore, or localization XML (`strings*.xml`) changes.
* **Verification Impact & Test Surface**: Add pure JVM tests for synchronous bounds resolution, invalid/zero-span/antimeridian guardrails, and Web Mercator initial zoom estimation, plus Compose/contract verification for `WorkoutSummary` memoization & `PathPreviewMap` options/layout retry (with `0` new `src/main` reads); run targeted map/summary/contract test suites and Stage 5 Light/Dark `@Preview` PNG audit (`Rule 8.5`).
* **Risk Assessment & Invariants**: **LOW** — localized to two Compose UI files (`PathPreviewMap.kt`, `WorkoutSummary.kt`). Primary risks are degenerate/invalid DB bounds (`minLat > maxLat`, `NaN`, single-point `0`-span) and breaking `WorkoutClusterSplitMapPreviewTest` source-string checks, both directly mitigated by strict bounds validation and preserving exact parameter/builder constructs.

## 5. High-Level Solution
Extract pure helpers to (1) resolve `LatLngBounds` synchronously from `targetBounds`, valid `MappablePath` bounds, or `allPaths`/`start`/`end`/`apex` points and (2) compute an aspect-aware Web Mercator `CameraPosition` to seed both `rememberCameraPositionState` and `GoogleMapOptions().liteMode(true).camera(...)`. Replace the `isMapLoaded`-gated `LaunchedEffect` with `onSizeChanged`-tracked `MapEffect(bounds, mapSize, isMapLoaded, retryTrigger)` applying `newLatLngBounds(..., 20)` immediately with explicit `IllegalStateException` retry. In `WorkoutSummary.kt`, memoize `workoutData.toMapTrack()` with full read-field keys and pass validated persisted `targetBounds`.
