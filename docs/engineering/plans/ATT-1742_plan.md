# Stage 3: Implementation Plan - ATT-1742: [Aftermath/Splits] High-Aesthetic Redesign of Lap & Interval Split Visualizer

**Ticket**: [[ATT-1742]](https://rainerblind.atlassian.net/browse/ATT-1742)  
**Sub-task**: [[ATT-1824]](https://rainerblind.atlassian.net/browse/ATT-1824) (`[Impl-Plan]`)  
**Parent Epic**: [[ATT-111]](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.7`  
**Requirement Mapping**: `REQ-UI-204`  
**Test Mapping**: `TST-UI-158`  
**Branch**: `feature/ATT-1742`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Problem Description & Background

In sprint review 2026-40.5 (ATT-1392), the user rejected the initial split bar visualization ("looks really bad") due to cartoon animal emojis (`🐇`, `🦔`), clashing multi-colored rainbow bars, and visual clutter. After reverting the visual card in sprint 2026-40.6 (ATT-1741), ATT-1742 now delivers the high-aesthetic Material 3 redesign, transforming lap and interval comparison into a sleek, professional athletic visualizer.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-204` (*Aftermath: High-Aesthetic Lap & Interval Split Visualizer Architecture*)
* **Test Mapping**: `TST-UI-158` (*Aftermath High-Aesthetic Lap & Interval Split Visualizer Verification*)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing unit tests in `LapSplitCalculatorTest` and overall test suite continue to pass cleanly.
2. **Tabular Lap Preservation**: In `WorkoutLaps.kt`, existing lap list rows, manual lap editing bottom sheets, and lap deletion remain 100% operational.
3. **Map Rendering Invariant**: In `TrackOnMapScreen.kt`, standard course tracks, markers, elevation profiles, and zone distribution cards render cleanly with zero disruption.
4. **Subtask Direct Completion**: Subtask transitions directly to `Erledigt` upon passing Gate 3 audit via transition `freigabe`.
5. **Parent Human Gate Invariance**: Terminal completion of `ATT-1742` remains strictly reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `LapSplitVisualizer.kt` (`ui/aftermath/splits/`)
* Introduce `LapSplitVisualizerCard` and `LapSplitVisualizer`:
  - **Header**: Lap icon (`ic_lap_laps`), localized title (`R.string.aftermath_laps_splits_title`), and overall summary.
  - **Columnar Row Layout**:
    - Slot 1: Lap badge (e.g. `L1`, `L2`) in `Surface(shape = RoundedCornerShape(4.dp), color = primaryContainer)`.
    - Slot 2: Distance & Duration (`1.00 km • 4:12`) in `bodySmall`.
    - Slot 3: Pace/Speed (`4:12 /km` or `31.2 km/h`) in `labelMedium` Bold.
    - Slot 4: Subtle proportional horizontal split bar (`MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)`, `tertiary` for fastest split).
    - Slot 5: "Best" badge on the fastest split (`R.string.split_badge_best`).
  - **Strict Emoji Prohibition**: No emojis (`🐇`, `🦔`) or garish multi-colored neon bars.
  - **Interactive State**: Tapping a split toggles selection (`selectedLapNr`) with subtle tonal card accenting.

### Component 2: `TrackOnMapScreen.kt` Integration
* Compute `splitChartData` via `remember(laps, sportType) { LapSplitCalculator.calculateSplitData(laps, sportType) }`.
* Track `selectedLapNr` and resolve `lapSegment` via `LapSegmentUtils.sliceLapSegment(trackPoints, selectedLapNr, laps)`.
* Embed `LapSplitVisualizerCard` in `analyticsContent` slotted container below the zone cards.
* Highlight selected lap segment on map track polyline with start/stop pins.

### Component 3: `WorkoutLaps.kt` Integration
* Compute `splitChartData` and render `LapSplitVisualizer` above the laps table when `splitChartData != null`.

### Component 4: 9-Language Localization
* Add `split_badge_best` across all 9 localized resource files (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Localization Strings
* Files: `app/src/main/res/values*/strings.xml` (9 locales)
* Add `split_badge_best` ("Best", "Beste", "Mejor", "Meilleur", "Migliore", "ベスト", "Beste", "Najlepszy", "Melhor").

### Step 2: High-Aesthetic Composable Construction
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/splits/LapSplitVisualizer.kt`
* Implement `LapSplitVisualizer` and `LapSplitVisualizerCard` with clean Material 3 tokens, columnar spacing, and subtle split bars.

### Step 3: Integrate into `TrackOnMapScreen.kt` & `WorkoutLaps.kt`
* Files:
  - `app/src/main/java/de/rainerblind/atrainingtracker/ui/map/TrackOnMapScreen.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutlaps/WorkoutLaps.kt`
* Connect split data, interactive lap selection, and map polyline highlighting.

### Step 4: Unit & Localization Tests
* Files:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/splits/LapSplitCalculatorTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/splits/LapSplitVisualizerTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/splits/LapSplitLocalizationTest.kt`
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.splits.*"`

---

## 6. Verification & Rollback Plan

* **Verification**:
  - Execute targeted unit tests: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.splits.*"`
  - Full clean-room test suite: `./gradlew testDebugUnitTest`
  - Requirement governance: `python3 tools/verify_requirement_governance.py --text-file docs/engineering/analysis/ATT-1742_analysis.md`
* **Rollback**:
  - `git checkout sprint/2026-40.7` cleanly discards all changes without affecting develop or other sprint tickets.
