# Stage 1 Analysis: ATT-3777 — Remove Status Badge, Reset Action, Last-Updated Row, and Athlete Weight Row from ZonesSettingsActivity Header

## 1. Problem Domain & Root Cause Analysis (RCA)

### 1.1 Observable Symptom
In `ZonesSettingsActivity.kt` (`ZoneSettingsScreen`, lines 229–383), the top `Surface(color = MaterialTheme.colorScheme.primaryContainer)` header is visually cluttered with four redundant or misplaced UI elements above the `PrimaryScrollableTabRow`:
1. The status `SuggestionChip` (`zone_calc_status_customized` / `zone_calc_status_calculated`, lines 253–265) beside the screen title.
2. The Reset-to-Defaults `IconButton` (`Icons.Default.RestartAlt`, lines 281–294) in the top-right action bar alongside `Icons.Default.Calculate`.
3. The "Zuletzt aktualisiert / Last updated" row + calibration `SuggestionChip` (`zone_calc_last_updated` / `zone_calc_calibration_recommended`, lines 298–333) when `currentType == ZoneType.PWR_BIKE`.
4. The "Athletengewicht / Athlete Weight" row + Edit `TextButton` (`athlete_weight_title` / `athlete_weight_edit`, lines 335–368) when `currentType == ZoneType.PWR_BIKE`.

### 1.2 Technical Root Cause
- During `ATT-3188` (Automatic Zone Calculation) and `ATT-1289` (Power-to-Weight ratio), metadata badges, reset actions, calibration timestamps, and athlete weight editing were all stacked into the top `primaryContainer` header of `ZonesSettingsActivity.kt`.
- During the Sprint `2026-42.5` Joint Review of `ATT-3188`, the user identified that the top header of `ZonesSettingsActivity` should be clean and focused strictly on the screen title (`R.string.title_edit_zones`), the single Calculate `IconButton` (`Icons.Default.Calculate`) on the right, and the `PrimaryScrollableTabRow` below it (while Athlete Weight belongs to the dedicated Athlete Profile screen in `ATT-3704`).

## 2. Chesterton's Fence & Requirement Archaeology (`REQ-PRO-022`)

1. **Original Requirement ID & Target**:
   - Amends the header presentation of `REQ-UI-373` (*Automated Training Zone Calculation*), `REQ-UI-378` (*Compact Action Buttons and Header Height Containment in ZonesSettingsActivity*), and `REQ-TRK-019` (*Real-Time Power-to-Weight Cockpit Telemetry*).
   - Formalized as net-new requirement `REQ-UI-385` (*Streamlined Single-Action Header in ZonesSettingsActivity*) and updating `REQ-UI-378` / `TST-UI-341`.
2. **Historical Origin & Commit Trace**:
   - `ATT-3188` (commit `c800ca58`) added the status `SuggestionChip`, `resetToDefaults` button, and `ftpUpdatedAt` row.
   - `ATT-1289` (commit `5eee662f`) added the athlete weight row in the `PWR_BIKE` header.
   - `ATT-3675` (commit `0cf5f417`, `REQ-UI-378`) converted the two header `TextButton`s into `IconButton`s (`Calculate` and `RestartAlt`).
3. **Root Reason for Existing Formulation**:
   - Those elements were placed in the header before the dedicated Athlete Profile screen (`ATT-3704`) was planned and before on-device review revealed that the extra header rows and badges crowded the vertical viewport.
4. **Preservation of Core Invariants**:
   - Retaining the title (`R.string.title_edit_zones`, `maxLines = 1`, `overflow = TextOverflow.Ellipsis`), the `Icons.Default.Calculate` `IconButton` (`contentDescription = stringResource(R.string.zone_calc_button)`), and `PrimaryScrollableTabRow` preserves full access to `ZoneCalculatorDialog` (`ZoneCalculatorStateHolder` still receives `isCustomized`, `storedFtp`, `storedHrMax`, `storedHrRest`, `storedHrMethod`) while reducing the header to a clean, single title/action bar + tab row.
   - Underlying `SettingsDataStore` methods (`resetToDefaults`, `ftpUpdatedAtFlow`, `athleteWeightKgFlow`) remain intact for unit tests, cockpit W/kg fallback dialog, and `ATT-3704`.

## 3. Scope Bounding (`ATT-1250`)

### In Scope
- Remove from `ZonesSettingsActivity.kt` header:
  1. The status `SuggestionChip` (`zone_calc_status_customized` / `zone_calc_status_calculated`).
  2. The `Icons.Default.RestartAlt` `IconButton`.
  3. The `if (currentType == ZoneType.PWR_BIKE && ftpUpdatedAt != null)` row (`zone_calc_last_updated` and `zone_calc_calibration_recommended`).
  4. The `if (currentType == ZoneType.PWR_BIKE)` athlete weight row (`athlete_weight_title` and `athlete_weight_edit`) and the now-unused `showAthleteWeightDialog` state in `ZonesSettingsActivity.kt`.
- Update `ZonesSettingsHeaderContractTest.kt` to verify the streamlined single-action header contract (`Icons.Default.Calculate` present; status `SuggestionChip`, `RestartAlt`, `zone_calc_last_updated`, and `athlete_weight_title` absent from header).

### Out of Scope
- Dedicated Athlete Profile screen (handled in `ATT-3704`).
- Modifying `SettingsDataStore` persistence methods or `ZoneCalculatorDialog` internals.
