# Stage 3: Implementation Plan - ATT-6322: WorkoutDetailTabbedContent & WorkoutShareBottomSheet Fixes

**Ticket**: [ATT-6322](https://atrainingtracker.atlassian.net/browse/ATT-6322)  
**Sub-task**: [ATT-6330](https://atrainingtracker.atlassian.net/browse/ATT-6330) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-68](https://atrainingtracker.atlassian.net/browse/ATT-68) (*Workouts & Aftermath*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6322`  
**Requirement Mapping**: `REQ-REV-006` (`docs/requirements/req_aftermath_reviews.md`)  
**Test Mapping**: `TST-REV-007` (`docs/tests/tst_aftermath_reviews.md`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-09  

---

## 1. Problem Description & Background
Sprint Review of `ATT-6208` identified that selecting Option A (*'Karten- & Analyse-Snapshot'*) freezes the `HorizontalPager` at 50% between tabs (due to `pagerState.currentPage` key in `LaunchedEffect`), strands the user on the Map tab/detail view without restoring caller state, and renders a duplicate `WorkoutHeader` inside the Map tab under the tab row.

---

## 2. Traceability & Requirements Mapping
* **REQ-REV-006.1** -> `TST-REV-007.1`: Remove `pagerState.currentPage` key; use `scrollToPage(mapTabIndex)`.
* **REQ-REV-006.2** -> `TST-REV-007.2`: Restore `originPageIndex` upon completion; bubble `onSnapshotComplete` to list containers.
* **REQ-REV-006.3 & REQ-REV-006.4** -> `TST-REV-007.3`: Add `showHeader: Boolean = true` to `MapDetailLayout`, offscreen `headerLayer` capture when `false`, pass `false` from `WorkoutDetailTabbedContent`.
* **Clean-Room Test Regression** -> `TST-REV-007.4`: Full targeted unit test suite passing with 100% parity.

---

## 3. System Invariants & Preserved Behavior
1. **Bitmap Fidelity**: `headerLayer` in `MapDetailLayout` continues outputting full software bitmap for `combineWorkoutAndShare`.
2. **Standalone Layout Parity**: All standalone `MapDetailLayout` callers retain visible headers via `showHeader = true` default.
3. **No Regressions**: Existing `WorkoutShareBottomSheetContractTest` and `WorkoutDetailTabsTest` continue passing.
4. **Human Decision Gate**: Parent ticket advances to `Implementation` and remains under human terminal authority.

---

## 4. Proposed Architectural Changes

### UI Consistency (Rule 23)
* **Reference**: `WorkoutDetailTabbedContent` Top Chrome & `MapDetailLayout` header slot.
* **Reused Components**: `MapDetailLayout`, `WorkoutHeader`, `HorizontalPager`.
* **Theme Tokens**: Standard `MaterialTheme.colorScheme.surface`, zero new one-off styles.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: MapDetailLayout showHeader Parameter & Offscreen Recording (`REQ-REV-006.3`, `REQ-REV-006.4`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt`
* Add `showHeader: Boolean = true` parameter.
* When `showHeader == true`, render visible `Surface` + `header()` as before.
* When `showHeader == false`, place `header()` in `Modifier.layout { m, c -> val p = m.measure(c); layout(0,0){ p.place(0,0) } }.drawWithContent { headerLayer.record { drawContent() } }`.

### Step 2: WorkoutDetailTabbedContent Key Fix, Header Suppression & Origin Tab Restoration (`REQ-REV-006.1`, `REQ-REV-006.2`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDetailTabbedContent.kt`
* Expose `onSnapshotComplete: (() -> Unit)? = null` parameter.
* Track `var originPageIndex by rememberSaveable { mutableStateOf<Int?>(null) }`. Set to `pagerState.currentPage` in `onSelectMapSnapshot`.
* Change `LaunchedEffect(pendingAutoShare, mapTabIndex)` (remove `pagerState.currentPage`). Use `pagerState.scrollToPage(mapTabIndex)`.
* Pass `showHeader = false` to all child `MapDetailLayout` call sites.
* In `onSnapshotComplete`: restore `originPageIndex` via `scrollToPage`, clear state, invoke `onSnapshotComplete?.invoke()`.

### Step 3: Propagate onSnapshotComplete Across Caller Screens (`REQ-REV-006.2`)
* **Files**: `TrackOnMapScreen.kt`, `WorkoutSummariesTabbedScreen.kt`, `WorkoutSummariesListFragment.kt`, `WorkoutClustersScreen.kt`
* Forward `onSnapshotComplete` from `TrackOnMapScreen` to `WorkoutDetailTabbedContent`.
* In list screens: wire `onSnapshotComplete` to reset `autoShareSnapshotOnOpen = false` and dismiss detail view (`selectedWorkoutIdForDetails = null` / `inspectedWorkout = null`).

### Step 4: Unit & Contract Tests (`TST-REV-007.1..4`)
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/WorkoutShareBottomSheetContractTest.kt`
* Add test cases asserting `LaunchedEffect` keys, `showHeader` parameter, offscreen recording, and callback propagation.
* Run targeted tests: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted tests during construction, followed by `verify_requirement_governance.py`.
* **Rollback**: Clean branch isolation on `feature/ATT-6322` allows atomic rollback if required.
