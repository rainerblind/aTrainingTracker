# Stage 3: Implementation Plan - ATT-4838: [Bug] Going to the config tracking tabs does not open the first tab

**Ticket**: [ATT-4838](https://rainerblind.atlassian.net/browse/ATT-4838)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Active Sprint**: `2026-44.1`  
**Requirement Mapping**: `REQ-UI-435`  
**Test Mapping**: `TST-UI-435`  
**Author**: AI Agent 1 (Software Architect)  
**Date**: 2026-11-01  

---

## 1. Architectural Decomposition (SWE.2)

- **Pure Domain Resolver (`TrackingTabsPageResolver.kt`)**:
  - Implements pure function `resolveInitialPage(previousMode, newMode, lastKnownPage, tabCount, activityTypeChanged)`:
    - `TRACKING -> CONFIGURATION` / `onActivityTypeSelected`: Returns `0` (1st configured tab).
    - `CONFIGURATION <-> PREVIEW`: Preserves active sensor tab index ($p \leftrightarrow p$, clamped to `0 until tabCount`).
    - `PREVIEW -> TRACKING`: Returns `p + 1` (accounting for Control Tab at index 0), or `0` if empty.
    - `activityTypeChanged` in config/preview: Returns `0`.
    - Same-mode tab additions/deletions: Preserves and clamps `lastKnownPage`.
- **UI Compose Integration (`TrackingTabsScreen.kt`)**:
  - Track `previousScreenMode` and `previousActivityType` via `remember`.
  - Update `initialPage` computation via `remember(activityType, trackingViews.size, screenMode)`.
  - Update pager recreation key to `key(activityType, trackingViews.size, screenMode)`.

---

## 2. Atomic Implementation Steps

| Step | Action | Target File(s) | Verification / Test |
| :--- | :--- | :--- | :--- |
| **Step 1** | Implement `TrackingTabsPageResolver` object with `resolveInitialPage` | `TrackingTabsPageResolver.kt` | Compilation check |
| **Step 2** | Implement test suite covering all transitions and edge cases | `TrackingTabsPageResolverTest.kt` | `./gradlew testDebugUnitTest --tests "*TrackingTabsPageResolverTest*"` |
| **Step 3** | Integrate resolver and updated `key` into `TrackingTabsScreen.kt` | `TrackingTabsScreen.kt` | Compilation check |
| **Step 4** | Clean-room verification across all navigation and UI unit tests | `TrackingTabsNavigationTimeoutTest.kt`, etc. | `./gradlew testDebugUnitTest` |

---

## 3. UI Consistency & Invariants

- **UI Consistency**: No visual styling changes; purely resolves pager index.
- **System Invariants**:
  - 100% preservation of tab addition/deletion stability.
  - Zero disruption to `TabNavigationEvent.NavigateTo` or timeout safety (`ATT-4000`).
  - Zero database migrations or background service changes.
- **Rollback Safety**: Completely decoupled pure resolver function.
