# Stage 1 Analysis: ATT-3053 - Harmonize Climb popup visual design and architecture with Segment and Route popups using shared base layout

**Ticket**: [ATT-3053](https://atrainingtracker.atlassian.net/browse/ATT-3053)  
**Sub-task**: [ATT-3072](https://atrainingtracker.atlassian.net/browse/ATT-3072) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Active Sprint**: `Sprint 2026-41.7`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Problem Statement & User Feedback

During the Sprint 2026-41.6 review of ATT-2860, the product owner/user reviewed the unified bottom sheets and observed:
> *"This works very well for Segments but abolutely not for the climbs. The popup for the climbs must be (alomst) similr to the popup of the segments. From my point of view, the climb popup should share some of the code form the segment (and route) popup. I.e., I think there should be a common base class..."*

### Architectural Gap & Asymmetry Analysis
1. **Divergent Scaffolding & Code Duplication**:
   - In `SegmentDetailSheet.kt` and `RouteDetailSheet.kt`, both composables configure an identical Material 3 `ModalBottomSheet` container with `skipPartiallyExpanded = true`, `BottomSheetDesign.SheetShape`, `BottomSheetDesign.SheetTonalElevation`, `dragHandle = null`, `fillMaxHeight()`, and an overlay circular dismiss button (`IconButton` wrapping a 2.dp elevated `Surface` with `CircleShape` and `Icons.Default.Close`).
   - However, this scaffolding is duplicated between `SegmentDetailSheet` and `RouteDetailSheet`, rather than encapsulated in a reusable shared base composable (`EntityDetailSheetScaffold`).
2. **Disparate Visual & Functional Presentation in `ClimbDetailSheet.kt`**:
   - Unlike `SegmentDetailSheet` and `RouteDetailSheet` which host full canonical screens powered by `MapDetailLayout` (offering full-bleed maps, elevation profiles with interactive scrubbing, synchronized markers, and standard header/detail rows), `ClimbDetailSheet.kt` still uses `AppModalBottomSheet` hosting a disjoint vertical stack of three custom `ElevatedCard` containers:
     - `ClimbDetailMetricsCard`: Custom 4-metric horizontal row with non-standard dividers.
     - `ClimbDetailMapCard`: Hardcoded 200.dp fixed-height map box lacking collapsible split-pane interactions.
     - `ClimbDetailElevationProfileCard`: Fixed 110.dp static Canvas (`ClimbDetailElevationProfile`) with no scrubbing, no tooltip, and no spatial synchronization with the map.
   - This creates an inconsistent visual presentation and fragmented sheet architecture directly noticed and rejected by the user.

---

## 2. Chesterton's Fence Archaeology & Requirement History (`REQ-PRO-022`)

### 2.1 Original Requirement ID & Target
- Modifies/refines `REQ-UI-315` (*Harmonized Visual & UX Architecture for Climb, Segment, and Route Detail Bottom Sheets via MapDetailLayout*, Sprint 2026-41.6, ATT-2860) and `REQ-UI-300` (*Dedicated Route Climb Detail View with Focused Map and Zoomed Isolated Elevation Profile*, Sprint 2026-41.3, ATT-2511).
- Introduces `REQ-UI-333` (*Entity Detail Sheet Scaffold & Climb Popup Harmonization via MapDetailLayout*).

### 2.2 Historical Origin & Commit Trace
- `REQ-UI-300` was authored under ticket `ATT-2511` in Sprint 2026-41.3 (`commit eb4a719d`) as the initial standalone climb inspection sheet using `AppModalBottomSheet` and 3 custom cards.
- `REQ-UI-315` was authored under ticket `ATT-2860` in Sprint 2026-41.5/41.6 (`commit 7a1d596f`) to unify bottom sheets. While `SegmentDetailSheet` and `RouteDetailSheet` were migrated to embed `SegmentOnMapScreen` and `RouteOnMapScreen` via `MapDetailLayout`, `ClimbDetailSheet` was not fully converted, and no shared scaffold composable was extracted.

### 2.3 Root Reason for Existing Formulation
In Sprint 2026-41.3, `Climb` entities did not possess an existing standalone map screen (unlike `SegmentOnMapScreen` and `RouteOnMapScreen`), so a quick 3-card stack within `AppModalBottomSheet` was written as an MVP. When ATT-2860 harmonized segments and routes, climbs were deferred due to time constraints, leaving the climb popup visually alien to athletes.

### 2.4 Preservation of Core Invariants
- `calculateClimbBounds`: Bounding box calculation for climbs with single-point fallback must remain intact.
- Metric precision: Start offset, climb distance, vertical gain, average grade, maximum grade, and UCI category chip must remain visible.
- Interaction safety: Dismissing the climb sheet via drag gesture, scrim click, or close button must not clear parent route selection, scroll state, or zoom level in `RouteOnMapScreen`.
- Clean-room test suite: 100% unit test pass rate with zero regressions.

---

## 3. Scope Bounding & User Grounding (`ATT-1250`)

### In-Scope:
1. **Shared Sheet Scaffold (`EntityDetailSheetScaffold`)**:
   - Create a reusable, decoupled base composable `EntityDetailSheetScaffold` in `com.atrainingtracker.trainingtracker.ui.components.core` (or `ui.map`).
   - Encapsulate `rememberModalBottomSheetState(skipPartiallyExpanded = true)`, `ModalBottomSheet`, `BottomSheetDesign.SheetShape`, `BottomSheetDesign.SheetTonalElevation`, `dragHandle = null`, `modifier.fillMaxHeight()`, and the standardized floating circular dismiss close button (`IconButton` with `CircleShape`, 2.dp elevation, alpha 0.85f).
   - Refactor `SegmentDetailSheet.kt` and `RouteDetailSheet.kt` to leverage `EntityDetailSheetScaffold`, eliminating boilerplate duplication.
2. **Climb Screen & Detail Architecture (`ClimbOnMapScreen.kt` & `ClimbDetailSheet.kt`)**:
   - Implement `ClimbOnMapScreen` (hosted in `ClimbDetailSheet` or standalone in `ui.climbs`) backed by `MapDetailLayout`, matching the exact visual rhythm of `SegmentOnMapScreen`:
     - Structured `ClimbHeader`: Sport icon (`BSportType.BIKE`), climb name (bold titleLarge), `ClimbCategoryChip`, route counter badge (`routeIndex of totalRouteClimbs`), and start distance along route.
     - Structured `ClimbDetails`: 3-row metric presentation (Row 1: Distance, Row 2: Average and Max Grade, Row 3: Ascent Gain and Min/Max Altitude) matching `SegmentDetails` design tokens.
     - `HorizontalDivider` between header and details.
     - Canonical `MapDetailLayout`: Embedded map with `MapZoomFocus.EXPLICIT_BOUNDS`, `initialBounds = calculateClimbBounds(climb)`, category-colored climb polyline, start/summit markers, and the interactive `ElevationProfile` chart with scrubbing synchronization.
   - Refactor `ClimbDetailSheet` to wrap `ClimbOnMapScreen` within `EntityDetailSheetScaffold`.
3. **Automated Contracts & Tests**:
   - Author `EntityDetailSheetScaffoldContractTest.kt` verifying shared scaffolding across Climb, Segment, and Route sheets.
   - Update `ClimbDetailSheetContractTest.kt` to verify `EntityDetailSheetScaffold`, `ClimbHeader`, `ClimbDetails`, `MapDetailLayout`, and bounding box calculations.
   - Verify 100% full clean-room unit test pass rate.

### Out-of-Scope:
- Modifying live climb sheet HUD during recording (`LiveClimbSheet.kt`).
- Redesigning route breakdown cards (`RouteClimbsBreakdownSection.kt`) or changing route GPX export.
- Altering the Strava segment matching algorithm (`RouteSegmentMatcher.kt`).

---

## 4. Technical Architecture Proposal

```
+-----------------------------------------------------------------------------------+
|                           EntityDetailSheetScaffold                                |
|  - ModalBottomSheet (skipPartiallyExpanded = true, shape = BottomSheetDesign)      |
|  - Top-End Floating Circular Dismiss Close Button (CircleShape, 2.dp elevation)   |
|  +-----------------------------------------------------------------------------+  |
|  | Slot: content()                                                             |  |
|  |                                                                             |  |
|  |   [Option A: SegmentDetailSheet] -> hosts SegmentOnMapScreen               |  |
|  |   [Option B: RouteDetailSheet]   -> hosts RouteOnMapScreen                 |  |
|  |   [Option C: ClimbDetailSheet]   -> hosts ClimbOnMapScreen                 |  |
|  |                                                                             |  |
|  |   Inside ClimbOnMapScreen (powered by MapDetailLayout):                    |  |
|  |   1. Header: ClimbHeader (Sport icon, Name, Category chip, Route counter)    |  |
|  |   2. Divider: HorizontalDivider(thickness = 0.5.dp)                         |  |
|  |   3. Details: ClimbDetails (Distance, Avg/Max Grade, Elevation Gain, Min/Max) |  |
|  |   4. MapDetailLayout Viewport:                                              |  |
|  |      - Collapsible Map (MapZoomFocus.EXPLICIT_BOUNDS, category polyline)   |  |
|  |      - ElevationProfile with interactive scrub synchronization              |  |
|  +-----------------------------------------------------------------------------+  |
+-----------------------------------------------------------------------------------+
```

---

## 5. Review & Gate 1 Criteria Verification
- [x] Problem statement incorporates direct user feedback and explains root causes of architectural divergence.
- [x] Chesterton's Fence archaeology explores `REQ-UI-300` and `REQ-UI-315` historical background.
- [x] Clear scope bounding separates in-scope shared scaffolding and popup harmonization from out-of-scope live recording features.
- [x] Architecture proposal de-duplicates code and establishes a common base composable across all three entity sheets.
