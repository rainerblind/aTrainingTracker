# Stage 3 Implementation Plan: ATT-6168 - Filter-Chips & Cluster-/Routen-Filter-Sheets: Imperiale Einheiten (mi/ft) & Rundung

**Ticket**: [ATT-6168](https://rainerblind.atlassian.net/browse/ATT-6168)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-6033](https://rainerblind.atlassian.net/browse/ATT-6033) (*Code Architecture, Deduplication & Clean Code Refactoring (OOP & FP)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.1`  
**Requirement Mapping**: `REQ-UI-508` (`REQ-UI-508.1`..`REQ-UI-508.5`)  
**Test Spec Mapping**: `TST-UI-508` (`TST-UI-508.1`..`TST-UI-508.5`)  
**Branch**: `feature/ATT-6168`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-10  

---

## 1. Technical Architecture & SWE.2 Design
* **Decomposition & Architecture Boundaries**:
  - `ui/common/filters/FilterFormattingUtils.kt`:
    1. **Unit State**: `rememberIsImperialUnits(): Boolean` reading `TrainingApplication.getUnit() == MyUnits.IMPERIAL`.
    2. **Multipliers & Units**: `getDistanceMultiplier(isImperial)`, `getElevationMultiplier(isImperial)`, `getDistanceUnit(isImperial)`, `getElevationUnit(isImperial)`.
    3. **Consistent Rounding**: `formatDistanceInputValue(meters, multiplier)` (max 1 decimal, no trailing `.0` for integers) and `formatElevationInputValue(meters, multiplier)` (integer rounded).
    4. **Chip Formatter**: `formatFilterDistanceChip(minM, maxM, isImperial)` and `formatFilterElevationChip(minM, maxM, isImperial)` consuming unit-aware string resources.
    5. **Athletic Presets**: `getDistancePresets(isImperial)` and `getElevationPresets(isImperial)` providing athletic thresholds in SI meters.
  - **Component Integration**:
    - `ActiveFilterChipsRow.kt`, `ActiveClusterFilterChipsRow.kt`, `ActiveRouteFilterChipsRow.kt`: Delegate distance and elevation chip label formatting to `formatFilterDistanceChip` and `formatFilterElevationChip`.
    - `ClusterFilterBottomSheet.kt` & `RouteFilterBottomSheet.kt`: Consume `rememberIsImperialUnits()`, unit multipliers, presets, input formatters, and update criteria with meter conversion (`miles * 1609.344`, `feet * 0.3048`).
    - `WorkoutFilterBottomSheet.kt`: Consolidate duplicated local unit logic to use `FilterFormattingUtils`.
  - **Localization**: Add imperial strings (`filter_*_distance_format_mi`, `filter_*_elevation_format_ft`) to `strings_filters.xml` across all 9 locales.
* **Invariants**: Raw SI meter storage in criteria; 100% 9-language translation parity; zero metric regression; strict Code Lock until Gate 3 passes.

---

## 2. Atomic Implementation Steps

| Step | Component | Target File | Action & Invariants | Traceability |
| :--- | :--- | :--- | :--- | :--- |
| **1** | Common Filters | `FilterFormattingUtils.kt` | Create shared utility: `rememberIsImperialUnits`, multipliers, input rounders, presets, and chip formatters. | `REQ-UI-508.1`<br>`TST-UI-508.1` |
| **2** | Localization | `res/values*/strings_filters.xml` | Add imperial interval/min/max format strings across all 9 canonical locales. | `REQ-UI-508.4`<br>`TST-UI-508.4` |
| **3** | Active Chips | `Active*FilterChipsRow.kt` (3 files) | Replace hardcoded `/ 1000.0` and metric strings with `formatFilterDistanceChip` and `formatFilterElevationChip`. | `REQ-UI-508.2`<br>`TST-UI-508.2` |
| **4** | Filter Sheets | `ClusterFilterBottomSheet.kt` & `RouteFilterBottomSheet.kt` | Integrate imperial presets, dynamic unit labels `(mi)`/`(ft)`, and two-way meter conversion on apply. | `REQ-UI-508.3`<br>`TST-UI-508.3` |
| **5** | Clean Up | `WorkoutFilterBottomSheet.kt` | Consolidate preset/multiplier definitions to reuse `FilterFormattingUtils`. | `REQ-UI-508.1`<br>`TST-UI-508.5` |
| **6** | Unit Tests | `FilterFormattingUtilsTest.kt` | Author unit test suite verifying rounding, imperial/metric chip strings, presets, and meter conversions. | `REQ-UI-508.1-3`<br>`TST-UI-508.1,3` |
| **7** | Verification | Test Suite | Run targeted tests (`ui.common.filters.*`, `ui.aftermath.workoutlist.*`, `ui.clusters.*`, `ui.routes.*`, `TranslationParityTest`). | `REQ-PRO-001`<br>`TST-UI-508.5` |

---

## 3. UI Consistency & System Invariants (Rule 4)
* **Reference UI & Tokens**: Uses standard Material 3 tokens (`FilterChip`, `OutlinedTextField`), `MaterialTheme.colorScheme.primary`, and shared `FilterBottomSheetScaffold`.
* **Invariants**: Raw meter storage in criteria (`minDistanceMeters`, `minElevationGainMeters`); no DB changes; clean-room test pass rate.
* **Risk Rating**: **LOW** (Presentation layer and string resource enhancement; domain queries and models unmodified).
