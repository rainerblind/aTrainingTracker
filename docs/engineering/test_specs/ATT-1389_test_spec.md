# Stage 2 Requirement & Test Specification: ATT-1389

**Ticket**: [ATT-1389](https://atrainingtracker.atlassian.net/browse/ATT-1389)  
**Sub-task**: [ATT-1710](https://atrainingtracker.atlassian.net/browse/ATT-1710) (`[Spec]`)  
**Parent Epic**: [ATT-111](https://atrainingtracker.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.5`  
**Branch**: `feature/ATT-1389`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-30  

---

## 1. Formal Requirements Specification

### `REQ-UI-202`: Aftermath Heart Rate 5-Zone Distribution Bar (Time-in-Zones) Architecture

The system SHALL provide a compact, visually intuitive 5-zone Heart Rate distribution bar ("Time-in-Zones") within the workout details / aftermath inspection view ([TrackOnMapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt) / [MapDetailLayout.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt)) (ATT-1389):

1. **Domain Model (`ZoneDistributionModels.kt`)**:
   - The system SHALL define `ZoneTimeEntry(val zoneIndex: Int, val zoneLabelResId: Int, val durationSec: Long, val percentage: Float, val color: Color)`.
   - The system SHALL define `ZoneDistributionData(val totalActiveTimeSec: Long, val entries: List<ZoneTimeEntry>)` where `entries` contains exactly 5 elements representing Zones 1 through 5.
   - The system SHALL define `HeartRateZoneThresholds(val z1Max: Int, val z2Max: Int, val z3Max: Int, val z4Max: Int)`.

2. **Pure Mathematical Engine (`ZoneDistributionCalculator.kt`)**:
   - `ZoneDistributionCalculator.calculateHeartRateDistribution(samples, thresholds)` SHALL assign each valid HR sample to Zones 1..5:
     - $\text{HR} \le Z1_{\max} \implies \text{Zone 1}$
     - $Z1_{\max} < \text{HR} \le Z2_{\max} \implies \text{Zone 2}$
     - $Z2_{\max} < \text{HR} \le Z3_{\max} \implies \text{Zone 3}$
     - $Z3_{\max} < \text{HR} \le Z4_{\max} \implies \text{Zone 4}$
     - $\text{HR} > Z4_{\max} \implies \text{Zone 5}$
   - Active duration spent in each zone SHALL be accumulated from consecutive sample timestamps $\Delta t = (t_{i+1} - t_i)$.
   - $\Delta t$ SHALL be clamped to $[0\text{s}, 5\text{s}]$ to eliminate runaway accumulation during tracking pauses or GPS dropouts.
   - If total active HR duration is 0 or no valid HR samples exist, the calculator SHALL return `null`.

3. **Full-Fidelity SQLite Telemetry Extraction (`WorkoutRepository.kt`)**:
   - `WorkoutRepository.getHeartRateZoneDistribution(workoutId, bSportType)` SHALL execute on `Dispatchers.IO`.
   - The repository SHALL query athlete HR zone thresholds from `SettingsDataStore`:
     - If `bSportType == BSportType.BIKE` $\implies$ `ZoneType.HR_BIKE`.
     - Otherwise $\implies$ `ZoneType.HR_RUN`.
   - The repository SHALL query the workout samples table for `TIME_ACTIVE`, `TIME_TOTAL`, and `HR`.
   - If the `HR` column does not exist or the table is empty, the repository SHALL cleanly return `null`.

4. **ViewModel State Flow (`TrackOnMapAftermathViewModel.kt`)**:
   - `AftermathMapUIState` SHALL include `val hrZoneDistribution: ZoneDistributionData? = null`.
   - In `loadAftermathData(workoutData)`, the ViewModel SHALL asynchronously query `getHeartRateZoneDistribution` and emit to `_uiState`.

5. **Visual UI Component (`HeartRateZoneDistributionCard.kt`)**:
   - The system SHALL render a rounded horizontal stacked bar with segments filled with `TTColor.Zone1` through `TTColor.Zone5`.
   - The width of each segment SHALL be proportional to its time percentage ($P_z$). Segments with 0% SHALL have zero width.
   - Below the bar, a compact legend SHALL display Zone indicators (Z1..Z5 with colored dot), formatted time (`m:ss` or `h:mm:ss`), and percentage (`X%`).
   - The card SHALL display a compact title row with a heart icon and localized text `R.string.aftermath_hr_zones_title`.

6. **Slotted MapDetailLayout Integration (`MapDetailLayout.kt`, `TrackOnMapScreen.kt`)**:
   - `MapDetailLayout` SHALL provide an optional `analyticsContent: @Composable ColumnScope.() -> Unit = {}` slot directly integrated with the layout.
   - If `hrZoneDistribution == null`, the component SHALL render nothing and consume 0 vertical space.
   - For stationary / indoor workouts (`showMap == false`, `showElevationProfile == false`), `analyticsContent` SHALL remain fully visible.

7. **100% 9-Language Localization Parity**:
   - `aftermath_hr_zones_title` SHALL be defined across all 9 supported locales (en, de, es, fr, it, ja, nl, pl, pt).

---

## 2. Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**: Net-new requirement (`REQ-UI-202`) fulfilling Pillar 1 of Epic [ATT-111](https://atrainingtracker.atlassian.net/browse/ATT-111).
2. **Historical Origin & Commit Trace**: Ticket `ATT-1389`, Sprint `2026-40.5`.
3. **Root Reason for Existing Formulation**: Previously, Aftermath only presented scalar heart rate extrema (Average and Maximum HR in `WorkoutExtrema`), providing zero visibility into physiological training compliance or time-in-zones breakdown.
4. **Preservation of Core Invariants**:
   - `MapDetailLayout` reusability for Routes and Segments is preserved via default no-op lambdas.
   - Existing zone configuration in `SettingsDataStore` and zone colors in `TTColor` remain unchanged.
   - Single-thread SQLite concurrency on `Dispatchers.IO` is strictly maintained.

---

## 3. Detailed Acceptance Criteria (Given-When-Then)

### Scenario 1: Clean Zone 2 Base Workout
- **Given** an athlete with running HR thresholds (Z1: 130, Z2: 150, Z3: 165, Z4: 180 bpm) records a 60-minute run with 50 minutes in Zone 2 and 10 minutes in Zone 1.
- **When** opening the workout in Aftermath,
- **Then** the Heart Rate Zone Distribution Card SHALL display a stacked bar showing ~83% in Zone 2 (Green) and ~17% in Zone 1 (Chartreuse), with legend reflecting "Z1: 10:00 (17%)" and "Z2: 50:00 (83%)".

### Scenario 2: Workout Without Heart Rate Telemetry
- **Given** a workout recorded without a heart rate sensor (or all HR values null/zero).
- **When** opening the workout in Aftermath,
- **Then** `hrZoneDistribution` SHALL evaluate to `null` and no Heart Rate Zone card or empty placeholder SHALL be displayed.

### Scenario 3: Indoor Trainer Workout (No GPS)
- **Given** an indoor bike workout recorded with heart rate (`trainer = true`, `showMap = false`, `showElevationProfile = false`).
- **When** viewing the workout in Aftermath,
- **Then** the Heart Rate Zone Distribution Card SHALL render prominently below the workout header using `ZoneType.HR_BIKE` thresholds.

---

## 4. Test Specifications (`TST-UI-156`)

### Test Case 1: `ZoneDistributionCalculator` Zone Classification (`[TST-UI-156.1]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/ZoneDistributionCalculatorTest.kt`
* **Preconditions**: Defined `HeartRateZoneThresholds(z1Max = 130, z2Max = 150, z3Max = 165, z4Max = 180)`.
* **Action**:
  - Evaluate heart rates at 120 bpm, 130 bpm (Z1 boundary).
  - Evaluate heart rates at 131 bpm, 150 bpm (Z2 boundary).
  - Evaluate heart rates at 151 bpm, 165 bpm (Z3 boundary).
  - Evaluate heart rates at 166 bpm, 180 bpm (Z4 boundary).
  - Evaluate heart rates at 181 bpm, 200 bpm (Z5).
* **Expected Result**: Exact zone assignment matching mathematical intervals: Z1 $\le 130$, Z2 $(130, 150]$, Z3 $(150, 165]$, Z4 $(165, 180]$, Z5 $> 180$.

### Test Case 2: Time Accumulation & Pause Clamping (`[TST-UI-156.2]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/ZoneDistributionCalculatorTest.kt`
* **Action**:
  - Provide a stream of 1Hz samples in Zone 2 for 60 seconds $\implies 60$ seconds.
  - Provide a 10-minute pause gap where timestamp jumps by 600s $\implies \Delta t$ clamped to $\le 5$s.
  - Verify total duration equals sum of all 5 zone durations.
  - Verify percentages sum to $100.0\% \pm 0.5\%$.
* **Expected Result**: Accurate accumulation without runaway pause drift; percentages sum to 100%.

### Test Case 3: Graceful Null / Zero Telemetry Handling (`[TST-UI-156.3]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/ZoneDistributionCalculatorTest.kt`
* **Action**:
  - Pass empty sample list $\implies$ assert null.
  - Pass samples with only null or 0 HR $\implies$ assert null.
* **Expected Result**: Calculator cleanly returns null without throwing exceptions.

### Test Case 4: `WorkoutRepository` Sport-Specific Zone Selection (`[TST-UI-156.4]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/HeartRateZoneRepositoryTest.kt`
* **Action**:
  - Verify `BSportType.BIKE` queries `ZoneType.HR_BIKE`.
  - Verify `BSportType.RUN` queries `ZoneType.HR_RUN`.
* **Expected Result**: Accurate sport type zone threshold binding.

### Test Case 5: 9-Language Localization Audit (`[TST-UI-156.5]`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/HeartRateZoneLocalizationTest.kt`
* **Action**:
  - Verify `aftermath_hr_zones_title` exists across `values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`.
* **Expected Result**: 100% presence and non-blank values across all 9 locales.

### Test Case 6: Clean-Room Full Suite Regression (`[TST-UI-156.6]`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: 100% pass rate across all project modules with 0 regressions.

---

## 5. Traceability Matrix

| Requirement | Test Specification | Verification Method | Deliverable |
| :--- | :--- | :--- | :--- |
| `REQ-UI-202.1` | `TST-UI-156.1` | Unit Test | `ZoneDistributionModels.kt` |
| `REQ-UI-202.2` | `TST-UI-156.2`, `TST-UI-156.3` | Unit Test | `ZoneDistributionCalculator.kt` |
| `REQ-UI-202.3` | `TST-UI-156.4` | Unit Test | `WorkoutRepository.kt` |
| `REQ-UI-202.4` | `TST-UI-156.4` | Unit Test | `TrackOnMapAftermathViewModel.kt` |
| `REQ-UI-202.5` | `TST-UI-156.1` | Unit Test | `HeartRateZoneDistributionCard.kt` |
| `REQ-UI-202.6` | `TST-UI-156.5` | Localization Audit | `strings.xml` (all 9 locales) |
| `REQ-UI-202.7` | `TST-UI-156.6` | Clean-Room Suite | `./gradlew testDebugUnitTest` |
