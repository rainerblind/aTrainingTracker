# Stage 2: Requirement & Test Specification - ATT-4838: [Bug] Going to the config tracking tabs does not open the first tab

**Ticket**: [ATT-4838](https://rainerblind.atlassian.net/browse/ATT-4838)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Active Sprint**: `2026-44.1`  
**Requirement Mapping**: `REQ-UI-435`  
**Test Mapping**: `TST-UI-435`  
**Author**: AI Agent 2 (Test Architect)  
**Date**: 2026-11-01  

---

## 1. Traceability Matrix

| Requirement Clause | Test Spec ID | Verification Method | Target Component |
| :--- | :--- | :--- | :--- |
| `REQ-UI-435.1` (Drawer Config Entry) | `TST-UI-435.1` | Unit Test | `TrackingTabsPageResolverTest` |
| `REQ-UI-435.2` (Editor-Preview Round-Trip) | `TST-UI-435.2` | Unit Test | `TrackingTabsPageResolverTest` |
| `REQ-UI-435.3` (Preview Exit to Tracking) | `TST-UI-435.3` | Unit Test | `TrackingTabsPageResolverTest` |
| `REQ-UI-435.4` (ActivityType Switch & Stability)| `TST-UI-435.4` | Unit Test | `TrackingTabsPageResolverTest` |

---

## 2. Test Specifications (`TST-UI-435`)

### TST-UI-435.1: Drawer Configuration Entry Resolution
- **Setup**: `tabCount = 3`, `lastKnownPage` at `0`, `1` (viewing Sensor Tab 0 in tracking), or `2` (viewing Sensor Tab 1).
- **Execution**: Call `TrackingTabsPageResolver.resolveInitialPage(TRACKING, CONFIGURATION, lastKnownPage, tabCount)`.
- **Expected Result**: Returns `0` (1st configured tab) for all values of `lastKnownPage`.
- **Sport Change**: When `activityTypeChanged = true` in `CONFIGURATION`, returns `0`.

### TST-UI-435.2: Editor-Preview Round-Trip Resolution
- **Setup**: `tabCount = 3`, `lastKnownPage = 1` (Sensor Tab 1).
- **Execution**:
  - `resolveInitialPage(CONFIGURATION, PREVIEW, 1, tabCount)` -> Returns `1`.
  - `resolveInitialPage(PREVIEW, CONFIGURATION, 1, tabCount)` -> Returns `1`.
- **Bounds Clamping**: If `lastKnownPage = 5` and `tabCount = 2`, returns `1`.

### TST-UI-435.3: Preview Exit to Tracking Resolution
- **Setup**: `tabCount = 3`, athlete previewing Sensor Tab 0 (`lastKnownPage = 0`) or Sensor Tab 1 (`lastKnownPage = 1`).
- **Execution**: Call `resolveInitialPage(PREVIEW, TRACKING, lastKnownPage, tabCount)`.
- **Expected Result**: Returns `1` for `lastKnownPage = 0`, and `2` for `lastKnownPage = 1` (offset +1 for Control Tab).
- **Empty State**: If `tabCount = 0`, returns `0`.

### TST-UI-435.4: Same-Mode Stability & Tab Count Mutation
- **Setup**: Mode unchanged (`TRACKING -> TRACKING` or `CONFIGURATION -> CONFIGURATION`).
- **Expected Result**: Preserves `lastKnownPage` clamped to valid page boundaries.

---

## 3. Acceptance Verification Criteria

- All unit tests in `TrackingTabsPageResolverTest` pass 100%.
- Living documentation `req_ui_design_system.md` and `tst_ui_design_system.md` synchronized.
- Zero regressions in existing `TrackingTabsNavigationTimeoutTest` and `SingleActivityNavigationTest`.
