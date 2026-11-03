# Stage 5: Walkthrough & Verification - ATT-5079: [Feature] Move tracking tab.

**Ticket**: [[ATT-5079]](https://rainerblind.atlassian.net/browse/ATT-5079)  
**Sub-task**: [[ATT-5118]](https://rainerblind.atlassian.net/browse/ATT-5118) (`[Test]`)  
**Parent Epic**: None / Standalone Sprint Feature  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.1`  
**Requirement Mapping**: `REQ-UI-440` (*Move Tracking Tab Left/Right*)  
**Test Mapping**: `TST-UI-440` (*Move Tracking Tab Left/Right Test Suite*)  
**Branch**: `feature/ATT-5079`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-02  

---

## 1. Executive Summary & Verification Overview
* **Issue Addressed**: Athletes in Configuration Mode (`ScreenMode.CONFIGURATION`) could add, delete, rename, and copy tabs, but could not reorder existing tabs within an active sport profile (`ActivityType`).
* **Delivered Changes**:
  1. `TrackingTabConfigHeader.kt`: Added Move Left (`Icons.AutoMirrored.Filled.ArrowBack`) and Move Right (`Icons.AutoMirrored.Filled.ArrowForward`) icon buttons to Row 2 alongside Add Before, Copy, Delete, and Add After. Gated with `enabled = tabIndex > 0` (left) and `enabled = tabIndex < tabCount - 1` (right).
  2. `TrackingViewsDatabaseManager.java`: Implemented `moveTabView(viewId, moveLeft): Int` running atomically inside `beginTransaction()`, swapping adjacent tabs within the target `ActivityType`, normalizing `LAYOUT_NR` (1..N), and leaving other profiles untouched.
  3. `TrackingViewsRepository.kt` & `TrackingTabsViewModel.kt`: Exposed `moveTabView` on `Dispatchers.IO`, triggered `configUpdateTrigger`, and dispatched `TabNavigationEvent.NavigateTo(newIndex)` so the `HorizontalPager` smoothly follows moved tabs across repeated clicks.
  4. `TrackingTabsScreen.kt`: Wired `pagerState.currentPage` and `trackingViews.size` to the header.
  5. 9-Language Localization: Added `cd_move_tab_left` and `cd_move_tab_right` with 100% parity across all 9 supported locales.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-440.1` | `[TST-UI-440.1]` | Contract Test (`TrackingTabConfigHeaderContractTest`) | **PASSED** | `Verified` |
| `REQ-UI-440.2` | `[TST-UI-440.2]` | Unit / DB Tests (`TrackingViewsDatabaseManagerTest`) | **PASSED** | `Verified` |
| `REQ-UI-440.3` | `[TST-UI-440.3]` | Unit Tests (`TrackingViewsRepositoryTest`, `TrackingTabsViewModelTest`) | **PASSED** | `Verified` |
| `REQ-UI-440.4` | `[TST-UI-440.4]` | Localization Parity Test (`TranslationParityTest`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `[TST-UI-440.5]` | Tier 1 Targeted (`database.*`, `ui.tracking.*`, `localization.*`, `build.*`, `theme.*`) | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Unit Test Suite (Rule 60 Scope)
```text
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.*" \
  --tests "com.atrainingtracker.trainingtracker.ui.tracking.*" \
  --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" \
  --tests "com.atrainingtracker.trainingtracker.build.*" \
  --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"
BUILD SUCCESSFUL (733 tests, 0 failures, 0 skipped, 100% PASS)
```

### Requirement Governance Audit (`verify_requirement_governance.py`)
```text
python3 tools/verify_requirement_governance.py
Net-new requirement(s) detected: REQ-UI-440. Bypassing archaeology check cleanly.
```

---

## 4. Hardware / Physical Verification (Pixel 10)
* **Device Status**: `adb devices` reported `66020DLCR002FL offline`. Physical APK deployment skipped in accordance with Rules 60 & 62; fully verified via contract, database, and ViewModel unit tests.
* **Visual Consistency**: Verified against existing `TrackingTabConfigHeader.kt` Row 2 styling. Reused `IconButton`, `Icons.AutoMirrored.Filled.ArrowBack`/`ArrowForward`, standard padding, and `MaterialTheme.colorScheme.primary` tint.

---

## 5. Invariant & Governance Verification
1. **Atomic SQLite Transaction**: `moveTabView` executes in `db.beginTransaction()` on `Dispatchers.IO` with continuous 1..N order.
2. **Profile Isolation**: Tabs in other `ActivityType` profiles remain 100% unaltered.
3. **Living Documentation**: `REQ-UI-440` and `TST-UI-440` updated to `Verified`.
4. **Subtask Governance**: Stage 5 subtask submitted; ready for Gate 5 review and squash-merge (Rule 3).
