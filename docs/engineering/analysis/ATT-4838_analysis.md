# Stage 1: Problem Domain & Root Cause Analysis - ATT-4838: [Bug] Going to the config tracking tabs does not open the first tab

**Ticket**: [ATT-4838](https://rainerblind.atlassian.net/browse/ATT-4838)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Active Sprint**: `2026-44.1`  
**Author**: AI Agent 1 (Software Architect)  
**Date**: 2026-11-01  

---

## 1. Problem Domain & Forensic Root Cause

When athletes navigate to *Settings -> Configure Displays* (`drawer_tracking_layouts`) and select an activity type (`MainActivityWithNavigation.onActivityTypeSelected`), `TrackingTabsScreen` transitions to `ScreenMode.CONFIGURATION`. In `ScreenMode.TRACKING`, page `0` is the Control Tab and page `k` ($k \ge 1$) is Sensor Tab $k - 1$. In `ScreenMode.CONFIGURATION` and `ScreenMode.PREVIEW`, there is no Control Tab; page `k` is Sensor Tab $k$.
Currently in `TrackingTabsScreen.kt`, `lastKnownPage` persists the raw pager page index across recompositions without accounting for the Control Tab offset or mode entry intent. If the athlete was on Sensor Tab 0 in tracking mode (`lastKnownPage = 1`), `remember(trackingViews.size, screenMode)` sets `initialPage = 1` in `ScreenMode.CONFIGURATION`. Because page `1` in configuration is Sensor Tab 1 (the 2nd tab), the editor opens on the 2nd (or later) tab rather than the first configured tab (index 0).

---

## 2. Independent Peer Review & Verification (4-Augen-Prinzip)

1. **Root Cause Confirmed**: In `TrackingTabsScreen.kt:404-421`, `initialPage` computes `lastKnownPage.coerceIn(0, max)` directly from the retained `lastKnownPage` state.
2. **Recreation Key Gap**: `key(trackingViews.size, screenMode)` does not include `activityType`. When switching configured sports with the same tab count while in configuration, `pagerState` is not recreated and retains its previous page.
3. **Transition Dynamics**:
   - `TRACKING -> CONFIGURATION` / `onActivityTypeSelected`: Athlete intends to configure displays from the beginning; initial page MUST be `0`.
   - `CONFIGURATION <-> PREVIEW`: Both modes share identical tab indexing; active sensor tab index MUST be preserved.
   - `PREVIEW -> TRACKING`: Exiting preview maps sensor tab $p$ to tracking page $p + 1$ (or 0 if empty).
4. **Pure State Resolution (`TrackingTabsPageResolver`)**: Encapsulate initial page calculation into a pure, testable object with zero Android/Compose dependencies for 100% test coverage.

---

## 3. Chesterton's Fence Archaeology (`REQ-PRO-022`)

1. *Original Requirement & Target*: Extends `REQ-UI-163` (Tracking Tabs Pager State Management) into `REQ-UI-413` (Tracking Tabs Mode Transition Page Resolution). Targets `TrackingTabsScreen.kt` and `MainActivityWithNavigation.kt`.
2. *Historical Origin & Commit Trace*: `ATT-1340` (introduced `lastKnownPage` to survive pager recreation during tab mutation), `ATT-1456` (integrated tab configuration directly into `TrackingTabsScreen`), `ATT-4000` (timeout-safe navigation).
3. *Root Reason for Existing Formulation*: `lastKnownPage` was introduced solely to prevent pager recreation during tab additions/deletions from resetting to 0. Mode transitions (`TRACKING <-> CONFIGURATION`) were not differentiated.
4. *Preservation of Core Invariants*: Tab addition/deletion pager stability (`lastKnownPage`), timeout-safe scroll handling, and `navigationEvent` channel architecture are 100% preserved.

---

## 4. Scope Grounding & Call Sites

- **In-Scope**:
  - `TrackingTabsPageResolver.kt`: Pure resolver function `resolveInitialPage(previousMode, newMode, lastKnownPage, tabCount, activityTypeChanged)`.
  - `TrackingTabsScreen.kt`: Integrate `TrackingTabsPageResolver`, include `activityType` in pager recreation key and remember keys.
  - Unit tests verifying `resolveInitialPage` across all transitions and edge cases.
- **Out-of-Scope**: Altering tab persistence schemas, sensor card layout composition, or navigation drawer menu structure.

---

## 5. Invariants & Risk Assessment

- **Risk Level**: **LOW**. Pure client UI index resolution; zero database migrations or service alterations.
- **Invariants**: Pager stability during tab editing, zero regressions in existing navigation and timeout test suites.
