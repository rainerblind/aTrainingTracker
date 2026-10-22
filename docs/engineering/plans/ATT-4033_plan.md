# Stage 3: Implementation Plan - ATT-4033: Improve Climb Detection & Route/TCX Fidelity

**Ticket**: [ATT-4033](https://rainerblind.atlassian.net/browse/ATT-4033) | **Parent Epic**: [ATT-2565](https://rainerblind.atlassian.net/browse/ATT-2565)  
**Target Release**: `4.9.40` | **Active Sprint**: `2026-43.6` | **Branch**: `feature/ATT-4033`  
**Requirement Mapping**: `REQ-MAP-055` (`REQ-MAP-055.1`..`REQ-MAP-055.6`) | **Test Spec**: `TST-MAP-055` (`TST-MAP-055.1`..`TST-MAP-055.6`)  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-22  

---

## 1. Problem Description & Architecture (SWE.2)
Targeted optimizations across 5 components: $O(N)$ climb candidate scanning with steep section isolation; GPX elevation delta hysteresis ($\ge 2.0\text{ m}$) & resilient DEM batch enrichment; decimal `<Watts>`/`<Cadence>` rounding and position-less indoor TCX trackpoints; interval-aware Normalized Power rolling window & dead code excision; and Power Zone 1 coasting ($0\text{ W}$) inclusion with non-power null guard.
* **Architecture & Layers**: Pure mathematical/algorithmic layers (`climbs`, `physiology`, `zones`) and streaming XML parsers (`routes/parsers`, `migration/parsers`). Zero schema changes.
* **UI Consistency**: No UI layout changes (telemetry calculation & parser engine fidelity).

---

## 2. Invariants & Chesterton's Fence Audit
1. **Climb Categorization Parity (`REQ-MAP-027`)**: Score formula (`dist * avgGrade`) and Cat 4..HC thresholds remain untouched.
2. **Polyline Coordinate Requirement**: `TcxCourseParser` strictly requires valid coordinates for route projection, filtering position-less points.
3. **Non-Power Null Guard**: All-zero power streams return `null` (`PowerZoneDistributionCalculatorTest` parity) to prevent phantom Zone 1 cards.
4. **Normalized Power Bit-Parity**: Default `sampleIntervalSec = 1.0` retains exact numerical bit-parity with existing test cases.

---

## 3. Step-by-Step Implementation Sequence (Stage 4 Construction)

| Step | Target Files & Exact Lines | Description | Targeted Test Command |
| :--- | :--- | :--- | :--- |
| **Step 1** | `app/.../climbs/ClimbDetector.kt:64-142`<br>`app/.../climbs/ClimbDetectorTest.kt` | Advance $i$ past insufficient bumps (`maxOf(i+1, peakIdx)`); evaluate local gradient thresholds within candidate ascents to isolate steep climbs before summit dilution. | `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.climbs.ClimbDetectorTest"` |
| **Step 2** | `app/.../routes/GpxRouteImporter.kt:186-225`<br>`app/.../routes/GpxRouteImporterElevationTest.kt` | Add $\ge 2.0\text{ m}$ elevation delta hysteresis accumulator in `calculateElevationGain`; log warning and `continue` on non-fatal DEM batch failures (`ServerError`, `NetworkError`, `Exception`). | `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.routes.GpxRouteImporter*"` |
| **Step 3** | `app/.../routes/parsers/TrackpointModels.kt:30-31`<br>`app/.../routes/parsers/TcxTrackpointParser.kt:170-180,236`<br>`app/.../routes/TcxCourseParser.kt:55-67`<br>`app/.../migration/parsers/TcxWorkoutParser.kt:120-140` | Make `RawTrackpoint.lat/lng` nullable (`Double? = null`); parse `<Watts>`/`<Cadence>` via `toDoubleOrNull()?.roundToInt()`; retain position-less points in workout parser and filter in course parser. | `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.routes.TcxCourseParserTest" --tests "com.atrainingtracker.trainingtracker.migration.parsers.TcxWorkoutParserTest"` |
| **Step 4** | `app/.../physiology/PowerPhysiologyCalculator.kt:444-463,814,842-850`<br>`app/.../physiology/PowerPhysiologyCalculatorTest.kt` | Scale NP window via `maxOf(1, (30.0 / sampleIntervalSec).roundToInt())` with `@JvmOverloads` default $1.0\text{ s}$; pass `sampleIntervalSec` at L814; excise dead code branch in `calculateWorkKj`. | `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.physiology.PowerPhysiologyCalculatorTest"` |
| **Step 5** | `app/.../ui/aftermath/WorkoutRepository.kt:693,841`<br>`app/.../ui/aftermath/zones/ZoneDistributionCalculator.kt:143-144`<br>`app/.../ui/aftermath/zones/PowerZoneDistributionCalculatorTest.kt` | Accept `pwr >= 0` in repository; in calculator, include $0\text{ W}$ in Zone 1 when `samples.any { it.value > 0 }` while returning `null` if all samples $\le 0\text{ W}$. | `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.zones.PowerZoneDistributionCalculatorTest"` |
| **Step 6** | Governance & Full Regression | Verify governance script passes (0 errors); run full test suite in Stage 5 clean-room regression. | `python3 tools/verify_requirement_governance.py` |

---

## 4. Caller-Site Audit (Rule 5.3) & Rollback Plan
* **Callers of `calculateNormalizedPower`**: `computeAerobicEfficiencyAndDecoupling` (L610, 619, 621), `calculateCyclingMetrics` (L814), and `PowerPhysiologyCalculatorTest` (L133). Default `sampleIntervalSec = 1.0` preserves 100% binary/source compatibility.
* **Verification & Rollback**: Incremental targeted unit tests per step; clean-room rollback via `git checkout feature/ATT-4033 && git reset --hard HEAD`.
