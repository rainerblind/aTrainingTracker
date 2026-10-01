# Stage 2: Requirement & Test Specification - ATT-1812: Restore map preview visibility on detailed workout inspection screen

**Ticket**: [ATT-1812](https://rainerblind.atlassian.net/browse/ATT-1812)  
**Sub-task**: [ATT-1844](https://rainerblind.atlassian.net/browse/ATT-1844) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.7`  
**Requirement Mapping**: `REQ-UI-213` (*Aftermath: Resilient Map Preview Visibility & Scrollable Telemetry Layout Architecture*)  
**Test Spec ID**: `TST-UI-167`  
**Branch**: `feature/ATT-1812`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Requirement Specification (REQ-UI-213)

### 1.1 Problem Statement & Rationale
During Sprint 2026-40.6 verification on physical hardware (Pixel 10), the map preview completely disappeared on the detailed workout inspection screen (`TrackOnMapScreen` / `MapDetailLayout`). The cumulative vertical heights of non-weighted children in `MapDetailLayout` (`WorkoutHeader` ~150 dp, `ElevationProfile` ~160 dp, continuous telemetry charts for HR, Speed/Pace, and Power ~480 dp, plus analytics cards for HR/Power zones and lap splits ~350 dp) exceeded the viewport height (~800 dp). In Jetpack Compose, non-weighted items are measured first; when their total height exceeds available constraints, `Modifier.weight(1f)` allocated to the Map collapses to `0 dp`.

Furthermore, because the root `Column` was unscrollable, content below the fold was clipped and inaccessible, and snapshot sharing (`combineWorkoutAndShare`) failed to capture a valid map bitmap.

### 1.2 Functional & Architectural Requirements
The system SHALL ensure the map preview remains continuously visible and interactive while providing seamless access to all charts and analytics cards in `MapDetailLayout.kt`:

1. **Adaptive Scrollable Layout Architecture (`MapDetailLayout.kt`)**:
   - The layout SHALL dynamically identify whether overflow-inducing content is present:
     - `hasTelemetryGraphs = showZoomControls && activeScrubPath != null && (TelemetryMetricUtils.hasHeartRateData(activeScrubPath) || TelemetryMetricUtils.hasSpeedData(activeScrubPath) || TelemetryMetricUtils.hasPowerData(activeScrubPath))`
     - `hasScrollableContent = analyticsContent != null || hasTelemetryGraphs`
   - Parameter `analyticsContent` SHALL be nullable with default `null`: `analyticsContent: (@Composable ColumnScope.() -> Unit)? = null`.
2. **Guaranteed Map Viewport & Dedicated Scroll Container**:
   - When `showMap == true` and `hasScrollableContent == true` (Detailed Workout Inspection):
     - The Map Box SHALL be constrained with a guaranteed minimum height and flexible weight: `Modifier.weight(1f).heightIn(min = 240.dp).fillMaxWidth()`.
     - Section 3 (`ElevationProfile` and `TelemetryMetricGraph`s) and Section 4 (`analyticsContent`) SHALL be hosted inside a dedicated vertically scrollable container: `Column(modifier = Modifier.weight(1.2f).fillMaxWidth().verticalScroll(rememberScrollState()))`.
   - When `showMap == true` and `hasScrollableContent == false` (Routes & Segments):
     - The Map Box SHALL retain full-viewport weight expansion: `Modifier.weight(1f).fillMaxWidth()`.
     - The lower section SHALL wrap its content at the bottom (`wrapContentHeight()`), ensuring zero blank whitespace.
   - When `showMap == false` (LiveSegment popup sheet):
     - The root `Column` SHALL retain unweighted `wrapContentHeight()`, preserving compact bottom sheet geometry.
3. **Gesture Isolation & Interaction Safety**:
   - The Map container SHALL reside outside of the vertical scroll container, guaranteeing that all Google Map gestures (pan, pinch-to-zoom, compass rotation) operate with zero touch interception conflicts.
4. **Snapshot Sharing & Graphics Layer Recording**:
   - Both `elevationLayer` and `analyticsLayer` SHALL wrap their respective `Surface` composables inside the scrollable column, ensuring that upon `combineWorkoutAndShare`, full unclipped graphics layer bitmaps are rendered and stitched with the non-zero measured map bitmap.
5. **Multi-Chart Synchronized Scrubbing Parity**:
   - Shared `selectedDistance` state SHALL remain synchronously wired across the visible Map marker, the elevation profile, and all continuous telemetry graphs (HR, Speed/Pace, Power).

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Map Preview Visibility & Height Guarantee)**:
  * *Given* an athlete viewing a completed workout with GPS and sensor telemetry in Aftermath (`TrackOnMapScreen`),
  * *When* the screen renders on device (e.g. Pixel 10 viewport ~808 dp),
  * *Then* the Map preview SHALL be visible above the charts with measured height >= 240 dp, rendering the route track, start/stop pins, and share button.
* **Criterion 2 (Scrollable Charts & Analytics Access)**:
  * *Given* an athlete viewing a workout with Elevation, HR, Pace/Speed, Power, and Zone/Split cards,
  * *When* the athlete scrolls down on the lower section,
  * *Then* all continuous metric graphs, HR zones, Power zones, and lap splits SHALL smoothly scroll into view.
* **Criterion 3 (Synchronized Scrubbing with Visible Map)**:
  * *Given* an athlete scrubbing horizontally across any visible telemetry graph in the lower panel,
  * *When* the cursor position changes,
  * *Then* the location pin on the map preview above SHALL synchronously translate along the route track in real time.
* **Criterion 4 (Zero Route and Segment Regression)**:
  * *Given* an athlete viewing a saved route (`RouteOnMapScreen`) or segment (`SegmentOnMapScreen`),
  * *When* rendered,
  * *Then* the Map SHALL expand to fill the full screen down to the compact elevation profile with zero empty whitespace.
* **Criterion 5 (Zero LiveSegmentSheet Regression)**:
  * *Given* an athlete in active tracking with a live segment triggered (`LIveSegmentSheet.kt`),
  * *When* the bottom sheet expands,
  * *Then* it SHALL retain `wrapContentHeight()` without a map and without vertical scrollbars.
* **Criterion 6 (Snapshot Sharing Integrity)**:
  * *Given* an athlete tapping the Share button on the Map preview,
  * *When* the workout summary digest is generated,
  * *Then* `combineWorkoutAndShare` SHALL receive a valid non-zero map bitmap along with header, elevation, and analytics bitmaps.

### 1.4 System Invariants & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement (`REQ-UI-213`), resolving layout starvation introduced by `REQ-UI-206` (`ATT-1740`) and `REQ-UI-205` (`ATT-1389`) under Epic `ATT-111`.
* **Historical Origin & Commit Trace**: Commit `061a4b35` (`ATT-1740`) added continuous telemetry graphs without scroll containers; commit `e7a6a273` (`ATT-1389`) added analytics cards.
* **Root Reason for Existing Formulation**: `Modifier.weight(1f)` assumed only a single 160 dp elevation profile was below the map.
* **Preservation of Core Invariants**: Route inspection, segment inspection, LiveSegmentSheet background surface contracts (`REQ-UI-196`), elevation zoom math (`REQ-UI-197`), and 9-language localization parity (`REQ-UI-106`) remain 100% preserved.

---

## 2. Test Specification (TST-UI-167)

### Test Case 1: Layout Contract & Sizing Tests (`TST-UI-167.1`)
* **Scope**: Automated Unit & Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayoutTest.kt`
* **Preconditions**: `MapDetailLayout.kt` exists.
* **Action**:
  - Verify that `MapDetailLayout` declares `analyticsContent: (@Composable ColumnScope.() -> Unit)? = null`.
  - Verify that `MapDetailLayout` computes `hasTelemetryGraphs` checking `hasHeartRateData`, `hasSpeedData`, and `hasPowerData`.
  - Verify that `MapDetailLayout` conditionally creates a scrollable container (`verticalScroll`) when scrollable content is present.
  - Verify that the map Box applies `heightIn(min = 240.dp)` when scrollable content is present to prevent 0dp collapse.
* **Expected Result**: Assertions pass, verifying resilient layout constraints.

### Test Case 2: Regression Invariants Across Map Screens (`TST-UI-167.2`)
* **Scope**: Integration Contract Tests
* **Target Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfileLayoutTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/segments/LiveSegmentSheetLayoutTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/core/BottomSheetVisualContractTest.kt`
* **Preconditions**: Screens and contract tests in place.
* **Action**:
  - Execute existing contract tests asserting `showZoomControls` forwarding, `LiveSegmentSheet` zoom suppression, and unified surface background contracts (`!useStatusBarsPadding -> background(surface)`).
* **Expected Result**: All existing layout contracts pass with 0 regressions.

### Test Case 3: Scrubbing & Snapshot Sharing Contract Verification (`TST-UI-167.3`)
* **Scope**: Automated Unit & Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraphTest.kt`
* **Preconditions**: `MapDetailLayout.kt` and `TelemetryMetricGraph.kt` exist.
* **Action**:
  - Verify `selectedDistance` touch synchronization across graphs.
  - Verify `combineWorkoutAndShare` integration and `elevationLayer` / `analyticsLayer` graphics recording.
* **Expected Result**: Contracts pass, verifying zero regression in multi-metric synchronization.

### Test Case 4: 9-Language Localization Audit (`TST-UI-167.4`)
* **Scope**: Automated Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricLocalizationTest.kt`
* **Preconditions**: 9 localized `strings.xml` files.
* **Action**: Verify graph headings (`graph_heading_elevation`, `graph_heading_heart_rate`, `graph_heading_speed`, `graph_heading_pace`, `graph_heading_power`) across all 9 locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
* **Expected Result**: 100% parity, zero missing entries.

### Test Case 5: Clean-Room Full Suite Regression (`TST-UI-167.5`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across all unit tests with 0 test failures.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-167.1]` | Contract | `MapDetailLayout` layout modifiers & scroll container | `REQ-UI-213` | Specified |
| `[TST-UI-167.2]` | Regression | `ElevationProfileLayoutTest`, `LiveSegmentSheetLayoutTest` | `REQ-UI-213`, `REQ-UI-196`, `REQ-UI-197` | Specified |
| `[TST-UI-167.3]` | Contract | `TelemetryMetricGraphTest` scrubbing synchronization | `REQ-UI-213`, `REQ-UI-206` | Specified |
| `[TST-UI-167.4]` | Localization | `TelemetryMetricLocalizationTest` (9 locales) | `REQ-UI-213`, `REQ-UI-106` | Specified |
| `[TST-UI-167.5]` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
