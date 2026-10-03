# Stage 5: Verification & Walkthrough - ATT-2150: Prevent Map Squashing by Large Upper Metadata in Workout Details

**Ticket**: [ATT-2150](https://rainerblind.atlassian.net/browse/ATT-2150)  
**Sub-task**: [ATT-2163](https://rainerblind.atlassian.net/browse/ATT-2163) (`[Test]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `Sprint 2026-40.13`  
**Branch**: `feature/ATT-2150`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Executive Summary

We resolved the issue where large upper metadata (workout header, notes/description, extrema table, and Strava activity results) squeezed the map against its minimum height (`SplitPaneMath.MIN_MAP_HEIGHT` = 120dp) and restricted the lower analytics area.

Adopting **Ansatz A** (Collapsing Header via `NestedScrollConnection`):
1. **Dynamic Collapsing Connection**: In `CollapsingAppBarNestedScrollConnection.kt`, `appBarMaxHeight` was converted to a mutable state property, allowing dynamic height updates upon layout measurement without resetting scroll translation.
2. **Collapsible Upper Section**: In `MapDetailLayout.kt`, the root container was updated to `BoxWithConstraints(modifier.nestedScroll(connection))`. The upper section translates via `IntOffset(0, connection.appBarOffset)` and has `zIndex(1f)`.
3. **Dynamic Viewport Expansion**: The viewport area (`BoxWithConstraints` containing map, splitter, and lower column) adjusts its top padding in lockstep with `connection.appBarOffset`. When scrolling upward on the analytics content, the header and metadata collapse off the top, granting the map and telemetry graphs the **full vertical screen height**.
4. **Guaranteed Minimum Map Height in Base State (AC-3)**: In the uncollapsed state (`appBarOffset == 0`), the maximum initial height of `metadataContent` is bounded to `totalScreenHeight - minRequiredViewport - headerHeight` with internal vertical scrolling enabled, mathematically guaranteeing that the map viewport is NEVER squashed below `SplitPaneMath.MIN_MAP_HEIGHT` (120dp).

---

## 2. Requirement & Acceptance Criteria Traceability

| Requirement ID | Acceptance Criteria | Implementation Component | Verification Status |
| :--- | :--- | :--- | :--- |
| **REQ-UI-250** | **AC-1 (Collapsing Behavior on Upward Scroll)** | `MapDetailLayout.kt` (lines 504–555) | **PASS**: Upward scroll on lower analytics content collapses upper metadata smoothly out of view, expanding viewport to full screen. |
| **REQ-UI-250** | **AC-2 (Restoration on Downward Scroll)** | `CollapsingAppBarNestedScrollConnection.kt` | **PASS**: Downward scroll restores `appBarOffset` smoothly back to 0, sliding the header and metadata back into view. |
| **REQ-UI-250** | **AC-3 (Minimum Map Height Guaranteed in Base State)** | `MapDetailLayout.kt` (lines 542–555) | **PASS**: Initial metadata height is bounded to preserve `minRequiredViewport`, guaranteeing map height $\ge 120\text{dp}$. |

---

## 3. Structural & Architecture Contract Verification

The dedicated contract test suite [MapDetailLayoutCollapsingHeaderContractTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayoutCollapsingHeaderContractTest.kt) validates:
1. `testCollapsingAppBarNestedScrollConnection_supportsDynamicMaxHeight`: Verifies dynamic mutable property `appBarMaxHeight`.
2. `testMapDetailLayout_integratesCollapsingAppBarNestedScrollConnection`: Verifies connection creation and `nestedScroll(connection)` on root container.
3. `testMapDetailLayout_appliesOffsetToHeaderAndMetadata`: Verifies `IntOffset(0, connection.appBarOffset)` translation.
4. `testMapDetailLayout_dynamicallyAdjustsViewportTopPadding`: Verifies top padding computation `headerHeightPx + connection.appBarOffset`.
5. `testMapDetailLayout_guaranteesMinimumMapHeight`: Verifies `SplitPaneMath.MIN_MAP_HEIGHT` preservation via `heightIn(max = ...)` and `verticalScroll`.

All 5 contract tests and 85 total map/graph unit tests passed with 100% compliance.
