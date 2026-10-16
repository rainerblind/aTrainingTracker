# Stage 5 Walkthrough & Verification Report: ATT-3861

**Parent Ticket**: `ATT-3861` — Calculate and display Efficiency Factor (EF) and Aerobic Decoupling (Pw:Hr / Pa:Hr) in Workout Summaries, Details, and Imports
**Requirement ID**: `REQ-UI-400` (`REQ-UI-400.1` – `REQ-UI-400.4`)
**Test ID**: `TST-UI-363` (`TST-UI-363.1` – `TST-UI-363.3`)
**Sprint Branch**: `sprint/2026-42.7`

---

## 1. Executive Summary of Delivered Architecture

1. **Canonical Physiology Domain Calculator (`PowerPhysiologyCalculator.kt` — Rule 36 Single Source of Truth)**:
   - Implemented `calculateNormalizedPower(powerSamplesWatts)` using Dr. Andrew Coggan's 30-second rolling average (with growing $1..29\text{ s}$ warm-up window per Rule 40) raised to the 4th power, averaged, and taken to the $1/4$ root.
   - Implemented `calculateEfficiencyFactorPower(normalizedPowerWatts, avgHeartRateBpm)` returning $\text{EF}_{\text{pw}} = \text{NP} / \text{Avg\_HR}$ (in $\text{W/bpm}$).
   - Implemented `calculateEfficiencyFactorSpeed(avgSpeedMps, avgHeartRateBpm)` returning $\text{EF}_{\text{pa}} = (\text{Avg\_Speed}_{\text{m/s}} \times 60.0) / \text{Avg\_HR}$ (in $\text{m/min/bpm}$).
   - Implemented `calculateAerobicDecouplingFromEf(efFirstHalf, efSecondHalf)` returning $\text{Decoupling }\% = \frac{\text{EF}_1 - \text{EF}_2}{\text{EF}_1} \times 100.0$.
   - Implemented `computeAerobicEfficiencyAndDecoupling(powerSamplesWatts, speedSamplesMps, hrSamplesBpm, bSportType)` splitting active paired streams into two equal halves ($H_1$ and $H_2$), preferring `Pw:Hr` when valid power + HR samples exist and falling back to `Pa:Hr` when valid speed + HR samples exist, and classifying decoupling $\ge 5.0\%$ (`AEROBIC_DECOUPLING_DRIFT_THRESHOLD_PCT`) as `isHighDrift = true`.
2. **Persistence in Live Finalization & File Imports (`REQ-UI-400.2`)**:
   - Extended `ExtremaType.java` with `EFFICIENCY_FACTOR(R.string.efficiency_factor_short)` and `AEROBIC_DECOUPLING(R.string.aerobic_decoupling_short)`.
   - Updated `LegacyImportEngine.kt` (`recalculateStats()`) and `TrackerService.java` (`finalizeLiveSession()`) to compute `AerobicEfficiencyResult` from recorded `POWER`, `SPEED_mps`, and `HR` sample streams and persist `EFFICIENCY_FACTOR` and `AEROBIC_DECOUPLING` on `SensorType.POWER` (`Pw:Hr`) or `SensorType.SPEED_mps` (`Pa:Hr`) with zero SQLite schema migration (`DB_VERSION = 24`).
3. **Symmetric DTO Mapping & Aftermath UI Presentation (`REQ-UI-400.3` — Rule 20 & Rule 23)**:
   - Added `efficiencyFactor`, `aerobicDecouplingPct`, `isPowerBasedEfficiency`, and `isAerobicDecouplingHighDrift` to `WorkoutData.kt` and `WorkoutDetailsData.kt`.
   - Implemented `resolveAerobicEfficiencySummary(...)` in `WorkoutDataMapper.kt` and wired it symmetrically into both `fromCursor(cursor)` and `fromSnapshot(snapshot, batch)` (Rule 20).
   - Updated `WorkoutDetails.kt` (rendered in both `WorkoutSummary.kt` and `TrackOnMapScreen.kt`) to display formatted Efficiency Factor (`EF`) and Aerobic Decoupling (`Pw:Hr` / `Pa:Hr`) alongside a semantic Coupled ($< 5\%$) vs. High Drift ($\ge 5\%$) badge.
4. **9-Language Localization Parity (`REQ-UI-400.4`)**:
   - Added all 8 string keys (`efficiency_factor_short`, `aerobic_decoupling_short`, `workout_ef_pwhr_format`, `workout_ef_pahr_format`, `workout_ef_only_pw_format`, `workout_ef_only_pa_format`, `workout_decoupling_coupled`, `workout_decoupling_drift`) across all 9 supported locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).

---

## 2. Verification & Clean-Room Regression Results

| Test Suite / Check | Scope | Result |
| :--- | :--- | :---: |
| `PowerPhysiologyCalculatorTest` (`TST-UI-363.1`) | 30s rolling `calculateNormalizedPower`, cycling `EF` & `Pw:Hr` decoupling ($\ge 5\%$ drift), running `EF` & `Pa:Hr` decoupling ($< 5\%$ coupled), and missing-HR edge cases | **PASS** |
| `WorkoutDataMappingSymmetryTest` (`TST-UI-363.2`) | Rule 20 symmetry across single `fromCursor`, batch `fromCursor`, and `fromSnapshot` for `efficiencyFactor`, `aerobicDecouplingPct`, `isPowerBasedEfficiency`, and `isAerobicDecouplingHighDrift` | **PASS** |
| `AthleteProfileContractAndLocalizationTest` (`TST-UI-363.3`) | 9-locale parity and positional format specifiers (`%1$.2f`, `%2$.1f`) | **PASS** |
| `SensorCategoryAndSelectorContractTest` | 44-sensor `SensorCategory` partition preserved | **PASS** |
| `WorkoutSummaryClickContractTest` | 4 telemetry graphs in `WorkoutSummary.kt` preserved | **PASS** |
| Full Clean-Room Regression (`./gradlew testDebugUnitTest`) | Complete unit test suite across all modules | **PASS (100%)** |

---

## 3. Traceability Matrix Synchronization

- `docs/requirements.md`: `REQ-UI-400` updated from `Planned` to `Verified`.
- `docs/tests.md`: `TST-UI-363` updated from `Planned` to `Verified`.
