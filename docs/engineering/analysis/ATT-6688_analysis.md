# Stage 1 Analysis: ATT-6688 - Wire LiveMmpCurveChart into Cockpit Tracking Layouts with PR Reference Curve and Breakthrough Notifications

**Ticket**: [ATT-6688](https://rainerblind.atlassian.net/browse/ATT-6688)  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Branch**: `feature/ATT-6688` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation
* **Zero Production Call Sites**: In `ATT-1298`, the logarithmic-scale `LiveMmpCurveChart` composable was implemented, but it currently has zero production call sites in `SensorGridScreen.kt`, `TrackingViewModel.kt`, or Cockpit layout settings.
* **Missing Reference Benchmark & Breakthroughs**: Athletes currently lack visual comparison against historical all-time/season PR power-duration benchmarks (`referencePoints`), and real-time breakthroughs across benchmark durations (`5s`, `1m`, `5m`, `20m`) are not signaled.
* **Test Hygiene Debt (`Rule 5.4`)**: `LiveMmpCurveChartTest.kt` line 62 uses brittle `File.readLines()` source-text grepping rather than semantic Compose contract and state behavior assertions.

---

## 2. Root Cause Analysis & Architectural Audit
* **Layout Storage Gap**: `TrackingViewsDbHelper.java` (`VIEWS_TABLE`) stores spatial toggles (`SHOW_MAP`, `SHOW_ELEVATION_PROFILE`, `SHOW_LIVE_SEGMENTS`, `SHOW_LIVE_CLIMBS`, `SHOW_BIKE_BELL`) at DB version 19, lacking a `SHOW_MMP_CURVE` column.
* **Telemetry Streaming Flow**: `CyclingPowerMetricsDevice.java` computes and broadcasts 1 Hz rolling means (`POWER_5S`, `POWER_1M`, `POWER_5M`, `POWER_20M`) and monotonic session peaks (`POWER_PEAK_5S`, `POWER_PEAK_1M`, `POWER_PEAK_5M`, `POWER_PEAK_20M`). However, `TrackingViewModel.kt` does not bundle these into `LiveMmpCurveData` or expose them to `SensorGridScreen.kt`.
* **Historical Extrema Availability**: `WorkoutSummariesDatabaseManager.java` indexes `TABLE_EXTREMA_VALUES` via `idx_extrema_sensor_extrema`, allowing efficient querying of historical all-time maximums for power peak sensor types.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Cockpit Layout Integration: Add `SHOW_MMP_CURVE` to `VIEWS_TABLE` (DB version 19 -> 20 migration), expose `SpatialCockpitToggleCard` in `SensorGridScreen.kt`, and render `LiveMmpCurveChart` when enabled.
  2. Live Telemetry Streaming: Extract rolling averages, session peaks, and instantaneous power in `TrackingViewModel.kt` to update `LiveMmpCurveData` in UI state.
  3. Historical PR Reference Curve & Breakthroughs: Pass historical PR benchmarks (`referencePoints`) into `LiveMmpCurveChart.kt`, draw the dashed reference curve, and trigger non-intrusive breakthrough badges/pins when session peaks exceed benchmarks.
  4. Test Hygiene: Replace `File.readLines()` in `LiveMmpCurveChartTest.kt` with robust semantic unit and Compose contract tests.
* **Out-of-Scope Non-Goals**:
  1. Modifying `SlidingWindowPowerAccumulator.kt` ring-buffer decimation algorithms.
  2. Altering third-party cloud upload contracts or BLE GATT parsers.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-TRK-058` (*Live Mean Maximal Power Accumulators, Benchmark Durations & Cockpit MMP Curve Chart*), specifically `REQ-TRK-058.4` (Composable Vector Chart) and `REQ-TRK-058.6` (Cockpit Layout Integration & PR Overlays). Targets `LiveMmpCurveChart.kt`, `SensorGridScreen.kt`, `TrackingViewModel.kt`, `TrackingViewsDbHelper.java`, and `LiveMmpCurveChartTest.kt`.
* **Historical Origin & Commit Trace**: Introduced in `ATT-1298` (commit `5eee662f` / `ef243477`), extending `REQ-TRK-019` (W/kg), `REQ-TRK-020` (NP/IF), `REQ-TRK-021` (Work/TSS), and Cockpit layout schema migrations (`ATT-4841`, `ATT-4861`).
* **Root Reason for Existing Formulation**: `ATT-1298` delivered mathematical accumulator backends and isolated chart rendering, deferring end-to-end Cockpit tab toggles, historical PR integration, and notification banners to `ATT-5908`. Source-text grepping was a temporary placeholder for snapshot hygiene validation.
* **Preservation of Core Invariants**:
  1. Zero snapshot state writes during composition (`Rule 66`).
  2. 1-Hz sampling rate and non-blocking background telemetry (`REQ-TRK-007`).
  3. Pause lifecycle freezing and session reset parity (`REQ-TRK-058.5`, `Rule 51`).
  4. 100% 9-language translation parity in `strings_cockpit.xml`.

---

## 5. Architectural Strategy & High-Level Solution
* **Database & Repository**: Upgrade `TrackingViewsDbHelper.DB_VERSION` to 20 with `ALTER TABLE viewsTable ADD COLUMN ShowMmpCurve int DEFAULT 0`. Propagate `showMmpCurve` through `TrackingViewInfo` and `TrackingViewsRepository`.
* **Telemetry Flow**: In `TrackingViewModel.kt`, assemble `LiveMmpCurveData` from `allSensorData` on each tick. Query historical benchmark PRs from `TABLE_EXTREMA_VALUES` via `WorkoutSummariesDatabaseManager`.
* **Composable Presentation**: Enhance `LiveMmpCurveChart.kt` with `referencePoints` dashed rendering, breakthrough detection, and transient callout banner. Embed chart cleanly in `SensorGridScreen.kt`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Database migration must execute idempotently without data loss for existing layouts (`Rule 62`).
  2. Main-thread execution cost of chart redraws bounded to $< 2\text{ ms}$ per tick.
  3. Seamless backwards compatibility if power telemetry is unavailable (graceful empty chart fallback).
* **Risk Rating**: **MEDIUM** — Clean schema bump with isolated Cockpit composable wiring and zero background service disruption (3 SP).
