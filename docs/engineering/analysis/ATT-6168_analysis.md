# Stage 1 Analysis: ATT-6168 - Filter-Chips & Cluster-/Routen-Filter-Sheets: Imperiale Einheiten (mi/ft) & konsistente Rundung

**Ticket**: [ATT-6168](https://rainerblind.atlassian.net/browse/ATT-6168)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-6033](https://rainerblind.atlassian.net/browse/ATT-6033) (*Code Architecture, Deduplication & Clean Code Refactoring (OOP & FP)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.1`  
**Branch**: `feature/ATT-6168`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-10  

---

## 1. Problem Statement & Motivation
In `aTrainingTracker`, athletes can configure Metric (`km`/`m`) or Imperial (`mi`/`ft`) units (`TrainingApplication.getUnit() == MyUnits.IMPERIAL`). While `WorkoutFilterBottomSheet.kt` partially supports imperial inputs, filter chips and other filter sheets ignore imperial units:
1. **Hardcoded Metric Filter Chips**: `ActiveFilterChipsRow.kt`, `ActiveClusterFilterChipsRow.kt`, and `ActiveRouteFilterChipsRow.kt` unconditionally divide distance meters by `1000.0` and append `"km"`, and cast elevation meters to `toInt()` appending `"m"`. When imperial is active, chips display inaccurate metric labels (e.g., 10 mi displays as `"16.09344 km"` instead of `"10 mi"`).
2. **Missing Imperial Support in Cluster & Route Sheets**: `ClusterFilterBottomSheet.kt` and `RouteFilterBottomSheet.kt` hardcode metric presets (`< 10 km`, `10 - 25 km`, `< 100 m`) and hardcode metric text input labels (`(km)`, `(m)`).
3. **Floating-Point String Formatting Artifacts**: Formatting unrounded division results via `.toString()` renders floating-point precision artifacts (e.g. `"16.09344"`).
4. **Missing Imperial Localization Resources**: `strings.xml` and `strings_filters.xml` lack imperial filter format strings (`mi` and `ft`) across supported locales.

---

## 2. Root Cause Analysis & Independent Peer Review
* **Root Cause**:
  - `ActiveFilterChipsRow.kt:266-306`, `ActiveClusterFilterChipsRow.kt:104-127`, `ActiveRouteFilterChipsRow.kt:99-122`: Unconditional `/ 1000.0` and hardcoded references to `R.string.filter_*_distance_format` and `R.string.filter_*_elevation_format`.
  - `ClusterFilterBottomSheet.kt:87-99,209-265,280-305` and `RouteFilterBottomSheet.kt:75-88,200-260`: Hardcoded metric presets and multipliers without checking `TrainingApplication.getUnit()`.
  - Duplication of unit extraction logic (`WorkoutFilterBottomSheet.kt:169-171`) instead of a shared architectural utility in `ui/common/filters/`.
* **Independent Peer Review & Verification (4-Augen-Prinzip — ATT-3534)**:
  - Validated that `WorkoutFilterCriteria`, `ClusterFilterCriteria`, and `RouteFilterCriteria` store all distances and elevation gain in meters. The defect is strictly localized to presentation formatting and sheet presets/input conversions.
  - Verified 9 supported locales: `values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`. Net-new strings will be placed in `strings_filters.xml` across all 9 locales per Rule 9 (`REQ-PRO-041.3`).

---

## 3. Scope Grounding & Non-Goals (ATT-1250)
* **In-Scope Goals (2 SP)**:
  - Extract reusable `FilterUnitHelper` / formatting utilities into `ui/common/filters/`.
  - Imperial unit display (`mi`/`ft`) and consistent rounding (max 1 decimal for distance, no trailing `.0` for integers; integer for elevation) in all 3 active chip rows.
  - Imperial presets and unit-aware inputs in `ClusterFilterBottomSheet` and `RouteFilterBottomSheet`.
  - 9-language translation parity in `strings_filters.xml`.
  - Comprehensive unit tests in `FilterFormattingUtilsTest.kt`.
* **Out-of-Scope Non-Goals**:
  - `ATT-6043`: Full composite filter sheet deduplication / section component refactoring (blocked by this ticket).
  - Segment filter climb categories (dimensionless categorical rankings).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Requirement Shard**: Living doc shard `docs/requirements/req_ui_design_system.md` and `docs/tests/tst_ui_design_system.md`.
* **Proposed Requirements**: Introduces `REQ-UI-508` (*Imperial Unit Parity & Consistent Rounding for Filter Chips & Sheets*) and `TST-UI-508`, refining `REQ-UI-157` (*Workout Filter*), `REQ-UI-187` (*Cluster Filter*), and `REQ-UI-425` (*Elevation Gain Filter*).
* **Historical Origin**: Sprint 2026-40.4 (`ATT-1276`, commit `69e4f1a2`), where filter chips were initially introduced with metric-only strings.
* **Root Reason for Existing Formulation**: Metric was the original baseline; imperial was added incrementally to workout bottom sheets but omitted from chips and secondary sheets.
* **Preservation of Core Invariants**: Underlying filter criteria domain models store raw SI meters (`minDistanceMeters`, `minElevationGainMeters`); database queries, ViewModel state, and filter evaluation contracts remain 100% unchanged.

---

## 5. Architectural Strategy & High-Level Solution
* Create `com.atrainingtracker.trainingtracker.ui.common.filters.FilterFormattingUtils`:
  - `rememberIsImperialUnits(): Boolean` (reads `TrainingApplication.getUnit() == MyUnits.IMPERIAL`).
  - `formatFilterDistanceChip(...)` & `formatFilterElevationChip(...)`: format intervals, min, and max using `mi`/`km` and `ft`/`m`.
  - `formatDistanceInputValue(meters, multiplier)`: rounds to at most 1 decimal place, omitting `.0` for integers (e.g. `10`, `12.5`).
  - `formatElevationInputValue(meters, multiplier)`: rounds to nearest integer.
  - Standardized distance and elevation preset suppliers for both metric and imperial systems.
* Integrate into `ActiveFilterChipsRow`, `ActiveClusterFilterChipsRow`, `ActiveRouteFilterChipsRow`, `ClusterFilterBottomSheet`, `RouteFilterBottomSheet`, and `WorkoutFilterBottomSheet`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Raw SI meter storage in filter criteria; 100% 9-language translation parity; zero regression on existing metric filter behavior.
* **Risk Rating**: **LOW** (Encapsulated in UI formatting and composable presets; no database schema or domain layer alterations).
