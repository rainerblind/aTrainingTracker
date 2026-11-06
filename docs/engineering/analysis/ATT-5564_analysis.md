# Stage 1 Analysis: ATT-5564 - Lieblingsstrecke (WorkoutClusterHeatmapScreen): Drag-Handle-Balken im Workout-Peek-Bottom-Sheet anzeigen

**Ticket**: [ATT-5564](https://rainerblind.atlassian.net/browse/ATT-5564)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (Auto Name / Route Clusters) | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-45.8` | **Branch**: `feature/ATT-5564`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-06  

---

## 1. Problem Statement & Motivation
* In `WorkoutClusterHeatmapScreen.kt`, selecting a workout member (from the cluster map polyline or the effort progression chart `WorkoutClusterTrendChartCard`) displays a persistent workout peek bottom sheet hosting `TrackOnMapScreen`.
* Currently, the sheet lacks a top drag handle affordance (`sheetDragHandle = null`). Athletes cannot visually discern that the peek sheet can be dragged upwards to inspect full workout telemetry, splits, elevation, and lap analytics (Sprint Review 2026-45.7 / ATT-5485).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Forensic RCA**: In `WorkoutClusterHeatmapScreen.kt` (L345–353), `BottomSheetScaffold` explicitly specifies `sheetDragHandle = null`, suppressing the drag indicator.
* **Independent Peer Review & Verification (4-Augen-Prinzip)**:
  * Audited `WorkoutClusterHeatmapScreen.kt` (L351–353): Sheet peek height is `BottomSheetDesign.PeekHeightWorkout + navBarHeight` when `peekedWorkoutDataWithTrack != null && !isEditingFingerprint`.
  * Evaluated `BottomSheetDefaults.DragHandle()` vs. `MinimumDragHandle()`:
    * `BottomSheetDefaults.DragHandle()` (M3 default): 32dp x 4dp with 22dp top and 22dp bottom padding (48dp vertical footprint). Consuming 48dp of `PeekHeightWorkout` (140dp) leaves only 92dp, risking visual compression or clipping of `WorkoutHeader` rows.
    * `MinimumDragHandle()` (`com.atrainingtracker.trainingtracker.ui.components.core.MinimumDragHandle`, `REQ-UI-196` / `ATT-1644`): Standardized 32dp x 3dp pill with 8dp top and 4dp bottom padding (15dp vertical footprint), matching `AppModalBottomSheet`, `FilterBottomSheetScaffold`, and `MapDetailLayout`.
  * Verified conditional visibility: Suppressing `sheetDragHandle` when `peekedWorkoutDataWithTrack == null || isEditingFingerprint` guarantees zero phantom touch captures or visual leaks while browsing or editing cluster fingerprints.

---

## 3. User Scope Grounding (ATT-1250) & Side-Effect Audit (Rule 68)
* **In-Scope**:
  * Provide a visible drag handle (`MinimumDragHandle()` conforming to `REQ-UI-196` / design system baseline, satisfying the ticket request for a Zieh-Indikator) in `WorkoutClusterHeatmapScreen.kt` on `BottomSheetScaffold` when `peekedWorkoutDataWithTrack != null && !isEditingFingerprint`.
  * Extend `BottomSheetVisualContractTest.kt` to ensure `sheetDragHandle` is not set to `null` on `WorkoutClusterHeatmapScreen`.
* **Out-of-Scope**:
  * Modifying `TrackOnMapScreen` or `WorkoutDetailTabbedContent`.
  * Modifying cluster spatial grouping algorithms, polyline heatmap decoders, or fingerprint editing workflows.
* **Complexity Rating**: **1 SP (Trivial)**.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-UI-221` (*Standardized Bottom Sheet Peek Height Baselines*) and references `REQ-UI-196` (*Subtle Drag Handle Affordance & Unified Sheet Styling*).
* **Historical Origin & Commit Trace**: Introduced in commit `f4d05a73` (SCRUM-229) and peek height standardized in commit `9c3dd09e` (ATT-1645).
* **Root Reason for Existing Formulation**: `sheetDragHandle = null` was originally set to keep the top of `TrackOnMapScreen` flush, overlooking that athletes need a visual drag affordance to discover expandable workout details.
* **Preservation of Core Invariants**: `BottomSheetDesign.PeekHeightWorkout` calibration, surface background colors, and fingerprint editing state transitions remain intact.

---

## 5. Architectural Strategy & High-Level Solution
* Update `WorkoutClusterHeatmapScreen.kt` `BottomSheetScaffold` configuration:
  Bind `sheetDragHandle = if (peekedWorkoutDataWithTrack != null && !isEditingFingerprint) { { MinimumDragHandle() } } else null`.
* Update `BottomSheetVisualContractTest.kt`: Assert `WorkoutClusterHeatmapScreen.kt` does not set `sheetDragHandle = null` and binds `MinimumDragHandle`.

---

## 6. System Invariants & Target Release Verification
* **Target Release**: `V5.0.0` (verified active unreleased version in Jira via `jira_util.py versions`).
* **Risk Rating**: **LOW** (Declarative Compose slot enhancement; zero concurrency or database risk).
