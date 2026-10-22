# Stage 2 Requirement & Test Specification: ATT-3861 — Calculate and Display Efficiency Factor (EF) and Aerobic Decoupling (Pw:Hr / Pa:Hr) in Workout Summaries, Details, and Imports

## 1. Formal Requirement Specification (`REQ-UI-400`)

### `REQ-UI-400`: Efficiency Factor (EF) and Aerobic Decoupling (Pw:Hr / Pa:Hr) Calculation, Persistence, and Aftermath Display
The system SHALL calculate, persist, and display Efficiency Factor ($\text{EF}$) and Aerobic Decoupling ($\text{Pw:Hr }\%$ for cycling/power, $\text{Pa:Hr }\%$ for running/speed) across the physiology domain calculator (`PowerPhysiologyCalculator`), live session finalization (`TrackerService`), TCX/GPX/FIT file imports (`LegacyImportEngine`), and Aftermath Workout Summaries and Workout Details (`WorkoutData`, `WorkoutDataMapper`, `WorkoutDetailsData`, `WorkoutDetails`) (`ATT-3861`, extending `REQ-UI-399` and `REQ-STB-013`):

1. **Canonical Physiology Domain Calculation (`REQ-UI-400.1` — Rule 36 & Rule 40)**:
   - `PowerPhysiologyCalculator` (`com.atrainingtracker.trainingtracker.physiology`) SHALL implement pure, stateless functions:
     - `calculateNormalizedPower(powerSamplesWatts: List<Double?>): Double?`: Computes Dr. Andrew Coggan's 30-second rolling average (with growing $1..29\text{ s}$ warm-up window per Rule 40) raised to the 4th power, averaged across active samples, and taken to the $1/4$ root.
     - `calculateEfficiencyFactorPower(normalizedPowerWatts: Double, avgHeartRateBpm: Double): Double?`: Computes cycling Efficiency Factor $\text{EF}_{\text{pw}} = \text{NP} / \text{Avg\_HR}$ (in $\text{W/bpm}$) when $\text{NP} > 0$ and $\text{Avg\_HR} > 0$.
     - `calculateEfficiencyFactorSpeed(avgSpeedMps: Double, avgHeartRateBpm: Double): Double?`: Computes running/speed Efficiency Factor $\text{EF}_{\text{pa}} = (\text{avgSpeedMps} \times 60.0) / \text{Avg\_HR}$ (in $\text{m/min/bpm}$) when $\text{avgSpeedMps} > 0$ and $\text{Avg\_HR} > 0$.
     - `calculateAerobicDecouplingFromEf(efFirstHalf: Double, efSecondHalf: Double): Double?`: Computes percentage aerobic decoupling $\text{Decoupling }\% = \frac{\text{EF}_1 - \text{EF}_2}{\text{EF}_1} \times 100.0$ when $\text{EF}_1 > 0$ and $\text{EF}_2 > 0$.
     - `computeAerobicEfficiencyAndDecoupling(powerSamplesWatts: List<Double?>, speedSamplesMps: List<Double?>, hrSamplesBpm: List<Double?>, bSportType: BSportType): AerobicEfficiencyResult`: Evaluates paired active workout streams (splitting into equal first half $H_1$ and second half $H_2$), preferring Power + HR (`Pw:Hr`) when valid positive power and HR samples exist, and falling back to Speed + HR (`Pa:Hr`) when valid positive speed and HR samples exist. Classifies decoupling $\ge 5.0\%$ (`AEROBIC_DECOUPLING_DRIFT_THRESHOLD_PCT = 5.0`) as high cardiovascular drift (`isHighDrift = true`) and $< 5.0\%$ as coupled (`isHighDrift = false`).

2. **Database Persistence in Live Finalization & File Imports (`REQ-UI-400.2`)**:
   - `ExtremaType.java` SHALL define `EFFICIENCY_FACTOR(R.string.efficiency_factor_short)` and `AEROBIC_DECOUPLING(R.string.aerobic_decoupling_short)`.
   - Both `LegacyImportEngine.recalculateStats()` (invoked during TCX, GPX, and FIT imports) and `TrackerService.finalizeLiveSession()` (invoked at the end of live tracking) SHALL query the workout's recorded `POWER`, `SPEED_mps`, and `HR` sample streams from `WorkoutSamplesDatabaseManager`, compute `AerobicEfficiencyResult`, and persist `ExtremaType.EFFICIENCY_FACTOR` and `ExtremaType.AEROBIC_DECOUPLING` under `SensorType.POWER` (for `Pw:Hr`) or `SensorType.SPEED_mps` (for `Pa:Hr`) in `WorkoutSummariesDatabaseManager` (and `WorkoutRepository`).

