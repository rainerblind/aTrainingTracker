# Stage 3: Implementation Plan - ATT-2463: Remove corridor-based route grouping and differentiating thumbnail zoom (ATT-1954)

**Ticket**: [ATT-2463](https://rainerblind.atlassian.net/browse/ATT-2463)  
**Sub-task**: [ATT-2598](https://rainerblind.atlassian.net/browse/ATT-2598) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-41.1`  
**Requirement Mapping**: `REQ-MAP-035` (Supersedes and removes `REQ-MAP-030`)  
**Test Mapping**: `TST-MAP-037`  
**Branch**: `feature/ATT-2463`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-06  

---

## 1. Problem Description & Background

During Sprint 2026-40.16 physical testing on a Pixel 10 device, the corridor-based route grouping and differentiating thumbnail zoom features (`ATT-1954` / `REQ-MAP-030`) proved counterproductive:
1. **Cluttered Route Overview**: The departure gateway direction filter chips (North, East, South, West) added cognitive noise and unnecessary visual chrome to `RouteTabbedScreen`, impairing rapid route browsing.
2. **Disorienting Map Thumbnails**: The differentiating thumbnail zoom cropped common entry and exit corridors to focus tightly on the unique middle loop. Athletes reported that this tight crop eliminated crucial geographic reference context, making it hard to identify familiar routes at a glance.
3. **Dead Settings & String Bloat**: Tuning preferences (`corridorGroupingEnabled`, `focusedThumbnailZoomEnabled`) and 14 direction/preference string resources across 9 locales bloated configuration and translation tables.

This plan details the surgical removal of corridor-based route grouping and differentiating thumbnail zoom, the extraction of shared geodesic calculation utilities (`haversineDistanceMeters` and `calculateInitialBearing`) into a pure standalone utility `GeoUtils.kt` to preserve Fork Navigation (`REQ-MAP-031`), and the complete cleanup of UI layouts, preferences, strings, and automated tests.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-MAP-035` (*Standalone Geodesic Calculation Utilities & Flat Route List Presentation*)
  * Supersedes and permanently removes `REQ-MAP-030` (*Corridor-Based Route Grouping & Differentiating Thumbnail Zoom*).
  * Preserves `REQ-MAP-031` (*In-Ride Fork-in-the-Road Route Selection & Decision Alerts*).
  * Preserves `REQ-MAP-024` (*Quick Route Selector*), `REQ-MAP-028` (*Turn-by-Turn Prompts*), `REQ-MAP-029` (*Take Me Home Return Navigation*), `REQ-MAP-033` (*High-Contrast POI Badges*), and `REQ-MAP-034` (*Designated Home-Base Selection*).
* **Test Mapping**: `TST-MAP-037` (*Standalone Geodesic Calculation Utilities & Flat Route List Presentation Verification*)
  * `TST-MAP-037.1`: Geodesic math accuracy and bearing normalization in `GeoUtilsTest.kt`.
  * `TST-MAP-037.2`: Clean flat layout and absence of gateway chips in `RouteTabbedCleanLayoutTest.kt`.
  * `TST-MAP-037.3`: Preference backward compatibility and tolerant parsing in `TuningPreferencesDataStoreTest.kt`.
  * `TST-MAP-037.4`: 9-language translation parity audit in `TranslationParityTest.kt`.
  * `TST-MAP-037.5`: Full clean-room test suite regression execution (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Mathematical Equivalence for Fork Navigation**: `ForkRouteMatcher.kt` and `RouteDivergenceDetector.kt` rely on great-circle distance and initial bearing calculations previously housed in `RouteCorridorClassifier.kt`. These algorithms must be preserved in `GeoUtils.kt` with zero floating-point divergence.
2. **Zero Framework Dependencies in GeoUtils**: `GeoUtils.kt` must remain a pure Kotlin utility with no Android framework (`android.*`) dependencies, guaranteeing instant execution in unit tests.
3. **DataStore Resilience**: Removal of `corridorGroupingEnabled` and `focusedThumbnailZoomEnabled` properties must not cause crashes or schema deserialization issues for users with existing stored preference keys.
4. **Clean-Room Test Suite Integrity**: All 1992+ existing unit and contract tests across the project must pass with 0 failures after excising obsolete tests and adding replacement tests.
5. **Human Decision Gate Guard (Rule 1)**: Subtask `ATT-2598` transitions to `Erledigt` via `freigabe` upon Gate 3 audit approval; parent `ATT-2463` is NEVER completed by AI and terminates at `Final Review (Human)`.

---

## 4. Proposed Architectural Changes (SWE.2)

### Component 1: Pure Geodesic Math Utilities (`GeoUtils.kt`)
* Path: `app/src/main/java/com/atrainingtracker/trainingtracker/routes/GeoUtils.kt`
* Functions:
  * `haversineDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double`: Calculates great-circle distance on WGS-84 sphere ($R = 6371000\text{ m}$).
  * `calculateInitialBearing(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double`: Calculates forward azimuth bearing in degrees, normalized strictly to $[0.0, 360.0)$.
* Callers Updated:
  * `ForkRouteMatcher.kt`: Replace `RouteCorridorClassifier.haversineDistanceMeters` and `calculateInitialBearing` with `GeoUtils` equivalents.
  * `RouteDivergenceDetector.kt`: Replace `RouteCorridorClassifier.haversineDistanceMeters` and `calculateInitialBearing` with `GeoUtils` equivalents.

### Component 2: Complete Excision of Obsolete Route Processing Files
* Delete `app/src/main/java/com/atrainingtracker/trainingtracker/routes/RouteCorridorClassifier.kt` (contains `GatewayDirection` enum, departure classification, corridor grouping).
* Delete `app/src/main/java/com/atrainingtracker/trainingtracker/routes/RouteBoundingBoxCalculator.kt` (contains corridor trimming, dynamic loop bounding box).
* Delete `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/GatewayFilterChipsRow.kt` (contains directional filter chip composables).

### Component 3: Route Screen UI Simplification
* `RouteTabbedScreen.kt`:
  * Remove `GatewayFilterChipsRow` import and usage.
  * Remove state: `selectedGateway`, `availableGateways`, `hasGatewayChips`, `gatewayChipsHeight`.
  * Pass raw sport-filtered routes directly to `RouteList`.
* `RouteList.kt`:
  * Remove `focusedThumbnailZoomEnabled` parameter.
* `RouteItem.kt`:
  * Remove `RouteBoundingBoxCalculator` import and usage.
  * Remove `focusedThumbnailZoomEnabled` parameter.
  * Always pass `targetBounds = null` to `PathPreviewMap`, guaranteeing full-route bounding box preview.

### Component 4: Configuration & Preferences Cleanup
* `TuningConfig.kt`:
  * Remove `corridorGroupingEnabled` and `focusedThumbnailZoomEnabled` properties.
* `TuningPreferencesDataStore.kt`:
  * Remove `corridorGroupingEnabled` and `focusedThumbnailZoomEnabled` flows and update functions.
  * Remove obsolete preference keys.
* `AdvancedTuningScreen.kt` (if referenced):
  * Ensure no preference controls remain for corridor grouping or thumbnail zoom.

### Component 5: String Resource Cleanup (9 Locales)
* Remove obsolete strings across `res/values/strings.xml` and 8 localized variants (`values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`):
  * Direction tokens: `gateway_all`, `gateway_north`, `gateway_northeast`, `gateway_east`, `gateway_southeast`, `gateway_south`, `gateway_southwest`, `gateway_west`, `gateway_northwest`, `gateway_unknown`.
  * Preference tokens: `pref_corridor_grouping_title`, `pref_corridor_grouping_desc`, `pref_focused_thumbnail_title`, `pref_focused_thumbnail_desc`.

### Component 6: Test Suite Alignment
* Delete obsolete test files:
  * `app/src/test/java/com/atrainingtracker/trainingtracker/routes/RouteCorridorClassifierTest.kt`
  * `app/src/test/java/com/atrainingtracker/trainingtracker/routes/RouteBoundingBoxCalculatorTest.kt`
  * `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteCorridorUiIntegrationTest.kt`
* Create new test files:
  * `app/src/test/java/com/atrainingtracker/trainingtracker/routes/GeoUtilsTest.kt`: Unit tests verifying distance and initial bearing calculations against standard geographic landmarks and corner cases (coincident points, poles, anti-meridian).
  * `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteTabbedCleanLayoutTest.kt`: Structural contract test verifying absence of gateway filter chips and compliance with clean flat list layout.

---

## 5. UI Consistency (Rule 23 — mandatory if UI is added or changed)

* **Reference Screen / Component**: Standard route list presentation (`RouteTabbedScreen.kt`, `RouteList.kt`, `RouteItem.kt`) reverting to the clean visual baseline established before ATT-1954, identical in pattern to `WorkoutsListScreen` and `KnownLocationsScreen`.
* **Reused Components**:
  * Standard `LazyColumn` route list container.
  * `RouteItem` card with standard Material 3 elevation and shape (`RoundedCornerShape(12.dp)`).
  * `PathPreviewMap` rendering the canonical full-extent route bounding box preview.
* **Theme Tokens**:
  * Shapes: Standard `12.dp` for `RouteItem` card container.
  * Spacing: Standard `16.dp` horizontal margins, `8.dp` vertical item spacing.
  * Colors: Standard `MaterialTheme.colorScheme.surfaceVariant`, `onSurface`, and `TTColor.RouteSelected`.
* **New One-Off Styles & Justification**: None. This change strictly eliminates custom directional filter chips (`GatewayFilterChipsRow`) and cropped thumbnail boxes, restoring the app's clean visual baseline.

---

## 6. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Create `GeoUtils.kt` and Rewire Fork Navigation Callers
* Files:
  * `app/src/main/java/com/atrainingtracker/trainingtracker/routes/GeoUtils.kt`
  * `app/src/main/java/com/atrainingtracker/trainingtracker/routes/ForkRouteMatcher.kt`
  * `app/src/main/java/com/atrainingtracker/trainingtracker/routes/RouteDivergenceDetector.kt`
* Actions:
  1. Implement `haversineDistanceMeters` and `calculateInitialBearing` in `GeoUtils.kt`.
  2. Update `ForkRouteMatcher.kt` and `RouteDivergenceDetector.kt` to reference `GeoUtils`.

### Step 2: Delete Obsolete Route Processing Files
* Files to delete:
  * `app/src/main/java/com/atrainingtracker/trainingtracker/routes/RouteCorridorClassifier.kt`
  * `app/src/main/java/com/atrainingtracker/trainingtracker/routes/RouteBoundingBoxCalculator.kt`
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/GatewayFilterChipsRow.kt`

### Step 3: Streamline Route UI Screens
* Files:
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteTabbedScreen.kt`
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteList.kt`
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteItem.kt`
* Actions:
  1. In `RouteTabbedScreen.kt`, remove `GatewayFilterChipsRow`, `selectedGateway`, and gateway filtering logic.
  2. In `RouteList.kt`, remove `focusedThumbnailZoomEnabled` parameter.
  3. In `RouteItem.kt`, remove `RouteBoundingBoxCalculator` and `focusedThumbnailZoomEnabled`, passing `targetBounds = null` to `PathPreviewMap`.

### Step 4: Clean Up Preferences & Configuration
* Files:
  * `app/src/main/java/com/atrainingtracker/trainingtracker/tuning/TuningConfig.kt`
  * `app/src/main/java/com/atrainingtracker/trainingtracker/preferences/TuningPreferencesDataStore.kt`
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/preferences/AdvancedTuningScreen.kt` (if applicable)
* Actions:
  1. Remove `corridorGroupingEnabled` and `focusedThumbnailZoomEnabled` properties and mutators.

### Step 5: Clean Up String Resources Across 9 Locales
* Files:
  * `app/src/main/res/values/strings.xml`
  * `app/src/main/res/values-de/strings.xml`
  * `app/src/main/res/values-es/strings.xml`
  * `app/src/main/res/values-fr/strings.xml`
  * `app/src/main/res/values-it/strings.xml`
  * `app/src/main/res/values-ja/strings.xml`
  * `app/src/main/res/values-nl/strings.xml`
  * `app/src/main/res/values-pl/strings.xml`
  * `app/src/main/res/values-pt/strings.xml`
* Actions:
  1. Remove all 14 gateway and corridor preference string entries from each locale.

### Step 6: Test Suite Alignment & Replacement
* Files to delete:
  * `app/src/test/java/com/atrainingtracker/trainingtracker/routes/RouteCorridorClassifierTest.kt`
  * `app/src/test/java/com/atrainingtracker/trainingtracker/routes/RouteBoundingBoxCalculatorTest.kt`
  * `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteCorridorUiIntegrationTest.kt`
* Files to create:
  * `app/src/test/java/com/atrainingtracker/trainingtracker/routes/GeoUtilsTest.kt`
  * `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteTabbedCleanLayoutTest.kt`
* Targeted Test Command:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.routes.*" --tests "com.atrainingtracker.trainingtracker.ui.routes.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"
  ```

---

## 7. Verification & Rollback Plan

* **Verification**:
  - Run targeted unit tests for routes, UI, and localization.
  - In Stage 5, execute full clean-room test suite `./gradlew testDebugUnitTest`.
  - Validate living documentation (`docs/requirements.md`, `docs/tests.md`).
* **Rollback**:
  - Branch isolation on `feature/ATT-2463` enables total revert without affecting `sprint/2026-41.1` or `develop`.
