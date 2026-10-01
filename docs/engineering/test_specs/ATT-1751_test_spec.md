# Stage 2: Requirement & Test Specification - ATT-1751: [Cockpit/Typography] Configurable Cockpit Font Family & Boldness (Normal, Semi-Bold, Bold) in Advanced Settings

**Ticket**: [[ATT-1751]](https://rainerblind.atlassian.net/browse/ATT-1751)  
**Sub-task**: [[ATT-1830]](https://rainerblind.atlassian.net/browse/ATT-1830) (`[Req & Test Spec]`)  
**Parent Epic**: [[ATT-355]](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.7`  
**Requirement Mapping**: `REQ-UI-212` (*Configurable Cockpit Typography (Font Family & Boldness) in Advanced Settings*)  
**Test Spec ID**: `TST-UI-166`  
**Branch**: `feature/ATT-1751`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Requirement Specification (REQ-UI-212)

### 1.1 Problem Statement & Rationale
During live workouts (e.g., fast road cycling, trail running, mountain biking), the Cockpit HUD is the primary telemetry interface. Athletes glance at live metrics (heart rate, power, cadence, speed, slope) under variable environmental conditions including bright direct sunlight, bike mount vibrations, and varying viewing distances.

Currently, `SensorFieldView.kt` hardcodes `FontWeight.SemiBold` (`getSensorValueTextStyle`) and relies exclusively on the platform default system typeface (`FontFamily.Default` / Roboto). Athletes cannot adjust font thickness or font family to match their eyesight, bike computer aesthetic preferences, or sunlight readability conditions.

Introducing configurable font families (7-segment digital display, modern athletic sans-serif, monospace, playful) and adjustable boldness (Normal, Semi-Bold, Bold) elevates the cockpit user experience while preserving existing defaults for 100% backward compatibility.

### 1.2 Functional & Architectural Requirements
1. **Cockpit Typography Domain Models (`CockpitTypography.kt`)**:
   - The system SHALL define `CockpitFontWeight`: `NORMAL` (`FontWeight.Normal`), `SEMI_BOLD` (`FontWeight.SemiBold`, default), and `BOLD` (`FontWeight.Bold`).
   - The system SHALL define `CockpitFontFamily` with curated choices: `SYSTEM_DEFAULT` (default system font / `FontFamily.Default`), `SEVEN_SEGMENT` (classic digital retro LCD font `Orbitron`), `MODERN_ATHLETIC` (condensed athletic sans-serif `Roboto Condensed`), `MONOSPACE` (fixed-width tabular digits `FontFamily.Monospace` / `Roboto Mono`), and `PLAYFUL` (casual font `Comic Neue`).
   - `CockpitTypography.kt` SHALL provide defensive fallback resolution to standard Compose families (`Default`, `Monospace`, `SansSerif`, `Cursive`) to guarantee 100% test safety on JVM unit test runners and offline reliability.
2. **Preferences DataStore Management (`TuningPreferencesDataStore.kt`)**:
   - The system SHALL persist cockpit typography in `TuningConfig` under DataStore keys `KEY_COCKPIT_FONT_FAMILY` and `KEY_COCKPIT_FONT_WEIGHT`.
   - Default values SHALL strictly be `SYSTEM_DEFAULT` and `SEMI_BOLD`, ensuring 100% backward compatibility for existing users.
   - Factory reset in `TuningPreferencesDataStore` SHALL restore `SYSTEM_DEFAULT` and `SEMI_BOLD`.
3. **Advanced Settings UI Integration (`AdvancedTuningDialog.kt`)**:
   - `AdvancedTuningDialog.kt` SHALL provide a dedicated configuration section titled *"Cockpit-Typografie"* / *"Cockpit Typography"* (`@string/tuning_cat_cockpit_typography`).
   - The section SHALL feature a font family selector (`SYSTEM_DEFAULT`, `SEVEN_SEGMENT`, `MODERN_ATHLETIC`, `MONOSPACE`, `PLAYFUL`) with localized display names.
   - The section SHALL feature a 3-way segmented button or toggle for boldness (`Normal`, `Semi-Bold`, `Bold`).
   - The section SHALL provide an interactive live preview card displaying sample sensor metrics (e.g. "148 bpm", "28.5 km/h", "1:24:35") reflecting current font and boldness selections immediately.
4. **Cockpit HUD Rendering Integration (`SensorFieldView.kt`)**:
   - `SensorFieldView.kt` and `SensorGridScreen.kt` SHALL consume configured typography via `LocalCockpitTypography` CompositionLocal, keeping `SensorFieldState` and database schemas decoupled.
   - `getSensorValueTextStyle` and `getSensorUnitTextStyle` SHALL parameterize font family and weight across all 9 `ViewSize` steps (`XSMALL` through `XXXHUGE`) without vertical clipping, baseline shifts, or unit label collision.
5. **100% 9-Language Localization Parity**:
   - All section titles, font family names, and boldness labels SHALL be defined across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement (`REQ-UI-212`), extending Epic `ATT-355` (*Good and consistent UI*) and building upon `REQ-UI-171` (*High-Contrast Cockpit Typography and Subtle Tile Grid*) and `REQ-UI-181` (*Ultra-Large Cockpit Typography Extensions*).
* **Historical Origin & Commit Trace**: Tickets `ATT-1264` and `ATT-1431`.
* **Root Reason for Existing Formulation**: `REQ-UI-171` hardcoded `FontWeight.SemiBold` and default system font to guarantee high contrast and sunlight legibility on AMOLED displays. Providing user-selectable font families and boldness empowers athletes to tailor the HUD to their specific eyesight and mounting preferences.
* **Preservation of Core Invariants**:
  - Default configuration remains strictly `SEMI_BOLD` and `SYSTEM_DEFAULT` (100% visual parity for existing users upon app update).
  - The 9-step `ViewSize` scale (`XSMALL` .. `XXXHUGE`) and proportional unit scaling remain immutable.
  - AMOLED high-contrast color tokens are preserved.
  - Zero mutation to `SensorFieldState` or SQLite schemas.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (7-Segment Digital Display)**:
  * *Given* an athlete in `AdvancedTuningDialog`,
  * *When* selecting '7-Segment Digital' (`SEVEN_SEGMENT`) and 'Bold',
  * *Then* the live preview card and the Cockpit HUD SHALL render sensor metric values in 7-segment digital LCD digits with bold weight.
* **Criterion 2 (Modern & Monospace Styles)**:
  * *Given* an athlete selecting 'Monospace' or 'Modern Athletic',
  * *When* viewing rapidly changing metrics during a workout,
  * *Then* digits SHALL render cleanly without horizontal jitter or layout shifts.
* **Criterion 3 (Playful / Fun Font)**:
  * *Given* an athlete selecting 'Playful',
  * *When* viewing the Cockpit HUD,
  * *Then* sensor metrics SHALL render in the casual/playful typeface across all sensor fields.
* **Criterion 4 (ViewSize Layout Stability)**:
  * *Given* any combination of font family and boldness,
  * *When* displayed across all 9 tile sizes (`XSMALL` through `XXXHUGE`),
  * *Then* numbers and unit labels SHALL fit cleanly within their tiles without clipping, truncation, or baseline overflow.
* **Criterion 5 (Default Compatibility)**:
  * *Given* a fresh install or existing update,
  * *When* opening the Cockpit HUD without altering typography settings,
  * *Then* metrics SHALL default to the system default font with Semi-Bold weight.

### 1.5 System Invariants
* Single-thread SQLite confinement and schema immutability (`SensorFieldState` and database schemas unmodified).
* Default settings (`SYSTEM_DEFAULT`, `SEMI_BOLD`) 100% preserved.
* Parent ticket Human Decision Gate remains inviolable.

---

## 2. Test Specification (TST-UI-166)

### Test Case 1: Preference DataStore & Serialization Unit Tests (`TST-UI-166.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/settings/TuningPreferencesCockpitTypographyTest.kt`
* **Preconditions**: Initialized `TuningPreferencesDataStore` with test preferences DataStore.
* **Action**:
  1. Verify default values: `CockpitFontFamily.SYSTEM_DEFAULT` and `CockpitFontWeight.SEMI_BOLD`.
  2. Mutate `cockpit_font_family` across all enum entries and verify emitted `tuningConfigFlow`.
  3. Mutate `cockpit_font_weight` across all enum entries and verify emitted `tuningConfigFlow`.
  4. Perform factory reset and verify restoration of `SYSTEM_DEFAULT` and `SEMI_BOLD`.
  5. Inject unrecognized string tokens and verify defensive fallback to defaults.
* **Expected Result**: 100% serialization integrity, reactive flow emissions, clean fallback on corrupted data.

### Test Case 2: Typography Safe Resolution & Mapping Tests (`TST-UI-166.2`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/typography/CockpitTypographyResolutionTest.kt`
* **Preconditions**: Isolated JVM test environment without active Android downloadable font provider.
* **Action**:
  1. Resolve `FontFamily` for all 5 `CockpitFontFamily` entries via `CockpitTypography.resolveFontFamily(context, family)`.
  2. Verify mapping of `CockpitFontWeight.asFontWeight()` to `FontWeight.Normal`, `FontWeight.SemiBold`, and `FontWeight.Bold`.
* **Expected Result**: Zero exceptions thrown; non-null `FontFamily` returned for all enum values; accurate `FontWeight` mappings.

### Test Case 3: SensorFieldView Visual & Scaling Contract Tests (`TST-UI-166.3`)
* **Scope**: Composable Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/SensorFieldViewTypographyTest.kt`
* **Preconditions**: Compose test harness.
* **Action**:
  1. Evaluate `getSensorValueTextStyle` and `getSensorUnitTextStyle` across all 9 `ViewSize` steps (`XSMALL` through `XXXHUGE`) with each `CockpitFontFamily` and `CockpitFontWeight`.
  2. Assert font family and font weight are propagated.
  3. Assert font size, line height, and baseline calculations remain strictly positive and non-colliding.
* **Expected Result**: 100% font style contract compliance without negative line heights or clipping across all 9 sizes.

### Test Case 4: 9-Language Localization Audit (`TST-UI-166.4`)
* **Scope**: Localization Parity Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/typography/CockpitTypographyLocalizationTest.kt`
* **Goal**: Verify string resources (`tuning_cat_cockpit_typography`, font family names, and boldness labels) exist across all 9 localized resource files:
  * `values/strings.xml` (EN)
  * `values-de/strings.xml` (DE)
  * `values-es/strings.xml` (ES)
  * `values-fr/strings.xml` (FR)
  * `values-it/strings.xml` (IT)
  * `values-ja/strings.xml` (JA)
  * `values-nl/strings.xml` (NL)
  * `values-pl/strings.xml` (PL)
  * `values-pt/strings.xml` (PT)
* **Expected Result**: 100% translation parity, zero missing entries, zero format specifier mismatches.

### Test Case 5: Clean-Room Full Suite Regression Execution (`TST-UI-166.5`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: 100% pass rate across the full application unit test suite.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-166.1` | Unit | `TuningPreferencesDataStore` | `REQ-UI-212` | Specified |
| `TST-UI-166.2` | Unit | `CockpitTypography.resolveFontFamily` | `REQ-UI-212` | Specified |
| `TST-UI-166.3` | Contract | `SensorFieldView.getSensorValueTextStyle` | `REQ-UI-212`, `REQ-UI-171`, `REQ-UI-181` | Specified |
| `TST-UI-166.4` | Localization | `CockpitTypographyLocalizationTest` | `REQ-UI-212`, `REQ-UI-106` | Specified |
| `TST-UI-166.5` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
