# Stage 2: Requirement & Test Specification - ATT-6168: Filter-Chips & Cluster-/Routen-Filter-Sheets: Imperiale Einheiten (mi/ft) & konsistente Rundung

**Ticket**: [ATT-6168](https://rainerblind.atlassian.net/browse/ATT-6168) | **Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-6033](https://rainerblind.atlassian.net/browse/ATT-6033) (*Code Architecture, Deduplication & Clean Code Refactoring (OOP & FP)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6168`  
**Requirement Mapping**: `REQ-UI-508` (`REQ-UI-508.1`..`REQ-UI-508.5`) | **Test Spec ID**: `TST-UI-508` (`TST-UI-508.1`..`TST-UI-508.5`)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Requirement Specification (`REQ-UI-508`)

### 1.1 Problem Statement & Rationale
Filter chips across Workouts, Clusters, and Routes hardcode metric units (`km`, `m`) and unconditional `/ 1000.0` division, causing inaccurate labels (e.g. 10 mi displays as "16.09344 km") and floating-point precision artifacts when Imperial units are configured (`TrainingApplication.getUnit() == MyUnits.IMPERIAL`). In addition, `ClusterFilterBottomSheet` and `RouteFilterBottomSheet` hardcode metric presets and labels. The system SHALL centralize filter unit formatting into `FilterFormattingUtils.kt`, provide unit-aware chip formatting across all 3 active chip rows, and support imperial presets/inputs in Cluster and Route filter sheets with 9-language localization parity in `strings_filters.xml`.

### 1.2 Functional & Architectural Requirements
* **REQ-UI-508.1** (*Centralized Filter Formatting Utilities*): `FilterFormattingUtils.kt` SHALL provide `formatFilterDistanceChip` and `formatFilterElevationChip` for interval, min-only, and max-only chips. Distance SHALL round to at most 1 decimal (stripping trailing `.0` for integers); elevation SHALL round to nearest integer. Distance and elevation preset lists SHALL provide athletic thresholds for metric and imperial.
* **REQ-UI-508.2** (*Active Filter Chips Imperial Unit Parity*): In `ActiveFilterChipsRow.kt`, `ActiveClusterFilterChipsRow.kt`, and `ActiveRouteFilterChipsRow.kt`, distance and elevation chips SHALL dynamically display `mi`/`ft` under imperial units and `km`/`m` under metric units.
* **REQ-UI-508.3** (*Cluster & Route Sheet Imperial Presets and Inputs*): In `ClusterFilterBottomSheet.kt` and `RouteFilterBottomSheet.kt`, distance and elevation presets and input labels SHALL reflect the active unit system, converting entered imperial values to SI meters on save.
* **REQ-UI-508.4** (*Domain-Partitioned 9-Language Localization Parity*): Imperial filter strings SHALL be defined in `strings_filters.xml` across all 9 canonical locales (`values*`).
* **REQ-UI-508.5** (*Preservation of Domain Invariants*): Filter criteria data classes SHALL continue storing raw SI meters. Database queries and filter algorithms remain 100% unchanged.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (Active Chip Imperial Distance)**: *Given* Imperial units active and 10 miles (16,093.44 m) filtered, *When* active chip rows render, *Then* chip displays "10 mi" without floating-point artifacts.
* **AC-2 (Active Chip Imperial Elevation)**: *Given* Imperial units active and 1,000 feet (304.8 m) filtered, *When* active chip rows render, *Then* chip displays "1000 ft" instead of "304 m".
* **AC-3 (Cluster & Route Bottom Sheet Presets)**: *Given* Imperial units active, *When* opening `ClusterFilterBottomSheet` or `RouteFilterBottomSheet`, *Then* presets display mile and feet thresholds and text fields indicate `(mi)` and `(ft)`.
* **AC-4 (Input Meter Conversion)**: *Given* an athlete entering 5.5 miles in sheet, *When* applying filter, *Then* criteria stores ~8,851.39 meters in `minDistanceMeters`.
* **AC-5 (9-Language Parity)**: *Given* any of the 9 supported locales, *When* running `TranslationParityTest`, *Then* zero missing string resources or specifier mismatches exist.

---

## 2. Test Specification & Traceability Matrix (`TST-UI-508`)

| Req ID | Test Case | Target Component | Verification Scope |
| :--- | :--- | :--- | :--- |
| `REQ-UI-508.1` | `TST-UI-508.1` | `FilterFormattingUtilsTest.kt` | Formatting intervals, min, and max for distance and elevation in metric and imperial |
| `REQ-UI-508.2` | `TST-UI-508.2` | `ActiveFilterChipsRowTest.kt` | Chip display verification across workout, cluster, and route chips in imperial mode |
| `REQ-UI-508.3` | `TST-UI-508.3` | `FilterFormattingUtilsTest.kt` | Preset generation and two-way display-to-SI meter conversions |
| `REQ-UI-508.4` | `TST-UI-508.4` | `TranslationParityTest.kt` | 9-locale parity audit for `strings_filters.xml` |
| `REQ-UI-508.5` | `TST-UI-508.5` | Targeted Test Suite | Targeted tests: `ui.common.filters.*`, `ui.aftermath.workoutlist.*`, `ui.clusters.*`, `ui.routes.*` |
