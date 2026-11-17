# Stage 2: Requirement & Test Specification - ATT-8440: RadarTrafficSummaryCard Canonical Speed Units

**Ticket**: [ATT-8440](https://rainerblind.atlassian.net/browse/ATT-8440) | **Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) | **Branch**: `feature/ATT-8440`  
**Requirement Mapping**: `REQ-UI-2784` (refining `REQ-RAD-005`, `Rule 36`) | **Test Spec ID**: `TST-UI-2784`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-17

---

## 1. Requirement Specification (`REQ-UI-2784`)

* **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit
* **Implementation**: `RadarTrafficSummaryCard.kt`, `MetricFormatterContext.kt`, `SpeedFormatter.kt`, `WorkoutDetailTabbedContent.kt`

### 1.1 Functional & Architectural Requirements
1. **Canonical Speed Formatting (`REQ-UI-2784.1`)**: `RadarTrafficSummaryCard.kt` (`L131`, `L138`) SHALL format `summary.maxApproachSpeedMps` and `summary.maxVehicleSpeedMps` via `LocalMetricFormatter.current.speed.format_with_units(...)` (`MetricFormatterContext.kt`, `SpeedFormatter.kt`), displaying `km/h` under `MyUnits.METRIC` and `mile/h` under `MyUnits.IMPERIAL`.
2. **Scope & Guard Preservation (`REQ-UI-2784.2`)**: Preserve early-return guard `if (summary == null || summary.totalVehicles <= 0) return` (`L56`), non-speed fields (`vehiclesPerKm` `L110`, `vehiclesPerHour` `L117`, `minPassingDistanceM` `L157`), `WorkoutDetailTabbedContent.kt:932` call site, and `RadarTrafficSummaryFormatter.kt:78-82` untouched.

### 1.2 Acceptance Criteria (Given-When-Then)
* **Given** a `RadarTrafficSummary` with `totalVehicles = 12`, `maxApproachSpeedMps = 10.0`, `maxVehicleSpeedMps = 20.0`,
  * **When** `TrainingApplication.getUnit()` is `MyUnits.METRIC`,
  * **Then** `RadarTrafficSummaryCard` renders `36.0 km/h` / `36,0 km/h` and `72.0 km/h` / `72,0 km/h`.
  * **When** `TrainingApplication.getUnit()` is `MyUnits.IMPERIAL`,
  * **Then** `RadarTrafficSummaryCard` renders `22.4 mile/h` / `22,4 mile/h` and `44.7 mile/h` / `44,7 mile/h` (zero `km/h` speed strings).
* **Given** `summary == null` or `totalVehicles <= 0`, **When** composed, **Then** nothing is rendered.

### 1.3 System Invariants
Zero SQLite/DTO changes; `TrainingApplication.setUnitForTesting(null)` cleanup in tests; Light/Dark theme readability; `ANT-Android-SDKs` untouched.

---

## 2. Test Specification (`TST-UI-2784`)

* **`TST-UI-2784.1` (`RadarTrafficSummaryCardTest.kt`)**: Render `RadarTrafficSummaryCard` in Robolectric under `MyUnits.METRIC` (`km/h`) and `MyUnits.IMPERIAL` (`mile/h`), asserting formatted approach & vehicle speeds and `@After` reset `TrainingApplication.setUnitForTesting(null)`.
* **`TST-UI-2784.2` (`RadarTrafficSummaryCardTest.kt`)**: Assert empty composition for `null` / `totalVehicles <= 0` and verify non-speed metrics (`vehiclesPerKm`, `vehiclesPerHour`, `minPassingDistanceM`) remain intact.
* **`TST-UI-2784.3` (`RadarTrafficSummaryCardTest.kt`)**: Capture Light & Dark preview PNG under `MyUnits.IMPERIAL` (`Rule 8.5`) and inspect via `read_file`.
* **`TST-UI-2784.4`**: Run `RadarTrafficSummaryCardTest`, `RadarTrafficSummaryTest`, `RadarTrafficSummaryFormatterTest`, and `SpeedFormatterTest` (100% pass).

---

## 3. Traceability Matrix

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-2784.1` | UI (Robolectric) | `RadarTrafficSummaryCardTest`: `METRIC` (`km/h`) & `IMPERIAL` (`mile/h`) via `LocalMetricFormatter` | `REQ-UI-2784.1` | Planned |
| `TST-UI-2784.2` | UI (Robolectric) | `RadarTrafficSummaryCardTest`: null/zero guard & non-speed fields untouched | `REQ-UI-2784.2` | Planned |
| `TST-UI-2784.3` | Visual (`Rule 8.5`) | `RadarTrafficSummaryCardTest`: Light & Dark imperial card screenshot | `REQ-UI-2784.1` | Planned |
| `TST-UI-2784.4` | Regression | `RadarTrafficSummary*` + `SpeedFormatterTest` 100% pass | `REQ-UI-2784.1`, `REQ-UI-2784.2` | Planned |
