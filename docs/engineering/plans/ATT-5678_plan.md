# Stage 3: Implementation Plan - ATT-5678: Trademark-Free Physiology Terminology

**Ticket**: [ATT-5678](https://rainerblind.atlassian.net/browse/ATT-5678)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*) | **Story Points**: 2 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.10` | **Branch**: `feature/ATT-5678`  
**Requirement Mapping**: `REQ-UI-459` (`REQ-UI-459.1`–`REQ-UI-459.5`) | **Test Mapping**: `TST-UI-459` (`TST-UI-459.1`–`TST-UI-459.5`)  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-07  

---

## 1. Problem Description & Background
Peaksware, LLC holds registered trademarks on cycling/endurance terms: `Normalized Power®` (`NP®`), `Intensity Factor®` (`IF®`), `Training Stress Score®` (`TSS®`), and `Performance Management Chart®` (`PMC®`), alongside `CTL`, `ATL`, and `TSB`. To eliminate legal risk and align with industry standards (Strava, Intervals.icu, GoldenCheetah), aTrainingTracker must migrate all user-facing labels across all 9 locales to trademark-free terminology (`WP`, `RI`, `Load`, `Load / Form`, `Fitness (42d)`, `Fatigue (7d)`, `Form (Balance)`) while preserving 100% calculation invariance, database schema stability, and backup backward compatibility.

---

## 2. Invariants & Preserved Behavior
1. **Mathematical Invariance (`REQ-UI-459.5`)**: Calculation formulas in `PowerPhysiologyCalculator.kt` and `CyclingPowerMetricsDevice.java` remain 100% unchanged.
2. **Schema & DTO Stability (`REQ-UI-459.5`)**: Internal SQLite columns, sensor enums (`SensorType.NORMALIZED_POWER`, `TSS`), and DTO fields (`normalizedPowerWatts`, `tss`) remain invariant.
3. **Format Specifier Parity (`REQ-UI-459.2`, `REQ-UI-459.3`)**: Positional format specifiers (`%1$+.1f`, `%1$.0f`, `%1$.2f`) remain intact across all 9 locales.
4. **Non-Translatable Badges (`REQ-UI-459.1`)**: `normalized_power_short` (`WP`), `intensity_factor_short` (`RI`), and `tss_short` (`Load`) remain strictly in base `values/strings.xml` with `translatable="false"` (`TST-STR-019.2`).
5. **No Inline Emojis (`ATT-5400`)**: All updated strings remain strictly text-only without decorative emojis.

---

## 3. UI Consistency & Architectural Strategy
* **Reference Screens**: `WorkoutDetails.kt`, `AthleteProfileScreen.kt`, `PhysiologyModelsSection.kt`, `TipsGuideScreen.kt`.
* **Theme Tokens**: Standard Material 3 typography and semantic colors (`MaterialTheme.colorScheme.*`). Badges and metric cells continue utilizing `MetricItem` and `AltitudeRow` layouts without visual regression.
* **New One-Off Styles**: None. Pure string resource updates and unit test mock synchronization.

---

## 4. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Base English String Resources (`REQ-UI-459.1`–`REQ-UI-459.4`)
* **File**: `app/src/main/res/values/strings.xml`
* **Changes**: Update 20 string keys to trademark-free terminology:
  - `normalized_power` $\rightarrow$ "Weighted Power", `normalized_power_short` $\rightarrow$ "WP" (`translatable="false"`)
  - `intensity_factor` $\rightarrow$ "Relative Intensity", `intensity_factor_short` $\rightarrow$ "RI" (`translatable="false"`)
  - `tss` $\rightarrow$ "Training Load", `tss_short` $\rightarrow$ "Load" (`translatable="false"`)
  - `workout_power_metrics_*_format`: `WP: %1$.0f W`, `RI: %1$.2f`, `Load: %1$.0f`
  - `athlete_profile_pmc_*`: "Training Load & Form (Fitness / Fatigue / Form)", "Fitness (42d)", "Fatigue (7d)", "Form (Balance)", `%1$+.1f/wk`
  - `tuning_physiology_*`: "Power Training Metrics (WP, RI, Load)", desc, "Load / Form", "Training Load & Form", desc
  - `tip_training_zones_desc`: replace NP, IF, TSS with WP, RI, Load.

### Step 2: 8 Localized Resource Files Parity (`REQ-UI-459.1`–`REQ-UI-459.4`)
* **Files**: `app/src/main/res/values-{de,es,fr,it,ja,nl,pl,pt}/strings.xml`
* **Changes**: Update the corresponding 17 translatable keys in each locale to localized trademark-free terminology with matching positional format specifiers.

### Step 3: Unit Test & Subtitle Mock Updates (`TST-UI-459.2`)
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningAccordionTest.kt`
* **Changes**: Update mock return value for `tuning_physiology_pmc_short` to `"Load / Form"` and assert formatted subtitle `"Coggan, Load / Form, EF / Decoupling, CP / W'"`.

### Step 4: User Guide Documentation Updates
* **Files**: `docs/guide/en/07-physiology-training-zones.md`, `docs/guide/de/07-physiology-training-zones.md`
* **Changes**: Update text references explaining Weighted Power (WP), Relative Intensity (RI), Training Load (Load), and Training Load & Form (Fitness / Fatigue / Form).

---

## 5. Verification Commands (`TST-UI-459.5`)
* `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.ui.athleteprofile.AthleteProfileContractAndLocalizationTest" --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningAccordionTest" --tests "com.atrainingtracker.trainingtracker.ui.help.TipsGuideScreenContractTest"`
* `python3 tools/verify_requirement_governance.py`
