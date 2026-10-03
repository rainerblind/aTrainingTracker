# Stage 2: Requirement & Test Specification - ATT-2150: Prevent Map Squashing by Large Upper Metadata in Workout Details

**Ticket**: [ATT-2150](https://rainerblind.atlassian.net/browse/ATT-2150)  
**Sub-task**: [ATT-2160](https://rainerblind.atlassian.net/browse/ATT-2160) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `Sprint 2026-40.13`  
**Branch**: `feature/ATT-2150`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Formal Requirement Specification: REQ-UI-250

### REQ-UI-250: Aftermath/Layout: Collapsing Upper Metadata Architecture & Map Squashing Prevention via NestedScroll in MapDetailLayout

The system SHALL decouple upper workout metadata (header, description, extrema, Strava) from static viewport allocation in `MapDetailLayout.kt` by integrating a collapsible nested-scroll connection, preventing map squashing and enabling full-screen expansion for map and telemetry analytics (ATT-2150):

1. **Collapsing AppBar NestedScroll Connection (`MapDetailLayout.kt`)**:
   - The root container of `MapDetailLayout` SHALL attach a `CollapsingAppBarNestedScrollConnection` via `Modifier.nestedScroll(connection)`.
   - The upper section containing `header()` and optional `metadataContent` SHALL translate vertically using `Modifier.offset { IntOffset(0, connection.appBarOffset) }`.
   - On upward scroll gestures within `lowerColumn` (or upper metadata), `connection.appBarOffset` SHALL decrement from `0` to `-appBarMaxHeight`, collapsing the header and metadata off the top of the viewport.
   - The viewport container (`BoxWithConstraints`) SHALL dynamically expand into the released vertical space, granting the map and telemetry graphs up to the full screen height.

2. **Downward Scroll Restoration**:
   - On downward scroll gestures, `connection.appBarOffset` SHALL increment smoothly back towards `0`, sliding the header and metadata back into view.

3. **Guaranteed Minimum Map Height in Base State**:
   - In the uncollapsed base state (`connection.appBarOffset == 0`), the maximum initial height of the upper metadata container SHALL be bounded to:
     $$\text{maxAllowedHeight} = \text{totalScreenHeight} - (\text{MIN\_MAP\_HEIGHT} + \text{DIVIDER\_TOUCH\_HEIGHT} + \text{toolbarHeight} + \text{MIN\_LOWER\_HEIGHT})$$
   - An internal scroll state SHALL be attached to `metadataContent` if its contents exceed this threshold, ensuring that the map viewport is NEVER squashed below `SplitPaneMath.MIN_MAP_HEIGHT` (120dp) regardless of metadata volume.

4. **Preserved Invariants**:
   - Custom callers with `metadataContent == null` (Routes, Segments, Heatmaps, LiveSegmentSheet) retain their existing layout.
   - Bottom sheet presentation (`!useStatusBarsPadding`) with sheet drag handle and `onHeaderHeightMeasured` callback remains intact.
   - 9-language translation parity maintained.

---

## 2. Acceptance Criteria (Given-When-Then)

* **AC-1 (Collapsing Behavior on Upward Scroll)**:
  * *Given* the detailed workout view (`MapDetailLayout`) with active upper metadata (description, extrema, Strava),
  * *When* the athlete scrolls upward on the lower analytics content,
  * *Then* the upper metadata section SHALL smoothly slide upward out of the viewport, granting full screen height to the map and graphs.
* **AC-2 (Restoration on Downward Scroll)**:
  * *Given* a collapsed upper metadata section,
  * *When* the athlete scrolls downward,
  * *Then* the header and metadata SHALL smoothly slide back into view.
* **AC-3 (Minimum Map Height Guaranteed in Base State)**:
  * *Given* an arbitrarily large metadata block (long description, 10 extrema, Strava PRs),
  * *When* the view is displayed in the uncollapsed base state,
  * *Then* the map SHALL NEVER be squashed below `SplitPaneMath.MIN_MAP_HEIGHT` (120dp).

---

## 3. Formal Test Specification: TST-UI-209

### TST-UI-209: Aftermath/Layout: Collapsing Upper Metadata Architecture & Map Squashing Prevention via NestedScroll Verification
* **Scope**: Verify `MapDetailLayout.kt` integration of `CollapsingAppBarNestedScrollConnection`, offset translation, and viewport space expansion.
* **Methodology**:
  1. *Structural & Architecture Contract Test (`MapDetailLayoutCollapsingHeaderContractTest.kt`)*:
     - Verify `MapDetailLayout.kt` integrates `CollapsingAppBarNestedScrollConnection`.
     - Verify root container attaches `Modifier.nestedScroll(connection)`.
     - Verify upper metadata container applies `Modifier.offset { IntOffset(0, connection.appBarOffset) }`.
     - Verify viewport area expands as `connection.appBarOffset` changes.
  2. *Clean-Room Regression Suite Execution*:
     - Run `./gradlew testDebugUnitTest` verifying 100% pass rate across the full suite.
