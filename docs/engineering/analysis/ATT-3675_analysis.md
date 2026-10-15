# Stage 1 Analysis: ATT-3675 - Replace overflowing text buttons in ZonesSettingsActivity header with compact icon actions to prevent vertical screen blowout

**Ticket**: [ATT-3675](https://rainerblind.atlassian.net/browse/ATT-3675)  
**Sub-task**: [ATT-3680](https://rainerblind.atlassian.net/browse/ATT-3680) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.5` (Sprint ID `372`)  
**Branch**: `feature/ATT-3675`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-15  

---

## 1. Problem Statement & Motivation

During the Sprint 2026-42.4 Review of ATT-3184 and ATT-3188 on Google Pixel 10 (Android 16, SDK 37), opening `ZonesSettingsActivity` displayed a giant light-blue header consuming ~95% of the physical screen height (~800dp). This catastrophic blowout pushed the tab row ('Lauf HF', 'Rad HF', 'Rad Leistung') and all zone cards completely off the bottom of the screen behind the Android system navigation bar.

The expected behavior is a compact, single-row header matching `LayoutConstants.HEADER_TITLE_ROW_HEIGHT` or standard top app bars, leaving maximal vertical viewport space for the zone tabs and boundary editing cards.

---

## 2. Root Cause Analysis (Forensic Investigation)

In `ZonesSettingsActivity.kt` (lines 228–282), four elements were placed side-by-side inside a single horizontal `Row`:
1. Title `Text`: `stringResource(R.string.title_edit_zones)` ("Trainings Zonen") in `headlineSmall` (~150dp).
2. Status `SuggestionChip`: displaying "Berechnet" or "Benutzerdefiniert" (~90dp).
3. First `TextButton`: `stringResource(R.string.zone_calc_button)` ("Zonen berechnen" / "Calculate Zones", added in ATT-3188) (~140dp).
4. Second `TextButton`: `stringResource(R.string.reset_to_defaults)` ("Auf Standardwerte zurücksetzen" / "Reset to defaults", added in ATT-3184) (~240dp in German).

On standard mobile display widths (360dp–412dp), the cumulative unconstrained width of these four items (~620dp) exceeds the available physical width. Because Compose's horizontal `Row` with `SpaceBetween` measures children sequentially, the fourth item (`TextButton(R.string.reset_to_defaults)`) is squeezed into a near-zero width column (< 15dp) at the far right edge.
As a result, Compose wraps the German string `"Auf Standardwerte zurücksetzen"` vertically at one character per line across ~30 lines. Each line height in Material 3 button typography adds ~24dp–28dp, inflating the total header height to ~800dp.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Replace both horizontal `TextButton` controls ("Zonen berechnen" and "Auf Standardwerte zurücksetzen") in `ZonesSettingsActivity.kt` with compact, standard `IconButton` symbol actions:
     * Calculate zones: `Icons.Default.Calculate` with `contentDescription = stringResource(R.string.zone_calc_button)`.
     * Reset to defaults: `Icons.Default.RestartAlt` with `contentDescription = stringResource(R.string.reset_to_defaults)`.
  2. Protect the title row against horizontal starvation and vertical wrapping by applying `Modifier.weight(1f, fill = false)` and `maxLines = 1, overflow = TextOverflow.Ellipsis` to the title `Text`.
  3. Ensure adherence to `docs/design_guidelines.md` Section 5.6 (*Compact Icon Actions in Top Headers*).
  4. Verify multi-state Jetpack Compose `@Preview` renders cleanly across Light, Dark, and AMOLED themes and both German and English locales.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Modifying zone calculation mathematics (`ZoneCalculationMath.kt`).
  * Modifying DataStore persistence keys or schemas in `SettingsDataStore.kt`.
  * Changing boundary card inputs or validation logic in `ZoneSettingsCard`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Amends UI presentation details of `REQ-UI-370` (*7-Zone Coggan Cycling Power Model & Palette*, ATT-3184) and `REQ-UI-373` (*Automatic Training Zone Boundary Calculation*, ATT-3188) in `ZonesSettingsActivity.kt`.
* **Historical Origin & Commit Trace**: Commit `0da639ba` (ATT-3184, Reset Button) and commit `c800ca58` (ATT-3188, Calculate Button).
* **Root Reason for Existing Formulation**: Quick prototyping of actions in the header row used `TextButton` to make the action discoverable with text labels, which was acceptable in English tests with wide desktop preview windows but blew out on real mobile screens under localized German text.
* **Preservation of Core Invariants**: The functional actions (launching `showCalculatorDialog` and triggering `dataStore.resetToDefaults(currentType)`) are 100% preserved. Only the visual container transitions from verbose `TextButton` to compact `IconButton` with full accessibility `contentDescription`.

---

## 5. Architectural Strategy & High-Level Solution

In `ZonesSettingsActivity.kt`:
1. In the header `Row` (lines 228–282):
   ```kotlin
   Row(
       modifier = Modifier
           .fillMaxWidth()
           .padding(horizontal = 16.dp, vertical = 4.dp),
       verticalAlignment = Alignment.CenterVertically,
       horizontalArrangement = Arrangement.SpaceBetween
   ) {
       Row(
           modifier = Modifier.weight(1f, fill = false),
           verticalAlignment = Alignment.CenterVertically,
           horizontalArrangement = Arrangement.spacedBy(8.dp)
       ) {
           Text(
               text = stringResource(R.string.title_edit_zones),
               style = MaterialTheme.typography.headlineSmall,
               color = MaterialTheme.colorScheme.onPrimaryContainer,
               maxLines = 1,
               overflow = TextOverflow.Ellipsis
           )
           SuggestionChip(
               onClick = {},
               label = {
                   Text(
                       text = stringResource(
                           if (isCustomized) R.string.zone_calc_status_customized
                           else R.string.zone_calc_status_calculated
                       ),
                       style = MaterialTheme.typography.labelSmall
                   )
               },
               shape = MaterialTheme.shapes.extraSmall
           )
       }

       Row(
           verticalAlignment = Alignment.CenterVertically,
           horizontalArrangement = Arrangement.spacedBy(4.dp)
       ) {
           IconButton(
               onClick = { showCalculatorDialog = true }
           ) {
               Icon(
                   imageVector = Icons.Default.Calculate,
                   contentDescription = stringResource(R.string.zone_calc_button),
                   tint = MaterialTheme.colorScheme.onPrimaryContainer
               )
           }
           IconButton(
               onClick = {
                   scope.launch(Dispatchers.IO) {
                       dataStore.resetToDefaults(currentType)
                       reloadProfiles()
                   }
               }
           ) {
               Icon(
                   imageVector = Icons.Default.RestartAlt,
                   contentDescription = stringResource(R.string.reset_to_defaults),
                   tint = MaterialTheme.colorScheme.onPrimaryContainer
               )
           }
       }
   }
   ```
2. Add a layout/contract UI test or verify via existing Compose screenshot/unit tests (`ZonesSettingsActivityVisualContractTest.kt` or `ZonesSettingsLayoutTest.kt`) asserting header height bounds and icon presence.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Header height remains strictly constrained to standard app bar dimensions (< 64dp) regardless of screen width or language locale.
  2. Full accessibility parity: screen readers read the localized action names from `contentDescription`.
  3. Zero regressions in zone calculation dialog or reset-to-defaults functionality.
  4. 100% clean-room test suite pass rate.
* **Risk Rating**: **LOW**
  * Localized to a single Composable header `Row` in `ZonesSettingsActivity.kt`. No changes to data models, business logic, or databases.
