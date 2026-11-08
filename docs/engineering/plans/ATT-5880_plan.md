# Stage 3: Implementation Plan - ATT-5880: Map Camera Centering & Window Insets

**Ticket**: [ATT-5880](https://atrainingtracker.atlassian.net/browse/ATT-5880) | **Target Release**: `V5.0.0` | **Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5880`  
**Requirement Mapping**: `REQ-MAP-059` | **Test Mapping**: `TST-MAP-061` | **Author**: AI Agent 1 (Implementer)  

---

## 1. Problem Description & Background
Opening `Karten -> Karte` or Cockpit tracking map defaults to world zoom or `(0.0, 0.0)` Null Island in the Gulf of Guinea when GPS fix is pending. Furthermore, `GoogleMap` in `MapScreenWithTrack` uses zero bottom padding, clipping the Google attribution logo behind edge-to-edge system navigation bars, and lacks top-left navigation context (`Menu` / `Back`).

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-MAP-059` (*Initial Map Camera Centering, Fallback Cascade & Navigation Bar Insets*)
  * `REQ-MAP-059.1`: Defensive fallback cascade in `MapInitialLocationResolver` (`Live GPS` $\rightarrow$ `Last Known` $\rightarrow$ `Home Base` $\rightarrow$ `Most Visited` $\rightarrow$ `Recent Workout Start`).
  * `REQ-MAP-059.2`: Initial camera centering at zoom 14.0f and deferred live fix alignment in `MapBoundsController`.
  * `REQ-MAP-059.3`: System navigation bar inset forwarding (`contentPadding: PaddingValues`) to `GoogleMap`.
  * `REQ-MAP-059.4`: Top-start 44dp circular Surface navigation button on `Karten -> Karte`.
* **Test Mapping**: `TST-MAP-061` (`TST-MAP-061.1` through `TST-MAP-061.6`).

---

## 3. System Invariants & Preserved Behavior
1. **Rule 33 Spatial Authority**: Forward lookahead in `followMeController` remains analytical; zero dynamic padding loops. Fixed system insets (`WindowInsets.navigationBars`) are static.
2. **Recomposition Boundary Isolation**: `@Immutable TrackingMapState` retains zero reflection violations in `TrackingMapIsolationContractTest`.
3. **Defensive Location Access**: All system location queries catch `SecurityException` / `Exception` without crashes.
4. **UI Consistency**: Top-start button matches top-end layers button (44dp circular Surface, `TTAlpha.Overlay`, `elevation = 6.dp`, `statusBarsPadding()`).

---

## 4. Proposed Architectural Changes
* **Component 1: Fallback Cascade (`MapInitialLocationResolver.kt`)**: Implement `resolveInitialLocation(context, liveLocation)` querying `LocationManager` (permission-guarded), `KnownLocationsRepository` (home base or most visited), and `WorkoutSummariesDatabaseManager` (recent workout start).
* **Component 2: Camera Centering & Deferred Fix (`ATrainingTrackerMap.kt`, `SensorGridScreen.kt`, `MapBehaviors.kt`)**: In `ATrainingTrackerMap`, initialize `CameraPositionState` to `CameraPosition.fromLatLngZoom(resolvedLocation, 14f)` when `initialBounds == null`. In `MapBoundsController`, include `currentLocation` in `LaunchedEffect` keys to align deferred GPS fixes.
* **Component 3: Navigation Bar Insets (`ATrainingTrackerMap.kt`, `MapScreenWithTrack.kt`)**: Add `contentPadding: PaddingValues = PaddingValues()` to `ATrainingTrackerMap` and pass `WindowInsets.navigationBars.asPaddingValues()` in `MapScreenWithTrack`.
* **Component 4: Top-Start Navigation Button (`MapScreenWithTrack.kt`, `ATrainingTrackerApp.kt`)**: Add `onMenuClick` / `onNavigateBack` callbacks and render a 44dp circular Surface button at `Alignment.TopStart.statusBarsPadding()`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
* **Step 1: Fallback Resolver & Unit Tests**: Create `MapInitialLocationResolver.kt` and `MapInitialLocationResolverTest.kt`.
* **Step 2: Camera Initialization & Inset Forwarding**: Update `ATrainingTrackerMap.kt` and `SensorGridScreen.kt` for initial coordinate resolution and `contentPadding` forwarding.
* **Step 3: Deferred Fix Camera Alignment**: Update `MapBehaviors.kt` to trigger camera alignment when `currentLocation` arrives.
* **Step 4: Window Insets & Top-Start Button**: Update `MapScreenWithTrack.kt` and `ATrainingTrackerApp.kt` for bottom insets and top-start `Icons.Default.Menu` / `Icons.AutoMirrored.Filled.ArrowBack` Surface button (`testTag("map_navigation_button")`).
* **Step 5: Contract & Isolation Tests**: Update `TrackingMapIsolationContractTest.kt`, `MapScreenWithTrackTest.kt`, and `MapBehaviorsTest.kt`.
* **Step 6: Verification & Governance Suite**: Run targeted unit tests and `python3 tools/verify_requirement_governance.py`.

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit tests (`ui.map.*`, `ui.tracking.tracking.*`) and requirement governance check.
* **Rollback**: Branch isolation on `feature/ATT-5880` enables clean revert.
