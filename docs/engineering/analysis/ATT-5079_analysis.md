# Stage 1 Analysis: ATT-5079 - [Feature] Move tracking tab.

**Ticket**: [ATT-5079](https://rainerblind.atlassian.net/browse/ATT-5079)  
**Sub-task**: [ATT-5112](https://rainerblind.atlassian.net/browse/ATT-5112) (`[Analysis]`)  
**Parent Epic**: Standalone / None  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.1`  
**Branch**: `feature/ATT-5079`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-02  

---

## 1. Problem Statement & Motivation
- In Configuration Mode (`ScreenMode.CONFIGURATION`), athletes can add, delete, rename, and copy tracking tabs, but cannot reorder existing tabs within an `ActivityType`.
- Reordering currently requires recreating tabs from scratch or performing complex copy/delete workarounds.
- Scope: Add *Move Left* (`Icons.AutoMirrored.Filled.ArrowBack`) and *Move Right* (`Icons.AutoMirrored.Filled.ArrowForward`) buttons in Row 2 of `TrackingTabConfigHeader.kt` (disabled at boundary positions `index == 0` / `index == tabCount - 1`), atomically update `LAYOUT_NR` in SQLite (`VIEWS_TABLE`), and emit `TabNavigationEvent.NavigateTo(newIndex)` so the `HorizontalPager` follows the moved tab.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
- **Gap Analysis**:
  - `TrackingTabConfigHeader.kt`: Row 2 contains `Add Before`, `Delete`, `Copy`, and `Add After`, but lacks move controls and tab index/count parameters.
  - `TrackingViewsDatabaseManager.java`: Tab order relies on `LAYOUT_NR ASC`. Existing methods lack an atomic swap/reorder operation scoped to an `ActivityType`.
  - `TrackingViewsRepository.kt` & `TrackingTabsViewModel.kt`: Lack `moveTabView` / `onMoveTabLeft` / `onMoveTabRight` methods and navigation dispatch.
- **Independent Peer Review & Verification**:
  - Verified against live codebase (`TrackingTabConfigHeader.kt:109`, `TrackingViewsRepository.kt:225`, `TrackingTabsScreen.kt:480-515`).
  - `navigateToPagerTarget` already handles `TabNavigationEvent.NavigateTo(newIndex)` natively.
  - Reordering must be strictly scoped to the tab's `ActivityType` and execute inside an atomic SQLite transaction (`beginTransaction()`), normalizing `LAYOUT_NR` (1..N) to prevent order corruption or duplicate sequence numbers across multiple profiles.

---

## 3. User Scope Grounding (ATT-1250)
- **In-Scope Goals**:
  1. Add *Move Left* (`ArrowBack`) and *Move Right* (`ArrowForward`) to Row 2 of `TrackingTabConfigHeader`, disabled at `index == 0` and `index == tabCount - 1`.
  2. Implement atomic `moveTabView(viewId, moveLeft): Int` in `TrackingViewsDatabaseManager` (scoped strictly to the tab's `ActivityType`).
  3. Emit `TabNavigationEvent.NavigateTo(newIndex)` in `TrackingTabsViewModel` on successful move so `HorizontalPager` smoothly follows the moved tab across repeated clicks.
  4. 9-language localization parity for action content descriptions (`cd_move_tab_left`, `cd_move_tab_right`).
  5. Unit and contract test coverage across DB manager, ViewModel, and Header.
- **Out-of-Scope Non-Goals (Scope Bounding)**:
  - Drag-and-drop tab reordering gestures on the tab row itself (unrequested; header buttons suffice).
  - Cross-`ActivityType` tab moves (covered by copy in `ATT-5078`).
  - Refactoring legacy un-scoped `addEmptyTabView` / `deleteTabView` queries outside the scope of this ticket.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
- **Original Requirement ID & Target**: Net-new requirement `REQ-UI-440` (*Move Tracking Tab Left/Right*), extending `REQ-UI-103` (*Customizable Cockpits*), `REQ-UI-175` (*Tracking Tabs Configuration Transition*), and `REQ-UI-439` (*Copy Tracking Tab*). Targets `TrackingTabConfigHeader.kt`, `TrackingTabsScreen.kt`, `TrackingTabsViewModel.kt`, `TrackingViewsRepository.kt`, and `TrackingViewsDatabaseManager.java`.
- **Historical Origin & Commit Trace**: `ATT-1281` (Sprint 2026-40.4) & `ATT-5078` established tab management in `TrackingTabConfigHeader`. Tabs have an intrinsic `LAYOUT_NR` defining presentation order.
- **Root Reason for Existing Formulation**: Tab order was previously determined solely by creation order (`addEmptyTabView` before/after). Manual reordering was deferred until tab CRUD and copy workflows stabilized.
- **Preservation of Core Invariants**: Existing tab table schema (`VIEWS_TABLE`, `ROWS_TABLE`, `SECTION_DIVIDERS_TABLE`), sensor configs, telemetry logging, and other sport profiles MUST remain 100% unaltered.

---

## 5. Architectural Strategy & High-Level Solution
- **UI (`TrackingTabConfigHeader.kt`, `TrackingTabsScreen.kt`)**:
  - Add `tabIndex: Int`, `tabCount: Int`, `onMoveTabLeft: (Long) -> Unit`, and `onMoveTabRight: (Long) -> Unit`.
  - Arrange Row 2: `[Move Left] [Add Before] [Copy] [Delete] [Add After] [Move Right]`.
  - Disable: `enabled = tabIndex > 0` (left) and `enabled = tabIndex < tabCount - 1` (right).
- **Persistence (`TrackingViewsDatabaseManager.java`)**:
  - `moveTabView(long viewId, boolean moveLeft)`: Query views for `activityType` sorted by `LAYOUT_NR ASC, C_ID ASC`, swap target view with adjacent neighbor, re-index `LAYOUT_NR = 1..N` inside an atomic transaction, return `newIndex`.
- **ViewModel & Repository (`TrackingTabsViewModel.kt`, `TrackingViewsRepository.kt`)**:
  - `moveTabView` runs on `Dispatchers.IO`, updates `configUpdateTrigger`, and sends `TabNavigationEvent.NavigateTo(newIndex)`.

---

## 6. System Invariants & Risk Assessment
- **Core Invariants**:
  1. Atomic transaction guarantees zero orphaned or corrupted `LAYOUT_NR` sequence numbers.
  2. Strict isolation to the active `ActivityType`—other profiles' tabs remain untouched.
  3. `HorizontalPager` follows moved tab reliably across repeated taps.
  4. 100% clean-room test pass rate and 9-language localization parity.
- **Risk Rating**: **LOW**. Isolated to UI configuration header and local tab reordering query; zero impact on active tracking, sensors, or background services.
