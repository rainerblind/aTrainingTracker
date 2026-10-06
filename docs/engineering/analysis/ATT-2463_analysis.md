# Stage 1 Analysis: ATT-2463 - Remove corridor-based route grouping and differentiating thumbnail zoom (ATT-1954)

**Ticket**: [ATT-2463](https://rainerblind.atlassian.net/browse/ATT-2463)  
**Sub-task**: [ATT-2596](https://rainerblind.atlassian.net/browse/ATT-2596) (`[Analysis]`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-41.1`  
**Branch**: `feature/ATT-2463`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-06  

---

## 1. Problem Statement & Motivation

During the Sprint 2026-40.16 Joint Review on physical test hardware (Pixel 10), the human tester/developer evaluated the newly introduced "Corridor-Based Route Grouping & Differentiating Thumbnail Zoom" (`ATT-1954` / `REQ-MAP-030` / `TST-MAP-032`). The real-world experience did not align with expectations:
> *"This is absolutely not what I thought it would be. Please create a ticket to remove this."*

Specifically:
1. Grouping routes by departure gateway direction (North, East, South, West, etc.) with top filter chips added visual clutter to `RouteTabbedScreen` and disrupted simple alphabetical and sport-based list scanning.
2. Differentiating thumbnail zoom cropped common entry/exit corridors to focus on unique middle loops, but caused confusingly tight, disjointed map previews without sufficient geographic context.
3. The feature introduced configuration toggles and gateway direction strings across 9 locales that are now obsolete.

The goal of `ATT-2463` is the complete, surgical removal of corridor-based route grouping and differentiating thumbnail zoom, cleanly restoring flat route list ordering and standard full-extent route map previews.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Inventory of Code Introduced by ATT-1954
A complete forensic trace of ATT-1954 across the codebase reveals the following artifacts:
* **Algorithmic & Utility Classes**:
  * `app/src/main/java/com/atrainingtracker/trainingtracker/routes/RouteCorridorClassifier.kt`: Implements `GatewayDirection` enum, bearing classification, initial outbound heading detection, and `groupRoutesByCorridor`.
  * `app/src/main/java/com/atrainingtracker/trainingtracker/routes/RouteBoundingBoxCalculator.kt`: Implements `RouteBoundingBox` and `calculateDifferentiatingBounds`.
* **UI Components**:
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/GatewayFilterChipsRow.kt`: Filter chip row for compass directions.
  * `RouteTabbedScreen.kt`: Houses `selectedGateway`, `availableGateways`, `hasGatewayChips`, `gatewayChipsHeight`, nested scroll offset calculations, and gateway filtering.
  * `RouteList.kt`: Accepts `focusedThumbnailZoomEnabled: Boolean = true` and forwards it to `RouteItem`.
  * `RouteItem.kt`: Computes `targetBounds` via `RouteBoundingBoxCalculator.calculateDifferentiatingBounds` and forwards to `PathPreviewMap`.
* **Configuration & Persistence**:
  * `TuningConfig.kt` & `TuningPreferencesDataStore.kt`:
    * Preferences: `corridorGroupingEnabled` and `focusedThumbnailZoomEnabled`.
    * Keys: `KEY_CORRIDOR_GROUPING_ENABLED` and `KEY_FOCUSED_THUMBNAIL_ZOOM_ENABLED`.
    * Updaters: `updateCorridorGroupingEnabled` and `updateFocusedThumbnailZoomEnabled`.
* **Localization**:
  * String resources `gateway_all`, `gateway_north`..`gateway_northwest`, `gateway_unknown`, `pref_corridor_grouping_title`, `pref_corridor_grouping_desc`, `pref_focused_thumbnail_title`, `pref_focused_thumbnail_desc` across all 9 `values*/strings.xml`.
* **Unit Tests**:
  * `RouteCorridorClassifierTest.kt`, `RouteBoundingBoxCalculatorTest.kt`, `RouteCorridorUiIntegrationTest.kt`.
* **Living Documentation**:
  * `REQ-MAP-030` in `docs/requirements.md` and `TST-MAP-032` in `docs/tests.md`.

### 2.2 Shared Geodesic Dependency Archaeology
Crucially, a reverse-dependency analysis on `RouteCorridorClassifier.kt` revealed that subsequent navigation features (`ATT-1955` / `REQ-MAP-031` *In-Ride Fork-in-the-Road Route Selection & Decision Alerts*) reuse two pure mathematical functions from `RouteCorridorClassifier`:
* `ForkRouteMatcher.kt`: Uses `RouteCorridorClassifier.haversineDistanceMeters`.
* `RouteDivergenceDetector.kt`: Uses `RouteCorridorClassifier.haversineDistanceMeters` and `RouteCorridorClassifier.calculateInitialBearing`.

**Architectural Decision**:
Deleting `RouteCorridorClassifier.kt` outright without migrating these geodesic utilities would break Fork Navigation (`ATT-1955`). Therefore, we must extract `haversineDistanceMeters` and `calculateInitialBearing` into a standalone, pure utility `app/src/main/java/com/atrainingtracker/trainingtracker/routes/GeoUtils.kt`, rewire `ForkRouteMatcher` and `RouteDivergenceDetector` to `GeoUtils`, and back it with targeted unit tests (`GeoUtilsTest.kt`).

---

## 3. User Scope Grounding (ATT-1250)

### In-Scope Goals:
1. **Clean Code Excision**: Remove `RouteBoundingBoxCalculator.kt`, `GatewayFilterChipsRow.kt`, and the corridor classification logic from `RouteCorridorClassifier.kt`.
2. **Shared Geodesic Preservation**: Create `GeoUtils.kt` containing `haversineDistanceMeters` and `calculateInitialBearing`, migrating callers in `ForkRouteMatcher.kt` and `RouteDivergenceDetector.kt`.
3. **UI Restoration**:
   - `RouteTabbedScreen.kt`: Remove `GatewayFilterChipsRow`, `selectedGateway`, `availableGateways`, `hasGatewayChips`, and gateway filtering.
   - `RouteList.kt`: Remove `focusedThumbnailZoomEnabled` parameter.
   - `RouteItem.kt`: Remove `RouteBoundingBoxCalculator` usage and `targetBounds` computation; pass `targetBounds = null` to `PathPreviewMap` (reverting to global route bounds).
4. **DataStore Clean-up**:
   - Remove `corridorGroupingEnabled` and `focusedThumbnailZoomEnabled` properties from `TuningConfig`.
   - Remove unused keys from active preference flow while gracefully tolerating existing stored keys in user DataStore files without crash.
5. **Localization Cleanup**: Remove obsolete `gateway_*` and `pref_corridor_*` / `pref_focused_*` strings across all 9 supported locales.
6. **Living Documentation Synchronization**: Mark `REQ-MAP-030` and `TST-MAP-032` as `Superseded / Removed` with a Chesterton's Fence note referencing `ATT-2463`.

### Out-of-Scope Non-Goals (Scope Bounding):
* Do not modify `PathPreviewMap.kt` internal rendering of global bounds.
* Do not modify `RouteAutoDetector` or `RouteDivergenceDetector` algorithmic decision thresholds.
* Do not alter SQLite schema in `Routes.db`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-MAP-030` (*Corridor-Based Route Grouping & Differentiating Thumbnail Zoom*).
* **Historical Origin & Commit Trace**: Ticket `ATT-1954`, Sprint `2026-40.16` (commit `69e9e1c4`), Epic `ATT-66` (*[Epic] Improve Routes*).
* **Root Reason for Existing Formulation**: The original intent was to help athletes visually differentiate between routes sharing identical departure roads and avoid compressed, indistinguishable thumbnails.
* **Preservation of Core Invariants**: Real-world on-device testing demonstrated that departure corridor clustering created unwanted cognitive load, and zoomed thumbnails lacked sufficient landscape orientation. Full-route global bounding boxes (`REQ-MAP-024`) and flat list sorting by recency and distance (`REQ-UI-280`, `REQ-UI-281`) provide a vastly superior user experience. Removing `REQ-MAP-030` restores simplicity while preserving all surrounding route management and navigation invariants.

---

## 5. Architectural Strategy & High-Level Solution

```
Before (ATT-1954):
  RouteTabbedScreen ────> GatewayFilterChipsRow ───> RouteCorridorClassifier (Corridor Clustering)
  RouteItem ────────────> RouteBoundingBoxCalculator (Differentiating Bounds)
  RouteDivergenceDetector ──> RouteCorridorClassifier.haversineDistanceMeters
  ForkRouteMatcher ─────────> RouteCorridorClassifier.haversineDistanceMeters

After (ATT-2463):
  RouteTabbedScreen ────> (Standard flat list, no gateway chips)
  RouteItem ────────────> PathPreviewMap (Global route bounds)
  RouteDivergenceDetector ──> GeoUtils.haversineDistanceMeters & calculateInitialBearing
  ForkRouteMatcher ─────────> GeoUtils.haversineDistanceMeters
  [RouteCorridorClassifier, RouteBoundingBoxCalculator, GatewayFilterChipsRow DELETED]
```

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero production regressions: all existing route list filtering, sorting, elevation profiles, and in-ride fork detections remain 100% operational.
  2. DataStore backward compatibility: users with persisted corridor preference flags do not encounter crashes or migration failures.
  3. Clean-room test suite achieves 100% pass rate.
* **Risk Rating**: **LOW**
  - Justification: Removal of self-contained UI widgets and algorithmic filters; extracted geodesic math (`GeoUtils`) guarantees zero disruption to fork navigation.
