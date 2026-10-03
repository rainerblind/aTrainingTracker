# Stage 3: Implementation Plan - ATT-2150: Prevent Map Squashing by Large Upper Metadata in Workout Details

**Ticket**: [ATT-2150](https://rainerblind.atlassian.net/browse/ATT-2150)  
**Sub-task**: [ATT-2161](https://rainerblind.atlassian.net/browse/ATT-2161) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `Sprint 2026-40.13`  
**Branch**: `feature/ATT-2150`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Architectural Strategy & SWE.2 Design

We resolve the map squashing problem by adopting **Ansatz A** (Collapsing Header via `NestedScrollConnection`):
1. **Dynamic Max Height in `CollapsingAppBarNestedScrollConnection.kt`**:
   - Update `CollapsingAppBarNestedScrollConnection` to permit updating `appBarMaxHeight` dynamically via `var appBarMaxHeight by mutableIntStateOf(appBarMaxHeight)`.
   - This maintains 100% backward compatibility with all existing tabbed screens while allowing `MapDetailLayout` to adjust to dynamically measured metadata heights without resetting scroll position.
2. **Root Container & Viewport Restructuring in `MapDetailLayout.kt`**:
   - Change the root container of `MapDetailLayout` to `BoxWithConstraints(modifier.nestedScroll(connection))`.
   - The viewport container (`BoxWithConstraints` for map and lower column) is placed with top padding equal to `(headerHeightPx + connection.appBarOffset).coerceAtLeast(0).toDp()`.
   - When the user scrolls upward, `connection.appBarOffset` decreases to `-headerHeightPx`, collapsing the header off the screen and decreasing top padding to 0, giving the viewport full screen height.
   - When the user scrolls downward, the header and metadata slide smoothly back down.
3. **Guaranteed Minimum Map Height in Base State (AC-3)**:
   - In the initial base state (`connection.appBarOffset == 0`), `metadataContent` is bounded to `maxMetadataHeight = totalScreenHeight - minRequiredViewport - headerHeight` with internal vertical scroll enabled, ensuring the map is NEVER compressed below `SplitPaneMath.MIN_MAP_HEIGHT` (120dp).

---

## 2. Step-by-Step Implementation Work Breakdown

### Step 1: Dynamic Max Height in `CollapsingAppBarNestedScrollConnection.kt`
- File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/utils/CollapsingAppBarNestedScrollConnection.kt`
- Change `appBarMaxHeight` constructor parameter to a mutable property:
  ```kotlin
  class CollapsingAppBarNestedScrollConnection(
      appBarMaxHeight: Int
  ) : NestedScrollConnection {
      var appBarMaxHeight by mutableIntStateOf(appBarMaxHeight)
  ...
  ```
- Retain existing `onPreScroll` consumption and math.

### Step 2: Restructure `MapDetailLayout.kt` for Collapsing Header via NestedScroll
- File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt`
- Integrate `val connection = remember { CollapsingAppBarNestedScrollConnection(0) }`.
- Restructure root container to connect `Modifier.nestedScroll(connection)`.
- Apply `Modifier.offset { IntOffset(0, connection.appBarOffset) }` to upper header and metadata container.
- Update `connection.appBarMaxHeight = measuredHeight` inside `onGloballyPositioned`.
- Set viewport top padding to `(headerHeightPx + connection.appBarOffset).coerceAtLeast(0).toDp()`.
- Bound `metadataContent` to guarantee `minRequiredViewport` in base state (`MIN_MAP_HEIGHT` = 120dp preserved).

### Step 3: Implement Structural & Architecture Contract Tests
- File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayoutCollapsingHeaderContractTest.kt`
- Verify `MapDetailLayout.kt` attaches `nestedScroll` connection.
- Verify upper metadata container applies `IntOffset(0, connection.appBarOffset)` or `appBarOffset`.
- Verify viewport area expands as `connection.appBarOffset` collapses.
- Verify `SplitPaneMath.MIN_MAP_HEIGHT` boundary preservation.

### Step 4: Verification & Clean-Room Regression Suite
- Run `./gradlew testDebugUnitTest --tests "*MapDetailLayout*"`
- Run full regression suite `./gradlew testDebugUnitTest` verifying 100% pass rate.

---

## 3. Invariants & Guardrails

- **Bottom Sheet Mode**: In `LIveSegmentSheet`, `useStatusBarsPadding = false` and `onHeaderHeightMeasured` must continue to measure header height and emit callback.
- **Contract Test Continuity**: `MapDetailLayoutMetadataSlotContractTest.kt` and `TracklessAftermathVisualContractTest.kt` must pass without modifications or regressions.
- **9-Language Translation Parity**: Retained at 100%.
