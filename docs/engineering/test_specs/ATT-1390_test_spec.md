# Stage 2 Requirement & Test Specification: ATT-1390

**Ticket**: [ATT-1390](https://atrainingtracker.atlassian.net/browse/ATT-1390)  
**Sub-task**: [ATT-1717](https://atrainingtracker.atlassian.net/browse/ATT-1717) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-111](https://atrainingtracker.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.5`  
**Branch**: `feature/ATT-1390`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-30  

---

## 1. Formal Requirements Specification

### `REQ-UI-203`: Aftermath Cycling Power 5-Zone Distribution Bar (Time-in-Zones) Architecture

The system SHALL provide a compact, visually intuitive 5-zone Cycling Power distribution bar ("Time-in-Zones") within the workout details / aftermath inspection view ([TrackOnMapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt) / [MapDetailLayout.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt)) for sessions recorded with power meter telemetry (ATT-1390):

1. **Domain Model (`ZoneDistributionModels.kt`)**:
   - The system SHALL define `PowerZoneThresholds(val z1Max: Int, val z2Max: Int, val z3Max: Int, val z4Max: Int)`.
   - The system SHALL reuse `ZoneTimeEntry` and `ZoneDistributionData` representing Zones 1 through 5.

2. **Pure Mathematical Engine (`ZoneDistributionCalculator.kt`)**:
   - `ZoneDistributionCalculator.calculatePowerDistribution(samples, thresholds)` SHALL assign each valid power sample ($P > 0$) to Zones 1..5:
     - $P \le Z1_{\max} \implies \text{Zone 1}$
     - $Z1_{\max} < P \le Z2_{\max} \implies \text{Zone 2}$
     - $Z2_{\max} < P \le Z3_{\max} \implies \text{Zone 3}$
     - $Z3_{\max} < P \le Z4_{\max} \implies \text{Zone 4}$
     - $P > Z4_{\max} \implies \text{Zone 5}$
   - Active duration spent in each zone SHALL be accumulated from consecutive sample timestamps $\Delta t = (t_{i+1} - t_i)$.
   - $\Delta t$ SHALL be clamped to $[0\text{s}, 5\text{s}]$ to eliminate runaway accumulation during coasting pauses or dropouts.
   - If total active power duration is 0 or no valid power samples exist, the calculator SHALL return `null`.

3. **Full-Fidelity SQLite Telemetry Extraction (`WorkoutRepository.kt`)**:
   - `WorkoutRepository.getPowerZoneDistribution(workoutId)` SHALL execute on `Dispatchers.IO`.
   - The repository SHALL query athlete power zone thresholds from `SettingsDataStore`:
     - Keys `pwr_bike_zone1_max` through `pwr_bike_zone4_max` via `SettingsDataStoreJavaHelper.getZoneMax(application, ZoneType.PWR_BIKE, 1..4)`.
   - The repository SHALL query the workout samples table for `TIME_ACTIVE`, `TIME_TOTAL`, and `POWER`.
   - If the `POWER` column does not exist or the table contains 0 power samples, the repository SHALL cleanly return `null`.

4. **ViewModel State Flow (`TrackOnMapAftermathViewModel.kt`)**:
   - `AftermathMapUIState` SHALL include `val powerZoneDistribution: ZoneDistributionData? = null`.
   - In `loadAftermathData(workoutData)`, the ViewModel SHALL asynchronously query `getPowerZoneDistribution` and emit to `_uiState`.

5. **Visual UI Component (`PowerZoneDistributionCard.kt`)**:
   - The system SHALL render a rounded horizontal stacked bar with segments filled with `TTColor.Zone1` through `TTColor.Zone5`.
   - The width of each segment SHALL be proportional to its time percentage. Segments with 0% SHALL have zero width.
   - Below the bar, a compact legend SHALL display Zone indicators (Z1..Z5 with colored dot), formatted time (`m:ss` or `h:mm:ss`), and percentage (`X%`).
   - The card SHALL display a compact title row with a power icon (`R.drawable.ic_power`) and localized text `R.string.aftermath_power_zones_title`.

6. **Slotted MapDetailLayout Integration (`MapDetailLayout.kt`, `TrackOnMapScreen.kt`)**:
   - In `TrackOnMapScreen.kt`, `powerZoneDistribution` SHALL be passed to `analyticsContent`.
   - If both `hrZoneDistribution` and `powerZoneDistribution` are present, both cards SHALL render sequentially with standard spacing (`8.dp`).
   - If `powerZoneDistribution == null`, the component SHALL render nothing and consume 0 vertical space.

7. **100% 9-Language Localization Parity**:
   - `aftermath_power_zones_title` SHALL be defined across all 9 supported locales (en, de, es, fr, it, ja, nl, pl, pt).

---

## 2. Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**: Net-new requirement (`REQ-UI-203`) fulfilling Pillar 2 of Epic [ATT-111](https://atrainingtracker.atlassian.net/browse/ATT-111).
2. **Historical Origin & Commit Trace**: Ticket `ATT-1390`, Sprint `2026-40.5`.
3. **Root Reason for Existing Formulation**: Aftermath inspection previously presented scalar power extrema (Average and Max Power in `WorkoutExtrema`), providing zero visibility into power zone distribution or interval work.
4. **Preservation of Core Invariants**:
   - `analyticsContent` slot in `MapDetailLayout` established in ATT-1389 remains the single extension point.
   - Existing power zone configuration in `SettingsDataStore` and zone colors in `TTColor` remain unchanged.
   - Single-thread SQLite concurrency on `Dispatchers.IO` is strictly maintained.

---

## 3. Detailed Acceptance Criteria (Given-When-Then)

### Scenario 1: Cycling Workout With Power Telemetry
- **Given** an athlete with bike power thresholds (Z1: 150W, Z2: 200W, Z3: 250W, Z4: 300W) records a cycling session with power meter data.
- **When** opening the workout in Aftermath,
- **Then** the Power Zone Distribution Card SHALL render below the elevation profile (or below HR zones card if both exist), showing proportional stacked bars and time/percentage breakdown across Z1..Z5.

### Scenario 2: Workout Without Power Telemetry
- **Given** a workout recorded without a power meter (e.g. running workout or bike ride without power sensor).
- **When** opening the workout in Aftermath,
- **Then** `powerZoneDistribution` SHALL evaluate to `null` and no Power Zone card or placeholder SHALL be displayed.

### Scenario 3: Simultaneous Heart Rate and Power Telemetry
- **Given** a workout recorded with both Heart Rate and Power sensors.
- **When** viewing the workout in Aftermath,
- **Then** both `HeartRateZoneDistributionCard` and `PowerZoneDistributionCard` SHALL render in `analyticsContent`, neatly stacked with 8.dp separation.

---

## 4. Test Specifications (`TST-UI-157`)

### Test Case 1: `ZoneDistributionCalculator` Power Classification (`[TST-UI-157.1]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/PowerZoneDistributionCalculatorTest.kt`
* **Preconditions**: Defined `PowerZoneThresholds(z1Max = 150, z2Max = 200, z3Max = 250, z4Max = 300)`.
* **Action**:
  - Evaluate power at 140W, 150W (Z1 boundary).
  - Evaluate power at 151W, 200W (Z2 boundary).
  - Evaluate power at 201W, 250W (Z3 boundary).
  - Evaluate power at 251W, 300W (Z4 boundary).
  - Evaluate power at 301W, 450W (Z5).
* **Expected Result**: Exact zone assignment matching mathematical intervals: Z1 $\le 150$, Z2 $(150, 200]$, Z3 $(200, 250]$, Z4 $(250, 300]$, Z5 $> 300$.

### Test Case 2: Time Accumulation & Pause Clamping (`[TST-UI-157.2]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/PowerZoneDistributionCalculatorTest.kt`
* **Action**:
  - Stream 1Hz samples in Zone 3 for 60 seconds $\implies 60$ seconds.
  - Inject 5-minute pause gap $\implies \Delta t$ clamped to $\le 5$s.
  - Verify total duration equals sum of all 5 zone durations.
  - Verify percentages sum to $100.0\% \pm 0.5\%$.
* **Expected Result**: Accurate accumulation without runaway pause drift; percentages sum to 100%.

### Test Case 3: Graceful Null / Zero Telemetry Handling (`[TST-UI-157.3]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/PowerZoneDistributionCalculatorTest.kt`
* **Action**:
  - Pass empty sample list $\implies$ assert null.
  - Pass samples with only 0 Watts $\implies$ assert null.
* **Expected Result**: Calculator cleanly returns null without throwing exceptions.

### Test Case 4: 9-Language Localization Audit (`[TST-UI-157.4]`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/PowerZoneLocalizationTest.kt`
* **Action**:
  - Verify `aftermath_power_zones_title` exists across `values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`.
* **Expected Result**: 100% presence and non-blank values across all 9 locales.

### Test Case 5: Clean-Room Full Suite Regression (`[TST-UI-157.5]`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: 100% pass rate across all project modules with 0 regressions.

---

## 5. Traceability Matrix

| Requirement | Test Specification | Verification Method | Deliverable |
| :--- | :--- | :--- | :--- |
| `REQ-UI-203.1` | `TST-UI-157.1` | Unit Test | `ZoneDistributionModels.kt` |
| `REQ-UI-203.2` | `TST-UI-157.2`, `TST-UI-157.3` | Unit Test | `ZoneDistributionCalculator.kt` |
| `REQ-UI-203.3` | `TST-UI-157.1` | Unit Test | `WorkoutRepository.kt` |
| `REQ-UI-203.4` | `TST-UI-157.1` | Unit Test | `TrackOnMapAftermathViewModel.kt` |
| `REQ-UI-203.5` | `TST-UI-157.1` | Unit Test | `PowerZoneDistributionCard.kt` |
| `REQ-UI-203.6` | `TST-UI-157.4` | Localization Audit | `strings.xml` (all 9 locales) |
| `REQ-UI-203.7` | `TST-UI-157.5` | Clean-Room Suite | `./gradlew testDebugUnitTest` |
