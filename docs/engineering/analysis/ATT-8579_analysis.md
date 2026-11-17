# Stage 1 Analysis: ATT-8579 - Canonical Internal Storage and Display Units for Every SensorType

**Ticket**: `ATT-8579` (`2 SP`, Epic `ATT-431`) | **Branch**: `feature/ATT-8579` | **Date**: 2026-11-17

## 1. Problem Statement & Gap Analysis
- `SensorType.java` defines **67** enum constants, of which only 10 encode units in their names (`DISTANCE_m`, `DISTANCE_m_LAP`, `LINE_DISTANCE_m`, `SPEED_mps`, `PACE_spm`, `WORK_KJ`, `RADAR_CLOSEST_DISTANCE_m`, `RADAR_APPROACH_SPEED_mps`, `RADAR_PASSING_DISTANCE_m`, `WORLD_YAW_RATE_radps`).
- `SensorType.unitId` (`@Deprecated`) stores UI display resource IDs (`R.string.units_*`), not internal storage/sensor-bus units; `MyHelper.getUnitsId()` remaps core speed/distance/altitude/pace types to Metric/Imperial labels, creating ambiguity between raw SQLite columns (`sensorType.name()`), formatted UI strings, and file exports.
- Unspecified units caused `ATT-8577`: `ErtsPostWorkoutRecomputer.kt:377` writes `alt.verticalSpeedMps` (`m/s`) into `VERTICAL_SPEED`, whereas `VerticalSpeedAndSlopeDevice.java:216-253` & `AltitudeAndSlopeDevice_ATTFused.kt:155` emit `m/h` (`[-3000, 3000]`).

## 2. Independent Peer Review & Verification (Code-Grounded Findings)
Verified across `SensorType.java` (67 constants), formatters, `MyHelper.java`, `WorkoutSamplesDatabaseManager.java`, devices, and exporters:
- **Exact Count & Naming**: `SensorType.values()` has **67** constants (6 `SensorCategory`, 9 `SensorSubCategory`; note `SensorCategory.java:31` Javadoc states stale `63`).
- **Sentinels & Bounds**:
  - `BATTERY_REMAINING_TIME` (`s`, `INTEGER`, `BatteryDevice.java:311-330`, `BatteryRemainingTimeFormatter.kt:58-60`): `-1` = `CHARGING_STATUS_CODE` (`"Charging"`), `<= -2` = `STABILIZING_STATUS_CODE` (`"--:--"`), `>= 0` = seconds (`"%d:%02d h"`).
  - `PACE_spm` (`s/m`, `PaceFormatter.kt:35-37`): `> BANALService.MAX_PACE` renders `"~~"`.
  - `HRV_DFA_A1` (`DfaA1Formatter.kt:28-31`) & `POWER_TO_WEIGHT` / `W_PRIME_BAL` (`PowerToWeightFormatter.kt:26-29`): `< 0`, `NaN`, or `Infinite` render `"--.--"` / `"--"`.
- **Formatter / Helper Discrepancies Observed (Document Only, Out of Scope to Refactor)**:
  - `W_PRIME_BAL` (`SensorType.java:92`): `CyclingPowerMetricsDevice.java:786` emits **kJ**, `unitId = R.string.units_work_kj`, but formatter is `PowerToWeightFormatter` (`format_with_units` outputs `"W/kg"`).
  - `RADAR_CLOSEST_DISTANCE_m`, `RADAR_PASSING_DISTANCE_m`, `RADAR_APPROACH_SPEED_mps`: use `DistanceFormatter`/`SpeedFormatter` (km/mi, km/h/mph), but `MyHelper.getUnitsId()` (`MyHelper.java:150-158`) omits them and falls back to `units_distance_basic` (`m`) / `units_speed_basic` (`m/s`).
