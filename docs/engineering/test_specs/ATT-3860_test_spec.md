# Stage 2 Requirement & Test Specification: `ATT-3860` — Calculate and Persist Power-Based Training Metrics (NP, IF, TSS, Work kJ, W/kg) When Importing TCX, GPX, or FIT Workout Files

## 1. Formal Requirement Specification (`REQ-MIG-039`)

### `REQ-MIG-039`: Offline Power Training Metrics Calculation, Historical Athlete Threshold Resolution, and Import/Finalization Extrema Persistence (`ATT-3860`)
The system SHALL calculate and persist session-level power training metrics — **Normalized Power ($\text{NP}$, in $\text{W}$)**, **Intensity Factor ($\text{IF} = \text{NP} / \text{FTP}$)**, **Training Stress Score ($\text{TSS} = \frac{T_{\text{active}} \cdot \text{NP} \cdot \text{IF}}{\text{FTP} \cdot 36.0}$)**, **Mechanical Work ($\text{Work}$, in $\text{kJ}$)**, **Power-to-Weight ($\text{W/kg}$)**, and **Variability Index ($\text{VI} = \text{NP} / \text{Avg\_Power}$)** — across `PowerPhysiologyCalculator`, `AthleteMetricsDatabaseManager`, `SettingsDataStoreJavaHelper`, `FitWorkoutParser`, `LegacyImportEngine.recalculateStats()`, and `TrackerService.finalizeLiveSession()` (ATT-3860, complementing `REQ-MIG-038`, `REQ-TRK-019`, `REQ-TRK-020`, `REQ-TRK-021`, and `REQ-UI-391`):

1. **Canonical Power Training Metrics Domain Calculator (`REQ-MIG-039.1` — Rule 36 & Rule 40)**:
   - `PowerPhysiologyCalculator` (`com.atrainingtracker.trainingtracker.physiology`) SHALL implement pure, stateless functions:
     - `calculateIntensityFactor(normalizedPowerWatts: Double, ftpWatts: Double): Double?`: returns $\text{NP} / \text{FTP}$ when $\text{NP} > 0$ and $\text{FTP} > 0$, else `null`.
     - `calculateTss(activeTimeSeconds: Double, normalizedPowerWatts: Double, intensityFactor: Double, ftpWatts: Double): Double?`: returns $\frac{T_{\text{active}} \cdot \text{NP} \cdot \text{IF}}{\text{FTP} \cdot 36.0}$ when $T_{\text{active}} > 0$, $\text{NP} > 0$, $\text{IF} > 0$, and $\text{FTP} > 0$, else `null`.
     - `calculateWorkKj(powerSamplesWatts: List<Double?>, sampleIntervalSec: Double = 1.0): Double?`: returns $\frac{\sum P_i \cdot \Delta t}{1000.0}$ over finite non-negative power samples when at least one sample is $> 0$, else `null`.
     - `calculatePowerToWeight(powerWatts: Double, weightKg: Double): Double?`: returns $P / m_{\text{kg}}$ when $P \ge 0$ and $m_{\text{kg}} > 0$, else `null`.
     - `calculateVariabilityIndex(normalizedPowerWatts: Double, avgPowerWatts: Double): Double?`: returns $\text{NP} / \text{Avg\_Power}$ when $\text{NP} > 0$ and $\text{Avg\_Power} > 0$, else `null`.
     - `computePowerTrainingMetrics(...)`: returns `PowerTrainingMetricsResult(normalizedPowerWatts, avgPowerWatts, maxPowerWatts, variabilityIndex, intensityFactor, tss, workKj, avgPowerToWeight, maxPowerToWeight, npPowerToWeight)` evaluated from `powerSamplesWatts`, `activeTimeSeconds`, `ftpWatts`, `athleteWeightKg`, and optional FIT session fallback values (`fallbackNpWatts`, `fallbackIf`, `fallbackTss`, `fallbackWorkKj`).

2. **Historical Athlete Profile Threshold Lookup (`REQ-MIG-039.2`)**:
   - `AthleteMetricsDatabaseManager` SHALL provide `getEntryEffectiveAt(metricType: AthleteMetricType, timestampMs: Long): AthleteMetricEntry?` returning the chronologically latest entry for `metricType` with `timestamp_ms <= timestampMs` (`ORDER BY timestamp_ms DESC, _id DESC LIMIT 1`), falling back to `getLatestEntry(metricType)` when no entry precedes `timestampMs`.
   - `SettingsDataStoreJavaHelper` SHALL provide `@JvmStatic fun resolveEffectiveFtpAt(context: Context, timestampMs: Long?): Int?` and `@JvmStatic fun resolveEffectiveWeightKgAt(context: Context, timestampMs: Long?): Float`, querying `AthleteMetricsDatabaseManager.getInstance(context).getEntryEffectiveAt(...)` when `timestampMs != null && timestampMs > 0L` and falling back gracefully to `getFtp(context)` / `getAthleteWeightKg(context)`.

