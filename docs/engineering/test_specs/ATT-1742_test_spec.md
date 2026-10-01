# Stage 2: Requirement & Test Specification - ATT-1742: [Aftermath/Splits] High-Aesthetic Redesign of Lap & Interval Split Visualizer

**Ticket**: [[ATT-1742]](https://rainerblind.atlassian.net/browse/ATT-1742)  
**Sub-task**: [[ATT-1823]](https://rainerblind.atlassian.net/browse/ATT-1823) (`[Test-Spec]`)  
**Parent Epic**: [[ATT-111]](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.7`  
**Requirement Mapping**: `REQ-UI-204` (*Aftermath: High-Aesthetic Lap & Interval Split Visualizer Architecture*)  
**Test Spec ID**: `TST-UI-158`  
**Branch**: `feature/ATT-1742`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Requirement Specification (REQ-UI-204)

### 1.1 Problem Statement & Rationale
In Sprint Review 2026-40.5 (ATT-1392), user inspection of the initial lap split visualizer rejected it due to childish animal emojis (`🐇`, `🦔`), garish rainbow bars, and visual clutter ("looks really bad"). Following clean removal in ATT-1741, this specification defines the high-aesthetic Material 3 redesign: clean typography, aligned columns, subtle tonal relative bars, elegant "Best" pill badge, and smooth map segment highlighting.

### 1.2 Functional & Architectural Requirements
1. **Domain & Calculation Engine**:
   - `LapSplitItem` and `LapSplitChartData` immutable models encapsulate lap metrics, relative ratio ($[0.25, 1.0]$), fastest/slowest flags, and formatted pace/speed strings.
   - `LapSplitCalculator.calculateSplitData` computes split metrics across valid laps. Sessions with $< 2$ laps cleanly return `null`.
   - Pure mathematical calculations without emoji glyphs or saturated rainbow zone colors.
2. **Visual Composable (`LapSplitVisualizer.kt`)**:
   - `LapSplitVisualizerCard` renders as a cohesive Material 3 card (`surfaceVariant.copy(alpha = 0.5f)`).
   - Header displays lap icon, localized title (`R.string.aftermath_laps_splits_title`), and overall summary.
   - Each split item displays clean horizontal columnar slots:
     - Lap number pill (`L1`, `L2`)
     - Distance and duration (`1.00 km • 4:12`)
     - Formatted pace/speed
     - Subtle proportional horizontal bar (`MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)`)
     - Fastest split marked by an elegant, understated "Best" pill badge (`R.string.split_badge_best`).
3. **Screen Integration**:
   - `TrackOnMapScreen.kt`: Embedded within `analyticsContent` when `splitChartData != null`.
   - `WorkoutLaps.kt`: Embedded above the detailed lap table when `splitChartData != null`.
   - Tapping a split toggles selection (`selectedLapNr`) and triggers interactive track segment highlighting on the map polyline.
4. **Localization Parity**:
   - `aftermath_laps_splits_title`, `split_badge_best`, `laps_header` defined across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (High-Aesthetic Rendering & Emoji Prohibition)**:
  * *Given* a workout with $\ge 2$ laps viewed in Aftermath or WorkoutLaps,
  * *When* `LapSplitVisualizerCard` renders,
  * *Then* splits display with clean typography and aligned columns, fastest split displays an elegant 'Best' pill badge, and NO animal emojis (`🐇`, `🦔`) or saturated rainbow bars are rendered.
* **Criterion 2 (Map Polyline Highlighting on Split Selection)**:
  * *Given* `TrackOnMapScreen` displaying `LapSplitVisualizerCard`,
  * *When* the athlete taps a split row,
  * *Then* the row applies a subtle active accent, and the corresponding lap route segment is highlighted on the map polyline with start/stop pins.
* **Criterion 3 (Sub-2-Lap Omission)**:
  * *Given* a workout with 0 or 1 lap,
  * *When* viewed in Aftermath or WorkoutLaps,
  * *Then* the split visualizer card is cleanly omitted with zero whitespace.

### 1.4 System Invariants
* Single-thread SQLite confinement on `Dispatchers.IO`.
* Zero regression in existing tabular lap list, manual lap addition, or lap editing.
* Parent ticket Human Decision Gate remains inviolable.

---

## 2. Test Specification (TST-UI-158)

### Test Case 1: Pure Engine & Relative Ratio Calculation (`TST-UI-158.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/splits/LapSplitCalculatorTest.kt`
* **Preconditions**: Synthetic laps with varying speeds and sport types (Run vs Bike).
* **Action**: Invoke `LapSplitCalculator.calculateSplitData(laps, sportType)`.
* **Expected Result**: Fastest and slowest laps correctly flagged; relative ratios bounded in $[0.25, 1.0]$; run produces `min/km` pace; bike produces `km/h` speed; $< 2$ laps returns `null`.

### Test Case 2: Visual Layout & Columnar Alignment Contract (`TST-UI-158.2`)
* **Scope**: Composable Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/splits/LapSplitVisualizerTest.kt`
* **Preconditions**: Populated `LapSplitChartData`.
* **Action**: Render `LapSplitVisualizer` and inspect semantic nodes and structure.
* **Expected Result**: Verify absence of emoji strings; verify fastest lap contains "Best" badge; verify click callback dispatches selected `lapNr`.

### Test Case 3: 9-Language Localization Audit (`TST-UI-158.3`)
* **Scope**: Localization Parity Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/splits/LapSplitLocalizationTest.kt`
* **Goal**: Verify string resources (`aftermath_laps_splits_title`, `split_badge_best`, `laps_header`) exist across all 9 localized resource files:
  * `values/strings.xml` (EN)
  * `values-de/strings.xml` (DE)
  * `values-es/strings.xml` (ES)
  * `values-fr/strings.xml` (FR)
  * `values-it/strings.xml` (IT)
  * `values-ja/strings.xml` (JA)
  * `values-nl/strings.xml` (NL)
  * `values-pl/strings.xml` (PL)
  * `values-pt/strings.xml` (PT)
* **Expected Result**: 100% parity, zero missing entries, zero format specifier mismatches.

### Test Case 4: Clean-Room Regression Suite (`TST-UI-158.4`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: 100% pass rate across the full application unit test suite.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-158.1` | Unit | `LapSplitCalculator.calculateSplitData` | `REQ-UI-204` | Specified |
| `TST-UI-158.2` | Composable / Contract | `LapSplitVisualizer.kt` | `REQ-UI-204` | Specified |
| `TST-UI-158.3` | Localization | `LapSplitLocalizationTest` | `REQ-UI-204`, `REQ-UI-106` | Specified |
| `TST-UI-158.4` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
