# Stage 2: Requirement & Test Specification (ATT-2457)

**Ticket**: [ATT-2457](https://atrainingtracker.atlassian.net/browse/ATT-2457)  
**Summary**: [Verbesserung] Show tile borders in settings live preview and rename Cockpit & Typografie section to include tile styling  
**Requirement**: `REQ-UI-277`  
**Test Case**: `TST-UI-237`  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Active Sprint**: `2026-41.1`  
**Target Release**: `V4.9.39`  
**Author**: AI Agent 1 (Implementer)  
**Auditor**: AI Agent 2 (Auditor)  
**Date**: 2026-10-06  

---

## 1. Formal Requirement Specification: REQ-UI-277

### REQ-UI-277: Settings Live Preview Cockpit Tile Styling & Comprehensive Section Naming

The system SHALL render individual cockpit tile borders, corner radius, elevation, and spacing inside the settings live preview card in `CockpitTypographySection.kt`, and SHALL rename the settings category header to reflect both typography and tile styling across all 9 supported locales (ATT-2457):

1. **WYSIWYG Live Preview Tile Rendering (`CockpitTypographySection.kt`)**:
   - The live preview container SHALL render sample metrics ("148 bpm", "28.5 km/h", "1:24:35") as discrete mini cockpit tiles rather than unstructured text on a flat background card.
   - Each mini preview tile SHALL faithfully apply:
     - **Corner Radius & Shape**: Resolved via `SensorFieldStyle.resolveShape(sensorFieldCornerRadius.dp)`.
     - **Elevation & Shadow**: Resolved via `style.elevation` based on the selected `sensorFieldVariant`.
     - **Border Stroke**: Resolved via `SensorFieldStyle.resolveBorder(sensorFieldBorderThickness.dp, sensorFieldBorderContrast, isDarkTheme)`.
     - **Container Background**: `MaterialTheme.colorScheme.surface` contrasting against the outer preview container's `surfaceVariant`.
     - **Inter-Tile Grid Spacing**: The row arrangement SHALL reflect `style.gridSpacing` (0 dp for `CLASSIC_SEAMLESS`, 4 dp for `OUTLINED_TILES`, 6 dp for `ELEVATED_CARDS`, 8 dp for `SOFT_CAPSULES`).
   - The sample metrics SHALL continue to reflect `cockpitFontFamily` and `cockpitFontWeight` accurately.

2. **Comprehensive Category Header Renaming**:
   - String resource `tuning_cat_cockpit_typography` SHALL be updated across all 9 application locales to encompass both typography and tile styling:
     - **EN** (`values`): `Cockpit, Typography & Tiles`
     - **DE** (`values-de`): `Cockpit, Typografie & Kacheln`
     - **ES** (`values-es`): `Cockpit, tipografía y cuadrícula`
     - **FR** (`values-fr`): `Cockpit, typographie et tuiles`
     - **IT** (`values-it`): `Cockpit, tipografia e riquadri`
     - **JA** (`values-ja`): `コックピット、フォント＆タイル`
     - **NL** (`values-nl`): `Cockpit, typografie & tegels`
     - **PL** (`values-pl`): `Kokpit, typografia i kafelki`
     - **PT** (`values-pt`): `Cockpit, tipografia e blocos`
   - The resource ID `tuning_cat_cockpit_typography` SHALL be preserved to guarantee zero breaking changes to existing tests and XML references.

3. **Architectural Modularity Invariant**:
   - `CockpitTypographySection.kt` and `AdvancedTuningDialog.kt` SHALL remain strictly under 400 lines of code.

---

## 2. Formal Test Specification: TST-UI-237

### TST-UI-237: Settings Live Preview Cockpit Tile Styling & Comprehensive Section Naming Verification

1. **UI & Live Preview Contract Tests (`CockpitPreviewTileContractTest.kt`)**:
   - Verify that `CockpitTypographySection.kt` resolves `effectiveShape`, `effectiveElevation`, and `effectiveBorder` using `SensorFieldStyle`.
   - Verify that preview tiles utilize `Surface` or `Card` with `border = effectiveBorder`, `shape = effectiveShape`, and `elevation = effectiveElevation`.
   - Verify that preview tiles are arranged horizontally with inter-tile spacing derived from `style.gridSpacing`.
2. **Modularity & File Length Contract Tests (`AdvancedTuningModularityTest.kt`)**:
   - Verify `CockpitTypographySection.kt` line count is strictly < 400 lines.
   - Verify `AdvancedTuningDialog.kt` line count is strictly < 400 lines.
3. **9-Language Localization Audit (`CockpitTypographyLocalizationTest.kt` & `TranslationParityTest.kt`)**:
   - Verify `tuning_cat_cockpit_typography` exists, is non-empty, and contains language-specific tile terminology across EN, DE, ES, FR, IT, JA, NL, PL, and PT.
4. **Full Clean-Room Regression**:
   - Execute `./gradlew testDebugUnitTest` verifying 100% test pass rate with 0 regressions.

---

## 3. Acceptance Criteria (Given-When-Then)

- **Scenario 1: Live Preview reflects Outlined Tiles preset**
  - *Given* an athlete in `AdvancedTuningDialog` viewing the *Cockpit, Typografie & Kacheln* section,
  - *When* selecting `OUTLINED_TILES` preset,
  - *Then* the live preview tiles SHALL render with 8 dp rounded corners, 4 dp spacing, and a prominent 2 dp outline stroke.

- **Scenario 2: Live Preview reflects Border Thickness slider adjustments**
  - *Given* an athlete adjusting the Border Thickness slider from 1.0 dp to 4.0 dp,
  - *When* moving the slider,
  - *Then* the live preview tile border stroke width SHALL visibly increase in real-time.

- **Scenario 3: Live Preview reflects Classic Seamless borderless/seamless geometry**
  - *Given* an athlete selecting `CLASSIC_SEAMLESS`,
  - *When* observing the preview tiles,
  - *Then* the tiles SHALL have 0 dp corner radius and 0 dp grid spacing, sitting flush against each other.

- **Scenario 4: Section title displays comprehensive naming across languages**
  - *Given* an athlete running the app in German,
  - *When* opening `AdvancedTuningDialog`,
  - *Then* Section 1 SHALL be titled "Cockpit, Typografie & Kacheln".
  - *Given* an athlete running the app in English,
  - *When* opening `AdvancedTuningDialog`,
  - *Then* Section 1 SHALL be titled "Cockpit, Typography & Tiles".
