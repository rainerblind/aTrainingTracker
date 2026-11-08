# ATT-6208 Analysis Deliverable: WorkoutShareBottomSheet Map & Analytics Direct Snapshot & Share

## 1. Metadata
* **Ticket Key**: ATT-6208
* **Summary**: WorkoutShareBottomSheet ('Karten- & Analyse-Snapshot'): Directly capture composite map/chart snapshot and open Android Share sheet instead of only navigating to Map tab
* **Target Release**: V5.0.0
* **Active Sprint**: 2026-45.13
* **Requirement Mapping**: REQ-PER-029 (`docs/requirements/req_aftermath_periods.md`)
* **Test Mapping**: TST-PER-037 (`docs/tests/tst_aftermath_periods.md`)

## 2. Problem Description & Root Cause Analysis
* **User Observation**: Selecting Option A ("Karten- & Analyse-Snapshot") in `WorkoutShareBottomSheet`:
  1. From Workouts List (`WorkoutSummary`): Only navigates to `WorkoutDetailScreen` without capturing a snapshot or launching the Android Share intent.
  2. From Workout Details (`WorkoutDetailTabbedContent`): Only scrolls to the *Karte & Diagramme* tab (`pagerState.animateScrollToPage(mapTabIndex)`) without triggering composite map+chart snapshot capture (`isSharing = true` in `MapDetailLayout`) or opening the Android Share chooser.
  3. Inside Map Tab (`MapDetailLayout`): `showFloatingShareButton` was default-suppressed when `workoutData != null`, leaving the user with no alternative in-map button to trigger snapshot generation.
* **Root Cause**:
  * In `WorkoutSummary.kt`: `onSelectMapSnapshot` invokes `onMapClick()`, which only selects the workout ID for detail viewing without communicating a pending snapshot share request.
  * In `WorkoutDetailTabbedContent.kt`: `onSelectMapSnapshot` only executes pager animation to `mapTabIndex` without setting any trigger state to initiate `isSharing` on `MapDetailLayout`.
  * In `MapDetailLayout.kt`: `isSharing` state was kept strictly private and internal, with no external trigger parameter accepted from parent composables (`WorkoutDetailTabbedContent`).

## 3. Architecture & Proposed Solution
1. **Programmatic Snapshot Trigger in `MapDetailLayout`**:
   * Expose `externalSnapshotTrigger: Boolean = false` and `onSnapshotComplete: (() -> Unit)? = null` on `MapDetailLayout`.
   * When `externalSnapshotTrigger` becomes true and `!isSharing`, set `isSharing = true` to trigger `MapEffect(..., shouldTakeSnapshot)`. Reset via `onSnapshotComplete`.
   * Enable `showFloatingShareButton = true` or provide fallback trigger support on the map tab.
2. **Automated Handoff in `WorkoutDetailTabbedContent`**:
   * Introduce `pendingSnapshotShare` state. When Option A is selected (or when opened with `initialAutoShareSnapshot = true`), switch to `mapTabIndex`, wait briefly (300-400ms) for map tiles/charts to settle, and activate `triggerMapSnapshot = true`.
3. **List-to-Detail Navigation Bridge**:
   * Add `onShareMapSnapshot: (WorkoutData) -> Unit` from `WorkoutSummary` -> `WorkoutList` -> `WorkoutTabsScreen` -> `WorkoutSummariesTabbedScreen` / `WorkoutSummariesListFragment`.
   * Opening details via `onShareMapSnapshot` sets `initialAutoShareSnapshot = true` on `TrackOnMapScreen` / `WorkoutDetailTabbedContent`.

## 4. Chesterton's Fence & Side-Effect Audit
* **ATT-5809 Context**: ATT-5809 centralized workout sharing onto `WorkoutHeader` and suppressed the floating share button in `MapDetailLayout` when `workoutData != null`. Bridging Option A directly to snapshot capture honors ATT-5809's vision while fixing the incomplete transition.
* **ATT-6214 Alignment**: `MapDetailLayout`'s top-end action column stacking (`Arrangement.spacedBy(16.dp)`) gracefully handles share button presence/absence without overlap.
* **Performance & Safety**: Map snapshot and `combineWorkoutAndShare` remain off-main-thread (`Dispatchers.Default` / `IO`). `delay` before snapshot ensures clean map tile rendering without UI jank.

## 5. Independent Peer Review & Verification
* **Contract Tests**: Verify parameter extensions on `MapDetailLayout`, `WorkoutDetailTabbedContent`, and `WorkoutSummary`.
* **Clean-Room Test Scope**: `com.atrainingtracker.trainingtracker.ui.map.*`, `com.atrainingtracker.trainingtracker.ui.aftermath.*`, and `tools/verify_requirement_governance.py`.
