# Stage 1 Analysis: ATT-3773 — Configure default Zone Card Display Mode independently for Workout Summary and Workout Details

## 1. Executive Summary & Problem Statement
During the Sprint `2026-42.5` Joint Review of `ATT-3676`, the user identified two UX limitations in how Zone Distribution cards (`HeartRateZoneDistributionCard`, `PowerZoneDistributionCard`) are configured and rendered across the Workout Summary (list cards) and Workout Details (`TrackOnMapScreen`) views:
1. **Shared Global Display Mode Default**: Currently, `WorkoutCardSectionPreferences.zoneCardDisplayMode` (default `COLUMNS`) serves as the single shared default for both the Workout Summary list (`WorkoutSummary.kt`) and the full-screen Workout Details view (`TrackOnMapScreen.kt`), whereas the athlete wants a compact default (`ZoneCardDisplayMode.STACKED_BAR` / "Balken") in the Workout Summary list and a detailed default (`ZoneCardDisplayMode.HISTOGRAM` / "Histogramm", with automatic fallback to `COLUMNS` when fine-grained histogram data is absent) in Workout Details.
2. **Mode Switcher Clutter in Workout Summary**: Rendering the 3-way `SingleChoiceSegmentedButtonRow` mode switcher at the bottom of every HR and Power Zone Distribution card inside the scrollable Workout Summary list (`WorkoutSummary.kt`) adds vertical height and visual clutter. The user requested that Workout Summary cards omit the mode switcher row (`showModeSelector = false`) so the summary remains compact and dense, while preserving the interactive mode switcher (`showModeSelector = true`) in Workout Details (`TrackOnMapScreen.kt`).

## 2. Forensic Root Cause Analysis (RCA)
1. **`MyPreferenceManager.kt`**:
   - `WorkoutCardSectionPreferences` (line 62) defines `val zoneCardDisplayMode: ZoneCardDisplayMode = ZoneCardDisplayMode.COLUMNS`, and `workoutCardPreferencesFlow` (lines 154–162) falls back to `ZoneCardDisplayMode.COLUMNS` when `WORKOUT_CARD_ZONE_DISPLAY_MODE` is unset.
   - `WorkoutDetailPreferences` (lines 68–78) has no `zoneCardDisplayMode` property, and `MyPreferenceManager` has no `WORKOUT_DETAIL_ZONE_DISPLAY_MODE` DataStore key.
2. **`TrackOnMapScreen.kt`**:
   - Lines 157–158 resolve `effectiveHrZoneDisplayMode` and `effectivePowerZoneDisplayMode` against `persistedCardPrefs.zoneCardDisplayMode` instead of `activeDetailPrefs.zoneCardDisplayMode`.
3. **`WorkoutMasksAndCardsSection.kt`**:
   - Lines 340–376 render only a single `SingleChoiceSegmentedButtonRow` bound to `workoutCardPrefs.zoneCardDisplayMode`, rather than independent selectors for Summary (`workoutCardPrefs.zoneCardDisplayMode` when `workoutCardPrefs.showZoneAnalysis` is enabled) and Details (`workoutDetailPrefs.zoneCardDisplayMode` when `workoutDetailPrefs.showZoneAnalysis` is enabled).
   - Furthermore, `WorkoutMasksAndCardsSection.kt` is currently 398 lines (`< 400` line modularity cap enforced by `AdvancedTuningModularityTest`), requiring concise helper extraction when adding the second selector.
4. **`HeartRateZoneDistributionCard.kt`, `PowerZoneDistributionCard.kt`, and `WorkoutSummary.kt`**:
   - `HeartRateZoneDistributionCard` (line 129) and `PowerZoneDistributionCard` (line 129) unconditionally render the bottom `SingleChoiceSegmentedButtonRow` whenever `distribution.entries.isNotEmpty()`, lacking a `showModeSelector: Boolean = true` parameter.
   - `WorkoutSummary.kt` (lines 391–410) renders `HeartRateZoneDistributionCard` and `PowerZoneDistributionCard` without suppressing the mode switcher row.

