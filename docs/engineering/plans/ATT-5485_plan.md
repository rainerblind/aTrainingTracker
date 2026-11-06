# Stage 3: Implementation Plan - ATT-5485: Split-Pane Lieblingsstrecke Layout with Large Central Map

**Ticket**: [ATT-5485](https://rainerblind.atlassian.net/browse/ATT-5485)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.7` | **Branch**: `feature/ATT-5485`  
**Requirement Mapping**: `REQ-SET-068`, `REQ-SET-078` | **Test Mapping**: `TST-SET-055`, `TST-SET-078`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-06  

---

## 1. Problem Description & Background
On `WorkoutClusterHeatmapScreen.kt`, the entire 6-tier dashboard (`WorkoutClusterSummaryHeader` + `WorkoutClusterDetailDashboard` including `WorkoutClusterTrendChartCard` and its callout banner) is passed into `MapDetailLayout.header`, leaving `analyticsContent = null`. This consumes ~70% screen height, disables `SplitPaneDivider`, and crushes the map into ~30% height. Furthermore, the 50dp selected-workout banner in `WorkoutClusterTrendChartCard` is redundant. Restructuring the screen to use `MapDetailLayout`'s split-pane architecture restores the large central map (~60–70% height) with draggable resizing, compact 2-line header, bottom Detail/Trend tabs, and direct tap-to-peek interaction.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-SET-068` (*Comprehensive Workout Cluster Performance & Volume Statistics*), `REQ-SET-078` (*Historical Duration and Speed/Pace Trend Chart for Workout Clusters*)
  * `REQ-SET-068`: Compact 2-line top header (`MapDetailLayout.header`) and 3-tier performance/volume statistics hosted in `MapDetailLayout.analyticsContent`.
  * `REQ-SET-078.1`: Progression trend chart hosted in Tab 2 of `analyticsContent` when valid count $N \ge 2$.
  * `REQ-SET-078.4`: Removal of redundant 50dp callout card; direct tap-to-peek launch on effort point tap.
  * `REQ-SET-078.5`: 9-language localization parity for tab labels (`cluster_tab_statistics`).
* **Test Mapping**: `TST-SET-055` (Compact Header & Split Pane Contract), `TST-SET-078` (Trend Tab Hosting, Direct Tap-to-Peek & Banner Removal)

---

## 3. System Invariants & Preserved Behavior
1. **Fingerprint Editing Invariance**: Start, End, Apex, and Min/Max altitude marker drag editing, chip selection, and dirty-state saving in `WorkoutClusterSummaryHeader` remain 100% operational.
2. **Cluster Statistics & Trend Math Invariance**: Zero alterations to `WorkoutClusterStats` computation, `WorkoutClusterTrendMath` binning/IQR fencing, or `WorkoutClustersViewModel`.
3. **Database & Schema Invariance**: Zero SQLite table alterations or migrations. UI-only split-pane refactoring.
4. **Interactive Peek & Sheet Invariance**: Tapping member tracks, markers, or trend points continues to dispatch `selectWorkoutForPeek`, opening `TrackOnMapScreen` bottom sheet.

---

## 4. Proposed Architectural Changes & UI Consistency
* **Compact Header (`WorkoutClusterSummaryHeader` in `WorkoutClusterHeatmapScreen.kt`)**:
  - Row 1: `WorkoutClusterIdentityRow` + Top-End Action buttons (`EditLocationAlt`, `Edit`, Long-press Delete).
  - Row 2: Viewing mode renders compact base metadata (`RefDistance • SportName • LinkedEquipment`); editing mode renders marker-type `FilterChip` row.
  - Removes embedded `WorkoutClusterDetailDashboard` from `header`.
* **Lower Viewport Analytics Tabs (`WorkoutClusterAnalyticsContent` in `WorkoutClusterHeatmapScreen.kt`)**:
  - Pass `analyticsContent = { ... }` into `MapDetailLayout`, enabling `SplitPaneDivider`.
  - When `validTrendWorkoutsCount >= 2`: Render `SingleChoiceSegmentedButtonRow` (or compact tab switcher) switching between Tab 1 (*Statistik*) and Tab 2 (*Leistungsverlauf* / `WorkoutClusterTrendChartCard`).
  - When `validTrendWorkoutsCount < 2`: Render Tab 1 (Tiers 1, 2, 3) directly without tab row.
* **Trend Card Streamlining (`WorkoutClusterTrendChartCard.kt`)**:
  - Delete redundant bottom `Surface` callout card (L320–420).
  - Update `Canvas` `detectTapGestures` hit detection to invoke `onSelectWorkout(hit.workoutId)` directly on point tap.
* **UI Consistency**:
  - Reference screens: `MapDetailLayout.kt`, `RouteOnMapScreen.kt`, `GradeZoneDistributionCard.kt`.
  - Reused tokens: `SingleChoiceSegmentedButtonRow`, `SegmentedButton`, `SegmentedButtonDefaults`, `MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
* **Step 1: 9-Language String Localization (`strings.xml`)**:
  - Add `cluster_tab_statistics` across all 9 locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
* **Step 2: Streamline `WorkoutClusterTrendChartCard.kt`**:
  - Remove redundant callout `Surface` banner and trigger `onSelectWorkout(hit.workoutId)` directly in `detectTapGestures`.
* **Step 3: Restructure `WorkoutClusterHeatmapScreen.kt`**:
  - Refactor `WorkoutClusterSummaryHeader` to 2-line layout (Row 1 Identity + Row 2 Base Metadata / Chips).
  - Implement `WorkoutClusterAnalyticsContent` and pass into `MapDetailLayout.analyticsContent`.
* **Step 4: Update Contract & Unit Tests**:
  - Update `WorkoutClusterTrendLayoutAndVisibilityTest.kt` for split-pane hosting, tab structure, and banner removal.
  - Run: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.clusters.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`
  - Run: `python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit tests (`ui.clusters.*`), `TranslationParityTest`, `ThemeColorCentralizationAuditTest`, and `verify_requirement_governance.py`.
* **Rollback**: UI-only change; clean `git revert` on `feature/ATT-5485` if needed.
