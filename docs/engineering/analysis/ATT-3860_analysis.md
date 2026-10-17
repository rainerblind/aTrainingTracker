# Stage 1 Analysis: `ATT-3860` — Calculate and Persist Power-Based Training Metrics (NP, IF, TSS, Work kJ, W/kg) When Importing TCX, GPX, or FIT Workout Files

## 1. Executive Summary & Problem Statement
When a workout containing power telemetry is imported from a TCX (`TcxWorkoutParser.kt`), GPX (`GpxWorkoutParser.kt`), or FIT (`FitWorkoutParser.kt`) file, `LegacyImportEngine.recalculateStats(...)` currently computes `MIN`, `MAX`, and `AVG` extrema only for the 6 core sensors (`HR`, `CADENCE`, `POWER`, `SPEED_mps`, `ALTITUDE`, `TEMPERATURE`), plus Skiba's $W'_{\text{bal}}$ (`ATT-3862`) and Aerobic Efficiency / Decoupling (`ATT-3861`).
Similarly, during live workout recording, `CyclingPowerMetricsDevice` computes real-time cumulative session metrics (`NORMALIZED_POWER`, `INTENSITY_FACTOR`, `TSS`, `WORK_KJ`, `POWER_TO_WEIGHT`), but `TrackerService.finalizeLiveSession()` does not persist session-level extrema for these 5 derived power training metrics into `WorkoutSummariesDatabaseManager.TABLE_EXTREMA_VALUES`.

Consequently, imported TCX/GPX/FIT rides and finalized workouts lack persisted session extrema for:
1. **Normalized Power ($\text{NP}$, in $\text{W}$)** — `SensorType.NORMALIZED_POWER`
2. **Intensity Factor ($\text{IF} = \text{NP} / \text{FTP}$)** — `SensorType.INTENSITY_FACTOR`
3. **Training Stress Score ($\text{TSS} = \frac{T_{\text{active}} \cdot \text{NP} \cdot \text{IF}}{\text{FTP} \cdot 36.0}$)** — `SensorType.TSS`
4. **Mechanical Work ($\text{Work}$, in $\text{kJ}$)** — `SensorType.WORK_KJ`
5. **Power-to-Weight ($\text{W/kg}$)** — `SensorType.POWER_TO_WEIGHT`

Furthermore, in `FitWorkoutParser.kt`, `foundSensors` is currently initialized as `emptySet()`, omitting detected sensor types (such as `SensorType.POWER`, `SensorType.HR`, `SensorType.CADENCE`, `SensorType.SPEED_mps`, `SensorType.ALTITUDE`, `SensorType.LATITUDE`, `SensorType.LONGITUDE`, `SensorType.DISTANCE_m`, `SensorType.TEMPERATURE`) from `ParsedWorkoutData.foundSensors` and `WorkoutSummaries.GC_DATA`, and ignoring optional FIT `SessionMesg` summary fields (`normalizedPower`, `intensityFactor`, `trainingStressScore`, `totalWork`).

