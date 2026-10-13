# Stage 1: Problem Domain & Root Cause Analysis - ATT-1954: Corridor-Based Route Grouping & Differentiating Thumbnail Zoom

**Ticket**: [ATT-1954](https://rainerblind.atlassian.net/browse/ATT-1954)  
**Sub-task**: [ATT-2426](https://rainerblind.atlassian.net/browse/ATT-2426) (`[Analysis] Corridor-Based Route Grouping & Differentiating Thumbnail Zoom`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*[Epic] Improve Routes*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-40.16`  
**Requirement Mapping**: `REQ-MAP-030`  
**Test Mapping**: `TST-MAP-032`  
**Branch**: `feature/ATT-1954`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Problem Description & Operational Context

When endurance athletes view their saved routes list (`RoutesListScreen.kt`), routes departing from home or local trailheads share identical departure corridors (first 1–3 km) and return funnels (last 1–3 km). 

Currently, route card preview thumbnails render the global bounding box encompassing the entire polyline. This forces the unique middle loop (the defining exploratory segment of the workout) to be compressed into a tiny, indistinct squiggle in the center of the card. Furthermore, a flat list of 20+ routes starting from the same base lacks structural organization by exit corridor or direction. 

However, some users prefer the global full-route overview by default. Therefore, athletes need an intelligent system that:
1. Detects shared departure/return corridors and calculates a focused *differentiating thumbnail bounding box* zooming into the unique core loop.
2. Organizes routes by exit gateway corridors with filter chips/collapsible groups.
3. Provides a clean configuration toggle in Tuning / Advanced Settings to switch between focused differentiating zoom and standard global full-route overview according to athlete preference.

---

## 2. Requirement Archaeology & Chesterton's Fence (`REQ-PRO-022`)

1. **Original Requirement ID & Target**: Net-new requirement `REQ-MAP-030` (*Corridor-Based Route Grouping & Differentiating Thumbnail Zoom*). No existing requirements modified.
2. **Historical Origin & Commit Trace**: Ticket `ATT-1954`, sprint `2026-40.16`, target release `V4.9.39`, Epic `ATT-66` (*[Epic] Improve Routes*).
3. **Root Reason for Existing Formulation**: Previously, `RoutesListScreen` only displayed routes in flat chronological or alphabetical sorting, rendering entire route bounding boxes that obscured the unique geometry of local loops.
4. **Preservation of Core Invariants**:
   - SQLite schema for `Routes.db` (v10) must remain untouched.
   - Calculations must be completely offline, pure, and thread-safe.
   - When the feature is disabled, behavior must strictly preserve the existing full bounding box thumbnail rendering and flat list order.
   - 100% full-suite test pass rate across all 1,770+ unit tests must be maintained.

---

## 3. Scope Bounding & User Grounding (`ATT-1250`)

### In-Scope Objectives
1. **Preferences & Configuration**:
   - Add toggles in `TuningPreferencesDataStore.kt` / `TuningConfig.kt`:
     - `corridorGroupingEnabled: Boolean` (Default: `true`)
     - `focusedThumbnailZoomEnabled: Boolean` (Default: `true`)
   - Surface in `TuningCategoriesScreen.kt` under Routes section.
2. **Corridor Analysis Engine (`RouteCorridorClassifier.kt`)**:
   - Analyzes route prefix (outbound) and suffix (inbound) path points across saved routes.
   - Detects shared corridors within a spatial tolerance of $50\,\text{m}$.
   - Classifies departure gateway by initial cardinal/compass heading ($0^\circ - 360^\circ$ mapped to North, Northeast, East, Southeast, South, Southwest, West, Northwest).
3. **Differentiating Bounding Box Engine (`RouteBoundingBoxCalculator.kt`)**:
   - Determines the subsegment $[D_{\text{start\_unique}}, D_{\text{end\_unique}}]$ excluding shared entry/exit corridors.
   - Calculates focused `LatLngBounds` centered on the unique core geometry with safety padding.
4. **Routes List UI Integration (`RoutesListScreen.kt`)**:
   - Gateway corridor filter chips at the top of the route list when grouping is active.
   - Map preview cards respect `focusedThumbnailZoomEnabled`.
5. **Localization Parity**:
   - 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT for all gateway and setting labels.

### Out-of-Scope (Non-Goals)
- Schema migrations in `Routes.db` (no database version bump).
- Changes to GPX / TCX file parsers or turn-by-turn cue generators.
- Modifying full-screen map inspection view (`RouteOnMapScreen.kt`).

---

## 4. Proposed Architectural Design

### Component 1: `RouteCorridorClassifier.kt`
* Path: `app/src/main/java/com/atrainingtracker/trainingtracker/routes/RouteCorridorClassifier.kt`
* Pure algorithmic component:
  - `classifyGatewayHeading(path: List<PathPoint>): GatewayDirection`
  - `groupRoutesByCorridor(routes: List<RouteWithPath>): Map<GatewayDirection, List<RouteWithPath>>`

### Component 2: `RouteBoundingBoxCalculator.kt`
* Path: `app/src/main/java/com/atrainingtracker/trainingtracker/routes/RouteBoundingBoxCalculator.kt`
* Computes bounding boxes:
  - `calculateGlobalBounds(path: List<PathPoint>): LatLngBounds`
  - `calculateDifferentiatingBounds(route: RouteWithPath, commonCorridorRadiusMeters: Double = 1500.0): LatLngBounds`

### Component 3: `TuningPreferencesDataStore.kt` & `TuningConfig.kt`
* Expose `corridorGroupingEnabled` and `focusedThumbnailZoomEnabled`.

### Component 4: `RoutesListScreen.kt` & `RouteCard.kt`
* Add gateway filter chips row.
* Feed differentiating or global bounds to map thumbnail preview.

---

## 5. Verification Plan
- `RouteCorridorClassifierTest.kt`: Tests gateway heading detection and corridor clustering.
- `RouteBoundingBoxCalculatorTest.kt`: Tests global vs differentiating bounds calculation.
- `TranslationParityTest`: Verifies 9-language translation parity.
- Clean-room regression `./gradlew testDebugUnitTest`.
