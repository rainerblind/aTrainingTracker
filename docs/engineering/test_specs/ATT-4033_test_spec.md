# Stage 2: Requirement & Test Specification - ATT-4033: Improve climb detection performance and route/TCX import fidelity

**Ticket**: [ATT-4033](https://rainerblind.atlassian.net/browse/ATT-4033)  
**Parent Epic**: [ATT-2565](https://rainerblind.atlassian.net/browse/ATT-2565) | **Target Release**: `4.9.40` | **Active Sprint**: `2026-43.6` | **Branch**: `feature/ATT-4033`  
**Requirement Mapping**: `REQ-MAP-055` (*Climb Detection Performance, Route/TCX Import Fidelity & Power Calibration*)  
**Test Spec ID**: `TST-MAP-055`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-22  

---

## 1. Requirement Specification (`REQ-MAP-055`)

### 1.1 Problem Statement & Rationale
An independent whole-app code review identified performance bottlenecks and fidelity defects across climb detection, route/TCX ingestion, and power analytics:
1. `ClimbDetector.detectClimbs` rescans points with `i++` upon candidate bump rejection, causing $O(N^2)$ execution (~50M loop iterations on 10k-point routes); distant summits dilute and miss embedded steep climbs.
2. `GpxRouteImporter.calculateElevationGain` sums raw positive deltas without hysteresis, accumulating GPS/DEM noise; DEM enrichment terminates via `break` on transient single-batch failures.
3. `TcxTrackpointParser` drops trackpoints lacking `<Position>` (`lat`/`lng`), losing all telemetry (HR, power, cadence) for indoor workouts; `toIntOrNull()` drops decimal `<Watts>`/`<Cadence>`.
4. `PowerPhysiologyCalculator.calculateNormalizedPower` hardcodes a 30-sample rolling window regardless of sample interval; lines 843–849 contain a duplicate dead-code branch in `calculateWorkKj`.
5. `WorkoutRepository` (`pwr > 0`) and `ZoneDistributionCalculator` (`filter { it.value > 0 }`) exclude 0 W coasting samples from Power Zone 1.

### 1.2 Functional & Architectural Requirements
1. **Climb Detection O(N) Advancement & Steep Section Scanning (`REQ-MAP-055.1`)**: In `ClimbDetector.detectClimbs`, candidate search SHALL advance the scanning index past non-viable bumps via `maxOf(i + 1, peakIdx)`, achieving near-linear $O(N)$ execution on large routes ($N \ge 10,000$). The detector SHALL evaluate local gradient thresholds along the ascent profile to isolate steep sections embedded in gentle ascents prior to summit dilution, while strictly preserving `REQ-MAP-027` scoring (`dist * avgGrade`) and category thresholds (`HC`, `CAT_1`..`CAT_4`).
2. **GPX Elevation Delta Hysteresis & Resilient DEM Batching (`REQ-MAP-055.2`)**: In `GpxRouteImporter.calculateElevationGain`, the system SHALL enforce an elevation delta hysteresis threshold ($\ge 2.0\text{ m}$) before accumulating vertical ascent. In DEM batch enrichment, the importer SHALL log a warning and continue subsequent batches on individual batch failures rather than terminating with `break`.
3. **TCX Decimal Rounding & Position-less Trackpoint Ingestion (`REQ-MAP-055.3`)**: In `TcxTrackpointParser`, `<Watts>` and `<Cadence>` values SHALL be parsed using `toDoubleOrNull()?.roundToInt()`. `RawTrackpoint` SHALL declare nullable `lat`/`lng` (`Double? = null`). Trackpoints lacking `<Position>` tags SHALL be retained for indoor workouts (`TcxWorkoutParser`), while `TcxCourseParser` SHALL filter them out for route polyline projection.
4. **Interval-Aware Normalized Power & Dead Code Clean-up (`REQ-MAP-055.4`)**: In `PowerPhysiologyCalculator.calculateNormalizedPower`, the rolling window size SHALL evaluate `maxOf(1, (30.0 / sampleIntervalSec).roundToInt())` with default `sampleIntervalSec = 1.0`. The redundant work-kJ dead code branch in `calculateWorkKj` (lines 843–849) SHALL be excised.
5. **Power Zone 1 Coasting Inclusion & Non-Power Null Guard (`REQ-MAP-055.5`)**: In `WorkoutRepository.getPowerZoneDistribution` and `ZoneDistributionCalculator.calculateDistribution`, 0 W coasting samples SHALL be categorized into Power Zone 1 if and only if positive power samples exist (`samples.any { it.value > 0 }`). If all power samples are zero or absent, the method SHALL return `null` (`PowerZoneDistributionCalculatorTest` parity).

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-MAP-055`, refining `REQ-MAP-027` (`ClimbDetector.kt`), `REQ-MAP-025` (`GpxRouteImporter.kt`), `REQ-EXP-001` (`TcxTrackpointParser.kt`, `TrackpointModels.kt`), and `REQ-UI-370` (`PowerPhysiologyCalculator.kt`, `WorkoutRepository.kt`, `ZoneDistributionCalculator.kt`).
* **Historical Origin & Commit Trace**: Independent whole-app review (`0234bfc0`, Oct 2026, `ATT-4033`). Traces to `ATT-1281` (commit `19177a5b`), `ATT-1502` (commit `3a65239e`), `ATT-58` (commit `2b5f7e41`), `ATT-3184` (commit `25828a66`), and `ATT-3188` (commit `889fcebf`).
* **Root Reason for Existing Formulation**: `ClimbDetector` used `i++` to prevent skipping overlapping candidate peaks; GPX elevation gain summed raw deltas without anticipating GPS/DEM jitter; TCX parser assumed mandatory GPS coordinates; Normalized Power assumed fixed 1 Hz (1s) sampling; Zone distribution filtered positive power (`pwr > 0`) to prevent non-power workouts from displaying empty Zone 1 cards without accounting for legitimate cycling coasting.
* **Preservation of Core Invariants**: `REQ-MAP-027` climb scoring thresholds, route polyline coordinate requirements in `TcxCourseParser`, zero-power null returns (`PowerZoneDistributionCalculatorTest` parity), default 1.0s sample interval bit-parity, and 100% clean-room test pass rate remain strictly preserved.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Climb Detector O(N) & Steep Embedded Ascents)**: *Given* a 10,000-point route or gentle ascent with an embedded steep section, *When* `detectClimbs` executes, *Then* peak index advances in $O(N)$ and local steep climbs are detected preserving `REQ-MAP-027` category thresholds.
* **Criterion 2 (GPX Elevation Delta Hysteresis & Resilient DEM Batching)**: *Given* noisy elevation points, *When* `calculateElevationGain` executes, *Then* sub-2.0m fluctuations are filtered. *Given* a transient DEM batch failure, *Then* subsequent batches continue enrichment.
* **Criterion 3 (TCX Decimal Rounding & Position-less Workout Ingestion)**: *Given* TCX XML with decimal `<Watts>`/`<Cadence>` or missing `<Position>`, *When* parsed, *Then* telemetry rounds to nearest integer, and position-less points are retained in workouts and filtered in routes.
* **Criterion 4 (Interval-Aware Normalized Power)**: *Given* sample interval $\Delta t \ne 1.0\text{ s}$, *When* `calculateNormalizedPower` executes, *Then* window size scales to $\max(1, \text{round}(30.0 / \Delta t))$, and work-kJ dead code is removed.
* **Criterion 5 (Power Zone Coasting Inclusion & Null Guard)**: *Given* power samples with positive values and 0 W coasting, *When* `getPowerZoneDistribution` executes, *Then* 0 W is included in Zone 1. *Given* all-zero power, *Then* `null` is returned.

---

## 2. Test Specification (`TST-MAP-055`)

### Test Cases
* `TST-MAP-055.1` (**Climb Detector O(N) Advancement & Steep Embedded Climb Unit Tests**): Verifies `maxOf(i + 1, peakIdx)` $O(N)$ execution ($\le 100\text{ ms}$ on 10k points), steep embedded climb detection, and `REQ-MAP-027` category threshold parity in `ClimbDetectorTest.kt`.
* `TST-MAP-055.2` (**GPX Elevation Gain Hysteresis & Resilient DEM Batch Enrichment Unit Tests**): Verifies $\ge 2.0\text{ m}$ elevation delta hysteresis noise filtering and continuation on single-batch failure in `GpxRouteImporterTest.kt`.
* `TST-MAP-055.3` (**TCX Decimal Rounding & Position-less Trackpoint Parsing Unit Tests**): Verifies decimal watts/cadence rounding, indoor position-less trackpoint ingestion in `TcxWorkoutParserTest.kt`, and route projection coordinate filtering in `TcxCourseParserTest.kt`.
* `TST-MAP-055.4` (**Interval-Aware Normalized Power & Work-kJ Cleanup Unit Tests**): Verifies dynamic rolling window scaling across non-1Hz intervals, 1.0s bit-parity, and dead-code excision in `PowerPhysiologyCalculatorTest.kt`.
* `TST-MAP-055.5` (**Power Zone 1 Coasting Inclusion & Zero-Power Null Guard Unit Tests**): Verifies 0 W coasting in Zone 1 for active power workouts and `null` return for all-zero/absent power in `PowerZoneDistributionCalculatorTest.kt` and `WorkoutRepositoryTest.kt`.
* `TST-MAP-055.6` (**Clean-Room Full Suite Regression Execution**): `./gradlew testDebugUnitTest` and `TranslationParityTest` achieve 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Class Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-MAP-055.1` | Unit | `ClimbDetector.kt` | `REQ-MAP-055.1` | Proposed |
| `TST-MAP-055.2` | Unit | `GpxRouteImporter.kt` | `REQ-MAP-055.2` | Proposed |
| `TST-MAP-055.3` | Unit | `TcxTrackpointParser.kt`, `TrackpointModels.kt` | `REQ-MAP-055.3` | Proposed |
| `TST-MAP-055.4` | Unit | `PowerPhysiologyCalculator.kt` | `REQ-MAP-055.4` | Proposed |
| `TST-MAP-055.5` | Unit | `WorkoutRepository.kt`, `ZoneDistributionCalculator.kt` | `REQ-MAP-055.5` | Proposed |
| `TST-MAP-055.6` | Regression | `./gradlew testDebugUnitTest`, `TranslationParityTest.kt` | `REQ-MAP-055.6` | Proposed |
