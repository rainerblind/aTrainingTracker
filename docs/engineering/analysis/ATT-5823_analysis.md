# Stage 1: Problem Domain & Root Cause Analysis - ATT-5823

**Ticket**: [ATT-5823](https://rainerblind.atlassian.net/browse/ATT-5823)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*) | **Story Points**: 2 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.11` | **Branch**: `feature/ATT-5823`  
**Requirement Mapping**: `REQ-UI-466` (extending `REQ-UI-165`, `REQ-UI-180`, `REQ-UI-186`, `REQ-DAT-011`)  
**Test Mapping**: `TST-UI-466` (verifying `TST-UI-132.4`, `TST-DAT-011.3`)  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-07  

---

## 1. Problem Domain & Background
In the **Lieblingsorte** view (`KnownLocationsScreen.kt` / `KnownLocationsViewModel.kt`), all known start locations are currently displayed unconditionally (`uiState.locations`). Locations with only 1–4 starts (e.g., vacation rides or incidental GPS recordings) clutter the screen. Furthermore, the screen lacks a filter UI in the top header, an active filter chip strip, and a filter bottom sheet, whereas `WorkoutClustersTabsScreen` and `RouteTabbedScreen` already feature canonical Material 3 filtering.

### Independent Peer Review & Hypothesis Verification (Rules 69 & 70, ATT-3534)
* **Code Audit**: Confirmed `KnownLocationsScreen.kt` (lines 317–318) renders unfiltered `uiState.locations`, and `KnownLocationsViewModel.kt` only filters by raw text `searchQuery` without start count thresholding.
* **Authoritative Starts Metric**: `startsByLocationId[item.id] ?: item.hitCount` correctly computes effective starts dynamically from `WorkoutRepository` (`REQ-DAT-011`), which must serve as the filtering metric.
* **Default Threshold Contract**: Upon opening *Lieblingsorte*, `minStarts` must default to 5 (`minStarts = 5`), so only true favorite locations are shown initially while preserving full access to incidental locations via filter reset.
* **Design System Harmonization**: Standardizing with `FilterActionButton` (with badge), `ActiveKnownLocationsFilterChipsRow` (`RemovableFilterChip`), and `KnownLocationsFilterBottomSheet` (`FilterBottomSheetScaffold`) aligns with the app's established UX rhythm.

---

## 2. Chesterton's Fence & Invariant Archaeology
* **Original Requirement ID & Target**: `REQ-UI-165` (Locations Management), `REQ-UI-180` (Streamlined management & search), `REQ-DAT-011` (Dynamic starts count).
* **Historical Origin & Commit Trace**: `ATT-1382` (`REQ-UI-165`), `ATT-1871`/`ATT-2051` (`REQ-UI-180`), `ATT-2134` (`REQ-DAT-011`).
* **Root Reason for Existing Formulation**: `KnownLocationsScreen` was originally created as an administrative location hub displaying all detected coordinates without thresholding. Clutter from incidental starts was unaddressed.
* **Preservation of Core Invariants**:
  1. *Non-Destructive Filtering*: Stored SQLite locations are never modified or hidden permanently; clearing the filter displays all locations (including 1–4 starts).
  2. *Authoritative Starts Formula*: Predicate strictly uses `startsByLocationId[item.id] ?: item.hitCount`, ensuring numerical parity with card badges.
  3. *Action & Navigation Invariants*: Single-tap edit, long-press context menu (`REQ-UI-061`), map preview, and starts/routes drill-downs remain intact.

---

## 3. User Scope Grounding (ATT-1250) & Complexity Split Guardrail
* **In-Scope**:
  1. Define `KnownLocationsFilterCriteria(query: String = "", minStarts: Int? = 5)` and integrate into `KnownLocationsUiState` & `KnownLocationsViewModel`.
  2. Add `FilterActionButton` with active badge in `KnownLocationsScreen` top app bar beside the sort button.
  3. Implement `ActiveKnownLocationsFilterChipsRow` displaying active filter chips (`"≥ 5 Starts"` and query) with single-tap dismissal.
  4. Implement `KnownLocationsFilterBottomSheet` using `FilterBottomSheetScaffold` with name search and starts presets (≥ 3, ≥ 5, ≥ 10, ≥ 25) + reset.
  5. Update `KnownLocationsListContent` to render `uiState.filteredLocations` with empty-filter fallback.
  6. Ensure 9-language localization parity for all new filter string resources.
  7. Extend `KnownLocationsViewModelTest` with threshold filtering assertions.
* **Out-of-Scope**: DataStore persistence of filter state (explicit requirement: always default to $\ge 5$ starts on screen entry).
* **Complexity Rating**: **2 SP (Standard)**. Self-contained UI/ViewModel extension adhering to established project filter scaffolds.

---

## 4. Blast Radius & Side-Effect Audit (Rule 68)
| Affected Component | Impact / Verification |
| :--- | :--- |
| `KnownLocationsViewModel.kt` | Filter criteria state, `applyFilter` predicate, and backwards compatibility for `searchQuery`. |
| `KnownLocationsScreen.kt` | Top bar filter button, active filter chip strip, bottom sheet trigger, and `filteredLocations` list binding. |
| `KnownLocationsFilterBottomSheet.kt` | Net-new composable based on `FilterBottomSheetScaffold`. |
| `ActiveKnownLocationsFilterChipsRow.kt` | Net-new composable based on `RemovableFilterChip`. |
| `res/values*/strings.xml` | 9-language localization parity for filter strings. |
| `KnownLocationsViewModelTest.kt` | Unit tests for default `minStarts = 5`, threshold selection, query combination, and reset. |

---

## 5. System Invariants & Risk Assessment
* **Core Invariants**: Zero SQLite schema alterations; all existing click handlers and test tags preserved.
* **Risk Rating**: **LOW** (Presentation-layer filtering and ViewModel state strictly scoped to favorite locations screen).
