# Stage 1 Analysis: Improve Laps Table (ATT-920)

* **Ticket**: [ATT-920](https://rainerblind.atlassian.net/browse/ATT-920) (*[Verbesserung] Improve laps table*)
* **Sub-task**: [ATT-930](https://rainerblind.atlassian.net/browse/ATT-930) (*[Analysis] Improve laps table*)
* **Parent Epic**: [ATT-826](https://rainerblind.atlassian.net/browse/ATT-826) (*[Epic] Laps*)
* **Target Release Version**: `V4.9.36`
* **Branch**: `feature/ATT-920`

---

## 1. Problem Statement & User Inspection Analysis

### 1.1 Forensic Analysis of `Screenshot_20260912-214332.png`
Based on the defect evidence provided in Jira attachment `Screenshot_20260912-214332.png`, the workout laps table (`WorkoutLaps.kt`) exhibits several critical UI and readability flaws:

1. **Missing Table Column Headers**:
   - Above the lap split rows, only the section title `Runden (8)` is rendered.
   - The three numeric columns (`0:23:53`, `514 m`, `46:27 min/km`) have no column headers. Users must guess which column represents Duration, Distance, or Pace/Speed.
   - In stark contrast, the `WorkoutExtrema` table directly above it provides clear, subtle column headers (`min`, `Ø`, `max`) with sensor labels and units.

2. **Awkward Two-Line Wrapping of Pace / Speed Units**:
   - In the fourth column, `speedPaceFormatted` is generated using `formatters.pace.format_with_units(...)`, outputting strings like `"46:27 min/km"`.
   - In standard phone viewports (360dp–400dp width), the allocated column width (`weight(1.1f)` ≈ 80dp) cannot accommodate 13 characters in `bodyMedium` (14sp).
   - Compose breaks the text at the space/slash: `"46:27 min/"` on line 1, and `"km"` pushed onto line 2.
   - This occurs on almost every row (`46:27 min/\nkm`, `28:28 min/\nkm`, `84:44 min/\nkm`), creating bloated, uneven row heights and visual clutter.

3. **Misaligned Rabbit (🐇) and Hedgehog (🦔) Badges**:
   - Because the pace text wraps onto a second line, the performance highlight badges in the adjacent 26dp Box are pushed down to line 2, aligning awkwardly with the orphan unit `"km"` rather than the numerical pace value.

4. **Aggressive Truncation of Lap Names**:
   - Custom lap names are truncated very early with ellipsis:
     - `1. Pause am Bach` $\rightarrow$ `1. Pause a...`
     - `Pause an der Alpe` $\rightarrow$ `Pause an de...`
     - `Zum Murmeltierbau` $\rightarrow$ `Zum Murme...`
   - The first column is restricted to `weight(1.3f)` (only ~30% of row width) because excessive space is allocated to the numeric columns and unit strings.

---

## 2. Technical Root Cause Analysis

In `WorkoutLaps.kt`:
1. **No Header Composable**:
   `WorkoutLaps` renders the title row (`Runden (X)`), followed directly by the data rows, omitting any table header row.
2. **Redundant Per-Row Unit Formatting**:
   Line 125:
   ```kotlin
   val speedPaceFormatted = if (isRunningSport) {
       if (lap.speedAverageMps > 0.001) {
           formatters.pace.format_with_units(1.0 / lap.speedAverageMps) // Generates "46:27 min/km"
       } else {
           "--"
       }
   } else {
       formatters.speed.format_with_units(lap.speedAverageMps) // Generates "24.5 km/h"
   }
   ```
   Repeating `" min/km"` or `" km/h"` on every individual row wastes 7–8 characters per row, forcing line wrapping in constrained widths.
3. **Imbalanced Column Weights**:
   `LapRow` allocates:
   - `displayName`: `weight(1.3f)`
   - `timeFormatted`: `weight(0.9f)`
   - `distanceFormatted`: `weight(1.0f)`
   - `speedPaceFormatted`: `weight(1.1f)`
   - `badge`: `26.dp`
   This leaves insufficient width for lap names and insufficient width for pace units, causing truncation on the left and wrapping on the right.

---

## 3. Proposed Solution Architecture

### 3.1 Table Header Row (`LapTableHeader`)
Introduce a dedicated, subtle header row immediately above the lap data rows, adhering to the styling established in `WorkoutExtrema` (`MaterialTheme.typography.labelSmall`, `color = MaterialTheme.colorScheme.onSurfaceVariant`):
* **Col 1 (Left-aligned)**: `Runde` (DE) / `Lap` (EN) (`weight(1.6f)`)
* **Col 2 (Right-aligned)**: `Zeit` (DE) / `Time` (EN) (`weight(0.85f)`)
* **Col 3 (Right-aligned)**: `Distanz` (DE) / `Dist.` (EN) (`weight(0.85f)`)
* **Col 4 (Right-aligned)**:
  - Running: `Tempo [min/km]` (DE) / `Pace [min/km]` (EN) (or imperial unit `[min/mi]`) (`weight(1.1f)`)
  - Cycling / Other: `Geschw. [km/h]` (DE) / `Speed [km/h]` (EN) (or imperial unit `[mph]`) (`weight(1.1f)`)
* **Col 5**: `Spacer(modifier = Modifier.width(26.dp))` to preserve exact horizontal column alignment with data rows.

### 3.2 Concise Cell Formatting & Zero-Wrap Guarantee
1. **Pace & Speed**:
   - Because the unit (`[min/km]` or `[km/h]`) is explicitly declared in the column header, data cells only format the numeric value:
     - Running: `formatters.pace.format(1.0 / lap.speedAverageMps)` $\rightarrow$ `"46:27"` (5 chars).
     - Cycling/Other: `formatters.speed.format(lap.speedAverageMps)` $\rightarrow$ `"24.5"` (4 chars).
     - Stationary / 0-speed: `"--"`.
   - Enforce `maxLines = 1` and `softWrap = false` on all numeric text fields.
2. **Distance**:
   - Continue using `formatters.distance.format_with_units(lap.distanceTotalM)` (`"514 m"`, `"2,06 km"`), which is already compact (5–7 chars).
3. **Time**:
   - Continue using `formatters.time.format(lap.timeTotalS)` (`"0:23:53"`).

### 3.3 Rebalanced Column Proportions
* `Col 1 (Lap Name & Desc)`: Increased from `1.3f` $\rightarrow$ `1.6f` (~115dp, +25% wider). Lap titles have much more space before needing ellipsis.
* `Col 2 (Time)`: `0.85f` (compact, easily accommodates `"0:23:53"`).
* `Col 3 (Distance)`: `0.85f` (compact, easily accommodates `"2,06 km"` or `"514 m"`).
* `Col 4 (Pace / Speed)`: `1.1f` (easily accommodates `"46:27"` without wrapping, and provides sufficient space for the header `Tempo [min/km]`).
* `Col 5 (Badge)`: `26.dp` fixed width.

### 3.4 Visual Alignment & Spacing Polish
* Single-line numeric values guarantee consistent row heights across all rows.
* 🐇 and 🦔 badges remain vertically centered on the row, cleanly adjacent to the pace value.
* Subtle horizontal divider separates the header row from the first lap row.

---

## 4. Localization Impact

Add standard table header strings to `strings.xml` across all 9 supported locales:
* `lap_header_lap`: "Lap" (EN) / "Runde" (DE) / "Vuelta" (ES) / "Tour" (FR) / "Giro" (IT) / "ラップ" (JA) / "Ronde" (NL) / "Okrążenie" (PL) / "Volta" (PT)
* `lap_header_time`: "Time" (EN) / "Zeit" (DE) / "Tiempo" (ES) / "Temps" (FR) / "Tempo" (IT) / "タイム" (JA) / "Tijd" (NL) / "Czas" (PL) / "Tempo" (PT)
* Reuse existing strings:
  - `distance_short` ("Distanz" / "Dist.")
  - `pace_short` ("Tempo" / "Pace")
  - `speed_short` ("Geschw." / "Speed")
  - `MyHelper.getUnitsId(SensorType.PACE_spm)` (`min/km`, `min/mi`)
  - `MyHelper.getUnitsId(SensorType.SPEED_mps)` (`km/h`, `mph`)

---

## 5. Risk Assessment & Invariant Safety

* **Zero Data Layer Mutation**: No database schema changes, SQL queries, or repository models are modified.
* **Backward Compatibility**: `WorkoutLaps` maintains its existing `@Composable` signature (`laps`, `bSportType`, `modifier`, `onLapClick`).
* **Test Isolation**: Existing tests in `WorkoutLapsTest.kt` for `WorkoutLapsHelper` (badge computation, display thresholds) remain 100% valid. New Compose UI tests will verify header rendering, unit formatting, and single-line constraints.