## 2. Forensic Root Cause & Gap Analysis
1. **Shared Domain Power Training Metrics Calculator (`PowerPhysiologyCalculator.kt`)**:
   - `PowerPhysiologyCalculator.calculateNormalizedPower(powerSamplesWatts)` already implements Dr. Andrew Coggan's 30-second rolling average / 4th-power algorithm (`ATT-3861`, Rule 40).
   - We need pure, stateless domain functions in `PowerPhysiologyCalculator.kt` for:
     - `calculateIntensityFactor(normalizedPowerWatts: Double, ftpWatts: Double): Double?` ($\text{NP} / \text{FTP}$)
     - `calculateTss(activeTimeSeconds: Double, normalizedPowerWatts: Double, intensityFactor: Double, ftpWatts: Double): Double?` ($\frac{T_{\text{active}} \cdot \text{NP} \cdot \text{IF}}{\text{FTP} \cdot 36.0}$)
     - `calculateWorkKj(powerSamplesWatts: List<Double?>, sampleIntervalSec: Double = 1.0): Double?` ($\frac{\sum P_i \cdot \Delta t}{1000.0}$)
     - `calculatePowerToWeight(powerWatts: Double, weightKg: Double): Double?` ($P / m_{\text{kg}}$)
     - `calculateVariabilityIndex(normalizedPowerWatts: Double, avgPowerWatts: Double): Double?` ($\text{NP} / \text{Avg\_Power}$)
     - `computePowerTrainingMetrics(...)`: A unified helper returning `PowerTrainingMetricsResult` (`normalizedPowerWatts`, `avgPowerWatts`, `maxPowerWatts`, `variabilityIndex`, `intensityFactor`, `tss`, `workKj`, `avgPowerToWeight`, `maxPowerToWeight`, `npPowerToWeight`) with optional FIT session-level fallback seeds when sample streams are absent or empty.
2. **Historical Athlete Profile Threshold Lookup (`AthleteMetricsDatabaseManager.kt` & `SettingsDataStoreJavaHelper.kt`)**:
   - Imported workouts frequently have historical timestamps (`firstTime`, e.g., `2013-05-19 09:57:00`).
   - `AthleteMetricsDatabaseManager` stores timestamped `FTP_WATTS` and `WEIGHT_KG` entries in `athlete_metrics_history`.
   - Adding `getEntryEffectiveAt(metricType: AthleteMetricType, timestampMs: Long): AthleteMetricEntry?` to `AthleteMetricsDatabaseManager` (returning the latest entry with `timestamp_ms <= timestampMs`, or falling back to `getLatestEntry(metricType)` when all entries are newer than `timestampMs`) and exposing `SettingsDataStoreJavaHelper.resolveEffectiveFtpAt(context, timestampMs)` and `resolveEffectiveWeightKgAt(context, timestampMs)` (with fallback to `SettingsDataStore` preferences) ensures historical imports use accurate thresholds while remaining 100% resilient when `AthleteMetrics.db` is empty or uninitialized in unit tests.
3. **FIT Parser Sensor Set & Optional Session Summary Extraction (`FitWorkoutParser.kt` & `ParsedWorkoutData.kt`)**:
   - Populate `foundSensors: MutableSet<SensorType>` in `FitWorkoutParser.parse(...)` whenever valid `LATITUDE`, `LONGITUDE`, `ALTITUDE`, `DISTANCE_m`, `SPEED_mps`, `HR`, `CADENCE`, `POWER`, or `TEMPERATURE` records are parsed, and extract optional `SessionMesg` fields (`normalizedPower`, `intensityFactor`, `trainingStressScore`, `totalWork`) onto `ParsedWorkoutData`.
4. **Extrema Persistence in Import & Live Finalization (`LegacyImportEngine.kt` & `TrackerService.java`)**:
   - In `LegacyImportEngine.recalculateStats(...)` and `TrackerService.finalizeLiveSession()`, compute `PowerTrainingMetricsResult` and persist:
     - `SensorType.NORMALIZED_POWER` (`ExtremaType.AVG`, `ExtremaType.END`)
     - `SensorType.INTENSITY_FACTOR` (`ExtremaType.AVG`, `ExtremaType.END`)
     - `SensorType.TSS` (`ExtremaType.AVG`, `ExtremaType.MAX`, `ExtremaType.END`)
     - `SensorType.WORK_KJ` (`ExtremaType.AVG`, `ExtremaType.MAX`, `ExtremaType.END`)
     - `SensorType.POWER_TO_WEIGHT` (`ExtremaType.AVG` = `avgPowerToWeight`, `ExtremaType.MAX` = `maxPowerToWeight`)

