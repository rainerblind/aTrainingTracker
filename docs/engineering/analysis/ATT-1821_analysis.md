# Stage 1 Analysis: ATT-1821 - Structure Advanced Settings into Navigable / Collapsible Subsections

**Ticket**: [ATT-1821](https://rainerblind.atlassian.net/browse/ATT-1821)  
**Sub-task**: [ATT-1902](https://rainerblind.atlassian.net/browse/ATT-1902) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.8`  
**Branch**: `feature/ATT-1821`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Problem Statement & Motivation

With the successive introduction of telemetry heuristics, filter settings, display tuning, cockpit personalization, and post-workout configuration controls across recent sprints, the Advanced Settings dialog (`AdvancedTuningDialog.kt`) has grown into a flat, monolithic, unsegmented column of 768 lines of code:
- **AMOLED Battery Saver & Brightness**: Full dim factor, medium dim factor, flat slope threshold, steep slope threshold, wake-up duration, downward damping delay.
- **Sensoren, GPS & Filter**: GPS horizontal accuracy limit, barometric altitude smoothing window, slope calculation minimum speed.
- **Aftermath & Analyse**: Profile X-axis domain (Distance vs Time).
- **Workout-Masken & Detailkarten**: 8 workout list card section visibility switches and 6 edit workout field visibility switches (relocated in ATT-1815 / `REQ-UI-216`).
- **Cockpit-Typografie**: Font family dropdown (5 options), boldness selector (3 options), and live 3-metric preview HUD card (ATT-1751 / `REQ-UI-212`).

In its current monolithic form, an athlete opening Advanced Settings is confronted with an overwhelming vertical stack of sliders, switches, dropdowns, and cards. Reviewing or adjusting a specific setting requires extensive vertical scrolling, leading to poor discoverability, accidental touches on sliders during scrolling, and a lack of visual hierarchy.

The objective of **ATT-1821** is to refactor `AdvancedTuningDialog.kt` into a **modular, navigable, and collapsible subsection architecture** featuring clean Material 3 accordion cards. Each subsection will present a category icon, clear title, live active-value summary subtitle, and an expandable chevron, enabling athletes to instantly grasp current configurations and expand only the specific controls they wish to adjust.

---

## 2. Root Cause Analysis & Architecture Gap Analysis

### Forensic Inspection of `AdvancedTuningDialog.kt`:
1. **Flat Monolithic Layout**:
   - `AdvancedTuningDialog` places all controls inside a single `Column(verticalArrangement = Arrangement.spacedBy(16.dp))`.
   - Categories are demarcated solely by raw text headers (`TuningCategoryHeader`) and simple horizontal lines (`HorizontalDivider()`).
   - The entire contents cannot fit on any standard mobile viewport, requiring several screens worth of scrolling.
2. **Coupled UI Components**:
   - All slider rendering, toggle logic, dropdown mechanics, and the typography live preview card are declared in one monolithic 768-line file.
   - Adding or adjusting any category requires editing the central composable, violating the Open-Closed Principle and increasing risk of regression.
3. **Missing Value Summaries on Inactive Categories**:
   - When a category is scrolled off-screen or out of view, the user has zero feedback about its current configuration without scrolling all the way to it.
   - For example, an athlete wanting to check their current cockpit font or GPS threshold has to scroll past 6 battery saver sliders and dividers.
4. **Accordion & Collapsible Design Requirements**:
   - According to the ticket specification and Material 3 design guidelines:
     - Group into 5 clear semantic subsections:
       1. **Cockpit & Typografie** (`tuning_cat_cockpit_typography`)
       2. **AMOLED-Akkuschoner & Helligkeit** (`tuning_cat_battery_saver`)
       3. **Sensoren, GPS & Filter** (`tuning_cat_sensors_gps` or unified GPS & sensor filter)
       4. **Aftermath & Analyse** (`tuning_cat_aftermath`)
       5. **Workout-Masken & Detailkarten** (`settings_workout_masks_cards`)
     - Each card must display:
       - Leading Category Icon (e.g. `TextFields`, `BrightnessMedium`, `LocationOn`, `ShowChart`, `ViewList`).
       - Primary Title.
       - Active Value Summary Subtitle (e.g. "Modern Athletic, Semi-Bold", "25% / 50%, Flat: 2.0%", "GPS: 200m, Alt: 21s", "X-Axis: Distance", "8 Cards, 6 Fields active").
       - Trailing Expand/Collapse Indicator with animated transition.
     - Content expansion managed via smooth `AnimatedVisibility`.
     - Extensible architecture: Decoupled subsection composables so future tuning additions can be added without modifying the accordion container structure.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Decompose `AdvancedTuningDialog.kt` into a modular, extensible subsection architecture.
  * Implement `TuningAccordionSection` composable: Material 3 card container, rounded corners (12.dp), subtle outlineVariant border, zero tonal elevation (`REQ-UI-218`), clickable header row with category icon, title, active-value summary subtitle, and animated expand/collapse chevron.
  * Structure settings into 5 canonical subsections:
    1. **Cockpit & Typografie**: Font family dropdown, font boldness segmented chips, live preview HUD card.
    2. **AMOLED-Akkuschoner & Helligkeit**: Full dimming, medium dimming, flat slope threshold, steep slope threshold, wake-up duration, downward damping delay sliders.
    3. **Sensoren, GPS & Filter**: GPS horizontal accuracy threshold, altitude smoothing window, slope minimum speed sliders.
    4. **Aftermath & Analyse**: Profile X-axis domain chips (Distance vs Time).
    5. **Workout-Masken & Detailkarten**: 8 workout card section toggles, 6 edit workout field toggles.
  * Provide dynamic, live-updating subtitle summaries for each section that immediately reflect slider adjustments, chip selections, or toggle switches.
  * Retain advisory warning card at the top.
  * Retain unified global footer with single-tap "Reset to Factory Defaults" (`OutlinedButton`) and Save/Cancel actions in `AppBottomSheetContent`.
  * Ensure 100% 9-language localization parity (EN, DE, ES, FR, IT, JA, NL, PL, PT) for any new section headers or subtitle strings.
  * Maintain all underlying DataStore persistence keys, default values, and reactive update flows.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Do not change any tuning preference keys, default constants, or clamping ranges.
  * Do not change the logic or behavior of `TuningPreferencesDataStore.kt` or `MyPreferenceManager.kt`.
  * Do not change how downstream consumers (`BatterySaverController.kt`, `SensorGridScreen.kt`, `WorkoutSummary.kt`, `ElevationProfile.kt`, etc.) consume configurations.
  * Do not alter the navigation drawer or Display Settings dialog routing.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

* **Original Requirement ID & Target**: `REQ-SET-073` (*Dedicated Advanced Tuning Preferences Screen with Parameter Clamping and Reset-to-Defaults*), `REQ-UI-201` (*Profile X-Axis Domain Preference*), `REQ-UI-212` (*Configurable Cockpit Typography*), and `REQ-UI-216` (*Relocation of Workout Card Sections and Edit Workout Fields Customization Toggles*).
* **Historical Origin & Commit Trace**:
  - `REQ-SET-073`: Sprint 2026-40.4 (`ATT-1268`), established `AdvancedTuningDialog.kt` and initial slider items.
  - `REQ-UI-201`: Sprint 2026-40.5 (`ATT-1391`), introduced Category 4 (*Aftermath & Profile Analytics*).
  - `REQ-UI-212`: Sprint 2026-40.7 (`ATT-1751`), introduced Category 5 (*Cockpit Typography*).
  - `REQ-UI-216`: Sprint 2026-40.7 (`ATT-1815`), relocated 14 toggles into Category 4.
* **Root Reason for Existing Formulation**:
  - Each prior ticket added controls sequentially to the end of the `Column` in `AdvancedTuningDialog.kt`. While appropriate during incremental development, the cumulative total of 25+ parameters and preview cards created an unmanageable flat list that degraded usability.
* **Preservation of Core Invariants**:
  - `REQ-SET-073`'s advisory warning card, helper descriptions, default value tags (`Default: X`), and factory reset capability remain 100% strictly preserved.
  - `REQ-UI-218`'s zero tonal elevation standard (`0.dp`) and clean surface background container colors are preserved on accordion cards.
  - All 25+ configurable parameters remain fully functional and reactive.

---

## 5. Architectural Strategy & High-Level Solution

### Component Architecture:
```
AdvancedTuningDialog
├── AppBottomSheetContent (title = Advanced Tuning, icon = Tune, actions = SaveCancel)
│   └── Column (verticalArrangement = 12.dp)
│       ├── Advisory Warning Card (Icons.Default.Warning)
│       ├── Accordion 1: Cockpit & Typografie (CockpitTypographySection)
│       ├── Accordion 2: AMOLED-Akkuschoner & Helligkeit (AmoledBatterySaverSection)
│       ├── Accordion 3: Sensoren, GPS & Filter (SensorsGpsFilterSection)
│       ├── Accordion 4: Aftermath & Analyse (AftermathAnalysisSection)
│       ├── Accordion 5: Workout-Masken & Detailkarten (WorkoutMasksAndCardsSection)
│       └── Reset to Factory Defaults Button (Icons.Default.RestartAlt)
```

### Subsection Details:
1. **`TuningAccordionSection`**:
   - Reusable container composable taking:
     - `icon: ImageVector`
     - `title: String`
     - `subtitle: String`
     - `isExpanded: Boolean`
     - `onToggle: () -> Unit`
     - `content: @Composable () -> Unit`
   - Animated chevron rotation (`animateFloatAsState` from 0° to 180°).
   - `AnimatedVisibility(visible = isExpanded, enter = expandVertically(), exit = shrinkVertically())`.
   - Card shape: `RoundedCornerShape(12.dp)`.
   - Container color: `MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)`.
   - Border: `1.dp` solid `MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)`.

2. **Expanded State Management**:
   - State: `var expandedSection by rememberSaveable { mutableStateOf<TuningSection?>(TuningSection.COCKPIT_TYPOGRAPHY) }` (or multi-expanded `setOf<TuningSection>`).
   - Using a set of expanded sections (`var expandedSections by rememberSaveable { ... }`) allows users to have multiple sections open simultaneously if desired, with clean individual toggling. Default: first section open or all collapsed with summaries visible.

3. **Active Value Subtitle Generation**:
   - **Cockpit**: `"${stringResource(cockpitFontFamily.getDisplayNameRes())}, ${stringResource(cockpitFontWeight.getDisplayNameRes())}"`
   - **Battery Saver**: `"${(fullDimFactor * 100).roundToInt()}% / ${(mediumDimFactor * 100).roundToInt()}%, Flat: ${String.format(Locale.getDefault(), "%.1f%%", slopeFlat)}, Steep: ${String.format(Locale.getDefault(), "%.1f%%", slopeSteep)}"`
   - **Sensors & GPS**: `"GPS: ${gpsAccuracy.roundToInt()}m, Alt: ${altitudeWindowSec}s, Speed: ${String.format(Locale.getDefault(), "%.1f m/s", slopeMinSpeed)}"`
   - **Aftermath**: `"${stringResource(R.string.tuning_profile_x_axis_title)}: ${if (profileXAxisDomain == ProfileXAxisDomain.DISTANCE) stringResource(R.string.tuning_profile_x_axis_distance) else stringResource(R.string.tuning_profile_x_axis_time)}"`
   - **Workout Masks & Cards**: `"${activeCardCount}/8 Cards, ${activeFieldCount}/6 Fields"`

---

## 6. Acceptance Criteria (Given-When-Then)

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

## 7. Forensic Verification & Invariant Checklist

- [x] Advisory warning notice card preserved at top.
- [x] All 25+ parameters retained with full functional parity.
- [x] Reset to Factory Defaults retained and operational.
- [x] Save and Cancel actions retained in bottom sheet header/actions.
- [x] Material 3 zero tonal elevation preserved (`REQ-UI-218`).
- [x] 9-language localization parity maintained across all locales.
