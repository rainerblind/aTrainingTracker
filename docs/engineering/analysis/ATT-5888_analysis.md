# Stage 1 Analysis: ATT-5888 - Uniform 'Clear all' Action in ActiveFilterChipsRow

**Ticket**: [ATT-5888](https://atrainingtracker.atlassian.net/browse/ATT-5888)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Global UI & Design System*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5888`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-08  

---

## 1. Problem Statement & Motivation
* In `ActiveKnownLocationsFilterChipsRow.kt` (Lieblingsorte), the "Alle zurücksetzen" (`R.string.filter_clear_all`) `TextButton` is rendered unconditionally whenever any filter is active (`!criteria.isEmpty`, `activeFilterCount >= 1`).
* In contrast, all other 5 filterable list implementations gate the "Clear all" button behind `activeFilterCount > 1` (or `>= 2`), hiding it when exactly 1 filter is active (such as the default `minHitCount = 5` on Favorite Routes / Lieblingsstrecken per `REQ-UI-467.1`).
* Athletes expect a consistent, prominent 1-tap action to reset filters across all list screens without hunting for small chip `×` touch targets.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA**: A legacy plural heuristic (`activeFilterCount > 1`) assumed "Clear all" was redundant for single filters.
* **Independent Peer Review & Verification (ATT-3534)**:
  * Inspected `ActiveKnownLocationsFilterChipsRow.kt:84-95`: Renders `item("clear_all")` unconditionally.
  * Verified the 5 diverging files:
    1. `ActiveFilterChipsRow.kt:298`: `if (criteria.activeFilterCount > 1)`
    2. `ActiveSegmentFilterChipsRow.kt:126`: `if (criteria.activeFilterCount > 1)`
    3. `ActiveRouteFilterChipsRow.kt:131`: `if (criteria.activeFilterCount > 1)`
    4. `ActiveClusterFilterChipsRow.kt:146`: `if (criteria.activeFilterCount >= 2)`
    5. `ActiveDeviceFilterChipsRow.kt:103`: `if (criteria.activeFilterCount > 1)`
  * All 6 composables already enforce `if (criteria.isEmpty) return` at the top of the function. Removing the inner `activeFilterCount > 1` / `>= 2` if-guard guarantees that "Clear all" is shown whenever any filter is active, with zero empty-state leakage.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope**:
  * Remove `activeFilterCount > 1` (or `>= 2`) guards in `ActiveFilterChipsRow.kt`, `ActiveSegmentFilterChipsRow.kt`, `ActiveRouteFilterChipsRow.kt`, `ActiveClusterFilterChipsRow.kt`, and `ActiveDeviceFilterChipsRow.kt`.
  * Create `ActiveFilterChipsRowClearAllContractTest.kt` asserting that all 6 implementations render `filter_clear_all` whenever `!criteria.isEmpty`.
* **Out-of-Scope**: Altering filter criteria data classes, bottom sheets, `matches()` logic, or list ViewModels.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: Net-new `REQ-UI-468` (*Uniform 'Clear all' Action Across Active Filter Chips Rows*), refining `REQ-UI-132`, `REQ-UI-351`, `REQ-UI-431`, `REQ-UI-466`, and `REQ-UI-467`.
* **Historical Origin & Commit Trace**: `ATT-1402`, `ATT-3166`, `ATT-5822`, `ATT-5823`.
* **Root Reason for Existing Formulation**: Assumed clearing a single filter should only be done via the individual chip's remove button.
* **Preservation of Core Invariants**: `criteria.isEmpty` remains the sole row visibility gate; all `onRemove*` and `onClearAll` callbacks remain intact; 100% 9-language localization parity preserved.

---

## 5. Architectural Strategy & High-Level Solution
* Remove the `if (criteria.activeFilterCount > 1)` and `if (criteria.activeFilterCount >= 2)` conditional wrappers in the 5 affected files, promoting `item("clearAll")` / `item(key = "clear_all")` to an unconditional item in `LazyRow`.
* Establish an automated structural contract test verifying that none of the 6 `Active*FilterChipsRow` source files contain `activeFilterCount > 1` or `activeFilterCount >= 2`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Zero filter chips row displayed when `criteria.isEmpty`; zero regression in individual chip deletion; 100% test pass rate.
* **Risk Rating**: **LOW** — Declarative UI conditional simplification across 5 leaf Composable rows with zero side effects on data models or databases.
