# Stage 2: Requirement & Test Specification - ATT-1821: Structure Advanced Settings into Navigable / Collapsible Subsections

**Ticket**: [ATT-1821](https://rainerblind.atlassian.net/browse/ATT-1821)  
**Sub-task**: [ATT-1903](https://rainerblind.atlassian.net/browse/ATT-1903) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.8`  
**Branch**: `feature/ATT-1821`  
**Requirement Mapping**: `REQ-UI-222` (*Settings/UI: Modular, Collapsible Subsection Accordion Architecture for Advanced Settings*)  
**Test Mapping**: `TST-UI-176`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Formal Requirement Specification (`REQ-UI-222`)

### REQ-UI-222: Settings/UI: Modular, Collapsible Subsection Accordion Architecture for Advanced Settings

The system SHALL structure the Advanced Settings dialog (`AdvancedTuningDialog.kt`) into a modular, collapsible subsection architecture featuring Material 3 accordion cards with live active-value summary subtitles and animated expand/collapse mechanics (ATT-1821):

1. **Canonical Subsection Grouping**:
   - The 25+ parameters of `AdvancedTuningDialog.kt` SHALL be organized into 5 distinct, semantic accordion subsections:
     - **Section 1 (Cockpit & Typografie)** (`tuning_cat_cockpit_typography`): Cockpit Typography (Font family selector, Boldness segmented chips, and live 3-metric preview HUD card).
     - **Section 2 (AMOLED-Akkuschoner & Helligkeit)** (`tuning_cat_battery_saver`): AMOLED Battery Saver & Brightness (Full dimming brightness, Medium dimming brightness, Flat slope threshold, Steep slope threshold, Wake-up duration, Downward damping delay sliders).
     - **Section 3 (Sensoren, GPS & Filter)** (`tuning_cat_sensors_gps`): Sensors, GPS & Filtering (GPS horizontal accuracy threshold, Altitude smoothing window, Slope minimum speed sliders).
     - **Section 4 (Aftermath & Analyse)** (`tuning_cat_aftermath`): Aftermath & Profile Analytics (Profile X-Axis domain selector: Distance vs Time).
     - **Section 5 (Workout-Masken & Detailkarten)** (`tuning_cat_workout_masks_cards`): Workout Cards & Field Masks (8 Workout List card section toggles, 6 Edit Workout field toggles).

2. **Accordion Container Specifications (`TuningAccordionSection`)**:
   - Each subsection SHALL be encapsulated within a Material 3 Card container (`RoundedCornerShape(12.dp)`, `containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)`, `border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))`, zero tonal elevation `0.dp` per `REQ-UI-218`).
   - The Header Row SHALL be clickable across its entire bounds to toggle expansion state.
   - The Header Row SHALL render:
     - Category leading icon (`CockpitTypography`: `TextFields`; `AmoledBatterySaver`: `BrightnessMedium`; `SensorsGpsFilter`: `LocationOn`; `AftermathAnalysis`: `ShowChart`; `WorkoutMasksAndCards`: `ViewList`).
     - Title in `MaterialTheme.typography.titleMedium` (`FontWeight.SemiBold`).
     - Live active-value summary subtitle in `MaterialTheme.typography.bodySmall` (`color = MaterialTheme.colorScheme.onSurfaceVariant`).
     - Trailing expandable chevron icon (`Icons.Default.ExpandMore`) with smooth animated rotation (`animateFloatAsState` from $0^\circ$ collapsed to $180^\circ$ expanded).
   - Subsection contents SHALL expand and collapse via `AnimatedVisibility(visible = isExpanded, enter = expandVertically(), exit = shrinkVertically())`.

3. **Live Active-Value Subtitle Updating**:
   - Each subsection header subtitle SHALL reactively update upon any parameter modification without requiring dialog save:
     - **Cockpit**: `"${fontFamilyName}, ${fontWeightName}"`.
     - **Battery Saver**: `"${fullDim}% / ${mediumDim}%, Flat: ${flatSlope}%, Steep: ${steepSlope}%"`.
     - **Sensors & GPS**: `"GPS: ${gpsAccuracy}m, Alt: ${altitudeWindow}s, Speed: ${slopeMinSpeed} m/s"`.
     - **Aftermath**: `"X-Axis: ${domainName}"`.
     - **Workout Cards & Masks**: `"${activeCardCount}/8 Cards, ${activeFieldCount}/6 Fields"`.

4. **Extensible & Decoupled Architecture**:
   - Individual subsection composables SHALL be declared as separate, focused composable functions (`CockpitTypographySection`, `AmoledBatterySaverSection`, `SensorsGpsFilterSection`, `AftermathAnalysisSection`, `WorkoutMasksAndCardsSection`) with typed parameters or state callbacks, decoupled from the outer bottom sheet layout.

5. **Preservation of Core Invariants**:
   - Top advisory warning card (`Icons.Default.Warning`, `errorContainer` tint) SHALL remain visible at the top of the dialog.
   - Factory reset action (`R.string.reset_to_defaults`) SHALL atomically restore all 25+ parameters to defaults and immediately update all 5 subtitle summaries.
   - `AppBottomSheetContent` header title, icon (`Icons.Default.Tune`), and Save/Cancel footer actions remain 100% strictly preserved.
   - DataStore persistence keys, default values, and downstream reactive consumers remain 100% unchanged.
   - 100% 9-language localization parity across all 9 supported locales.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

* **Original Requirement ID & Target**: Net-new requirement (`REQ-UI-222`), refining and restructuring the UI presentation layer of `REQ-SET-073`, `REQ-UI-201`, `REQ-UI-212`, and `REQ-UI-216` under Epic `ATT-355` (*Good and consistent UI*).
* **Historical Origin & Commit Trace**:
  - `REQ-SET-073`: Sprint 2026-40.4 (`ATT-1268`), established `AdvancedTuningDialog.kt` and initial sliders.
  - `REQ-UI-201`: Sprint 2026-40.5 (`ATT-1391`), introduced Category 4 (*Aftermath & Profile Analytics*).
  - `REQ-UI-212`: Sprint 2026-40.7 (`ATT-1751`), introduced Category 5 (*Cockpit Typography*).
  - `REQ-UI-216`: Sprint 2026-40.7 (`ATT-1815`), relocated 14 toggles into Category 4.
* **Root Reason for Existing Formulation**:
  - Controls were previously appended sequentially into a single flat `Column`. As the number of options grew to over 25 distinct sliders, dropdowns, and switches, the dialog became an unsegmented scroll list where inactive settings lacked summary feedback and navigating to specific categories was cumbersome.
* **Preservation of Core Invariants**:
  - All 25+ parameter data models, slider ranges, default constants, DataStore keys, and downstream reactive consumers are 100% strictly preserved.
  - Advisory warning notice card, factory reset button, and Save/Cancel dialog actions are 100% strictly preserved.
  - Zero tonal elevation (`0.dp`) per `REQ-UI-218` is strictly preserved on accordion cards.

---

## 3. Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Accordion Structure & Discoverability)**:
  - *Given* an athlete opening Advanced Settings (`AdvancedTuningDialog`),
  - *When* the dialog renders,
  - *Then* settings SHALL be structured into 5 distinct accordion cards (`Cockpit & Typografie`, `AMOLED-Akkuschoner & Helligkeit`, `Sensoren, GPS & Filter`, `Aftermath & Analyse`, `Workout-Masken & Detailkarten`), each featuring a category icon, title, active-value summary subtitle, and expand/collapse chevron.
* **Criterion 2 (Collapsible Accordion Interaction)**:
  - *Given* any accordion section in collapsed state,
  - *When* the athlete taps the card header,
  - *Then* the section SHALL smoothly expand via `AnimatedVisibility`, revealing its dedicated sliders, toggles, or controls.
  - *When* tapped again, it SHALL smoothly collapse back to its header footprint.
* **Criterion 3 (Live Active-Value Subtitle Updating)**:
  - *Given* any setting adjusted within an expanded section (e.g. changing font family, adjusting dimming percentage, or toggling a card section),
  - *When* the parameter value changes,
  - *Then* the corresponding section header subtitle SHALL immediately update to reflect the new active value.
* **Criterion 4 (Footer Actions & Reset Invariant)**:
  - *Given* settings modified across multiple subsections,
  - *When* tapping "Reset to Factory Defaults",
  - *Then* all parameters SHALL revert to factory defaults atomically, and all section subtitle summaries SHALL immediately update to show default configurations.
  - *When* tapping "Save", all configurations SHALL persist cleanly in DataStore.
* **Criterion 5 (Material 3 & Localization Parity)**:
  - *Given* any supported app theme and locale,
  - *When* viewing the dialog,
  - *Then* cards SHALL comply with Material 3 styling (`0.dp` tonal elevation) and all text SHALL be localized across all 9 supported languages.

---

## 4. Test Specification (`TST-UI-176`)

### TST-UI-176.1: Subtitle Formatter Logic Unit Tests (`AdvancedTuningAccordionTest.kt`)
* Test `testCockpitTypographySubtitle_reflectsFontAndWeight()`:
  - Verify formatting for combinations of `CockpitFontFamily` (System Default, 7-Segment, Modern Athletic, Monospace, Playful) and `CockpitFontWeight` (Normal, Semi-Bold, Bold).
* Test `testAmoledBatterySaverSubtitle_reflectsDimFactorsAndSlopes()`:
  - Verify formatting for dimming percentages (e.g. 25% / 50%) and slope thresholds (2.0% / 5.0%).
* Test `testSensorsGpsFilterSubtitle_reflectsGpsAltSpeed()`:
  - Verify formatting for GPS accuracy (200m), altitude window (21s), and minimum slope speed (0.5 m/s).
* Test `testAftermathAnalysisSubtitle_reflectsDomain()`:
  - Verify formatting when domain is `DISTANCE` vs `TIME`.
* Test `testWorkoutMasksAndCardsSubtitle_reflectsActiveCounts()`:
  - Verify calculation of active card section count out of 8 and active edit workout field count out of 6.

### TST-UI-176.2: Accordion State & Visual Contract Tests (`AdvancedTuningVisualContractTest.kt`)
* Test `testAdvancedTuningDialog_usesAccordionSectionStructure()`:
  - Verify `AdvancedTuningDialog.kt` contains `TuningAccordionSection` invocations for all 5 semantic categories.
  - Verify zero remaining flat category headers with plain dividers.
* Test `testTuningAccordionSection_zeroTonalElevationAndM3Tokens()`:
  - Verify `TuningAccordionSection` applies `tonalElevation = 0.dp` and Material 3 surfaceVariant container styling.
* Test `testDecoupledSubsections_existAsIndividualComposables()`:
  - Verify presence of decoupled subsection composables (`CockpitTypographySection`, `AmoledBatterySaverSection`, `SensorsGpsFilterSection`, `AftermathAnalysisSection`, `WorkoutMasksAndCardsSection`).

### TST-UI-176.3: 9-Language Localization Audit
* Verify that string resources `tuning_cat_sensors_gps`, `tuning_cat_workout_masks_cards`, and `tuning_summary_masks_cards_format` are defined in:
  - `values/strings.xml` (EN)
  - `values-de/strings.xml` (DE)
  - `values-es/strings.xml` (ES)
  - `values-fr/strings.xml` (FR)
  - `values-it/strings.xml` (IT)
  - `values-ja/strings.xml` (JA)
  - `values-nl/strings.xml` (NL)
  - `values-pl/strings.xml` (PL)
  - `values-pt/strings.xml` (PT)
* Verify matching positional format tokens (`%1$d`, `%2$d`).

### TST-UI-176.4: Full Clean-Room Regression Suite
* Run `./gradlew testDebugUnitTest` across all modules to verify 100% pass rate.

---

## 5. Traceability Matrix

| Requirement | Test Spec | Verification Method | Target Status |
| :--- | :--- | :--- | :--- |
| `REQ-UI-222` (item 1, 2) | `[TST-UI-176.2]` | Visual Contract & Architecture Test | Specified |
| `REQ-UI-222` (item 3) | `[TST-UI-176.1]` | Subtitle Formatter Unit Test | Specified |
| `REQ-UI-222` (item 4) | `[TST-UI-176.2]` | Architecture Decoupling Test | Specified |
| `REQ-UI-222` (item 5) | `[TST-UI-176.3]` | 9-Language Localization Parity Check | Specified |
| `REQ-PRO-001` | `[TST-UI-176.4]` | Clean-Room Full Suite Regression (`./gradlew testDebugUnitTest`) | Specified |