3. **FIT Parser Sensor Set & Optional Session Power Summary Extraction (`REQ-MIG-039.3`)**:
   - `FitWorkoutParser.parse(...)` SHALL populate `foundSensors: Set<SensorType>` with all `SensorType`s present in `rawRecords` (`LATITUDE`, `LONGITUDE`, `ALTITUDE`, `DISTANCE_m`, `SPEED_mps`, `HR`, `CADENCE`, `POWER`, `TEMPERATURE`) and extract optional FIT `SessionMesg` power summary fields (`sessionNormalizedPowerWatts`, `sessionIntensityFactor`, `sessionTss`, `sessionWorkKj`) into `ParsedWorkoutData`.

4. **Extrema Persistence in Import & Live Finalization (`REQ-MIG-039.4`)**:
   - In `LegacyImportEngine.recalculateStats(...)` and `TrackerService.finalizeLiveSession()`, when valid power samples (or FIT session power summaries) exist, the system SHALL compute `PowerTrainingMetricsResult` using the workout's start timestamp (`firstTime` / session start) and persist into `WorkoutSummariesDatabaseManager` (`TABLE_EXTREMA_VALUES`):
     - `SensorType.NORMALIZED_POWER`: `ExtremaType.AVG` (and `ExtremaType.END`)
     - `SensorType.INTENSITY_FACTOR`: `ExtremaType.AVG` (and `ExtremaType.END`)
     - `SensorType.TSS`: `ExtremaType.AVG`, `ExtremaType.MAX` (and `ExtremaType.END`)
     - `SensorType.WORK_KJ`: `ExtremaType.AVG`, `ExtremaType.MAX` (and `ExtremaType.END`)
     - `SensorType.POWER_TO_WEIGHT`: `ExtremaType.AVG` (`avgPowerToWeight`) and `ExtremaType.MAX` (`maxPowerToWeight`)

### Requirement Archaeology & Chesterton's Fence Audit (`REQ-PRO-022`)
1. *Original Requirement ID & Target*: Net-new requirement `REQ-MIG-039`, complementing `REQ-MIG-038` (`ATT-3552`), `REQ-TRK-019` (`ATT-1289`), `REQ-TRK-020` (`ATT-1290`), `REQ-TRK-021` (`ATT-1291`), and `REQ-UI-391` (`ATT-3704`). Targets `PowerPhysiologyCalculator.kt`, `AthleteMetricsDatabaseManager.kt`, `SettingsDataStoreJavaHelper.kt`, `ParsedWorkoutData.kt`, `FitWorkoutParser.kt`, `LegacyImportEngine.kt`, `TrackerService.java`, and `CyclingPowerMetricsDevice.java`.
2. *Historical Origin & Commit Trace*: Sprint 2026-42.4 (`ATT-1289`, `ATT-1290`, `ATT-1291`) introduced live 1-Hz power metrics in `CyclingPowerMetricsDevice.java`; Sprint 2026-42.5 (`ATT-3552`, commit `7062af56`) extracted `TcxWorkoutParser.kt`, `GpxWorkoutParser.kt`, and `FitWorkoutParser.kt`.
3. *Root Reason for Existing Formulation*: `LegacyImportEngine.recalculateStats(...)` originally computed `MIN`/`MAX`/`AVG` SQL aggregates only over the 6 raw sample columns (`HR`, `CADENCE`, `POWER`, `SPEED_mps`, `ALTITUDE`, `TEMPERATURE`), before derived power physiology metrics and timestamped Athlete Profile thresholds existed.
4. *Preservation of Core Invariants*: Zero SQLite schema migrations (`WorkoutSummaries.db` `DB_VERSION = 24`, `AthleteMetrics.db` `DB_VERSION = 1`), `SensorType` count (`44`), live 1-Hz `CyclingPowerMetricsDevice` warm-up and pause gating (`REQ-TRK-023`), and 100% clean-room test pass rate remain strictly preserved.

