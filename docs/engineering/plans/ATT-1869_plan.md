# Stage 3: Implementation Plan - ATT-1869: Lap Split Visualizer Refinements: 3-Lap Truncation, Custom Names, Pace Units, and Rabbit Icon

**Ticket**: [ATT-1869](https://rainerblind.atlassian.net/browse/ATT-1869)  
**Sub-task**: [ATT-1934](https://rainerblind.atlassian.net/browse/ATT-1934) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.8`  
**Requirement Mapping**: `REQ-UI-228`, `REQ-UI-204`  
**Test Mapping**: `TST-UI-182`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Architectural Overview & Component Decomposition (SWE.2)

```
┌────────────────────────────────────────────────────────────────────────┐
│                        TrackOnMapScreen.kt                             │
│                  (Slotted Analytics Content Container)                 │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                       LapSplitVisualizerCard                           │
│     • Header: ic_lap_laps + "Runden & Splits" + "Laps (X)"             │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        LapSplitVisualizer                              │
│  • Truncation: displayedSplits = take(3) when collapsed (splits > 3)   │
│  • Expand/Collapse TextButton ("Alle 12 Runden" / "Weniger anzeigen")  │
│  ────────────────────────────────────────────────────────────────────  │
│  Row: [ Lap Name / Badge ]  [ Distance • Time ]  [ 🐇 Pace + Unit ]    │
│  Bar: ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓░░░░░░░░░░░░░░░░░░░ (Proportional Tonal Bar) │
└────────────────────────────────────────────────────────────────────────┘
```

### Component Roles & Boundaries
1. **`LapSplitVisualizer.kt` (`ui/aftermath/splits/`)**:
   - Manages internal collapsible state `var isExpanded by rememberSaveable { mutableStateOf(false) }`.
   - Filters `splitData.splits` to `take(3)` when `!isExpanded && splitData.splits.size > 3`.
   - Renders `split.displayName` inside the lap pill badge with `TextOverflow.Ellipsis` and `Modifier.widthIn(max = 120.dp)`.
   - Renders a right-aligned container for pace/speed with the rabbit icon (`🐇`) immediately left of the text for the fastest split, guaranteeing exact horizontal right-alignment across all rows.
   - Eliminates the textual 'Best' badge (`R.string.split_badge_best`).
2. **`TrackOnMapScreen.kt` & `WorkoutLaps.kt`**:
   - Updates `calculateSplitData` invocations to pass `formatters.pace.format_with_units(it)` and `formatters.speed.format_with_units(it)`.
3. **`LapSplitVisualizerTest.kt` & `LapSplitRefinementsLocalizationTest.kt`**:
   - Validates emoji presence (`🐇` on fastest, no `🦔`, no `split_badge_best`), truncation behavior, lap name rendering, and 9-language localization parity.

---

## 2. Atomic Implementation Step Sequence

### Step 1: Composable Refinements in `LapSplitVisualizer.kt`
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/splits/LapSplitVisualizer.kt`
* **Changes**:
  1. Add collapsible display logic:
     ```kotlin
     var isExpanded by rememberSaveable { mutableStateOf(false) }
     val displayedSplits = if (isExpanded || splitData.splits.size <= 3) {
         splitData.splits
     } else {
         splitData.splits.take(3)
     }
     ```
  2. In `LapSplitVisualizer`, iterate over `displayedSplits` instead of `splitData.splits`.
  3. Below the split rows, add the expansion toggle button when `splitData.splits.size > 3`:
     ```kotlin
     if (splitData.splits.size > 3) {
         TextButton(
             onClick = { isExpanded = !isExpanded },
             modifier = Modifier.align(Alignment.Start)
         ) {
             Text(
                 text = if (!isExpanded) {
                     stringResource(R.string.show_all_laps, splitData.splits.size)
                 } else {
                     stringResource(R.string.show_fewer_laps)
                 },
                 style = MaterialTheme.typography.labelMedium,
                 color = MaterialTheme.colorScheme.primary
             )
         }
     }
     ```
  4. Replace `text = "L${split.lapNr}"` with `text = split.displayName`, adding `maxLines = 1`, `overflow = TextOverflow.Ellipsis`, and `modifier = Modifier.defaultMinSize(minWidth = 26.dp).widthIn(max = 120.dp).padding(horizontal = 6.dp, vertical = 2.dp)`.
  5. Replace the pace and 'Best' badge block with right-aligned layout:
     ```kotlin
     Row(
         verticalAlignment = Alignment.CenterVertically,
         horizontalArrangement = Arrangement.End
     ) {
         if (split.isFastest) {
             Text(
                 text = "🐇",
                 fontSize = 13.sp,
                 modifier = Modifier.semantics {
                     contentDescription = "Fastest split"
                 }
             )
             Spacer(modifier = Modifier.width(4.dp))
         }
         Text(
             text = split.formattedPaceOrSpeed,
             style = MaterialTheme.typography.bodySmall,
             fontWeight = FontWeight.Bold,
             color = MaterialTheme.colorScheme.onSurface,
             textAlign = TextAlign.End
         )
     }
     ```

### Step 2: Caller Units Integration (`TrackOnMapScreen.kt` & `WorkoutLaps.kt`)
* **Target Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutlaps/WorkoutLaps.kt`
* **Changes**:
  - Replace `{ formatters.pace.format(it) }` with `{ formatters.pace.format_with_units(it) }`.
  - Replace `{ formatters.speed.format(it) }` with `{ formatters.speed.format_with_units(it) }`.

### Step 3: Test Suite Updates & Expansion
* **Target Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/splits/LapSplitVisualizerTest.kt`:
    - Update `testLapSplitVisualizerSourceContainsNoEmojis` to assert that `LapSplitVisualizer.kt` contains the rabbit emoji (`\uD83D\uDC07`), does NOT contain hedgehog (`\uD83E\uDD94`), and does NOT contain `R.string.split_badge_best`.
    - Add truncation contract tests verifying 3-lap default and expansion.
    - Add lap name rendering assertions.
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/splits/LapSplitCalculatorTest.kt`:
    - Assert `displayName` preservation with custom names.
    - Assert unit presence in formatted pace strings.
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/splits/LapSplitRefinementsLocalizationTest.kt`:
    - Verify `show_all_laps` and `show_fewer_laps` exist and are non-blank across all 9 localized resource files.

### Step 4: Verification & Gate Review
* Run targeted unit tests:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.splits.*"
  ```
* Run full clean-room regression suite:
  ```bash
  ./gradlew testDebugUnitTest
  ```

---

## 3. Invariant Protection & Rollback Safety

1. **Backwards Compatibility**: Laps with null or empty custom names seamlessly fall back to `"Lap $fallbackIndex"`, ensuring zero visual regressions for unedited workouts.
2. **Schema Invariance**: Zero modifications to SQLite database schemas or `LapData` entities.
3. **9-Language Parity**: All strings utilized (`show_all_laps`, `show_fewer_laps`) already exist across all 9 supported locales.
4. **Offline Resilience**: Pure Compose and formatting logic operates 100% offline without network calls or external services.
