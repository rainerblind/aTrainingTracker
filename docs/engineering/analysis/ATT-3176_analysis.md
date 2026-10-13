# Stage 1: Problem Domain & Root Cause Analysis - ATT-3176

## 1. Executive Summary & Problem Domain
* **Issue**: [ATT-3176](https://atrainingtracker.atlassian.net/browse/ATT-3176): `[Verbesserung] Default Live Segment and Climb bottom sheets to fully expanded state during tracking`
* **Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355): *Good and consistent UI*
* **Target Requirement**: Amend `REQ-UI-327` (`REQ-UI-343`)
* **Target Test Spec**: `TST-UI-303` (updating contract assertions in `SensorGridScreenClimbSuppressionTest.kt`)
* **Sprint**: `2026-41.9`

### Problem Description
Currently, when an athlete approaches or enters a live segment (`LiveSegmentSheet`) or climb (`LiveClimbSheet`) during an active tracking workout, `SensorGridScreen` initializes and animates the Material 3 `BottomSheetScaffold` into `SheetValue.PartiallyExpanded` (at peek height of `126.dp + navBarHeight`).
In this peeked state, only the top header card is visible. The elevation profile graph (`ClimbProfileCanvas`), instantaneous gradient visualization, and real-time pacing HUD telemetry remain occluded until the athlete manually swipes the bottom sheet upward.
Touching or swiping up a bottom sheet on a handlebar-mounted phone or while running is inconvenient, distracts from the road or trail, and introduces safety hazards.
The default behavior must be updated so that whenever a Live Segment or Climb popup appears, it is displayed **fully expanded** (`SheetValue.Expanded`) by default.

---

## 2. Forensic Investigation & Technical Root Cause

### 2.1 Code Touchpoint in `SensorGridScreen.kt`
In `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt`:
```kotlin
// Line 231:
val initialSheetValue = if (shouldShowBottomSheet) SheetValue.PartiallyExpanded else SheetValue.Hidden

// Line 240-250:
LaunchedEffect(shouldShowBottomSheet) {
    if (!shouldShowBottomSheet) {
        if (scaffoldState.bottomSheetState.currentValue != SheetValue.Hidden) {
            scaffoldState.bottomSheetState.hide()
        }
    } else {
        if (scaffoldState.bottomSheetState.currentValue == SheetValue.Hidden) {
            scaffoldState.bottomSheetState.partialExpand()
        }
    }
}
```
1. `initialSheetValue` evaluates to `SheetValue.PartiallyExpanded` when `shouldShowBottomSheet` is true on initial composition.
2. `LaunchedEffect(shouldShowBottomSheet)` executes `scaffoldState.bottomSheetState.partialExpand()` when transitioning from hidden (`!shouldShowBottomSheet`) to visible (`shouldShowBottomSheet`).
3. Consequently, the sheet always settles at `sheetPeekHeight` (`BottomSheetDesign.PeekHeightLiveSegment + navBarHeight`), clipping the elevation profile canvas and HUD metrics.

### 2.2 Preserved Swipe Down Ergonomics
The bottom sheet configuration in `SensorGridScreen.kt` declares:
```kotlin
sheetSwipeEnabled = (showLiveSegments || showLiveClimbs) && screenMode == ScreenMode.TRACKING && isTabActive
```
And `BottomSheetScaffold` supports drag gestures between `SheetValue.Expanded` and `SheetValue.PartiallyExpanded` when `sheetPeekHeight > 0.dp`.
Therefore, when defaulting to `SheetValue.Expanded`, the athlete still retains the full ability to swipe down to peek height (`SheetValue.PartiallyExpanded`) or dismiss if they wish to focus on underlying cockpit sensor tiles.

---

## 3. Chesterton's Fence & Requirement Archaeology

1. **Original Requirement ID & Target**:
   - `REQ-UI-327` (*Tracking Tab Live Climb and Segment Cockpit BottomSheet Active-Tab Gating & State Synchronization*).
   - Introduced in Sprint `2026-41.7` (`ATT-2945`).
2. **Historical Origin & Commit Trace**:
   - In `ATT-2945`, the primary goal was to eliminate bottom sheet peek artifacts on tabs where Live Climbs were disabled and avoid horizontal drag contention in `HorizontalPager`.
   - `SheetValue.PartiallyExpanded` was selected conservatively as the default state to avoid abruptly covering sensor tiles during early prototyping.
3. **Reason for Refinement (Chesterton's Fence Forensics)**:
   - Field testing and joint review demonstrated that during active segments and climbs, the elevation profile and climb grade are the most critical metrics needed by the athlete.
   - Forcing athletes to take their hands off handlebars at the base of a steep climb to swipe open the sheet defeats the purpose of automated in-ride intelligence.
   - Section 5.8 was already added to `docs/design_guidelines.md` establishing that live tracking bottom sheets must default to fully expanded.
4. **Preservation of Core Invariants**:
   - Active-tab gating (`isTabActive`) prevents cross-tab state bleeding.
   - Hiding the sheet (`SheetValue.Hidden`) when `!shouldShowBottomSheet` remains 100% intact.
   - Swipe gestures remain enabled for manual collapse or adjustment.

---

## 4. Scope Bounding & Invariants (Rule 26 / ATT-1250)

* **Bounded Scope**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt` (lines 231, 247).
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreenClimbSuppressionTest.kt` (lines 79, 102).
  - `docs/requirements.md` and `docs/tests.md`.
* **Zero Cross-Cutting Modifications**:
  - No changes to `LiveClimbSheet.kt`, `LiveSegmentSheet.kt`, `TrackingTabsScreen.kt`, or `TrackingViewModel.kt`.
  - No changes to sensor drivers, database schemas, or Android manifests.

---

## 5. ASPICE Traceability Matrix & Impact Analysis

| Component | Target File | Impact |
| :--- | :--- | :--- |
| **UI Presentation** | `SensorGridScreen.kt` | Initialize with `SheetValue.Expanded`; call `expand()` instead of `partialExpand()`. |
| **Contract Verification** | `SensorGridScreenClimbSuppressionTest.kt` | Update contract assertions to verify `SheetValue.Expanded` and `expand()`. |
| **Living Documentation** | `docs/requirements.md` | Add `REQ-UI-343` (or amend `REQ-UI-327`). |
| **Test Documentation** | `docs/tests.md` | Add `TST-UI-303`. |
| **Design Guidelines** | `docs/design_guidelines.md` | Verify Section 5.8 alignment. |
