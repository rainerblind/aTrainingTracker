# Stage 1 Analysis: ATT-4927 - Sport-Dependent Bar Support, Empty Tile Fallback & Upgrade Parity

**Ticket**: [ATT-4927](https://rainerblind.atlassian.net/browse/ATT-4927)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Branch**: `feature/ATT-4927`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`)  
**Date**: 2026-11-01  

---

## 1. Problem Statement & Motivation
Sprint Review 2026-44.1 of [ATT-4841] (per-field Value/Bar flags) uncovered three related deficiencies in cockpit sensor fields:
1. **Sport-Independent Bar Configuration**: `TrackingViewsDbHelper.isBarSupportedSensorType()` evaluated only sensor type. Because zone models in `TrackingViewModel.getZoneType` exist only for `HR` (Run/Bike) and `POWER` (Bike), `EditSensorFieldDialog` offered Value/Bar switches in sports where no bar can render (e.g. HR in Generic/Other, POWER in Run).
2. **Empty Bar-Only Cockpit Tile**: If a field was configured bar-only (`showValue = false`) and no bar can be drawn (unsupported sport, or before sport detection / `UNKNOWN` activity), `SensorFieldView` rendered neither value nor bar, leaving the tile completely blank.
3. **Forced Upgrade Overwrite**: The `v15->v16` migration set `ShowBar = 1` for all HR/POWER fields, ignoring the athlete's previous global preference (`ZoneDisplayOptions.showGauge`, which defaulted to `false`). Users saw unwanted zone bars appear everywhere.

---

## 2. Root Cause Analysis & Independent Peer Review (4-Augen-Prinzip)
1. **Decoupled Support Predicates**: `EditSensorFieldViewModel` checked only `selectedSensorType` rather than `(sensorType, activityType.sportType)`. Likewise, `TrackingViewModel` did not restrict `PEDAL_POWER_BALANCE` or `W_PRIME_BAL` bars to `BSportType.BIKE`.
2. **Cockpit Value Gating**: `SensorFieldView.kt` conditioned the numeric value row strictly on `fieldState.showValue`. When `showValue = false` and no bar state was available (`zoneGaugeState == null`, etc.), both branches skipped rendering.
3. **Database Version Bump (v18)**:
   - `ATT-4861` earlier in this sprint already bumped `TrackingViewsDatabaseManager.DB_VERSION` from `16` to `17` (`ColWeight`).
   - To repair already-upgraded `v16` and `v17` installations per Ceremony 1 Human Clarification, `DB_VERSION` must be bumped to **`18`**.
   - The `v18` migration step will query rows joined with `VIEWS_TABLE` (`ActivityType` $\rightarrow$ `BSportType`):
     - `HR` / `POWER`: re-evaluate `ShowBar` against legacy `SettingsDataStoreJavaHelper.getZoneDisplayOptions(context, zoneType).showGauge` and sport validity.
     - `PEDAL_POWER_BALANCE` / `W_PRIME_BAL`: set `ShowBar = 1` only for `BSportType.BIKE`, else `0`.
     - Other sensors: set `ShowBar = 0`. If `ShowBar == 0`, enforce `ShowValue = 1`.
   - `DatabaseSchemaRegistry.kt` will reference `TrackingViewsDatabaseManager.TrackingViewsDbHelper.DB_VERSION` dynamically (Rule 62).

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope**:
  * Unified predicate `isBarSupported(sensorType, sportType)` in `TrackingViewsDatabaseManager` (HR $\rightarrow$ Run/Bike; Power/Balance/W'Bal $\rightarrow$ Bike).
  * Update `EditSensorFieldViewModel` and `EditSensorFieldDialog` to hide Value/Bar switches when unsupported and persist `showValue = true, showBar = false`.
  * Update `TrackingViewModel` to restrict `pedalBalanceState` and `wPrimeBatteryState` to `BSportType.BIKE`.
  * Empty tile fallback in `SensorFieldView`: render numeric value whenever `fieldState.showValue || !hasActiveBar`.
  * Bump `DB_VERSION` to `18` with `v18` migration repairing existing rows using legacy `showGauge` and sport rules.
  * Synchronize `DatabaseSchemaRegistry.kt` via constant reference.
  * Unit tests covering sport-bar rules, empty tile fallback, and `v18` migration.
* **Out-of-Scope Non-Goals**:
  * Adding zone models for `UNKNOWN` sport type or running power.
  * Visual redesign of zone bars, balance bars, or battery gauges.
  * Modifying SQLite table schema structure (columns already exist).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-TRK-046` (*Per-Field Element Display Configuration*), amending `REQ-UI-376`, `REQ-UI-377`, `REQ-UI-416`, and `REQ-MIG-041`. Targets `TrackingViewsDatabaseManager.java`, `EditSensorFieldViewModel.kt`, `SensorFieldView.kt`, `TrackingViewModel.kt`, and `DatabaseSchemaRegistry.kt`.
* **Historical Origin & Commit Trace**: Commit `050f1437` (`ATT-4841`) introduced `ShowValue`/`ShowBar` in `v16` but defaulted `ShowBar = 1` for all bar-capable sensors without checking sport type or prior settings.
* **Root Reason for Existing Formulation**: `ATT-4841` aimed to provide per-field flexibility, but assumed sensor type alone governed bar feasibility and overlooked that global `showGauge` defaulted to `false`.
* **Preservation of Core Invariants**: Mutual exclusion (cannot deselect both Value and Bar), True-Black AMOLED compliance, and layout stability invariants are 100% preserved.

---

## 5. Architectural Strategy & Technical Design
1. **Canonical Support Predicate**: In `TrackingViewsDatabaseManager`, define `isBarSupported(SensorType, BSportType)`:
   - `HR`: `sportType in [RUN, BIKE]`
   - `POWER`, `PEDAL_POWER_BALANCE`, `W_PRIME_BAL`: `sportType == BIKE`
   - All others: `false`.
2. **Dialog & State**: `EditSensorFieldUiState.isBarSupported` checks `isBarSupported(selectedSensorType, activityType.sportType)`. If false, Value/Bar switches hide, and `showValue = true, showBar = false` is saved.
3. **Empty Tile Fallback**: In `SensorFieldView`, `hasActiveBar = fieldState.showBar && (zoneGaugeState != null || pedalBalanceState != null || wPrimeBatteryState != null)`. Render numeric value when `fieldState.showValue || !hasActiveBar`.
4. **Database Migration v18**: Bump `DB_VERSION` to `18`. Join `ROWS_TABLE` with `VIEWS_TABLE` to resolve `ActivityType`. Carry over `showGauge` from `SettingsDataStoreJavaHelper.getZoneDisplayOptions(context, zoneType)` for valid zone fields, set `ShowBar = 1` for Bike Balance/W'Bal, and `0` for unsupported fields.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Cockpit tiles never render completely empty regardless of configuration or telemetry state.
  2. Previous global `showGauge = false` is respected across upgrade paths (`v15->v18`, `v16->v18`, `v17->v18`).
  3. `EditSensorFieldDialog` offers Value/Bar options only when a bar can physically render for the layout's sport.
* **Risk Rating**: **LOW** — Data backfill and UI condition fallback; no schema table alterations or network calls.
