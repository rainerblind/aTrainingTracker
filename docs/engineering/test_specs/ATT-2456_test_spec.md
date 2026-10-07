# Stage 2: Requirement & Test Specification (ATT-2456)

**Ticket**: [ATT-2456](https://atrainingtracker.atlassian.net/browse/ATT-2456)  
**Summary**: [Verbesserung] More distinct cockpit tile variants and selectable tile border colour (white/black)  
**Requirement**: `REQ-UI-276`  
**Test Case**: `TST-UI-236`  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Active Sprint**: `2026-41.1`  
**Target Release**: `V4.9.39`  
**Author**: AI Agent 1 (Implementer)  
**Auditor**: AI Agent 2 (Auditor)  
**Date**: 2026-10-06  

---

## 1. Formal Requirement Specification: REQ-UI-276

### REQ-UI-276: Cockpit Tile Distinct Presets, Granular Corner Radius, Border Thickness, and Theme-Aware Border Contrast

The system SHALL provide distinctly differentiated cockpit tile styling presets and granular, athlete-adjustable sliders for corner radius, border thickness, and theme-aware border contrast in `CockpitTypographySection.kt` and `SensorGridScreen.kt` (ATT-2456):

1. **Distinct Baseline Presets (`SensorFieldVariant` & `SensorFieldStyle`)**:
   The 4 styling variants SHALL serve as baseline presets with distinctly noticeable visual signatures at glance distance:
   - `CLASSIC_SEAMLESS`: `cornerRadius = 0.dp` (`RectangleShape`), `gridSpacing = 0.dp`, `defaultElevation = 0.dp`, `borderThickness = 1.0.dp`, `borderContrast = 0.0f`.
   - `OUTLINED_TILES`: `cornerRadius = 8.dp` (`RoundedCornerShape(8.dp)`), `gridSpacing = 4.dp`, `defaultElevation = 0.dp`, `borderThickness = 2.0.dp`, `borderContrast = 0.5f`.
   - `ELEVATED_CARDS`: `cornerRadius = 10.dp` (`RoundedCornerShape(10.dp)`), `gridSpacing = 6.dp`, `defaultElevation = 3.dp`, `borderThickness = 0.0.dp`, `borderContrast = 0.0f`.
   - `SOFT_CAPSULES`: `cornerRadius = 16.dp` (`RoundedCornerShape(16.dp)`), `gridSpacing = 8.dp`, `defaultElevation = 1.dp`, `borderThickness = 1.5.dp`, `borderContrast = 0.7f`.

2. **Granular Preference Sliders in Cockpit Typography (`CockpitTypographySection.kt`)**:
   Below the variant dropdown, the section SHALL render 3 dedicated `TuningSliderItem` controls:
   - **Corner Radius**: Range `0.0f .. 20.0f` with 19 steps (1 dp increments). Displays formatted value (e.g. `0 dp`, `8 dp`, `20 dp`).
   - **Border Thickness**: Range `0.0f .. 4.0f` with 7 steps (0.5 dp increments: 0.0, 0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0 dp). Displays formatted value (e.g. `1.0 dp`, `2.0 dp`).
   - **Border Contrast / Luminance**: Range `0.0f .. 1.0f` with 9 steps (10% increments: 0% .. 100%). Displays formatted percentage (e.g. `0%`, `50%`, `100%`).

3. **Dynamic Preset Synchronization**:
   - When the athlete selects a preset from the `SensorFieldVariant` dropdown, the 3 sliders SHALL automatically snap to that preset's baseline parameters.
   - Adjusting any slider independently SHALL update the corresponding parameter without locking or resetting other sliders.

4. **Theme-Aware Border Color Resolution (`SensorFieldStyle.kt`)**:
   - Border stroke SHALL be resolved theme-aware:
     - When `borderThickness <= 0.0.dp`: `null` (borderless).
     - When `borderThickness > 0.0.dp`: `BorderStroke(borderThickness, resolvedColor)`.
     - In **Dark / AMOLED Cockpit Mode**: `resolvedColor = lerp(subtleDarkColor, Color.White, borderContrast.coerceIn(0f, 1f))`, where `subtleDarkColor = Color(0xFF383838)`.
     - In **Light Mode**: `resolvedColor = lerp(subtleLightColor, Color.Black, borderContrast.coerceIn(0f, 1f))`, where `subtleLightColor = Color(0xFFD6D6D6)`.
   - When in configuration mode and `isSelectedForMove` is true, the primary accent border (`BorderStroke(2.dp, MaterialTheme.colorScheme.primary)`) SHALL take precedence over the custom border.

5. **DataStore Preference Persistence & Atomic Factory Reset (`TuningPreferencesDataStore.kt`)**:
   - `KEY_SENSOR_FIELD_CORNER_RADIUS`: Float (default `0.0f`).
   - `KEY_SENSOR_FIELD_BORDER_THICKNESS`: Float (default `1.0f`).
   - `KEY_SENSOR_FIELD_BORDER_CONTRAST`: Float (default `0.0f`).
   - `resetToDefaults()` SHALL atomically clear all 3 keys along with `KEY_SENSOR_FIELD_VARIANT`.

6. **Live Cockpit Ingestion (`SensorGridScreen.kt`)**:
   - `SensorGridScreen` SHALL reactively observe `cornerRadius`, `borderThickness`, and `borderContrast` from `tuningConfigFlow`.
   - The resolved shape (`if (cornerRadius > 0.dp) RoundedCornerShape(cornerRadius) else RectangleShape`) and border SHALL be supplied to each `SensorFieldView`, updating the live tracking cockpit across all tabs instantly upon saving or adjusting preferences.

7. **100% 9-Language Localization Parity**:
   - String resources for titles, helper texts, and default descriptions (`tuning_sensor_field_corner_radius_title`, `tuning_sensor_field_border_thickness_title`, `tuning_sensor_field_border_contrast_title`, etc.) SHALL be defined with identical keys across all 9 application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).

