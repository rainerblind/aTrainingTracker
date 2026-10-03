# Stage 1: Problem Domain & Root Cause Analysis - ATT-2150: Prevent Map Squashing by Large Upper Metadata in Workout Details

**Ticket**: [ATT-2150](https://rainerblind.atlassian.net/browse/ATT-2150)  
**Sub-task**: [ATT-2159](https://rainerblind.atlassian.net/browse/ATT-2159) (`[Analysis]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `Sprint 2026-40.13`  
**Branch**: `feature/ATT-2150`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Problem Statement & Root Cause Analysis

### 1.1 Observed Symptoms
In `MapDetailLayout.kt`, the workout header and upper metadata (`metadataContent`, introduced in ATT-2112 and extended with Strava in ATT-2137) are rendered statically above the resizable map viewport `BoxWithConstraints(modifier = Modifier.weight(1f))`.
When a workout has extensive descriptions/notes, multiple extrema metrics, or Strava activity results, this upper section occupies a substantial portion of the vertical screen height (often 300–450dp on a typical 750–850dp phone viewport).
Consequently:
1. `BoxWithConstraints` receives only the remaining screen height (e.g. 250–350dp).
2. The map is squeezed down towards its minimum ergonomic height (`SplitPaneMath.MIN_MAP_HEIGHT` = 120dp).
3. The lower scrollable area (`lowerColumn`) for elevation profiles, telemetry charts (Heart Rate, Speed/Pace, Power), and zone distribution cards is constrained to a tiny window, forcing excessive scrolling and impairing data readability.

### 1.2 Root Cause Analysis
`MapDetailLayout.kt` previously arranged the header/metadata and `BoxWithConstraints` in a static vertical `Column` where the upper area consumed unconditional wrap-content height. Because the upper area did not participate in nested scrolling:
- Vertical scroll gestures on `lowerColumn` only scrolled the contents of `lowerColumn` within its restricted container.
- The upper metadata remained statically pinned, never releasing vertical space for the map and telemetry graphs.
- If the upper metadata exceeded `screenHeight - MIN_MAP_HEIGHT - MIN_LOWER_HEIGHT`, the map would be squashed against its ergonomic threshold or clip the layout.

---

## 2. Agreed Architectural Solution: Approach A (Collapsing Header via NestedScroll)

Analogous to the tabbed screens across the application (`SegmentsTabsScreen`, `EquipmentTabsScreen`, `WorkoutTabsScreen`):
1. **Collapsing AppBar NestedScroll Connection**:
   - Integrate `CollapsingAppBarNestedScrollConnection` into `MapDetailLayout.kt`.
   - The root container connects via `Modifier.nestedScroll(connection)`.
2. **Smooth Collapsing on Upward Scroll (AC-1)**:
   - When the athlete scrolls upward on `lowerColumn` (or on scrollable metadata), `connection.onPreScroll` consumes the upward scroll delta, moving `connection.appBarOffset` from `0` to `-appBarMaxHeight`.
   - The upper section (Header, Description, Extrema, Strava) translates upwards by `IntOffset(0, connection.appBarOffset)`.
   - The top padding of the viewport area decreases in lockstep, dynamically expanding `BoxWithConstraints` up to the **full screen height**, granting maximum real estate to the map, splitter, and telemetry graphs.
3. **Smooth Restoration on Downward Scroll (AC-2)**:
   - When the athlete scrolls downward, `onPreScroll` expands `appBarOffset` back towards `0`, smoothly sliding the header and metadata back into view.
4. **Guaranteed Minimum Map Height in Base State (AC-3)**:
   - To guarantee that an arbitrarily large metadata block never squashes the map below `SplitPaneMath.MIN_MAP_HEIGHT` (120dp) in the uncollapsed base state (`appBarOffset == 0`), the maximum initial height of the upper metadata container is bounded to `(totalScreenHeight - minRequiredViewport).coerceAtLeast(100.dp)` with an internal vertical scroll, where:
     $$\text{minRequiredViewport} = \text{MIN\_MAP\_HEIGHT} + \text{DIVIDER\_TOUCH\_HEIGHT} + \text{TOOLBAR\_HEIGHT} + \text{MIN\_LOWER\_HEIGHT}$$
   - This mathematically guarantees that the map viewport is NEVER compressed below `MIN_MAP_HEIGHT`.

---

## 3. Chesterton's Fence & Invariant Archaeology

1. **Slotted Custom Callers**:
   - `RouteOnMapScreen`, `SegmentOnMapScreen`, `WorkoutClusterHeatmapScreen`, and `LIveSegmentSheet` call `MapDetailLayout` with `metadataContent = null`. In these cases, `appBarMaxHeight` encompasses only the `header()`, preserving existing lightweight behavior.
2. **Bottom Sheet Mode (`!useStatusBarsPadding`)**:
   - In `LIveSegmentSheet`, `useStatusBarsPadding` is false, and `onHeaderHeightMeasured` is invoked. This measurement and sheet drag handle behavior are strictly preserved.
3. **Map Pan & Splitter Interaction**:
   - Map dragging and `SplitPaneDivider` dragging must not conflict with nested scroll gestures. The splitter continues to control `splitFraction` independently of the collapsing app bar offset.
4. **Localization Parity**:
   - Zero hardcoded strings introduced; full 9-language translation parity maintained.
