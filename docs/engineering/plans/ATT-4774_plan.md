# Stage 3: Implementation Plan - ATT-4774: Reclaim Vertical Space in WorkoutDetailTabbedContent

**Ticket**: [ATT-4774](https://rainerblind.atlassian.net/browse/ATT-4774)  
**Sub-task**: [ATT-4784](https://rainerblind.atlassian.net/browse/ATT-4784) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2144](https://rainerblind.atlassian.net/browse/ATT-2144) (*[Epic] Landscape Mode*)  
**Target Release**: `v2.44.0` | **Active Sprint**: `Sprint 2026-43.10` | **Branch**: `feature/ATT-4774`  
**Requirement Mapping**: `REQ-UI-433` (refining `REQ-UI-408`, `REQ-UI-431`, `REQ-UI-225`)  
**Test Mapping**: `TST-UI-398` (`TST-UI-398.1` .. `TST-UI-398.5`)  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-23  

---

## 1. Problem Description & Background
In `WorkoutDetailTabbedContent` (landscape height ~411dp), static top chrome (`WorkoutHeader`, `PrimaryScrollableTabRow`, 36dp `GlobalTelemetryZoomToolbar`) consumes >55% screen height. In addition, `TelemetryMetricGraph` double-pads the 72% pane while sibling charts (`ElevationProfile`, Speed/Pace, W'bal) span 100%, causing X-axis ticks and vertical scrubbing cursors to misalign horizontally.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-433` (*Vertical Space Reclamation in WorkoutDetailTabbedContent*)
  * `REQ-UI-433.1`: Scroll-away collapsing header via `CollapsingAppBarNestedScrollConnection` -> `TST-UI-398.1`
  * `REQ-UI-433.2`: Compact single-line landscape `WorkoutHeader` ($\le 44\text{dp}$) -> `TST-UI-398.2`
  * `REQ-UI-433.3`: Zero-extra-row inline / floating zoom controls in `MapDetailLayout` -> `TST-UI-398.3`
  * `REQ-UI-433.4`: 72% horizontal plot alignment and double-padding elimination across stacked charts -> `TST-UI-398.4`
  * `REQ-UI-433.5`: Preservation of portrait invariants and full suite regression pass -> `TST-UI-398.5`

---

## 3. System Invariants & Preserved Behavior
1. **Zero Unintended Regressions**: 100% portrait aftermath layout fidelity and existing tests (`WorkoutDetailTabsTest`, `TelemetryMetricGraphLandscapeContractTest`) preserved.
2. **Scrubbing & Gesture Synchronization**: Multi-chart vertical cursor alignment and canvas touch isolation intact.
3. **Parent Human Decision Gate**: Terminal ticket closure strictly reserved for human in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes
* **UI Consistency (Rule 23)**:
  * Reference screens: `RouteTabbedScreen` (collapsing header/tabs) and `MapDetailLayout`.
  * Reused components: `CollapsingAppBarNestedScrollConnection`, `GlobalTelemetryZoomToolbar`, `WorkoutHeader`.
  * Theme tokens: `MaterialTheme.colorScheme.surface`, `primary`, `TTAlpha.Faint`, `LayoutConstants`.
  * New one-off styles: None.

| Component | Target File | Architectural Changes |
| :--- | :--- | :--- |
| **Top Chrome** | `WorkoutDetailTabbedContent.kt` | Attach `CollapsingAppBarNestedScrollConnection`; dynamically translate header/tabs with `offset` and slide `HorizontalPager` upward. |
| **Header** | `WorkoutHeader.kt` | Under `ORIENTATION_LANDSCAPE`, render compact single-line horizontal `Row(40.dp)` with icon, title, cluster, sport, date/time, and actions. |
| **Zoom Controls** | `MapDetailLayout.kt` & `GlobalTelemetryZoomToolbar.kt` | Eliminate 36dp reserved vertical row; render zoom controls as compact floating overlay at `Alignment.TopEnd`. |
| **Multi-Chart** | `TelemetryMetricGraph.kt` & `ElevationProfile.kt` | Enforce 72% plot width across all stacked charts in landscape; set `endPaddingPx = 0.dp` on the 72% curve pane. |

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Implement Single-Line Landscape WorkoutHeader (`REQ-UI-433.2`)
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt`
* Action: Branch on `LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE` to render compact single-line row.

### Step 2: Wire Collapsing NestedScrollConnection in WorkoutDetailTabbedContent (`REQ-UI-433.1`)
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDetailTabbedContent.kt`
* Action: Add `CollapsingAppBarNestedScrollConnection`, measure header/tab heights, translate header with `connection.appBarOffset`, adjust pager top padding.

### Step 3: Reclaim Zoom Toolbar Vertical Row in MapDetailLayout (`REQ-UI-433.3`)
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt`, `GlobalTelemetryZoomToolbar.kt`
* Action: Position zoom controls as a compact floating overlay at `Alignment.TopEnd` of the lower section; set `toolbarHeightPx = 0f` in `SplitPaneMath`.

### Step 4: 72% Plot Width Alignment & Double-Padding Elimination (`REQ-UI-433.4`)
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraph.kt`, `ElevationProfile.kt`, `MapDetailLayout.kt`, `WorkoutSummary.kt`
* Action: In landscape, apply 72/28 horizontal split to all sibling charts (`ElevationProfile`, Speed/Pace, W'bal); set `endPaddingPx = 0.dp` on 72% curve pane.

### Step 5: Unit & Contract Tests Execution (`TST-UI-398.1` .. `TST-UI-398.5`)
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDetailTabbedContentTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeaderLandscapeTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraphLandscapeAlignmentTest.kt`
* Action: Implement contract test suites and verify 100% pass rate:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*" --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.ui.components.workoutheader.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Run targeted test suite, Theme Color Centralization audit, and APK build.
* **Rollback**: Branch isolation (`feature/ATT-4774`); revert via `git checkout sprint/2026-43.10`.
