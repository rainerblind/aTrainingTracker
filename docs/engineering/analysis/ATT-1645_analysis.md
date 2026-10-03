# Stage 1 Analysis: ATT-1645 - Calibrate and Optimize Initial Height & Peek Baselines for Popups and Bottom Sheets (Flush Header-to-NavBar Baseline)

**Ticket**: [ATT-1645](https://atrainingtracker.atlassian.net/browse/ATT-1645)  
**Sub-task**: [ATT-2052](https://atrainingtracker.atlassian.net/browse/ATT-2052) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.12`  
**Branch**: `feature/ATT-1645`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Problem Statement & Motivation

Across `aTrainingTracker`, modal and persistent bottom sheets provide athletes with instant peek digests of domain entities (Segments and Routes) on map screens before the sheet is dragged upward to reveal elevation profiles, telemetry charts, or deeper analytical tables.

During physical device review on Google Pixel 10 hardware (Sprint `2026-40.11`), the resting peek behavior of the bottom sheets was evaluated as "still not ideal". 

During Ceremony 1 Sprint-Start screening for Sprint `2026-40.12`, the user explicitly formulated the precise visual and geometric invariant:
> *"There is the line between the white heading and the green map. This line must be exactly on top of the navigation bar."*

### Visual Defect
In collapsed peek state on physical hardware:
1. Either the peek height was taller than the white header card, causing an unsightly band of the green Google Map underneath the header to peek out above the system navigation bar;
2. Or static fallbacks and dynamic measurement timing left the header partially clipped or misaligned with the top edge of the navigation bar.

The target behavior is strict geometric alignment:
- The entire white header surface (drag handle, title, metrics, description) must be 100% visible above the system navigation bar.
- The separation line between the bottom of the white heading card and the top of the underlying green map must sit **flush with the top edge of the navigation bar**. Zero pixels of the green map should peek out above the bar, and zero pixels of the white header should be occluded behind the bar.

---

## 2. Root Cause Analysis (Forensic Investigation)

### Geometric & Layout Architecture in `MapScreenWithTrack` and `MapDetailLayout`

1. **Scaffold Coordinate System in Edge-to-Edge Mode**:
   - `MapScreenWithTrack.kt` operates in full edge-to-edge mode (`WindowInsets.navigationBars`).
   - `BottomSheetScaffold` anchors the sheet container at the bottom edge of the display.
   - `sheetPeekHeight` determines the vertical travel of the bottom sheet above the bottom edge of the display:
     $$\text{Top of Sheet } Y_{\text{top}} = \text{ScreenHeight} - \text{sheetPeekHeight}$$
   - The top edge of the system navigation bar sits at:
     $$Y_{\text{navBarTop}} = \text{ScreenHeight} - \text{navBarHeight}$$
   - Therefore, the visible window of the bottom sheet above the navigation bar has height:
     $$\text{VisibleHeight} = Y_{\text{navBarTop}} - Y_{\text{top}} = \text{sheetPeekHeight} - \text{navBarHeight}$$

2. **Decoupled Header vs. Green Map Layout in `MapDetailLayout`**:
   - Inside `MapDetailLayout.kt`, the content is laid out in a vertical `Column`:
     - Top Child: `Column(Modifier.fillMaxWidth().onGloballyPositioned { ... })` containing `MinimumDragHandle()` and `Surface(header)` (the **white heading**).
     - Second Child: `mapBox(Modifier.fillMaxSize())` or the resizable viewport (the **green map**).
   - The boundary line between the white heading and the green map is exactly the bottom edge of the header Column ($Y_{\text{headerBottom}} = Y_{\text{top}} + H_{\text{header}}$).
   - For this dividing line to sit flush on top of the navigation bar ($Y_{\text{headerBottom}} = Y_{\text{navBarTop}}$):
     $$(Y_{\text{top}} + H_{\text{header}}) = (Y_{\text{top}} + \text{VisibleHeight}) \implies \text{VisibleHeight} \equiv H_{\text{header}}$$
     $$\text{sheetPeekHeight} = H_{\text{header}} + \text{navBarHeight}$$

3. **Why the Green Map Peeked Out in Sprint 2026-40.11**:
   - **Oversized Initial Fallback Constants**: In `BottomSheetDesign.kt`, `PeekHeightSegment` was hardcoded to `192.dp`. The actual rendered height of `SegmentHeader + divider + SegmentDetails + MinimumDragHandle` is approximately $140\text{dp}$ to $148\text{dp}$.
   - When a segment was selected, `measuredSegmentHeaderHeight` was initially `null`, so the sheet partially expanded to $192\text{dp} + \text{navBarHeight}$. This immediately exposed $\approx 45\text{dp}$ to $50\text{dp}$ of the **green map** directly above the navigation bar!
   - **Missing Snap/Partial-Expand on Measurement Update**: In Jetpack Compose Material 3 `BottomSheetScaffold`, updating `sheetPeekHeight` dynamically after the sheet has already partially expanded does not automatically adjust the sheet's active resting offset if the bottom sheet state does not re-anchor or re-trigger `partialExpand()`.
   - **Route Description Variability**: Routes with and without descriptions used static constants ($112\text{dp}$ / $152\text{dp}$) that did not match the exact rendered height of `RouteSummaryHeader`.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Calibrate baseline tokens in `BottomSheetDesign.kt` to match the exact physical dimensions of headers ($144\text{dp}$ for Segments, $116\text{dp}$ for Routes without description, $152\text{dp}$ for Routes with description).
  2. In `MapScreenWithTrack.kt`, ensure `sheetPeekHeight` accurately reflects the measured header height ($H_{\text{header}} + \text{navBarHeight}$) for both Segments and Routes.
  3. Ensure that when `measuredSegmentHeaderHeight` or `measuredRouteHeaderHeight` updates, the bottom sheet resting offset aligns flush with the top edge of the navigation bar without exposing green map tiles.
  4. Ensure zero occlusion of any header row (including the altitude row in segments and full description text in routes).
  5. Validate on 3-button navigation mode on Google Pixel 10 hardware.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Locking favorite location sheets (handled separately under [ATT-2042](https://atrainingtracker.atlassian.net/browse/ATT-2042)).
  * Changes to workout heatmap peek baselines (`PeekHeightWorkout = 140.dp`).
  * Changes to live segment peek baselines (`PeekHeightLiveSegment = 126.dp`, verified working in Sprint 2026-40.10).
  * Upward expanded sheet travel adjustments (already completed in ATT-2008).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (`REQ-PRO-022`)

* **Target Requirement**: `REQ-UI-221` (Standardized Bottom Sheet Peek Baselines)
* **Original Form**: Established static Dp tokens in `BottomSheetDesign.kt` (`PeekHeightSegment = 192.dp`, `PeekHeightRoute = 112.dp`).
* **Historical Trace**: Commit `5e37030c` / Sprint 2026-40.8 & 2026-40.9.
* **Root Reason for Existing Formulation**: Static tokens were established to avoid arbitrary scattered numbers across screens and provide a safe upper bound.
* **Preservation of Core Invariants**: The fallback tokens in `BottomSheetDesign.kt` are maintained, but recalibrated downward to realistic header boundaries ($144\text{dp}$ for segments) so that initial unmeasured frames never expose the green map, while runtime self-measurement locks the line flush to the navigation bar.

---

## 5. Architectural Strategy & High-Level Solution

```
+-----------------------------------------------------------------------------------+
| MapScreenWithTrack.kt (BottomSheetScaffold)                                       |
|                                                                                   |
|   sheetPeekHeight = (measuredHeaderHeight ?: fallbackBaseline) + navBarHeight     |
|                                                                                   |
|   Visible Area Above NavBar: Exactly measuredHeaderHeight                         |
|   +-----------------------------------------------------------------------------+ |
|   | MapDetailLayout.kt                                                          | |
|   |   Column(onGloballyPositioned -> onHeaderHeightMeasured)                    | |
|   |   +-----------------------------------------------------------------------+ | |
|   |   | MinimumDragHandle() + Surface(header)           [WHITE HEADING]       | | |
|   |   +-----------------------------------------------------------------------+ | |
|   +---|-------------------------------------------------------------------------+-+
| ===== | ======================== DIVIDING LINE ================================ | ===== (Flush on NavBar Top Edge)
|       | [Navigation Bar Area] (3-Button or Gesture Bar)                         |
|       | Map tiles / lower viewport remain occluded behind navigation bar        |
+-------+-------------------------------------------------------------------------+
```

1. **Recalibrate Fallback Baselines in `BottomSheetDesign.kt`**:
   - `PeekHeightSegment = 144.dp` (recalibrated from 192dp).
   - `PeekHeightRoute = 116.dp` (recalibrated from 112dp).
   - `PeekHeightRouteWithDescription = 152.dp`.
2. **Measurement & Offset Alignment in `MapScreenWithTrack.kt`**:
   - Synchronize `measuredSegmentHeaderHeight` and `measuredRouteHeaderHeight`.
   - Ensure `sheetPeekHeight = measuredHeaderHeight + navBarHeight`.
   - If the sheet is in peek state when measurement completes, ensure the sheet offset smoothly matches the exact measured height.
3. **Verify Header Surface & Padding in `MapDetailLayout.kt`**:
   - Confirm that no extra vertical padding or spacer exists between the bottom of `Surface(header)` and the top of the map viewport.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Navigation bar insets (`WindowInsets.navigationBars`) are strictly accounted for across 3-button and gesture navigation modes.
  2. No regression in route selection or segment selection gestures.
  3. Clean fallback to calibrated tokens if measurement is delayed.
* **Risk Rating**: **LOW**
  - Adjustments are purely geometric layout calculations and token calibrations in `MapScreenWithTrack.kt` and `BottomSheetDesign.kt`.
