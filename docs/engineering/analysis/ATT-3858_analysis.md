# Stage 1 Analysis: `ATT-3858` — Display Power-Based Training Metrics (NP, IF, TSS, Work kJ, W/kg, VI) in Workout Summaries and Workout Details

## 1. Executive Summary & Problem Statement
During live tracking (`CyclingPowerMetricsDevice`) and workout finalization / TCX-GPX-FIT import (`ATT-3860`, `LegacyImportEngine.recalculateStats`, `TrackerService.finalizeLiveSession`), **aTrainingTracker** computes and persists session-level power-based training metrics:
- **Normalized Power ($\text{NP}$, in $\text{W}$)** — `SensorType.NORMALIZED_POWER`
- **Intensity Factor ($\text{IF} = \text{NP} / \text{FTP}$)** — `SensorType.INTENSITY_FACTOR`
- **Training Stress Score ($\text{TSS} = \frac{T_{\text{active}} \cdot \text{NP} \cdot \text{IF}}{\text{FTP} \cdot 36.0}$)** — `SensorType.TSS`
- **Mechanical Work ($\text{Work}$, in $\text{kJ}$)** — `SensorType.WORK_KJ`
- **Power-to-Weight ($\text{W/kg}$)** — `SensorType.POWER_TO_WEIGHT`
- **Variability Index ($\text{VI} = \text{NP} / \text{Avg\_Power}$)** — derived pacing steadiness ratio

However, neither the Workout Summary card (`WorkoutSummary.kt`) nor the Workout Details view (`TrackOnMapScreen.kt` / `MapDetailLayout.kt`) currently maps or displays these power-based training metrics in `WorkoutData`, `WorkoutDetailsData`, or `WorkoutDetails.kt`—only the basic Min / Avg / Max Power row from `SensorType.CORE_METRICS` is shown in `WorkoutExtrema`.

## 2. Forensic Root Cause & Gap Analysis
1. **DTO & Symmetric Mapper Gap (`WorkoutData.kt`, `WorkoutDetailsData.kt`, `WorkoutDataMapper.kt`)**:
   - `WorkoutData` and `WorkoutDetailsData` currently expose `minWPrimeBalKj` (`ATT-3862`) and `efficiencyFactor` / `aerobicDecouplingPct` (`ATT-3861`), but lack fields for `normalizedPowerWatts`, `intensityFactor`, `tss`, `workKj`, `avgPowerToWeight`, `maxPowerToWeight`, and `variabilityIndex`.
   - `WorkoutDataMapper` needs a single, pure resolution helper `resolvePowerTrainingSummary(...)` invoked symmetrically in both `fromCursor(cursor)` and `fromSnapshot(snapshot, batch)` (Rule 20):
     - Prefers `ExtremaType.END` (falling back to `ExtremaType.AVG` / `ExtremaType.MAX`) for cumulative session metrics (`NORMALIZED_POWER`, `INTENSITY_FACTOR`, `TSS`, `WORK_KJ`) so intermediate tick averages are never misread as session totals.
     - Computes `variabilityIndex = PowerPhysiologyCalculator.calculateVariabilityIndex(np, avgPower)` whenever both `NP` and `Avg_Power` are positive.
     - Provides a deterministic on-the-fly fallback for legacy power workouts that have `SensorType.POWER` (`AVG` / `MAX`) and `activeTimeSec` prior to full sample recalculation, deriving `workKj`, `avgPowerToWeight`, `maxPowerToWeight`, and (using effective FTP) fallback `intensityFactor` and `tss`.
2. **Shared Aftermath UI Component (`WorkoutDetails.kt`)**:
   - `WorkoutDetails.kt` is rendered inside both `WorkoutSummary.kt` (Workout Summary list cards) and `TrackOnMapScreen.kt` / `MapDetailLayout.kt` (Workout Details screen).
   - Rendering a dedicated, localized Power Training Metrics section inside `WorkoutDetails.kt` when power training metrics are present surfaces `NP`, `IF`, `TSS`, `Work (kJ)`, `W/kg`, and `VI` across both Workout Summaries and Workout Details without modifying `// 8. Telemetry Metric Graphs` in `WorkoutSummary.kt` (preserving `WorkoutSummaryClickContractTest`).

