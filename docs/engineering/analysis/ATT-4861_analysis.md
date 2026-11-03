# Stage 1 Analysis: ATT-4861 - User-Configurable Horizontal Width Ratios in Cockpit Sensor Fields

**Ticket**: [ATT-4861](https://rainerblind.atlassian.net/browse/ATT-4861)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Branch**: `feature/ATT-4861`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-01  

---

## 1. Problem Statement & Motivation
In `SensorGridScreen.kt`, multi-column sensor fields are hardcoded to `Box(modifier = Modifier.weight(1f))` (lines 740 and 1058), forcing equal horizontal width (`1/2 : 1/2` for 2 fields, `1/3 : 1/3 : 1/3` for 3 fields). Wide visual telemetry indicators (e.g. Power Zone Bars, W'bal battery, Pedal Balance gauges) require asymmetric horizontal layouts—such as a wide `3/4` Zone Bar paired with a compact `1/4` numeric value readout.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Root Cause**: `LayoutRowsTable` lacks horizontal weight persistence, and `SensorGridScreen` hardcodes uniform weight `1f`.
* **Independent Peer Review & Verification**:
  * Verified `SensorGridScreen.kt` (lines 740, 1058): hardcoded `Modifier.weight(1f)` in both configuration and tracking modes.
  * Verified `TrackingViewsDatabaseManager.java`: `ROWS_TABLE` is at schema `DB_VERSION = 16`. Adding `ColWeight real DEFAULT 1.0` requires bumping to `v17`.
  * Verified `DatabaseSchemaRegistry.kt`: maps `TrackingViews.db` and `FlexibleGenericView.db` to 16; requires updating to 17 (Rule 62).
  * Blind spot audit: Jetpack Compose `Modifier.weight` crashes with `IllegalArgumentException` if weight $\le 0f$. The runtime must guard `effectiveWeight = if (field.colWeight > 0f) field.colWeight else 1.0f`.
  * Upgrade path: Existing databases upgraded from v16 to v17 must backfill `ColWeight = 1.0` to preserve existing equal `1:1` layout behavior (Rule 66).

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * SQLite migration `v16 -> v17` adding `ColWeight real DEFAULT 1.0` to `LayoutRowsTable` with `DatabaseSchemaRegistry` parity.
  * Pass `colWeight` through `SensorFieldConfig`, `TrackingViewsRepository`, `TrackingViewModel`, and `SensorFieldState`.
  * Support per-field relative weight (1x, 2x, 3x) in `EditSensorFieldDialog` and quick 2-field row split presets (`3/4 : 1/4`, `2/3 : 1/3`, `1/2 : 1/2`, `1/3 : 2/3`, `1/4 : 3/4`).
  * Render `Modifier.weight(effectiveWeight)` in `SensorGridContent` and `SensorGridScreen` configuration mode.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Equalizing vertical row heights (bounded to ATT-4878).
  * Sport-dependent bar gating or v16 gauge migration corrections (bounded to ATT-4927).
  * Continuous drag-to-resize divider touch handles or freeform floating layouts.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-UI-437` extending `REQ-UI-200` (Grid Normalization & Editing) and `REQ-TRK-046` (Per-Field Element Display).
* **Historical Origin & Commit Trace**: Commit `a6920388` introduced Compose sensor grid with uniform `Modifier.weight(1f)`.
* **Root Reason for Existing Formulation**: Baseline equal-width distribution was standard for uniform numeric tiles before graphic zone bars and gauges were added.
* **Preservation of Core Invariants**: Defaulting `ColWeight = 1.0` guarantees existing 1:1 and 1:1:1 layouts remain visually identical.

---

## 5. Architectural Strategy & High-Level Solution
* **Database & Migration (`TrackingViewsDatabaseManager.java`)**:
  * Bump `DB_VERSION` to 17. Update `CREATE_LAYOUTS_TABLE_V17` with `ColWeight real DEFAULT 1.0`.
  * `onUpgrade`: add `ColWeight` column and backfill `UPDATE LayoutRowsTable SET ColWeight = 1.0 WHERE ColWeight IS NULL;`.
  * Add `updateColWeight(sensorFieldId, colWeight)` and `updateRowSplit(tabViewId, rowNr, leftWeight, rightWeight)`.
* **Schema Registry & Repo (`DatabaseSchemaRegistry.kt`, `TrackingViewsRepository.kt`)**:
  * Register version 17 for `TrackingViews.db` and `FlexibleGenericView.db`.
  * Add `colWeight: Float = 1.0f` to `SensorFieldConfig` and map symmetrically in cursors (Rule 20).
* **UI & Configuration (`EditSensorFieldDialog.kt`, `SensorGridScreen.kt`)**:
  * Expose relative weight (1x, 2x, 3x) and quick row split presets (`3/4 : 1/4`, `2/3 : 1/3`, `1/2 : 1/2`, `1/3 : 2/3`, `1/4 : 3/4`) when editing a field sharing a row with 1 other field.
  * Apply `Modifier.weight(effectiveWeight)` in `SensorGridContent` and `SensorGridScreen` config mode.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Default `ColWeight = 1.0` preserves 100% backward compatibility for existing layouts.
  2. Guard `effectiveWeight = if (colWeight > 0f) colWeight else 1.0f` to prevent Compose layout crashes.
  3. Single-field rows naturally consume 100% row width via Compose `RowScope.weight`.
* **Risk Rating**: **LOW** — Additive SQLite column with DEFAULT constraint, clean Compose weight propagation, zero impact on background sensor services.
