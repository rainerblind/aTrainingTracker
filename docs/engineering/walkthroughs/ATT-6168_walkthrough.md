# Stage 5: Walkthrough & Verification - ATT-6168: Filter-Chips & Cluster-/Routen-Filter-Sheets: Imperiale Einheiten (mi/ft) & Rundung

**Ticket**: [ATT-6168](https://rainerblind.atlassian.net/browse/ATT-6168)  
**Parent Epic**: [ATT-6033](https://rainerblind.atlassian.net/browse/ATT-6033) (*Code Architecture, Deduplication & Clean Code Refactoring (OOP & FP)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6168`  
**Requirement Mapping**: `REQ-UI-508` (`REQ-UI-508.1`..`REQ-UI-508.5`) | **Test Spec ID**: `TST-UI-508` (`TST-UI-508.1`..`TST-UI-508.5`)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Executive Summary & Verification Scope
ATT-6168 resolves imperial unit ignoring, floating-point display artifacts, and duplicated filter formatting across all active filter chip rows and filter bottom sheets:
1. **Centralized Filter Formatting Utilities (`FilterFormattingUtils.kt`)**: Centralized `rememberIsImperialUnits()`, unit multipliers (`1609.344` for miles, `0.3048` for feet), unit abbreviations (`mi`/`km`, `ft`/`m`), distance rounding (max 1 decimal, stripping redundant `.0` for integers), integer elevation rounding, athletic presets (5 thresholds), and localized chip formatters (`REQ-UI-508.1`).
2. **Active Filter Chips Imperial Unit Parity**: Updated `ActiveFilterChipsRow.kt`, `ActiveClusterFilterChipsRow.kt`, and `ActiveRouteFilterChipsRow.kt` to format chips via `FilterFormattingUtils`, dynamically displaying `mi`/`ft` under Imperial mode and `km`/`m` under Metric mode without floating-point artifacts (`REQ-UI-508.2`).
3. **Cluster & Route Filter Sheets**: Modernized `ClusterFilterBottomSheet.kt` and `RouteFilterBottomSheet.kt` to consume unit-aware presets, dynamic labels `(mi)`/`(ft)`, and bidirectional SI meter conversions on filter application (`REQ-UI-508.3`).
4. **Workout Filter Sheet Consolidation**: Consolidated `WorkoutFilterBottomSheet.kt` presets and multiplier definitions to reuse `FilterFormattingUtils` (`REQ-UI-508.1`).
5. **9-Language Localization Parity**: Added 6 imperial filter interval strings (`filter_*_distance_format_mi`, `filter_*_elevation_format_ft`) across all 9 canonical locales in `strings_filters.xml` (`REQ-UI-508.4`).
6. **Domain Invariants**: Preserved raw SI meters in all filter criteria models (`WorkoutFilterCriteria`, `ClusterFilterCriteria`, `RouteFilterCriteria`). Zero database query or schema changes (`REQ-UI-508.5`).

---

## 2. Automated Test Execution & Scope (Rule 60)
* **Tier 1 Targeted Test Scope**:
  * Command: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --max-workers=2 --tests "com.atrainingtracker.trainingtracker.ui.common.filters.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.*" --tests "com.atrainingtracker.trainingtracker.ui.clusters.*" --tests "com.atrainingtracker.trainingtracker.ui.routes.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.ui.components.core.*"`
  * Scope: All common filter formatters, chip contracts, workout/cluster/route filter criteria and sheets, 9-language translation parity, theme token audit, and core components.
  * Result: **100% PASS** (0 failures, 0 regressions under `parallel_sprint_lock.py`).
* **Living Documentation Governance**:
  * `REQ-UI-508` in `docs/requirements/req_ui_design_system.md` and `TST-UI-508` in `docs/tests/tst_ui_design_system.md` updated to `[Verified]`.

---

## 3. Acceptance Criteria Verification Table

| Acceptance Criterion | Verification Method | Status | Evidence |
| :--- | :--- | :--- | :--- |
| **AC-1 (Active Chip Imperial Distance)**: 10 mi (16,093.44 m) displays "10 mi" without ".0" | Unit Test | **MET** | `FilterFormattingUtilsTest.kt:testFormatDistanceFilterString_metricAndImperial` & `ActiveFilterChipsRowImperialTest.kt` |
| **AC-2 (Active Chip Imperial Elevation)**: 1,000 ft (304.8 m) displays "1000 ft" instead of "304 m" | Unit Test | **MET** | `FilterFormattingUtilsTest.kt:testFormatElevationFilterString_metricAndImperial` & `ActiveFilterChipsRowImperialTest.kt` |
| **AC-3 (Cluster & Route Presets)**: Bottom sheets provide mile and feet presets and dynamic labels | Unit Test & Code Inspection | **MET** | `FilterFormattingUtilsTest.kt:testGetDistancePresets_providesAthleticThresholds`, `ClusterFilterBottomSheet.kt`, `RouteFilterBottomSheet.kt` |
| **AC-4 (Input Meter Conversion)**: 5.5 mi input converts to 8,851.392 m and formats back to "5.5" | Unit Test | **MET** | `FilterFormattingUtilsTest.kt:testInputToMeterConversionAndBack_preservesAccuracy` |
| **AC-5 (9-Language Parity)**: All 9 supported locales define imperial strings without mismatch | Unit Test | **MET** | `TranslationParityTest.kt` (100% PASS across all 9 canonical locales) |

---

## 4. Hardware / Physical Verification (Pixel 10)
* **Status**: Headless unit and contract test verification. `adb devices` reported no physical device connected (`List of devices attached` empty). Booting an Android emulator is strictly prohibited per Rule 79 (`ATT-5890`). Verified via unit test suite covering display conversions, precision thresholds, and Compose contract tests.
