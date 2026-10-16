# Stage 3 Implementation Plan: `ATT-3860` — Calculate and Persist Power-Based Training Metrics (NP, IF, TSS, Work kJ, W/kg) When Importing TCX, GPX, or FIT Workout Files

## 1. Architectural Overview (SWE.2)
- **Domain Physiology Layer (`PowerPhysiologyCalculator.kt`)**:
  - Pure, stateless Kotlin functions for `calculateIntensityFactor(npWatts, ftpWatts)`, `calculateTss(activeTimeSeconds, npWatts, ifValue, ftpWatts)`, `calculateWorkKj(powerSamplesWatts, sampleIntervalSec)`, `calculatePowerToWeight(powerWatts, weightKg)`, `calculateVariabilityIndex(npWatts, avgPowerWatts)`, and `computePowerTrainingMetrics(...)` returning `PowerTrainingMetricsResult`.
- **Database & Settings Layer (`AthleteMetricsDatabaseManager.kt` & `SettingsDataStoreJavaHelper.kt`)**:
  - `AthleteMetricsDatabaseManager.getEntryEffectiveAt(metricType, timestampMs)` queries `athlete_metrics_history` for the latest entry with `timestamp_ms <= timestampMs`, falling back to `getLatestEntry(metricType)`.
  - `SettingsDataStoreJavaHelper.resolveEffectiveFtpAt(context, timestampMs)` and `resolveEffectiveWeightKgAt(context, timestampMs)` query `AthleteMetricsDatabaseManager` (wrapped in safe `try/catch` for unit test environments) and fall back to `getFtp(context)` / `getAthleteWeightKg(context)`.
- **Import & Live Finalization Layer (`ParsedWorkoutData.kt`, `FitWorkoutParser.kt`, `LegacyImportEngine.kt`, `TrackerService.java`, `CyclingPowerMetricsDevice.java`)**:
  - `FitWorkoutParser.kt` populates `foundSensors: MutableSet<SensorType>` across all record fields (`LATITUDE`, `LONGITUDE`, `ALTITUDE`, `DISTANCE_m`, `SPEED_mps`, `HR`, `CADENCE`, `POWER`, `TEMPERATURE`) and extracts optional `SessionMesg` fields (`sessionNormalizedPowerWatts`, `sessionIntensityFactor`, `sessionTss`, `sessionWorkKj`) into `ParsedWorkoutData`.
  - `LegacyImportEngine.recalculateStats(...)` and `TrackerService.finalizeLiveSession()` compute `PowerTrainingMetricsResult` and persist `NORMALIZED_POWER` (`AVG`, `END`), `INTENSITY_FACTOR` (`AVG`, `END`), `TSS` (`AVG`, `MAX`, `END`), `WORK_KJ` (`AVG`, `MAX`, `END`), and `POWER_TO_WEIGHT` (`AVG`, `MAX`) in `WorkoutSummariesDatabaseManager` (`TABLE_EXTREMA_VALUES`).

## 2. Atomic Implementation Steps

### Step 1: Extend `PowerPhysiologyCalculator.kt` (`REQ-MIG-039.1`)
- Target file: `app/src/main/java/com/atrainingtracker/trainingtracker/physiology/PowerPhysiologyCalculator.kt`
- Define `PowerTrainingMetricsResult` data class and implement:
  - `calculateIntensityFactor(normalizedPowerWatts: Double, ftpWatts: Double): Double?`
  - `calculateTss(activeTimeSeconds: Double, normalizedPowerWatts: Double, intensityFactor: Double, ftpWatts: Double): Double?`
  - `calculateWorkKj(powerSamplesWatts: List<Double?>, sampleIntervalSec: Double = 1.0): Double?`
  - `calculatePowerToWeight(powerWatts: Double, weightKg: Double): Double?`
  - `calculateVariabilityIndex(normalizedPowerWatts: Double, avgPowerWatts: Double): Double?`
  - `computePowerTrainingMetrics(...)`
- Verification: `./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.physiology.PowerPhysiologyCalculatorTest`

### Step 2: Add Historical Athlete Threshold Lookup (`REQ-MIG-039.2`)
- Target files:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/database/AthleteMetricsDatabaseManager.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/settings/SettingsDataStoreJavaHelper.kt`
- Implement `getEntryEffectiveAt(metricType: AthleteMetricType, timestampMs: Long): AthleteMetricEntry?` in `AthleteMetricsDatabaseManager.kt`.
- Implement `resolveEffectiveFtpAt(context: Context, timestampMs: Long?): Int?` and `resolveEffectiveWeightKgAt(context: Context, timestampMs: Long?): Float` in `SettingsDataStoreJavaHelper.kt`.

### Step 3: Populate `foundSensors` & Optional Session Summary Fields in `FitWorkoutParser.kt` (`REQ-MIG-039.3`)
- Target files:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/migration/parsers/ParsedWorkoutData.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/migration/parsers/FitWorkoutParser.kt`
- Add optional `sessionNormalizedPowerWatts: Double? = null`, `sessionIntensityFactor: Double? = null`, `sessionTss: Double? = null`, `sessionWorkKj: Double? = null` to `ParsedWorkoutData`.
- Populate `foundSensors` in `FitWorkoutParser.parse(...)` and extract optional `primarySession` power summary fields.

### Step 4: Persist Power Training Metrics Extrema in `LegacyImportEngine.kt` & `TrackerService.java` (`REQ-MIG-039.4`)
- Target files:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`
  - `app/src/main/java/com/atrainingtracker/banalservice/devices/CyclingPowerMetricsDevice.java`
- Compute `PowerTrainingMetricsResult` using historical FTP and weight effective at workout start (`firstTime`) and persist `NORMALIZED_POWER`, `INTENSITY_FACTOR`, `TSS`, `WORK_KJ`, and `POWER_TO_WEIGHT` extrema via `updateExtremaValue`.
- Delegate `CyclingPowerMetricsDevice.java` live `W/kg`, `IF`, and `TSS` formulas to `PowerPhysiologyCalculator` (Rule 36).

### Step 5: Unit & Contract Test Suite (`TST-MIG-036`)
- Target file: `app/src/test/java/com/atrainingtracker/trainingtracker/physiology/PowerPhysiologyCalculatorTest.kt`
- Add comprehensive unit tests for `calculateIntensityFactor`, `calculateTss`, `calculateWorkKj`, `calculatePowerToWeight`, `calculateVariabilityIndex`, `computePowerTrainingMetrics`, FIT session fallback seeds, and source contract assertions for `AthleteMetricsDatabaseManager`, `SettingsDataStoreJavaHelper`, `FitWorkoutParser`, `LegacyImportEngine`, and `TrackerService`.

## 3. Invariants & Rollback Safety
- **Zero SQLite Schema Alterations**: Both `WorkoutSummaries.db` (`DB_VERSION = 24`) and `AthleteMetrics.db` (`DB_VERSION = 1`) remain untouched.
- **Sensor Count Lockstep**: `SensorType.values().size == 44` (`POWER_AND_PEDALING == 14`) remains unchanged.
- **UI Consistency (Rule 23)**: No UI layout changes in `ATT-3860` (UI presentation is scoped to `ATT-3858` and `ATT-3859`).
