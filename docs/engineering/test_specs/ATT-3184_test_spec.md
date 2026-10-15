# Stage 2: Requirement & Test Specification - ATT-3184: [Verbesserung] Migrate cycling power to 7-zone Coggan model while retaining 5-zone scheme for heart rate

**Ticket**: [ATT-3184](https://atrainingtracker.atlassian.net/browse/ATT-3184)  
**Sub-task**: [ATT-3596](https://atrainingtracker.atlassian.net/browse/ATT-3596) (`[Test-Spec]`)  
**Parent Epic**: [ATT-111](https://atrainingtracker.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `v1.0.0-rc1`  
**Active Sprint**: `2026-42.4`  
**Requirement Mapping**: `REQ-UI-370` (*Decoupled Training Zone Architecture & 7-Zone Coggan Cycling Power Model*)  
**Test Spec ID**: `TST-UI-333`  
**Branch**: `feature/ATT-3184`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-15  

---

## 1. Requirement Specification (REQ-UI-370)

### 1.1 Problem Statement & Rationale
Currently, `aTrainingTracker` enforces an identical 5-zone model across Heart Rate (`HR_RUN`, `HR_BIKE`) and Cycling Power (`PWR_BIKE`). While a 5-zone model is the established gold standard for Heart Rate (governed by cardiac autonomic response and stroke volume limits), cycling power universally follows Dr. Andy Coggan's 7-Zone Model (Hunter & Coggan, *Training and Racing with a Power Meter*). 

Because cycling power is mechanical, instantaneous, and spans an extreme dynamic range above threshold (Lactate Threshold, VO2 Max, Anaerobic Capacity, and Neuromuscular Sprint Power), collapsing all efforts $> 105\%$ FTP into a generic "Zone 5 (Anaerobic)" prevents athletes from analyzing and pacing suprathreshold workouts.

`REQ-UI-370` amends `REQ-UI-203` and complements `REQ-UI-202` and `REQ-UI-231` by decoupling the zone architecture: retaining 5 zones for heart rate while expanding cycling power to Dr. Andy Coggan's 7-zone framework across settings, real-time HUD tiles, and post-workout aftermath analytics.

### 1.2 Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-UI-203` (*Aftermath: Cycling Power 5-Zone Distribution (Time-in-Zones) Vertical Column Chart Architecture*, Sprint 2026-40.5, ATT-1390). Targets `SettingsDataStore.kt`, `SettingsDataStoreJavaHelper.kt`, `ZonesSettingsActivity.kt`, `ZoneDistributionModels.kt`, `TelemetryZoneMath.kt`, `TrackingViewModel.kt`, `ZoneDistributionCalculator.kt`, `TelemetryHistogramCalculator.kt`, `Color.kt`, and `res/values*/strings.xml`.
* **Historical Origin & Commit Trace**:
  - Sprint 2026-40.5 (`ATT-1390`, commit `e3966ebf`): Initial introduction of cycling power 5-zone distribution in Aftermath, designed to match `REQ-UI-202` (Heart Rate 5-Zone Distribution).
  - Sprint 2026-40.8 (`ATT-1839`, `ATT-1845`): Added telemetry metric zone bands and fine-grained histograms with 5 zones.
  - Sprint 2026-40.11 (`ATT-2031`): Standardized threshold loading in `TelemetryZoneMath`.
* **Root Reason for Existing Formulation (The "Fence")**:
  Power was originally implemented with 5 zones solely to match Heart Rate (`REQ-UI-202`) and minimize architectural divergence across `ZoneDistributionModels.kt` and `ZonesSettingsActivity.kt`.
* **Preservation of Core Invariants**:
  - Heart Rate (`HR_RUN`, `HR_BIKE`) MUST remain strictly on the 5-zone scheme with 4 upper boundaries (`z1Max..z4Max`) and `TTColor.Zone1..Zone5`.
  - Non-destructive dynamic migration: Existing installations with configured power thresholds (1..4) must not crash or lose their settings; `PWR_BIKE_ZONE5_MAX` and `PWR_BIKE_ZONE6_MAX` must default dynamically and proportionally from the athlete's stored `ZONE4_MAX`.
  - Single-thread SQLite confinement and dynamic calculation: Raw workout samples in `WorkoutSamples` remain untouched; zone distributions are computed on-the-fly without SQLite schema changes.
  - Exporter compatibility: Raw telemetry exports (TCX, GPX, FIT, CSV, JSON) remain 100% untouched and compatible.
  - 100% 9-language localization parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

### 1.3 Functional & Architectural Requirements

1. **Color Token System & Theme Parity (`Color.kt` & `color.xml`)**:
   * The system SHALL define `TTColor.Zone6 = Color(0xFF4E342E)` (Dark Chestnut Brown) and `TTColor.Zone7 = Color(0xFF000000)` (Black).
   * In `res/values/color.xml`, the system SHALL declare `<color name="zone_6">#4E342E</color>` and `<color name="zone_7">#000000</color>`.
   * In dark / AMOLED theme, Zone 7 retains Black fill with white / contrast boundary.

2. **DataStore Storage & Dynamic Proportional Migration (`SettingsDataStore.kt`)**:
   * `ZoneType` SHALL support `zoneCount` (5 for `HR_RUN` and `HR_BIKE`, 7 for `PWR_BIKE`) and `boundaryCount` (4 for HR, 6 for Power).
   * `Zone` enum SHALL define `ZONE_1(1)`, `ZONE_2(2)`, `ZONE_3(3)`, `ZONE_4(4)`, `ZONE_5(5)`, `ZONE_6(6)`.
   * The system SHALL define integer preference keys `pwr_bike_zone5_max` and `pwr_bike_zone6_max`.
   * For existing installations upgrading with missing Z5/Z6 keys, the system SHALL calculate proportional defaults based on the athlete's existing `PWR_BIKE_ZONE4_MAX`:
     $$\text{Estimated FTP} = \max\left(50, \text{round}\left(\frac{\text{z4Max}}{1.05}\right)\right)$$
     $$\text{Default z5Max (VO2 Max, 120\% FTP)} = \max\left(\text{z4Max} + 10, \text{round}(\text{Estimated FTP} \times 1.20)\right)$$
     $$\text{Default z6Max (Anaerobic Capacity, 150\% FTP)} = \max\left(\text{z5Max} + 10, \text{round}(\text{Estimated FTP} \times 1.50)\right)$$
   * If `PWR_BIKE_ZONE4_MAX` is not configured either (fresh install), the system SHALL use canonical 300W FTP defaults:
     $\text{Z1}=150\text{W}, \text{Z2}=200\text{W}, \text{Z3}=250\text{W}, \text{Z4}=300\text{W}, \text{Z5}=350\text{W}, \text{Z6}=450\text{W}$.
   * The migration check and preference initialization SHALL execute inside a single atomic `dataStore.edit` transaction block.
   * Saving power zone thresholds SHALL validate strict monotonic ascending order: $0 < z1 < z2 < z3 < z4 < z5 < z6$.

3. **Synchronous Java Helper (`SettingsDataStoreJavaHelper.kt`)**:
   * `getZoneMax(context, zoneType, zoneIndex)` SHALL support `zoneIndex == 5` and `zoneIndex == 6` mapping to `getZone5MaxFlow` and `getZone6MaxFlow`.

4. **Domain Models & Telemetry Mathematics (`ZoneDistributionModels.kt`, `TelemetryZoneMath.kt`, `BatterySaverModel.kt`)**:
   * `PowerZoneThresholds` SHALL define 6 upper boundaries: `(z1Max, z2Max, z3Max, z4Max, z5Max, z6Max)`.
   * `TelemetryZoneMath.calculatePowerZoneBands()` SHALL compute 7 bands clamped to $[dataMin, dataMax]$ with colors `Zone1..Zone7` and labels `Z1..Z7`.
   * `TelemetryZoneMath.determinePowerZone()` SHALL map instantaneous power to 1-indexed zones `1..7`.
   * `TelemetryZoneMath.loadPowerThresholds()` SHALL query all 6 boundaries from `SettingsDataStoreJavaHelper` and validate $0 < z1 < z2 < z3 < z4 < z5 < z6$.
   * `BatterySaverModel.kt.calculateZoneIndex()` SHALL map power values to zones `1..7` when `zoneType == ZoneType.PWR_BIKE`.

5. **Real-Time Tracking Cockpit HUD (`TrackingViewModel.kt`)**:
   * `zoneColors` list in `TrackingViewModel` SHALL include all 7 colors (`Zone1..Zone7`).
   * `calculateZoneColor()` SHALL resolve power sensor readings across all 7 zones for background, left/right bar, and text color modes.

6. **Settings UI (`ZonesSettingsActivity.kt`)**:
   * `ZoneSettingsContent` SHALL dynamically render 5 boundary cards for Heart Rate (`HR_RUN`, `HR_BIKE`) and 7 boundary cards for Cycling Power (`PWR_BIKE`).
   * Linked min/max boundary editing SHALL maintain $z_n \ge z_{n-1} + 1$ across all 7 power cards.
   * The live theme-aware preview section SHALL display 7 interactive chips (`Z1..Z7`) when configuring Power and 5 chips (`Z1..Z5`) when configuring Heart Rate.

7. **Post-Workout Aftermath Analytics & Histograms (`ZoneDistributionCalculator.kt`, `TelemetryHistogramCalculator.kt`)**:
   * `ZoneDistributionCalculator.calculatePowerDistribution()` SHALL compute time and percentages across 7 zones (`Zone1..Zone7`) with labels from resources or programmatic fallback (`Z1..Z7`).
   * `TelemetryHistogramCalculator.calculatePowerHistogram()` SHALL map frequency bins across 7 zones with matching colors.
   * `ZoneDistributionColumnChart.kt` SHALL dynamically adapt to 7 equal-width columns without hardcoded size limits.

8. **9-Language Localization Parity**:
   * The system SHALL define `zone_6_label` and `zone_7_label` across all 9 supported application locales:
     - EN: `"Zone 6 (Anaerobic Capacity)"`, `"Zone 7 (Neuromuscular Power)"`
     - DE: `"Zone 6 (Anaerobe Kapazität)"`, `"Zone 7 (Neuromuskuläre Leistung)"`
     - ES: `"Zona 6 (Capacidad anaeróbica)"`, `"Zona 7 (Potencia neuromuscular)"`
     - FR: `"Zone 6 (Capacité anaérobie)"`, `"Zone 7 (Puissance neuromusculaire)"`
     - IT: `"Zona 6 (Capacità anaerobica)"`, `"Zona 7 (Potenza neuromuscolare)"`
     - JA: `"ゾーン6 (無酸素運動容量)"`, `"ゾーン7 (神経筋パワー)"`
     - NL: `"Zone 6 (Anaerobe capaciteit)"`, `"Zone 7 (Neuromusculair vermogen)"`
     - PL: `"Strefa 6 (Wydolność beztlenowa)"`, `"Strefa 7 (Moc nerwowo-mięśniowa)"`
     - PT: `"Zona 6 (Capacidade anaeróbica)"`, `"Zona 7 (Potência neuromuscular)"`

### 1.4 Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Zones Settings 5 vs 7 Dynamic Card Rendering)**:
  * *Given* an athlete opens `ZonesSettingsActivity`,
  * *When* selecting Heart Rate (Run or Bike),
  * *Then* exactly 5 zone cards and 5 preview chips (`Z1..Z5`) are rendered.
  * *When* selecting Cycling Power,
  * *Then* exactly 7 zone cards and 7 preview chips (`Z1..Z7`) are rendered with linked min/max editing.

* **Criterion 2 (Dynamic Proportional Migration)**:
  * *Given* an existing user database where `PWR_BIKE_ZONE4_MAX = 210` (corresponding to a 200W FTP athlete) and Z5/Z6 are unconfigured,
  * *When* `SettingsDataStore` loads power thresholds,
  * *Then* `PWR_BIKE_ZONE5_MAX` resolves to $240\text{W}$ ($120\%$ FTP) and `PWR_BIKE_ZONE6_MAX` resolves to $300\text{W}$ ($150\%$ FTP) within an atomic transaction, avoiding arbitrary 350W/450W defaults.

* **Criterion 3 (Cockpit HUD Zone 6 and Zone 7 Indication)**:
  * *Given* an active workout with a power meter connected,
  * *When* the athlete produces power in Zone 6 ($Z5_{\max} < P \le Z6_{\max}$),
  * *Then* the cockpit power tile resolves `TTColor.Zone6` (Dark Chestnut Brown).
  * *When* the athlete produces power in Zone 7 ($P > Z6_{\max}$),
  * *Then* the cockpit power tile resolves `TTColor.Zone7` (Black).

* **Criterion 4 (Aftermath Analytics 7-Zone Distribution)**:
  * *Given* a completed workout containing power meter telemetry,
  * *When* viewing the workout summary in Aftermath,
  * *Then* the Power Zone Distribution card renders 7 vertical columns with accurate time-in-zone durations and percentages.

* **Criterion 5 (Regression Prevention & Clean-Room Test Pass Rate)**:
  * *Given* all existing unit tests in `HeartRateZoneLocalizationTest`, `PowerZoneDistributionCalculatorTest`, `TelemetryZoneMathTest`, and `SensorFieldZoneDisplayTest`,
  * *When* running `./gradlew testDebugUnitTest`,
  * *Then* 100% of tests pass without regressions.

---

## 2. Test Specification (TST-UI-333)

### Test Case 1: `testSettingsDataStore_powerZonesDynamicProportionalMigration` (`TST-UI-333.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/settings/SettingsDataStorePowerZonesTest.kt`
* **Preconditions**: Test DataStore instance.
* **Action**:
  1. Initialize DataStore with existing custom `PWR_BIKE_ZONE4_MAX = 210` (no Z5/Z6 set).
  2. Query `getZone5MaxFlow` and `getZone6MaxFlow`.
* **Expected Result**: Assert `z5Max == 240` and `z6Max == 300` derived proportionally from the 200W FTP baseline.

### Test Case 2: `testSettingsDataStore_monotonicValidationAndWorstCaseBounds` (`TST-UI-333.2`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/settings/SettingsDataStorePowerZonesTest.kt`
* **Preconditions**: DataStore with edge-case baselines: ultra-low FTP (60W) and ultra-high FTP (500W).
* **Action**: Verify threshold calculation and save validation.
* **Expected Result**: Assert $0 < z1 < z2 < z3 < z4 < z5 < z6$ strictly holds for all edge cases with zero overlap or negative values.

### Test Case 3: `testTelemetryZoneMath_calculatePowerZoneBands7Zones` (`TST-UI-333.3`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryZoneMathTest.kt`
* **Preconditions**: `PowerZoneThresholds(150, 200, 250, 300, 350, 450)` and data range `[0.0, 600.0]`.
* **Action**: Call `calculatePowerZoneBands`.
* **Expected Result**: Assert returned list contains 7 bands with zone indices `1..7`, colors `Zone1..Zone7`, and non-overlapping clamped intervals.

### Test Case 4: `testPowerZoneDistributionCalculator_7ZonesClassification` (`TST-UI-333.4`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/PowerZoneDistributionCalculatorTest.kt`
* **Preconditions**: `PowerZoneThresholds(150, 200, 250, 300, 350, 450)` and samples spanning all 7 zones (100W, 180W, 230W, 280W, 330W, 400W, 550W).
* **Action**: Call `calculatePowerDistribution`.
* **Expected Result**: Assert `result.entries.size == 7`, every entry has `durationSec > 0`, and `zoneIndex == 1..7`.

### Test Case 5: `testTelemetryHistogramCalculator_7ZonesPowerHistogram` (`TST-UI-333.5`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/PowerZoneDistributionCalculatorTest.kt`
* **Preconditions**: Samples up to 600W.
* **Action**: Call `calculatePowerHistogram`.
* **Expected Result**: Assert bins above 350W map to Zone 6 (`TTColor.Zone6`) and bins above 450W map to Zone 7 (`TTColor.Zone7`).

### Test Case 6: `testTrackingViewModel_powerZoneColorsResolution7Zones` (`TST-UI-333.6`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/SensorFieldZoneDisplayTest.kt`
* **Preconditions**: `SensorFieldState` with power values in Z6 (400W) and Z7 (600W).
* **Action**: Verify zone color assignment.
* **Expected Result**: Assert Z6 resolves `TTColor.Zone6` and Z7 resolves `TTColor.Zone7`.

### Test Case 7: `testPowerZoneLocalization_all9LocalesParity` (`TST-UI-333.7`)
* **Scope**: Localization Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/PowerZoneLocalizationTest.kt`
* **Preconditions**: 9 locale directories (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
* **Action**: Parse `strings.xml` in each directory.
* **Expected Result**: Assert `zone_6_label` and `zone_7_label` exist and are non-empty in all 9 directories.

### Test Case 8: Clean-Room Regression Suite (`TST-UI-333.8`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite with zero regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-333.1` | Unit | `SettingsDataStore.getZone5MaxFlow`, `getZone6MaxFlow` | `REQ-UI-370.2` | Specified |
| `TST-UI-333.2` | Unit | `SettingsDataStore.savePowerZoneThresholds` | `REQ-UI-370.2` | Specified |
| `TST-UI-333.3` | Unit | `TelemetryZoneMath.calculatePowerZoneBands` | `REQ-UI-370.4` | Specified |
| `TST-UI-333.4` | Unit | `ZoneDistributionCalculator.calculatePowerDistribution` | `REQ-UI-370.7` | Specified |
| `TST-UI-333.5` | Unit | `TelemetryHistogramCalculator.calculatePowerHistogram` | `REQ-UI-370.7` | Specified |
| `TST-UI-333.6` | Unit | `TrackingViewModel.calculateZoneColor` | `REQ-UI-370.5` | Specified |
| `TST-UI-333.7` | Localization | `PowerZoneLocalizationTest` (all 9 locales) | `REQ-UI-370.8` | Specified |
| `TST-UI-333.8` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
