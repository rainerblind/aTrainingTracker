# Stage 2: Requirement & Test Specification - ATT-5079: [Feature] Move tracking tab.

**Ticket**: [[ATT-5079]](https://rainerblind.atlassian.net/browse/ATT-5079)  
**Sub-task**: [[ATT-5114]](https://rainerblind.atlassian.net/browse/ATT-5114) (`[Req & Test Spec]`)  
**Parent Epic**: None / Standalone Sprint Feature  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.1`  
**Requirement Mapping**: `REQ-UI-440` (*Move Tracking Tab Left/Right*)  
**Test Spec ID**: `TST-UI-440` (`TST-UI-440.1`..`TST-UI-440.5`)  
**Branch**: `feature/ATT-5079`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-02  

---

## 1. Requirement Specification (REQ-UI-440)

### 1.1 Problem Statement & Rationale
Athletes configuring custom tracking cockpits in Configuration Mode (`ScreenMode.CONFIGURATION`) need to reorder existing tabs within their active sport profile (`ActivityType`) without recreating tabs from scratch. The configuration header must provide Move Left and Move Right controls, atomically update `LAYOUT_NR` in SQLite scoped to the active profile, and emit a navigation event so `HorizontalPager` follows the tab for repeated repositioning.

### 1.2 Functional & Architectural Requirements
1. **Move Left & Move Right Header Actions (`REQ-UI-440.1`)**:
   - In Row 2 of `TrackingTabConfigHeader.kt`, provide `IconButton`s for Move Left (`Icons.AutoMirrored.Filled.ArrowBack`) and Move Right (`Icons.AutoMirrored.Filled.ArrowForward`) alongside `Add Before`, `Copy`, `Delete`, and `Add After`.
   - Disable Move Left when `tabIndex == 0`. Disable Move Right when `tabIndex >= tabCount - 1`.
2. **Transactional SQLite Reorder & Profile Isolation (`REQ-UI-440.2`)**:
   - `TrackingViewsDatabaseManager.java` and `TrackingViewsRepository.kt` SHALL swap `LAYOUT_NR` with the adjacent tab inside an atomic SQLite transaction on `Dispatchers.IO`, strictly scoped to the tab's `ActivityType`, and re-normalize `LAYOUT_NR` to continuous 1..N order.
3. **Reactive Pager Focus & Repeatable Navigation (`REQ-UI-440.3`)**:
   - Upon moving, `TrackingTabsViewModel.kt` SHALL emit `TabNavigationEvent.NavigateTo(newIndex)` so the `HorizontalPager` smoothly follows the active tab across repeated taps.
4. **9-Language Localization Parity (`REQ-UI-440.4`)**:
   - Provide 100% parity across all 9 supported locales:

| Locale | `cd_move_tab_left` | `cd_move_tab_right` |
| :--- | :--- | :--- |
| `values` (EN) | Move tab left | Move tab right |
| `values-de` (DE) | Tab nach links verschieben | Tab nach rechts verschieben |
| `values-es` (ES) | Mover pestaña a la izquierda | Mover pestaña a la derecha |
| `values-fr` (FR) | Déplacer l\'onglet vers la gauche | Déplacer l\'onglet vers la droite |
| `values-it` (IT) | Sposta scheda a sinistra | Sposta scheda a destra |
| `values-ja` (JA) | タブを左へ移動 | タブを右へ移動 |
| `values-nl` (NL) | Tabblad naar links verplaatsen | Tabblad naar rechts verplaatsen |
| `values-pl` (PL) | Przesuń kartę w lewo | Przesuń kartę w prawo |
| `values-pt` (PT) | Mover separador para a esquerda | Mover separador para a direita |

### 1.3 Acceptance Criteria (Given-When-Then) & System Invariants
* **Criterion 1 (Boundary Gating)**: *Given* `ScreenMode.CONFIGURATION`, *When* viewing tab at `index == 0`, *Then* Move Left is disabled; *When* at `index == tabCount - 1`, *Then* Move Right is disabled.
* **Criterion 2 (Atomic Swap)**: *Given* intermediate tab index, *When* Move Left/Right is tapped, *Then* `LAYOUT_NR` swaps with neighbor tab in an atomic transaction; other profiles remain untouched.
* **Criterion 3 (Pager Follow)**: *Given* a move operation succeeds, *When* repository updates, *Then* `HorizontalPager` smoothly animates to `newIndex`.
* **Invariants**: Transactional atomicity on `Dispatchers.IO`, strict `ActivityType` isolation, 9-locale parity.

---

## 2. Test Specification (TST-UI-440)

### Test Case 1: `testTrackingTabConfigHeader_hasMoveButtonsAndBoundaryContracts` (`[TST-UI-440.1]`)
* **Scope**: Contract / Unit Test (`TrackingTabConfigHeaderContractTest.kt`)
* **Action**: Assert `ArrowBack`, `ArrowForward`, `cd_move_tab_left`, `cd_move_tab_right`, `tabIndex`, and `tabCount` boundary gating.

### Test Case 2: `testMoveTabView_swapsLayoutNrAtomicallyAndIsolatesProfiles` (`[TST-UI-440.2]`)
* **Scope**: Unit / Database Test (`TrackingViewsDatabaseManagerTest.kt`)
* **Action**: Create sequential tabs across multiple `ActivityType`s; invoke `moveTabView(viewId, moveLeft)`.
* **Expected Result**: Swaps `LAYOUT_NR` within target `ActivityType`, normalizes 1..N order, returns new index, and preserves untouched profiles.

### Test Case 3: `testMoveTabView_repositoryAndViewModelNavigation` (`[TST-UI-440.3]`)
* **Scope**: Unit Test (`TrackingViewsRepositoryTest.kt` / `TrackingTabsViewModelTest.kt`)
* **Action**: Invoke `moveTabViewLeft` / `moveTabViewRight`; assert `_navigationEvent` emits `TabNavigationEvent.NavigateTo(newIndex)`.

### Test Case 4: 9-Language Localization Audit (`[TST-UI-440.4]`)
* **Scope**: Localization Parity Test (`TranslationParityTest.kt`)
* **Expected Result**: 100% parity across 9 locales for `cd_move_tab_left` and `cd_move_tab_right`.

### Test Case 5: Clean-Room Regression Suite (`[TST-UI-440.5]`)
* **Command**: `./gradlew testDebugUnitTest` (full pass with zero regressions).

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-440.1]` | Contract | `TrackingTabConfigHeader.kt` | `REQ-UI-440.1` | Specified |
| `[TST-UI-440.2]` | Database | `TrackingViewsDatabaseManager.moveTabView` | `REQ-UI-440.2` | Specified |
| `[TST-UI-440.3]` | ViewModel | `TrackingTabsViewModel.onMoveTab` | `REQ-UI-440.3` | Specified |
| `[TST-UI-440.4]` | Localization | `TranslationParityTest` | `REQ-UI-440.4`, `REQ-UI-106` | Specified |
| `[TST-UI-440.5]` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
