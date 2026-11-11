# Stage 1 Analysis: ATT-6726 - [Verbesserung] EditSensorFieldDialog & SensorType: Introduce 2-level sensor hierarchy (SensorSubCategory), unify HRV/Radar groups, and wire missing Power/Radar metrics

**Ticket**: [ATT-6726](https://rainerblind.atlassian.net/browse/ATT-6726)  
**Sub-task**: [ATT-6799](https://rainerblind.atlassian.net/browse/ATT-6799) (`[Analysis]`)  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `Sprint 2026-46.2`  
**Branch**: `feature/ATT-6726`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation
Since `ATT-3852` introduced the 7-category accordion in `EditSensorFieldDialog.kt`, metrics have grown to 63 constants (50 cockpit-selectable), creating critical UX deficiencies:
1. **Overloaded Flat Lists**: `POWER_AND_PEDALING` contains 22 flat metrics; `SYSTEM_ENVIRONMENT_AND_DIAGNOSTICS` contains 16 mixed metrics (Radar, HRV, Temperature, Battery).
2. **Fragmented HRV Metrics**: `HRV_DFA_A1` and `HRV_RMSSD` reside in Heart Rate, while `RR_INTERVAL` and `HRV_ARTIFACT_RATE` are isolated in System/Diagnostics.
3. **Stranded Cadence & Title Mismatch**: In cycling, `CADENCE` sits alone in `CADENCE_AND_RUNNING` with a running shoe icon; `ELEVATION_AND_NAVIGATION` mentions "Navigation" despite GPS coordinates/bearing never being selectable.
4. **Missing Telemetry in `ActivityType`**: 8 Power Duration metrics (`POWER_5S..20M`, `POWER_PEAK_5S..20M`) and 2 Radar V2 metrics (`RADAR_PASSING_DISTANCE_m`, `RADAR_LARGE_VEHICLE_COUNT`) are missing from `getSensorTypeArray()`; `CALORIES` is commented out across 3 profiles; non-priority ordering lists balance before power.
5. **No Visual Capability Badges**: Athletes cannot identify which metrics support Bar (`BAR`) or Sparkline (`GRAPH`) before adding them to the grid.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Architectural Gap**: `SensorCategory` is a 1-level flat enum. Metrics expanded from 43 to 63 without sub-grouping abstractions.
* **Call Sites Audited**:
  * `SensorCategory.java` & `SensorType.java`: 7 flat categories lack sub-level categorization; `RR_INTERVAL`/`HRV_ARTIFACT_RATE` assigned to wrong domain.
  * `ActivityType.java`: `getSensorTypeArray()` hardcoded arrays lack ATT-1298 power duration metrics and ATT-6687 radar V2 metrics; radar is unconditionally appended to run profiles instead of sport-scoped.
  * `EditSensorFieldDialog.kt`: Accordion only renders 1-level category headers; lacks sub-accordions and capability badges (`isBarSupported`, `isGraphSupported`).
* **Independent Peer Review & Verification**:
  * Confirmed: `CyclingPowerMetricsDevice.java` instantiates 14 sensors, but 8 power-duration sensors are completely omitted from `ActivityType.BIKE_POWER`.
  * Confirmed: `SensorFilterDefaults.kt` safely defaults power duration metrics to `INSTANTANEOUS` via fallback branch.
  * Confirmed: Persistence in `ROWS_TABLE` stores `sensorType.name()`, leaving schema untouched and fully backward-compatible.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Introduce `SensorSubCategory` enum (9 sub-groups) with 9-language localization parity.
  * Refine `SensorCategory` to 6 clean top-level categories (merge `CADENCE`/`STRIDES` into Speed/Pace/Distance; rename Elevation; unify all 5 HRV metrics under Heart Rate).
  * Update `SensorType` with `@Nullable SensorSubCategory getSubCategory()`.
  * Wire missing 8 Power Duration + 2 Radar V2 metrics into `ActivityType.getSensorTypeArray`, scope radar to cycling, uncomment `CALORIES`, and apply athletic priority ordering.
  * Update `EditSensorFieldDialog.kt` to render 2-level accordion (direct core metrics top + sub-accordions), auto-expand selected item's category/sub-category, and display compact `Bar`/`Graph` capability badges.
  * Add unit and contract tests verifying `CyclingPowerMetricsDevice` parity, 63-metric taxonomy, and 9-locale parity.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No changes to database schema or `SensorFieldConfig` persistence format.
  * No changes to underlying filter processing engines or BLE sensor decoders.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-395` (*Categorized & Searchable SensorType Selector in EditSensorFieldDialog*), targeting `SensorCategory.java`, `SensorType.java`, `ActivityType.java`, and `EditSensorFieldDialog.kt`.
* **Historical Origin & Commit Trace**: Added in `ATT-3852` (commit `14292b35206`), refined in `ATT-3542` and `ATT-5879`.
* **Root Reason for Existing Formulation**: 7 flat categories were designed for 43 metrics. Subsequent addition of 20 metrics overloaded Power and System categories.
* **Preservation of Core Invariants**: All 63 `SensorType` enum names and ordinals remain untouched. Bottom-sheet scroll safety (zero nested scrollable containers per Section 5.8) is strictly preserved.

---

## 5. Architectural Strategy & High-Level Solution
1. **Taxonomy (SWE.2)**:
   * `SensorCategory`: 6 groups (`POWER_AND_PEDALING`, `HEART_RATE_AND_ENERGY`, `SPEED_PACE_AND_DISTANCE`, `TIME_AND_LAPS`, `ELEVATION`, `SYSTEM_ENVIRONMENT_AND_DIAGNOSTICS`).
   * `SensorSubCategory`: `POWER_LOAD`, `POWER_ROLLING`, `POWER_PEAK`, `PEDALING_DYNAMICS`, `HRV_AND_DRIFT`, `LAP_METRICS`, `RADAR_SAFETY`, `AMBIENT_TEMPERATURE`, `PHONE_BATTERY_GROUP`.
   * Direct core metrics have `subCategory == null` (immediate 1-tap visibility).
2. **Activity Wiring**:
   * Wire `POWER_5S..20M` and `POWER_PEAK_5S..20M` into `BIKE_POWER`.
   * Scope radar metrics to `BSportType.BIKE`. Enable `CALORIES` across all profiles.
3. **UI & Capabilities**:
   * In `EditSensorFieldDialog.kt`, render direct metrics first, then sub-category expanders.
   * Add capability pills (`Bar`, `Graph`) leveraging existing `TrackingViewsDbHelper.isBarSupported` and `isGraphSupported`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero regression in existing 63 metrics, settings persistence, or database schemas.
  2. Strict preservation of Section 5.8 (no nested scrolling containers within bottom sheet).
  3. 9-language translation parity across all new string keys.
* **Risk Rating**: **LOW** (3 SP) — Self-contained UI presentation and enum taxonomy refactoring; telemetry pipeline, filters, and persistence remain completely unaltered.
