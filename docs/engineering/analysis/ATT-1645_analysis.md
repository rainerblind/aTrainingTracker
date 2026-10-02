# Stage 1 Analysis: ATT-1645 - Calibrate and Optimize Initial Height & Peek Baselines for Popups and Bottom Sheets

**Ticket**: [ATT-1645](https://atrainingtracker.atlassian.net/browse/ATT-1645)  
**Sub-task**: [ATT-1961](https://atrainingtracker.atlassian.net/browse/ATT-1961) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.9`  
**Branch**: `feature/ATT-1645`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Problem Statement & Motivation

Across `aTrainingTracker`, modal and persistent bottom sheets provide athletes with instant peek digests of domain entities (Locations, Routes, Segments, Live Segments, and Workouts) before the sheet is dragged upward to reveal elevation profiles, telemetry charts, or deeper analytical tables.

During physical device review on Google Pixel 10 (Android 15 / gesture navigation bar) in Sprint `2026-40.8`, the initial baseline tokens established in `ATT-1645` revealed three concrete calibration discrepancies:
1. **Segments Bottom Sheet (`SegmentOnMapScreen.kt`)**: The third metrics row of the `SegmentDetails` card (altitude icon, elevation gain, min altitude, max altitude) was obscured behind the system navigation bar.
2. **Routes Bottom Sheet (`RouteOnMapScreen.kt`)**: When a route contained an optional description (`summary.description.isNotEmpty()`), the description text was obscured behind the system navigation bar.
3. **Live Segment Bottom Sheet (`SensorGridScreen.kt` / `LiveSegmentSheet.kt`)**: The baseline height was set higher than the live header footprint, causing the top slice of the underlying elevation profile graph to awkwardly peek out above the navigation bar before the user initiated a swipe gesture.

The objective of this revision cycle in Sprint `2026-40.9` is to recalibrate the `BottomSheetDesign` peek baseline tokens and screen-level peek formulas so that all header rows sit cleanly above the system navigation bar across all device configurations, while secondary charts remain strictly below the fold.

---

## 2. Root Cause Analysis (Forensic Investigation)

### Forensic Inspection of Candidate Sheets & Header Footprints

1. **Segments Peek (`SegmentHeader` + `SegmentDetails` via `SegmentOnMapScreen.kt` / `MapDetailLayout.kt`)**:
   * *Observed Defect*: Altitude gain and min/max elevation row is obscured behind the navigation bar.
   * *Content Structure Breakdown*:
     * `MinimumDragHandle()`: $15\text{dp}$ ($8\text{dp}$ top + $3\text{dp}$ pill + $4\text{dp}$ bottom).
     * `SegmentHeader`: $8\text{dp}$ top padding + Sport Icon ($32\text{dp}$) & Title (`titleLarge`, ~$28\text{dp}$) + Category/City row ($20\text{dp}$) + $4\text{dp}$ bottom padding = ~$64\text{dp}$.
     * `HorizontalDivider`: $0.5\text{dp}$.
     * `SegmentDetails`:
       * Top padding: $4\text{dp}$.
       * Row 1 (Distance & Strava logo): ~$28\text{dp}$.
       * Spacing: $4\text{dp}$.
       * Row 2 (Grades: average & maximum): ~$24\text{dp}$.
       * Spacing: $4\text{dp}$.
       * Row 3 (Altitude icon, Ascent, Min, Max): ~$28\text{dp}$.
       * Bottom padding: $8\text{dp}$.
       * Total `SegmentDetails`: $4 + 28 + 4 + 24 + 4 + 28 + 8 = 96\text{dp}$.
   * *Actual Footprint*: $15\text{dp} (\text{handle}) + 64\text{dp} (\text{header}) + 1\text{dp} (\text{divider}) + 96\text{dp} (\text{details}) = 176\text{dp}$ (up to $184\text{dp}$ with multi-line titles).
   * *Root Cause*: `BottomSheetDesign.PeekHeightSegment` was set to $156\text{dp}$. At $156\text{dp}$, the sheet is $20\text{dp}$ to $28\text{dp}$ too short, truncating Row 3 (the altitude row) directly behind the navigation bar.
   * *Countermeasure*: Increase `BottomSheetDesign.PeekHeightSegment` from $156\text{dp}$ to **`192.dp`** ($188\text{dp}$ content + $4\text{dp}$ breathing room).

2. **Routes Peek (`RouteSummaryHeader` via `RouteOnMapScreen.kt` / `MapDetailLayout.kt`)**:
   * *Observed Defect*: Route description is obscured behind the navigation bar when present.
   * *Content Structure Breakdown*:
     * `MinimumDragHandle()`: $15\text{dp}$.
     * Base Header Box (Sport Icon $32\text{dp}$, Name `titleLarge`, Source label, Distance, Elevation Gain, Visibility Switch): ~$88\text{dp}$.
     * Base Footprint (No Description): $15 + 88 = 103\text{dp}$ (fits inside $112\text{dp}$).
     * Optional Description Block:
       * `HorizontalDivider`: $4.5\text{dp}$ ($4\text{dp}$ top padding + $0.5\text{dp}$ line).
       * Description `Text` (`bodyMedium`, line height $20\text{dp}$, $8\text{dp}$ bottom padding): ~$28\text{dp}$ (single line) or ~$48\text{dp}$ (two lines).
       * Additional height: ~$32.5\text{dp}$ to $48\text{dp}$.
   * *Footprint With Description*: $103\text{dp} + 36\text{dp} \approx 139\text{dp}$ to $148\text{dp}$.
   * *Root Cause*: `BottomSheetDesign.PeekHeightRoute` was set statically to $112\text{dp}$. This baseline cleanly accommodates routes without description, but truncates the description row whenever a description is present.
   * *Countermeasure*:
     * Define `BottomSheetDesign.PeekHeightRouteWithDescription = 152.dp`.
     * In `MapScreenWithTrack.kt`, conditionally evaluate whether the selected route has a non-empty description:
       `val basePeek = if (routeSummary?.description.isNullOrEmpty()) BottomSheetDesign.PeekHeightRoute else BottomSheetDesign.PeekHeightRouteWithDescription`
       `sheetPeekHeight = basePeek + navBarHeight`.

3. **Live Segment Peek (`LiveSegmentSheet.kt` / `SensorGridScreen.kt`)**:
   * *Observed Defect*: Elevation profile graph is slightly visible above the navigation bar in the resting peek state.
   * *Content Structure Breakdown*:
     * `MinimumDragHandle()`: $15\text{dp}$.
     * `SegmentHeader`: $2\text{dp}$ top padding + Icon ($32\text{dp}$) & Title ($24\text{dp}$) + Live status label ($16\text{dp}$) + $4\text{dp}$ bottom padding = ~$46\text{dp}$.
     * `HorizontalDivider`: $0.5\text{dp}$.
     * `SegmentLiveDetails`: $4\text{dp}$ top padding + Left Column (Distance, Remaining, Offset: ~$60\text{dp}$) + $4\text{dp}$ bottom padding = ~$68\text{dp}$.
   * *Actual Footprint*: $15\text{dp} + 46\text{dp} + 0.5\text{dp} + 68\text{dp} \approx 129.5\text{dp}$ (or $115\text{dp}$ without drag handle).
   * *Root Cause*: `BottomSheetDesign.PeekHeightLiveSegment` was set to $140\text{dp}$. Because the live header layout requires only ~$124\text{dp}$ to $128\text{dp}$, setting peek height to $140\text{dp} + \text{navBarHeight}$ created an excess margin of ~$12\text{dp}$ to $16\text{dp}$, causing the top of the subsequent `ElevationProfile` graph in `MapDetailLayout` to spill over into the visible peek viewport.
   * *Countermeasure*: Recalibrate `BottomSheetDesign.PeekHeightLiveSegment` from $140\text{dp}$ to **`126.dp`**, cleanly framing the live progress indicators without revealing the underlying elevation profile until swiped upward.

4. **Workouts Peek & Favorite Locations Peek**:
   * `PeekHeightWorkout` ($140\text{dp}$) and `PeekHeightKnownLocation` ($108\text{dp}$) were verified as optimal during the physical review on Pixel 10 and require no changes.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Recalibrate `BottomSheetDesign.PeekHeightSegment` to `192.dp` to ensure the altitude metrics row is 100% visible above the navigation bar.
  2. Introduce `BottomSheetDesign.PeekHeightRouteWithDescription = 152.dp` and integrate dynamic description-aware peek calculation in `MapScreenWithTrack.kt`.
  3. Recalibrate `BottomSheetDesign.PeekHeightLiveSegment` to `126.dp` in `BottomSheetDesign.kt` to prevent the elevation profile from peeking out before upward swipe.
  4. Ensure `WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()` remains strictly added to every non-zero peek baseline.
  5. Update unit tests in `BottomSheetDesignTest.kt` and contract tests in `BottomSheetVisualContractTest.kt`.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No redesign of `MapDetailLayout` internals or gesture handling.
  * No alteration to workout cluster heatmaps or historical period maps (`PeekHeightWorkout = 140.dp` remains unchanged).
  * No modification to favorite location sheets (`PeekHeightKnownLocation = 108.dp` remains unchanged).
  * No modifications to database schemas, tracking services, or GPS logic.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

### Audit of Existing Requirements (`REQ-PRO-022`)

1. **`REQ-UI-221` (*UI/Sheets: Standardized Bottom Sheet Peek Height Baselines and Information Footprint Framing*)**:
   * *Original Requirement Target*: `BottomSheetDesign.kt`, `MapScreenWithTrack.kt`, `SensorGridScreen.kt`, `WorkoutClusterHeatmapScreen.kt`, `PeriodMapScreen.kt`.
   * *Historical Origin & Commit Trace*: Sprint `2026-40.8` (Commit `9c3dd09e`, ATT-1645).
   * *Root Reason for Existing Formulation*: Centralized disparate magic numbers into `BottomSheetDesign` constants (`140.dp`, `112.dp`, `156.dp`, `108.dp`, `140.dp`).
   * *Preservation of Core Invariants*: Invariant that every persistent sheet incorporates `navBarHeight` and adheres to `REQ-SET-069` status bar constraints is 100% preserved. The token values are updated to reflect physical device geometry findings (`PeekHeightSegment = 192.dp`, `PeekHeightRouteWithDescription = 152.dp`, `PeekHeightLiveSegment = 126.dp`).

---

## 5. Architectural Strategy & High-Level Solution

### Step 1: Update Tokens in `BottomSheetDesign.kt`
```kotlin
object BottomSheetDesign {
    // Existing tokens...
    val PeekHeightWorkout: Dp = 140.dp
    val PeekHeightRoute: Dp = 112.dp
    val PeekHeightRouteWithDescription: Dp = 152.dp
    val PeekHeightSegment: Dp = 192.dp
    val PeekHeightKnownLocation: Dp = 108.dp
    val PeekHeightLiveSegment: Dp = 126.dp
}
```

### Step 2: Screen-Level Calibration in `MapScreenWithTrack.kt`
```kotlin
sheetPeekHeight = when {
    selectedSegmentId != null -> BottomSheetDesign.PeekHeightSegment + navBarHeight
    selectedRouteId != null -> {
        val summary = allRoutes.find { it.summary.id == selectedRouteId }?.summary
        val basePeek = if (summary?.description.isNullOrEmpty()) {
            BottomSheetDesign.PeekHeightRoute
        } else {
            BottomSheetDesign.PeekHeightRouteWithDescription
        }
        basePeek + navBarHeight
    }
    selectedLocationId != null -> BottomSheetDesign.PeekHeightKnownLocation + navBarHeight
    else -> 0.dp
}
```

### Step 3: Verification & Unit Testing
* Update `BottomSheetDesignTest.kt` to assert the updated peek baseline tokens.
* Update `BottomSheetVisualContractTest.kt` to verify that `MapScreenWithTrack.kt` and `SensorGridScreen.kt` consume the calibrated tokens without hardcoded values.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. `WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()` is strictly added to every non-zero peek baseline.
  2. Maximum sheet expansion remains constrained by status bar insets (`REQ-SET-069`).
  3. Minimum drag handle dimensions (`32dp x 3dp`) and vertical touch padding remain unchanged.
  4. Zero changes to GPS tracking, sensor pipeline, or database schema.

* **Risk Rating**: **LOW**  
  * Justification: Clean UI token recalibration directly addressing physical device feedback from Sprint `2026-40.8` review. Zero regression risk for business logic or data pipelines.