3. **Symmetric DTO Mapping & Aftermath UI Presentation (`REQ-UI-400.3` — Rule 20 & Rule 23)**:
   - `WorkoutData` and `WorkoutDetailsData` SHALL expose `efficiencyFactor: Double? = null`, `aerobicDecouplingPct: Double? = null`, `isPowerBasedEfficiency: Boolean = true`, and `isAerobicDecouplingHighDrift: Boolean = false`.
   - `WorkoutDataMapper.fromCursor(cursor)` and `WorkoutDataMapper.fromSnapshot(snapshot, batch)` SHALL symmetrically populate these fields via `resolveAerobicEfficiencySummary(...)` (with fallback calculation of `efficiencyFactor` when `HR (AVG)` and `POWER`/`SPEED_mps` averages exist).
   - `WorkoutDetails.kt` (rendered in both `WorkoutSummary.kt` and `TrackOnMapScreen.kt`) SHALL display the formatted Efficiency Factor (`EF`) and Aerobic Decoupling (`Pw:Hr` or `Pa:Hr` percentage) alongside a semantic Coupled ($< 5.0\%$) vs. High Drift ($\ge 5.0\%$) badge whenever available.

4. **9-Language Localization Parity (`REQ-UI-400.4`)**:
   - All new string resources (`efficiency_factor_short`, `aerobic_decoupling_short`, `workout_ef_pwhr_format`, `workout_ef_pahr_format`, `workout_ef_only_pw_format`, `workout_ef_only_pa_format`, `workout_decoupling_coupled`, `workout_decoupling_drift`) SHALL exist across all 9 supported locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) with matching positional format specifiers.

### Requirement Archaeology & Chesterton's Fence Audit (`REQ-UI-400`)
1. **Original Requirement ID & Target**:
   - Net-new requirement `REQ-UI-400` added to `docs/requirements.md`, extending `REQ-UI-399` (`ATT-3862`) and `REQ-STB-013` (`ATT-2309`). Targets `PowerPhysiologyCalculator.kt`, `ExtremaType.java`, `TrackerService.java`, `LegacyImportEngine.kt`, `WorkoutData.kt`, `WorkoutDataMapper.kt`, `WorkoutDetailsData.kt`, `WorkoutDetails.kt`, and `values*/strings.xml`.
2. **Historical Origin & Commit Trace**:
   - Sprint `2026-42.7` (`ATT-3862`, commit `61ccb801`) and Sprint `2026-41.1` (`ATT-2309`, `LegacyImportEngine`).
3. **Root Reason for Existing Formulation ("Why was this fence built?")**:
   - `WorkoutSummaries` (`TABLE_EXTREMA_VALUES`) originally stored univariate per-sensor extrema (`MIN`, `AVG`, `MAX`, `END`) without cross-sensor aerobic efficiency (`NP / Avg_HR`, `Speed / Avg_HR`) or first-half vs. second-half cardiovascular drift (`Pw:Hr %`, `Pa:Hr %`).
4. **Preservation of Core Invariants ("Why is it safe to extend now?")**:
   - Persisting `ExtremaType.EFFICIENCY_FACTOR` and `ExtremaType.AEROBIC_DECOUPLING` on `SensorType.POWER` (for `Pw:Hr`) and `SensorType.SPEED_mps` (for `Pa:Hr`) inside `TABLE_EXTREMA_VALUES` requires zero SQLite schema changes (`DB_VERSION = 24`), keeps `SensorType.values().size == 44` intact (`SensorCategoryAndSelectorContractTest`), preserves the 4 telemetry graphs in `WorkoutSummary.kt` (`WorkoutSummaryClickContractTest`), and enforces Rule 20 DTO mapping symmetry across `fromCursor` and `fromSnapshot`.

### System Invariants (`REQ-UI-400`)
- **Rule 36 (Single Source of Truth & DRY)**: All Efficiency Factor (`EF`) and Aerobic Decoupling (`Pw:Hr` / `Pa:Hr`) math MUST reside strictly in `PowerPhysiologyCalculator.kt`.
- **Rule 20 (Symmetric DTO Mapping)**: `WorkoutDataMapper.fromCursor` (single & batch) and `WorkoutDataMapper.fromSnapshot` MUST populate `efficiencyFactor`, `aerobicDecouplingPct`, `isPowerBasedEfficiency`, and `isAerobicDecouplingHighDrift` identically.
- **Zero Schema Migration**: `WorkoutSummariesDatabaseManager.DB_VERSION = 24` MUST remain unchanged.

---

