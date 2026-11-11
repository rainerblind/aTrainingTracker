# Stage 2: Requirement & Test Specification - ATT-6726: 2-Level Sensor Hierarchy & ActivityType Mapping

**Ticket**: [ATT-6726](https://rainerblind.atlassian.net/browse/ATT-6726) | **Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-46.2` | **Branch**: `feature/ATT-6726`  
**Requirement Mapping**: `REQ-TRK-070` | **Test Spec ID**: `TST-TRK-070`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-11  

---

## 1. Requirement Specification (REQ-TRK-070)

### 1.1 Problem Statement & Rationale
With telemetry expanding from 43 to 63 constants (50 cockpit-selectable), the flat 7-category picker in `EditSensorFieldDialog.kt` (`REQ-UI-395`) suffers from severe cognitive overload: `POWER_AND_PEDALING` contains 22 flat items; `SYSTEM_ENVIRONMENT_AND_DIAGNOSTICS` contains 16 mixed items; HRV metrics are split across Heart Rate and System Diagnostics; `CADENCE` sits isolated under running; 10 telemetry metrics (8 power duration, 2 radar v2) are omitted from `ActivityType.getSensorTypeArray()`; `CALORIES` is commented out; and capability badges (`Bar`, `Graph`) are absent.

### 1.2 Functional & Architectural Requirements
* **REQ-TRK-070.1** (*6-Category Taxonomy*): `SensorCategory` SHALL define 6 clean top-level categories (`POWER_AND_PEDALING`, `HEART_RATE_AND_ENERGY`, `SPEED_PACE_AND_DISTANCE`, `TIME_AND_LAPS`, `ELEVATION`, `SYSTEM_ENVIRONMENT_AND_DIAGNOSTICS`), merging cadence into speed/pace/distance and unifying all 5 HRV metrics under heart rate.
* **REQ-TRK-070.2** (*9-SubCategory Hierarchy*): `SensorSubCategory` SHALL define 9 sub-groups (`POWER_LOAD`, `POWER_ROLLING`, `POWER_PEAK`, `PEDALING_DYNAMICS`, `HRV_AND_DRIFT`, `LAP_METRICS`, `RADAR_SAFETY`, `AMBIENT_TEMPERATURE`, `PHONE_BATTERY_GROUP`). `SensorType` SHALL expose `@Nullable SensorSubCategory getSubCategory()`, returning `null` for direct core metrics.
* **REQ-TRK-070.3** (*ActivityType Telemetry Wiring*): `ActivityType.getSensorTypeArray()` SHALL wire 8 Power Duration metrics (`POWER_5S..20M`, `POWER_PEAK_5S..20M`) into `BIKE_POWER`, wire 2 Radar V2 metrics (`RADAR_PASSING_DISTANCE_m`, `RADAR_LARGE_VEHICLE_COUNT`) into cycling profiles, scope radar to cycling, uncomment `CALORIES`, and apply athletic priority ordering.
* **REQ-TRK-070.4** (*2-Level Accordion & Capability Badges*): `EditSensorFieldDialog.kt` SHALL render direct metrics at the top of category sections, group sub-categories into collapsible cards, auto-expand active selections, and display compact `Bar` and `Graph` badges via `TrackingViewsDbHelper`.
* **REQ-TRK-070.5** (*9-Language Localization Parity*): All category and sub-category title strings SHALL have 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

### 1.3 Acceptance Criteria (Given-When-Then) & Invariants
* *Given* `SensorCategory` and `SensorSubCategory`, *When* evaluated, *Then* 6 categories and 9 sub-categories exist and all 63 `SensorType` constants map correctly.
* *Given* `ActivityType.BIKE_POWER`, *When* `getSensorTypeArray()` is inspected, *Then* 8 power duration sensors and radar v2 metrics are present, and `CALORIES` is active.
* *Given* `EditSensorFieldDialog.kt`, *When* launched, *Then* selected item's category/sub-category are auto-expanded with `Bar`/`Graph` capability badges rendered.
* *Invariants*: All 63 `SensorType` enum names/ordinals preserved; zero DB schema modifications; Section 5.8 scroll safety preserved (no nested scroll containers); 100% test pass rate.

---

## 2. Test Specification (TST-TRK-070)

### Test Case 1: 2-Level Sensor Taxonomy & SensorType Mapping (`TST-TRK-070.1`)
* **Scope**: Unit Test (`SensorCategoryAndSelectorContractTest.kt`)
* **Assertion**: Verify 6 categories, 9 sub-categories, 63 sensor mappings (5 HRV metrics unified under `HEART_RATE_AND_ENERGY`, cadence in `SPEED_PACE_AND_DISTANCE`, direct metrics returning `null` sub-category).

### Test Case 2: ActivityType Sensor Telemetry Mapping & Athletic Priority (`TST-TRK-070.2`)
* **Scope**: Unit Test (`ActivityTypeTest.kt`)
* **Assertion**: Verify 8 power durations present in `BIKE_POWER`, radar scoped to cycling including v2 metrics, `CALORIES` present, and priority order puts instant telemetry first.

### Test Case 3: EditSensorFieldDialog 2-Level Grouping & Capability Badges (`TST-TRK-070.3`)
* **Scope**: Unit & Contract Test (`EditSensorFieldDialogTest.kt`)
* **Assertion**: Verify direct metrics rendered at category top, sub-categories partitioned, auto-expansion of active item, and capability badge evaluation matching `TrackingViewsDbHelper`.

### Test Case 4: 9-Language Localization & Format Parity (`TST-TRK-070.4`)
* **Scope**: Localization Parity Test (`SensorCategoryAndSelectorContractTest.kt`)
* **Assertion**: 100% parity across 9 locales for all category and sub-category title strings with zero missing keys or format token discrepancies.

### Test Case 5: Clean-Room Targeted Regression Suite Execution (`TST-TRK-070.5`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.sensor.*" --tests "com.atrainingtracker.banalservice.ActivityTypeTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield.*"`
* **Assertion**: 100% pass rate across all modified packages.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-070.1` | Unit | `SensorCategory.java`, `SensorSubCategory.java`, `SensorType.java` | `REQ-TRK-070.1`, `REQ-TRK-070.2` | Specified |
| `TST-TRK-070.2` | Unit | `ActivityType.java` | `REQ-TRK-070.3` | Specified |
| `TST-TRK-070.3` | Unit / UI | `EditSensorFieldDialog.kt` | `REQ-TRK-070.4` | Specified |
| `TST-TRK-070.4` | Localization | `values*/strings.xml` | `REQ-TRK-070.5` | Specified |
| `TST-TRK-070.5` | Regression | `banalservice.sensor.*`, `ActivityTypeTest`, `editsensorfield.*` | `REQ-PRO-001` | Specified |
