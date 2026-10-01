# Stage 3: Implementation Plan - ATT-1751: [Cockpit/Typography] Configurable Cockpit Font Family & Boldness (Normal, Semi-Bold, Bold) in Advanced Settings

**Ticket**: [[ATT-1751]](https://rainerblind.atlassian.net/browse/ATT-1751)  
**Sub-task**: [[ATT-1831]](https://rainerblind.atlassian.net/browse/ATT-1831) (`[Impl-Plan]`)  
**Parent Epic**: [[ATT-355]](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.7`  
**Requirement Mapping**: `REQ-UI-212` (*Configurable Cockpit Typography (Font Family & Boldness) in Advanced Settings*)  
**Test Mapping**: `TST-UI-166` (*Cockpit Typography Configuration & Rendering Verification*)  
**Branch**: `feature/ATT-1751`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Problem Description & Background

During live workouts (e.g., fast road cycling, trail running, mountain biking), the Cockpit HUD is the athlete's primary telemetry display. Athletes glance at live metrics (heart rate, power, cadence, speed, slope) under variable environmental conditions including bright direct sunlight, bike mount vibrations, and varying viewing distances.

Currently, `SensorFieldView.kt` hardcodes `FontWeight.SemiBold` (`getSensorValueTextStyle`) and relies exclusively on the platform default system typeface (`FontFamily.Default` / Roboto). Athletes cannot adjust font thickness or font family to match their eyesight, bike computer aesthetic preferences, or sunlight readability conditions.

This implementation plan defines the complete architectural construction to introduce:
1. Curated font families: System Default, 7-Segment Digital LCD (`Orbitron`), Modern Athletic Sans-Serif (`Roboto Condensed`), Fixed-width Tabular Monospace (`Roboto Mono`), and Playful (`Comic Neue`).
2. Configurable boldness: Normal, Semi-Bold, and Bold.
3. Decoupled Compose architecture via `LocalCockpitTypography` CompositionLocal.
4. Preference persistence in `TuningPreferencesDataStore.kt` with atomic factory reset.
5. Interactive live preview card in `AdvancedTuningDialog.kt`.
6. 100% 9-language localization parity and unit test suite.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-212` (*Configurable Cockpit Typography (Font Family & Boldness) in Advanced Settings*)
* **Test Mapping**: `TST-UI-166` (*Cockpit Typography Configuration & Rendering Verification*)
* **Foundation Requirements Preserved**:
  * `REQ-UI-171`: High-Contrast Cockpit Typography and Subtle Tile Grid
  * `REQ-UI-181`: Ultra-Large Cockpit Typography Extensions
  * `REQ-UI-106`: Global Localization (9 Languages)
  * `REQ-PRO-016`: ASPICE Quality Gates and Pre-Implementation CLI Check

---

## 3. System Invariants & Preserved Behavior

1. **Default Settings Backward Compatibility**: Fresh installs and existing user updates strictly default to `CockpitFontFamily.SYSTEM_DEFAULT` and `CockpitFontWeight.SEMI_BOLD`, ensuring 100% visual parity.
2. **Decoupled Architecture & Schema Immutability**: Neither `SensorFieldState` nor SQLite database schemas are modified. Typography preferences are consumed strictly via Compose's `LocalCockpitTypography`.
3. **9-Step ViewSize Scale Integrity**: All 9 tile sizes (`XSMALL` through `XXXHUGE`) scale cleanly without line height collisions, negative vertical padding, or unit label overlap.
4. **Offline & JVM Test Resilience**: The typography resolver provides defensive fallbacks to standard Compose font families (`Default`, `Monospace`, `SansSerif`, `Cursive`), preventing `Resources.NotFoundException` during JVM tests or offline operation.
5. **Sub-Task Self-Sufficiency**: Subtask `ATT-1831` transitions directly to `Erledigt` upon automated Gate 3 audit PASS via `freigabe`.
6. **Parent Human Gate Invariance**: Terminal completion of parent `ATT-1751` remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: Font Resources (`app/src/main/res/font/`, `values/preloaded_fonts.xml`)
* Define Google Play Services downloadable font descriptors in `app/src/main/res/font/`:
  * `orbitron.xml` (`name=Orbitron&weight=700`)
  * `roboto_condensed.xml` (`name=Roboto Condensed&weight=700`)
  * `comic_neue.xml` (`name=Comic Neue&weight=700`)
  * `roboto_mono.xml` (`name=Roboto Mono&weight=700`)
* Register font resources in `app/src/main/res/values/preloaded_fonts.xml`.

### Component 2: Cockpit Typography Domain Models & Resolver (`CockpitTypography.kt`)
* Package: `com.atrainingtracker.trainingtracker.ui.tracking.typography`
* Enums:
  * `CockpitFontWeight`: `NORMAL` (`FontWeight.Normal`), `SEMI_BOLD` (`FontWeight.SemiBold`), `BOLD` (`FontWeight.Bold`).
  * `CockpitFontFamily`: `SYSTEM_DEFAULT`, `SEVEN_SEGMENT`, `MODERN_ATHLETIC`, `MONOSPACE`, `PLAYFUL`.
* Safe Resolver: `CockpitTypography.resolveFontFamily(context: Context, family: CockpitFontFamily): FontFamily` with `try-catch` fallback.
* CompositionLocal: `LocalCockpitTypography = compositionLocalOf { CockpitTypographyConfig() }`.

### Component 3: DataStore Persistence (`TuningPreferencesDataStore.kt`, `TuningConfig`)
* Add keys: `KEY_COCKPIT_FONT_FAMILY`, `KEY_COCKPIT_FONT_WEIGHT`.
* Add properties to `TuningConfig` and defaults in `TuningPreferencesDefaults`.
* Update `saveTuningConfig` and `resetToDefaults` to handle cockpit typography.

### Component 4: Advanced Tuning Dialog (`AdvancedTuningDialog.kt`)
* Add "Cockpit-Typografie" section:
  * Font Family selector (visual dropdown or radio group).
  * 3-way segmented button for boldness (`Normal`, `Semi-Bold`, `Bold`).
  * Interactive live preview card with sample metrics ("148 bpm", "28.5 km/h", "1:24:35").

### Component 5: Cockpit HUD Integration (`SensorFieldView.kt`, `SensorGridScreen.kt`)
* Parameterize `getSensorValueTextStyle` and `getSensorUnitTextStyle` with `fontFamily: FontFamily?` and `fontWeight: FontWeight = FontWeight.SemiBold`.
* In `SensorGridScreen.kt`, provide `LocalCockpitTypography` via `CompositionLocalProvider` populated from `TuningPreferencesDataStore`.

### Component 6: 9-Language Localization
* Add strings for `tuning_cat_cockpit_typography`, font family names, and boldness options in `values/strings.xml` and all 8 localized folders (`values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Font Resource Definitions & Preloading
* Files:
  * `app/src/main/res/font/orbitron.xml`
  * `app/src/main/res/font/roboto_condensed.xml`
  * `app/src/main/res/font/comic_neue.xml`
  * `app/src/main/res/font/roboto_mono.xml`
  * `app/src/main/res/values/preloaded_fonts.xml`
* Changes: Define downloadable font XMLs and preload entries.

### Step 2: Typography Domain Models & Resolver
* Files:
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/typography/CockpitTypography.kt`
* Changes: Define `CockpitFontWeight`, `CockpitFontFamily`, `CockpitTypographyConfig`, `LocalCockpitTypography`, and `resolveFontFamily`.

### Step 3: Preferences DataStore Management
* Files:
  * `app/src/main/java/com/atrainingtracker/trainingtracker/settings/TuningPreferencesDataStore.kt`
* Changes: Extend `TuningConfig`, `TuningPreferencesDefaults`, DataStore keys, flow mapping, and reset logic.

### Step 4: 9-Language Localization Parity
* Files:
  * `app/src/main/res/values*/strings.xml` (all 9 locales)
* Changes: Add `tuning_cat_cockpit_typography`, font names, and weight labels.

### Step 5: Advanced Settings UI & Live Preview
* Files:
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningDialog.kt`
* Changes: Integrate typography configuration section with live preview tile.

### Step 6: Cockpit HUD Text Style Integration
* Files:
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/SensorFieldView.kt`
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/SensorGridScreen.kt`
* Changes: Consume `LocalCockpitTypography` in `SensorFieldView` and provide configuration in `SensorGridScreen`.

### Step 7: Unit Testing & Verification
* Files:
  * `app/src/test/java/com/atrainingtracker/trainingtracker/settings/TuningPreferencesCockpitTypographyTest.kt`
  * `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/typography/CockpitTypographyResolutionTest.kt`
  * `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/SensorFieldViewTypographyTest.kt`
  * `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/typography/CockpitTypographyLocalizationTest.kt`
* Command:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.settings.*" --tests "com.atrainingtracker.trainingtracker.ui.tracking.*"`

---

## 6. Verification & Rollback Plan

* **Verification**:
  1. Targeted unit tests during Stage 4 implementation.
  2. Full clean-room regression test suite (`./gradlew testDebugUnitTest`) in Stage 5.
  3. On-device deployment to Pixel 10 (`66020DLCR002FL`) to inspect visual typography rendering and settings changes.
* **Rollback**: Feature branch isolation (`feature/ATT-1751`) allows immediate revert without affecting the sprint branch (`sprint/2026-40.7`).