---

## 2. Requirement Archaeology & Chesterton's Fence Audit

1. **Original Target & Historical Trace**:
   - `REQ-UI-171` established high-contrast AMOLED cockpit typography.
   - `REQ-UI-200` established drag-and-drop pick-and-place reordering with 2 dp primary accent selection border.
   - `REQ-UI-258` introduced the initial 4 enum variants in ATT-2058.
2. **Historical Origin & Commit Trace**:
   - Ticket `ATT-2456`, sprint `2026-41.1`, target release `V4.9.39`, Epic `ATT-355`.
3. **Root Reason for Existing Formulation**:
   - ATT-2058 introduced the 4 presets with corner radii of 0 dp, 6 dp, 8 dp, and 12 dp and hardcoded 1 dp border. Arm's-length on-device evaluation by the PO revealed this was too subtle to distinguish outdoors.
4. **Preservation of Core Invariants**:
   - Production defaults remain on `CLASSIC_SEAMLESS` (0 dp corner radius, 1.0 dp border thickness, 0% contrast), ensuring 100% backward compatibility for all users.
   - Pick-and-place selection border (`2.dp, primary`) takes absolute precedence during configuration mode.
   - File size constraints (< 400 lines) across tuning settings composables remain strictly enforced.

---

## 3. Acceptance Criteria (Given-When-Then)

- **AC-1: Distinct Baseline Presets**:
  - *Given* an athlete selecting `OUTLINED_TILES`, `ELEVATED_CARDS`, or `SOFT_CAPSULES` from the preset dropdown,
  - *When* selected,
  - *Then* the corner radius, border thickness, and border contrast sliders snap to that preset's baseline parameters (e.g. 8 dp / 2.0 dp / 50% for Outlined Tiles).

- **AC-2: Independent Corner Radius Slider**:
  - *Given* an athlete in `AdvancedTuningDialog` (Cockpit & Typografie),
  - *When* dragging the Corner Radius slider from 0 dp to 14 dp,
  - *Then* the corner radius updates to 14 dp and live cockpit sensor fields render with `RoundedCornerShape(14.dp)`.

- **AC-3: Independent Border Thickness Slider**:
  - *Given* an athlete adjusting Border Thickness to 0.0 dp (borderless) or 3.0 dp (heavy stroke),
  - *When* saved,
  - *Then* sensor fields render with either no border (0.0 dp) or a prominent 3.0 dp border stroke.

- **AC-4: Theme-Aware Border Contrast**:
  - *Given* an athlete setting Border Contrast to 100% (1.0),
  - *When* rendered in Dark / AMOLED Cockpit Mode,
  - *Then* the border color resolves to crisp `Color.White`.
  - *When* rendered in Light Mode,
  - *Then* the border color resolves to crisp `Color.Black`.

- **AC-5: Atomic Factory Reset**:
  - *Given* custom slider values (e.g. 18 dp corner radius, 3.5 dp border, 90% contrast),
  - *When* tapping "Auf Werkseinstellungen zurücksetzen",
  - *Then* all cockpit tile parameters revert to `CLASSIC_SEAMLESS` defaults (0 dp, 1.0 dp, 0%).

- **AC-6: Pick-and-Place Selection Invariant**:
  - *Given* a sensor field with a 3.0 dp custom border,
  - *When* selected for move in configuration mode,
  - *Then* the tile renders the 2 dp primary accent selection border, returning to the custom border once move completes or cancels.

---

## 4. Formal Test Specification: TST-UI-236

### TST-UI-236: Cockpit Tile Distinct Presets, Granular Corner Radius, Border Thickness, and Theme-Aware Border Contrast Verification

1. **Preset & Style Contract Tests (`SensorFieldStyleContractTest.kt`)**:
   - Verify `SensorFieldStyle.forVariant` yields distinctly differentiated baseline parameters across all 4 variants.
   - Verify `SensorFieldStyle.resolveBorder` returns `null` when `thickness <= 0.dp`.
   - Verify `SensorFieldStyle.resolveBorder` interpolates correctly to `Color.White` in Dark Mode and `Color.Black` in Light Mode when `contrast == 1.0f`.
   - Verify `SensorFieldStyle.resolveBorder` interpolates correctly to subtle tones when `contrast == 0.0f`.

2. **DataStore Persistence & Clamping Tests (`TuningPreferencesDataStoreSensorFieldTest.kt`)**:
   - Verify persistence of `KEY_SENSOR_FIELD_CORNER_RADIUS`, `KEY_SENSOR_FIELD_BORDER_THICKNESS`, `KEY_SENSOR_FIELD_BORDER_CONTRAST`.
   - Verify boundary clamping: corner radius clamped to `0.0f .. 20.0f`, border thickness clamped to `0.0f .. 4.0f`, border contrast clamped to `0.0f .. 1.0f`.
   - Verify `resetToDefaults()` clears all 3 keys and restores default configuration.

3. **UI Contract & Modularity Tests (`AdvancedTuningModularityTest.kt`)**:
   - Verify line count of `AdvancedTuningDialog.kt` and `CockpitTypographySection.kt` remains strictly under 400 lines.
   - Verify presence of Corner Radius, Border Thickness, and Border Contrast slider items in `CockpitTypographySection.kt`.

4. **9-Language Localization Audit (`TranslationParityTest.kt`)**:
   - Verify all new string resources exist and are non-empty across all 9 application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).

5. **Clean-Room Full Suite Regression Execution**:
   - Execute `./gradlew testDebugUnitTest` across all modules to verify 100% test pass rate with 0 regressions.
