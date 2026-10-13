# Stage 3: Implementation Plan - ATT-1954: Corridor-Based Route Grouping & Differentiating Thumbnail Zoom

**Ticket**: [ATT-1954](https://rainerblind.atlassian.net/browse/ATT-1954)  
**Sub-task**: [ATT-2428](https://rainerblind.atlassian.net/browse/ATT-2428) (`[Impl-Plan] Corridor-Based Route Grouping & Differentiating Thumbnail Zoom`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*[Epic] Improve Routes*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-40.16`  
**Requirement Mapping**: `REQ-MAP-030`  
**Test Mapping**: `TST-MAP-032`  
**Branch**: `feature/ATT-1954`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Architectural Overview & Boundaries (SWE.2)

```
┌────────────────────────────────────────────────────────┐
│                   UI Layer (Compose)                   │
│   RoutesListScreen.kt  <──> GatewayFilterChipsRow      │
│   RouteCard.kt         <──> RouteMapPreview (Bounds)   │
└───────────────────────────▲────────────────────────────┘
                            │
┌───────────────────────────┴────────────────────────────┐
│                  Domain & Algorithmic                  │
│   RouteCorridorClassifier.kt                           │
│   RouteBoundingBoxCalculator.kt                        │
└───────────────────────────▲────────────────────────────┘
                            │
┌───────────────────────────┴────────────────────────────┐
│                    Preferences                         │
│   TuningPreferencesDataStore.kt / TuningConfig.kt      │
│   (corridorGroupingEnabled, focusedThumbnailZoom)      │
└────────────────────────────────────────────────────────┘
```

---

## 2. Atomic Step-by-Step Sequencing

### Step 1: Localization Parity (9 Locales)
* Add 13 string tokens across all 9 `strings.xml` files:
  - `gateway_all`, `gateway_north`, `gateway_northeast`, `gateway_east`, `gateway_southeast`, `gateway_south`, `gateway_southwest`, `gateway_west`, `gateway_northwest`, `gateway_unknown`
  - `pref_corridor_grouping_title`, `pref_corridor_grouping_desc`
  - `pref_focused_thumbnail_title`, `pref_focused_thumbnail_desc`
* Verify via `TranslationParityTest`.

### Step 2: Tuning Preferences Extension
* Add `corridorGroupingEnabled: Boolean = true` and `focusedThumbnailZoomEnabled: Boolean = true` to `TuningConfig.kt`.
* Add DataStore preferences keys and flows in `TuningPreferencesDataStore.kt`.

### Step 3: Gateway Corridor Classification Engine
* Create `app/src/main/java/com/atrainingtracker/trainingtracker/routes/RouteCorridorClassifier.kt`:
  - `GatewayDirection` enum with string resources.
  - Initial heading calculation based on forward vector up to 1000m.
  - `classifyGatewayHeading(path: List<PathPoint>): GatewayDirection`.
  - `groupRoutesByCorridor(routes: List<RouteWithPath>): Map<GatewayDirection, List<RouteWithPath>>`.
* Create unit tests in `app/src/test/java/com/atrainingtracker/trainingtracker/routes/RouteCorridorClassifierTest.kt`.

### Step 4: Differentiating Bounding Box Calculator
* Create `app/src/main/java/com/atrainingtracker/trainingtracker/routes/RouteBoundingBoxCalculator.kt`:
  - `RouteBoundingBox` data class.
  - `calculateGlobalBounds(path: List<PathPoint>): RouteBoundingBox`.
  - `calculateDifferentiatingBounds(route: RouteWithPath, commonCorridorRadiusMeters: Double = 1500.0): RouteBoundingBox`.
  - Applies $15\%$ padding and falls back to global bounds when unique loop is negligible.
* Create unit tests in `app/src/test/java/com/atrainingtracker/trainingtracker/routes/RouteBoundingBoxCalculatorTest.kt`.

### Step 5: Routes List UI Integration
* Update `RoutesListScreen.kt`:
  - Inject `TuningPreferencesDataStore`.
  - When `corridorGroupingEnabled` is active and routes map to $> 1$ distinct corridor, render `GatewayFilterChipsRow`.
  - When filtering by corridor, filter displayed routes accordingly.
  - When `focusedThumbnailZoomEnabled` is active, compute differentiating bounding box and pass to `RouteCard` / map preview.

### Step 6: Verification & Clean-Room Regression
* Run targeted tests: `RouteCorridorClassifierTest`, `RouteBoundingBoxCalculatorTest`, `TranslationParityTest`.
* Run full suite regression: `./gradlew testDebugUnitTest`.

---

## 3. Invariants & Guardrails
- SQLite schema for `Routes.db` (v10) must remain completely untouched.
- 100% offline pure calculations with zero network dependencies.
- Disabling preferences must cleanly preserve the baseline flat list and full bounding box view.
- 100% full-suite unit test pass rate across all 1,770+ unit tests.
