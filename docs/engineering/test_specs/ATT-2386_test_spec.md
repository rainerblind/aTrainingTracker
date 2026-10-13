# Stage 2: Requirement & Test Specification - ATT-2386: Anchor Elevation Profile to Bottom Navigation Bar and Dynamically Expand Upper Map in Route and Segment Details

**Ticket**: [ATT-2386](https://rainerblind.atlassian.net/browse/ATT-2386)  
**Sub-task**: [ATT-2490](https://rainerblind.atlassian.net/browse/ATT-2490) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-41.1`  
**Requirement Mapping**: `REQ-UI-273` (*Routes & Segments: Dynamically Maximized Map with Bottom-Anchored Intrinsic Elevation Profile in MapDetailLayout*)  
**Test Spec ID**: `TST-UI-233` (*Routes & Segments: Dynamically Maximized Map with Bottom-Anchored Intrinsic Elevation Profile Verification*)  
**Branch**: `feature/ATT-2386`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-05  

---

## 1. Requirement Specification (`REQ-UI-273`)

### 1.1 Problem Statement & Rationale
In route and segment detailed views (`RouteOnMapScreen` / `SegmentOnMapScreen` / `MapDetailLayout`), the screen viewport is split into two panes using a proportional split (`splitFraction`, defaulting to 50/50). Because routes and segments contain only an elevation profile (and zoom toolbar) without continuous telemetry graphs or analytics cards, allocating 50% of the screen height wastes 100–180 dp in empty whitespace above the Navigation Bar and artificially squashes the map. The elevation profile must wrap its intrinsic content height anchored flush to the Navigation Bar, while the map dynamically expands to fill all remaining vertical space (`weight(1f)`).

### 1.2 Functional & Architectural Requirements
The system SHALL decouple non-scrollable single-profile viewports (`hasScrollableContent == false`) from the proportional 50/50 split-pane in `MapDetailLayout.kt` (ATT-2386):
1. **Differentiated Viewport Allocation (`MapDetailLayout.kt`)**:
   When `showMap == true` and `hasLowerSection == true`:
   - (a) *Scrollable Multi-Section Viewports (`hasScrollableContent == true`)*: For workout aftermath inspection (`TrackOnMapScreen`), the system SHALL continue to provide the interactive resizable split-pane with `SplitPaneDivider`, `splitFraction` (default 50/50), and vertical scrolling across telemetry charts and analytics per `REQ-UI-223` and `REQ-UI-267`.
   - (b) *Non-Scrollable Single-Profile Viewports (`hasScrollableContent == false`)*: For route and segment detail screens (`RouteOnMapScreen` and `SegmentOnMapScreen`), the system SHALL eliminate the proportional split fraction and `SplitPaneDivider`.
2. **Dynamically Maximized Map Viewport**:
   In non-scrollable viewports (`!hasScrollableContent`), the map container (`mapBox`) SHALL receive `Modifier.weight(1f).heightIn(min = SplitPaneMath.MIN_MAP_HEIGHT).fillMaxWidth()`, expanding dynamically into all remaining vertical space above the elevation profile.
3. **Bottom-Anchored Intrinsic Elevation Profile**:
   In non-scrollable viewports (`!hasScrollableContent`), the lower elevation profile container SHALL apply `Modifier.fillMaxWidth().wrapContentHeight()`, hosting `GlobalTelemetryZoomToolbar` (height 40 dp), elevation header and profile canvas (~100–228 dp), and `scrubbingOverlay`, with its bottom edge sitting flush with the top of the Navigation Bar via `navigationBarsPadding()`.
4. **Preservation of Specialized Viewports & Invariants**:
   - Standalone map views without lower content (`WorkoutClusterHeatmapScreen`) retain full-screen map (`mapBox(Modifier.fillMaxSize())`).
   - Compact bottom sheets (`LiveSegmentSheet`, `showMap == false`) retain `wrapContentHeight()`.
   - Full interactivity of `GlobalTelemetryZoomToolbar` (+, -, 1.0x, pan toggle), 2-finger pinch zoom, coordinate scrubbing, and map marker synchronization are 100% strictly preserved.
   - Minimum map height protection (`SplitPaneMath.MIN_MAP_HEIGHT` = 120 dp) is strictly enforced.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Refines `REQ-UI-267` (*Routes & Segments: Position Elevation Profile Below Map with Interactive Zooming, Panning, and Resizable Split-Pane Viewport in MapDetailLayout*) and restores the intent of Clause 4 in `REQ-UI-223` (*Aftermath/Map: Interactive Draggable Splitter and Dynamic Viewport Resizing in MapDetailLayout*).
2. *Historical Origin & Commit Trace*:
   - `ATT-1890` (Sprint `2026-40.7`, commit `4e6224aa`): Introduced `REQ-UI-223` with Clause 4 stating: *"When `hasScrollableContent == false` (Routes & Segments), Map Box SHALL retain full `Modifier.weight(1f)` and lower section SHALL wrap its content (`wrapContentHeight()`), with zero divider displayed."*
   - `ATT-2311` (Sprint `2026-40.15`, commit `f129a0de`): Introduced `REQ-UI-267` to move elevation profile below the map in routes/segments by switching condition to `showMap && hasLowerSection`, unintentionally subjecting routes and segments to the 50/50 proportional split.
3. *Root Reason for Existing Formulation*: `ATT-2311` sought to position the elevation profile below the map rather than floating over it. Reusing the existing split layout block was straightforward, but enforced a 50% split budget designed for multi-chart workout aftermath rather than an intrinsic-height single profile.
4. *Preservation of Core Invariants*: `TrackOnMapScreen` retains full 2-pane interactive resizing with `SplitPaneDivider`; route/segment zoom toolbar and scrubbing remain 100% functional; minimum map height (120 dp) is strictly preserved.

### 1.4 Acceptance Criteria (Given-When-Then)
* **AC-1 (Dynamic Map Maximization in Routes & Segments)**:
  * *Given* an athlete viewing a route in `RouteOnMapScreen` or a segment in `SegmentOnMapScreen`,
  * *When* the screen renders,
  * *Then* the upper map SHALL receive `Modifier.weight(1f)` and expand dynamically to fill all vertical space above the elevation profile, with zero empty whitespace between the elevation profile and the Navigation Bar.
* **AC-2 (Intrinsic Profile Sizing & Navigation Bar Anchoring)**:
  * *Given* any elevation profile with range between 0 m and 1000+ m in `RouteOnMapScreen` or `SegmentOnMapScreen`,
  * *When* rendered,
  * *Then* the lower section SHALL size intrinsically to its content (`wrapContentHeight()`), with its bottom edge sitting flush against the top of the Navigation Bar (`navigationBarsPadding()`).
* **AC-3 (Preservation of Split-Pane in Workout Aftermath)**:
  * *Given* an athlete viewing a completed workout with telemetry charts in `TrackOnMapScreen`,
  * *When* inspecting the screen,
  * *Then* the interactive `SplitPaneDivider` SHALL remain active with proportional resizing (`splitFraction`) and vertical scrolling across all charts.
* **AC-4 (Interactivity Preservation)**:
  * *Given* `GlobalTelemetryZoomToolbar` and `ElevationProfile` in `RouteOnMapScreen` or `SegmentOnMapScreen`,
  * *When* zooming in/out, toggling pan mode, or scrubbing distance,
  * *Then* the curve SHALL magnify smoothly, and scrubbing SHALL accurately position the map marker.

---

## 2. Test Specification (`TST-UI-233`)

### Test Case 1: `testMapDetailLayout_differentiatesScrollableContentForDynamicMapExpansion` (`TST-UI-233.1`)
* **Scope**: Architectural Contract Test (`MapDetailLayoutDynamicViewportContractTest.kt`)
* **Goal**: Verify that `MapDetailLayout.kt`:
  1. Checks `hasScrollableContent` within the `showMap && hasLowerSection` block.
  2. When `hasScrollableContent == false`, assigns `Modifier.weight(1f).heightIn(min = SplitPaneMath.MIN_MAP_HEIGHT)` to `mapBox`.
  3. When `hasScrollableContent == false`, assigns `Modifier.fillMaxWidth().wrapContentHeight()` to the lower container.
  4. Only renders `SplitPaneDivider` when `hasScrollableContent == true`.
  5. Retains `navigationBarsPadding()` on `lowerColumn` Surface to ensure flush anchoring to the Navigation Bar.
* **Expected Result**: PASS.

### Test Case 2: `testMapDetailLayout_preservesExistingContractSuites` (`TST-UI-233.2`)
* **Scope**: Integration & Regression Contract Suite
* **Target Files**:
  - `MapDetailLayoutCollapsingHeaderContractTest.kt`
  - `SplitPaneDividerVisualContractTest.kt`
  - `MapDetailLayoutTest.kt`
* **Expected Result**: 100% PASS with zero broken assertions.

### Test Case 3: Clean-Room Regression Suite (`TST-UI-233.3`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Full suite verification with 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-233.1` | Unit / Contract | `MapDetailLayoutDynamicViewportContractTest` | `REQ-UI-273` | Specified |
| `TST-UI-233.2` | Integration | Existing MapDetailLayout test suite | `REQ-UI-273`, `REQ-UI-223`, `REQ-UI-267` | Specified |
| `TST-UI-233.3` | Regression | Full `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
