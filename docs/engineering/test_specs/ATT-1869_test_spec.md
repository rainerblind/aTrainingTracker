# Stage 2: Requirement & Test Specification - ATT-1869: Lap Split Visualizer Refinements: 3-Lap Truncation, Custom Names, Pace Units, and Rabbit Icon

**Ticket**: [ATT-1869](https://rainerblind.atlassian.net/browse/ATT-1869)  
**Sub-task**: [ATT-1933](https://rainerblind.atlassian.net/browse/ATT-1933) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.8`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Formal Requirement Specification: `REQ-UI-228`

### 1.1 Requirement Statement
The system SHALL refine the Lap & Interval Split Visualizer (`LapSplitVisualizer.kt`), ensuring compact vertical presentation, descriptive lap naming, explicit metric unit formatting, and right-aligned pace columns across workout aftermath inspection views (`TrackOnMapScreen.kt` and `WorkoutLaps.kt`) (ATT-1869):

1. **3-Lap Collapsible Truncation**:
   - When `splitData.splits.size > 3`, `LapSplitVisualizer` SHALL initially display only the first 3 laps (`splitData.splits.take(3)`).
   - It SHALL render an expandable `TextButton` toggling an internal `rememberSaveable { mutableStateOf(false) }` state.
   - When collapsed, the button text SHALL display `R.string.show_all_laps` formatted with total lap count (`splitData.splits.size`).
   - When expanded, the full split list SHALL be displayed and the button text SHALL display `R.string.show_fewer_laps`.
   - When `splitData.splits.size <= 3`, all laps SHALL be displayed and the toggle button SHALL be omitted.
2. **Actual & Custom Lap Names**:
   - In each split row, the lap badge SHALL display `split.displayName` instead of hardcoded synthetic `L{index}`.
   - Custom names (e.g., "Warm-up", "Intervall 1") or fallback default ("Lap 1") SHALL be rendered with `style = MaterialTheme.typography.labelSmall`, `fontWeight = FontWeight.Bold`, `maxLines = 1`, `overflow = TextOverflow.Ellipsis`, and constrained by `Modifier.widthIn(max = 120.dp)` to prevent horizontal row overflow.
3. **Explicit Pace & Speed Units**:
   - In `TrackOnMapScreen.kt` and `WorkoutLaps.kt`, `LapSplitCalculator.calculateSplitData` invocations SHALL pass `formatters.pace.format_with_units(it)` and `formatters.speed.format_with_units(it)`.
   - Each split row SHALL render explicit speed/pace units formatted with the athlete's active unit system (e.g. `min/km`, `min/mi`, `km/h`, `mile/h`).
4. **Fastest Lap Rabbit Icon & Columnar Right-Alignment**:
   - The textual 'Best' pill badge SHALL be removed.
   - In each split row, the pace/speed column SHALL be right-anchored.
   - On the fastest split row (`split.isFastest == true`), a subtle rabbit icon (`🐇`) SHALL be rendered immediately to the left of the formatted pace/speed text, separated by `4.dp` spacing.
   - The formatted pace/speed text SHALL remain right-aligned across all split rows, ensuring identical right margin alignment regardless of whether the rabbit icon is present.
5. **Preservation of Core Invariants**:
   - Interactive lap selection (`selectedLapNr`) and route highlighting on the map track polyline (`TrackOnMapScreen.kt`) remain 100% functional.
   - Single-thread SQLite confinement and `LapData` models remain unmodified.
   - Proportional tonal split bars and intensity color scaling remain intact.
   - 100% 9-language localization parity is preserved.

---

### 1.2 Given-When-Then Acceptance Criteria

- **AC-1 (3-Lap Truncation & Expansion)**:
  - *Given* a workout session with more than 3 recorded laps (e.g. 10 laps) viewed in `LapSplitVisualizer` or `LapSplitVisualizerCard`,
  - *When* the screen renders initially,
  - *Then* exactly 3 split rows SHALL be visible, followed by an expansion button displaying the total count (`Alle 10 Runden anzeigen` / `Show all 10 laps`).
  - *When* the athlete taps the expansion button,
  - *Then* all 10 split rows SHALL be displayed and the button text SHALL switch to `Weniger anzeigen` / `Show fewer`.

- **AC-2 (Actual Lap Names)**:
  - *Given* recorded laps with custom names ("Warm-up", "Intervall 1") or default names ("Lap 1", "Lap 2"),
  - *When* `LapSplitVisualizer` renders,
  - *Then* each row badge SHALL display the actual `displayName` instead of synthetic `L1..Lx` tokens.

- **AC-3 (Explicit Pace Units)**:
  - *Given* a running workout session with pace telemetry,
  - *When* viewing split rows in `LapSplitVisualizerCard` or `WorkoutLaps`,
  - *Then* the pace column SHALL display explicit units (e.g. `4:12 min/km` in Metric, `6:45 min/mile` in Imperial).
  - *Given* a cycling workout session with speed telemetry,
  - *Then* the speed column SHALL display explicit units (e.g. `32.4 km/h` in Metric, `20.1 mile/h` in Imperial).

- **AC-4 (Rabbit Icon & Right-Alignment)**:
  - *Given* a workout session with 2 or more laps with differing speeds,
  - *When* `LapSplitVisualizer` renders the fastest split row,
  - *Then* the textual 'Best' badge SHALL NOT be displayed, a rabbit icon (`🐇`) SHALL appear immediately to the left of the pace/speed text, and the numeric pace/speed text SHALL align rightwards at the exact same edge coordinate as all other non-fastest split rows.

---

## 2. Test Specification: `TST-UI-182`

### 2.1 Test Case Breakdown

#### TST-UI-182.1: Composable & Layout Contract Tests (`LapSplitVisualizerTest.kt`)
- **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/splits/LapSplitVisualizerTest.kt`
- **Verifications**:
  1. *Emoji Audit*: Verify `LapSplitVisualizer.kt` contains the rabbit emoji (`\uD83D\uDC07`), does NOT contain hedgehog (`\uD83E\uDD94`), and does NOT contain textual `split_badge_best` pill badge.
  2. *Truncation Logic*: Verify that when $> 3$ splits exist, only 3 items are rendered in collapsed state, and tapping toggle exposes full list.
  3. *Lap Name Display*: Verify `displayName` is referenced in the lap badge.
  4. *Right-Alignment*: Verify pace container structure positions rabbit to the left of the right-aligned text.

#### TST-UI-182.2: Data Model & Units Verification (`LapSplitCalculatorTest.kt`)
- **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/splits/LapSplitCalculatorTest.kt`
- **Verifications**:
  1. Verify `calculateSplitData` preserves custom `name` in `displayName`.
  2. Verify `formattedPaceOrSpeed` contains explicit units (`/km`, `min/km`, `km/h`).

#### TST-UI-182.3: 9-Language Localization Audit (`LapSplitLocalizationTest.kt`)
- **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/splits/LapSplitLocalizationTest.kt`
- **Verifications**:
  1. Verify `show_all_laps` and `show_fewer_laps` exist across all 9 localized resource files (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
  2. Verify `show_all_laps` contains positional argument `%1$d`.

#### TST-UI-182.4: Clean-Room Full Suite Regression Execution
- **Command**: `./gradlew testDebugUnitTest`
- **Success Criteria**: 100% pass rate across all modules with 0 regressions.

---

## 3. Traceability Matrix

| Requirement Clause | Test Specification | Verification Method |
| :--- | :--- | :--- |
| `REQ-UI-228` (item 1: 3-lap truncation & toggle) | `[TST-UI-182.1]` | Composable Contract Test (`LapSplitVisualizerTest.kt`) |
| `REQ-UI-228` (item 2: actual lap names) | `[TST-UI-182.1]`, `[TST-UI-182.2]` | Unit Test (`LapSplitCalculatorTest.kt`, `LapSplitVisualizerTest.kt`) |
| `REQ-UI-228` (item 3: explicit pace units) | `[TST-UI-182.2]` | Unit Test (`LapSplitCalculatorTest.kt`) |
| `REQ-UI-228` (item 4: rabbit icon & alignment) | `[TST-UI-182.1]` | Contract Test (`LapSplitVisualizerTest.kt`) |
| `REQ-UI-228` (item 5: localization parity) | `[TST-UI-182.3]` | Localization Audit (`LapSplitLocalizationTest.kt`) |
| `REQ-PRO-001` (Clean-room regression) | `[TST-UI-182.4]` | Full test suite (`./gradlew testDebugUnitTest`) |
