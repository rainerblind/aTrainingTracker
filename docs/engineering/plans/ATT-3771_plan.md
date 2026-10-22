# Stage 3 Implementation Plan: ATT-3771

## 1. Architectural Overview (SWE.2)
- **Target Components**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/WorkoutMasksAndCardsSection.kt` (Zone Card Display Mode selector in settings)
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/HeartRateZoneDistributionCard.kt` (Bottom mode switcher in HR Zone Distribution card)
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/PowerZoneDistributionCard.kt` (Bottom mode switcher in Power Zone Distribution card)
  - `app/src/main/res/values-{es,fr,it,nl,pl,pt}/strings.xml` (Concise single-word `zone_mode_stacked_bar` labels)
- **Target Requirement & Test**: `REQ-UI-386` / `TST-UI-349`
- **Design Pattern**:
  - Configure all 3-button `SingleChoiceSegmentedButtonRow` controls (`STACKED_BAR`, `COLUMNS`, `HISTOGRAM`) with `Modifier.fillMaxWidth()`, `icon = {}` (suppressing the default $18\,\text{dp}$ checkmark icon and $8\,\text{dp}$ spacer), `contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)`, and single-line `Text` (`style = MaterialTheme.typography.labelSmall`, `fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium`, `maxLines = 1`, `softWrap = false`, `overflow = TextOverflow.Ellipsis`).
  - Shorten `zone_mode_stacked_bar` in `es`, `fr`, `it`, `nl`, `pl`, and `pt` to concise single-word labels (`Barra`, `Barre`, `Balk`, `Pasek` — max 5 characters) so that every localized label across all 9 locales is $\le 11$ characters.

## 2. UI Consistency (Rule 23 & Rule 38)
- **Closest Existing Reference**: `SingleChoiceSegmentedButtonRow` pattern in `docs/design_guidelines.md` Section 1.1 and Section 5.6 (Rule 38: *No Wrapping in Dense Horizontal Chip / Button Rows*).
- **Theme Tokens (`docs/design_guidelines.md` Sec 5.2, 5.4, 5.5)**:
  - Uses standard Material 3 `SegmentedButtonDefaults.itemShape(index, count)` and default `SegmentedButtonDefaults.colors()` (`secondaryContainer` / `onSecondaryContainer` when selected, `surface` / `onSurface` when unselected).
  - Active vs. inactive distinction reinforced via `FontWeight.Bold` vs. `FontWeight.Medium`.
  - Keep `WorkoutMasksAndCardsSection.kt` strictly $< 400$ lines (`WorkoutMasksAndCardsLayoutTest` invariant).

## 3. Atomic Implementation Steps

### Step 1: Update 6 Localized `zone_mode_stacked_bar` Strings
- **Files**: `app/src/main/res/values-{es,fr,it,nl,pl,pt}/strings.xml`
- **Action**:
  - `values-es/strings.xml`: `<string name="zone_mode_stacked_bar">Barra</string>`
  - `values-fr/strings.xml`: `<string name="zone_mode_stacked_bar">Barre</string>`
  - `values-it/strings.xml`: `<string name="zone_mode_stacked_bar">Barra</string>`
  - `values-nl/strings.xml`: `<string name="zone_mode_stacked_bar">Balk</string>`
  - `values-pl/strings.xml`: `<string name="zone_mode_stacked_bar">Pasek</string>`
  - `values-pt/strings.xml`: `<string name="zone_mode_stacked_bar">Barra</string>`

### Step 2: Configure Compact Single-Line `SegmentedButton` in Settings & Cards
- **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/WorkoutMasksAndCardsSection.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/HeartRateZoneDistributionCard.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/PowerZoneDistributionCard.kt`
- **Action**:
  - In `WorkoutMasksAndCardsSection.kt`: Pass `icon = {}`, `contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)`, and `Text(..., style = MaterialTheme.typography.labelSmall, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)` to `SegmentedButton` while keeping total file length $< 400$ lines.
  - In `HeartRateZoneDistributionCard.kt` and `PowerZoneDistributionCard.kt`: Pass `Modifier.fillMaxWidth().height(28.dp)` to `SingleChoiceSegmentedButtonRow`, and configure each `SegmentedButton` with `icon = {}`, `contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)`, and `Text(..., style = MaterialTheme.typography.labelSmall, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)`.

### Step 3: Update Unit Tests (`TST-UI-349`)
- **Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/ZoneDistributionStackedBarLocalizationTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/WorkoutSummaryZoneDisplayModeTest.kt`
- **Action**:
  - Assert in `ZoneDistributionStackedBarLocalizationTest.kt` that all 9 locales have `zone_mode_stacked_bar`, `zone_mode_columns`, and `zone_mode_histogram` length $\le 11$ characters.
  - Assert in `WorkoutSummaryZoneDisplayModeTest.kt` that `WorkoutMasksAndCardsSection.kt`, `HeartRateZoneDistributionCard.kt`, and `PowerZoneDistributionCard.kt` enforce `icon = {}`, `maxLines = 1`, `softWrap = false`, and `TextOverflow.Ellipsis`.
- **Targeted Verification Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.ZoneDistributionStackedBarLocalizationTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.WorkoutSummaryZoneDisplayModeTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.WorkoutMasksAndCardsLayoutTest" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.TelemetryHistogramCardTest"
  ```

## 4. Invariants & Rollback Safety
- Preserves `< 400` line limit on `WorkoutMasksAndCardsSection.kt`.
- Preserves all existing structural assertions in `TelemetryHistogramCardTest` and `WorkoutSummaryZoneDisplayModeTest`.
- 100% clean-room unit test pass rate preserved.
