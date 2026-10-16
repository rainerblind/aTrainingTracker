# Stage 2 Requirement & Test Specification: ATT-3777

## 1. Requirement Specification (`REQ-UI-385`)

### `REQ-UI-385`: Streamlined Single-Action Header in ZonesSettingsActivity
- **Parent Ticket**: `ATT-3777`
- **Governing Guideline**: `docs/design_guidelines.md` Section 5.6 (*Placement, Entry Points & Screen Headers*)
- **Requirement Statement**:
  1. **Single-Action Top Bar (`REQ-UI-385.1`)**:
     - The top header bar of `ZonesSettingsActivity.kt` (`ZoneSettingsScreen`) SHALL contain strictly the screen title (`R.string.title_edit_zones`, with `maxLines = 1` and `overflow = TextOverflow.Ellipsis` inside a `Modifier.weight(1f, fill = false)` container) on the left and the single Calculate `IconButton` (`Icons.Default.Calculate`, `contentDescription = stringResource(R.string.zone_calc_button)`) on the right.
  2. **Removal of Redundant Header Elements (`REQ-UI-385.2`)**:
     - The header of `ZonesSettingsActivity.kt` SHALL NOT render:
       a. The status `SuggestionChip` (`R.string.zone_calc_status_customized` / `R.string.zone_calc_status_calculated`).
       b. The Reset-to-Defaults `IconButton` (`Icons.Default.RestartAlt` / `R.string.reset_to_defaults`).
       c. The "Last updated / Calibration recommended" row (`R.string.zone_calc_last_updated` / `R.string.zone_calc_calibration_recommended`).
       d. The "Athlete Weight" row (`R.string.athlete_weight_title` / `R.string.athlete_weight_edit`).
  3. **Preservation of Tab Bar & Calculator Dialog (`REQ-UI-385.3`)**:
     - Directly beneath the title/action row, the header Surface SHALL render `PrimaryScrollableTabRow` (`tab_hr_run`, `tab_hr_bike`, `tab_pwr_bike`).
     - Tapping the `Icons.Default.Calculate` `IconButton` SHALL continue to open `ZoneCalculatorDialog` with full `ZoneCalculatorStateHolder` state.

### Requirement Archaeology & Chesterton's Fence Audit (`REQ-PRO-022`)
1. **Original Requirement ID & Target**:
   - Net-new requirement `REQ-UI-385`, superseding the secondary header elements of `REQ-UI-373` (`ATT-3188`), `REQ-UI-378` (`ATT-3675`), and `REQ-TRK-019.4` (`ATT-1289`). Targets `ZonesSettingsActivity.kt`.
2. **Historical Origin & Commit Trace**:
   - Sprint 2026-42.4 (`ATT-3188` commit `c800ca58`, `ATT-1289` commit `5eee662f`) and Sprint 2026-42.5 (`ATT-3675` commit `0cf5f417`).
3. **Root Reason for Existing Formulation**:
   - Status chip, reset button, last-updated timestamp, and athlete weight row were initially placed in the `ZonesSettingsActivity` header before the dedicated Athlete Profile screen (`ATT-3704`) was designed and before on-device review revealed excessive header clutter.
4. **Preservation of Core Invariants**:
   - Retaining `title_edit_zones`, `Icons.Default.Calculate`, and `PrimaryScrollableTabRow` preserves full zone calculation and tab switching while keeping `SettingsDataStore` methods intact for unit tests and `ATT-3704`.

### Acceptance Criteria (Given-When-Then)
- **Given** `ZonesSettingsActivity` opened on any tab (`HR_RUN`, `HR_BIKE`, or `PWR_BIKE`),
- **When** the top header `Surface` renders,
- **Then** only the title (`title_edit_zones`), the `Icons.Default.Calculate` button, and the `PrimaryScrollableTabRow` are rendered in the header, and zero status chips, reset buttons, last-updated rows, or athlete weight rows appear in the header.
- **Given** the `Icons.Default.Calculate` button in the header,
- **When** tapped by the user,
- **Then** `ZoneCalculatorDialog` opens for the active `ZoneType`.

### System Invariants
- **Header Height Containment**: Header row height remains $\le 64\text{ dp}$ with single-line ellipsis title (`REQ-UI-378.2/.3`).
- **Accessibility Parity**: `Icons.Default.Calculate` `IconButton` retains localized `contentDescription = stringResource(R.string.zone_calc_button)` and $48\text{ dp}$ touch target.
- **DataStore API Stability**: `SettingsDataStore` methods (`resetToDefaults`, `ftpUpdatedAtFlow`, `athleteWeightKgFlow`) remain 100% intact.
- **Clean-Room Test Suite**: 100% pass rate across `./gradlew testDebugUnitTest`.

## 2. Test Specification (`TST-UI-348` & Updated `TST-UI-341`)

### `TST-UI-348`: Streamlined ZonesSettingsActivity Header Verification Suite (`ZonesSettingsHeaderContractTest.kt`)
1. **`testHeaderUsesCompactCalculateIconButtonAndOmitsRemovedElements`**:
   - Extract the header block between `// 1. THE HEADER` and `PrimaryScrollableTabRow(` in `ZonesSettingsActivity.kt`.
   - Assert `headerBlock` contains `IconButton` and `Icons.Default.Calculate` and `R.string.zone_calc_button`.
   - Assert `headerBlock` does NOT contain `TextButton`, `SuggestionChip`, `Icons.Default.RestartAlt`, `R.string.reset_to_defaults`, `zone_calc_status_customized`, `zone_calc_status_calculated`, `zone_calc_last_updated`, `zone_calc_calibration_recommended`, `athlete_weight_title`, or `athlete_weight_edit`.
2. **`testHeaderTitleIsBoundedWithMaxLinesAndEllipsis`**:
   - Assert `headerBlock` contains `maxLines = 1`, `overflow = TextOverflow.Ellipsis`, and `weight(1f, fill = false)`.

## 3. Localization Audit
- All existing 9-locale string resources remain intact in `values*/strings.xml`; no new string keys are added or broken.

## 4. Traceability Matrix

| Requirement ID | Test ID | Test Class | Status |
| :--- | :--- | :--- | :--- |
| `REQ-UI-385` | `TST-UI-348` | `ZonesSettingsHeaderContractTest.kt` | Planned |
