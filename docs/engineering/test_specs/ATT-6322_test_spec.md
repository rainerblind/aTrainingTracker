# Stage 2: Requirement & Test Specification - ATT-6322: WorkoutDetailTabbedContent & WorkoutShareBottomSheet Fixes

**Ticket**: [ATT-6322](https://atrainingtracker.atlassian.net/browse/ATT-6322)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-68](https://atrainingtracker.atlassian.net/browse/ATT-68) (*Workouts & Aftermath*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6322`  
**Requirement Mapping**: `REQ-REV-006` (*Map Snapshot Share Pager Stability, Header Deduplication & Caller State Restoration*)  
**Test Spec ID**: `TST-REV-007`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-09  

---

## 1. Requirement Specification (REQ-REV-006)

### 1.1 Problem Statement & Rationale
Selecting Option A (*'Karten- & Analyse-Snapshot'*) in `WorkoutShareBottomSheet` causes 3 UX defects:
1. `HorizontalPager` freezes at 50% between tabs due to `pagerState.currentPage` in `LaunchedEffect` keys (`Rule 80` / `docs/coding_standards.md` §1.11).
2. The athlete is stranded in the Map tab or detail screen instead of returning to the origin tab or list screen.
3. A duplicate `WorkoutHeader` is rendered visibly in the Map tab under the tab row because `header = snapshotHeader` is unconditionally rendered in `MapDetailLayout`.

### 1.2 Functional & Architectural Requirements
* **REQ-REV-006.1** (*Pager Scroll Key Invariant & Programmatic Navigation*): `WorkoutDetailTabbedContent` SHALL NOT include `pagerState.currentPage` in `LaunchedEffect` key list when automating snapshot tab switching (`pendingAutoShare`). The system SHALL use `pagerState.scrollToPage(mapTabIndex)` (instant transition without intermediate frames) so the pager coroutine is never cancelled mid-animation.
* **REQ-REV-006.2** (*Caller Tab & List Screen State Restoration*):
  - In `WorkoutDetailTabbedContent`, initiating an automated snapshot share SHALL capture `originPageIndex = pagerState.currentPage`. Upon `onSnapshotComplete`, the pager SHALL automatically scroll back to `originPageIndex`.
  - When opened with `initialAutoShareSnapshot = true` from workout list containers (`WorkoutSummariesTabbedScreen`, `WorkoutSummariesListFragment`, `WorkoutClustersScreen`), `onSnapshotComplete` SHALL be propagated via `TrackOnMapScreen` to automatically dismiss the detail screen, returning the athlete to the origin list view.
* **REQ-REV-006.3** (*MapDetailLayout Header Slot Offscreen Recording & Duplicate Prevention*): `MapDetailLayout` SHALL accept `showHeader: Boolean = true`. When `showHeader == false`, `MapDetailLayout` SHALL measure and record `header()` into `headerLayer` offscreen without rendering a second visible `WorkoutHeader` in the tab's upper layout column. `WorkoutDetailTabbedContent` SHALL pass `showHeader = false`.
* **REQ-REV-006.4** (*Preservation of Bitmap Output & Standalone Layout Invariants*): `headerLayer` in `MapDetailLayout` MUST continue producing a full-fidelity software bitmap for `combineWorkoutAndShare`. All standalone invocations of `MapDetailLayout` with `showHeader = true` MUST retain 100% visual layout parity.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Pager Stability & Tab Restoration)**:
  - *Given* an athlete viewing the Overview tab in `WorkoutDetailTabbedContent`,
  - *When* selecting 'Karten- & Analyse-Snapshot',
  - *Then* the pager instantly switches to the Map tab without freezing at 50%, captures the snapshot, and automatically returns to the Overview tab on completion.
* **Criterion 2 (Caller List Screen Dismissal)**:
  - *Given* an athlete triggering 'Karten- & Analyse-Snapshot' from a workout card in `WorkoutSummariesTabbedScreen`,
  - *When* the snapshot capture completes,
  - *Then* the temporary detail view automatically closes, returning the athlete directly to the summaries list.
* **Criterion 3 (Header Deduplication & Offscreen Capture)**:
  - *Given* the Map tab rendered inside `WorkoutDetailTabbedContent`,
  - *When* inspecting the screen,
  - *Then* only the single top chrome `WorkoutHeader` is visible above the tab row, while the generated share bitmap includes the full header.

---

## 2. Test Specification (TST-REV-007)

### Test Case 1: Pager LaunchedEffect Key Audit & Instant Navigation (`TST-REV-007.1`)
* **Scope**: Contract / Source Code Audit (`WorkoutDetailTabbedContentTest.kt`)
* **Assertion**: Verify `LaunchedEffect` does NOT include `pagerState.currentPage` in its keys and invokes `scrollToPage(mapTabIndex)` without intermediate animation frames.

### Test Case 2: Origin Tab & Caller Screen Restoration (`TST-REV-007.2`)
* **Scope**: Contract / Unit Test (`WorkoutDetailTabbedContentTest.kt`, `WorkoutShareBottomSheetContractTest.kt`)
* **Assertion**: Verify `WorkoutDetailTabbedContent` preserves origin tab index and restores it on snapshot completion; verify `TrackOnMapScreen` propagates `onSnapshotComplete` to list containers to dismiss the detail screen.

### Test Case 3: MapDetailLayout showHeader Parameter & Deduplication (`TST-REV-007.3`)
* **Scope**: Contract / Unit Test (`MapDetailLayoutTest.kt`, `WorkoutShareBottomSheetContractTest.kt`)
* **Assertion**: Verify `MapDetailLayout` declares `showHeader: Boolean = true`, records `headerLayer` offscreen when `showHeader == false`, and `WorkoutDetailTabbedContent` passes `showHeader = false`.

### Test Case 4: Tier 1 Targeted Unit Test Regression Suite (`TST-REV-007.4`)
* **Scope**: Targeted Unit Test Suite
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`
* **Expected Result**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-REV-007.1` | Contract | `WorkoutDetailTabbedContentTest.kt` | `REQ-REV-006.1` | Specified |
| `TST-REV-007.2` | Contract | `WorkoutDetailTabbedContentTest.kt`, `TrackOnMapScreen.kt` | `REQ-REV-006.2` | Specified |
| `TST-REV-007.3` | Contract | `MapDetailLayoutTest.kt`, `WorkoutShareBottomSheetContractTest.kt` | `REQ-REV-006.3`, `REQ-REV-006.4` | Specified |
| `TST-REV-007.4` | Regression | `ui.map.*`, `ui.aftermath.*` | `REQ-PRO-001` | Specified |
