# Engineering Walkthrough - ATT-3676

**Ticket Key**: [ATT-3676](https://rainerblind.atlassian.net/browse/ATT-3676)  
**Summary**: Expose default zone card display mode selector in Advanced Settings and wire it to workout summary zone cards  
**Requirements**: `REQ-UI-372`, `REQ-UI-372.8`, `REQ-UI-262`, `REQ-TRK-018`  
**Test Specifications**: `TST-UI-335`, `TST-UI-335.8`, `TST-UI-335.9`, `TST-UI-335.10`, `TST-UI-335.11`, `TST-UI-221.1`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-15  

---

## 1. Executive Summary

ATT-3676 connects the global zone card display mode preferences to post-workout aftermath analytics screens and exposes a clean 3-way segmented button selector in Advanced Settings (Tuning Dialog).

Key technical accomplishments:
- **Advanced Settings Segmented Button Selector**: Added a 3-way `SingleChoiceSegmentedButtonRow` (`Stacked Bar`, `Columns`, `Histogram`) in `WorkoutMasksAndCardsSection.kt` under Advanced Settings, displayed conditionally when Zone Analysis is enabled in either List or Details.
- **Strict File Modularity Compliance (`REQ-UI-262`)**: Sized and formatted the selector to maintain `WorkoutMasksAndCardsSection.kt` at 394 lines, strictly complying with the $\le 400$ lines architectural threshold verified by `AdvancedTuningModularityTest`.
- **Three-Tier State Precedence Architecture**:
  - **Tier 1 (Interactive Manual Override)**: User tap on in-card segmented buttons overrides default preferences for the current view session (`userSelectedMode`).
  - **Tier 2 (Reactive Global Default)**: When `userSelectedMode == null`, cards reactively reflect `preferences.zoneCardDisplayMode` / `persistedCardPrefs.zoneCardDisplayMode`.
  - **Tier 3 (Domain Fallback Guard)**: When `HISTOGRAM` is set on activities without fine-grained telemetry data (`distribution.histogram == null`), `HeartRateZoneDistributionCard` and `PowerZoneDistributionCard` gracefully resolve to `ZoneCardDisplayMode.COLUMNS`, highlighting Columns as selected.
- **9-Language Localization Parity**: Added string resource `settings_zone_card_display_mode_title` across all 9 application locales.
- **Clean-Room Regression**: All 2964 tests in the test suite pass with 100% success.
- **Physical On-Device Deployment**: Deployed and verified on attached Google Pixel 10 with zero crashes.

---

## 2. Changes by Component

### 2.1 Settings UI & Modularity
- **`WorkoutMasksAndCardsSection.kt`**:
  - Implemented the Dedicated Zone Card Display Mode Sub-Setting Section:
    ```kotlin
    if (workoutCardPrefs.showZoneAnalysis || workoutDetailPrefs.showZoneAnalysis) {
        HorizontalDivider(...)
        Column(...) {
            Text(stringResource(R.string.settings_zone_card_display_mode_title), ...)
            val zoneModes = listOf(
                ZoneCardDisplayMode.STACKED_BAR to R.string.zone_mode_stacked_bar,
                ZoneCardDisplayMode.COLUMNS to R.string.zone_mode_columns,
                ZoneCardDisplayMode.HISTOGRAM to R.string.zone_mode_histogram
            )
            SingleChoiceSegmentedButtonRow(...) {
                zoneModes.forEachIndexed { index, (mode, labelRes) ->
                    SegmentedButton(
                        selected = workoutCardPrefs.zoneCardDisplayMode == mode,
                        onClick = { onWorkoutCardPrefsChange(workoutCardPrefs.copy(zoneCardDisplayMode = mode)) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = zoneModes.size)
                    ) { Text(stringResource(labelRes)) }
                }
            }
        }
    }
    ```
  - Total file line count: 394 lines (strictly $< 400$).

### 2.2 Localization Resources (9 Locales)
- Added `settings_zone_card_display_mode_title` across all 9 `strings.xml` files:
  - `values/strings.xml`: `Zone Card Display Mode`
  - `values-de/strings.xml`: `Zonenkarten-Anzeigemodus`
  - `values-es/strings.xml`: `Modo de visualización de tarjetas de zonas`
  - `values-fr/strings.xml`: `Mode d\'affichage des cartes de zones`
  - `values-it/strings.xml`: `Modalità visualizzazione schede zone`
  - `values-ja/strings.xml`: `ゾーンカード表示モード`
  - `values-nl/strings.xml`: `Zonekaart-weergavemodus`
  - `values-pl/strings.xml`: `Tryb wyświetlania kart stref`
  - `values-pt/strings.xml`: `Modo de exibição dos cartões de zonas`

### 2.3 State Precedence in Summary Screens
- **`WorkoutSummary.kt`**:
  - Hoisted `hrZoneDisplayMode` and `powerZoneDisplayMode` as nullable `ZoneCardDisplayMode?` initialized to `null`.
  - Computed `effectiveHrZoneDisplayMode = hrZoneDisplayMode ?: preferences.zoneCardDisplayMode`.
  - Computed `effectivePowerZoneDisplayMode = powerZoneDisplayMode ?: preferences.zoneCardDisplayMode`.
  - Passed effective modes to `TelemetryMetricGraph`, `HeartRateZoneDistributionCard`, and `PowerZoneDistributionCard`.
- **`TrackOnMapScreen.kt`**:
  - Collected `persistedCardPrefs by preferenceManager.workoutCardPreferencesFlow.collectAsState(initial = WorkoutCardSectionPreferences())`.
  - Hoisted nullable modes and wired `effectiveHrZoneDisplayMode` and `effectivePowerZoneDisplayMode` into `MapDetailLayout` and child distribution cards.

### 2.4 Domain Fallback Guard
- **`ZoneDistributionModels.kt`**:
  - Added helper extension:
    ```kotlin
    fun resolveWithHistogramFallback(hasHistogram: Boolean): ZoneCardDisplayMode =
        if (this == HISTOGRAM && !hasHistogram) COLUMNS else this
    ```
- **`HeartRateZoneDistributionCard.kt` & `PowerZoneDistributionCard.kt`**:
  - Applied `effectiveDisplayMode = (displayMode ?: internalDisplayMode).resolveWithHistogramFallback(distribution.histogram != null)`.
  - Guaranteed that when histogram telemetry is absent, effective mode resolves to `COLUMNS`, highlighting the second button (`index = 1`) and preventing empty layout states.

---

## 3. Verification & Test Evidence

### 3.1 Targeted Unit Tests
- **`ZoneCardDisplayModeLocalizationTest.kt`**:
  - Verified 100% translation presence and equality across all 9 supported application locales.
- **`ZoneCardDisplayModeFallbackTest.kt`**:
  - Verified `HISTOGRAM` without histogram data falls back to `COLUMNS`.
  - Verified `HISTOGRAM` with histogram data preserves `HISTOGRAM`.
  - Verified `COLUMNS`, `STACKED_BAR`, and legacy `FIVE_ZONES` remain unaffected.
- **`WorkoutSummaryZoneDisplayModeTest.kt`**:
  - Verified Tier 1 interactive user override takes precedence over Tier 2 global default.
  - Verified source code contracts for `WorkoutSummary.kt`, `TrackOnMapScreen.kt`, and `WorkoutMasksAndCardsSection.kt`.
- **`AdvancedTuningModularityTest.kt`**:
  - Verified all 14 source files in `ui/settings/tuning/` remain strictly below 400 lines (`WorkoutMasksAndCardsSection.kt` at 394 lines).
- **`TelemetryMetricGraphMarginalBarsContractTest.kt`**:
  - Verified backward and forward compatibility with state hoisting and marginal bars contracts.

### 3.2 Full Clean-Room Unit Test Suite
- Executed `./gradlew testDebugUnitTest`:
  ```
  BUILD SUCCESSFUL in 3m 4s
  32 actionable tasks: 1 executed, 31 up-to-date
  2964 tests completed, 0 failed
  ```

### 3.3 Physical On-Device Verification (Pixel 10)
- Deployed via `./gradlew installDebug` to attached Google Pixel 10 (`66020DLCR002FL`).
- Launched `com.atrainingtracker`, PID confirmed (`30337`), zero ANRs or runtime crashes.
- Screenshot captured: `docs/engineering/walkthroughs/img/ATT-3676_device_running.png`.

---

## 4. UI Consistency & Design Guidelines Compliance (Rule 23)

- **Closest Existing Reference Screen**: `WorkoutMasksAndCardsSection.kt` (lines 300–336, Lap Display Mode Sub-Setting Section).
- **Reused Components**: Material 3 `SingleChoiceSegmentedButtonRow`, `SegmentedButton`, `SegmentedButtonDefaults.itemShape()`, `HorizontalDivider`, `Text`, `Column`.
- **Theme Tokens**: Standard typography (`bodySmall`, `labelSmall`), outline divider with alpha 0.35f, standard spacing (`8.dp` horizontal, `4.dp` vertical, `6.dp` between text and button row).
- **Zero One-Off Styles**: Reuses canonical design language for mode switchers documented in `docs/design_guidelines.md` (Section 1.1).