### Acceptance Criteria (Given-When-Then)
- **Given** a 1-hour (`3600 s`) steady power stream at $250\text{ W}$ with athlete $\text{FTP} = 250\text{ W}$ and body weight $75.0\text{ kg}$,
  **When** `PowerPhysiologyCalculator.computePowerTrainingMetrics` is evaluated,
  **Then** $\text{NP} = 250.0\text{ W}$, $\text{IF} = 1.00$, $\text{TSS} = 100.0$, $\text{Work} = 900.0\text{ kJ}$, $\text{VI} = 1.00$, and $\text{Avg W/kg} = 3.333\text{ W/kg}$.
- **Given** variable power samples ($30\text{ s}$ at $300\text{ W}$ alternating with $30\text{ s}$ at $100\text{ W}$, $\text{Avg\_Power} = 200\text{ W}$),
  **When** `computePowerTrainingMetrics` is evaluated,
  **Then** $\text{NP} > 200.0\text{ W}$ and $\text{VI} = \text{NP} / \text{Avg\_Power} > 1.00$.
- **Given** a workout with no power samples (`emptyList()` or all `null`) and no FIT session fallback values,
  **When** `computePowerTrainingMetrics` is evaluated,
  **Then** all returned power training metrics are `null`.

---

## 2. Test Specification (`TST-MIG-036`)

### `TST-MIG-036`: Offline Power Training Metrics Math, Historical Threshold Resolution & FIT Parser Contract Verification Suite (`ATT-3860`)
1. **Pure Domain Power Training Metrics Unit Tests (`PowerPhysiologyCalculatorTest.kt`)**:
   - Verify `calculateIntensityFactor(250.0, 250.0) == 1.0`, `calculateIntensityFactor(200.0, 250.0) == 0.80`, and `null` when $\text{FTP} \le 0$.
   - Verify `calculateTss(3600.0, 250.0, 1.0, 250.0) == 100.0`, `calculateTss(3600.0, 200.0, 0.8, 250.0) == 64.0`, and `null` when $\text{FTP} \le 0$ or $T_{\text{active}} \le 0$.
   - Verify `calculateWorkKj` integrates 1-Hz power samples ($250\text{ W} \times 3600\text{ s} = 900.0\text{ kJ}$) and returns `null` for empty/zero streams.
   - Verify `calculatePowerToWeight(250.0, 75.0) == 3.3333...` and `calculateVariabilityIndex(220.0, 200.0) == 1.10`.
   - Verify `computePowerTrainingMetrics` computes full session metrics (`NP`, `IF`, `TSS`, `Work kJ`, `Avg/Max/NP W/kg`, `VI`) from 1-Hz power samples and falls back to FIT session summary fields when sample streams are empty.
2. **Historical Athlete Profile Lookup & Import/Finalization Source Contract Tests (`PowerPhysiologyCalculatorTest.kt`)**:
   - Verify `AthleteMetricsDatabaseManager.kt` defines `getEntryEffectiveAt` and `SettingsDataStoreJavaHelper.kt` defines `resolveEffectiveFtpAt` and `resolveEffectiveWeightKgAt`.
   - Verify `FitWorkoutParser.kt` populates `foundSensors` for `SensorType.POWER` (and other recorded sensors) and extracts FIT `SessionMesg` power fields onto `ParsedWorkoutData`.
   - Verify `LegacyImportEngine.kt` and `TrackerService.java` persist `NORMALIZED_POWER`, `INTENSITY_FACTOR`, `TSS`, `WORK_KJ`, and `POWER_TO_WEIGHT` extrema via `updateExtremaValue`.
3. **Clean-Room Full Suite Regression Execution**:
   - Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

---

## 3. Traceability Matrix

| Requirement ID | Sub-Requirement | Test Case ID | Verification Artifact |
| :--- | :--- | :--- | :--- |
| `REQ-MIG-039` | `REQ-MIG-039.1` (Power Training Metrics Math: NP, IF, TSS, Work kJ, W/kg, VI) | `TST-MIG-036` | `PowerPhysiologyCalculatorTest.kt` |
| `REQ-MIG-039` | `REQ-MIG-039.2` (Historical Athlete FTP & Weight Resolution) | `TST-MIG-036` | `PowerPhysiologyCalculatorTest.kt` |
| `REQ-MIG-039` | `REQ-MIG-039.3` (FIT Parser `foundSensors` & Session Summary Fields) | `TST-MIG-036` | `PowerPhysiologyCalculatorTest.kt` |
| `REQ-MIG-039` | `REQ-MIG-039.4` (Import & Live Finalization Extrema Persistence) | `TST-MIG-036` | `PowerPhysiologyCalculatorTest.kt` |
