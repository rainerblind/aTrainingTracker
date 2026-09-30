# Stage 2 Requirement & Test Specification: ATT-1392

**Ticket**: [ATT-1392](https://atrainingtracker.atlassian.net/browse/ATT-1392)  
**Sub-task**: [ATT-1722](https://atrainingtracker.atlassian.net/browse/ATT-1722) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-111](https://atrainingtracker.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.5`  
**Branch**: `feature/ATT-1392`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-30  

---

## 1. Formal Requirements Specification

### `REQ-UI-204`: Aftermath Compact Lap & Interval Split Chart Architecture

The system SHALL provide a compact, visually intuitive Lap & Interval Split Bar Chart within the workout details / aftermath inspection views ([TrackOnMapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt) / [MapDetailLayout.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt) and [WorkoutLaps.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutlaps/WorkoutLaps.kt)) for sessions containing at least 2 recorded or imported laps (ATT-1392):

1. **Domain Model (`LapSplitModels.kt`)**:
   - The system SHALL define `LapSplitItem`:
     - `val lapNr: Long`: Sequential lap number.
     - `val displayName: String`: Resolved lap name (e.g. "Lap 1" or custom name).
     - `val durationSec: Int`: Lap duration in seconds.
     - `val distanceMeters: Double`: Lap distance in meters.
     - `val speedMps: Double`: Lap average speed in m/s.
     - `val formattedPaceOrSpeed: String`: Pre-formatted pace (`m:ss /km`) or speed (`X.X km/h`).
     - `val relativeRatio: Float`: Proportional ratio in $[0.25f, 1.0f]$ relative to speed range.
     - `val isFastest: Boolean`: Flag indicating the fastest split in the session.
     - `val isSlowest: Boolean`: Flag indicating the slowest split in the session.
     - `val color: Color`: Intensity tier color from `TTColor.Zone1`..`TTColor.Zone5`.
   - The system SHALL define `LapSplitChartData`:
     - `val splits: List<LapSplitItem>`: Ordered list of lap split items.
     - `val bSportType: BSportType`: Sport classification.
     - `val fastestLapNr: Long?`: Lap number of fastest split.
     - `val slowestLapNr: Long?`: Lap number of slowest split.

2. **Pure Mathematical Engine (`LapSplitCalculator.kt`)**:
   - `LapSplitCalculator.calculateSplitData(laps, bSportType, formatters)` SHALL calculate split metrics:
     - If `laps.size < 2`, the calculator SHALL return `null`.
     - Identifies maximum speed $v_{\max}$ and minimum speed $v_{\min}$ from valid laps ($v > 0.001$ m/s).
     - If $v_{\max} > v_{\min}$, the relative ratio SHALL be scaled:
       $$r_i = 0.25f + 0.75f \times \frac{v_i - v_{\min}}{v_{\max} - v_{\min}}$$
       ensuring even the slowest split has a visible base bar width ($25\%$). If all laps share identical speed, $r_i = 1.0f$.
     - Maps $r_i$ to 5 intensity tiers:
       - $r_i < 0.40 \implies \text{TTColor.Zone1}$
       - $0.40 \le r_i < 0.55 \implies \text{TTColor.Zone2}$
       - $0.55 \le r_i < 0.70 \implies \text{TTColor.Zone3}$
       - $0.70 \le r_i < 0.85 \implies \text{TTColor.Zone4}$
       - $r_i \ge 0.85 \implies \text{TTColor.Zone5}$
     - If `bSportType == BSportType.RUN`, the formatted metric SHALL display pace (`m:ss`). If cycling or other sport, it SHALL display speed (`X.X`).

3. **Visual UI Components (`LapSplitChart.kt`, `LapSplitChartCard.kt`)**:
   - `LapSplitChart`: Renders a responsive list of horizontal split bars with:
     - Lap indicator badge ("L1", "L2", ...) with primary accent.
     - Filled horizontal bar proportional to $r_i$ with rounded corners (`6.dp`) filled with intensity `color`.
     - Distance and formatted pace/speed text.
     - Visual badge indicator for fastest (🐇) and slowest (🦔) laps when speeds differ.
   - `LapSplitChartCard`: Material 3 elevated card with header row:
     - Icon: `R.drawable.ic_lap_laps`.
     - Localized Title: `R.string.aftermath_laps_splits_title`.
     - Summary: Lap count and total session distance.
     - Card body embedding `LapSplitChart`.

4. **Interactive Map Track Correlation (`TrackOnMapScreen.kt`)**:
   - In `TrackOnMapScreen`, `LapSplitChartCard` SHALL be placed in `analyticsContent`.
   - Tapping any lap bar SHALL toggle its selection state (`selectedLapNr`).
   - When a lap is selected (`selectedLapNr != null`):
     - The system SHALL calculate the lap's distance range via `LapSegmentUtils.calculateLapDistanceRange`.
     - The system SHALL slice the track coordinates via `LapSegmentUtils.sliceLapSegment`.
     - The sliced lap track SHALL be rendered as a prominent highlighted polyline (`width = 10f`, `color = MaterialTheme.colorScheme.primary`, `zIndex = 20f`) with start/stop markers on `ATrainingTrackerMap`.
   - Tapping the selected lap a second time SHALL clear selection and remove the highlight.

5. **Workout Summary Integration (`WorkoutLaps.kt`)**:
   - `WorkoutLaps` SHALL embed `LapSplitChart` above the numeric table rows when `laps.size >= 2`.

6. **100% 9-Language Localization Parity**:
   - String resource `aftermath_laps_splits_title` SHALL be defined across all 9 application locales without placeholders.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**: Net-new requirement (`REQ-UI-204`) completing the Visual Analytics pillar of Epic [ATT-111](https://atrainingtracker.atlassian.net/browse/ATT-111).
2. **Historical Origin & Commit Trace**: Ticket `ATT-1392`, Sprint `2026-40.5`.
3. **Root Reason for Existing Formulation**: Lap metrics were historically presented exclusively as tabular numbers in `WorkoutLaps.kt`, lacking graphical visual comparison and spatial map track correlation.
4. **Preservation of Core Invariants**:
   - Existing `WorkoutLaps` table and `LapEditBottomSheet` contracts remain 100% intact.
   - Workouts with $< 2$ laps evaluate to `null` and consume zero vertical space in `analyticsContent`.
   - Single-thread SQLite confinement and in-memory coordinate slicing are preserved.

---

## 3. Detailed Acceptance Criteria (Given-When-Then)

### Scenario 1: Multi-Lap Workout in Aftermath
- **Given** an athlete records or imports an activity containing 5 laps with varying speeds.
- **When** opening the workout in Aftermath (`TrackOnMapScreen`),
- **Then** `LapSplitChartCard` SHALL render in `analyticsContent` below the zone distribution cards, displaying 5 horizontal proportional bars color-coded by intensity, with fastest and slowest badges.

### Scenario 2: Interactive Map Highlighting
- **Given** `LapSplitChartCard` is displayed in `TrackOnMapScreen`.
- **When** the athlete taps Lap 3,
- **Then** the Lap 3 bar SHALL highlight with a primary border, and Lap 3's polyline segment SHALL render prominently on the map with start/stop markers.
- **When** the athlete taps Lap 3 again,
- **Then** the highlight SHALL be cleared, returning the map to standard full-course display.

### Scenario 3: Single-Lap or Lap-Free Workout
- **Given** a workout recorded without manual or automatic laps ($N < 2$).
- **When** viewing the workout in Aftermath,
- **Then** `LapSplitChartCard` SHALL evaluate to `null` and consume zero vertical space.

---

## 4. Test Specifications (`TST-UI-158`)

### Test Case 1: `LapSplitCalculator` Relative Scaling & Normalization (`[TST-UI-158.1]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/splits/LapSplitCalculatorTest.kt`
* **Action**:
  - Provide 4 laps with speeds 3.0 m/s, 4.0 m/s, 5.0 m/s, and 6.0 m/s.
  - Verify relative ratios: slowest (3.0 m/s) has ratio 0.25f; fastest (6.0 m/s) has ratio 1.0f; intermediate laps scale linearly.
* **Expected Result**: Ratios scale monotonically between 0.25f and 1.0f.

### Test Case 2: Fastest & Slowest Identification (`[TST-UI-158.2]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/splits/LapSplitCalculatorTest.kt`
* **Action**:
  - Verify `isFastest` is true only for lap 4 (6.0 m/s).
  - Verify `isSlowest` is true only for lap 1 (3.0 m/s).
  - Verify uniform speeds case: when all laps have identical speed, no lap is marked fastest/slowest and all have ratio 1.0f.
* **Expected Result**: Exact boundary and uniform speed handling.

### Test Case 3: Intensity Tier Color Mapping (`[TST-UI-158.3]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/splits/LapSplitCalculatorTest.kt`
* **Action**:
  - Verify colors assigned across 5 tiers: Tier 1 (`TTColor.Zone1`), Tier 2 (`TTColor.Zone2`), Tier 3 (`TTColor.Zone3`), Tier 4 (`TTColor.Zone4`), Tier 5 (`TTColor.Zone5`).
* **Expected Result**: Correct color matching per tier.

### Test Case 4: Graceful Null for Ineligible Workouts (`[TST-UI-158.4]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/splits/LapSplitCalculatorTest.kt`
* **Action**:
  - Test with empty list $\implies$ assert `null`.
  - Test with single lap $\implies$ assert `null`.
* **Expected Result**: Calculator returns null safely without exceptions.

### Test Case 5: 9-Language Localization Parity (`[TST-UI-158.5]`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/splits/LapSplitLocalizationTest.kt`
* **Action**:
  - Verify `aftermath_laps_splits_title` exists across `values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`.
* **Expected Result**: 100% presence and non-blank values across all 9 locales.

### Test Case 6: Clean-Room Full Suite Regression (`[TST-UI-158.6]`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: 100% pass rate across all project modules with 0 regressions.

---

## 5. Traceability Matrix

| Requirement | Test Specification | Verification Method | Deliverable |
| :--- | :--- | :--- | :--- |
| `REQ-UI-204.1` | `TST-UI-158.1` | Unit Test | `LapSplitModels.kt` |
| `REQ-UI-204.2` | `TST-UI-158.1`, `TST-UI-158.2`, `TST-UI-158.3`, `TST-UI-158.4` | Unit Test | `LapSplitCalculator.kt` |
| `REQ-UI-204.3` | `TST-UI-158.1` | Unit Test | `LapSplitChart.kt`, `LapSplitChartCard.kt` |
| `REQ-UI-204.4` | `TST-UI-158.2` | Unit Test | `TrackOnMapScreen.kt` |
| `REQ-UI-204.5` | `TST-UI-158.1` | Unit Test | `WorkoutLaps.kt` |
| `REQ-UI-204.6` | `TST-UI-158.5` | Localization Audit | `strings.xml` (all 9 locales) |
| `REQ-UI-204.7` | `TST-UI-158.6` | Clean-Room Suite | `./gradlew testDebugUnitTest` |
