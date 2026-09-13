# Stage 3 Implementation Plan: Improve Laps Table (ATT-920)

**Ticket**: [ATT-920](https://rainerblind.atlassian.net/browse/ATT-920) / Sub-task: [ATT-932](https://rainerblind.atlassian.net/browse/ATT-932)  
**Author**: Agent 1 (Pair Programming Assistant)  
**Date**: 2026-09-13  
**Target Version**: `V4.9.36`  
**Git Branch**: `feature/ATT-920`  
**Requirement**: [REQ-UI-143](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md#L277)  
**Test Specification**: [TST-UI-096](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md#L310)  

---

## 1. Executive Summary & Design Rationale

### 1.1 Problem Statement
The workout laps table (`WorkoutLaps.kt`) in the workout summary view currently suffers from four visual and layout defects:
1. **Missing Column Headers**: The table lacks dedicated column headers, forcing users to guess which numbers represent duration, distance, or pace/speed.
2. **Awkward Two-Line Wrapping**: Pace and speed values in the 4th column append unit strings (`formatters.pace.format_with_units(...)` $\rightarrow$ `"46:27 min/km"`), which overflow the available column width in standard mobile viewports (360–400dp), resulting in awkward two-line wrapping (`46:27 min/\nkm`).
3. **Misaligned Badges**: The two-line text wrapping forces row heights to expand unevenly, pushing the fastest (🐇 Rabbit) and slowest (🦔 Hedgehog) badges down to align with the orphan unit string rather than the numeric value.
4. **Premature Lap Name Truncation**: Column 1 is constrained to `weight(1.3f)` (~30% width), truncating custom lap names (e.g., `1. Pause am Bach` $\rightarrow$ `1. Pause a...`) unnecessarily early.

### 1.2 Architectural Solution
1. **Dedicated Table Header (`LapTableHeader`)**:
   - Introduce a subtle table header row styled identically to `WorkoutExtrema` (`MaterialTheme.typography.labelSmall`, `color = MaterialTheme.colorScheme.onSurfaceVariant`, `includeFontPadding = false`).
   - Clearly identify each column: **Lap**, **Time**, **Distance**, and **Pace [unit]** / **Speed [unit]**.
2. **Pure Numerical Formatting in Cells (Zero-Wrap Guarantee)**:
   - Move unit specification to the column header (e.g. `Tempo [min/km]` or `Geschw. [km/h]`).
   - Format data cells with pure numeric strings (`formatters.pace.format` $\rightarrow$ `"46:27"`, `formatters.speed.format` $\rightarrow$ `"24.5"`).
   - Enforce `maxLines = 1` and `softWrap = false` across all numeric cells.
3. **Rebalanced Proportional Column Widths**:
   - `Lap Name`: widened by +25% from `1.3f` to `1.6f` (~115dp).
   - `Time`: tightened from `0.9f` to `0.85f` (~60dp).
   - `Distance`: tightened from `1.0f` to `0.85f` (~60dp).
   - `Pace / Speed`: `1.1f` (~75dp), comfortably accommodating `"46:27"` or header `Tempo [min/km]`.
   - `Badge Spacer`: fixed `26.dp` width.
4. **Vertical Badge Centering**:
   - Align badges with `Alignment.Center` in the 26dp Box, guaranteeing vertical centering on single-line rows.
5. **100% 9-Language Localization Parity**:
   - Translate all new header labels across EN, DE, ES, FR, IT, JA, NL, PL, and PT.

---

## 2. Invariants & Safety Guardrails

In accordance with `REQ-PRO-013` and `REQ-UI-143`, the following system invariants MUST NOT be altered:
1. **Interactive Tap & Edit Bottom Sheet (`REQ-UI-142`)**:
   - Lap row clicking to open `LapEditBottomSheet` must continue to work unconditionally.
2. **Performance Highlight Logic (`REQ-UI-141`)**:
   - Detection of fastest (`🐇`) and slowest (`🦔`) laps based on `speedAverageMps` for workouts with $\ge 2$ distinct valid speeds must remain completely intact.
3. **Expandable Toggle Threshold (`REQ-UI-141`)**:
   - Default collapse to first 3 laps for workouts with $> 3$ laps, along with the "Show all X laps" / "Show fewer" button, must remain unchanged.
4. **Database & Persistence Integrity**:
   - `Laps.db` SQLite schema (`name`, `description`, `timeTotalS`, `distanceTotalM`, `speedAverageMps`) must not be modified.
5. **Zero-Lap Guard**:
   - When a workout has 0 laps, `WorkoutLaps` must return early and render nothing (0px layout overhead).

---

## 3. Technical Implementation Specifications

### 3.1 Composable Structure in `WorkoutLaps.kt`

#### 3.1.1 Header Component (`LapTableHeader`)
```kotlin
@Composable
private fun LapTableHeader(
    isRunningSport: Boolean,
    modifier: Modifier = Modifier
) {
    val speedPaceUnit = stringResource(
        MyHelper.getUnitsId(if (isRunningSport) SensorType.PACE_spm else SensorType.SPEED_mps)
    )
    val speedPaceLabel = stringResource(
        if (isRunningSport) R.string.lap_table_header_pace else R.string.lap_table_header_speed
    )

    val headerStyle = MaterialTheme.typography.labelSmall.copy(
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Bottom,
            trim = LineHeightStyle.Trim.Both
        )
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        // Col 1: Lap Name (weight 1.6f)
        Text(
            text = stringResource(R.string.lap_table_header_lap),
            style = headerStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1.6f),
            maxLines = 1
        )

        // Col 2: Duration (weight 0.85f)
        Text(
            text = stringResource(R.string.lap_table_header_time),
            style = headerStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.85f),
            maxLines = 1
        )

        // Col 3: Distance (weight 0.85f)
        Text(
            text = stringResource(R.string.lap_table_header_distance),
            style = headerStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.85f),
            maxLines = 1
        )

        // Col 4: Pace / Speed with unit (weight 1.1f)
        Text(
            text = "$speedPaceLabel [$speedPaceUnit]",
            style = headerStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.1f),
            maxLines = 1
        )

        // Col 5: Badge Spacer matching 26.dp Box in data rows
        Spacer(modifier = Modifier.width(26.dp))
    }
}
```

#### 3.1.2 Numerical Cell Formatting in `WorkoutLaps`
In `WorkoutLaps`:
```kotlin
val speedPaceFormatted = if (isRunningSport) {
    if (lap.speedAverageMps > 0.001) {
        formatters.pace.format(1.0 / lap.speedAverageMps)
    } else {
        "--"
    }
} else {
    formatters.speed.format(lap.speedAverageMps)
}
```

#### 3.1.3 Column Widths & Single-Line Constraints in `LapRow`
```kotlin
@Composable
private fun LapRow(
    displayName: String,
    description: String?,
    timeFormatted: String,
    distanceFormatted: String,
    speedPaceFormatted: String,
    badgeEmoji: String?,
    badgeDescription: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Col 1: Lap Name & optional description (weight 1.6f)
        Column(
            modifier = Modifier.weight(1.6f)
        ) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Col 2: Duration (weight 0.85f)
        Text(
            text = timeFormatted,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.weight(0.85f)
        )

        // Col 3: Distance (weight 0.85f)
        Text(
            text = distanceFormatted,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.weight(0.85f)
        )

        // Col 4: Pace or Speed (weight 1.1f)
        Text(
            text = speedPaceFormatted,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.weight(1.1f)
        )

        // Col 5: Badge (Rabbit / Hedgehog) (fixed 26.dp)
        Box(
            modifier = Modifier
                .width(26.dp)
                .padding(start = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            if (badgeEmoji != null) {
                Text(
                    text = badgeEmoji,
                    fontSize = 14.sp,
                    modifier = Modifier.semantics {
                        if (badgeDescription != null) {
                            this.contentDescription = badgeDescription
                        }
                    }
                )
            }
        }
    }
}
```

---

## 4. 9-Language Localization Matrix

The following 5 new string resources will be added across all 9 localized `strings.xml` files:

| Key | EN | DE | ES | FR | IT | JA | NL | PL | PT |
|:---|:---|:---|:---|:---|:---|:---|:---|:---|:---|
| `lap_table_header_lap` | Lap | Runde | Vuelta | Tour | Giro | ラップ | Ronde | Okrążenie | Volta |
| `lap_table_header_time` | Time | Zeit | Tiempo | Temps | Tempo | 時間 | Tijd | Czas | Tempo |
| `lap_table_header_distance` | Distance | Distanz | Distancia | Distance | Distanza | 距離 | Afstand | Dystans | Distância |
| `lap_table_header_pace` | Pace | Tempo | Ritmo | Allure | Passo | ペース | Tempo | Tempo | Ritmo |
| `lap_table_header_speed` | Speed | Geschw. | Vel. | Vitesse | Vel. | 速度 | Snelh. | Pręd. | Vel. |

---

## 5. Verification Plan

### 5.1 Automated Unit Testing (`WorkoutLapsTest.kt`)
Expand `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/workoutlaps/WorkoutLapsTest.kt`:
1. **`testWorkoutLaps_rendersHeaderRowWithLocalizedLabels`**:
   - Verify that when `WorkoutLaps` is rendered with laps, the header row contains "Lap", "Time", "Distance", and "Pace [min/km]" (for running) or "Speed [km/h]" (for cycling).
2. **`testWorkoutLaps_numericalPaceFormatting_noEmbeddedUnits`**:
   - Verify that data rows render numerical pace (e.g. `"46:27"`) without repeating `"min/km"` in the cell.
3. **`testWorkoutLaps_columnWeightsAndProportions`**:
   - Verify layout weights match `1.6f`, `0.85f`, `0.85f`, `1.1f`, and `26.dp`.
4. **`testWorkoutLaps_badgeVerticalAlignment`**:
   - Verify rabbit/hedgehog badges render centered in the 26dp Box on single-line rows.
5. **`testWorkoutLaps_emptyLapsRendersNothing`**:
   - Verify 0-lap list returns early and renders no header or rows.

### 5.2 Localization Parity Test
- Verify all 5 string keys exist across all 9 `res/values*/strings.xml` directories with zero missing translations.

### 5.3 Clean-Room Regression Test
- Run `./gradlew testDebugUnitTest` to guarantee 100% build health and zero test regressions across the entire application.
