# Stage 3: Implementation Plan - ATT-6726: 2-Level Sensor Hierarchy & ActivityType Mapping

**Ticket**: [ATT-6726](https://rainerblind.atlassian.net/browse/ATT-6726)  
**Sub-task**: [ATT-6807](https://rainerblind.atlassian.net/browse/ATT-6807) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `Sprint 2026-46.2`  
**Requirement Mapping**: `REQ-TRK-070`  
**Test Mapping**: `TST-TRK-070`  
**Branch**: `feature/ATT-6726`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-11  

---

## 1. Problem Description & Background
The sensor picker in `EditSensorFieldDialog.kt` contains 63 `SensorType` constants (50 cockpit-selectable) partitioned into 7 flat categories (`REQ-UI-395`). This causes severe UX clutter: `POWER_AND_PEDALING` has 22 flat metrics, `SYSTEM_ENVIRONMENT` has 16 mixed items, HRV metrics are split across two categories, `CADENCE` is isolated under running, 10 telemetry metrics (8 Power Duration, 2 Radar V2) are missing from `ActivityType.getSensorTypeArray()`, `CALORIES` is commented out, and capability badges (`Bar`, `Graph`) are absent.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-TRK-070` (*2-Level Sensor Hierarchy, ActivityType Mapping & Capability Badges*)
  * `REQ-TRK-070.1`: 6 clean top-level `SensorCategory` groups.
  * `REQ-TRK-070.2`: 9 `SensorSubCategory` groups with `@Nullable getSubCategory()`.
  * `REQ-TRK-070.3`: `ActivityType` wiring (8 Power Duration, 2 Radar V2, `CALORIES`, priority sort).
  * `REQ-TRK-070.4`: `EditSensorFieldDialog` 2-level accordion and `Bar`/`Graph` capability badges.
  * `REQ-TRK-070.5`: 9-language localization parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.
* **Test Mapping**: `TST-TRK-070.1`–`TST-TRK-070.5` in `docs/engineering/test_specs/ATT-6726_test_spec.md`.

---

## 3. System Invariants & Preserved Behavior
1. **Sensor Identity & Database Invariant**: All 63 `SensorType` enum names and ordinals remain untouched. `ROWS_TABLE` schema in `TrackingViewsDatabaseManager` is unchanged.
2. **Scroll Safety (Section 5.8)**: No nested `verticalScroll` or `LazyColumn` inside `AppModalBottomSheet`.
3. **Filter Defaults Invariant**: Instantaneous fallback in `SensorFilterDefaults.kt` preserved.
4. **9-Language Parity**: 100% localization parity for all new and updated category strings.

---

## 4. Proposed Architectural Changes & UI Consistency
* **Domain Model (`com.atrainingtracker.banalservice.sensor`)**:
  * New `SensorSubCategory.java`: Enum defining 9 sub-categories with `@StringRes int titleResId`.
  * Updated `SensorCategory.java`: 6 groups (`POWER_AND_PEDALING`, `HEART_RATE_AND_ENERGY`, `SPEED_PACE_AND_DISTANCE`, `TIME_AND_LAPS`, `ELEVATION`, `SYSTEM_ENVIRONMENT_AND_DIAGNOSTICS`).
  * Updated `SensorType.java`: Add `@Nullable SensorSubCategory subCategory`; map direct metrics to `null` and specialized metrics to their sub-category; unify all 5 HRV metrics under Heart Rate.
* **Activity Wiring (`com.atrainingtracker.banalservice.ActivityType`)**:
  * Add 8 Power Duration metrics to `BIKE_POWER`; add 2 Radar V2 metrics and scope all 6 Radar metrics to cycling sports; uncomment `CALORIES`; sort by athletic priority.
* **UI Consistency (`EditSensorFieldDialog.kt`)**:
  * Reused components: `Surface`, `OutlinedTextField`, `Icon`, `Text`, `HorizontalDivider`.
  * Theme tokens: `RoundedCornerShape(12.dp)`, `MaterialTheme.colorScheme.surfaceContainerLow`, `secondaryContainer`, `onSurface`.
  * Capability badges: Compact `4.dp` rounded badge for `Bar` and `Graph` based on `TrackingViewsDbHelper.isBarSupported` and `isGraphSupported`. Direct core metrics displayed at category top; sub-accordions auto-expand for selected items.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: SubCategory Enum & 9-Locale Strings (`REQ-TRK-070.2`, `REQ-TRK-070.5`)
* Files: `SensorSubCategory.java`, `app/src/main/res/values*/strings_cockpit.xml`.
* Changes: Create `SensorSubCategory` enum; add string keys `sensor_subcategory_*` and update category titles across 9 locales.

### Step 2: 2-Level Sensor Taxonomy Mapping (`REQ-TRK-070.1`, `REQ-TRK-070.2`)
* Files: `SensorCategory.java`, `SensorType.java`.
* Changes: Consolidate to 6 categories; add `subCategory` field and getter; unify HRV; map cadence/strides to Speed/Pace/Distance.

### Step 3: ActivityType Telemetry Wiring & Ordering (`REQ-TRK-070.3`)
* Files: `ActivityType.java`.
* Changes: Append 8 Power Duration metrics in `BIKE_POWER`; wire Radar V2 and scope to cycling; uncomment `CALORIES`; sort metrics by athletic priority.

### Step 4: 2-Level UI Accordion & Capability Badges (`REQ-TRK-070.4`)
* Files: `EditSensorFieldDialog.kt`, `EditSensorFieldViewModel.kt`.
* Changes: Render direct metrics at category top; render collapsible sub-category sections; auto-expand selected item's category/sub-category; render `Bar` and `Graph` badges.

### Step 5: Unit, Contract & Localization Verification (`TST-TRK-070.1`–`TST-TRK-070.5`)
* Files: `SensorCategoryAndSelectorContractTest.kt`, `ActivityTypeTest.kt`, `CyclingPowerMetricsDeviceContractTest.kt`.
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.sensor.*" --tests "com.atrainingtracker.banalservice.ActivityTypeTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Step-by-step unit testing during construction; clean-room full suite regression in Stage 5.
* **Rollback**: Self-contained git branch isolation on `feature/ATT-6726`; revert via branch reset if needed.
