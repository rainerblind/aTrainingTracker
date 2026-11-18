# Stage 2: Requirement & Test Specification - ATT-8736

**DOC-ID**: `DOC-TST-ATT-8736` | **Ticket**: [ATT-8736](https://atrainingtracker.atlassian.net/browse/ATT-8736) (`2 SP`) | **Parent Epic**: [ATT-68](https://atrainingtracker.atlassian.net/browse/ATT-68)  
**Target Release**: `V5.0.0` | **Sprint**: `sprint/2026-11-18_01_Früh` | **Branch**: `feature/ATT-8736`  
**Requirement Mapping**: `REQ-MAP-068` | **Test Spec ID**: `TST-MAP-071` | **System Requirement(s)**: `SYSREQ-TRK-004` | **Date**: 2026-11-18

---

## 1. Requirement Specification (`REQ-MAP-068` `[Planned]`)
* **Attributes**: Priority: `High` | Type: `Functional` | Verification Method: `Unit`, `Integration`, `On-Device Manual` | **Localization Parity**: `N/A - no UI strings`
* **Implementation**: `PathPreviewMap.kt`, `WorkoutSummary.kt`
* **Rationale**: `PathPreviewMap` initializes at `(0.0, 0.0), zoom = 0f`, omits `.camera(...)` on `GoogleMapOptions().liteMode(true)`, gates `newLatLngBounds` behind `isMapLoaded` without retry on zero-size `IllegalStateException`, and `WorkoutSummary` invokes `workoutData.toMapTrack()` un-memoized without passing persisted `targetBounds`.
1. **`REQ-MAP-068.1` (Synchronous Bounds Resolution & Camera Seeding)**: `PathPreviewMap` SHALL synchronously resolve `LatLngBounds` (`targetBounds` -> valid `MappablePath` bounds -> composite `allPaths`/`start`/`end`/`apex`), reject `null`, non-finite, out-of-range (`[-90, 90]`/`[-180, 180]`), or `minLat > maxLat` bounds, and handle antimeridian wrap (`sw.longitude > ne.longitude`). An aspect-aware Web Mercator helper (`256 dp` tile base) SHALL compute center and clamped zoom (`[1f, 17f]`, safe default `15f` for zero-span single points avoiding `ln(0)`) to seed both `GoogleMapOptions().liteMode(true).camera(...)` and `rememberCameraPositionState`, eliminating the `(0.0, 0.0), zoom = 0f` flash.
2. **`REQ-MAP-068.2` (`MapEffect` + `onSizeChanged` Framing & Retry)**: `PathPreviewMap` SHALL track non-zero container size via `Modifier.onSizeChanged` (avoiding `BoxWithConstraints`/`SubcomposeLayout` per `ATT-8748` `IntrinsicSize.Min` safety) and replace `LaunchedEffect` with `MapEffect(bounds, containerSize, isMapLoaded, retryTrigger)` applying `newLatLngBounds(bounds, 20)` immediately with bounded retry on transient zero-size `IllegalStateException` and re-framing on dynamic `bounds` changes.
3. **`REQ-MAP-068.3` (`WorkoutSummary` 9-Key Memoization & `targetBounds`)**: Both `WorkoutSectionType.MAP` and `WorkoutMediaSection` SHALL memoize `workoutData.toMapTrack()` via `remember` keyed on all 9 read fields (`id`, `bSportType`, `mapPolyline`, `encodedAltitudes`, `encodedDistances`, `minLat`, `minLng`, `maxLat`, `maxLng`) and pass validated persisted `targetBounds` to `PathPreviewMap`.
4. **`REQ-MAP-068.4` (Preserved Contracts & Guardrails)**: Preserve `liteMode(true)` (`REQ-STB-010`/`REQ-PRO-130.1`), `DarkMapStyle`/`DarkMapAntiFlashOverlay`, `20 px` padding, `RouteItem.kt` (`targetBounds = null`), `WorkoutClusterSplitMapPreviewTest` tokens (`paths: List<MappablePath> = emptyList()`, `path: MappablePath? = null`, `allPaths`, `allPaths.forEach`, `boundsBuilder.include(it)`), `WorkoutLapsIconContractTest` tokens (`apexIconRes: Int = R.drawable.ic_distance`, `createSensorMarker(context, apexIconRes, apexColor)`), `WorkoutRepository.kt <= 5,117` LoC untouched, `0` inline `Color(0x...)`, `private` `@Preview`s only, `0` new `src/main` test reads, and Stage 5 `Rule 8.5` PNG capture.

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Refines `REQ-STB-010.4` (`PathPreviewMap.kt`), `REQ-MAP-023`, `REQ-UI-505`, `REQ-UI-210` (`WorkoutSummary.kt`), and `REQ-PRO-130.1`.
2. *Historical Origin & Commit Trace*: `d81115f4` (`PathPreviewMap` rename), `8369dd5a` (`ATT-1246`/`ATT-1271`, `liteMode(true)`), `ea954e51` (`ATT-6318`, `paths`/`targetBounds`), `bcfed94d` (`ATT-5480`, `apexIconRes`).
3. *Root Reason for Existing Formulation*: `LaunchedEffect(..., isMapLoaded)` + `try/catch` avoided `IllegalStateException` prior to `MapView` layout while `liteMode(true)` prevented `GLSurfaceView` contention, without initial `CameraPosition` seeding or zero-size layout retry.
4. *Preservation of Core Invariants*: All Lite Mode flags, dark-map anti-flash/styling, `20 px` padding, `RouteItem.kt` `targetBounds = null`, contract-test tokens, and `WorkoutRepository.kt` LoC ceiling remain intact.

## 2. Acceptance Criteria (Given-When-Then)
* *Given* valid polyline/bounds (or single-point / antimeridian / invalid DB bounds), *When* `PathPreviewMap` composes before `onMapLoaded`, *Then* `GoogleMapOptions().liteMode(true).camera(...)` and `rememberCameraPositionState` initialize at the validated center and clamped zoom `[1f, 17f]` (zero `(0.0, 0.0), zoom = 0f` flash).
* *Given* zero-size initial layout before `onSizeChanged` settles, *When* `MapEffect` applies `newLatLngBounds(bounds, 20)` and retries on transient `IllegalStateException`, *Then* the map frames cleanly and re-frames on `bounds` changes.
* *Given* `WorkoutSummary` / `WorkoutMediaSection` recomposes with unchanged vs. changed 9-key fields, *When* `remember(...)` evaluates, *Then* `toMapTrack()` reuses cached output on unrelated recompositions, invalidates on any of the 9 keys, and passes validated `targetBounds`.

## 3. Test Specification (`TST-MAP-071`)
* **`TST-MAP-071.1` (`PathPreviewMapBoundsTest`)**: Unit-verify synchronous bounds priority, invalid/null/`NaN`/`minLat > maxLat`/out-of-range rejection, antimeridian wrap, and single-point `ln(0)` guard (`15f`).
* **`TST-MAP-071.2` (`PathPreviewMapBoundsTest`)**: Unit-verify aspect-aware Web Mercator zoom clamping `[1f, 17f]` and `GoogleMapOptions().liteMode(true).camera(...)` seeding parity.
* **`TST-MAP-071.3` (`PathPreviewMapBoundsTest`)**: Verify `Modifier.onSizeChanged` non-zero layout tracking (no `BoxWithConstraints`/`SubcomposeLayout`), `MapEffect` `newLatLngBounds(..., 20)`, bounded `IllegalStateException` retry, and dynamic re-framing.
* **`TST-MAP-071.4` (`WorkoutSummaryMapMemoizationTest`)**: Verify 9-key `remember` memoization/invalidation in `WorkoutSectionType.MAP` & `WorkoutMediaSection` plus validated `targetBounds` forwarding (`0` new `src/main` reads; `N/A - no UI strings`).
* **`TST-MAP-071.5` (Regression & `Rule 8.5`)**: Verify `WorkoutClusterSplitMapPreviewTest`, `WorkoutLapsIconContractTest`, `DarkMapAntiFlashOverlayTest`, `DarkMapStyleResolutionTest`, `MapPreviewLiteModeTest`, `RouteItemWaypointContractTest`, `ThemeColorCentralizationAuditTest`, `DeadCodeEliminationContractTest`, `SourceTextReadingAuditContractTest`, `ArchitectureConformanceContractTest`, plus Stage 5 Light/Dark `@Preview` PNG capture.

## 4. Traceability Matrix
| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-MAP-071.1` | Unit (Bounds/Math) | `PathPreviewMap` bounds helpers | `REQ-MAP-068.1` | Planned |
| `TST-MAP-071.2` | Unit (Camera/Options) | `PathPreviewMap` camera estimator | `REQ-MAP-068.1` | Planned |
| `TST-MAP-071.3` | Compose/Unit (Layout Retry) | `PathPreviewMap` (`onSizeChanged`/`MapEffect`) | `REQ-MAP-068.2` | Planned |
| `TST-MAP-071.4` | Compose UI (Memoization) | `WorkoutSummary`, `WorkoutMediaSection` | `REQ-MAP-068.3`, `.4` | Planned |
| `TST-MAP-071.5` | Contracts & `Rule 8.5` PNG | Map & Governance Contract Suites | `REQ-MAP-068.4`, `SYSREQ-TRK-004` | Planned |