## 3. Chesterton's Fence Archaeology (`REQ-PRO-022`)
- **Original Requirement ID & Target**: Amends `REQ-UI-372` (*Horizontal Stacked Distribution Bar Component & 3-Way Zone Display Mode Switching in Workout Summary Cards*, `ATT-3390` & `ATT-3676`) and `REQ-UI-386` (`ATT-3771`), formulated as net-new requirement `REQ-UI-388`. Targets `MyPreferenceManager.kt`, `HeartRateZoneDistributionCard.kt`, `PowerZoneDistributionCard.kt`, `WorkoutSummary.kt`, `TrackOnMapScreen.kt`, and `WorkoutMasksAndCardsSection.kt`.
- **Historical Origin & Commit Trace**:
  - `ATT-3390` (Sprint `2026-42.4`, commit `38706240`) introduced `ZoneCardDisplayMode.STACKED_BAR` and `WORKOUT_CARD_ZONE_DISPLAY_MODE` in `WorkoutCardSectionPreferences` with default `COLUMNS`.
  - `ATT-3676` (Sprint `2026-42.5`, commit `b989e1ce`) added the default mode selector in `WorkoutMasksAndCardsSection.kt` and wired both `WorkoutSummary.kt` and `TrackOnMapScreen.kt` to `WorkoutCardSectionPreferences.zoneCardDisplayMode`.
- **Root Reason for Existing Formulation**: `ATT-3676` initially used a single shared preference across both Summary and Details before the Sprint `2026-42.5` Joint Review revealed that athletes want a compact legend-free & switcher-free `STACKED_BAR` in the Workout Summary list and a rich interactive `HISTOGRAM` (with switcher) in Workout Details.
- **Preservation of Core Invariants**:
  - Tier 3 domain fallback (`ZoneCardDisplayMode.HISTOGRAM.resolveWithHistogramFallback(distribution.histogram != null)` -> `COLUMNS`) remains 100% intact when `HISTOGRAM` is selected or defaulted for workouts without fine-grained histogram bins.
  - `WorkoutMasksAndCardsSection.kt` strictly remains `< 400` lines (`REQ-UI-262`).
  - Interactive in-card mode switching (`showModeSelector = true`) and single-line `SegmentedButton` typography (`REQ-UI-386` / Rule 38) remain intact in Workout Details (`TrackOnMapScreen.kt`).

## 4. Scope Bounding (`ATT-1250`)
- **In Scope**:
  1. Update `WorkoutCardSectionPreferences.zoneCardDisplayMode` default to `ZoneCardDisplayMode.STACKED_BAR` in `MyPreferenceManager.kt`.
  2. Add `val zoneCardDisplayMode: ZoneCardDisplayMode = ZoneCardDisplayMode.HISTOGRAM` to `WorkoutDetailPreferences` and persist via `WORKOUT_DETAIL_ZONE_DISPLAY_MODE = stringPreferencesKey("workout_detail_zone_display_mode")` in `MyPreferenceManager.kt`.
  3. Add `showModeSelector: Boolean = true` to `HeartRateZoneDistributionCard` and `PowerZoneDistributionCard`, gating the bottom `SingleChoiceSegmentedButtonRow` on `if (showModeSelector && distribution.entries.isNotEmpty())`.
  4. Pass `showModeSelector = false` to `HeartRateZoneDistributionCard` and `PowerZoneDistributionCard` in `WorkoutSummary.kt`.
  5. Bind `effectiveHrZoneDisplayMode` and `effectivePowerZoneDisplayMode` in `TrackOnMapScreen.kt` to `activeDetailPrefs.zoneCardDisplayMode` (retaining `showModeSelector = true`).
  6. Update `WorkoutMasksAndCardsSection.kt` to expose independent Zone Card Display Mode selectors for Summary (`workoutCardPrefs.zoneCardDisplayMode`) and Details (`workoutDetailPrefs.zoneCardDisplayMode`), keeping `WorkoutMasksAndCardsSection.kt` `< 400` lines.
  7. Update `WorkoutCardSectionPreferencesTest.kt` and `WorkoutSummaryZoneDisplayModeTest.kt`.
- **Out of Scope**:
  - Modifying zone calculation logic, histogram bin widths, or SQLite schemas.
