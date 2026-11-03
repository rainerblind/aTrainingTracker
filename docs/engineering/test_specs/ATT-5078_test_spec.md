# Stage 2: Requirement & Test Specification - ATT-5078: [Feature] Copy tracking tab to other sport types

**Ticket**: [[ATT-5078]](https://rainerblind.atlassian.net/browse/ATT-5078)  
**Sub-task**: [[ATT-5106]](https://rainerblind.atlassian.net/browse/ATT-5106) (`[Req & Test Spec]`)  
**Parent Epic**: None / Standalone Sprint Feature  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.1`  
**Requirement Mapping**: `REQ-UI-439` (*Copy Tracking Tab to Target Activity Types*)  
**Test Spec ID**: `TST-UI-439` (`TST-UI-439.1`..`TST-UI-439.5`)  
**Branch**: `feature/ATT-5078`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-02  

---

## 1. Requirement Specification (REQ-UI-439)

### 1.1 Problem Statement & Rationale
Athletes configuring custom tracking cockpits often wish to reuse complex tab layouts across different sport types (`ActivityType`), or duplicate an existing tab within the same sport type to test variations. Currently, tracking tabs can only be created from scratch as blank tabs or modified in place, forcing tedious manual reconfiguration for every sport profile. The system must provide a convenient *Copy Tab* action in `TrackingTabConfigHeader.kt` opening a multi-select bottom sheet with deep-copy and append semantics across target sport types.

### 1.2 Functional & Architectural Requirements
1. **Copy Tab Header Action (`REQ-UI-439.1`)**:
   - In Row 2 of `TrackingTabConfigHeader.kt`, provide an `IconButton` with `Icons.Default.ContentCopy`, content description `R.string.cd_copy_tab`, invoking callback `onCopyTab(tabViewId: Long)`.
2. **Target Profile Selection Dialog (`REQ-UI-439.2`)**:
   - Provide a modal bottom sheet (`CopyTrackingTabDialog.kt`) listing all `ActivityType.values()` with authentic sport logos (`type.logoId`), localized titles (`type.titleId`), and checkboxes.
   - Confirming with $\ge 1$ selected profiles invokes copy; cancel or dismiss aborts without modification.
3. **Transactional Deep-Copy & Auto-Navigation (`REQ-UI-439.3`)**:
   - In `TrackingViewsDatabaseManager.java` and `TrackingViewsRepository.kt`, execute deep-copy in an atomic SQLite transaction on `Dispatchers.IO`:
     - Clone `VIEWS_TABLE` row with `LAYOUT_NR = max(LAYOUT_NR) + 1` for each target `ActivityType`.
     - Clone associated `ROWS_TABLE` and `SECTION_DIVIDERS_TABLE` rows referencing the new `view_id`.
   - If the currently active profile is targeted, refresh tab list and emit `TabNavigationEvent.NavigateTo(newIndex)`.
4. **9-Language Localization & Accessibility Parity (`REQ-UI-439.4`)**:
   - All user-facing strings (`cd_copy_tab`, `copy_tab_title`, `copy_tab_action`) must be provided with 100% parity across all 9 supported locales: EN, DE, ES, FR, IT, JA, NL, PL, PT.

### 1.3 Acceptance Criteria (Given-When-Then) & System Invariants
* **Criterion 1 (Header Action)**: *Given* tab config mode (`ScreenMode.CONFIGURATION`), *When* viewing Row 2, *Then* `Icons.Default.ContentCopy` is rendered with `R.string.cd_copy_tab`.
* **Criterion 2 (Selection Bottom Sheet)**: *Given* *Copy Tab* is tapped, *When* `CopyTrackingTabDialog` opens, *Then* all `ActivityType` profiles are displayed with authentic logos, localized titles, and checkboxes.
* **Criterion 3 (Deep-Copy & Append)**: *Given* $\ge 1$ target profiles selected, *When* tapping *Copy*, *Then* the source tab, sensor cells, and dividers are cloned and appended at `max(LAYOUT_NR) + 1` in an atomic transaction.
* **Criterion 4 (Active Profile Navigation)**: *Given* active profile is among targets, *When* copy completes, *Then* UI refreshes and navigates to the appended duplicate tab.
* **Invariants**: Transactional atomicity on `Dispatchers.IO`, zero main-thread database queries, unchanged existing tab schemas/data, 9-locale parity.

---

## 2. Test Specification (TST-UI-439)

### Test Case 1: `testTrackingTabConfigHeader_hasCopyButton` (`[TST-UI-439.1]`)
* **Scope**: Contract / Unit Test (`TrackingTabConfigHeaderContractTest.kt`)
* **Action**: Assert `content` contains `Icons.Default.ContentCopy`, `R.string.cd_copy_tab`, and `onCopyTab`.

### Test Case 2: `testCopyTabViewToActivityTypes_deepCopiesViewsRowsAndDividers` (`[TST-UI-439.2]`)
* **Scope**: Unit / Database Test (`TrackingViewsDatabaseManagerTest.kt`)
* **Action**: Call `copyTabViewToActivityTypes(sourceViewId, listOf(ActivityType.BIKE_POWER, ActivityType.GENERIC_HR))`.
* **Expected Result**: Appends tabs with sequential `LAYOUT_NR`, cloned rows and section dividers matching source configuration.

### Test Case 3: `testCopyTabView_repositoryAndViewModelNavigation` (`[TST-UI-439.3]`)
* **Scope**: Unit Test (`TrackingViewsRepositoryTest.kt` / `TrackingTabsViewModelTest.kt`)
* **Action**: Trigger copy targeting active profile; observe `navigationEvent` emits `TabNavigationEvent.NavigateTo(newIndex)`.

### Test Case 4: 9-Language Localization Audit (`[TST-UI-439.4]`)
* **Scope**: Localization Parity Test (`TranslationParityTest.kt`)
* **Expected Result**: 100% parity across `values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt` for `cd_copy_tab`, `copy_tab_title`, and `copy_tab_action`.

### Test Case 5: Clean-Room Regression Suite (`[TST-UI-439.5]`)
* **Command**: `./gradlew testDebugUnitTest` (full pass with zero regressions).

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-439.1]` | Contract | `TrackingTabConfigHeader.kt` | `REQ-UI-439.1` | Specified |
| `[TST-UI-439.2]` | Database | `TrackingViewsDatabaseManager.copyTabViewToActivityTypes` | `REQ-UI-439.2`, `REQ-UI-439.3` | Specified |
| `[TST-UI-439.3]` | ViewModel | `TrackingTabsViewModel.onCopyTab` | `REQ-UI-439.3` | Specified |
| `[TST-UI-439.4]` | Localization | `TranslationParityTest` | `REQ-UI-439.4`, `REQ-UI-106` | Specified |
| `[TST-UI-439.5]` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
