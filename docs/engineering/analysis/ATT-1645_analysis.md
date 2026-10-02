# Stage 1 Analysis: ATT-1645 - Calibrate and Optimize Initial Height & Peek Baselines for Popups and Bottom Sheets (Dynamic Self-Measuring Baselines)

**Ticket**: [ATT-1645](https://atrainingtracker.atlassian.net/browse/ATT-1645)  
**Sub-task**: [ATT-2009](https://atrainingtracker.atlassian.net/browse/ATT-2009) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.11`  
**Branch**: `feature/ATT-1645`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Problem Statement & Motivation

Across `aTrainingTracker`, modal and persistent bottom sheets provide athletes with instant peek digests of domain entities (Locations, Routes, Segments, Live Segments, and Workouts) before the sheet is dragged upward to reveal elevation profiles, telemetry charts, or deeper analytical tables.

During physical device review on Google Pixel 10 hardware in Sprint `2026-40.10`, static peek height constants (e.g. `192.dp` for Segments, `112.dp`/`152.dp` for Routes) repeatedly exhibited edge-case clipping:
1. **Segments Bottom Sheet (`SegmentOnMapScreen.kt`)**: The third metrics row of `SegmentDetails` (altitude icon, elevation gain, min altitude, max altitude) was occluded behind the system navigation bar when font scale, display zoom, or segment title wrapping varied.
2. **Routes Bottom Sheet (`RouteOnMapScreen.kt`)**: Route descriptions with differing line counts or lengths still suffered partial cut-offs behind the system navigation bar.
3. **Live Segment Bottom Sheet (`SensorGridScreen.kt` / `LIveSegmentSheet.kt`)**: User verified during Sprint 2026-40.10 testing that the live segment peek baseline ($126\text{dp}$) now looks correct on Pixel 10 hardware, confirming that precise framing is achievable.

Because static hardcoded Dp constants are fundamentally brittle in the face of user-customized font sizes, dynamic text wrapping, and device navigation bar heights, the solution formulated and approved in Ceremony 1 is **Dynamic Self-Measuring Header Peek Baselines**.

---

## 2. Root Cause Analysis (Forensic Investigation)

### Forensic Inspection of Static Peek Limitations

1. **Font Scale & Line Wrap Variability**:
   - `SegmentHeader` displays `summary.name` in `titleLarge` (~$28\text{dp}$) and `summary.climbCategory`. On standard display settings with short names, this requires one line. On larger display zooms or localized strings with longer segment names, the title wraps to two lines, adding $24\text{dp}$ to $32\text{dp}$ of unbudgeted vertical space.
   - `SegmentDetails` contains 3 distinct rows:
     - Row 1: Distance & Strava logo (~$28\text{dp}$)
     - Row 2: Grades average & maximum (~$24\text{dp}$)
     - Row 3: Altitude icon, Ascent, Min, Max (~$28\text{dp}$)
   - When any static constant is used, any variation in system font scale directly pushes Row 3 below the navigation bar boundary.

2. **Route Description Variability**:
   - Routes can have descriptions ranging from zero characters to long multi-sentence descriptions. Even with a binary distinction (`PeekHeightRoute` vs `PeekHeightRouteWithDescription`), a 3-line description will overflow a static $152\text{dp}$ baseline.

3. **Decoupled Architecture Between Scaffolding and Slotted Header**:
   - `BottomSheetScaffold` in `MapScreenWithTrack.kt` requires `sheetPeekHeight` as a parameter.
   - However, the actual header layout (`MinimumDragHandle() + Surface(header)`) is rendered deep inside `MapDetailLayout.kt`.
   - Without an upward measurement feedback loop from `MapDetailLayout` to `MapScreenWithTrack`, the scaffold has zero insight into the real rendered height of its child header.

---

## 3. Architectural Solution: Dynamic Self-Measuring Header Peek Baselines

### Architectural Design

```
+-----------------------------------------------------------------------------------+
| MapScreenWithTrack.kt                                                             |
|   var measuredSegmentHeaderHeight: Dp? by remember(selectedSegmentId)             |
|   var measuredRouteHeaderHeight: Dp? by remember(selectedRouteId)                 |
|                                                                                   |
|   sheetPeekHeight = (measuredHeaderHeight ?: fallbackBaseline) + navBarHeight     |
|                                                                                   |
|   BottomSheetScaffold(sheetPeekHeight = sheetPeekHeight) {                        |
|     sheetContent = {                                                              |
|       SegmentOnMapScreen(                                                         |
|         onHeaderHeightMeasured = { measuredSegmentHeaderHeight = it }             |
|       )                                                                           |
|     }                                                                             |
|   }                                                                               |
+-----------------------------------------------------------------------------------+
                                         ^
                                         | onHeaderHeightMeasured(heightDp)
+----------------------------------------+------------------------------------------+
| MapDetailLayout.kt                                                                |
|   Column(                                                                         |
|     modifier = Modifier.onGloballyPositioned { coordinates ->                     |
|       val heightDp = with(density) { coordinates.size.height.toDp() }             |
|       onHeaderHeightMeasured?.invoke(heightDp)                                    |
|     }                                                                             |
|   ) {                                                                             |
|     if (!useStatusBarsPadding) MinimumDragHandle()                                |
|     Surface(header) { header() }                                                  |
|   }                                                                               |
+-----------------------------------------------------------------------------------+
```

1. **Self-Measuring Container in `MapDetailLayout.kt`**:
   - Wrap `MinimumDragHandle()` and `Surface(header)` in a Column with `Modifier.fillMaxWidth().onGloballyPositioned { coordinates -> ... }`.
   - Convert pixel height to Dp via `LocalDensity.current`.
   - Pass measured Dp to optional callback `onHeaderHeightMeasured: ((Dp) -> Unit)? = null`.

2. **Screen-Level Forwarding**:
   - `SegmentOnMapScreen`: Accept `onHeaderHeightMeasured: ((Dp) -> Unit)? = null` and forward to `MapDetailLayout`.
   - `RouteOnMapScreen`: Accept `onHeaderHeightMeasured: ((Dp) -> Unit)? = null` and forward to `MapDetailLayout`.
   - `LIveSegmentSheet`: Retain `BottomSheetDesign.PeekHeightLiveSegment` ($126\text{dp}$), with optional measurement hook.

3. **Parent Scaffolding Integration (`MapScreenWithTrack.kt`)**:
   - Maintain `measuredSegmentHeaderHeight` and `measuredRouteHeaderHeight` remembered per entity ID.
   - Compute `sheetPeekHeight` as:
     - `selectedSegmentId != null -> (measuredSegmentHeaderHeight ?: BottomSheetDesign.PeekHeightSegment) + navBarHeight`
     - `selectedRouteId != null -> (measuredRouteHeaderHeight ?: defaultRoutePeek) + navBarHeight`
     - `selectedLocationId != null -> BottomSheetDesign.PeekHeightKnownLocation + navBarHeight`
   - Initial frame uses established fallback baselines; as soon as layout completes (first frame), the exact measured height locks the peek baseline cleanly above the navigation bar.

---

## 4. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Add `onHeaderHeightMeasured: ((Dp) -> Unit)? = null` to `MapDetailLayout.kt`.
  2. Implement `Modifier.onGloballyPositioned` measurement around `MinimumDragHandle() + Surface(header)` in `MapDetailLayout.kt`.
  3. Propagate `onHeaderHeightMeasured` through `SegmentOnMapScreen.kt` and `RouteOnMapScreen.kt`.
  4. Integrate measured peek heights in `MapScreenWithTrack.kt` with defensive fallback to `BottomSheetDesign` constants.
  5. Preserve calibrated `BottomSheetDesign.PeekHeightLiveSegment = 126.dp` for `LIveSegmentSheet.kt` / `SensorGridScreen.kt`.
  6. Unit and visual contract tests verifying fallback constants and measurement callbacks.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No modification to map rendering or GPS tracking pipelines.
  * No changes to workout heatmap peek baselines (`PeekHeightWorkout = 140.dp`).
  * No changes to favorite location sheets (`PeekHeightKnownLocation = 108.dp`).
  * Upward travel / expanded height constraint fixes belong to `ATT-2008`.

---

## 5. Chesterton's Fence Archaeology (`REQ-PRO-022`)

* **Target Requirement**: `REQ-UI-221` (Standardized Bottom Sheet Peek Baselines)
* **Original Form**: Established static Dp tokens in `BottomSheetDesign.kt` for each sheet type.
* **Historical Trace**: Commit `5e37030c` / Sprint 2026-40.8 & 2026-40.9.
* **Root Reason for Existing Formulation**: Providing clean, un-crowded peeks without hardcoding scattered magic numbers in individual screens.
* **Preservation of Core Invariants**: The standardized tokens in `BottomSheetDesign.kt` are preserved as immediate initial-render fallbacks and unit test baselines. The dynamic measurement enhances and supersedes static values at runtime to eliminate device font/density discrepancies.

---

## 6. System Invariants & Caller Stability

1. **Navigation Bar Inset Integrity**: `WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()` is strictly added to every non-zero sheet peek height.
2. **Defensive Fallback**: If measurement is delayed or unavailable, `sheetPeekHeight` safely falls back to `BottomSheetDesign` constants, preventing layout jumps or zero-height sheets.
3. **9-Language Localization Parity**: No user-facing text strings are added or altered.
4. **Touch & Gesture Stability**: Sheet dragging, swiping, and expansion behavior remain identical.

---

## 7. Risk Rating & Mitigation

* **Risk Level**: **LOW**
* **Technical Justification**: Non-invasive addition of an optional callback on `MapDetailLayout` with `Modifier.onGloballyPositioned`. When callback is null (existing callers like Workout Map, Live Segment, etc.), behavior is completely unchanged. Defensive fallback ensures robust rendering even in preview or testing environments.
