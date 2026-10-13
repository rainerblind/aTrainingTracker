# Stage 3: Technical Implementation Plan (ATT-2456)

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

## 1. Architecture Overview & Component Decomposition

```
┌────────────────────────────────────────────────────────┐
│               CockpitTypographySection.kt              │
│  - SensorFieldVariant Dropdown (4 Presets)             │
│  - Corner Radius Slider (0 dp .. 20 dp, steps=19)      │
│  - Border Thickness Slider (0.0 .. 4.0 dp, steps=7)   │
│  - Border Contrast Slider (0% .. 100%, steps=9)       │
└───────────────────────────┬────────────────────────────┘
                            │ On change / Save
                            ▼
┌────────────────────────────────────────────────────────┐
│             TuningPreferencesDataStore.kt              │
│  - KEY_SENSOR_FIELD_VARIANT: String                    │
│  - KEY_SENSOR_FIELD_CORNER_RADIUS: Float (0..20)       │
│  - KEY_SENSOR_FIELD_BORDER_THICKNESS: Float (0..4)     │
│  - KEY_SENSOR_FIELD_BORDER_CONTRAST: Float (0..1)      │
└───────────────────────────┬────────────────────────────┘
                            │ reactive tuningConfigFlow
                            ▼
┌────────────────────────────────────────────────────────┐
│                 SensorGridScreen.kt                    │
│  - effectiveShape = resolveShape(cornerRadius)         │
│  - effectiveBorder = resolveBorder(thick, contrast)   │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│                 SensorFieldView.kt                     │
│  - isSelectedForMove ? 2.dp primary : effectiveBorder  │
│  - Card(shape = effectiveShape, border = border)       │
└────────────────────────────────────────────────────────┘
```

---

## 2. Invariants & Guardrails

1. **Non-Breaking Factory Defaults**:
   Default preference remains `CLASSIC_SEAMLESS` (corner radius `0.0f`, border thickness `1.0f`, border contrast `0.0f`), preserving the identical look-and-feel for users who have not customized tile styling.
2. **File Size Enforcement (< 400 lines)**:
   - `AdvancedTuningDialog.kt` is currently at 382 lines. State declarations and callbacks for the 3 new properties will be kept minimal (e.g. combined into compact statements) so line count remains strictly below 400.
   - `CockpitTypographySection.kt` is at 277 lines; adding the 3 sliders adds ~50 lines, remaining safely below 330 lines.
3. **Pick-and-Place Selection Integrity**:
   When `isSelectedForMove` is true, `BorderStroke(2.dp, MaterialTheme.colorScheme.primary)` must take absolute precedence over any custom border stroke.
4. **Theme-Aware Contrast Resolution**:
   Contrast is linearly interpolated between subtle theme tone (`#383838` dark / `#D6D6D6` light) and maximum contrast (`#FFFFFF` dark / `#000000` light) to prevent invisible borders across dark and light modes.
