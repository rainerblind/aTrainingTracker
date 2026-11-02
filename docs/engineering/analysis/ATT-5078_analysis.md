# Stage 1 Analysis: ATT-5078 - [Feature] Copy tracking tab to other sport types

**Ticket**: [ATT-5078](https://rainerblind.atlassian.net/browse/ATT-5078)  
**Sub-task**: [ATT-5105](https://rainerblind.atlassian.net/browse/ATT-5105) (`[Analysis]`)  
**Parent Epic**: None / Standalone Sprint Feature  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.1`  
**Branch**: `feature/ATT-5078`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-02  

---

## 1. Problem Statement & Motivation
Athletes configuring custom tracking cockpits often wish to reuse complex tab layouts (e.g. customized multi-field grids, power/cadence tiles, and section dividers) across different sport types (`ActivityType`), or duplicate an existing tab within the same sport type to test variations. Currently, tracking tabs can only be created from scratch as blank tabs or modified in place, forcing tedious manual reconfiguration for every sport profile.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Gap Analysis**:
  * `TrackingTabConfigHeader.kt` Row 2 only exposes `Add Before`, `Delete`, and `Add After` actions. No copy action exists.
  * Existing `ActivityTypeSelectionDialog.kt` is designed for single-selection profile switching; no multi-selection sheet exists for batch copying across profiles.
  * `TrackingViewsDatabaseManager.java` lacks deep-copy functionality: cloning a tab requires reading `VIEWS_TABLE` settings, computing the next sequential `LAYOUT_NR` per target sport type (`MAX(LAYOUT_NR) + 1`), inserting the new view row, and cloning all associated `ROWS_TABLE` sensor fields and `SECTION_DIVIDERS_TABLE` records within an atomic SQLite transaction.
* **Independent Peer Review & Verification (ATT-3534)**:
  * Live codebase audit confirms `VIEWS_TABLE` indexes tabs by `(ActivityType, LayoutNr ASC)`. Cloning to target profiles requires appending at `max(LAYOUT_NR) + 1` for each target profile.
  * `ROWS_TABLE` columns (`ROW_NR`, `COL_NR`, `SENSOR_TYPE`, `VIEW_SIZE`, `FILTER_TYPE`, `FILTER_CONSTANT`, `SHOW_VALUE`, `SHOW_BAR`, `COL_WEIGHT`, `SOURCE_DEVICE_ID`) and `SECTION_DIVIDERS_TABLE` columns (`ROW_NR`, `TITLE`) must be cloned completely to preserve layout fidelity.
  * Duplicating within the same sport type (`sourceActivityType in targetTypes`) is fully supported by this append semantic.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Add a Copy Tab icon button (`Icons.Default.ContentCopy`) in Row 2 of `TrackingTabConfigHeader.kt`.
  * Implement multi-select modal bottom sheet (`CopyTrackingTabDialog.kt`) listing all active `ActivityType` profiles with checkboxes.
  * Implement transactional deep-copy in `TrackingViewsDatabaseManager.java` and `TrackingViewsRepository.kt`.
  * Auto-navigate to the newly appended tab when duplicating into the currently active profile.
  * 9-language localization parity for all copy tab labels, titles, and content descriptions.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Modifying existing single-select `ActivityTypeSelectionDialog.kt` used for profile switching.
  * Altering telemetry processing, BANALService, or smartwatch/Pebble view synchronization.
  * Redesigning existing add/delete tab workflows or database schemas.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-UI-438` (*Copy Tracking Tab to Target Activity Types*), extending `REQ-UI-103` (*Customizable Cockpits*) and `REQ-UI-175` (*Tracking Tabs Configuration Transition*).
* **Historical Origin & Commit Trace**: `REQ-UI-103` established customizable sport-specific cockpits; `REQ-UI-175` established embedded tab configuration headers and navigation.
* **Root Reason for Existing Formulation**: Tabs were strictly scoped to individual `ActivityType` enums without cross-profile transfer utilities.
* **Preservation of Core Invariants**: Existing tab schemas (`VIEWS_TABLE`, `ROWS_TABLE`, `SECTION_DIVIDERS_TABLE`), data precision, and single-profile queries remain 100% unaltered.

---

## 5. Architectural Strategy & High-Level Solution
* **UI Layer**:
  * `TrackingTabConfigHeader.kt`: Add `IconButton` with `Icons.Default.ContentCopy` in Row 2 invoking `onCopyTab(tabViewId)`.
  * `CopyTrackingTabDialog.kt`: Compose bottom sheet with multi-select checkboxes for `ActivityType.values()`, defaulting to unchecked or source checked, with Cancel and Copy buttons.
  * `TrackingTabsViewModel.kt`: Manage `copySourceTabId: StateFlow<Long?>`, handle open/dismiss, execute copy via repository, and trigger pager navigation to the new tab index if active profile is targeted.
* **Data Layer**:
  * `TrackingViewsDatabaseManager.java`: Add `@WorkerThread public List<Long> copyTabViewToActivityTypes(long sourceViewId, List<ActivityType> targetTypes)` executing inside an atomic SQLite transaction.
  * `TrackingViewsRepository.kt`: Suspend method `copyTabView(sourceViewId: Long, targetTypes: List<ActivityType>)` on `Dispatchers.IO`, incrementing `configUpdateTrigger` on `Dispatchers.Main`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Deep-copy MUST preserve all sensor field configs, column weights, value/bar visibility, and section dividers identically.
  2. Database operations MUST execute on background worker threads (`Dispatchers.IO`) in an atomic transaction.
  3. Existing tabs and active telemetry MUST remain completely unaffected.
* **Risk Rating**: **LOW** (Additive non-breaking UI action, dedicated dialog, and isolated database clone method).
