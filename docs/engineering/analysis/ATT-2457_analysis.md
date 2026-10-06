# Stage 1: Forensic Analysis & Scope Bounding (ATT-2457)

**Ticket**: [ATT-2457](https://atrainingtracker.atlassian.net/browse/ATT-2457)  
**Summary**: [Verbesserung] Show tile borders in settings live preview and rename Cockpit & Typografie section to include tile styling  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Active Sprint**: `2026-41.1`  
**Target Release**: `V4.9.39`  
**Author**: AI Agent 1 (Implementer)  
**Auditor**: AI Agent 2 (Auditor)  
**Date**: 2026-10-06  

---

## 1. Executive Summary

During the on-device review of ATT-2058 and ATT-2456 in Sprint 2026-40.16 Joint Review on a physical Pixel 10 device, the Product Owner identified two key usability and visual feedback gaps in *Experten-Einstellungen -> Cockpit-Typografie* (`AdvancedTuningDialog` / `CockpitTypographySection`):
1. **Live Preview Tile Borders & Geometry Missing**:
   - Although the section provides tile variant selection (`SensorFieldVariant`) and granular sliders for Corner Radius, Border Thickness, and Theme-Aware Border Contrast (ATT-2456), the live preview card at the bottom of the section displays sample metric text ("148 bpm", "28.5 km/h", "1:24:35") directly on a plain background surface without individual tile cards, borders, corner rounding, elevation, or inter-tile grid spacing.
   - Consequently, athletes cannot evaluate the visual impact of changing tile presets, corner radius, or border thickness/contrast without leaving settings and returning to the tracking cockpit.
2. **Incomplete Section Title Scope**:
   - The accordion section title was labeled *Cockpit-Typografie* (`tuning_cat_cockpit_typography`), which only conveys font family and boldness configuration. It fails to convey that the section also houses cockpit tile geometry, border styling, and variant presets.
   - The section header should be renamed to comprehensively reflect both typography and tile borders/styling (e.g., DE: "Cockpit, Typografie & Kacheln"; EN: "Cockpit, Typography & Tiles"), with full 9-language localization parity.

---

## 2. Forensic Analysis & Root Cause Identification

### 2.1 Current Implementation State in CockpitTypographySection.kt
In `CockpitTypographySection.kt` (lines 244–322):
```kotlin
Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ),
    shape = RoundedCornerShape(10.dp)
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = stringResource(R.string.tuning_cockpit_preview_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // ... Row with bare Column { Text(...); Text(...) }
    }
}
```
**Deficiency**:
- The preview `Row` contains 3 plain `Column` elements without any `Card`, `Surface`, or `Box` wrappers.
- The `border`, `shape`, `elevation`, and `gridSpacing` defined by `sensorFieldVariant`, `sensorFieldCornerRadius`, `sensorFieldBorderThickness`, and `sensorFieldBorderContrast` are completely unreferenced in the preview rendering.
- Even when an athlete selects `OUTLINED_TILES` or adjusts `sensorFieldBorderThickness` to 4.0 dp, the preview remains visually unchanged aside from font family and boldness.

### 2.2 Cockpit HUD Parity Model
In the actual tracking cockpit (`SensorGridScreen.kt`):
- Grid spacing between tiles is governed by `activeVariantStyle.gridSpacing`.
- Tile shape is resolved via `SensorFieldStyle.resolveShape(sensorFieldCornerRadius.dp)`.
- Tile border stroke is resolved dynamically via:
  ```kotlin
  SensorFieldStyle.resolveBorder(
      borderThickness = sensorFieldBorderThickness.dp,
      borderContrast = sensorFieldBorderContrast,
      isDarkTheme = isDarkTheme
  )
  ```
- Tile elevation is governed by `activeVariantStyle.elevation`.
- Tile background is `MaterialTheme.colorScheme.surface` or container surface.

By encapsulating each of the 3 preview sample metrics ("148 bpm", "28.5 km/h", "1:24:35") into its own mini tile composable utilizing the identical shape, elevation, border stroke, and spacing mechanics as `SensorFieldView`, the settings live preview provides 100% WYSIWYG fidelity before the athlete dismisses the dialog.

### 2.3 Section Header Renaming
In `strings.xml`:
- Existing key: `tuning_cat_cockpit_typography`.
- Used in `AdvancedTuningDialog.kt`:
  ```kotlin
  TuningAccordionSection(
      icon = Icons.Default.TextFields,
      title = stringResource(R.string.tuning_cat_cockpit_typography),
      subtitle = TuningSubtitleFormatter.formatCockpitSubtitle(cockpitFontFamily, cockpitFontWeight, sensorFieldVariant, context),
      ...
  )
  ```
- To preserve binary and resource-key compatibility with existing contract tests (`CockpitTypographyLocalizationTest`, `AdvancedTuningVisualContractTest`), we will retain the resource ID `tuning_cat_cockpit_typography` and update its localized string value across all 9 languages.

---

## 3. Chesterton's Fence Requirement Archaeology

1. **REQ-UI-212 (Cockpit Typography in Advanced Settings)**:
   - Established `tuning_cat_cockpit_typography` as the section title and introduced font family / boldness controls and the live preview card.
   - *Preservation*: The live preview MUST continue to reflect `cockpitFontFamily` and `cockpitFontWeight` accurately.
2. **REQ-UI-222 (Accordion Architecture for Advanced Settings)**:
   - Established Section 1 as `CockpitTypographySection` with live active-value summary subtitle (`formatCockpitSubtitle`).
   - *Preservation*: The subtitle formatter already outputs `"$familyName, $weightName · $variantName"`, which accurately summarizes both typography and tile variant. The accordion container structure and icons remain preserved.
3. **REQ-UI-258 & REQ-UI-276 (Cockpit Tile Variants & Granular Adjustments)**:
   - Added `SensorFieldVariant`, `sensorFieldCornerRadius`, `sensorFieldBorderThickness`, and `sensorFieldBorderContrast`.
   - *Preservation*: Sliders and dropdown menu in `CockpitTypographySection` remain intact and functional; the live preview now actively wires to these parameters.

---

## 4. Proposed Solution & Architecture

### 4.1 Live Preview Mini-Tile Architecture
In `CockpitTypographySection.kt`:
1. Compute the preview styling:
   - `val style = remember(sensorFieldVariant) { SensorFieldStyle.forVariant(sensorFieldVariant) }`
   - `val shape = remember(sensorFieldCornerRadius) { SensorFieldStyle.resolveShape(sensorFieldCornerRadius.dp) }`
   - `val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f`
   - `val border = remember(sensorFieldBorderThickness, sensorFieldBorderContrast, isDark) { SensorFieldStyle.resolveBorder(sensorFieldBorderThickness.dp, sensorFieldBorderContrast, isDark) }`
2. Render the sample tiles inside the preview card using a `Row` with `horizontalArrangement = Arrangement.spacedBy(style.gridSpacing.coerceAtLeast(2.dp))` (or using `style.gridSpacing` directly, with an inner preview canvas background so borderless tiles or 0-spacing tiles have clean framing).
3. Each mini-tile is rendered as a `Surface` or `Card`:
   - `shape = shape`
   - `border = border`
   - `shadowElevation = style.elevation`
   - `tonalElevation = style.elevation`
   - `color = MaterialTheme.colorScheme.surface`
   - Inner padding to display the metric value and unit cleanly with `previewFamily` and `previewWeight`.

### 4.2 9-Language Resource Parity for Section Title
Update `tuning_cat_cockpit_typography` across all 9 localized `strings.xml` files:
- **EN** (`values`): `Cockpit, Typography & Tiles`
- **DE** (`values-de`): `Cockpit, Typografie & Kacheln`
- **ES** (`values-es`): `Cockpit, tipografía y cuadrícula`
- **FR** (`values-fr`): `Cockpit, typographie et tuiles`
- **IT** (`values-it`): `Cockpit, tipografia e riquadri`
- **JA** (`values-ja`): `コックピット、フォント＆タイル`
- **NL** (`values-nl`): `Cockpit, typografie & tegels`
- **PL** (`values-pl`): `Kokpit, typografia i kafelki`
- **PT** (`values-pt`): `Cockpit, tipografia e blocos`

---

## 5. Scope Bounding & Out of Scope Guardrails

### In Scope
- Update `CockpitTypographySection.kt` to render individual preview tiles reflecting `shape`, `elevation`, `border`, and `gridSpacing`.
- Update string resource `tuning_cat_cockpit_typography` across all 9 language directories.
- Unit and contract test coverage verifying live preview tile structure and 9-language translation parity.
- Clean-room regression test run (`./gradlew testDebugUnitTest`).

### Out of Scope
- Altering the underlying `SensorFieldStyle` resolution algorithms or defaults (completed in ATT-2456).
- Modifying tracking database schemas or `SensorFieldState`.
- Modifying other accordion sections in `AdvancedTuningDialog`.

---

## 6. Verification Criteria (Gate 1 Exit Criteria)

1. **Analysis Deliverable**: This document stored at `docs/engineering/analysis/ATT-2457_analysis.md`.
2. **Subtask Updated & Audited**: `ATT-2550` description updated, transitioned to `in_review`, audited via `review_agent.py audit ATT-2550`, and transitioned to `Erledigt`.
3. **Living Documentation & Invariants**: Chesterton's Fence analysis complete, zero backward compatibility regressions.
