# Stage 1 Analysis: ATT-1644 - Optimize LiveSegment Popup Appearance and Layout

**Ticket**: [ATT-1644](https://atrainingtracker.atlassian.net/browse/ATT-1644)  
**Sub-task**: [ATT-1664](https://atrainingtracker.atlassian.net/browse/ATT-1664) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.5`  
**Branch**: `feature/ATT-1644`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-30  

---

## 1. Problem Statement & Motivation

During on-device testing of the live tracking cockpit in Sprint `2026-40.4`, the user reviewed the LiveSegment popup (`SensorGridScreen.kt`, `LIveSegmentSheet.kt`, `MapDetailLayout.kt`):
- **Feedback on Drag Handle**: The dark grey drag handle affordance at the top of the popup ("grey thing at the top of the popup") was perceived as overly dominant, thick, dark, and unappealing.
- **User Guidance**: The user does not like the current prominent appearance. As a compromise, a very slight, subtle/muted grey or an alternative, much less intrusive affordance is requested.
- **Popup Margins & Padding**: The top padding between the handle and the segment title row ("🚴 Zum Bäcker") requires visual harmonization.
- **Theme Consistency**: The toned-down handle must look refined and elegant in both Light and Dark themes.

---

## 2. Root Cause Analysis (Forensic Investigation)

### A. Component Hierarchy & Hosting
1. In `SensorGridScreen.kt` (lines 108–128), `BottomSheetScaffold` uses `sheetDragHandle = null` and delegates drag handle rendering to sheet contents:
   ```kotlin
   BottomSheetScaffold(
       scaffoldState = scaffoldState,
       sheetShape = BottomSheetDesign.SheetShape,
       sheetShadowElevation = BottomSheetDesign.SheetShadowElevation,
       sheetTonalElevation = BottomSheetDesign.SheetTonalElevation,
       sheetDragHandle = null,
       sheetPeekHeight = if (showLiveSegments && screenMode == ScreenMode.TRACKING) 140.dp + navBarHeight else 0.dp,
       sheetSwipeEnabled = showLiveSegments,
       sheetContent = {
           if (showLiveSegments) {
               Box(modifier = Modifier.fillMaxWidth().sheetContour()) {
                   LiveSegmentSheet(liveSegment = activeSegment)
               }
           }
       }
   )
   ```
2. In `LIveSegmentSheet.kt`, the content is wrapped in `MapDetailLayout` with `useStatusBarsPadding = false`.
3. In `MapDetailLayout.kt` (lines 88–92), when `!useStatusBarsPadding`, the layout explicitly renders `MinimumDragHandle()`:
   ```kotlin
   if (!useStatusBarsPadding) {
       MinimumDragHandle()
   }
   ```
4. In `MinimumDragHandle.kt` (lines 41–57):
   ```kotlin
   @Composable
   fun MinimumDragHandle(modifier: Modifier = Modifier) {
       Box(
           modifier = modifier
               .fillMaxWidth()
               .padding(top = 12.dp, bottom = 6.dp),
           contentAlignment = Alignment.Center
       ) {
           Surface(
               modifier = Modifier.size(
                   width = BottomSheetDesign.DragHandleWidth, // 36.dp
                   height = BottomSheetDesign.DragHandleHeight // 4.dp
               ),
               color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
               shape = CircleShape
           ) {
               Box(Modifier.matchParentSize())
           }
       }
   }
   ```

### B. Root Causes of Visual Discontent:
1. **Excessive Visual Contrast (`onSurfaceVariant.copy(alpha = 0.5f)`)**:
   `onSurfaceVariant` in Material 3 is meant for high-contrast secondary text (`#49454F` in light theme). At 50% opacity, it appears as a stark, heavy `#8E8A90` bar that draws the eye away from the live segment title and metrics. By contrast, Material 3's `outlineVariant` token is expressly designed for subtle structural dividers and outlines.
2. **Heavy Dimensions ($36\text{dp} \times 4\text{dp}$)**:
   A thickness of 4dp combined with high contrast gives the pill a chunky appearance. Reducing the thickness to 3dp and width to 32dp yields a much sleeker, modern pill shape.
3. **Asymmetrical Vertical Padding**:
   `MinimumDragHandle` has `padding(top = 12.dp, bottom = 6.dp)`, followed by `SegmentHeader`'s `vertical = 4.dp`. This results in 12dp above the pill and 10dp below the pill. Changing the handle padding to `top = 8.dp, bottom = 4.dp` creates a uniform 8dp top margin and an 8dp gap to the header title text, eliminating wasted vertical space.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Refine `BottomSheetDesign.kt` tokens:
    - `DragHandleWidth`: update to `32.dp`.
    - `DragHandleHeight`: update to `3.dp`.
  * Refine `MinimumDragHandle.kt`:
    - Use `MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)` (or support custom color / dimensions parameter with refined defaults).
    - Refine container padding to `top = 8.dp, bottom = 4.dp`.
  * Harmonize spacing in `LiveSegmentSheet` / `MapDetailLayout`.
  * Verify visual appeal in both Light and Dark mode.
  * Update unit tests in `MinimumDragHandleTest.kt` to validate the refined dimensions.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Do not alter the peek height logic (`140.dp + navBarHeight`) or swipe mechanics of `BottomSheetScaffold`.
  * Do not change segment calculation, GPS proximity math, or Strava segment synchronization.
  * Do not alter modal bottom sheet behaviors (`AppModalBottomSheet`) beyond inheriting the refined, elegant handle appearance.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

* **Original Requirement ID & Target**: `REQ-UI-189` (*UI/Sheets: Standardized Boundaries, Contours, Drop Shadow Elevation & Drag Affordances*).
* **Historical Origin & Commit Trace**: Introduced in ticket `ATT-1588` (commit `69c84e1b`) under Epic `ATT-355`.
* **Root Reason for Existing Formulation**: `REQ-UI-189` sought to guarantee unambiguous touch affordance across sheets by introducing a standardized 36dp x 4dp handle.
* **Refinement Rationale**: Live user feedback proved that 36dp x 4dp with `onSurfaceVariant(0.5f)` is visually heavy and distracting in the real cockpit context. A subtle, muted `outlineVariant(0.6f)` handle of dimensions 32dp x 3dp preserves affordance while matching the refined, minimalist aesthetic demanded by the user.
* **Preservation of Core Invariants**:
  - Edge-to-edge window insets (`REQ-UI-148`) remain 100% intact.
  - Sheet contour border (`1.dp` `outlineVariant`) and drop shadow (`8.dp`) are preserved.
  - Sheet touch targets and drag gesture recognition are preserved.

---

## 5. Architectural Strategy & High-Level Solution

1. **Design Tokens (`BottomSheetDesign.kt`)**:
   - `DragHandleWidth: Dp = 32.dp`
   - `DragHandleHeight: Dp = 3.dp`
2. **Composable Refinement (`MinimumDragHandle.kt`)**:
   - Add parameters with elegant defaults:
     ```kotlin
     @Composable
     fun MinimumDragHandle(
         modifier: Modifier = Modifier,
         width: Dp = BottomSheetDesign.DragHandleWidth,
         height: Dp = BottomSheetDesign.DragHandleHeight,
         color: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
         topPadding: Dp = 8.dp,
         bottomPadding: Dp = 4.dp
     )
     ```
3. **Verification**:
   - Update `MinimumDragHandleTest.kt` to assert dimensions `32dp` and `3dp`.
   - Run targeted Compose unit tests.

---

## 6. System Invariants & Risk Assessment

| Invariant / Risk | Mitigation |
| :--- | :--- |
| **Touch Target Area** | The container `Box` maintains full width and comfortable vertical bounds (`8.dp` top + `4.dp` bottom + `3.dp` handle = `15.dp`), ensuring drag gesture events continue to be captured reliably by `BottomSheetScaffold`. |
| **Theme Contrast** | `outlineVariant` at 60% alpha provides an elegant, low-contrast pill in both Light and Dark themes without harsh black/dark-grey artifacts. |
| **No Regression on Modal Sheets** | All modal sheets (`AppModalBottomSheet`, `FilterBottomSheetScaffold`) use `MinimumDragHandle()`, automatically inheriting the cleaner visual aesthetic. |
