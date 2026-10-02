# Stage 1 Analysis: ATT-1645 - Calibrate and Optimize Initial Height & Peek Baselines for Popups and Bottom Sheets

**Ticket**: [ATT-1645](https://atrainingtracker.atlassian.net/browse/ATT-1645)  
**Sub-task**: [ATT-1897](https://atrainingtracker.atlassian.net/browse/ATT-1897) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.8`  
**Branch**: `feature/ATT-1645`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Problem Statement & Motivation

Across `aTrainingTracker`, various bottom sheets, popups, and persistent scaffolds are used to inspect contextual domain entities on map canvases and tracking cockpits:
1. Favorite start locations (*Lieblingsorte*) on the central map ([MapScreenWithTrack.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapScreenWithTrack.kt)).
2. Selected routes on the central map ([RouteOnMapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteOnMapScreen.kt)).
3. Selected segments on the central map ([SegmentOnMapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/segments/SegmentOnMapScreen.kt)).
4. Live segments during active recording on the sensor cockpit ([SensorGridScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt) / [LIveSegmentSheet.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/segments/LIveSegmentSheet.kt)).
5. Peeked workout cards on analytical cluster heatmaps ([WorkoutClusterHeatmapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterHeatmapScreen.kt)) and historical period maps ([PeriodMapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodMapScreen.kt)).

Currently, these bottom sheets utilize disparate, hardcoded initial peek height values (`100.dp`, `120.dp`, `140.dp`, `185.dp`). On physical devices (e.g. Google Pixel 10), several of these heights cause visual flaws:
* **Vertical Clipping & Truncation**: For workouts in cluster heatmaps and period maps (`120.dp`), the header's Date & Time row or location tags are cut in half or hidden behind the bottom navigation bar.
* **Awkward Margins**: For routes (`100.dp`), the visibility switch and ascent metric are pinched right against the bottom edge without clean padding.
* **Excessive Trailing Space**: For segments (`185.dp`), the card overshoots its content, creating awkward empty space or partially revealing underlying elevation profile slices before the user initiates a drag gesture.
* **Fragmented Design Token Governance**: Sheet peek baselines are hardcoded locally in screen composables rather than governed centrally in [BottomSheetDesign.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/core/BottomSheetDesign.kt).

---

## 2. Root Cause Analysis (Forensic Investigation)

### Forensic Inspection of Candidate Sheets & Header Footprints

1. **Known Locations Peek (`KnownLocationOnMapSheet` in `MapScreenWithTrack.kt`)**:
   * *Current Peek Height*: `100.dp + navBarHeight`.
   * *Content Structure*:
     * Minimum drag handle (`8dp top + 3dp pill + 4dp bottom = 15dp`).
     * Header container padding: `12dp top + 12dp bottom = 24dp`.
     * Title Row: Location Name (`titleMedium`, `24dp`) + Edit IconButton (`24dp`).
     * Spacing: `4dp`.
     * Metric Row: Ascent icon + formatted altitude (`titleMedium`, `24dp`) + hit count (`bodyMedium`, `20dp`).
   * *Natural Content Footprint*: $15 + 24 + 24 + 4 + 24 = 91\text{dp}$.
   * *Forensic Assessment*: At `100.dp`, padding is minimally sufficient ($9\text{dp}$ buffer), but calibrating to `108.dp` provides perfect visual breathing room above the navigation bar.

2. **Routes Peek (`RouteSummaryHeader` via `RouteOnMapScreen.kt`)**:
   * *Current Peek Height*: `100.dp + navBarHeight`.
   * *Content Structure*:
     * Minimum drag handle: `15dp`.
     * Header padding: `8dp top + 4dp bottom = 12dp`.
     * Top Row: Sport Icon (`32dp`) + Name (`titleLarge`, `28dp`) + Source subtitle (`16dp`) = ~`44dp`.
     * Spacing: `4dp`.
     * Metric Row: Distance (`20dp`) + Elevation Gain (`20dp`) + trailing visibility Switch (`scale 0.7f`, ~`28dp`).
   * *Natural Content Footprint*: $15 + 12 + 44 + 4 + 24 = 99\text{dp}$.
   * *Forensic Assessment*: At `100.dp`, the bottom switch overlay and elevation gain metric sit right at the razor edge of the viewport cut. Calibrating to `112.dp` provides comfortable, unclipped framing.

3. **Segments Peek (`SegmentHeader` + `SegmentDetails` via `SegmentOnMapScreen.kt`)**:
   * *Current Peek Height*: `185.dp + navBarHeight`.
   * *Content Structure*:
     * Minimum drag handle: `15dp`.
     * `SegmentHeader`: Sport Icon (`32dp`) + Name (`titleLarge`, `28dp`) + Category badge + City/PR row (`20dp`) + padding (`12dp`) = ~`60dp`.
     * Divider: `0.5dp`.
     * `SegmentDetails`: Row 1 Distance/Branding (`20dp`) + Row 2 Grades (`20dp`) + Row 3 Altitude min/max/gain (`24dp`) + padding (`12dp`) = ~`76dp`.
   * *Natural Content Footprint*: $15 + 60 + 0.5 + 76 = 151.5\text{dp}$.
   * *Forensic Assessment*: At `185.dp`, the peek overshoots by ~`33.5dp`, exposing a dangling strip of the elevation profile or empty container background. Calibrating to `156.dp` aligns the 3-row detail card cleanly with consistent bottom padding.

4. **Live Segment Peek (`LiveSegmentSheet` via `SensorGridScreen.kt`)**:
   * *Current Peek Height*: `140.dp + navBarHeight`.
   * *Content Structure*: Minimum drag handle (`15dp`) + `SegmentHeader` (`55dp`) + Divider (`0.5dp`) + `SegmentLiveDetails` (`65dp`).
   * *Natural Content Footprint*: ~`136dp`.
   * *Forensic Assessment*: `140.dp` is already well-calibrated and serves as the canonical reference for live tracking sheets.

5. **Workouts Peek (`WorkoutHeader` via `WorkoutClusterHeatmapScreen.kt` & `PeriodMapScreen.kt`)**:
   * *Current Peek Height*: `120.dp + navBarHeight`.
   * *Content Structure*:
     * Minimum drag handle: `15dp`.
     * Header padding: `8dp top + 4dp bottom = 12dp`.
     * Top Row: Sport Icon (`32dp`) + Workout Name (`titleLarge`, max 2 lines = `28–48dp`) + Action Spacer.
     * Cluster Badge Row: Height `30dp`.
     * Row A: Sport Name + Equipment / Trainer label = `20dp`.
     * Row B: Date (`ic_date_start`) and Time (`ic_time_start`) = `20dp`.
     * Optional Row C: Favorite start / destination locations (`20dp`).
   * *Natural Content Footprint*: $15 + 12 + 32 + 30 + 20 + 20 = 129\text{dp}$ (without Row C) and up to $145\text{dp}$ (with two-line title or cluster button).
   * *Forensic Assessment*: At `120.dp`, the Date/Time row is severely clipped or cut off completely on standard screen heights. Calibrating the baseline to `140.dp` allows the core identity (Sport, Name, Cluster, Sport type, and Date/Time) to be completely visible without user touch.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Audit all persistent and map peek sheet composables across the codebase.
  2. Centralize and formalize peek height baseline design tokens in [BottomSheetDesign.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/core/BottomSheetDesign.kt):
     * `PeekHeightWorkout = 140.dp`
     * `PeekHeightRoute = 112.dp`
     * `PeekHeightSegment = 156.dp`
     * `PeekHeightKnownLocation = 108.dp`
     * `PeekHeightLiveSegment = 140.dp`
  3. Replace scattered hardcoded values across [MapScreenWithTrack.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapScreenWithTrack.kt), [SensorGridScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt), [WorkoutClusterHeatmapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterHeatmapScreen.kt), and [PeriodMapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodMapScreen.kt).
  4. Ensure `WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()` is consistently incorporated across all peek calculations to eliminate system bar clipping on devices with 3-button or gesture navigation.
  5. Expand unit and contract tests in `BottomSheetDesignTest.kt` and `BottomSheetVisualContractTest.kt` to guarantee token compliance.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Modifying sheet drag gestures or expanding behavior (governed by `BottomSheetScaffoldState`).
  * Modifying internal header typography or deleting data fields from `WorkoutHeader`, `RouteSummaryHeader`, or `SegmentHeader`.
  * Redesigning filter bottom sheets (`FilterBottomSheetScaffold.kt`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

### Audit of Existing Requirements (`REQ-PRO-022`)

1. **`REQ-SET-069` (Workout, Route & Segment Detail Bottom Sheet Scaffold Status Bar Boundary Constraint)**:
   * *Original Requirement Target*: `PeriodMapScreen.kt`, `MapFragmentWithTrack.kt`, `WorkoutClusterHeatmapScreen.kt`.
   * *Historical Origin & Commit Trace*: Commit `b4ebca54` / `191ba05d` (ATT-1382 / ATT-1449).
   * *Root Reason for Existing Formulation*: Constrains maximum upward expansion (`SheetValue.Expanded`) to `0f` relative to `statusBarsPadding()`, preventing the sheet title or drag handle from drawing behind system clock/battery icons, while preserving initial peek height and `MinimumDragHandle`.
   * *Preservation of Core Invariants*: Status bar constraints and `SheetValue.Expanded` top alignment remain strictly untouched. The calibrated peek baselines improve initial visibility while strictly adhering to `REQ-SET-069`.

2. **`REQ-UI-180` (Display Favorite Locations on Central Navigation Map)**:
   * *Original Requirement Target*: `MapScreenWithTrack.kt`.
   * *Historical Origin & Commit Trace*: Commit `191ba05d` (ATT-1449 / ATT-1553).
   * *Root Reason for Existing Formulation*: Specified tapping a location pin expands sheet to peek height (`140.dp + navBarHeight`). In subsequent UI polishing (ATT-1588 / REQ-UI-189), the card was made more compact and reduced to `100.dp`.
   * *Preservation of Core Invariants*: Location name, altitude, start count, and edit trigger remain 100% functional. Calibrating peek baseline to `108.dp` provides perfect framing without regression.

3. **Net-New Requirement Formulation**:
   * We introduce **`REQ-UI-220`** (*Standardized Bottom Sheet Peek Height Baselines & Information Footprint Framing*), formalizing the canonical tokens in `BottomSheetDesign` and cross-screen compliance.

---

## 5. Architectural Strategy & High-Level Solution

### Step 1: Token Definition in `BottomSheetDesign.kt`
Extend `BottomSheetDesign` with semantic peek baseline tokens:
```kotlin
object BottomSheetDesign {
    // Existing tokens...
    val SheetCornerRadius: Dp = 20.dp
    val SheetShape: Shape = RoundedCornerShape(topStart = SheetCornerRadius, topEnd = SheetCornerRadius)
    val SheetShadowElevation: Dp = 8.dp
    val SheetTonalElevation: Dp = 0.dp
    val BorderWidth: Dp = 1.dp
    val DragHandleWidth: Dp = 32.dp
    val DragHandleHeight: Dp = 3.dp

    // --- Standardized Peek Height Baselines (REQ-UI-220, ATT-1645) ---
    /** Calibrated baseline for single workout detail peeks (Heatmap & Period Map) cleanly framing WorkoutHeader. */
    val PeekHeightWorkout: Dp = 140.dp

    /** Calibrated baseline for route detail peeks framing RouteSummaryHeader and visibility switch. */
    val PeekHeightRoute: Dp = 112.dp

    /** Calibrated baseline for segment detail peeks framing SegmentHeader and SegmentDetails. */
    val PeekHeightSegment: Dp = 156.dp

    /** Calibrated baseline for favorite location (Lieblingsort) peeks framing KnownLocationOnMapSheet. */
    val PeekHeightKnownLocation: Dp = 108.dp

    /** Calibrated baseline for active live segment tracking peek framing live delta and target metrics. */
    val PeekHeightLiveSegment: Dp = 140.dp
}
```

### Step 2: Screen Integration
* **`MapScreenWithTrack.kt`**:
  ```kotlin
  sheetPeekHeight = when {
      selectedSegmentId != null -> BottomSheetDesign.PeekHeightSegment + navBarHeight
      selectedRouteId != null -> BottomSheetDesign.PeekHeightRoute + navBarHeight
      selectedLocationId != null -> BottomSheetDesign.PeekHeightKnownLocation + navBarHeight
      else -> 0.dp
  }
  ```
* **`SensorGridScreen.kt`**:
  ```kotlin
  sheetPeekHeight = if (showLiveSegments && screenMode == ScreenMode.TRACKING) {
      BottomSheetDesign.PeekHeightLiveSegment + navBarHeight
  } else 0.dp
  ```
* **`WorkoutClusterHeatmapScreen.kt` & `PeriodMapScreen.kt`**:
  ```kotlin
  sheetPeekHeight = if (peekedWorkoutDataWithTrack != null && !isEditingFingerprint) {
      BottomSheetDesign.PeekHeightWorkout + navBarHeight
  } else 0.dp
  ```

### Step 3: Verification & Unit Testing
* Update `BottomSheetDesignTest.kt` to assert the 5 peek height baseline constants.
* Update `BottomSheetVisualContractTest.kt` to assert that screens reference `BottomSheetDesign.PeekHeight*` rather than raw hardcoded numeric literals.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. `WindowInsets.navigationBars` bottom padding is strictly added to every non-zero peek baseline.
  2. Maximum expanded height constraints (`maxSheetHeight = maxHeight - statusBarHeight`) are strictly preserved (`REQ-SET-069`).
  3. Zero alteration to underlying data flow, repository single-thread dispatchers, or GPS tracking engines.
  4. Parent ticket Human Decision Gate remains strictly guarded.

* **Risk Rating**: **LOW**  
  * Justification: Clean architectural consolidation replacing hardcoded magic numbers with centralized design tokens in `BottomSheetDesign`. Zero database, thread, or Android framework lifecycle risks.
