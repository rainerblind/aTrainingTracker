# Stage 1 Analysis: ATT-6322 - WorkoutDetailTabbedContent & WorkoutShareBottomSheet: Fix stuck 50% tab transition on Map Snapshot share, remove duplicate WorkoutHeader & restore caller tab/screen

**Ticket**: [ATT-6322](https://atrainingtracker.atlassian.net/browse/ATT-6322)  
**Sub-task**: [ATT-6325](https://atrainingtracker.atlassian.net/browse/ATT-6325) (`[Analysis]`)  
**Parent Epic**: [ATT-68](https://atrainingtracker.atlassian.net/browse/ATT-68) (*Workouts & Aftermath (WorkoutSummaries, Details & Graphs)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6322`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-09  
**Living Doc Shard**: `docs/requirements/req_aftermath_reviews.md` & `docs/tests/tst_aftermath_reviews.md`  

---

## 1. Problem Statement & Motivation
During Sprint Review of `ATT-6208` (`att_6208_review_stuck_transition.png`), triggering *"Karten- & Analyse-Snapshot"* in `WorkoutShareBottomSheet` exposed 3 defects:
1. `HorizontalPager` freezes at ~50% midpoint between *Übersicht* and *Karte & Diagramme*.
2. User is stranded on *Karte & Diagramme* (or in `WorkoutDetailTabbedContent` when launched from `WorkoutSummariesScreen` / `WorkoutClustersScreen`) without returning to the origin tab or list screen after snapshot completion.
3. A duplicate `WorkoutHeader` is rendered visibly in the *Karte & Diagramme* tab directly under the tab row because `header = snapshotHeader` is unconditionally rendered in `MapDetailLayout`.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Root Cause 1 (`HorizontalPager` stuck at 50%)**: `WorkoutDetailTabbedContent.kt` (lines 175–185) specifies `LaunchedEffect(pendingAutoShare, pagerState.currentPage, mapTabIndex)`. When `pagerState.animateScrollToPage(mapTabIndex)` executes, passing the 50% midpoint flips `pagerState.currentPage` from 0 to 1, cancelling the coroutine mid-flight (`Rule 80` / `docs/coding_standards.md` §1.11).
* **Root Cause 2 (Stranded Navigation State)**: Neither `WorkoutDetailTabbedContent` nor caller list screens (`WorkoutSummariesTabbedScreen`, `WorkoutSummariesListFragment`, `WorkoutClustersScreen`) save origin page state or wire `onSnapshotComplete` to restore caller page/screen.
* **Root Cause 3 (Duplicate Header)**: `MapDetailLayout.kt` (line 789) unconditionally displays `header()` in its main UI column; `WorkoutDetailTabbedContent` passes `header = snapshotHeader` to `MapDetailLayout` for all modes while already rendering top chrome `WorkoutHeader` above the tab row.
* **Independent Peer Review & Verification**:
  * Confirmed in `WorkoutDetailTabbedContent.kt`: `LaunchedEffect` cancellation confirmed via `pagerState.currentPage` key mutation.
  * Confirmed in `MapDetailLayout.kt`: `headerLayer` records `header()` directly from visible slot.
  * Confirmed callers: `WorkoutSummariesTabbedScreen`, `WorkoutSummariesListFragment`, and `WorkoutClustersScreen` launch `TrackOnMapScreen` via `initialAutoShareSnapshot = true` but lack completion dismissal handlers.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Remove `pagerState.currentPage` from `LaunchedEffect` keys; switch to instant programmatic navigation (`pagerState.scrollToPage(mapTabIndex)`).
  2. Restore caller tab (`originPageIndex`) upon snapshot completion in `WorkoutDetailTabbedContent`, and pop detail screen back to summaries list when launched via `initialAutoShareSnapshot`.
  3. Support `showHeader: Boolean = true` in `MapDetailLayout` (`false` in `WorkoutDetailTabbedContent`) so `snapshotHeader` records offscreen into `headerLayer` without visual duplication under the tab row.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No redesign of `combineWorkoutAndShare` bitmap stitching or Android system share intent dispatch.
  * No modification to tab config persistence or gesture isolation logic.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-PER-029` (`WorkoutShareBottomSheet Option A Automated Capture`) / introduces `REQ-REV-006` in `docs/requirements/req_aftermath_reviews.md`.
* **Historical Origin & Commit Trace**: Commit `62017d86` (`ATT-6208`) introduced automated snapshot triggers and `initialAutoShareSnapshot`; commit `ab9bebee` (`ATT-5809`) passed `snapshotHeader` to `MapDetailLayout` to ensure header bitmap inclusion.
* **Root Reason for Existing Formulation**: `snapshotHeader` was required so `headerLayer.record` provides the workout title and stats to `combineWorkoutAndShare`.
* **Preservation of Core Invariants**: `headerLayer` continues capturing full `snapshotHeader` offscreen; all existing standalone `MapDetailLayout` callers retain visible headers (`showHeader = true` default); zero main-thread bitmap composition.

---

## 5. Architectural Strategy & High-Level Solution
1. **Pager Scroll Key Fix & Instant Navigation**: In `WorkoutDetailTabbedContent.kt`, key `LaunchedEffect(pendingAutoShare, mapTabIndex)` (omitting `pagerState.currentPage`). Replace `animateScrollToPage` with `scrollToPage(mapTabIndex)` for programmatic snapshot.
2. **Origin Tab & Screen State Restoration**:
   * In `WorkoutDetailTabbedContent.kt`: save `originPageIndex = pagerState.currentPage` when initiating snapshot share; restore on `onSnapshotComplete`.
   * Propagate `onSnapshotComplete: (() -> Unit)?` via `TrackOnMapScreen` to `WorkoutSummariesTabbedScreen`, `WorkoutSummariesListFragment`, and `WorkoutClustersScreen` to close detail view when `initialAutoShareSnapshot == true`.
3. **Offscreen Header Recording in `MapDetailLayout`**:
   * Add `showHeader: Boolean = true` parameter to `MapDetailLayout`.
   * When `showHeader == false`, place `header()` in a 0-height offscreen layout measuring constraints and recording into `headerLayer` without drawing to screen canvas. Pass `showHeader = false` from `WorkoutDetailTabbedContent`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. `headerLayer` continues to output valid software bitmap for `combineWorkoutAndShare`.
  2. Standalone `MapDetailLayout` usage (e.g. `TrackOnMapScreen` non-tabbed, `ClimbOnMapScreen`, `RouteOnMapScreen`, `SegmentOnMapScreen`, `MapScreenWithTrack`) remains 100% visually identical.
  3. Automated test suite and requirement governance pass with 100% parity.
* **Risk Rating**: **LOW**. Localized Compose state and layout parameters with zero database or background service impacts.
