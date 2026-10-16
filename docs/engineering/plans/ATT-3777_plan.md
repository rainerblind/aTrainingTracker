# Stage 3 Architecture & Implementation Plan: ATT-3777

## 1. Architectural Overview (SWE.2)
- **Ticket**: `ATT-3777` — Remove status badge, reset action, last-updated row, and athlete weight row from `ZonesSettingsActivity` header
- **Layer**: UI Layer (`com.atrainingtracker.trainingtracker.activities.ZonesSettingsActivity.kt`)
- **Threading / Dispatchers**: Pure Jetpack Compose UI rendering on `Dispatchers.Main`; zero background coroutine or DataStore schema changes.
- **Architectural Design**:
  1. In `ZoneSettingsScreen` (`ZonesSettingsActivity.kt`), remove the unused state variables and flows in the header (`isCalculated`, `ftpUpdatedAt`, `athleteWeightKg`, `showAthleteWeightDialog`).
  2. In the top header `Row`, keep the bounded left title container (`Modifier.weight(1f, fill = false)`) with `Text(text = stringResource(id = R.string.title_edit_zones), ... maxLines = 1, overflow = TextOverflow.Ellipsis)` and remove the `SuggestionChip` (`zone_calc_status_customized` / `zone_calc_status_calculated`).
  3. In the right action container, retain only the `Icons.Default.Calculate` `IconButton` and remove the `Icons.Default.RestartAlt` `IconButton`.
  4. Remove the `if (currentType == ZoneType.PWR_BIKE && ftpUpdatedAt != null)` row (`zone_calc_last_updated` / `zone_calc_calibration_recommended`).
  5. Remove the `if (currentType == ZoneType.PWR_BIKE)` athlete weight row (`athlete_weight_title` / `athlete_weight_edit`) and the `if (showAthleteWeightDialog)` dialog invocation at the bottom of `ZoneSettingsScreen`, plus any unused imports.
  6. Update `ZonesSettingsHeaderContractTest.kt` to extract the header block up to `PrimaryScrollableTabRow(`, verify the presence of `Icons.Default.Calculate`, and verify the complete absence of `SuggestionChip`, `Icons.Default.RestartAlt`, `zone_calc_last_updated`, `athlete_weight_title`, and `showAthleteWeightDialog`.

## 2. UI Consistency (Rule 23 & Design Guidelines Section 5)
- **Closest Reference Screen**: `RouteTabbedScreen` / `EquipmentTabsScreen` (compact single-row top title + single action icon + `PrimaryScrollableTabRow`).
- **Theme Tokens**:
  - Container: `MaterialTheme.colorScheme.primaryContainer`
  - Title & Icon tint: `MaterialTheme.colorScheme.onPrimaryContainer`
  - Spacing: `horizontal = 16.dp, vertical = 12.dp`
- **No New One-Off Styles**: Strictly removes redundant header rows and controls to restore a clean, compact top bar.

## 3. Atomic Step Breakdown

### Step 1: Streamline Header in `ZonesSettingsActivity.kt`
- **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/activities/ZonesSettingsActivity.kt`
- **Changes**:
  - Remove unused header flows (`isCalculated`, `ftpUpdatedAt`, `athleteWeightKg`) and `showAthleteWeightDialog`.
  - Remove `SuggestionChip` from the title `Row`.
  - Remove `Icons.Default.RestartAlt` `IconButton` from the action `Row`.
  - Remove the `ftpUpdatedAt` row and the `athleteWeightKg` row from the header `Column`.
  - Remove `if (showAthleteWeightDialog) { AthleteWeightDialog(...) }` and unused imports (`RestartAlt`, `SuggestionChip`, `SuggestionChipDefaults`, `DateFormat`, `Date`, `TimeUnit`, `AthleteWeightDialog`).

### Step 2: Update Header Contract Test (`ZonesSettingsHeaderContractTest.kt`)
- **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/zones/ZonesSettingsHeaderContractTest.kt`
- **Changes**:
  - Update `headerEndIndex` to `"PrimaryScrollableTabRow("`.
  - Assert presence of `Icons.Default.Calculate`, `R.string.zone_calc_button`, `maxLines = 1`, `overflow = TextOverflow.Ellipsis`, and `weight(1f, fill = false)`.
  - Assert absence of `SuggestionChip`, `Icons.Default.RestartAlt`, `R.string.reset_to_defaults`, `zone_calc_status_customized`, `zone_calc_status_calculated`, `zone_calc_last_updated`, `zone_calc_calibration_recommended`, `athlete_weight_title`, `athlete_weight_edit`, and `showAthleteWeightDialog`.
- **Verification Command**:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.zones.*"`

## 4. Non-Negotiable Invariants & Rollback Safety
- **Rule 4 Gate Pre-Check**: `python3 tools/jira_util.py check-gate <Impl-Plan-Key>` MUST pass (`Erledigt`) before touching `ZonesSettingsActivity.kt`.
- **DataStore Preservation**: `SettingsDataStore` APIs (`resetToDefaults`, `ftpUpdatedAtFlow`, `athleteWeightKgFlow`, `setAthleteWeightKg`) and `AthleteWeightDialog.kt` remain untouched.
- **Rollback Strategy**: `git checkout -- app/src/main/java/com/atrainingtracker/trainingtracker/activities/ZonesSettingsActivity.kt`.
