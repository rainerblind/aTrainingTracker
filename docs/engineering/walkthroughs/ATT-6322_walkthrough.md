# ATT-6322 Walkthrough: WorkoutDetailTabbedContent & WorkoutShareBottomSheet Fixes

**Ticket**: [ATT-6322](https://atrainingtracker.atlassian.net/browse/ATT-6322)  
**Parent Epic**: [ATT-68](https://atrainingtracker.atlassian.net/browse/ATT-68) (*Workouts & Aftermath*)  
**Sprint**: `2026-46.1` | **Target Release**: `V5.0.0` | **Status**: Verified  
**Requirement Mapping**: `REQ-REV-006` | **Test Mapping**: `TST-REV-007`  

---

## 1. Executive Summary

Sprint Review of `ATT-6208` identified three defects when triggering *'Karten- & Analyse-Snapshot'* in `WorkoutShareBottomSheet`:
1. `HorizontalPager` froze at 50% midpoint between tabs due to `pagerState.currentPage` key in `LaunchedEffect` cancelling animation coroutines (`Rule 80` / `docs/coding_standards.md` §1.11).
2. Athletes were stranded on the Map tab or detail screen without returning to the caller tab or summaries list.
3. A duplicate `WorkoutHeader` was rendered under the tab row in `MapDetailLayout`.

Under `ATT-6322` (governed by `REQ-REV-006` / `TST-REV-007`):
- **Pager Stability & Key Invariant**: Removed `pagerState.currentPage` from `LaunchedEffect` in `WorkoutDetailTabbedContent.kt`, keying strictly on `(pendingAutoShare, mapTabIndex)` and using instant `pagerState.scrollToPage(mapTabIndex)` without intermediate animation frames.
- **Header Deduplication & Offscreen Capture**: Added `showHeader: Boolean = true` to `MapDetailLayout.kt`. When `showHeader == false`, `header()` is measured with full constraints and recorded into `headerLayer` offscreen via an unplaced 0-height layout, completely eliminating visible duplicate headers under the tab row while preserving software bitmap fidelity for `combineWorkoutAndShare`.
- **Caller State Restoration**: Saved `originPageIndex` upon Option A selection and restored it in `handleSnapshotComplete`. Propagated `onSnapshotComplete` via `TrackOnMapScreen.kt` to auto-dismiss the temporary detail view in `WorkoutSummariesTabbedScreen.kt`, `WorkoutSummariesListFragment.kt`, and `WorkoutClustersScreen.kt`.

---

## 2. Traceability Matrix

| Requirement | Test Specification | Production Code | Verification Tests | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-REV-006.1` | `TST-REV-007.1` | `WorkoutDetailTabbedContent.kt` | `WorkoutShareBottomSheetContractTest` | **Verified** |
| `REQ-REV-006.2` | `TST-REV-007.2` | `WorkoutDetailTabbedContent.kt`, `TrackOnMapScreen.kt` | `WorkoutShareBottomSheetContractTest` | **Verified** |
| `REQ-REV-006.3` | `TST-REV-007.3` | `MapDetailLayout.kt`, `WorkoutDetailTabbedContent.kt` | `WorkoutShareBottomSheetContractTest` | **Verified** |
| `REQ-REV-006.4` | `TST-REV-007.4` | `MapDetailLayout.kt` | `WorkoutShareBottomSheetContractTest` | **Verified** |

---

## 3. Verification & Test Execution

- **Tier 1 Targeted Test Scope (Rule 60)**:
  - Command: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --max-workers=2 --tests "com.atrainingtracker.trainingtracker.ui.map.WorkoutShareBottomSheetContractTest" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*" --tests "com.atrainingtracker.trainingtracker.build.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.ui.components.core.*"`
  - Scope: 668 tests across aftermath UI, map share bottom sheet contracts, theme color centralization, core components, and ASPICE SWE build audits.
  - Result: **100% PASS** (0 failures, 0 errors, 0 regressions).
- **Living Documentation Governance**:
  - `python3 tools/verify_requirement_governance.py --sync-indexes && python3 tools/verify_requirement_governance.py`
  - Result: Master index tables synchronized with 100% parity (`docs/requirements.md`: 930 requirements, `docs/tests.md`: 932 tests).
- **Physical Device Execution**:
  - `adb devices` reported no physical devices attached. Per `Rule 79`, emulator launch was omitted.