5. **9-Language Localization Parity**:
   All new keys must exist with non-empty translations across all 9 supported locales: `values/` (EN), `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.

---

## 3. Atomic Implementation Steps

### Step 1: 9-Language Localization Strings
Add the following string keys across all 9 `strings.xml` files:
- `tuning_sensor_field_corner_radius_title`
- `tuning_sensor_field_corner_radius_desc`
- `tuning_sensor_field_corner_radius_default`
- `tuning_sensor_field_border_thickness_title`
- `tuning_sensor_field_border_thickness_desc`
- `tuning_sensor_field_border_thickness_default`
- `tuning_sensor_field_border_contrast_title`
- `tuning_sensor_field_border_contrast_desc`
- `tuning_sensor_field_border_contrast_default`

### Step 2: Update `SensorFieldStyle.kt`
- Update the 4 variants to have distinctly differentiated baseline values:
  - `CLASSIC_SEAMLESS`: radius `0.dp`, spacing `0.dp`, elevation `0.dp`, border `1.0.dp`, contrast `0.0f`
  - `OUTLINED_TILES`: radius `8.dp`, spacing `4.dp`, elevation `0.dp`, border `2.0.dp`, contrast `0.5f`
  - `ELEVATED_CARDS`: radius `10.dp`, spacing `6.dp`, elevation `3.dp`, border `0.0.dp`, contrast `0.0f`
  - `SOFT_CAPSULES`: radius `16.dp`, spacing `8.dp`, elevation `1.dp`, border `1.5.dp`, contrast `0.7f`
- Add properties `borderThickness: Dp` and `borderContrast: Float` to `SensorFieldStyle`.
- Add `resolveBorder(borderThickness: Dp, borderContrast: Float, isDarkTheme: Boolean, isSelectedForMove: Boolean = false): BorderStroke?`.
- Add `resolveShape(cornerRadius: Dp): Shape`.

### Step 3: Extend `TuningPreferencesDataStore.kt`
- Add preferences keys:
  - `KEY_SENSOR_FIELD_CORNER_RADIUS = floatPreferencesKey("tuning_sensor_field_corner_radius")`
  - `KEY_SENSOR_FIELD_BORDER_THICKNESS = floatPreferencesKey("tuning_sensor_field_border_thickness")`
  - `KEY_SENSOR_FIELD_BORDER_CONTRAST = floatPreferencesKey("tuning_sensor_field_border_contrast")`
- Extend `TuningConfig` data class:
  - `val sensorFieldCornerRadius: Float = 0.0f`
  - `val sensorFieldBorderThickness: Float = 1.0f`
  - `val sensorFieldBorderContrast: Float = 0.0f`
- Map keys in `tuningConfigFlow` with defensive clamping.
- Persist keys in `saveTuningConfig(...)`.
- Clear keys in `resetToDefaults()`.

### Step 4: Add Granular Sliders in `CockpitTypographySection.kt`
- Accept `sensorFieldCornerRadius: Float`, `onCornerRadiusChange: (Float) -> Unit`, `sensorFieldBorderThickness: Float`, `onBorderThicknessChange: (Float) -> Unit`, `sensorFieldBorderContrast: Float`, `onBorderContrastChange: (Float) -> Unit`.
- In `onSensorFieldVariantChange`: snap all 3 sliders to the selected variant's baseline parameters.
- Render 3 `TuningSliderItem` composables below the variant dropdown:
  - Corner Radius: 0 to 20 dp, steps = 19
  - Border Thickness: 0.0 to 4.0 dp, steps = 7
  - Border Contrast: 0% to 100%, steps = 9

### Step 5: Wire State in `AdvancedTuningDialog.kt`
- Expose and hoist state variables for `sensorFieldCornerRadius`, `sensorFieldBorderThickness`, and `sensorFieldBorderContrast`.
- Synchronize state in `LaunchedEffect(persistedConfig)`.
- Pass variables and callbacks to `CockpitTypographySection`.
- Include properties in `saveTuningConfig(...)` call.
- Verify line count of `AdvancedTuningDialog.kt` is < 400 lines.

### Step 6: Ingest Custom Styling in `SensorGridScreen.kt`
- Resolve `effectiveShape` via `SensorFieldStyle.resolveShape(tuningConfig.sensorFieldCornerRadius.dp)`.
- Resolve `effectiveBorder` via `SensorFieldStyle.resolveBorder(tuningConfig.sensorFieldBorderThickness.dp, tuningConfig.sensorFieldBorderContrast, isDarkTheme)`.
- Pass `effectiveBorder` and `effectiveShape` to `SensorFieldView`.
- Ensure selection highlight during configuration mode overrides `effectiveBorder`.

### Step 7: Author Unit & Contract Tests
- `SensorFieldStyleContractTest.kt`: verify preset baseline values, border resolution math, dark vs light contrast interpolation, and shape resolution.
- `TuningPreferencesDataStoreSensorFieldTest.kt`: verify DataStore persistence, value clamping, and factory reset.
- `AdvancedTuningModularityTest.kt`: verify line counts of `AdvancedTuningDialog.kt` and `CockpitTypographySection.kt` remain strictly < 400 lines.
- `TranslationParityTest.kt`: verify 9-language parity for all 9 new string keys.

### Step 8: Execute Verification & Clean-Room Regression
- Run targeted tests via Gradle.
- Run full `./gradlew testDebugUnitTest` suite.
