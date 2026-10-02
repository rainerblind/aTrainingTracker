# Stage 1: Problem Domain & Root Cause Analysis - ATT-1869: Lap Split Visualizer Refinements: 3-Lap Truncation, Custom Names, Pace Units, and Rabbit Icon

**Ticket**: [ATT-1869](https://rainerblind.atlassian.net/browse/ATT-1869)  
**Sub-task**: [ATT-1932](https://rainerblind.atlassian.net/browse/ATT-1932) (`[Analysis]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.8`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Problem Statement & Background

### 1.1 Context & Evolution (Chesterton's Fence Archaeology)
In Sprint 2026-40.7, ticket **ATT-1742** (*High-Aesthetic Lap & Interval Split Visualizer Architecture*) redesigned the split comparison visualizer in `LapSplitVisualizer.kt`. It replaced legacy multi-color rainbow bars with subtle primary/tertiary tonal bars and introduced an elegant "Best" text badge on the fastest split.

During hands-on field testing on the Pixel 10 across real outdoor workouts (running and cycling), four specific ergonomic and aesthetic shortcomings were identified:

1. **Unbounded Vertical Scrolling (Lack of 3-Lap Truncation)**:
   Workouts with many laps (e.g. 10–25 intervals or auto-laps) render every split row continuously in `LapSplitVisualizer` and `LapSplitVisualizerCard`. In contrast, the classic `WorkoutLaps` table defaults to showing only the first 3 laps with an expandable button (`Alle {count} Runden anzeigen` / `Weniger anzeigen`). The absence of truncation in `LapSplitVisualizer` forces excessive vertical scrolling in `TrackOnMapScreen` and `WorkoutLaps`.
2. **Obscured Custom Lap Names (Synthetic `L1`, `L2` Badges)**:
   `LapSplitVisualizer` hardcodes `"L${split.lapNr}"` into each lap pill badge. Athletes who name their intervals (e.g., "Warm-up", "Intervall 1", "Sprint", "Cool-down") or rely on descriptive lap names cannot see them in the visualizer.
3. **Missing Explicit Pace Units**:
   The pace column in `LapSplitVisualizer` renders raw numbers without units (e.g. `4:12` instead of `4:12 min/km` or `4:12 min/mi`), making it ambiguous compared to speed or elapsed time.
4. **Misaligned Pace Column & Fast Lap Badge**:
   The "Best" text badge was placed to the *right* of the pace string on the fastest split row. Because only one row has this badge and it varies in width, the right-hand numeric alignment of the pace column is broken across rows. Placing a subtle rabbit icon (`🐇`) *to the left* of the pace string preserves clean right-alignment across all rows while immediately highlighting the top performance.

---

## 2. Forensic Investigation of Affected Subsystems

### 2.1 Visualizer Architecture (`LapSplitVisualizer.kt`)
- `LapSplitVisualizer(splitData, selectedLapNr, onLapClick)`:
  - Iterates over `splitData.splits` with `splitData.splits.forEach`.
  - Has no internal or external truncation state.
  - Lap badge displays `text = "L${split.lapNr}"`, ignoring `split.displayName`.
  - Pace rendering:
    ```kotlin
    Text(
        text = split.formattedPaceOrSpeed,
        ...
    )
    if (split.isFastest) {
        Spacer(...)
        Surface(...) { Text(stringResource(R.string.split_badge_best)) }
    }
    ```
    This breaks numeric right-alignment.
- `LapSplitVisualizerCard(splitData, ...)`:
  - Wraps `LapSplitVisualizer` inside a Material 3 Card with header.
  - Passes full `splitData` directly.

### 2.2 Integration Callers (`TrackOnMapScreen.kt` & `WorkoutLaps.kt`)
- `TrackOnMapScreen.kt` line 75:
  ```kotlin
  val splitChartData = remember(workoutData.laps, workoutData.bSportType, formatters) {
      LapSplitCalculator.calculateSplitData(
          laps = workoutData.laps,
          bSportType = workoutData.bSportType,
          paceFormatter = { formatters.pace.format(it) },
          speedFormatter = { formatters.speed.format(it) }
      )
  }
  ```
  `formatters.pace.format(it)` returns `"4:12"` without units! `formatters.pace.format_with_units(it)` returns `"4:12 min/km"`.
- `WorkoutLaps.kt` line 62:
  ```kotlin
  val splitChartData = remember(laps, bSportType, formatters) {
      LapSplitCalculator.calculateSplitData(
          laps = laps,
          bSportType = bSportType,
          paceFormatter = { formatters.pace.format(it) },
          speedFormatter = { formatters.speed.format(it) }
      )
  }
  ```
  Also calls `format(it)` instead of `format_with_units(it)`.

### 2.3 Domain Model & Formatting Engine (`LapSplitModels.kt` & `LapSplitCalculator.kt`)
- `LapSplitItem` already defines `val displayName: String`.
- `LapSplitCalculator` already computes `displayName = lap.getDisplayName(lapIndex)`.
- Default formatters in `LapSplitCalculator` (`defaultPaceFormatter` and `defaultSpeedFormatter`) already append `"/km"` and `"km/h"`.
- Passing `format_with_units` from `LocalMetricFormatter` in the UI screens ensures user-configured unit system (Metric vs Imperial: `min/km` vs `min/mile`, `km/h` vs `mile/h`) is respected.

### 2.4 Existing Tests & Constraints (`LapSplitVisualizerTest.kt`)
- `LapSplitVisualizerTest` in `ATT-1742` added an assertion:
  `assertFalse("LapSplitVisualizer.kt must NOT contain rabbit emoji", content.contains("\uD83D\uDC07"))`.
- Under `ATT-1869`, the product direction explicitly restores the rabbit emoji (`🐇`) on the fastest split row, positioned to the left of the pace text.
- That test must be updated to verify the rabbit emoji is present on the fastest split and absent on other rows/elements.

---

## 3. Scope Bounding & Invariant Enforcements

### 3.1 In-Scope Deliverables
1. **3-Lap Truncation & Expansion Toggle**:
   - In `LapSplitVisualizer.kt`, introduce collapsible display logic defaulting to 3 laps when `splits.size > 3`.
   - Render an expand/collapse `TextButton` using existing strings `R.string.show_all_laps` (`Alle %1$d Runden anzeigen` / `Show all %1$d laps`) and `R.string.show_fewer_laps` (`Weniger anzeigen` / `Show fewer`).
   - State managed via `rememberSaveable { mutableStateOf(false) }`.
2. **Actual Lap Names**:
   - Display `split.displayName` in the lap badge pill with `maxLines = 1` and `TextOverflow.Ellipsis`, constrained by `Modifier.widthIn(max = 120.dp)`.
   - Custom names ("Warm-up", "Intervall 1") or fallback ("Lap 1") are displayed instead of synthetic `L1`.
3. **Explicit Pace Units**:
   - Update callers in `TrackOnMapScreen.kt` and `WorkoutLaps.kt` to pass `formatters.pace.format_with_units(it)` and `formatters.speed.format_with_units(it)`.
   - Ensure pace numbers include explicit units (e.g. `min/km`, `min/mi`, `km/h`, `mile/h`).
4. **Rabbit Icon on Fastest Split (Right-Aligned Pace Values)**:
   - Remove the textual 'Best' pill badge.
   - In each split row, align pace to the right:
     ```kotlin
     Row(verticalAlignment = Alignment.CenterVertically) {
         if (split.isFastest) {
             Text(text = "🐇", fontSize = 13.sp, ...)
             Spacer(modifier = Modifier.width(4.dp))
         }
         Text(
             text = split.formattedPaceOrSpeed,
             style = MaterialTheme.typography.bodySmall,
             fontWeight = FontWeight.Bold,
             textAlign = TextAlign.End
         )
     }
     ```
   - All rows right-align their pace text at the same horizontal boundary.

### 3.2 Out-of-Scope (Governance Guardrails)
- Do NOT alter SQLite database schemas or `LapData` definitions.
- Do NOT alter lap editing or deletion dialogs in `WorkoutLaps.kt`.
- Do NOT modify the map polyline segment highlight logic.
- Do NOT implement configurable display modes (Table vs Split Visualizer) — that belongs to sprint backlog ticket `ATT-1870`.

---

## 4. Requirement Modification Trace (Chesterton's Fence)

| Field | Detail |
| :--- | :--- |
| **Original Requirement ID & Target** | `REQ-UI-204` (*Aftermath: High-Aesthetic Lap & Interval Split Visualizer Architecture*), extending with `REQ-UI-228` under Epic `ATT-111`. |
| **Historical Origin & Commit Trace** | Sprint 2026-40.7 (`ATT-1742`). |
| **Root Reason for Existing Formulation** | ATT-1742 replaced emoji bars with text "Best", but omitted 3-lap truncation, hardcoded `L{index}`, stripped pace units in callers, and broke right-alignment with the trailing "Best" badge. |
| **Preservation of Core Invariants** | Columnar alignment, proportional split bars, map polyline synchronization, 9-language localization parity, and non-crashing calculation engines remain 100% preserved. |

---

## 5. Risk Assessment & Mitigations

| Risk | Severity | Mitigation Strategy |
| :--- | :--- | :--- |
| Long custom lap names truncate or overflow on narrow screens (320–360dp) | Medium | Constrain badge with `Modifier.widthIn(max = 110.dp)` with `TextOverflow.Ellipsis`; distance and pace columns remain fixed. |
| Double truncation if both visualizer and table have expand buttons in `WorkoutLaps` | Low | Visualizer and table each control their own collapsed state cleanly; user can expand either independently. |
| Unit test regressions in `LapSplitVisualizerTest` | Medium | Update emoji assertion from "no emojis" to "rabbit on fastest split, no hedgehog". |
