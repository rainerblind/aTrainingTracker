# Stage 1 Analysis: ATT-2860 - Visual and UX styling polish for Climb and Segment detail bottom sheets

**Ticket**: [ATT-2860](https://atrainingtracker.atlassian.net/browse/ATT-2860)  
**Sub-task**: [ATT-2949](https://atrainingtracker.atlassian.net/browse/ATT-2949) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-41.6`  
**Branch**: `feature/ATT-2860`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Problem Statement & Motivation

During Sprint 2026-41.5 review on a physical Google Pixel 10, the human user evaluated the previous implementation of `ATT-2860` (which attempted to polish `SegmentDetailSheet` and `ClimbDetailSheet` within an ad-hoc three-card column layout) and emphatically rejected it:
> *"Revision needed: Human user feedback during Sprint Review: Absolutely not satisfied. The segment popup must look identical to the one that pops up in the map. We should use the same code here. The route popup should follow this layout."*

### Current State vs. Expected Behavior
* **Current State**:
  * Tapping a segment on the general map (`MapScreenWithTrack.kt`) presents `SegmentOnMapScreen`, featuring the canonical `SegmentHeader` (sport icon, title, category badge, PR badge), `HorizontalDivider`, `SegmentDetails` (distance, Strava branding, grade, elevation gain, extrema), and `MapDetailLayout` (interactive map with zoom focus, draggable split-pane, interactive elevation profile, and matching routes breakdown).
  * In contrast, tapping a segment in the route breakdown list (`RouteOnMapScreen.kt`) opened `SegmentDetailSheet.kt` (`AppModalBottomSheet`), which rendered a completely disparate, vertically scrollable column of three cards: `SegmentDetailMetricsCard`, an embedded secondary map card (`SegmentDetailMapCard`), and a separate elevation profile card (`SegmentDetailElevationProfileCard`).
  * Similarly, tapping a route in the segment routes list opened `RouteDetailSheet.kt`, which replicated the same disjointed three-card layout instead of the rich, unified presentation of `RouteOnMapScreen.kt`.
  * `ClimbDetailSheet.kt` similarly duplicated this disconnected three-card architecture.
* **Expected Behavior**:
  * The segment popup/sheet (`SegmentDetailSheet`) MUST look identical to the one that pops up in the map (`SegmentOnMapScreen`), reusing the exact same composable code and architecture (`MapDetailLayout`).
  * The route popup/sheet (`RouteDetailSheet`) MUST follow this unified layout by reusing `RouteOnMapScreen`.
  * `ClimbDetailSheet` MUST align with this canonical architecture, providing consistent header hierarchy and `MapDetailLayout` integration across all spatial entities.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Architectural Divergence & Duplication
In earlier iterations (ATT-2774, ATT-2861), detail sheets were created as quick modal inspectors wrapping isolated cards inside a vertically scrolling `AppModalBottomSheet`. This caused several severe issues:
1. **Severe Code Duplication**:
   - `SegmentDetailSheet.kt` (453 lines) duplicated metric formatting, bounding box calculation, marker placement, and map setup already implemented in `SegmentOnMapScreen.kt`.
   - `RouteDetailSheet.kt` (329 lines) duplicated route metrics, start/end pins, and elevation charts already implemented in `RouteOnMapScreen.kt`.
2. **Gesture Collisions & Scroll Competition**:
   - Nesting an interactive Google Map (`ATrainingTrackerMap`) inside a vertically scrolling `ModalBottomSheet` column causes touch and pan gesture conflicts. In contrast, `MapDetailLayout` natively manages draggable split-panes, map interactions, and scrollable analytics without gesture fighting.
3. **Disjointed User Experience**:
   - Athletes saw two completely different UIs for the exact same entity depending on whether they tapped it on the general map or in a list/breakdown section.

### 2.2 Reusable Architecture with `MapDetailLayout`
`MapDetailLayout.kt` is the application's established core layout for entity inspection (Aftermath, Routes, Segments). It provides:
- Top slot: `header` (e.g. `SegmentHeader` + `SegmentDetails` or `RouteHeader` + `RouteDetails`) with measured peek heights.
- Center slot: `mapContent` (`ATrainingTrackerMap` with bounding box focus and polylines).
- Split-pane divider with draggable height adjustment (`REQ-UI-223`).
- Bottom slot: interactive elevation profile + scrollable `analyticsContent`.

By embedding `SegmentOnMapScreen` and `RouteOnMapScreen` inside a modal sheet container (or exposing a composable that hosts `MapDetailLayout` with a dismiss button), modal inspection achieves 100% visual identity, direct code reuse, and zero duplication.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. **Segment Popup Unification**: Refactor `SegmentDetailSheet.kt` so it renders `SegmentOnMapScreen` directly within a modal bottom sheet container, reusing the exact same code, header, map, and analytics breakdown.
  2. **Route Popup Layout Alignment**: Refactor `RouteDetailSheet.kt` so it reuses `RouteOnMapScreen` with `MapDetailLayout`, eliminating the redundant card column.
  3. **Climb Detail Alignment**: Align `ClimbDetailSheet.kt` to share this unified header hierarchy and `MapDetailLayout` presentation.
  4. **Dismiss & Route Index Handling**: Ensure modal presentation supports seamless dismiss (close button / drag handle) and displays route contextual badges (e.g. route climb/segment counter) cleanly.
  5. **Code Elimination**: Remove obsolete, duplicate card composables (`SegmentDetailMetricsCard`, `SegmentDetailMapCard`, `SegmentDetailElevationProfileCard`, `RouteDetailMetricsCard`, etc.).
  6. **Quality & Localization**: Maintain 100% 9-language localization parity and 100% unit test pass rate.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No modification to underlying Strava segment matching algorithms (`RouteSegmentMatcher.kt`) or climb detection algorithms (`ClimbDetector.kt`).
  * No changes to database schemas (`Routes.db`, `Climbs.db`).
  * No alterations to in-workout tracking HUDs (`ReturnNavigationHud`, `TurnPromptBanner`, `ForkDecisionCard`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-UI-315` (*Harmonized Visual & UX Styling for Climb and Segment Detail Bottom Sheets*, amending `REQ-UI-300` and `REQ-UI-303`).
* **Historical Origin & Commit Trace**: Ticket `ATT-2860`, Sprint `2026-41.5`, commit `0773a9f7` (Retro ATT-2867).
* **Root Reason for Existing Formulation**: `REQ-UI-315` originally attempted to harmonize the existing 3-card stack within `AppModalBottomSheet` by adjusting corner radii to 16.dp, fixing metric labels, and tweaking colors.
* **Preservation of Core Invariants**: The 3-card stack was rejected by the product owner because it broke visual unity with the map popup. Amending `REQ-UI-315` to mandate direct code reuse of `SegmentOnMapScreen` and `MapDetailLayout` fulfills the true user intent while strictly preserving all underlying invariants: entity bounds calculation, dismiss gestures, sport type filtering, and test contracts.

---

## 5. Architectural Strategy & High-Level Solution

1. **Unified Sheet Modal Container**:
   - Create a clean modal container `EntityDetailModalSheet` (or configure `ModalBottomSheet` / `AppModalBottomSheet` with `scrollable = false` and `dragHandle = null`) that hosts `MapDetailLayout`-based screens with maximum viewport height and standard edge-to-edge system insets.
2. **Segment Detail Sheet Refactoring**:
   - In `SegmentDetailSheet.kt`, remove the custom card stack.
   - Delegate directly to `SegmentOnMapScreen`, passing `segmentSummary`, `segment` (as `MapSegment`), and `candidateRoutes`.
   - Include a dismiss button / close action in the header or overlay.
3. **Route Detail Sheet Refactoring**:
   - In `RouteDetailSheet.kt`, remove the custom card stack.
   - Delegate directly to `RouteOnMapScreen`, passing `route` and `routeSummary`.
4. **Climb Detail Sheet Alignment**:
   - Refactor `ClimbDetailSheet.kt` to leverage `MapDetailLayout` with a dedicated `ClimbHeader` and `ClimbDetails` pattern matching `SegmentHeader`/`SegmentDetails`.
5. **Contract Tests & Parity**:
   - Update contract tests (`SegmentDetailSheetContractTest.kt`, `RouteDetailSheetContractTest.kt`, `ClimbDetailSheetTest.kt`) to verify the unified architecture.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression across all existing unit and contract tests.
  2. Map gestures (pan, zoom, double-tap) and elevation profile scrub gestures must operate smoothly without scroll conflicts.
  3. Seamless sheet dismissal returns athlete to previous screen without state reset.
  4. 100% 9-language localization parity across all supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
* **Risk Rating**: **LOW**
  - Consolidating divergent card implementations into established, thoroughly tested components (`SegmentOnMapScreen`, `RouteOnMapScreen`, `MapDetailLayout`) reduces codebase complexity, eliminates duplicate code, and hardens UI consistency across the entire app.
