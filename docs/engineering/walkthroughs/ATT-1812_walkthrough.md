# Stage 5: Walkthrough & Verification - ATT-1812: Restore map preview visibility on detailed workout inspection screen

**Ticket**: [ATT-1812](https://rainerblind.atlassian.net/browse/ATT-1812)  
**Sub-task**: [ATT-1848](https://rainerblind.atlassian.net/browse/ATT-1848) (`[Test]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.7`  
**Requirement Mapping**: `REQ-UI-213`  
**Test Mapping**: `TST-UI-167`  
**Branch**: `feature/ATT-1812`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Executive Summary & Verification Overview

During Sprint 2026-40.6 physical verification on Pixel 10 hardware, detailed workout inspection in Aftermath (`TrackOnMapScreen` / `MapDetailLayout`) exhibited total map preview disappearance (0 dp collapsed viewport). The root cause was identified as layout starvation: the unweighted children (`WorkoutHeader`, `ElevationProfile`, three continuous telemetry graphs for Heart Rate, Speed/Pace, and Power, plus analytics cards for Heart Rate zones, Power zones, and lap splits) cumulatively exceeded the available viewport height (~808 dp), starving the `Modifier.weight(1f)` allocated to the Google Map composable down to 0 dp. In addition, the lower section was unscrollable, preventing athletes from accessing charts below the fold and breaking snapshot sharing bitmap generation.

In Stage 4, the architectural fix was implemented in [MapDetailLayout.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt):
1. **Adaptive Scrollable Layout Architecture**: Introduced `hasTelemetryGraphs` and `hasScrollableContent` state detection, while making `analyticsContent` nullable (default `null`).
2. **Guaranteed Map Viewport & Dedicated Scroll Container**:
   - When detailed workout inspection with charts/analytics is active (`showMap == true && hasScrollableContent == true`), the Map container is constrained with `.weight(1f).heightIn(min = 240.dp).fillMaxWidth()`.
   - The lower section (Elevation profile, HR/Speed/Power graphs, and analytics cards) is enclosed within a dedicated vertical scroll container: `Column(modifier = Modifier.weight(1.2f).fillMaxWidth().verticalScroll(rememberScrollState()))`.
   - When viewing routes or segments without telemetry/analytics (`hasScrollableContent == false`), the Map container retains full viewport expansion and the lower container wraps its content (`wrapContentHeight()`), leaving zero whitespace.
   - When viewing `LIveSegmentSheet` (`showMap == false`), the root Column retains `wrapContentHeight()`, preserving bottom sheet contracts.
3. **Gesture Isolation**: The Google Map container resides strictly outside the vertical scroll container, preventing touch conflicts with map panning, rotation, and zooming.
4. **Snapshot Sharing Integrity**: Full unclipped graphics layer bitmaps are rendered across `elevationLayer` and `analyticsLayer` and stitched with the non-zero measured map bitmap.
5. **Synchronized Scrubbing**: Scrubbing `selectedDistance` across any graph continues to synchronously position the marker on the map above.

Verification was completed across automated contract tests, full clean-room unit regression, and hardware deployment on Pixel 10.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-213` | `TST-UI-167.1` | Automated Unit Test (`MapDetailLayoutTest`) | **PASSED** | `Verified` |
| `REQ-UI-213` | `TST-UI-167.2` | Integration Contract Tests (`LiveSegmentSheetLayoutTest`, `ElevationProfileLayoutTest`) | **PASSED** | `Verified` |
| `REQ-UI-213` | `TST-UI-167.3` | Multi-Metric Scrubbing Contract (`TelemetryMetricGraphTest`) | **PASSED** | `Verified` |
| `REQ-UI-213` | `TST-UI-167.4` | 9-Language Localization Audit (`TelemetryMetricLocalizationTest`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-UI-167.5` | Full Clean-Room `./gradlew testDebugUnitTest` | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Clean-Room Regression Suite (`./gradlew testDebugUnitTest`)
- Executed full test suite with 100% pass rate across all modules.
- Zero test failures, zero regressions.

### Targeted Contract Tests
- `MapDetailLayoutTest.testMapDetailLayout_declaresNullableAnalyticsContent`: PASSED
- `MapDetailLayoutTest.testMapDetailLayout_detectsScrollableContent`: PASSED
- `MapDetailLayoutTest.testMapDetailLayout_appliesResilientMinHeightAndScroll`: PASSED
- `MapDetailLayoutTest.testMapDetailLayout_preservesFullWeightForRoutesAndSegments`: PASSED
- `ElevationProfileLayoutTest`: PASSED
- `LiveSegmentSheetLayoutTest`: PASSED
- `TelemetryMetricGraphTest`: PASSED
- `TelemetryMetricLocalizationTest`: PASSED

---

## 4. Hardware / Physical Verification (Pixel 10)

- **Device**: Google Pixel 10 (Android 16, Build `66020DLCR002FL`).
- **Rendering & Viewport**:
  - The map preview is rendered with a minimum guaranteed viewport height of 240 dp above the telemetry charts.
  - The map track, start pin, stop pin, and share button are visible.
  - The lower charts container smoothly scrolls vertically, exposing Heart Rate, Speed/Pace, Power graphs, 5-zone distribution bars, and lap split cards.
  - Scrubbing across the elevation profile or telemetry graphs smoothly updates the position pin on the map.
  - Map touch gestures (pinch-to-zoom, pan, compass orientation) are isolated from chart vertical scrolling.
- **Route & Live Segment Verification**:
  - Route preview (`RouteOnMapScreen`) expands map to fill available space down to the compact profile.
  - Live segment bottom sheet (`LIveSegmentSheet`) retains compact `wrapContentHeight()` layout.

---

## 5. Invariant & Governance Verification

1. **Zero Production Regressions**: Clean-room unit test suite executed with 100% pass rate.
2. **Living Documentation Synchronized**: Status in `docs/requirements.md` and `docs/tests.md` updated to `Verified`.
3. **Subtask Completion**: Stage 5 subtask [ATT-1848](https://rainerblind.atlassian.net/browse/ATT-1848) transitioned to `Erledigt` via `freigabe`.
4. **Parent Ticket Final Review**: Parent ticket [ATT-1812](https://rainerblind.atlassian.net/browse/ATT-1812) transitioned to `Final Review (Human)` and assigned to human for final sprint review sign-off.
5. **Continuous Sprint Branch Integration**: Feature branch `feature/ATT-1812` integrated into `sprint/2026-40.7` with `--no-ff`.
