# Stage 2: Requirement & Test Specification - ATT-8676

**DOC-ID**: `DOC-TST-ATT-8676` | **Ticket**: [ATT-8676](https://rainerblind.atlassian.net/browse/ATT-8676)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*) | **Target Release**: `V5.0.0`  
**Sprint**: `sprint/2026-11-18_01_Früh` | **Branch**: `feature/ATT-8676` | **Author**: `--as agent1`  
**Requirement Mapping**: `REQ-TRK-088` (refining `REQ-TRK-083.1`, `REQ-CON-028`) | **Test Spec ID**: `TST-TRK-088`  

---

## 1. Requirement Specification (`REQ-TRK-088` `[Planned]`)

### 1.1 Attributes & Rationale
- **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit
- **Implementation**: `MyHelper.java`, `SensorType.java` (Javadoc), `docs/architecture/sensor_units.md`
- **Rationale**: `SensorType.RADAR_APPROACH_SPEED_mps` formats values via `SpeedFormatter` (`km/h` / `mile/h`), but `MyHelper.getUnitsId()` (`L152`) and `MyHelper.getShortUnitsId()` (`L162`) omit it from the `SPEED_mps` branch and fall through to `sensorType.getUnitId()` = `R.string.units_speed_basic` (`"m/s"`), causing unit mismatches across Cockpit, Dialogs, and Aftermath callers (`ATT-8676`).

### 1.2 Functional & Architectural Requirements
1. **`MyHelper` Radar Speed Unit Mapping (`REQ-TRK-088.1`)**:
   - `MyHelper.getUnitsId(SensorType)` SHALL include `SensorType.RADAR_APPROACH_SPEED_mps` in the `SPEED_mps` branch (`getSpeedUnitNameId()`): returning `R.string.units_speed_metric` (`"km/h"`) for `MyUnits.METRIC` and `R.string.units_speed_imperial` (`"mile/h"`) for `MyUnits.IMPERIAL` — never `R.string.units_speed_basic` (`"m/s"`).
   - `MyHelper.getShortUnitsId(SensorType)` SHALL include `SensorType.RADAR_APPROACH_SPEED_mps` in the `SPEED_mps` branch (`getShortSpeedUnitNameId()`): returning `R.string.units_speed_short_metric` (`"km/h"`) for `MyUnits.METRIC` and `R.string.units_speed_short_imperial` (`"mi/h"`) for `MyUnits.IMPERIAL` — never `R.string.units_speed_basic` (`"m/s"`).
2. **Dictionary & `SensorType` Javadoc Sync (`REQ-TRK-088.2`)**:
   - Update `docs/architecture/sensor_units.md` (`L76, L91`) and `SensorType.java` (`L440` Javadoc) to reflect `MyHelper.getUnitsId`/`getShortUnitsId` returning `km/h` / `mile/h` (`mi/h` short), while preserving `"Internal unit: {@code m/s}"` Javadoc and the `"MyHelper.getUnitsId"` contract token.
3. **Preserved Invariants & Localization (`REQ-TRK-088.3`)**:
   - Preserve `internalUnit = "m/s"`, `ordinal()`, `name()`, `@Deprecated getUnitId() = R.string.units_speed_basic`, FIT/CSV raw `m/s`, `RADAR_CLOSEST_DISTANCE_m`, `RADAR_PASSING_DISTANCE_m`, `W_PRIME_BAL`, and `WorkoutRepository.kt` untouched (`<= 5,117` LoC).
   - `Localization Parity: N/A - no UI strings` (`values*/strings_radar.xml` and `strings.xml` untouched).

### 1.3 Acceptance Criteria (Given-When-Then) & Invariants
- *Given* `MyUnits.METRIC`, *When* `MyHelper.getUnitsId(RADAR_APPROACH_SPEED_mps)` and `getShortUnitsId(RADAR_APPROACH_SPEED_mps)` are called, *Then* they return `R.string.units_speed_metric` (`"km/h"`) and `R.string.units_speed_short_metric` (`"km/h"`), never `R.string.units_speed_basic`.
- *Given* `MyUnits.IMPERIAL`, *When* `MyHelper.getUnitsId(RADAR_APPROACH_SPEED_mps)` and `getShortUnitsId(RADAR_APPROACH_SPEED_mps)` are called, *Then* they return `R.string.units_speed_imperial` (`"mile/h"`) and `R.string.units_speed_short_imperial` (`"mi/h"`), never `R.string.units_speed_basic`.
- *Given* `SensorTypeUnitsContractTest`, `SystemArchitectureDesignContractTest`, `SourceTextReadingAuditContractTest`, and `verify_requirement_governance.py`, *When* executed, *Then* all pass with `0` new `src/main` reads (`REQ-PRO-049.1`).
- **Invariants**: `internalUnit = "m/s"`, `getUnitId() = R.string.units_speed_basic`, FIT/CSV raw `m/s`, radar distances & `W_PRIME_BAL` unchanged, `WorkoutRepository.kt <= 5,117` LoC untouched, `Localization Parity: N/A - no UI strings`.

### Requirement Archaeology & Chesterton's Fence Audit
- **Original Requirement ID & Target**: Net-new `REQ-TRK-088` refining `REQ-TRK-083.1` (`req_tracking_cockpit_part2.md:2467-2474`) & `REQ-CON-028` (`MyHelper.java`, `SensorType.java`, `docs/architecture/sensor_units.md`).
- **Historical Origin & Commit Trace**: `ATT-5083` (`REQ-CON-028`) added `RADAR_APPROACH_SPEED_mps`; `ATT-8579` (`367a412f`, `a3508e6b`, `f55c9fee`) froze existing behavior in `REQ-TRK-083`; `ATT-8440` (`e3c278bb`) fixed `RadarTrafficSummaryCard.kt`.
- **Root Reason for Existing Formulation**: `REQ-TRK-083.1` intentionally documented legacy fallback behavior verbatim without runtime changes during `ATT-8579`.
- **Preservation of Core Invariants**: `internalUnit = "m/s"`, `ordinal()`/`name()`, `@Deprecated getUnitId()`, FIT/CSV `m/s`, `"MyHelper.getUnitsId"` token, radar distance types, `W_PRIME_BAL`, and `WorkoutRepository.kt` remain intact.

---

## 2. Test Specification (`TST-TRK-088` `[Planned]`)
- **`TST-TRK-088.1` (`MyHelperRadarUnitsTest.kt`)**: Assert Metric (`"km/h"`) and Imperial (`"mile/h"` / `"mi/h"`) unit IDs & resolved strings for `RADAR_APPROACH_SPEED_mps` across `MyHelper.getUnitsId`/`getShortUnitsId`, plus unchanged `internalUnit = "m/s"`, `getUnitId()`, and radar distance fallback.
- **`TST-TRK-088.2` (Caller & Formatter Alignment)**: Verify caller integration across `WorkoutDataMapper`, `WorkoutRepository`, `TrackingViewModel`, and `RadarTrafficSummaryCardTest` resolves Metric `"km/h"` / Imperial `"mile/h"` (`"mi/h"`), never `"m/s"`.
- **`TST-TRK-088.3` (Contracts & Governance)**: Run `SensorTypeUnitsContractTest`, `SystemArchitectureDesignContractTest`, `SourceTextReadingAuditContractTest` (`0` new `src/main` reads per `REQ-PRO-049.1`), and `verify_requirement_governance.py` (`exit 0`; `N/A - no UI strings`).

---

## 3. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-088.1` | Unit (`MyHelperRadarUnitsTest`) | `MyHelper.getUnitsId` / `getShortUnitsId` (Metric & Imperial) | `REQ-TRK-088.1`, `REQ-TRK-088.3` | Planned |
| `TST-TRK-088.2` | Caller Integration | `WorkoutDataMapper`, `WorkoutRepository`, `TrackingViewModel`, `RadarTrafficSummaryCardTest` | `REQ-TRK-088.1`, `REQ-CON-028` | Planned |
| `TST-TRK-088.3` | Contract & Governance (`0` `src/main` reads) | `SensorTypeUnitsContractTest`, `SystemArchitectureDesignContractTest`, `SourceTextReadingAuditContractTest` | `REQ-TRK-088.2`, `REQ-TRK-083`, `REQ-PRO-049.1` | Planned |
