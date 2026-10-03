# Stage 5: Verification Walkthrough - ATT-2138: Suppress Zoom Controls When Neither Elevation Profile Nor Telemetry Graphs Are Displayed

**Ticket**: [ATT-2138](https://rainerblind.atlassian.net/browse/ATT-2138)  
**Sub-task**: [ATT-2158](https://rainerblind.atlassian.net/browse/ATT-2158) (`[Test]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `Sprint 2026-40.13`  
**Branch**: `feature/ATT-2138`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Executive Summary

This walkthrough verifies [ATT-2138](https://rainerblind.atlassian.net/browse/ATT-2138), satisfying requirement `REQ-UI-249` and test specification `TST-UI-208`.
In [MapDetailLayout.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt), `hasZoomToolbar` now reactively suppresses `GlobalTelemetryZoomToolbar` whenever neither an elevation profile nor any telemetry graphs are displayed. This eliminates empty toolbar clutter and automatically recovers 36dp vertical screen real estate for the map and metadata via [SplitPaneMath.calculateAvailableHeight](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/core/SplitPaneDivider.kt).

---

## 2. Verification Matrix

| Acceptance Criterion | Verification Method | Status | Evidence |
| :--- | :--- | :--- | :--- |
| **AC-1**: Suppress Zoom Controls Without Graphs | [MapDetailLayoutZoomContractTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayoutZoomContractTest.kt) | **PASSED** | `hasZoomToolbar = showZoomControls && (showElevationProfile \|\| hasTelemetryGraphs) && !activeScrubPath.isNullOrEmpty()` |
| **AC-2**: Render Zoom Controls With Graphs | [MapDetailLayoutZoomContractTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayoutZoomContractTest.kt) | **PASSED** | Active elevation profile or active telemetry graphs evaluate `hasZoomToolbar = true` |
| **Space Recovery**: Reclaim 36dp Height | [MapDetailLayoutZoomContractTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayoutZoomContractTest.kt) | **PASSED** | `toolbarHeightPx = if (hasZoomToolbar) ... else 0f` consumed in `calculateAvailableHeight` |
| **Clean-Room Regression**: Full Suite Execution | `./gradlew testDebugUnitTest` | **PASSED** | 100% clean-room test suite pass rate |

---

## 3. Implementation Code Diffs

```diff
--- a/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt
+++ b/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt
@@ -124,6 +124,12 @@ fun MapDetailLayout(
-    val hasZoomToolbar = showZoomControls && activeScrubPath != null && activeScrubPath.isNotEmpty()
     val hasTelemetryGraphs = showZoomControls && showTelemetryCharts && activeScrubPath != null && (
         TelemetryMetricUtils.hasHeartRateData(activeScrubPath) ||
         TelemetryMetricUtils.hasSpeedData(activeScrubPath) ||
         TelemetryMetricUtils.hasPowerData(activeScrubPath)
     )
+    val hasZoomToolbar = showZoomControls && (showElevationProfile || hasTelemetryGraphs) && !activeScrubPath.isNullOrEmpty()
     val hasScrollableContent = metadataContent != null || analyticsContent != null || hasTelemetryGraphs
```