## 3. Requirement Archaeology & Chesterton's Fence Audit (`REQ-PRO-022`)
1. **Original Requirement ID & Target**:
   - Net-new requirement `REQ-MIG-039`, complementing `REQ-MIG-038` (*Dedicated Format Parsers for TCX, GPX, and FIT*, `ATT-3552`), `REQ-TRK-019` (*Power-to-Weight*, `ATT-1289`), `REQ-TRK-020` (*Normalized Power & Intensity Factor*, `ATT-1290`), `REQ-TRK-021` (*Mechanical Work & TSS*, `ATT-1291`), and `REQ-UI-391` (*Athlete Profile SQLite History*, `ATT-3704`).
   - Targets `PowerPhysiologyCalculator.kt`, `AthleteMetricsDatabaseManager.kt`, `SettingsDataStoreJavaHelper.kt`, `ParsedWorkoutData.kt`, `FitWorkoutParser.kt`, `LegacyImportEngine.kt`, `TrackerService.java`, and `CyclingPowerMetricsDevice.java`.
2. **Historical Origin & Commit Trace**:
   - Sprint 2026-42.4 (`ATT-1289`, `ATT-1290`, `ATT-1291`) introduced live 1-Hz `POWER_TO_WEIGHT`, `NORMALIZED_POWER`, `INTENSITY_FACTOR`, `WORK_KJ`, and `TSS` in `CyclingPowerMetricsDevice.java`.
   - Sprint 2026-42.5 (`ATT-3552`, commit `7062af56`) extracted `TcxWorkoutParser.kt`, `GpxWorkoutParser.kt`, and `FitWorkoutParser.kt` from `LegacyImportEngine.kt`.
3. **Root Reason for Existing Formulation**:
   - `LegacyImportEngine.recalculateStats(...)` was originally written to compute `MIN`/`MAX`/`AVG` SQL aggregates only over the 6 raw sample columns (`HR`, `CADENCE`, `POWER`, `SPEED_mps`, `ALTITUDE`, `TEMPERATURE`), before derived power physiology metrics and timestamped Athlete Profile thresholds existed.
4. **Preservation of Core Invariants**:
   - Zero SQLite schema changes (`WorkoutSummaries.db` and `AthleteMetrics.db` remain at their current schema versions).
   - Existing live 1-Hz sensor behavior in `CyclingPowerMetricsDevice.java` (including growing `1..29s` warm-up window, pause gating, and long-pause `> 30s` window flush) is 100% preserved.
   - Workouts without power data safely skip power metric persistence without nulls or NaNs.

## 4. User Scope Grounding (`ATT-1250`)
- **In Scope**:
  - Pure domain calculation of `NP`, `IF`, `TSS`, `Work (kJ)`, `W/kg`, and `VI` in `PowerPhysiologyCalculator.kt` (`PowerTrainingMetricsResult`).
  - Historical Athlete Profile lookup (`getEntryEffectiveAt` in `AthleteMetricsDatabaseManager.kt`, `resolveEffectiveFtpAt` and `resolveEffectiveWeightKgAt` in `SettingsDataStoreJavaHelper.kt`).
  - Populating `foundSensors` and optional `SessionMesg` power summary fields in `FitWorkoutParser.kt` / `ParsedWorkoutData.kt`.
  - Persisting `NORMALIZED_POWER`, `INTENSITY_FACTOR`, `TSS`, `WORK_KJ`, and `POWER_TO_WEIGHT` extrema in `LegacyImportEngine.recalculateStats(...)` and `TrackerService.finalizeLiveSession()`.
  - Unit tests verifying power training metrics math, historical FTP/weight resolution, and FIT parser sensor discovery.
- **Out of Scope**:
  - Longitudinal PMC (`CTL`, `ATL`, `TSB`) chart UI (`ATT-3859`).
  - Workout Summary / Workout Details UI cards for displaying `NP`, `IF`, `TSS`, `Work kJ`, `W/kg`, `VI` (`ATT-3858`).
