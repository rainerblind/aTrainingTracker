# Stage 5: Walkthrough & Verification - ATT-4838: [Bug] Going to the config tracking tabs does not open the first tab

**Ticket**: [ATT-4838](https://rainerblind.atlassian.net/browse/ATT-4838)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.1`  
**Requirement Mapping**: `REQ-UI-435`  
**Test Mapping**: `TST-UI-435`  
**Branch**: `feature/ATT-4838`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-01  

---

## 1. Executive Summary & Verification Overview

Resolved the issue where opening display configuration from the navigation drawer opened on the 2nd (or later) tab instead of the first configured tab:
1. **Pure Domain Resolver (`TrackingTabsPageResolver`)**: Encapsulated initial pager index calculation into a pure function `resolveInitialPage` that accounts for the presence of the Control Tab at index 0 in `ScreenMode.TRACKING` vs. absence in `ScreenMode.CONFIGURATION` and `ScreenMode.PREVIEW`.
2. **First Tab Reset on Entry**: When entering `CONFIGURATION` from `TRACKING` or changing configured sport via drawer, `resolveInitialPage` returns `0`, resetting both `initialPage` and `lastKnownPage` to the first tab.
3. **Editor-Preview Round-Trip Preservation**: Toggling between `CONFIGURATION` and `PREVIEW` preserves the active sensor tab index ($p \leftrightarrow p$).
4. **Preview Exit to Tracking**: Exiting preview back to tracking cockpit maps sensor tab $p$ to tracking page $p + 1$ (or 0 if empty).
5. **Recreation Key**: Included `activityType` in `key(activityType, trackingViews.size, screenMode)` ensuring pager recreation when switching sports with identical tab counts.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-435.1` (Drawer Config Entry) | `TST-UI-435.1` | Unit Test & On-Device | **PASSED** | `Verified` |
| `REQ-UI-435.2` (Editor-Preview Round-Trip) | `TST-UI-435.2` | Unit Test | **PASSED** | `Verified` |
| `REQ-UI-435.3` (Preview Exit to Tracking) | `TST-UI-435.3` | Unit Test | **PASSED** | `Verified` |
| `REQ-UI-435.4` (ActivityType Switch) | `TST-UI-435.4` | Unit Test & On-Device | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

* `TrackingTabsPageResolverTest`: 100% PASS (verified entry from Control Tab, Sensor Tab 0, Sensor Tab 1, sport switching, editor-preview preservation, out-of-bounds clamping, and empty states).
* `TrackingTabsNavigationTimeoutTest`: 100% PASS (verified timeout safety and event collection).
* `SingleActivityNavigationTest` & `TrackingTabsViewModelInteropTest`: 100% PASS (verified activity callback interop).
* `ThemeColorCentralizationAuditTest`: 100% PASS (theme token adherence).
* Resources: `./gradlew processDebugResources` passed in 1s.
* Governance: `python3 tools/verify_requirement_governance.py` passed cleanly.

---

## 4. Hardware / Physical Verification (Pixel 10)

Deployed debug APK on Pixel 10 (`66020DLCR002FL`):
* **Drawer Navigation**: Opened drawer menu, scrolled to *Einstellungen -> Tracking Tabs*, selected sport *Radeln*.
* **First Tab Verification**: Verified `TrackingTabsScreen` entered `ScreenMode.CONFIGURATION` directly on the first tab (*Standard*, index 0) instead of tab 2. Captured screenshot: `docs/engineering/walkthroughs/img/ATT-4838_config_first_tab.png`.
* **Stability**: Zero crashes observed in `adb logcat -b crash -d`.

---

## 5. Invariant & Governance Verification

1. **Zero Regressions**: Tab addition/deletion pager stability and navigation event timeout safety are 100% preserved.
2. **Living Documentation Synchronized**: `req_ui_design_system.md` and `tst_ui_design_system.md` updated to `Verified`.
3. **Subtask Completion**: Stage 5 subtask transitioned to `Erledigt` via `gate-verdict PASS`.
4. **Parent Ticket Final Review**: Parent ticket transitioned to `Final Review (Human)` assigned to `human`.
