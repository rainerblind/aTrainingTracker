# Stage 2: Requirement & Test Specification - ATT-5485: Split-Pane Lieblingsstrecke Layout with Large Central Map

**Ticket**: [ATT-5485](https://rainerblind.atlassian.net/browse/ATT-5485)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.7` | **Branch**: `feature/ATT-5485`  
**Requirement Mapping**: `REQ-SET-068`, `REQ-SET-078` | **Test Spec ID**: `TST-SET-055`, `TST-SET-078`  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-06  

---

## 1. Requirement Specification (`REQ-SET-068`, `REQ-SET-078`)

### 1.1 Problem Statement & Rationale
On `WorkoutClusterHeatmapScreen.kt`, the entire 6-tier dashboard (`WorkoutClusterSummaryHeader` + `WorkoutClusterDetailDashboard` including `WorkoutClusterTrendChartCard` and its callout banner) is passed into `MapDetailLayout.header`, leaving `analyticsContent = null`. This consumes ~70% screen height, disables `SplitPaneDivider`, and crushes the map into ~30% height. Furthermore, the 50dp selected-workout banner in `WorkoutClusterTrendChartCard` is redundant. Restructuring the screen to use `MapDetailLayout`'s split pane expands the map to ~60–70% height with draggable resizing.

### 1.2 Functional & Architectural Requirements
1. **Compact 2-Line Header (`MapDetailLayout.header`, `REQ-SET-068`)**:
   - Row 1: `WorkoutClusterIdentityRow` + Top-End Action buttons (`EditLocationAlt`, `Edit`, Long-press Delete).
   - Row 2 (viewing): Compact base metadata (`RefDistance • SportName • LinkedEquipment`).
   - Row 2 (editing): Marker-type `FilterChip` row (`Start`, `End`, `Apex`, `MinAlt`, `MaxAlt`).
2. **Bottom Detail Tabs (`MapDetailLayout.analyticsContent`, `REQ-SET-068`, `REQ-SET-078`)**:
   - Host the detail dashboard in `analyticsContent`, enabling `MapDetailLayout`'s draggable `SplitPaneDivider`.
   - Compact tab switcher: Tab 1 (*Statistik/Details*) renders 3-tier performance & volume cards; Tab 2 (*Leistungsverlauf*, visible when valid trend count $N \ge 2$) renders `WorkoutClusterTrendChartCard`. When $N < 2$, Tab 1 renders directly without tab bar.
3. **Removal of Redundant Trend Callout Banner (`REQ-SET-078.4`)**:
   - Remove the bottom callout `Surface` card from `WorkoutClusterTrendChartCard.kt`.
   - Tapping an effort point within 24dp hit radius SHALL directly dispatch `onSelectWorkout(hit.workoutId)` to launch the peek sheet.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (Header & Map Sizing)**: *Given* `WorkoutClusterHeatmapScreen`, *When* displayed, *Then* `header` consumes only 2 lines, `SplitPaneDivider` is enabled, and the cluster map occupies the majority of the viewport.
* **AC-2 (Tab Switching)**: *Given* a cluster with $N \ge 2$ valid workouts, *When* switching tabs in `analyticsContent`, *Then* Tab 1 renders performance/volume cards and Tab 2 renders the trend chart. When $N < 2$, Tab 1 renders without tab bar.
* **AC-3 (Direct Point Tap-to-Peek & Banner Removal)**: *Given* `WorkoutClusterTrendChartCard`, *When* a user taps an effort point, *Then* `onSelectWorkout(hit.workoutId)` is invoked directly and zero callout banner is rendered.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Refines `REQ-SET-068` and `REQ-SET-078`. Targets `WorkoutClusterHeatmapScreen.kt`, `WorkoutClusterTrendChartCard.kt`, and `WorkoutClusterTrendLayoutAndVisibilityTest.kt`.
* **Historical Origin & Commit Trace**: Commit `7fc0e3c1` (`ATT-4067`, Sprint 2026-42.10) added trend card into `WorkoutClusterSummaryHeader`; `ATT-791` created initial 3-tier header dashboard.
* **Root Reason for Existing Formulation**: Header placement kept all statistics in a single composable without multi-pane tab state management in `analyticsContent`.
* **Preservation of Core Invariants**: Retains all 3 statistic tiers (PR, Average Time, Pace/Speed, Distance, Ascent, Moving Time, Recency), dual-metric trend chart, outlier Tukey fencing, and tap-to-peek launch.

---

## 2. Test Specification (`TST-SET-055`, `TST-SET-078`)

### 2.1 Test Cases
1. **TC-1: Compact Header & Split Pane Contract (`TST-SET-055`, `WorkoutClusterTrendLayoutAndVisibilityTest.kt`)**:
   - Verify `WorkoutClusterSummaryHeader` renders only compact identity and metadata rows in `header`.
   - Verify `MapDetailLayout` receives non-null `analyticsContent` hosting the bottom detail section.
2. **TC-2: Trend Card Tab Hosting & Callout Banner Removal (`TST-SET-078`, `WorkoutClusterTrendLayoutAndVisibilityTest.kt`)**:
   - Verify `WorkoutClusterTrendChartCard` is hosted inside Tab 2 when valid trend count $N \ge 2$.
   - Verify absence of redundant bottom callout `Surface` banner and verify direct tap invocation of `onSelectWorkout`.
3. **TC-3: Targeted Test Suite Regression**:
   - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.clusters.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"` (100% PASS).

---

## 3. Traceability Matrix
| Requirement ID | Test Case ID | Scope | Status |
| :--- | :--- | :--- | :--- |
| `REQ-SET-068` | `TST-SET-055` (TC-1) | Unit / Layout Contract (`WorkoutClusterTrendLayoutAndVisibilityTest`) | Planned (`ATT-5485`) |
| `REQ-SET-078` | `TST-SET-078` (TC-2, TC-3) | Unit / Layout Contract (`WorkoutClusterTrendLayoutAndVisibilityTest`, `WorkoutClusterTrendMathTest`) | Planned (`ATT-5485`) |
