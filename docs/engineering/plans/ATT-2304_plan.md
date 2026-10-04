# Stage 3: Implementation Plan - ATT-2304: Restore Immediate Collapsing Header Expansion When Scrolling Down in Workout Details

**Ticket**: [ATT-2304](https://rainerblind.atlassian.net/browse/ATT-2304)  
**Sub-task**: [ATT-2344](https://rainerblind.atlassian.net/browse/ATT-2344) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-40.15`  
**Requirement Mapping**: `REQ-UI-266`  
**Test Mapping**: `TST-UI-225`  
**Branch**: `feature/ATT-2304`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Problem Description & Background

In the detailed workout view (`TrackOnMapScreen.kt` / `MapDetailLayout.kt`), scrolling upward collapses the upper metadata header off-screen to maximize viewport space for maps and graphs. However, during sprint review testing of ATT-2178 on physical hardware, downward scrolling failed to expand the header back into view. The header remained collapsed until the child scroll container was scrolled all the way to its topmost boundary (item 0).

This issue was caused by commit `91fb65c1` (ATT-2198 / ATT-2178), which moved downward scroll consumption entirely from `onPreScroll` to `onPostScroll` in `CollapsingAppBarNestedScrollConnection.kt`. Because child scroll containers consume downward deltas before reaching their top, `onPostScroll` never receives delta to expand the header.

This plan restores immediate Quick Return downward header expansion in `CollapsingAppBarNestedScrollConnection.kt` with sub-pixel float precision and seamless child handoff.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-266` (*Aftermath/Details: Immediate Collapsing Header Quick Return Expansion upon Downward Scrolling*)
* **Test Mapping**: `TST-UI-225` (*Immediate Collapsing Header Quick Return Expansion Verification*)

---

## 3. System Invariants & Preserved Behavior

1. **Sub-Pixel Floating-Point Precision (`REQ-UI-254`)**:
   Accumulation in `rawOffset: Float` must be strictly preserved to eliminate integer pixel truncation micro-stutters and frame jitter.
2. **Strict Clamping Bounds (`REQ-UI-250`)**:
   `rawOffset` and `appBarOffset` must remain bounded within `[-appBarMaxHeight, 0]`.
3. **Child Scroll Continuity & Non-Blocking Dispatch**:
   Once the header is fully expanded (`rawOffset == 0f`), downward scroll deltas must pass unconsumed to child containers. Once the header is fully collapsed (`rawOffset == -appBarMaxHeight`), upward scroll deltas must pass unconsumed to child containers.
4. **Minimum Map Viewport Height Protection (`REQ-UI-250`)**:
   In the uncollapsed base state (`appBarOffset == 0`), `maxMetadataHeightDp` calculations in `MapDetailLayout.kt` ensure the map viewport is never squashed below `SplitPaneMath.MIN_MAP_HEIGHT` (120dp).
5. **Caller Compatibility**:
   All 10 call sites of `CollapsingAppBarNestedScrollConnection` remain 100% binary and API compatible.
6. **Zero Premature Parent Completion**:
   Parent ticket `ATT-2304` terminal transition is ALWAYS `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `CollapsingAppBarNestedScrollConnection.kt`
* Path: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/utils/CollapsingAppBarNestedScrollConnection.kt`
* Add configurable parameter `val quickReturn: Boolean = true` (defaulting to `true`):
  ```kotlin
  class CollapsingAppBarNestedScrollConnection(
      initialAppBarMaxHeight: Int = 0,
      val quickReturn: Boolean = true
  ) : NestedScrollConnection
  ```
* In `onPreScroll(available: Offset, source: NestedScrollSource)`:
  - If `quickReturn` is enabled:
    - Handle both upward (`delta < 0f && rawOffset > -appBarMaxHeight`) and downward (`delta > 0f && rawOffset < 0f`) deltas.
    - Calculate new offset: `val newRaw = (rawOffset + delta).coerceIn(-appBarMaxHeight.toFloat(), 0f)`
    - Compute consumed delta: `val consumed = newRaw - rawOffset`
    - Update state: `rawOffset = newRaw`, `appBarOffset = newRaw.roundToInt()`
    - Return `Offset(0f, consumed)`
  - If `quickReturn` is disabled:
    - Retain legacy deferred behavior (upward only in `onPreScroll`).
* In `onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource)`:
  - Retain safety check for unconsumed downward delta if not in quick return or if excess delta remains:
    ```kotlin
    if (delta > 0f && rawOffset < 0f) {
        val newRaw = (rawOffset + delta).coerceIn(-appBarMaxHeight.toFloat(), 0f)
        val consumedY = newRaw - rawOffset
        rawOffset = newRaw
        appBarOffset = newRaw.roundToInt()
        return Offset(0f, consumedY)
    }
    ```

### Component 2: `CollapsingAppBarNestedScrollConnectionTest.kt`
* Path: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/utils/CollapsingAppBarNestedScrollConnectionTest.kt`
* Update existing tests and add new tests:
  - Verify downward scroll in `onPreScroll` consumes delta and expands `appBarOffset` towards 0 when partially or fully collapsed.
  - Verify downward scroll clamps at 0 and defers excess delta to child container.
  - Verify downward scroll returns `Offset.Zero` when already fully expanded.
  - Verify upward scroll collapse to `-appBarMaxHeight`.
  - Verify sub-pixel float precision accumulation.
  - Verify `quickReturn = false` preserves deferred `onPostScroll` behavior when explicitly requested.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update `CollapsingAppBarNestedScrollConnection.kt`
* Implement `quickReturn: Boolean = true` parameter.
* Implement bidirectional immediate scroll consumption in `onPreScroll` when `quickReturn == true`.
* Update KDoc with comprehensive architectural documentation (`REQ-PRO-011`).

### Step 2: Update and Extend Unit Tests in `CollapsingAppBarNestedScrollConnectionTest.kt`
* Update `testDownwardScroll_expandsInPreScroll_whenQuickReturnEnabled`:
  - Assert `onPreScroll` consumes downward delta and immediately expands `appBarOffset`.
* Add `testDownwardScroll_clampsAtZero_andDefersExcessToChild`:
  - Assert deltas exceeding remaining collapse range clamp to 0 and leave unconsumed delta for child.
* Add `testDownwardScroll_whenFullyExpanded_returnsZero`:
  - Assert zero consumption when already at 0.
* Add `testQuickReturnDisabled_defersToPostScroll`:
  - Assert backward compatibility when `quickReturn = false`.
* Re-verify upward collapse and sub-pixel float accumulation tests.

### Step 3: Targeted Unit Test Verification
* Execute targeted unit test command:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.utils.CollapsingAppBarNestedScrollConnectionTest"
  ```
* Assert 100% pass rate.

---

## 6. Verification & Rollback Plan

* **Verification**:
  - Step 3 targeted unit test suite.
  - Stage 5 clean-room full regression suite (`./gradlew testDebugUnitTest`).
  - Deploy debug APK to Pixel 10 test device (`./gradlew installDebug`) and verify downward expansion in workout details.
* **Rollback Plan**:
  - In case of issues, revert changes on `feature/ATT-2304`. Branch is isolated from `sprint/2026-40.15`.