- **Export Conversions Verified**:
  - `FitFileWriter.kt`: `LATITUDE`/`LONGITUDE` `° -> semicircles` (L577-578); `VERTICAL_OSCILLATION` `cm -> mm` (`* CM_TO_MM`, L674); `WORK_KJ` `kJ -> J` (`* 1000.0`, L985); `RR_INTERVAL` `ms -> s` (`/ 1000.0f`, L707); `PEDAL_POWER_BALANCE` bit-packed `%` (L636); `ALTITUDE`/`DISTANCE_m`/`SPEED_mps`/`GROUND_CONTACT_TIME`/`TEMPERATURE` stay `m`/`m/s`/`ms`/`°C`.
  - `TCXFileWriter.kt`: `ALTITUDE`/`DISTANCE_m` (`m`), `SPEED_mps` (`m/s`). `GPXFileWriter.java`: `LATITUDE`/`LONGITUDE` (`°`), `ALTITUDE` (`m`). `CSVFileWriter.java`: raw SQLite values verbatim. `GCFileWriter.java`: `DISTANCE_m / 1000.0` (`KM`, L200), `SPEED_mps * 3.6` (`KPH`, L204).

## 3. User Scope Grounding (ATT-1250) & Solution Plan
- **In-Scope Deliverables**:
  1. Add canonical 67-entry data dictionary `docs/architecture/sensor_units.md` linked from `docs/architecture.md`, specifying per `SensorType`: enum name, `SensorCategory`/`SensorSubCategory`, internal storage/bus unit, `SensorValueType`, range/sentinels, UI formatter & Metric/Imperial display unit, and FIT/TCX/GPX/CSV/GC export units.
  2. Add structured `internalUnit` field + `getInternalUnit()` accessor and per-constant Javadoc on all 67 constants in `SensorType.java`.
  3. Add JVM contract test (`SensorTypeUnitsContractTest.kt`) enforcing non-blank `internalUnit` on all 67 `SensorType.values()` and 100% bidirectional parity with `docs/architecture/sensor_units.md`.
- **Out-of-Scope Non-Goals**:
  - Do NOT rename `SensorType` enum constants or SQLite columns (`WorkoutSamplesDatabaseManager.makeColumns` uses `sensorType.name()`).
  - Do NOT alter `ErtsPostWorkoutRecomputer.kt` (`ATT-8577`), `MyHelper.getUnitsId()`, `W_PRIME_BAL` formatter, or device math; document authentic current behavior and discrepancies.

## 4. Requirement Archaeology & Chesterton's Fence Audit (`REQ-PRO-022`)
- **Net-New Specification**: No existing requirement mandates a canonical `SensorType` unit dictionary (`REQ-PRO-043` / `REQ-PRO-044` govern `docs/architecture.md` & `interfaces.md`; `REQ-TRK-070` governs `SensorCategory`/`SensorSubCategory`). Creating `docs/architecture/sensor_units.md` (linked from `docs/architecture.md`) avoids disturbing `AspiceSweAuditContractTest.kt` `interfaces.md` checks.
- **Preserved Invariants**: `SensorType` `Parcelable` `ordinal()` order, `name()` SQLite schema (`WorkoutSamples.db`), `@Deprecated getUnitId()` contract, and existing runtime formatter/exporter outputs remain 100% unchanged.

## 5. Change-Request Impact Analysis (`REQ-PRO-064`)
- **Affected Requirements**: Net-new `REQ-ARC-*` / `REQ-PRO-*` requirement (to be specified in Stage 2) extending `REQ-PRO-043` (*SWE.2 Architecture Conformance*) and `REQ-TRK-070` (*SensorType Taxonomy*).
- **Affected Architecture Elements**: `docs/architecture/sensor_units.md` (new), `docs/architecture.md` (cross-link), `app/src/main/java/com/atrainingtracker/banalservice/sensor/SensorType.java` (`internalUnit` metadata + Javadoc).
- **Verification Impact & Test Surface**: New JVM contract test `SensorTypeUnitsContractTest.kt`; regression check of `AspiceSweAuditContractTest.kt`, `ArchitectureConformanceContractTest.kt`, `SensorTypeFormatterTest.kt`, and `SensorTypeMmpTest.kt` via `./gradlew testDebugUnitTest`.
- **Risk Assessment & Invariants**: **LOW** — purely additive metadata, documentation, and JVM contract test; zero runtime logic, SQLite schema, or Parcelable ordinal changes.