## 2. Acceptance Criteria (Given-When-Then)
- **AC-1 (Cycling Efficiency Factor & Pw:Hr Decoupling)**:
  - *Given* a cycling workout stream where First Half ($H_1$) has steady $P_1 = 240\text{ W}$ and $\text{HR}_1 = 150\text{ bpm}$ ($\text{EF}_1 = 1.60\text{ W/bpm}$) and Second Half ($H_2$) has steady $P_2 = 240\text{ W}$ and $\text{HR}_2 = 160\text{ bpm}$ ($\text{EF}_2 = 1.50\text{ W/bpm}$),
  - *When* `PowerPhysiologyCalculator.computeAerobicEfficiencyAndDecoupling` is evaluated,
  - *Then* $\text{EF}_1 = 1.60$, $\text{EF}_2 = 1.50$, $\text{Decoupling }\% = \frac{1.60 - 1.50}{1.60} \times 100 = 6.25\%$, `isPowerBased == true`, and `isHighDrift == true` ($\ge 5.0\%$).
- **AC-2 (Running Efficiency Factor & Pa:Hr Coupled Response)**:
  - *Given* a running workout stream without power where $H_1$ has $\text{Speed}_1 = 3.5\text{ m/s}$ ($210\text{ m/min}$) and $\text{HR}_1 = 150\text{ bpm}$ ($\text{EF}_1 = 1.40\text{ m/min/bpm}$) and $H_2$ has $\text{Speed}_2 = 3.5\text{ m/s}$ and $\text{HR}_2 = 153\text{ bpm}$ ($\text{EF}_2 \approx 1.3725\text{ m/min/bpm}$),
  - *When* `PowerPhysiologyCalculator.computeAerobicEfficiencyAndDecoupling` is evaluated,
  - *Then* $\text{Decoupling }\% \approx 1.96\% < 5.0\%$, `isPowerBased == false`, and `isHighDrift == false` (Coupled).
- **AC-3 (Rule 20 Cursor & Snapshot Mapping Symmetry)**:
  - *Given* a workout with persisted `ExtremaType.EFFICIENCY_FACTOR` (`1.65`) and `ExtremaType.AEROBIC_DECOUPLING` (`3.8`) on `SensorType.POWER`,
  - *When* mapped via single-item `fromCursor`, batch `fromCursor`, and `fromSnapshot`,
  - *Then* all three paths yield identical `efficiencyFactor = 1.65`, `aerobicDecouplingPct = 3.8`, `isPowerBasedEfficiency = true`, and `isAerobicDecouplingHighDrift = false`.

---

## 3. Formal Test Specification (`TST-UI-363`)

| Test ID | Target Test Class | Verification Scope |
| :--- | :--- | :--- |
| `TST-UI-363.1` | `PowerPhysiologyCalculatorTest.kt` | Pure JVM unit tests for 30s rolling `calculateNormalizedPower`, `calculateEfficiencyFactorPower`, `calculateEfficiencyFactorSpeed`, `calculateAerobicDecouplingFromEf`, and `computeAerobicEfficiencyAndDecoupling` across cycling (`Pw:Hr`), running (`Pa:Hr`), coupled ($<5\%$), high-drift ($\ge 5\%$), and missing-HR/short-stream edge cases. |
| `TST-UI-363.2` | `WorkoutDataMappingSymmetryTest.kt` | Rule 20 mapping symmetry across single `fromCursor`, batch `fromCursor`, and `fromSnapshot` for `efficiencyFactor`, `aerobicDecouplingPct`, `isPowerBasedEfficiency`, and `isAerobicDecouplingHighDrift` (both persisted and fallback). |
| `TST-UI-363.3` | `AthleteProfileContractAndLocalizationTest.kt` | 9-locale resource parity (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) and positional format specifier parity for all new EF and Aerobic Decoupling strings. |

---

## 4. Bidirectional Traceability Matrix

| Requirement ID | Sub-Clauses | Test ID | Target Production & Test Files |
| :--- | :--- | :--- | :--- |
| `REQ-UI-400` | `REQ-UI-400.1` – `REQ-UI-400.4` | `TST-UI-363` | `PowerPhysiologyCalculator.kt`, `ExtremaType.java`, `TrackerService.java`, `LegacyImportEngine.kt`, `WorkoutData.kt`, `WorkoutDataMapper.kt`, `WorkoutDetailsData.kt`, `WorkoutDetails.kt`, `values*/strings.xml`, `PowerPhysiologyCalculatorTest.kt`, `WorkoutDataMappingSymmetryTest.kt`, `AthleteProfileContractAndLocalizationTest.kt` |
