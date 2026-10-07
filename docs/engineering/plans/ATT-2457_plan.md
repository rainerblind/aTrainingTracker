# Implementation Plan - ATT-2457: Settings Live Preview Cockpit Tile Styling & Comprehensive Section Naming

## 1. Problem Domain & Scope
In ATT-2456, distinct presets, granular corner radius, border thickness, and border contrast tuning sliders were added to the Settings tuning menu. However:
1. The live preview box at the bottom of `CockpitTypographySection.kt` renders sample telemetry (`148 bpm`, `28.5 km/h`, `1:24:35 TIME`) as raw text columns inside a plain card, rather than rendering distinct cockpit tiles that reflect the currently active tile corner radius, elevation, border stroke, and spacing.
2. The section title (`tuning_cat_cockpit_typography`) remains "Cockpit & Typografie" / "Cockpit & Typography", which omits tile styling even though tile geometry and aesthetics are configured within this section.

This plan details the implementation to:
- Update `tuning_cat_cockpit_typography` across all 9 localized `strings.xml` files to include tile styling.
- Refactor the live preview inside `CockpitTypographySection.kt` to render individual `PreviewCockpitTile` composables driven by `SensorFieldStyle`.
- Implement targeted contract and localization tests to satisfy `TST-UI-237` and `REQ-UI-277`.
- Maintain all existing invariants, including strict file length limits (< 400 lines).

---

## 2. Architecture & Design (SWE.2)

### 2.1 Localization Updates
Resource key: `tuning_cat_cockpit_typography`
- `values/strings.xml` (EN): `Cockpit & Tiles`
- `values-de/strings.xml` (DE): `Cockpit & Kacheln`
- `values-es/strings.xml` (ES): `Cabina y celdas`
- `values-fr/strings.xml` (FR): `Cockpit et tuiles`
- `values-it/strings.xml` (IT): `Cockpit e riquadri`
- `values-ja/strings.xml` (JA): `コックピットとタイル`
- `values-nl/strings.xml` (NL): `Cockpit en tegels`
- `values-pl/strings.xml` (PL): `Kokpit i kafelki`
- `values-pt/strings.xml` (PT): `Cockpit e blocos`

### 2.2 Live Preview Tile Rendering (`CockpitTypographySection.kt`)
The live preview container currently renders:
```kotlin
Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    )
) {
    ...
}
```
Within this preview container:
1. Resolve styling using `SensorFieldStyle`:
   - `isDarkTheme = isSystemInDarkTheme()`
   - `baseStyle = remember(sensorFieldVariant) { SensorFieldStyle.forVariant(sensorFieldVariant) }`
   - `tileShape = remember(sensorFieldCornerRadius) { SensorFieldStyle.resolveShape(sensorFieldCornerRadius.dp) }`
   - `tileBorder = remember(sensorFieldBorderThickness, sensorFieldBorderContrast, isDarkTheme) { SensorFieldStyle.resolveBorder(sensorFieldBorderThickness.dp, sensorFieldBorderContrast, isDarkTheme) }`
   - `tileElevation = if (baseStyle.defaultElevation > 0.dp) CardDefaults.cardElevation(defaultElevation = baseStyle.defaultElevation) else CardDefaults.cardElevation(defaultElevation = 0.dp)`
2. Render a horizontal `Row` containing 3 discrete tiles (`PreviewCockpitTile`):
   - Tile 1: Value `"148"`, Unit `"bpm"` (Heart Rate)
   - Tile 2: Value `"28.5"`, Unit `"km/h"` (Speed)
   - Tile 3: Value `"1:24:35"`, Unit `"TIME"` (Elapsed Time)
3. Spacing between tiles is derived from `baseStyle.gridSpacing.coerceAtLeast(6.dp)`.
4. Each `PreviewCockpitTile` is a `Card` applying:
   - `modifier = Modifier.weight(1f)`
   - `shape = tileShape`
   - `border = tileBorder`
   - `elevation = tileElevation`
   - `colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)`
   - Internal layout centering the metric value and unit vertically and horizontally with standard padding.

### 2.3 Modularity Constraint
- `CockpitTypographySection.kt` must remain strictly under 400 lines of code (enforced by `AdvancedTuningModularityTest.kt`).
- The addition of `PreviewCockpitTile` adds ~30 lines to the 325-line file, resulting in ~355 lines, safely below 400 lines.

---

## 3. Atomic Implementation Steps

### Step 1: 9-Language Localization Update
- Edit `app/src/main/res/values*/strings.xml` for all 9 locales:
  Update `tuning_cat_cockpit_typography` values.

### Step 2: Live Preview Cockpit Tiles in `CockpitTypographySection.kt`
- Import `androidx.compose.foundation.isSystemInDarkTheme`.
- In `CockpitTypographySection.kt`, derive `isDarkTheme`, `baseStyle`, `tileShape`, `tileBorder`, and `tileElevation`.
- Replace the raw Text Column row with a Row of 3 `PreviewCockpitTile` cards.
- Add private composable `PreviewCockpitTile`.

### Step 3: Targeted Unit & Contract Tests
- Create `CockpitPreviewTileContractTest.kt` in `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/`:
  - Verify `CockpitTypographySection.kt` invokes `SensorFieldStyle.resolveShape` and `SensorFieldStyle.resolveBorder`.
  - Verify `PreviewCockpitTile` utilizes `shape`, `border`, and `elevation`.
  - Verify horizontal arrangement utilizes `gridSpacing`.
- Create `CockpitTypographyLocalizationTest.kt`:
  - Verify `tuning_cat_cockpit_typography` across all 9 `strings.xml` files contains tile keywords ("Kacheln", "Tiles", "celdas", "tuiles", "riquadri", "タイル", "tegels", "kafelki", "blocos").
- Run existing `AdvancedTuningModularityTest` and `AdvancedTuningVisualContractTest`.

### Step 4: Verification & Audit
- Run targeted tests via Gradle (`BypassSandbox: true`).
- Gate 3 and Gate 4 compliance checks.

---

## 4. Test Strategy
- Unit & Contract tests:
  - `CockpitPreviewTileContractTest`
  - `CockpitTypographyLocalizationTest`
  - `AdvancedTuningModularityTest`
  - `AdvancedTuningVisualContractTest`
  - `SensorFieldStyleContractTest`
- Full test suite:
  - `./gradlew testDebugUnitTest` in Stage 5.
