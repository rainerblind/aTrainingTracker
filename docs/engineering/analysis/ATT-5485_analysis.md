# Stage 1 Analysis: ATT-5485 - Lieblingsstrecke (WorkoutClusterHeatmapScreen): Large Central Map with Compact 2-Line Header, Bottom Detail/Trend Tabs, and Removal of Trend Workout Banner

**Ticket**: [ATT-5485](https://rainerblind.atlassian.net/browse/ATT-5485)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.7`  
**Branch**: `feature/ATT-5485`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-06  

---

## 1. Problem Statement & Motivation
Currently on `WorkoutClusterHeatmapScreen.kt`, the entire 6-tier dashboard (`WorkoutClusterSummaryHeader` + `WorkoutClusterDetailDashboard` including `WorkoutClusterTrendChartCard` and its selected-workout callout banner) is passed into `MapDetailLayout.header`, while `analyticsContent` is left `null`.
As a result:
- The fixed top header consumes **~70% of the vertical screen height**.
- Because `analyticsContent == null`, `MapDetailLayout`'s draggable `SplitPaneDivider` is disabled (`hasLowerSection = false`).
- The cluster map is crushed into the bottom **~30% of the screen**, making it difficult to inspect route details and detours.
- The redundant selected-workout callout banner (`Surface` with date, duration, speed/pace, and arrow) in `WorkoutClusterTrendChartCard` wastes ~50 dp of vertical space without providing meaningful value.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Root Cause**: `ATT-791` and `ATT-4067` incrementally added summary statistics and progression trend charts directly to `WorkoutClusterSummaryHeader`. Because `MapDetailLayout` was adopted primarily for its map slot rather than its split-pane architecture, `analyticsContent` remained unused.
* **Independent Peer Review & Verification (ATT-3534 / Rules 69 & 70)**:
  - Audited `WorkoutClusterHeatmapScreen.kt` (L383–450): `MapDetailLayout` is invoked with `header = { WorkoutClusterSummaryHeader(...) }` and no `analyticsContent`.
  - Audited `MapDetailLayout.kt` (L161–163, L687–732): `hasLowerSection` evaluates to `false` when `analyticsContent == null`, `metadataContent == null`, and `activeScrubPath.isNullOrEmpty()`. Consequently, `MapDetailLayout` bypasses `SplitPaneDivider` and renders `mapBox` with `Modifier.weight(1f)` below the massive header.
  - Audited `WorkoutClusterTrendChartCard.kt` (L320–420): The callout card consumes ~50 dp. Tapping it simply calls `onSelectWorkout(selectedPoint.workoutId)`. Direct hit-testing in `detectTapGestures` can invoke `onSelectWorkout(hit.workoutId)` directly on point tap, making the banner completely redundant.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Streamline `WorkoutClusterSummaryHeader` in `MapDetailLayout.header` to a compact 2-line layout:
     - Row 1: `WorkoutClusterIdentityRow` + Top-End Action buttons (Edit Fingerprint, Rename, Long-press Delete).
     - Row 2 (viewing): Compact base metadata row (`RefDistance • SportName • LinkedEquipment`).
     - Row 2 (editing): Fingerprint marker-type `FilterChip` row (`Start`, `End`, `Apex`, `MinAlt`, `MaxAlt`).
  2. Implement `analyticsContent` in `MapDetailLayout` with a compact Tab/Segmented switcher:
     - Tab 1 (*Statistik* / *Details*): Tier 1 Performance Card, Tier 2 Cumulative Volume Card, and Tier 3 Activity & Recency Card (`[N Aufzeichnungen]` button + `Letzte Aktivität`).
     - Tab 2 (*Leistungsverlauf* / *Effort Progression*, when `validTrendWorkoutsCount >= 2`): `WorkoutClusterTrendChartCard`.
  3. Remove the redundant selected-workout callout banner from `WorkoutClusterTrendChartCard.kt` and trigger `onSelectWorkout(hit.workoutId)` directly on point tap.
  4. Update contract/unit tests (`WorkoutClusterTrendLayoutAndVisibilityTest.kt`) to reflect the new layout contract.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. No changes to cluster identification, spatial fingerprinting, or clustering math (`WorkoutClusterEngine.kt`, `WorkoutClusterRepository.kt`).
  2. No changes to `MapDetailLayout.kt` split-pane or draggable divider internals.
  3. No changes to peek sheet behavior (`TrackOnMapScreen`) or move-workout dialogs.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement IDs & Targets**:
  - `REQ-SET-068` (*Comprehensive Workout Cluster Performance & Volume Statistics*, `ATT-791`): Redefines Section 2 layout by decoupling the 2-line compact identity/metadata header from the 3-tier performance & volume dashboard, hosting the dashboard inside `MapDetailLayout.analyticsContent`.
  - `REQ-SET-078` (*Historical Duration and Speed/Pace Trend Chart for Workout Clusters*, `ATT-4067`, `ATT-4287`): Refines `REQ-SET-078.1` (moves trend card to Tab 2 of `analyticsContent`) and `REQ-SET-078.4` (streamlines touch interaction to direct tap-to-peek launch, eliminating the 50 dp callout banner).
  - `REQ-UI-223` / `REQ-UI-221` (*MapDetailLayout Draggable Viewport*): Preserves and enables the standard split-pane divider contract on `WorkoutClusterHeatmapScreen`.
* **Historical Origin & Commit Trace**:
  - Commit `7fc0e3c1` (`ATT-4067`, Sprint 2026-42.10): Added `WorkoutClusterTrendChartCard` with callout banner directly into `WorkoutClusterDetailDashboard`.
  - `ATT-791`: Established the 3-tier aggregate dashboard inside `WorkoutClusterSummaryHeader`.
* **Root Reason for Existing Formulation**: Placing everything inside `WorkoutClusterSummaryHeader` was a rapid initial implementation to keep all cluster metadata together without managing multi-pane tab state in `analyticsContent`.
* **Preservation of Core Invariants**: Retains all 3 statistic tiers (PR, Average Time, Pace/Speed, Distance, Ascent, Moving Time, Recency, Recordings Button), dual-metric trend chart (Duration vs Speed/Pace), outlier Tukey fencing, gold PR styling, and tap-to-peek workflow.

---

## 5. Architectural Strategy & High-Level Solution
* **UI Architecture (`WorkoutClusterHeatmapScreen.kt`)**:
  - Header: Renders `WorkoutClusterSummaryHeader` limited to Row 1 (Identity + Actions) and Row 2 (Metadata or Marker Chips).
  - Analytics Lower Pane: Passes `analyticsContent = { ... }` into `MapDetailLayout`, enabling `SplitPaneDivider`.
  - Tab Switcher: Renders compact tabs (*Statistik* vs. *Leistungsverlauf*). When `validTrendWorkoutsCount < 2`, displays Tab 1 directly without redundant tab bar.
* **Trend Card Optimization (`WorkoutClusterTrendChartCard.kt`)**:
  - Delete `Surface` callout inspector block (L320–420).
  - Update `Canvas` `detectTapGestures` to call `onSelectWorkout(hit.workoutId)` immediately upon valid touch hit.
* **Side-Effect & Cross-Caller Audit (Rule 68)**:
  - `WorkoutClusterHeatmapScreen` is the only caller of `WorkoutClusterSummaryHeader`.
  - `ClusterOnMapSheet` uses `WorkoutClusterIdentityRow` and `WorkoutClusterMetadataBlock` (already separate, unaffected).
  - Zero database or ViewModel state changes; all reactive state flows (`clusterStats`, `clusterWorkouts`, `mapState`) remain identical.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Cluster fingerprint editing markers & drag interactions must remain fully functional. Map tracks, heatmap overlay, and peek bottom sheet behavior must remain unaffected.
* **Risk Rating**: **LOW** (UI layout composition refactoring within existing `MapDetailLayout` slots, zero DB/concurrency risks).
