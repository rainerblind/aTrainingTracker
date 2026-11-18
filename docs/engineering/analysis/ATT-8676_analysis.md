# Stage 1 Analysis: ATT-8676 - Radar Speed Units (MyHelper.getUnitsId & Aftermath)

**Ticket**: [ATT-8676](https://rainerblind.atlassian.net/browse/ATT-8676)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-18_01_Früh` | **Branch**: `feature/ATT-8676`  
**Author**: AI Agent 1 (Implementer, `--as agent1`) | **Date**: 2026-11-18

---

## 1. Problem Statement & Motivation
During on-device review alongside `ATT-8440`, radar speed units displayed **`m/s`** instead of **`km/h`** (Metric) / **`mile/h`** (Imperial). `SensorType.RADAR_APPROACH_SPEED_mps` formats values via `SpeedFormatter` (which converts `m/s` to `km/h` or `mile/h`), but `MyHelper.getUnitsId()` and `MyHelper.getShortUnitsId()` omit `RADAR_APPROACH_SPEED_mps` and fall back to `R.string.units_speed_basic` (`"m/s"`).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Peer Review & Verification**:
  * **Confirmed Root Cause (`MyHelper.java:150-169`)**: `getUnitsId(SensorType)` (`:152`) only maps `SPEED_mps -> getSpeedUnitNameId()` (`units_speed_metric` `"km/h"` / `units_speed_imperial` `"mile/h"`), and `getShortUnitsId(SensorType)` (`:162`) only maps `SPEED_mps -> getShortSpeedUnitNameId()` (`units_speed_short_metric` `"km/h"` / `units_speed_short_imperial` `"mi/h"`). `RADAR_APPROACH_SPEED_mps` (`SensorType.java:443`) binds `SpeedFormatter` (`SpeedFormatter.kt:27-45`) yet falls through to `default -> sensorType.getUnitId()` = `R.string.units_speed_basic` (`"m/s"`).
  * **Why `RadarTrafficSummaryCard.kt` Differed**: Fixed in `ATT-8440` (`e3c278bb`), `RadarTrafficSummaryCard.kt:59,133,140` calls `LocalMetricFormatter.current.speed.format_with_units(...)` (`SpeedFormatter`), bypassing `MyHelper.getUnitsId()`.
  * **Caller Audit Across Cockpit, Extrema, Charts, Map & Export**:
    * *Live Cockpit & Dialogs (affected — displays `"m/s"`)*: `TrackingViewModel.kt:679` (`getShortUnitsId`), `EditSensorFieldDialog.kt:1176` (`getShortUnitsId`), `SensorSourceDialog.kt:114` (`getUnitsId`), `GetMergedDevicesUseCase.kt:55` (`getUnitsId`), `PebbleService.java:464,469` (`getShortUnitsId`).
    * *Aftermath Extrema & Charts*: `WorkoutDataMapper.kt:1048` (`getUnitsId`) and `WorkoutRepository.kt:3128` (`getShortUnitsId`) would emit `"m/s"` if queried for `RADAR_APPROACH_SPEED_mps` (currently `WorkoutDataMapper.sensorsToCheck:68-71` covers `CORE_METRICS + [VERTICAL_OSCILLATION, GROUND_CONTACT_TIME]` while `WorkoutRepository.kt:1483` persists `RADAR_APPROACH_SPEED_mps` extrema). `TelemetryMetricGraph.kt:118-127` plots `SPEED` (`SPEED_mps`), not `RADAR_APPROACH_SPEED_mps`.
    * *Map Pass Markers & Strava Share*: `WorkoutRepository.kt:2890-2913` (`radar_marker_*`) and `RadarTrafficSummaryFormatter.kt:78-83` (`radar_summary_max_speed*`) multiply by `3.6` using hardcoded `"km/h"` strings in `strings_radar.xml:164-165,177-178,184` (never `"m/s"`).
    * *Exporters*: `FitFileWriter.kt` (`EventMesg` + DevFields) and `CSVFileWriter.java` read raw SQLite `m/s` (`internalUnit = "m/s"`), unaffected by `MyHelper`.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Map `SensorType.RADAR_APPROACH_SPEED_mps` alongside `SPEED_mps` in `MyHelper.getUnitsId()` and `MyHelper.getShortUnitsId()` (`km/h` Metric / `mile/h` or `mi/h` Imperial).
  2. Synchronize `docs/architecture/sensor_units.md` (§1 & §2) and `SensorType.java` (`:440`) Javadoc with live behavior while preserving `internalUnit = "m/s"`.
  3. Add JVM unit tests verifying `MyHelper.getUnitsId`/`getShortUnitsId` (Metric & Imperial) and `SpeedFormatter` alignment across callers.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Do NOT alter `RADAR_CLOSEST_DISTANCE_m` / `RADAR_PASSING_DISTANCE_m` (`< 1000 m` distance units) or `W_PRIME_BAL` (`sensor_units.md` §2).
  * Do NOT refactor 9-language `strings_radar.xml` templates (`radar_marker_*`, `radar_summary_max_speed*`) — `N/A - no UI strings` (`1 SP` confirmed; no split).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-TRK-083` (`docs/requirements/req_tracking_cockpit_part2.md:2457-2492`) and `REQ-UI-2784` (`docs/requirements/req_ui_design_system_part5.md:1720`).
* **Historical Origin & Commit Trace**: `ATT-8579` (`367a412f`, `a3508e6b`, `f55c9fee`) established `REQ-TRK-083`, `docs/architecture/sensor_units.md`, and `SensorTypeUnitsContractTest.kt`; `ATT-8440` (`e3c278bb`) fixed `RadarTrafficSummaryCard.kt` via `SpeedFormatter`.
* **Root Reason for Existing Formulation**: `REQ-TRK-083.1` (`:2474`) explicitly froze runtime behavior during documentation (`ATT-8579`), recording the `MyHelper.getUnitsId()` radar fallback verbatim rather than fixing it.
* **Preservation of Core Invariants**: `SensorType.RADAR_APPROACH_SPEED_mps.internalUnit` (`"m/s"`), `SensorType.unitId` (`R.string.units_speed_basic`), `ordinal()`, `name()`, SQLite storage, FIT/CSV raw `m/s` exports, and `SensorTypeUnitsContractTest` (`"MyHelper.getUnitsId"` token) remain intact.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Net-new `REQ-TRK-086` (or refinement of `REQ-TRK-083.1` / `REQ-CON-028`) in `docs/requirements/req_tracking_cockpit_part2.md` (`3020` lines, headroom `<= 4000`) & `TST-TRK-086` in `docs/tests/tst_tracking_cockpit.md` (`3457` lines).
* **Affected Architecture Elements**: `MyHelper.java` (`getUnitsId`, `getShortUnitsId`), `SensorType.java` (`:440` Javadoc only), `docs/architecture/sensor_units.md` (§1 row `RADAR_APPROACH_SPEED_mps` & §2 item 3). Zero DB/schema/UI-string changes (`N/A - no UI strings`).
* **Verification Impact & Test Surface**: New `MyHelperRadarUnitsTest` (or `MyHelperUnitsTest`) covering `METRIC`/`IMPERIAL` for `SPEED_mps` & `RADAR_APPROACH_SPEED_mps`; `SensorTypeUnitsContractTest`; `RadarTrafficSummaryCardTest`; `verify_requirement_governance.py` (`verify_sensor_type_javadocs()`).
* **Risk Assessment & Invariants**: **LOW** risk — 2-case switch addition reusing `getSpeedUnitNameId()` / `getShortSpeedUnitNameId()`; preserves `internalUnit = "m/s"`, FIT/CSV export units, and `SensorType.getUnitId()`.

---

## 6. Architectural Strategy & High-Level Solution
1. Update `MyHelper.getUnitsId()` (`:152`) and `MyHelper.getShortUnitsId()` (`:162`) to group `case SPEED_mps, RADAR_APPROACH_SPEED_mps -> ...`.
2. Update `SensorType.java:440` Javadoc (preserving `"Internal unit: {@code m/s}"`) and `docs/architecture/sensor_units.md` (`:76, :91`) so `SensorTypeUnitsContractTest` and `verify_sensor_type_javadocs()` pass.
3. Verify `RadarTrafficSummaryCard`, Cockpit (`TrackingViewModel`), Extrema (`WorkoutDataMapper`), and `MyHelper` via targeted JVM unit tests.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**:
  1. `RADAR_APPROACH_SPEED_mps.internalUnit == "m/s"`, `ordinal()`, `name()`, and `getUnitId()` unchanged; FIT/CSV export stays `m/s`.
  2. `RADAR_CLOSEST_DISTANCE_m`, `RADAR_PASSING_DISTANCE_m`, and `W_PRIME_BAL` behavior unchanged; zero new UI strings (`N/A - no UI strings`).
  3. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW** — Isolated 2-line switch addition in `MyHelper.java` plus doc/Javadoc sync and unit tests; zero schema, concurrency, or string-parity risk.
