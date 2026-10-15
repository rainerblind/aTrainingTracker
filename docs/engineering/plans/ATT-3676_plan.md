# Stage 3: Implementation Plan - ATT-3676: Expose default zone card display mode selector in Advanced Settings and wire it to workout summary zone cards

**Ticket**: [ATT-3676](https://rainerblind.atlassian.net/browse/ATT-3676)  
**Sub-task**: [ATT-3700](https://rainerblind.atlassian.net/browse/ATT-3700) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `None`  
**Active Sprint**: `2026-42.5`  
**Requirement Mapping**: `REQ-UI-372`, `REQ-UI-372.8`, `REQ-UI-262`, `REQ-TRK-018`  
**Test Mapping**: `TST-UI-335`, `TST-UI-335.8`, `TST-UI-335.9`, `TST-UI-335.10`, `TST-UI-335.11`, `TST-UI-221.1`  
**Branch**: `feature/ATT-3676`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-15  

---

## 1. Problem Description & Background

In `ATT-3390` (Sprint 2026-42.3), three-way zone card display modes (`Stacked Bar`, `Columns`, `Histogram`) were introduced with interactive in-card segmented buttons and persisted to DataStore in `WorkoutCardSectionPreferences.zoneCardDisplayMode` (keyed by `WORKOUT_CARD_ZONE_DISPLAY_MODE`). However:
1. No configuration UI was surfaced in `WorkoutMasksAndCardsSection.kt` under Advanced Settings (Tuning Dialog), leaving users unable to configure their default preference.
2. `WorkoutSummary.kt` and `TrackOnMapScreen.kt` initialize zone card display mode to hardcoded `ZoneCardDisplayMode.FIVE_ZONES` / `COLUMNS` in local state, ignoring `WorkoutCardSectionPreferences.zoneCardDisplayMode`.
3. If `HISTOGRAM` is set as default or selected on a workout lacking fine-grained telemetry data (`distribution.histogram == null`), the card renders an empty dash ("–") and all segmented buttons appear unselected.

This plan details the implementation to expose the 3-way segmented button selector in Advanced Settings, wire reactive preferences to summary views with state precedence, and enforce fallback to `COLUMNS` when histogram data is absent.

---

## 2. Traceability & Requirements Mapping

* **Requirements**:
  - `REQ-UI-372`: Post-Workout Heart Rate & Power Zone Distribution Visualization (Parent).
  - `REQ-UI-372.8`: Configurable Global Zone Card Display Mode & Fallback Architecture (New Child).
  - `REQ-UI-262`: Modular Architecture for Advanced Tuning Dialog (Files strictly $\le 400$ lines).
  - `REQ-TRK-018`: Post-Workout Visual Telemetry & Geographic Map Screen Integration (Preserved).
* **Test Specifications**:
  - `TST-UI-335`: Post-Workout Heart Rate & Power Zone Distribution Verification Suite.
  - `TST-UI-335.8`: Default Zone Card Display Mode Preference Persistence & Selector Contract.
  - `TST-UI-335.9`: Summary View Reactive Preference & Local Override State Precedence.
  - `TST-UI-335.10`: Domain Fallback Guard on Absent Histogram Telemetry.
  - `TST-UI-335.11`: Localization Parity across all 9 supported locales.
  - `TST-UI-221.1`: Structural modularity and file size constraint tests (`AdvancedTuningModularityTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Strict File Size Constraint (REQ-UI-262)**: `WorkoutMasksAndCardsSection.kt` currently contains 357 lines. The addition of the dedicated zone card display mode sub-setting section must be achieved in $\le 35$ lines, maintaining total lines strictly $< 400$ (target: ~389 lines) to comply with `AdvancedTuningModularityTest`.
2. **Three-Tier Precedence Invariant**:
   - **Tier 1 (Interactive Manual Override)**: Explicit user interaction with in-card segmented buttons overrides default preferences for the current view session.
   - **Tier 2 (Reactive Global Default)**: In the absence of session-local interaction (`null`), cards reactively reflect `preferences.zoneCardDisplayMode`.
   - **Tier 3 (Domain Fallback Guard)**: When effective mode is `HISTOGRAM` and `distribution.histogram == null`, cards resolve to `ZoneCardDisplayMode.COLUMNS` to prevent empty rendering or unselected button states.
3. **9-Language Localization Parity**: `settings_zone_card_display_mode_title` must be declared in all 9 supported locale resource files (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
4. **DataStore Backward Compatibility**: Key `WORKOUT_CARD_ZONE_DISPLAY_MODE` and enum names (`STACKED_BAR`, `COLUMNS`, `HISTOGRAM`, `FIVE_ZONES`) remain unchanged. Default value remains `ZoneCardDisplayMode.COLUMNS`.
5. **Human Decision Gate Invariant**: Parent ticket `ATT-3676` will not be transitioned to `Erledigt` by automated agents; only transitioned to `Final Review (Human)` upon verification completion.

---

## 4. Proposed Architectural Changes

### Component 1: Localization Resources (`app/src/main/res/values*/strings.xml`)
* Add localized string resource `settings_zone_card_display_mode_title`:
  - `values/strings.xml`: `Zone Card Display Mode`
  - `values-de/strings.xml`: `Zonenkarten-Anzeigemodus`
  - `values-es/strings.xml`: `Modo de visualización de tarjetas de zonas`
  - `values-fr/strings.xml`: `Mode d\'affichage des cartes de zones`
  - `values-it/strings.xml`: `Modalità visualizzazione schede zone`
  - `values-ja/strings.xml`: `ゾーンカード表示モード`
  - `values-nl/strings.xml`: `Zonakaart-weergavemodus`
  - `values-pl/strings.xml`: `Tryb wyświetlania kart stref`
  - `values-pt/strings.xml`: `Modo de exibição dos cartões de zonas`

### Component 2: Settings UI (`WorkoutMasksAndCardsSection.kt`)
* In `WorkoutMasksAndCardsSection`, add a conditional sub-setting section when zone distribution cards are enabled:
  ```kotlin
  if (workoutCardPrefs.showHeartRateZones || workoutDetailPrefs.showHeartRateZones ||
      workoutCardPrefs.showPowerZones || workoutDetailPrefs.showPowerZones) { ... }
  ```
* Render a `SingleChoiceSegmentedButtonRow` using a loop over:
  - `ZoneCardDisplayMode.STACKED_BAR` -> `R.string.zone_mode_stacked_bar`
  - `ZoneCardDisplayMode.COLUMNS` -> `R.string.zone_mode_columns`
  - `ZoneCardDisplayMode.HISTOGRAM` -> `R.string.zone_mode_histogram`
* Dispatch `onWorkoutCardPrefsChange(workoutCardPrefs.copy(zoneCardDisplayMode = mode))`.

### Component 3: Summary Screens State Precedence (`WorkoutSummary.kt`, `TrackOnMapScreen.kt`)
* In `WorkoutSummary.kt`:
  - Refactor `hrZoneDisplayMode` and `powerZoneDisplayMode` from hardcoded initial value `FIVE_ZONES` to nullable `ZoneCardDisplayMode?` initialized to `null`.
  - Derive effective modes:
    ```kotlin
    val effectiveHrZoneDisplayMode = hrZoneDisplayMode ?: preferences.zoneCardDisplayMode
    val effectivePowerZoneDisplayMode = powerZoneDisplayMode ?: preferences.zoneCardDisplayMode
    ```
  - Pass effective modes to `HeartRateZoneDistributionCard` and `PowerZoneDistributionCard`, retaining `onDisplayModeChange = { hrZoneDisplayMode = it }`.
* In `TrackOnMapScreen.kt`:
  - Collect `workoutCardPreferencesFlow` as `persistedCardPrefs`.
  - Refactor state variables to nullable `ZoneCardDisplayMode?` defaulting to `null`.
  - Derive effective modes using `persistedCardPrefs.zoneCardDisplayMode`.
  - Pass effective modes to `MapDetailLayout` and child distribution cards.

### Component 4: Domain Fallback Guard (`HeartRateZoneDistributionCard.kt`, `PowerZoneDistributionCard.kt`)
* In `HeartRateZoneDistributionCard` and `PowerZoneDistributionCard`:
  - Compute `rawDisplayMode = displayMode ?: internalDisplayMode`.
  - Compute `effectiveDisplayMode = if (rawDisplayMode == ZoneCardDisplayMode.HISTOGRAM && distribution.histogram == null) ZoneCardDisplayMode.COLUMNS else rawDisplayMode`.
  - Use `effectiveDisplayMode` for both chart body rendering and segmented button row selection state.

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
* **Closest Existing Reference Screen**: `WorkoutMasksAndCardsSection.kt` (lines 300–336, Lap Display Mode Sub-Setting Section) and `AdvancedTuningScreen.kt`.
* **Reused Components**: Material 3 `SingleChoiceSegmentedButtonRow`, `SegmentedButton`, `SegmentedButtonDefaults.itemShape()`, `HorizontalDivider`, `Text`, `Column`.
* **Theme Tokens**:
  - Divider color: `MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)`.
  - Label style: `MaterialTheme.typography.bodySmall`, `FontWeight.Medium`, `color = MaterialTheme.colorScheme.onSurfaceVariant`.
  - Padding: `horizontal = 8.dp`, `vertical = 4.dp`, `spacedBy(6.dp)`.
* **Justification for Any New One-Off Style**: No new one-off styles are introduced; 100% compliant with existing sub-setting rows in `WorkoutMasksAndCardsSection.kt` and `docs/design_guidelines.md` (Section 1.1).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Add Localized Title String to 9 Locales
* Edit `app/src/main/res/values*/strings.xml` to add `settings_zone_card_display_mode_title`.
* Target test: `ZoneCardDisplayModeLocalizationTest.kt`.

### Step 2: Implement 3-Way Segmented Selector in `WorkoutMasksAndCardsSection.kt`
* Add conditional sub-setting section for zone card display mode in `WorkoutMasksAndCardsSection.kt`.
* Verify total file lines remain strictly $< 400$ (target $\le 390$ lines).
* Target test: `./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningModularityTest`.

### Step 3: Wire Reactive Preferences & State Precedence in `WorkoutSummary.kt` and `TrackOnMapScreen.kt`
* Update state declarations to `ZoneCardDisplayMode?` with fallback to `preferences.zoneCardDisplayMode`.
* Target test: `WorkoutSummaryZoneDisplayModeTest.kt`.

### Step 4: Implement Tier 3 Domain Fallback Guard in Distribution Cards
* Update `HeartRateZoneDistributionCard.kt` and `PowerZoneDistributionCard.kt` to resolve `HISTOGRAM` to `COLUMNS` when `distribution.histogram == null`.
* Target test: `ZoneCardDisplayModeFallbackTest.kt`.

### Step 5: Unit Test Suite Implementation & Execution
* Add unit tests in `WorkoutCardSectionPreferencesTest.kt`, `ZoneCardDisplayModeFallbackTest.kt`, `WorkoutSummaryZoneDisplayModeTest.kt`, and `ZoneCardDisplayModeLocalizationTest.kt`.
* Execute all unit tests and verify clean pass.

---

## 6. Verification & Rollback Plan

* **Verification**:
  - Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.*"`.
  - Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.zones.*"`.
  - Run full suite `./gradlew testDebugUnitTest`.
* **Rollback Plan**:
  - Revert commits on `feature/ATT-3676` back to `sprint/2026-42.5` head (`git reset --hard 62963a67`).
