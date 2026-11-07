# Stage 3: Implementation Plan - ATT-5826: Fix horizontal tab swipe from 'Map & Graphs' tab

**Ticket**: [ATT-5826](https://rainerblind.atlassian.net/browse/ATT-5826)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*) | **Story Points**: 2 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.10`  
**Requirement Mapping**: `REQ-UI-464` (`REQ-UI-464.1`, `REQ-UI-464.2`, `REQ-UI-464.3`, `REQ-UI-464.4`, refining `REQ-UI-408.4`)  
**Test Mapping**: `TST-UI-464` (`TST-UI-464.1`, `TST-UI-464.2`, `TST-UI-464.3`, `TST-UI-464.4`, slaved to `TST-UI-371.6`)  
**Branch**: `feature/ATT-5826`  
**Author**: AI Agent 1 (`subagent_pro`)  
**Date**: 2026-11-07  

---

## 1. Problem Description & Background
Athletes on the "Map & Graphs" tab (`tab_map_graphs`) in `WorkoutDetailTabbedContent.kt` cannot swipe horizontally to adjacent tabs because `MapDetailLayout` is wrapped in a blanket `isTouchingInteractiveCanvas = true` touch interceptor, `lastTouchXDp` is overwritten continuously during `MOVE` events, and `HorizontalPager` suffers from 1-frame recomposition lag on initial touch down.

---

## 2. Traceability & Requirements Mapping
* **`REQ-UI-464.1` (Edge Gutter Lock)**: Latch X-position on initial `ACTION_DOWN` with `32.dp` gutter; preserve across `MOVE`. -> `TST-UI-464.1`
* **`REQ-UI-464.2` (Granular Canvas Bounding)**: Restrict canvas isolation strictly to `mapBox` and chart plot area (`50.dp .. width - 52.dp`). -> `TST-UI-464.3`
* **`REQ-UI-464.3` (Non-Interactive Strip Pass-Through)**: Divider, toolbar, headers, spacers, Y-axis (`0..50.dp`), and histogram margins (`52.dp`) clear canvas touch. -> `TST-UI-464.2`
* **`REQ-UI-464.4` (Zero 1-Frame Lag)**: Synchronous down evaluation on `HorizontalPager` without dropped gestures. -> `TST-UI-464.1`, `TST-UI-464.4`

---

## 3. System Invariants & Preserved Behavior
1. **Scrubbing & 2D Pan Invariant**: Horizontal chart scrubbing and map panning inside active areas remain 100% isolated from pager swipe.
2. **Scroll Fluidity**: Vertical scrolling in lower pane and `SplitPaneDivider` vertical dragging remain unimpeded.
3. **Backward Compatibility**: Optional `onInteractiveCanvasTouch` callback defaults to `null` on `MapDetailLayout`, keeping all existing callers intact.
4. **Code Lock**: Strict enforcement of Rule 4; zero production code modified until Gate 3 passes.

---

## 4. Proposed Architectural Changes
* **`WorkoutDetailTabConfig.kt`**: Default `edgeSwipeGutterDp: Float = 32f` in `requiresHorizontalGestureIsolation`.
* **`MapDetailLayout.kt`**: Expose `onInteractiveCanvasTouch: ((Boolean) -> Unit)? = null`. Mark `mapBox` and chart plot regions as interactive (`true`); mark divider, toolbar, headers, spacers, and axis/histogram strips as neutral (`false`).
* **`WorkoutDetailTabbedContent.kt`**:
  * Latch `lastTouchXDp` strictly on `DOWN` (`changedToDown()`); do not update during `MOVE`.
  * Default `isTouchingInteractiveCanvas = false` at rest, eliminating the 1-frame recomposition drop.
  * Excise outer blanket `pointerInput { isTouchingInteractiveCanvas = true }` wrappers; bind `onInteractiveCanvasTouch`.
* **UI Consistency**: Reuses existing `SplitPaneMath`, `GlobalTelemetryZoomToolbarDefaults`, and MaterialTheme tokens; zero new one-off styles.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update `WorkoutDetailTabConfig.kt` Edge Gutter Default (`REQ-UI-464.1`)
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDetailTabConfig.kt`
* Change default `edgeSwipeGutterDp` parameter from `24f` to `32f` in `requiresHorizontalGestureIsolation`.

### Step 2: Add Granular Canvas Touch Notification to `MapDetailLayout.kt` (`REQ-UI-464.2`, `REQ-UI-464.3`)
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt`
* Add `onInteractiveCanvasTouch: ((Boolean) -> Unit)? = null` parameter.
* Apply touch pass-through (`false`) to divider, zoom toolbar, chart headers, and spacers.
* Apply interactive touch (`true`) to `mapBox` and chart plot canvases (within `50.dp .. width - (if (isLandscape) 0.dp else 52.dp)`).

### Step 3: Refactor `WorkoutDetailTabbedContent.kt` Gesture Handling (`REQ-UI-464.1`, `REQ-UI-464.4`)
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDetailTabbedContent.kt`
* Initialize `isTouchingInteractiveCanvas = false` at rest.
* Latch `lastTouchXDp` on initial `DOWN` pass and lock until all pointers are released.
* Remove outer `isTouchingInteractiveCanvas = true` pointerInput wrappers in Mode A, B, and C; pass callback to `MapDetailLayout`.

### Step 4: Unit & Contract Tests in `WorkoutDetailTabsTest.kt` (`TST-UI-464.1` - `TST-UI-464.3`)
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDetailTabsTest.kt`
* Add test cases verifying initial down latch at `32.dp`, neutral strip pass-through, and active plot isolation.

### Step 5: Verification & Governance Audit (`TST-UI-464.4`)
* Run:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*" --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*" --max-workers=2
  python3 tools/verify_requirement_governance.py
  ```

---

## 6. Verification & Rollback Plan
* **Verification**: 100% pass across targeted unit and contract tests with zero regressions on map/chart scrubbing or tab navigation.
* **Rollback**: `git checkout -- app/src/` cleanly reverts implementation changes.
