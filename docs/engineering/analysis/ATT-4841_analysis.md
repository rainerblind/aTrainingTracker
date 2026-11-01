# Stage 1: Problem Domain & Root Cause Analysis - ATT-4841: [Feature] Flag within sensor field config for showing hr zone, power zone bar, balance, ...

**Ticket**: [ATT-4841](https://rainerblind.atlassian.net/browse/ATT-4841)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Active Sprint**: `2026-44.1`  
**Author**: AI Agent 1 (Software Architect)  
**Date**: 2026-11-01  

---

## 1. Problem Domain & Forensic Root Cause

Currently, sensor fields in the tracking cockpit unconditionally render the numeric Value + Unit row. Horizontal visualization bars are either governed by a global profile setting (`ZoneDisplayOptions.showGauge` in `ZonesSettingsActivity` for `HR` and `POWER`) or hardcoded to render whenever data is non-null (`PEDAL_POWER_BALANCE` and `W_PRIME_BAL`). Athletes cannot configure on a per-sensor-field basis whether to show the numeric Value, the Bar, or both (e.g. showing a gauge bar for instantaneous 3s power while showing only the numeric value for 30s average power, or configuring a bar-only tile).

---

## 2. Independent Peer Review & Verification (4-Augen-Prinzip)

1. **Per-Field Schema & Model**:
   - `ROWS_TABLE` in `TrackingViewsDbHelper`: Upgrade `DB_VERSION` from 15 to 16, adding non-destructive columns `ShowValue INT DEFAULT 1` and `ShowBar INT DEFAULT 0`.
   - Backfill: Set `ShowValue = 1` across all rows; for bar-supported sensor types (`HR`, `POWER`, `PEDAL_POWER_BALANCE`, `W_PRIME_BAL`), default `ShowBar = 1`; for all other sensor types, default `ShowBar = 0`.
   - Update `SensorFieldConfig`, `SensorFieldState`, and `TrackingViewsRepository` (`fetchSensorFieldConfigs`, `fetchSingleSensorFieldConfig`, `updateSensorFieldConfig`, `insertSensorFieldConfig`) with `showValue: Boolean` and `showBar: Boolean`.
2. **Edit Dialog Configuration (`EditSensorFieldDialog` & `EditSensorFieldViewModel`)**:
   - For bar-supported sensor types, expose toggles/chips for *Value* (`showValue`) and *Bar* (`showBar`).
   - Constraint: At least one option must always remain active (cannot deselect both).
3. **Cockpit Rendering (`SensorFieldView.kt`, `TrackingViewModel.kt`)**:
   - Render Value + Unit row only if `fieldState.showValue == true`.
   - Render horizontal bar (`ZoneGaugeBar`, `PedalPowerBalanceBar`, `WPrimeBatteryGaugeBar`) only if `fieldState.showBar == true`.
4. **Deprecate Global Zone Gauge Setting**:
   - Remove global `showGauge` toggle row from `ZonesSettingsActivity.kt` so per-field `showBar` is the sole source of truth.

---

## 3. Chesterton's Fence Archaeology (`REQ-PRO-022`)

1. *Original Requirement & Target*: Extends `REQ-UI-376` (Zone Gauge Bar), `REQ-UI-377` (Pedal Balance Bar), and `REQ-UI-416` (W' Battery Gauge) into `REQ-TRK-046` (Per-Field Element Display Configuration).
2. *Historical Origin & Commit Trace*: `ATT-3574` (introduced `showGauge` in `ZoneDisplayOptions`), `ATT-4097` (added W' battery bar), `ATT-3660` (pedal balance indicator).
3. *Root Reason for Existing Formulation*: Originally, zone gauge display was treated as a sport profile property. As cockpit customization matured, users required granular control per sensor card.
4. *Preservation of Core Invariants*: Existing tab layouts, zone cutoff color calculations, and sensor streaming pipelines are 100% preserved.

---

## 4. Scope Grounding & Call Sites

- **In-Scope**:
  - `TrackingViewsDatabaseManager.java`: DB schema v16 migration, queries, updates.
  - `TrackingViewsRepository.kt`: Field config persistence & model mapping.
  - `SensorFieldState.kt`: `showValue` & `showBar` properties.
  - `EditSensorFieldViewModel.kt` & `EditSensorFieldDialog.kt`: UI chips & mutual exclusion constraint.
  - `SensorFieldView.kt`: Conditional value/bar rendering.
  - `ZonesSettingsActivity.kt`: Remove redundant global toggle.
  - 9-language strings and unit tests.
- **Out-of-Scope**: Adding graph/charting history inside individual sensor tiles.

---

## 5. Invariants & Risk Assessment

- **Risk Level**: **MEDIUM** (SQLite database schema upgrade v15 -> v16).
- **Invariants**: 100% non-destructive SQLite migration with PRAGMA idempotence and default backfills; 100% clean-room test pass rate.
