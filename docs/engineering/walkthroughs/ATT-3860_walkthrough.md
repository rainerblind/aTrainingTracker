# Stage 5 Walkthrough: `ATT-3860` — Calculate and Persist Power-Based Training Metrics (NP, IF, TSS, Work kJ, W/kg) When Importing TCX, GPX, or FIT Workout Files

## 1. Summary of Changes (`REQ-MIG-039`)
- **Shared Domain Power Training Metrics Calculator (`PowerPhysiologyCalculator.kt` — `REQ-MIG-039.1`)**:
  - Added `PowerTrainingMetricsResult` data class and pure, stateless functions:
    - `calculateIntensityFactor(normalizedPowerWatts, ftpWatts)` ($\text{NP} / \text{FTP}$)
    - `calculateTss(activeTimeSeconds, normalizedPowerWatts, intensityFactor, ftpWatts)` ($\frac{T_{\text{active}} \cdot \text{NP} \cdot \text{IF}}{\text{FTP} \cdot 36.0}$)
    - `calculateWorkKj(powerSamplesWatts, sampleIntervalSec)` ($\frac{\sum P_i \cdot \Delta t}{1000.0}$)
    - `calculatePowerToWeight(powerWatts, weightKg)` ($P / m_{\text{kg}}$)
    - `calculateVariabilityIndex(normalizedPowerWatts, avgPowerWatts)` ($\text{NP} / \text{Avg\_Power}$)
    - `computePowerTrainingMetrics(...)` evaluating recorded 1-Hz power streams alongside optional FIT session-level fallback seeds (`fallbackNpWatts`, `fallbackIf`, `fallbackTss`, `fallbackWorkKj`).
  - Delegated `CyclingPowerMetricsDevice.java` live `W/kg`, `IF`, and `TSS` calculations to `PowerPhysiologyCalculator` (Rule 36 — Single Source of Truth & DRY).
- **Historical Athlete Profile Threshold Lookup (`AthleteMetricsDatabaseManager.kt` & `SettingsDataStoreJavaHelper.kt` — `REQ-MIG-039.2`)**:
  - Added `getEntryEffectiveAt(metricType, timestampMs)` to `AthleteMetricsDatabaseManager.kt` to resolve the athlete's historical `FTP_WATTS` and `WEIGHT_KG` effective at or before the workout's start timestamp (`firstTime`), falling back to `getLatestEntry(metricType)`.
  - Added `resolveEffectiveFtpAt(context, timestampMs)` and `resolveEffectiveWeightKgAt(context, timestampMs)` to `SettingsDataStoreJavaHelper.kt` with graceful fallback to `SettingsDataStore` preferences.
- **FIT Parser Sensor Discovery & Session Power Summary Extraction (`FitWorkoutParser.kt` & `ParsedWorkoutData.kt` — `REQ-MIG-039.3`)**:
  - Populated `foundSensors` in `FitWorkoutParser.parse(...)` for all recorded sensor streams (`LATITUDE`, `LONGITUDE`, `ALTITUDE`, `DISTANCE_m`, `SPEED_mps`, `HR`, `CADENCE`, `POWER`, `TEMPERATURE`) and extracted optional `SessionMesg` summary fields (`sessionNormalizedPowerWatts`, `sessionIntensityFactor`, `sessionTss`, `sessionWorkKj`) onto `ParsedWorkoutData`.
- **Import & Live Finalization Extrema Persistence (`LegacyImportEngine.kt` & `TrackerService.java` — `REQ-MIG-039.4`)**:
  - Updated `LegacyImportEngine.recalculateStats(...)` and `TrackerService.finalizeLiveSession()` to compute `PowerTrainingMetricsResult` and persist `NORMALIZED_POWER` (`AVG`, `END`), `INTENSITY_FACTOR` (`AVG`, `END`), `TSS` (`AVG`, `MAX`, `END`), `WORK_KJ` (`AVG`, `MAX`, `END`), and `POWER_TO_WEIGHT` (`AVG`, `MAX`) in `WorkoutSummariesDatabaseManager` (`TABLE_EXTREMA_VALUES`).

## 2. Verification Results (`TST-MIG-036`)
- **Targeted Unit & Contract Tests**:
  - `PowerPhysiologyCalculatorTest` (`computePowerTrainingMetrics_steadyAndVariablePower_matchesCogganFormulas`, `importAndHistoricalThresholdResolution_sourceContractsArePresent`) and `CyclingPowerMetricsDeviceTest` passed 100%.
- **Full Clean-Room Regression Suite**:
  - `./gradlew testDebugUnitTest` passed 100% (`BUILD SUCCESSFUL`).
