# Stage 3: Implementation Plan - ATT-5079: [Feature] Move tracking tab.

**Ticket**: [[ATT-5079]](https://rainerblind.atlassian.net/browse/ATT-5079)  
**Sub-task**: [[ATT-5115]](https://rainerblind.atlassian.net/browse/ATT-5115) (`[Impl-Plan]`)  
**Parent Epic**: Standalone / None  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.1`  
**Requirement Mapping**: `REQ-UI-440` (`REQ-UI-440.1`..`REQ-UI-440.4`)  
**Test Mapping**: `TST-UI-440` (`TST-UI-440.1`..`TST-UI-440.5`)  
**Branch**: `feature/ATT-5079`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-02  

---

## 1. Problem Description & Background
Athletes in Configuration Mode (`ScreenMode.CONFIGURATION`) need to reorder tracking tabs within their active sport profile (`ActivityType`) (e.g. moving the 2nd tab to the end). The configuration header must provide Move Left and Move Right controls, atomically update `LAYOUT_NR` in SQLite scoped to the active profile, and emit a navigation event so `HorizontalPager` smoothly follows the tab for repeated repositioning.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-440` (*Move Tracking Tab Left/Right*)
  * `REQ-UI-440.1` (Header Buttons & Boundary Gating) $\rightarrow$ `TST-UI-440.1` (`TrackingTabConfigHeaderContractTest`)
  * `REQ-UI-440.2` (Transactional SQLite Reorder & Profile Isolation) $\rightarrow$ `TST-UI-440.2` (`TrackingViewsDatabaseManagerTest`)
  * `REQ-UI-440.3` (Reactive Pager Focus & Repeatable Navigation) $\rightarrow$ `TST-UI-440.3` (`TrackingTabsViewModelTest`)
  * `REQ-UI-440.4` (9-Language Localization Parity) $\rightarrow$ `TST-UI-440.4` (`TranslationParityTest`)

---

## 3. System Invariants & Preserved Behavior
1. **Zero SQLite Corruption & Profile Isolation**: `moveTabView` runs in an atomic SQLite transaction strictly scoped to the tab's `ActivityType`, normalizing `LAYOUT_NR` to continuous 1..N order. Untouched profiles, `ROWS_TABLE`, and `SECTION_DIVIDERS_TABLE` remain unaltered.
2. **Deterministic Pager Sync**: Emitting `TabNavigationEvent.NavigateTo(newIndex)` ensures `HorizontalPager` follows the moved tab across repeated clicks.
3. **Boundary Gating**: Move Left disabled at `tabIndex == 0`; Move Right disabled at `tabIndex >= tabCount - 1`.
4. **9-Language Parity & Code Lock**: 100% localization parity; production code lock (`check-gate`) verified before edits.

---

## 4. Proposed Architectural Changes
* **Database (`TrackingViewsDatabaseManager.java`)**:
  * Add `@WorkerThread public int moveTabView(long viewId, boolean moveLeft)`: fetch views for `activityType` sorted by `LAYOUT_NR ASC, C_ID ASC`, swap target with neighbor, re-index `LAYOUT_NR = 1..N` inside an atomic transaction, return `newIndex`.
* **Repository (`TrackingViewsRepository.kt`)**:
  * Add `suspend fun moveTabView(tabViewId: Long, moveLeft: Boolean): Int`: executes on `Dispatchers.IO`, increments `configUpdateTrigger` on `Dispatchers.Main`.
* **ViewModel (`TrackingTabsViewModel.kt`)**:
  * Add `fun onMoveTab(tabViewId: Long, moveLeft: Boolean)`: launches coroutine on `viewModelScope`, calls repository, and emits `TabNavigationEvent.NavigateTo(newIndex)`.
* **UI (`TrackingTabConfigHeader.kt` & `TrackingTabsScreen.kt`)**:
  * Update `TrackingTabConfigHeader`: add `tabIndex: Int`, `tabCount: Int`, `onMoveTab: (Long, Boolean) -> Unit`. Row 2 order: `[Move Left] [Add Before] [Copy] [Delete] [Add After] [Move Right]`.
  * `TrackingTabsScreen.kt`: pass `pagerState.currentPage`, `trackingViews.size`, and wire callback to ViewModel.
* **UI Consistency (Rule 23)**:
  * Reference: `TrackingTabConfigHeader.kt` Row 2 existing actions.
  * Reused: `IconButton`, `Icons.AutoMirrored.Filled.ArrowBack` (Move Left), `Icons.AutoMirrored.Filled.ArrowForward` (Move Right).
  * Tokens: `MaterialTheme.colorScheme.primary` tint, standard spacing. Zero new one-off styles.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1: Localization Resources (9 Locales)**: Add `cd_move_tab_left` and `cd_move_tab_right` to all 9 `strings.xml` files.
2. **Step 2: Database Reorder Method**: Implement `moveTabView` in `TrackingViewsDatabaseManager.java` with atomic transaction and profile isolation.
3. **Step 3: Repository & ViewModel Wiring**: Wire `moveTabView` in `TrackingViewsRepository.kt` and `onMoveTab` in `TrackingTabsViewModel.kt` with navigation dispatch.
4. **Step 4: Config Header & Screen UI**: Add Move Left/Right buttons and boundary gating to `TrackingTabConfigHeader.kt` and wire in `TrackingTabsScreen.kt`.
5. **Step 5: Automated Tests**: Add test cases in `TrackingViewsDatabaseManagerTest.kt`, `TrackingTabsViewModelTest.kt`, and `TrackingTabConfigHeaderContractTest.kt`.
6. **Step 6: Targeted Verification**: Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.*" --tests "com.atrainingtracker.trainingtracker.database.TrackingViewsDatabaseManagerTest" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`.

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted Tier 1 unit and contract tests in Stage 4, followed by clean-room verification in Stage 5.
* **Rollback**: Isolated branch `feature/ATT-5079`; revert branch if regression detected before Stage 5 squash.
