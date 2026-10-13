# Stage 3: Implementation Plan - ATT-2627: Context-aware route selection: proximity routes before start and active route detection during tracking

**Ticket**: [ATT-2627](https://atrainingtracker.atlassian.net/browse/ATT-2627)  
**Sub-task**: [ATT-2682](https://atrainingtracker.atlassian.net/browse/ATT-2682) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.3`  
**Requirement Mapping**: `REQ-UI-289` (*Context-Aware Route Selection: Pre-Tracking Start Proximity and In-Ride Corridor Auto-Detection Alignment*)  
**Test Mapping**: `TST-UI-249`  
**Branch**: `feature/ATT-2627`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-08  

---

## 1. Problem Description & Background

Physical on-device evaluation during Sprint 2026-41.1 review identified disjointed behavior between pre-ride route selection and in-ride route tracking:
1. `RouteSelectorViewModel` unconditionally evaluated route proximity based on the start point coordinates (`route.path.firstOrNull()`). While effective for unstarted sessions, during an active workout (`TRACKING` or `PAUSED`) athletes travel along route corridors rather than at the start point.
2. In `RouteSelectorSheet.kt`, route auto-detection was rendered as an isolated single-candidate prompt banner widget (`AutoDetectedRouteBanner`) above the start-point-ranked route list, causing layout fragmentation.
3. When no route candidates qualify in either state, `RouteSelectionButton` on `ControlTrackingScreen` rendered dimmed with static text ("Choose from file or history"), giving no feedback regarding *why* no route was available (e.g. no routes nearby vs no matching route detected along the corridor).
4. Athletes need seamless 1-tap access to "Take Me Home" return navigation mid-ride even when no route corridor is recognized.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-289` (*Context-Aware Route Selection: Pre-Tracking Start Proximity and In-Ride Corridor Auto-Detection Alignment*)
* **Test Mapping**: `TST-UI-249` (*Context-Aware Route Selection Verification*)
* **Refines / Integrates**: `REQ-UI-279` (*Branded Route Selection Button*), `REQ-UI-280` (*Streamlined Route Selector*), `REQ-UI-281` (*Proximity Radius & Dimmed Button*), and `REQ-UI-282` (*Mid-Ride Take Me Home Card*).

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing route navigation, turn-by-turn cues, and full unit test suite must continue passing 100%.
2. **Interactive Dimmed Button Invariant (`REQ-UI-281`)**: The dimmed button (`alpha = 0.45f`) must remain fully interactive (`Surface(onClick = onClick)`), ensuring mid-ride access to `MidRideHeimwegCard` in the sheet even when no route is recognized.
3. **Cockpit Prompt Invariant**: `AutoDetectedRouteBanner` in `SensorGridScreen.kt` (passive mid-ride prompt above sensor tiles) remains fully operational.
4. **Single Active Navigated Route Invariant**: `RoutesRepository` transactional route selection/clearing remains strictly preserved.
5. **Human Gate Governance**: Parent ticket `ATT-2627` terminal completion is strictly reserved for the human user in `Final Review (Human)`. Subtasks transition directly to `Erledigt` via `freigabe` upon automated audit pass.

---

## 4. Proposed Architectural Changes

### Component 1: `RouteAutoDetector` Multi-Candidate Corridor Matching
* Expose `fun evaluateMatchingRoutes(location: Location, routes: List<RouteWithPath>, currentlyActiveRouteId: Long?, currentTimeMillis: Long = System.currentTimeMillis()): List<RouteWithPath>`.
* Evaluates all routes in `routes`:
  - Excludes `currentlyActiveRouteId` and routes within cooldown dismissal.
  - Matches start proximity (`distToStart <= startProximityThresholdMeters` with bearing check if moving) OR path segment proximity (`distToSegment <= pathProximityThresholdMeters` with heading alignment).
  - Returns all matching candidates sorted by ascending closest segment distance, then `syncedAt` descending.

### Component 2: `RouteSelectorViewModel` Lifecycle Awareness
* Expose `val isTrackingActive = MutableStateFlow(false)` and `fun setTrackingActive(isActive: Boolean)`.
* In `combine`, branch candidate route resolution on `isTrackingActive`:
  - `isTrackingActive == false`: Use `RouteProximityRanker.filterAndRankRoutes(allRoutes, currentLatLng, radiusMeters)`, empty hint `R.string.route_no_routes_nearby`.
  - `isTrackingActive == true`: Use `autoDetector.evaluateMatchingRoutes(location, allRoutes, activeRouteId)`, empty hint `R.string.route_no_matching_route_detected`.
* Expose `isTrackingActive` and `contextEmptyHintRes` in `RouteSelectorUiState`.

### Component 3: `RouteSelectionButton` Contextual Subtitles
* Accept `emptySubtitleRes: Int = R.string.route_action_select_desc`.
* When `activeRoute == null`:
  - If routes are available: display `R.string.route_action_select_desc` ("Choose from file or history" / "Aus Datei oder Verlauf wählen").
  - If routes are empty (dimmed state): display `stringResource(emptySubtitleRes)` ("Keine Strecken in der Nähe" vs "Keine passende Strecke erkannt").

### Component 4: `RouteSelectorSheet.kt` Streamlining
* Excise `AutoDetectedRouteBanner` from `RouteSelectorContent` in `RouteSelectorSheet.kt` (in-ride recognized routes directly populate the candidate list).
* Display `uiState.contextEmptyHintRes` in empty state placeholder.
* Retain `MidRideHeimwegCard` at top of sheet when `isMidRide == true`.

### Component 5: `TrackingTabsScreen.kt` Integration
* Forward `isMidRide = (trackingMode == TrackingMode.TRACKING || trackingMode == TrackingMode.PAUSED)` to `routeSelectorViewModel.setTrackingActive(isMidRide)`.
* Supply `emptySubtitleRes = if (routeSelectorUiState.routes.isEmpty()) routeSelectorUiState.contextEmptyHintRes else R.string.route_action_select_desc` to `RouteSelectionButton`.

### UI Consistency (Rule 23)
* **Reference screen / component**: `RouteSelectionButton.kt` and `RouteSelectorSheet.kt` established in `REQ-UI-279` and `REQ-UI-282`.
* **Reused components**:
  - `RouteSelectionButton` (`ui/components/RouteSelectionButton.kt`): `RoundedCornerShape(12.dp)`, `TTColor.RouteSelected.copy(alpha = 0.35f)` border, 36.dp green-tinted icon container.
  - `MidRideHeimwegCard`: `RoundedCornerShape(12.dp)`, `Icons.Default.Home`.
  - `RouteCard`: Standard candidate card styling with `secondaryContainer` when active.
* **Theme tokens**: `MaterialTheme.colorScheme.surfaceVariant`, `MaterialTheme.typography.bodySmall`, `MaterialTheme.typography.titleMedium`.
* **New one-off styles & justification**: None. Reuses existing design system tokens and strings.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Multi-Candidate Evaluation in `RouteAutoDetector.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/routes/RouteAutoDetector.kt`
* Changes: Implement `evaluateMatchingRoutes` returning all matching route corridors sorted by proximity.

### Step 2: Lifecycle Awareness in `RouteSelectorViewModel.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSelectorViewModel.kt`
* Changes: Add `setTrackingActive(Boolean)`, branch candidate querying between pre-tracking start proximity and in-ride corridor matching, and expose `contextEmptyHintRes`.

### Step 3: Contextual Subtitles in `RouteSelectionButton.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/RouteSelectionButton.kt`
* Changes: Accept `emptySubtitleRes: Int`, display when `activeRoute == null`.

### Step 4: Streamlined Route Sheet & Excision of Banner in `RouteSelectorSheet.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSelectorSheet.kt`
* Changes: Remove `AutoDetectedRouteBanner` from `RouteSelectorContent`, bind empty state to `uiState.contextEmptyHintRes`.

### Step 5: Screen Wiring in `TrackingTabsScreen.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreen.kt`
* Changes: Bind `isMidRide` to `routeSelectorViewModel.setTrackingActive(isMidRide)` and pass contextual subtitle to `RouteSelectionButton`.

### Step 6: 9-Language Localization Parity
* Files: `app/src/main/res/values*/strings.xml` (all 9 locales)
* Changes: Add `route_no_routes_nearby` and `route_no_matching_route_detected`.

### Step 7: Unit & Contract Tests
* Files:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/routes/RouteAutoDetectorTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSelectorViewModelTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSelectorSheetTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/RouteSelectionButtonContractTest.kt`
* Command:
  ```bash
  ./gradlew testDebugUnitTest \
    --tests "com.atrainingtracker.trainingtracker.routes.RouteAutoDetectorTest" \
    --tests "com.atrainingtracker.trainingtracker.ui.routes.RouteSelectorViewModelTest" \
    --tests "com.atrainingtracker.trainingtracker.ui.routes.RouteSelectorSheetTest" \
    --tests "com.atrainingtracker.trainingtracker.ui.components.RouteSelectionButtonContractTest" \
    --tests "com.atrainingtracker.translations.TranslationParityTest"
  ```

---

## 6. Verification & Rollback Plan

* **Verification**: Execute targeted unit and contract tests in Stage 4, followed by full clean-room test suite regression (`./gradlew testDebugUnitTest`) in Stage 5.
* **Rollback Plan**: Git branch `feature/ATT-2627` provides isolated rollback capability before merging into `sprint/2026-41.3`.
