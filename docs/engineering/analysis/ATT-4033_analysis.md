# Stage 1 Analysis: ATT-4033 - Improve climb detection performance and route/TCX import fidelity

**Ticket**: [ATT-4033](https://rainerblind.atlassian.net/browse/ATT-4033)  
**Parent Epic**: [ATT-2565](https://rainerblind.atlassian.net/browse/ATT-2565) | **Target Release**: `4.9.40` | **Active Sprint**: `2026-43.6` | **Branch**: `feature/ATT-4033`  
**Requirement Mapping**: `REQ-MAP-055` (*Climb Detection Performance, Route/TCX Import Fidelity & Power Calibration*)  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-22  

---

## 1. Problem Statement & Motivation
An independent whole-app code review identified performance bottlenecks and fidelity defects across climb detection, route/TCX ingestion, and power analytics:
1. **Climb Detection O(N^2) & Dilution**: `ClimbDetector.detectClimbs` advances candidate rejection with `i++`, causing up to ~50M loop iterations on 10k-point routes; long gentle ascents dilute and miss steep embedded climbs because `peakIdx` scans to the distant summit.
2. **GPX Elevation Gain & Batch Jitter**: `GpxRouteImporter.calculateElevationGain` sums raw positive deltas without hysteresis, accumulating GPS/DEM noise into inflated gain; DEM enrichment `break`s on single-batch failures, abandoning the remaining route.
3. **TCX Trackpoint Ingestion Data Loss**: `TcxTrackpointParser` discards trackpoints missing `<Position>` (`lat`/`lng`), losing all telemetry (HR, power, cadence) for indoor workouts; `toIntOrNull()` drops decimal `<Watts>`/`<Cadence>`.
4. **Power Metrics & Dead Code**: `PowerPhysiologyCalculator.calculateNormalizedPower` hardcodes a 30-sample rolling window regardless of sampling interval; lines 843–849 contain a dead-code branch where work-kJ is never scaled by `activeTimeSeconds`.
5. **Power Zone Coasting Exclusion**: `WorkoutRepository` (`pwr > 0`) and `ZoneDistributionCalculator` (`filter { it.value > 0 }`) drop 0 W coasting from Zone 1, distorting active time-in-zones.

---

## 2. Root Cause Analysis & Call Site Audit

| Component / File | Findings & Root Cause | Remediation Strategy | Affected Call Sites |
|---|---|---|---|
| `ClimbDetector.kt` | L64–140: On candidate rejection `i++` re-scans identical points ($O(N^2)$); distant peak dilutes embedded steep climb. | Advance `i` past insufficient bumps (`maxOf(i+1, peakIdx)`); evaluate local gradient thresholds to capture steep sections in $O(N)$ time. | `ClimbDetector.kt:64-142` |
| `GpxRouteImporter.kt` | L214–225: Raw `curr - prev > 0` sums noise; L186–209: Non-fatal batch errors break enrichment loop. | Introduce elevation delta hysteresis ($\ge 2.0\text{ m}$ threshold); log warning and continue next batch instead of terminating. | `GpxRouteImporter.kt:186-225` |
| `TcxTrackpointParser.kt` | L170–180: `toIntOrNull()` drops decimals; L236: `lat != null && lng != null` guard drops indoor trackpoints. | Use `toDoubleOrNull()?.roundToInt()`; allow position-less trackpoints in `RawTrackpoint` (filtered for routes, retained for workouts). | `TcxTrackpointParser.kt:170-180,236`, `TrackpointModels.kt:30` |
| `PowerPhysiologyCalculator.kt` | L452–463: Window is 30 samples, not 30s; L843–849: Duplicate identical `calculateWorkKj` call in `if`/`else`. | Make window interval-aware (`roundToInt(30.0 / sampleIntervalSec)`); clean up redundant work-kJ branch. | `PowerPhysiologyCalculator.kt:452-463,842-850` |
| `WorkoutRepository.kt` & `ZoneDistributionCalculator.kt` | L693, 841 (`pwr > 0`) and `Calculator:143` omit 0 W coasting from Zone 1. | Include 0 W in Z1 only when `samples.any { it.value > 0 }`, preserving `null` for workouts lacking power data. | `WorkoutRepository.kt:693,841`, `ZoneDistributionCalculator.kt:143` |

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Objectives**:
  * Ensure `ClimbDetector` executes in near-linear time on 10k-point routes and detects steep climbs embedded in gentle ascents.
  * Apply hysteresis to GPX route elevation gain calculation and make DEM batch enrichment resilient against transient batch errors.
  * Support position-less TCX trackpoints for indoor workouts while preserving coordinate requirements for route navigation.
  * Parse decimal Watts and Cadence tags safely via `toDoubleOrNull()?.roundToInt()`.
  * Support `sampleIntervalSec` in Normalized Power and remove redundant dead code in work-kJ.
  * Count 0 W coasting in Zone 1 (Active Recovery) while preserving `null` zone distribution when no power meter was used.
* **Out-of-Scope Non-Goals**:
  * Do NOT modify Live ClimbPro Cockpit UI state machines or database schemas (`Climbs.db`).
  * Do NOT alter heart rate zone calculations or thresholds.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **REQ-MAP-055** (*Climb Detection Performance, Route/TCX Import Fidelity & Power Calibration*): Net-new requirement refining `REQ-MAP-027`, `REQ-MAP-025`, `REQ-MIG-037`, `REQ-MIG-039`, and `REQ-UI-370`.
* **Chesterton's Fence Invariants**:
  1. `ClimbDetector`: `REQ-MAP-027` scoring (`dist * avgGrade`) and category thresholds (HC to Cat 4) MUST NOT be altered.
  2. `TcxTrackpointParser`: `TcxCourseParser` requires valid coordinates for polyline route projection; position-less trackpoints MUST be ignored by route importer but ingested by `TcxWorkoutParser`.
  3. `PowerZoneDistributionCalculatorTest`: `testCalculatePowerDistribution_zeroDurationOrNullSamples_returnsNull` mandates that all-zero power streams return `null` so workouts without a power meter do not display a bogus Zone 1 card. 0 W is only included when positive samples exist (`any { it.value > 0 }`).
  4. `PowerPhysiologyCalculator`: Default 1.0s interval yields exact bit-parity with existing NP and TSS tests.

---

## 5. Architectural Strategy & System Invariants
1. **Near-Linear Climb Scanner**: Advance index past non-viable bumps and isolate steep sections before summit dilution.
2. **Hysteresis Accumulator**: Accumulate elevation gain only after threshold elevation reversal ($\Delta h \ge 2\text{ m}$).
3. **Resilient Decimals & Optional LatLng**: Allow null lat/lng in DTO with parser fallback; parse numeric strings with floating-point tolerance.
4. **Power Zone Guard**: Check `hasPositivePower` before accepting 0 W into Zone 1.
5. **System Invariants & Risk Assessment**:
   * Risk Rating: **LOW**. Targeted algorithmic optimizations, defensive parsing, and metric fixes with zero schema changes.
   * 100% test pass rate across `ClimbDetectorTest`, `PowerPhysiologyCalculatorTest`, `PowerZoneDistributionCalculatorTest`, and parser suites.
