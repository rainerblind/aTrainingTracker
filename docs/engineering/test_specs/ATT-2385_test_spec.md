# Stage 2: Requirement & Test Specification - ATT-2385: Prevent Overlapping Distance Tick Labels on Elevation Profile X-Axis in Segment and Route Cards

**Ticket**: [ATT-2385](https://rainerblind.atlassian.net/browse/ATT-2385)  
**Sub-task**: [ATT-2485](https://rainerblind.atlassian.net/browse/ATT-2485) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-41.1`  
**Requirement Mapping**: `REQ-UI-272` (*Elevation Profile Adaptive X-Axis Tick Spacing and Dynamic Label Collision Prevention*)  
**Test Spec ID**: `TST-UI-232`  
**Branch**: `feature/ATT-2385`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-05  

---

## 1. Requirement Specification (REQ-UI-272)

### 1.1 Problem Statement & Rationale
In segment and route card previews (e.g. segment *Pfefferburg_Schönaich*, 1.47 km), distance tick labels on the X-axis of the elevation profile collide horizontally and form an unreadable solid text block (`200m300m400m500m600m700m800m900m1000m1100m  1,47 km`, documented in `docs/attachments/elevation_profile_overlap.png`). This occurs because tracks between 501 m and 1,500 m receive an overly dense 100 m step interval, and the rendering loop in [ElevationProfile.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt) performs zero adjacent label clearance tracking.

### 1.2 Functional & Architectural Requirements
The system SHALL eliminate overlapping distance and time tick labels on the X-axis of the elevation profile (`ElevationProfile.kt`) across all card previews (`RouteItem`, `SegmentItem`, `WorkoutSummary`) and detailed inspection views (`MapDetailLayout`, `TrackOnMapScreen`, `SensorGridScreen`):

1. **Adaptive Step Interval Calibration (`ElevationProfileZoomMath.kt`)**:
   * `ElevationProfileZoomMath.calculateAdaptiveDistanceStep` SHALL provide balanced intermediate step increments for short and mid-range tracks. For metric units:
     * `visibleDist > 50_000` $\implies$ 10,000 m (10 km)
     * `visibleDist > 20_000` $\implies$ 5,000 m (5 km)
     * `visibleDist > 10_000` $\implies$ 2,000 m (2 km)
     * `visibleDist > 5_000` $\implies$ 1,000 m (1 km)
     * `visibleDist > 2_000` $\implies$ 500 m
     * `visibleDist > 800` $\implies$ 250 m (addresses 1.47 km tracks: yields 5 well-spaced ticks instead of 14 crowded ticks)
     * `visibleDist > 300` $\implies$ 100 m
     * `visibleDist > 150` $\implies$ 50 m
     * `else` $\implies$ 25 m
   * For imperial units:
     * `visibleMiles > 30` $\implies$ 5 mi
     * `visibleMiles > 10` $\implies$ 2 mi
     * `visibleMiles > 3` $\implies$ 1 mi
     * `visibleMiles > 1` $\implies$ 0.5 mi
     * `visibleMiles > 0.5` $\implies$ 0.25 mi
     * `visibleMiles > 0.2` $\implies$ 0.1 mi
     * `else` $\implies$ 0.05 mi
   * The calculation MAY accept an optional `canvasWidthPx: Float = 0f` parameter to dynamically widen the step when rendered on narrow preview cards.

2. **Dynamic Clearance Invariant & Adjacent Label Collision Guard (`ElevationProfile.kt`)**:
   * In both distance and time domain X-axis rendering loops, the system SHALL track the rightmost horizontal pixel coordinate of the previously rendered label (`lastDrawnRightX`).
   * An intermediate candidate tick at canvas coordinate `x` with label text width $w_{\text{label}}$ (horizontal span $[x - \frac{w_{\text{label}}}{2}, x + \frac{w_{\text{label}}}{2}]$) SHALL be rendered if and only if it satisfies all three clearance invariants:
     1. *Start boundary clearance*: $x - \frac{w_{\text{label}}}{2} \ge \text{startClearanceThreshold}$, where $\text{startClearanceThreshold} = \text{startLabelWidth} + \text{minSpacingPx}$ if `startLabel` is drawn (`currentZoomScale > 1.01f`), or $\text{minSpacingPx}$ otherwise.
     2. *Preceding label clearance*: $x - \frac{w_{\text{label}}}{2} \ge \text{lastDrawnRightX} + \text{minSpacingPx}$.
     3. *Terminal distance clearance*: $x + \frac{w_{\text{label}}}{2} \le \text{width} - \text{endLabelWidth} - \text{minSpacingPx}$.
   * The minimum spacing threshold $\text{minSpacingPx}$ SHALL equal at least $24.\text{dp}$ converted to pixels.

3. **Synchronized Tick Mark Suppression**:
   * When an intermediate candidate tick label is suppressed due to insufficient clearance, the vertical tick mark line (`drawLine(x, height, x, height - 10f, textPaint)`) at position `x` SHALL likewise be suppressed, ensuring that tick marks and labels remain 100% synchronized with zero orphan tick notches.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Refines Clause 5 of `REQ-UI-192` (*Interactive Elevation Profile Zoom, Pan & Centered Scaling Navigation*) and `REQ-UI-201` (*Synchronized Multi-Metric Scrubbing on Elevation Profile with Configurable X-Axis Domain Architecture*).
2. *Historical Origin & Commit Trace*: `ATT-527` (Sprint 2026-40.4, commit `69286d4e`) introduced initial `calculateAdaptiveDistanceStep`; `ATT-1391` (Sprint 2026-40.5) added time-domain ticks; `ATT-1819` / `REQ-UI-220` (Sprint 2026-40.7) introduced `shouldRenderMilestoneLabel` for telemetry charts.
3. *Root Reason for Existing Formulation*: `ElevationProfile.kt` originally used a static `x > 60f` threshold and coarse step thresholds (jumping from 500m straight down to 100m at 1,500m), without dynamic label width collision checking.
4. *Preservation of Core Invariants*: Elevation bounds sanitization (`REQ-UI-126`), horizontal zooming/panning (`REQ-UI-192`), contextual zoom control suppression in preview cards (`REQ-UI-197`), and scrubbing accuracy remain strictly preserved.

### 1.4 Acceptance Criteria (Given-When-Then)
* **AC-1 (Zero Overlapping Tick Labels)**:
  * *Given* an elevation profile for a segment or route of distance 1.47 km rendered in `RouteItem`, `SegmentItem`, or `WorkoutSummary`,
  * *When* the X-axis is drawn,
  * *Then* intermediate ticks SHALL render with a step of 250 m or 500 m, every label SHALL maintain at least 24 dp clearance from adjacent labels, and zero labels SHALL collide or overlap with each other or with the 1.47 km terminal label.
* **AC-2 (Guaranteed Universal Spacing Clearance)**:
  * *Given* any track distance from 50 m to 200 km rendered on any canvas width (portrait, landscape, split-screen),
  * *When* the elevation profile X-axis is rendered,
  * *Then* all rendered distance or time labels SHALL satisfy the clearance invariants with zero overlapping text.
* **AC-3 (Synchronized Tick Notches)**:
  * *Given* an intermediate tick position whose label is skipped due to spacing clearance,
  * *When* the canvas is painted,
  * *Then* the vertical tick line at that position SHALL also be suppressed.

### 1.5 System Invariants
1. Vertical elevation bounds and grade coloring math (`calculateElevationBounds`, `calculateElevationProfileHeight`) MUST NOT be altered.
2. Scrubbing distance translation (`onDistanceSelected`, `canvasXToDistance`) MUST NOT be altered.
3. 100% test suite pass rate MUST NOT be broken.

---

## 2. Test Specification (TST-UI-232)

### Test Case 1: `calculateAdaptiveDistanceStep_stepCalibrationForShortAndMidDistances` (`TST-UI-232.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfileZoomMathTest.kt`
* **Preconditions**: Metric and Imperial units.
* **Action**:
  * Query `calculateAdaptiveDistanceStep(1_470.0, MyUnits.METRIC)`.
  * Query `calculateAdaptiveDistanceStep(800.0, MyUnits.METRIC)`.
  * Query `calculateAdaptiveDistanceStep(2_500.0, MyUnits.METRIC)`.
  * Query `calculateAdaptiveDistanceStep(200.0, MyUnits.METRIC)`.
  * Query `calculateAdaptiveDistanceStep(100.0, MyUnits.METRIC)`.
* **Expected Result**:
  * 1,470 m yields 250 m.
  * 800 m yields 100 m.
  * 2,500 m yields 500 m.
  * 200 m yields 50 m.
  * 100 m yields 25 m.

### Test Case 2: `shouldRenderMilestoneLabel_clearanceEvaluationContract` (`TST-UI-232.2`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfileZoomMathTest.kt`
* **Action**:
  * Evaluate candidate tick label with insufficient gap from `lastDrawnRightX` (< 24dp): returns `false`.
  * Evaluate candidate tick label with sufficient gap from `lastDrawnRightX` (>= 24dp): returns `true`.
  * Evaluate candidate tick label colliding with `endLabel` boundary: returns `false`.
* **Expected Result**: Collision helper prevents all overlapping labels.

### Test Case 3: `testElevationProfile_enforcesLastDrawnRightXTracking` (`TST-UI-232.3`)
* **Scope**: Architectural / Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfileContractTest.kt`
* **Action**: Verify `ElevationProfile.kt` maintains `lastDrawnRightX` tracking in both distance and time rendering loops and enforces clearance with `endLabel`.
* **Expected Result**: Contract test asserts that neither rendering loop draws unchecked overlapping ticks.

### Test Case 4: Clean-Room Regression Suite (`TST-UI-232.4`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the entire test suite.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Component | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-232.1` | Unit | `ElevationProfileZoomMath.calculateAdaptiveDistanceStep` | `REQ-UI-272` (Clause 1) | Specified |
| `TST-UI-232.2` | Unit | `ElevationProfileZoomMath.shouldRenderTickLabel` | `REQ-UI-272` (Clause 2) | Specified |
| `TST-UI-232.3` | Contract | `ElevationProfile.kt` | `REQ-UI-272` (Clause 2 & 3) | Specified |
| `TST-UI-232.4` | Regression | Full Test Suite (`./gradlew testDebugUnitTest`) | `REQ-PRO-001` | Specified |
