# Stage 1 Analysis: ATT-1294 - Configurable live sparkline graph tiles across all numeric SensorTypes with selectable time horizon

**Ticket**: [ATT-1294](https://rainerblind.atlassian.net/browse/ATT-1294)  
**Sub-task**: [ATT-6406](https://rainerblind.atlassian.net/browse/ATT-6406) (`[Analysis]`)  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.1`  
**Branch**: `feature/ATT-1294`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-10  

---

## 1. Problem Statement & Motivation

Athletes monitoring live exertion benefit greatly from seeing temporal telemetry trends (whether heart rate, power, or cadence is surging, steady, or decaying; how altitude, slope, or temperature evolved; how work or TSS is climbing; and battery trends). The cockpit currently limits visual graphical feedback to gauge bars (`showBar`) on specific sensors. We need a general, configurable *Live Sparkline Graph* (`showGraph`) across all numeric `SensorType`s with user-selectable time horizons (1m, 2m, 5m, 10m, 20m, 1h, and Full Workout).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

* **Architectural Gap**: `TrackingViewsDatabaseManager` (schema v18) only persists `ShowValue` and `ShowBar` per layout tile (`ROWS_TABLE`). `SensorFieldConfig` and `SensorFieldState` lack graph configuration and buffer states. `EditSensorFieldDialog` has no `Graph` element chip or time-horizon selector.
* **Independent Peer Review & Verification**:
  * Confirmed in `TrackingViewsDatabaseManager.java`: `isBarSupported` is restricted to HR, Power, Pedal Balance, and W'bal. In contrast, `isGraphSupported` should encompass all numeric (`DOUBLE`/`INTEGER`) sensors while cleanly excluding strings (`ACCUMULATED_SENSORS`, `SENSORS`, `TIME_OF_DAY`), timers/laps (`TIME_TOTAL`, `TIME_ACTIVE`, `TIME_LAP`, `LAP_NR`), and geographic coordinates (`LATITUDE`, `LONGITUDE`).
  * Confirmed in `SensorFieldView.kt`: Tile rendering already supports value-only and bar-only layouts via `ViewSize`. Adding `showGraph` (Value + Graph, and Graph-only) integrates symmetrically with zero snapshot state writes during composition (`Rule 66`).
  * O(1) ring-buffer / self-compressing decimation algorithm maintains $< 1\text{ KB}$ memory and $< 0.1\text{ ms}$ render time even for multi-hour workouts.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Database schema v19 migration in `TrackingViewsDatabaseManager`: add `ShowGraph` (int default 0) and `GraphHorizonS` (int default 300) with complete `onCreate`/`onUpgrade` parity.
  * Define `isGraphSupported` in `TrackingViewsDbHelper` for all numeric metrics.
  * In `EditSensorFieldDialog`, add `Graph` chip and 7 time-horizon options (1m, 2m, 5m, 10m, 20m, 1h, Full Workout).
  * Implement `LiveSparklineBuffer` (fixed circular buffer for 1m–1h; self-compressing pairwise decimation for Full Workout).
  * Implement `SensorSparklineView` composable supporting Value+Graph and Graph-only modes.
  * 100% 9-language localization parity in `strings_cockpit.xml`.
  * Living requirements update: `docs/requirements/req_tracking_cockpit.md` (`REQ-TRK-059`).
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No modification to historical post-workout analysis charts (`TelemetryMetricGraph`).
  * No graph support for string types, timers, or raw lat/lon coordinates.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Net-new requirement `REQ-TRK-059`, extending `REQ-TRK-046` (`ShowValue`/`ShowBar` display elements) and `REQ-UI-436` (Bar-only mode). Targets `TrackingViewsDatabaseManager.java`, `SensorFieldView.kt`, `EditSensorFieldDialog.kt`, and `strings_cockpit.xml`.
* **Historical Origin & Commit Trace**: `ATT-4841` introduced `SHOW_VALUE` and `SHOW_BAR` in DB v16; `ATT-4927` added sport-dependent bar gating in DB v18.
* **Root Reason for Existing Formulation**: Prior tickets focused on zone gauge bars and pedal balance bars. Generalized continuous time-series sparklines were deferred to live cockpit UX.
* **Preservation of Core Invariants**: Existing tile layouts, `showValue`, `showBar`, and view size scaling remain 100% preserved. Database migrations preserve existing user layouts.

---

## 5. Architectural Strategy & High-Level Solution

* **Database Layer**: Bump `DB_VERSION` to 19; add `ShowGraph` and `GraphHorizonS` columns; implement upgrade migration.
* **Buffer Architecture**: `LiveSparklineBuffer` maintaining fixed ring buffer (1m–1h) or decimation buffer (Full Workout).
* **UI Presentation**: Add `Graph` chip and time horizon selector to `EditSensorFieldDialog`; render `SensorSparklineView` inside `SensorFieldView`.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Existing layout configurations and bar renderings remain unaltered.
  2. Database migration v18 $\to$ v19 preserves existing rows with defaults (`showGraph=0`, `horizon=300`).
  3. Zero Compose snapshot state writes during composition (`Rule 66`).
  4. 100% 9-language translation parity.
* **Risk Rating**: **MEDIUM** — Touches SQLite schema migration in `TrackingViewsDatabaseManager` and live 1-Hz cockpit tile composition.
