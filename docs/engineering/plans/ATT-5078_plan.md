# Stage 3: Implementation Plan - ATT-5078: [Feature] Copy tracking tab to other sport types

**Ticket**: [[ATT-5078]](https://rainerblind.atlassian.net/browse/ATT-5078)  
**Sub-task**: [[ATT-5107]](https://rainerblind.atlassian.net/browse/ATT-5107) (`[Impl-Plan]`)  
**Parent Epic**: None / Standalone Sprint Feature  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.1`  
**Requirement Mapping**: `REQ-UI-439` (*Copy Tracking Tab to Target Activity Types*)  
**Test Mapping**: `TST-UI-439` (*Copy Tracking Tab Test Suite*)  
**Branch**: `feature/ATT-5078`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-02  

---

## 1. Problem Description & Background
Athletes configuring custom cockpits need to copy tailored tracking tabs (with custom sensor grids, metric gauges, and section dividers) to other sport profiles or duplicate tabs within the same profile. The implementation introduces a copy action in the tab configuration header, a multi-select profile bottom sheet, transactional deep-copying in SQLite, and active profile auto-navigation.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-439` (`REQ-UI-439.1`..`REQ-UI-439.4`)
* **Test Mapping**: `TST-UI-439` (`TST-UI-439.1`..`TST-UI-439.5`)
  * `TST-UI-439.1` (Header UI contract) $\rightarrow$ `REQ-UI-439.1`
  * `TST-UI-439.2` (SQLite transactional deep-copy) $\rightarrow$ `REQ-UI-439.2`, `REQ-UI-439.3`
  * `TST-UI-439.3` (Repository and ViewModel auto-navigation) $\rightarrow$ `REQ-UI-439.3`
  * `TST-UI-439.4` (9-locale translation parity) $\rightarrow$ `REQ-UI-439.4`
  * `TST-UI-439.5` (Clean-room regression test) $\rightarrow$ `REQ-PRO-001`

---

## 3. System Invariants & Preserved Behavior
1. **Zero Unintended Regressions**: Existing tabs, layout ordering, and telemetry recording remain 100% unaltered.
2. **Thread Safety & Dispatcher Affinity**: Database deep-copy executes strictly on `Dispatchers.IO` in an atomic SQLite transaction; UI triggers update on `Dispatchers.Main`.
3. **Subtask Self-Sufficiency & Human Gate**: Subtasks transition to `Erledigt` via gate verdicts; parent ticket remains in review until human release approval.

---

## 4. Proposed Architectural Changes
* **Presentation Layer**:
  * `TrackingTabConfigHeader.kt`: Add `onCopyTab: (Long) -> Unit` and Copy Tab `IconButton` (`Icons.Default.ContentCopy`) in Row 2.
  * `CopyTrackingTabDialog.kt`: Implement M3 modal bottom sheet using `AppBottomSheetContent` and `AppDialogActions.CancelConfirm`, displaying checkboxes for `ActivityType.values()`.
  * `TrackingTabsViewModel.kt`: Add `copySourceTabId: StateFlow<Long?>`, `onOpenCopyDialog`, `onDismissCopyDialog`, and `onConfirmCopyTab` with auto-navigation.
* **Data Layer**:
  * `TrackingViewsDatabaseManager.java`: Add `@WorkerThread public List<Long> copyTabViewToActivityTypes(long sourceViewId, List<ActivityType> targetTypes)` with atomic SQLite transaction copying `VIEWS_TABLE`, `ROWS_TABLE`, and `SECTION_DIVIDERS_TABLE`.
  * `TrackingViewsRepository.kt`: Add suspend `copyTabView(sourceViewId, targetTypes)` invoking `viewsDbManager` on `Dispatchers.IO` and updating `configUpdateTrigger`.
* **UI Consistency (Rule 23)**:
  * **Reference screen**: `ActivityTypeSelectionDialog.kt` and `RouteSelectorModalBottomSheet.kt`.
  * **Reused components**: `AppBottomSheetContent`, `AppDialogActions.CancelConfirm`, `safePainterResource`.
  * **Theme tokens**: M3 `colorScheme.surface`, standard 16.dp horizontal padding, 4.dp item spacing.
  * **New one-off styles**: None.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
* **Step 1 (Localization)**: Add `cd_copy_tab`, `copy_tab_title`, and `copy_tab_action` across all 9 `res/values*/strings.xml`.
* **Step 2 (Data Layer)**: Implement `copyTabViewToActivityTypes` in `TrackingViewsDatabaseManager.java` and `copyTabView` in `TrackingViewsRepository.kt`.
* **Step 3 (UI & ViewModel)**: Create `CopyTrackingTabDialog.kt`, update `TrackingTabConfigHeader.kt`, `TrackingTabsScreen.kt`, and `TrackingTabsViewModel.kt`.
* **Step 4 (Unit & Contract Tests)**: Author `TrackingTabConfigHeaderContractTest.kt`, add unit tests in `TrackingViewsDatabaseManagerTest.kt` and `TrackingTabsViewModelTest.kt`, and verify `TranslationParityTest.kt`.
  * Test: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.TrackingViewsDatabaseManagerTest"`
  * Test: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Run targeted unit tests during construction, followed by full `./gradlew testDebugUnitTest` clean-room verification.
* **Rollback**: Branch isolation (`feature/ATT-5078`) enables safe reset without affecting other sprint work.