## 3. Requirement Archaeology & Chesterton's Fence Audit (`REQ-PRO-022`)
1. **Original Requirement ID & Target**:
   - Net-new requirement `REQ-UI-402`, building upon `REQ-MIG-039` (`ATT-3860`), `REQ-UI-399` (`ATT-3862`), `REQ-UI-400` (`ATT-3861`), and `REQ-STB-013` (Rule 20 cursor/snapshot symmetry).
   - Targets `WorkoutData.kt`, `WorkoutDetailsData.kt`, `WorkoutDataMapper.kt`, `WorkoutDetails.kt`, and all 9 `values*/strings.xml` resource files.
2. **Historical Origin & Commit Trace**:
   - `ATT-3860` (`REQ-MIG-039`) added session-level power training metric calculation and persistence (`NORMALIZED_POWER`, `INTENSITY_FACTOR`, `TSS`, `WORK_KJ`, `POWER_TO_WEIGHT`) during workout finalization and TCX/GPX/FIT import.
   - `ATT-3862` (`REQ-UI-399`) and `ATT-3861` (`REQ-UI-400`) established the pattern of surfacing physiology summary rows in `WorkoutDetails.kt` via symmetric `WorkoutDataMapper` fields.
3. **Root Reason for Existing Formulation**:
   - `WorkoutDataMapper` originally mapped only `SensorType.CORE_METRICS` (`POWER`, `HR`, `SPEED_mps`, `CADENCE`, `ALTITUDE`, `TEMPERATURE`) into `extremaRows` because `ExtremaDataRow` is structured strictly as a 3-column `Min / Avg / Max` table, whereas `NP`, `IF`, `TSS`, `Work (kJ)`, `W/kg`, and `VI` are session-level summary metrics.
4. **Preservation of Core Invariants**:
   - Rule 20 (`WorkoutDataMappingSymmetryTest`): `fromCursor(cursor)`, `fromCursor(cursor, batch)`, and `fromSnapshot(snapshot, batch)` produce identical `WorkoutData` and `WorkoutDetailsData` power training fields.
   - Non-power workouts (e.g., running or swimming without power) have `null` power training fields and render zero extra UI elements in `WorkoutDetails.kt`.
   - Zero raw `Color(0x...)` literals outside `Color.kt` (`ThemeColorCentralizationAuditTest` compliant).

## 4. User Scope Grounding (`ATT-1250`)
- **In Scope**:
  - Exposing `normalizedPowerWatts`, `intensityFactor`, `tss`, `workKj`, `avgPowerToWeight`, `maxPowerToWeight`, and `variabilityIndex` on `WorkoutData` and `WorkoutDetailsData`.
  - Symmetric resolution (`resolvePowerTrainingSummary`) in `WorkoutDataMapper.fromCursor` and `WorkoutDataMapper.fromSnapshot`, prioritizing `ExtremaType.END` for cumulative metrics and providing on-the-fly fallback derivation from `POWER` (`AVG`/`MAX`), `activeTimeSec`, effective FTP, and effective weight.
  - Rendering the Power Training Metrics summary (`NP`, `IF`, `TSS`, `Work kJ`, `W/kg`, `VI`) in `WorkoutDetails.kt` (shared by `WorkoutSummary.kt` and `TrackOnMapScreen.kt`) with 9-language localization parity.
  - Unit, Rule 20 symmetry, and 9-locale contract tests (`TST-UI-365`).
- **Out of Scope**:
  - Modifying live 1-Hz cockpit sensor calculations (`CyclingPowerMetricsDevice`).
  - Modifying the longitudinal Performance Management Chart (`ATT-3859`).
