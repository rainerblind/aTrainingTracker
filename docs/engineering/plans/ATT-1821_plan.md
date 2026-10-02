# Stage 3: Implementation Plan - ATT-1821: Structure Advanced Settings into Navigable / Collapsible Subsections

**Ticket**: [ATT-1821](https://rainerblind.atlassian.net/browse/ATT-1821)  
**Sub-task**: [ATT-1904](https://rainerblind.atlassian.net/browse/ATT-1904) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.8`  
**Requirement Mapping**: `REQ-UI-222` (*Settings/UI: Modular, Collapsible Subsection Accordion Architecture for Advanced Settings*)  
**Test Mapping**: `TST-UI-176`  
**Branch**: `feature/ATT-1821`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Problem Description & Background

The Advanced Settings dialog (`AdvancedTuningDialog.kt`) currently hosts over 25 individual tuning parameters, dropdowns, switches, and a live HUD preview card inside a flat, monolithic `Column`. Because settings are laid out sequentially with simple text headers and dividers, athletes must scroll through multiple screens of sliders to locate specific options. Moreover, inactive categories provide zero visibility into their current values without scrolling directly to them.

This implementation plan outlines the atomic construction steps to refactor `AdvancedTuningDialog.kt` into a modular, navigable, and collapsible subsection architecture. By grouping settings into 5 semantic Material 3 accordion cards with live active-value summary subtitles, athletes can immediately understand their active configuration at a glance and expand only the specific subsection they wish to modify.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-222` (*Settings/UI: Modular, Collapsible Subsection Accordion Architecture for Advanced Settings*)
* **Test Mapping**: `TST-UI-176`
  * `[TST-UI-176.1]`: Subtitle formatter unit tests in `AdvancedTuningAccordionTest.kt`
  * `[TST-UI-176.2]`: Accordion architecture and visual contract assertions in `AdvancedTuningVisualContractTest.kt`
  * `[TST-UI-176.3]`: 9-language localization audit across all 9 `strings.xml` resource files
  * `[TST-UI-176.4]`: Clean-room full suite regression (`./gradlew testDebugUnitTest`)

---

## 3. System Invariants & Preserved Behavior

1. **Parameter & Schema Invariance**: All 25+ parameters, clamping bounds, DataStore persistence keys, default values, and reactive update flows remain 100% strictly preserved.
2. **Advisory Warning Notice Card**: The top warning notice card (`Icons.Default.Warning`, `errorContainer` background) informing users about tracking heuristics and battery performance MUST remain prominently displayed at the top.
3. **Single-Tap Factory Reset**: The "Reset to Factory Defaults" (`R.string.reset_to_defaults`) button MUST atomically reset all tuning parameters, workout card preferences, and edit workout preferences, immediately updating all 5 subtitle summaries.
4. **Zero Tonal Elevation Standard**: Accordion cards and container elements MUST adhere to `tonalElevation = 0.dp` per `REQ-UI-218`.
5. **Subtask Self-Sufficiency**: Subtask `ATT-1904` transitions directly to `Erledigt` upon Gate 3 approval via `freigabe`.
6. **Parent Human Gate Invariance**: Terminal completion of parent ticket `ATT-1821` remains strictly reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Design (SWE.2)

### Component 1: `TuningAccordionSection` & Models (`AdvancedTuningAccordion.kt`)
* **Package**: `com.atrainingtracker.trainingtracker.ui.settings.tuning`
* **Enum**:
  ```kotlin
  enum class TuningSection {
      COCKPIT_TYPOGRAPHY,
      BATTERY_SAVER,
      SENSORS_GPS,
      AFTERMATH_ANALYSIS,
      WORKOUT_MASKS_CARDS
  }
  ```
* **Composable**:
  ```kotlin
  @Composable
  fun TuningAccordionSection(
      icon: ImageVector,
      title: String,
      subtitle: String,
      isExpanded: Boolean,
      onToggle: () -> Unit,
      modifier: Modifier = Modifier,
      content: @Composable () -> Unit
  )
  ```
  - Container: `Card` with `RoundedCornerShape(12.dp)`, `colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))`, `border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))`.
  - Header Row: Full-width clickable modifier (`Modifier.clickable(onClick = onToggle)`).
  - Trailing Chevron: `Icons.Default.ExpandMore` with rotation animated via `animateFloatAsState(targetValue = if (isExpanded) 180f else 0f)`.
  - Expansion: `AnimatedVisibility(visible = isExpanded, enter = expandVertically(), exit = shrinkVertically())`.

### Component 2: `TuningSubtitleFormatter` (Pure Testable Logic)
* Pure helper functions generating localized live subtitle strings:
  - `formatCockpitSubtitle(family: CockpitFontFamily, weight: CockpitFontWeight, context: Context): String`
  - `formatBatterySaverSubtitle(fullDim: Float, mediumDim: Float, flatSlope: Float, steepSlope: Float): String`
  - `formatSensorsGpsSubtitle(gpsAccuracy: Float, altitudeWindowSec: Int, slopeMinSpeed: Float): String`
  - `formatAftermathSubtitle(domain: ProfileXAxisDomain, context: Context): String`
  - `formatWorkoutMasksSubtitle(cardPrefs: WorkoutCardSectionPreferences, editPrefs: EditWorkoutFieldPreferences, context: Context): String`

### Component 3: Decoupled Subsection Composables (`AdvancedTuningSubsections.kt` or `AdvancedTuningDialog.kt`)
* `CockpitTypographySection`: Font family dropdown, weight filter chips, live preview HUD card.
* `AmoledBatterySaverSection`: 6 battery saver and slope sliders.
* `SensorsGpsFilterSection`: GPS accuracy, altitude smoothing window, slope minimum speed sliders.
* `AftermathAnalysisSection`: Profile X-axis domain selection chips.
* `WorkoutMasksAndCardsSection`: 8 workout list card toggles, 6 edit workout field toggles.

### Component 4: Dialog Refactoring (`AdvancedTuningDialog.kt`)
* Replace monolithic 768-line flat column with:
  1. Top Advisory Warning Card.
  2. 5 `TuningAccordionSection` instances bound to `expandedSections` state.
  3. Reset to Factory Defaults button.
  4. Global Save/Cancel actions in `AppBottomSheetContent`.

---

## 5. Atomic Implementation Steps

### Step 1: String Resources & 9-Language Localization
* Add new category titles and count formatting strings to:
  - `values/strings.xml` (EN)
  - `values-de/strings.xml` (DE)
  - `values-es/strings.xml` (ES)
  - `values-fr/strings.xml` (FR)
  - `values-it/strings.xml` (IT)
  - `values-ja/strings.xml` (JA)
  - `values-nl/strings.xml` (NL)
  - `values-pl/strings.xml` (PL)
  - `values-pt/strings.xml` (PT)
* Strings to add:
  - `tuning_cat_sensors_gps`
  - `tuning_cat_workout_masks_cards`
  - `tuning_summary_masks_cards_format`

### Step 2: Implement `AdvancedTuningAccordion.kt`
* Implement `TuningSection` enum.
* Implement `TuningSubtitleFormatter` object with pure formatting functions.
* Implement `TuningAccordionSection` composable with animated chevron and `AnimatedVisibility`.

### Step 3: Implement Decoupled Subsection Composables
* Extract and modularize:
  - `CockpitTypographySection`
  - `AmoledBatterySaverSection`
  - `SensorsGpsFilterSection`
  - `AftermathAnalysisSection`
  - `WorkoutMasksAndCardsSection`
* Ensure all existing sliders, helper texts, default value labels, and switches remain identical in functionality.

### Step 4: Integrate into `AdvancedTuningDialog.kt`
* Wire up `expandedSections` state (`rememberSaveable { mutableStateOf(setOf(TuningSection.COCKPIT_TYPOGRAPHY)) }`).
* Bind dynamic live subtitle formatting for each accordion section.
* Ensure factory reset resets all parameters and refreshes subtitle summaries immediately.

### Step 5: Unit Tests & Visual Contract Tests
* Create `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningAccordionTest.kt`.
* Create `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningVisualContractTest.kt`.
* Run `./gradlew testDebugUnitTest --tests "*AdvancedTuning*"` to verify all tests pass.

### Step 6: Full Clean-Room Regression Suite
* Run `./gradlew testDebugUnitTest` across all modules to verify 100% pass rate.

---

## 6. Verification Commands

```bash
# 1. Targeted Unit Tests
./gradlew testDebugUnitTest --tests "*AdvancedTuningAccordionTest*" --tests "*AdvancedTuningVisualContractTest*"

# 2. Clean-Room Full Suite Regression
./gradlew testDebugUnitTest
```
