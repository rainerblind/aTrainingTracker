# Stage 5 Verification & Walkthrough: ATT-1737

## 1. Ticket Information
- **Parent Ticket**: [ATT-1737](https://atrainingtracker.atlassian.net/browse/ATT-1737) - `[Verbesserung] [ElevationProfile] Resolve visual overlap between multi-metric scrubbing badge and zoom controls`
- **Subtask**: [ATT-1774](https://atrainingtracker.atlassian.net/browse/ATT-1774) - `Stage 5: Verification & Clean-Room Regression`
- **Fix Version**: `V4.9.38`
- **Target Branch**: `sprint/2026-40.6`
- **Feature Branch**: `feature/ATT-1737`

---

## 2. Executive Summary of Changes
Resolved the visual collision between the floating multi-metric telemetry badge (`ScrubbingTelemetryBadge`) and the zoom controls row (`+`, `-`, Pan/Scrub mode toggle, scale reset badge) when scrubbing along the left side of the elevation profile:
1. **Three-Layer Non-Overlapping Layout Architecture in [ElevationProfile.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt)**:
   - **Layer 1 (Controls & Legend)**: Occupies vertical band $[2.0\text{dp} .. 26.0\text{dp}]$ (`top = 2.dp`, height 24dp).
   - **Clearance 1**: $\ge 2.0\text{dp}$ separation between controls bottom (26.0dp) and badge top (28.0dp).
   - **Layer 2 (Multi-Metric Scrubbing Badge)**: Anchored at `Modifier.align(Alignment.TopCenter).padding(top = 28.dp)` (moved down from `top = 2.dp`). Occupies vertical band $[28.0\text{dp} .. 70.0\text{dp}]$.
   - **Clearance 2**: $\ge 2.0\text{dp}$ separation between badge bottom (70.0dp) and canvas chart start (72.0dp).
   - **Layer 3 (Canvas Chart Curve)**: Top padding set to `72.dp` (up from `44.dp`), with `totalCanvasHeight = cachedData.adaptiveHeight + 48.dp` (up from `+ 20.dp`).
   - **Plotting Height Invariant**: Strictly invariant at $(\text{adaptiveHeight} + 48\text{dp}) - 72\text{dp} - 24\text{dp} = \text{adaptiveHeight} - 48\text{dp}$.
2. **Unit Tests & Regression Guard in [ElevationProfileLayoutTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfileLayoutTest.kt)**:
   - Updated `testElevationProfile_sourceCodeInspection_layoutSeparation` to verify `topPadding == 72.dp`, `totalCanvasHeight == + 48.dp`, and badge `top = 28.dp`.
   - Updated `testVerticalLayoutGeometry_guaranteesNonOverlappingBounds` to verify that both `Clearance 1` and `Clearance 2` are $\ge 2.0\text{dp}$ and that net drawable chart height delta is 0.

---

## 3. Test & Verification Results

### A. Targeted Unit Test Suite
- Test File: [ElevationProfileLayoutTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfileLayoutTest.kt)
- Results:
  - `testElevationProfile_parameterDefaults_suppressZoomControls`: PASSED
  - `testListPreviewCallers_doNotEnableZoomControls`: PASSED
  - `testVerticalLayoutGeometry_guaranteesNonOverlappingBounds`: PASSED
  - `testElevationProfile_sourceCodeInspection_layoutSeparation`: PASSED
  - `testMapDetailLayout_enablesZoomControls`: PASSED
- Result: 5/5 tests passed (100%).

### B. Clean-Room Full Suite Regression Execution
- Command: `./gradlew testDebugUnitTest`
- Result: Executed clean-room regression test suite across all modules with 0 regressions.

---

## 4. Traceability & Living Documentation
- **Requirements**:
  - `REQ-UI-197`: Elevation Profile Contextual Zoom Controls Visibility & Non-Overlapping Multi-Metric Scrubbing Badge Architecture.
  - Status in `docs/requirements.md`: **Verified**
- **Test Specifications**:
  - `TST-UI-151`: Elevation Profile Contextual Zoom Controls Visibility and Non-Overlapping Multi-Metric Badge Layout Verification.
  - Status in `docs/tests.md`: **Verified**
