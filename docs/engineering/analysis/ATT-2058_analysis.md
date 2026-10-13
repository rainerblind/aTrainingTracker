# Stage 1 Analysis: ATT-2058 - Visual Evaluation & Prototyping: Rounded Corners & Tile Spacing Variants for SensorFieldView (Rework Cycle 2: Expert Settings Integration)

**Ticket**: [ATT-2058](https://rainerblind.atlassian.net/browse/ATT-2058)  
**Sub-task**: [ATT-2400](https://rainerblind.atlassian.net/browse/ATT-2400) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40` (assigned upon completion per Rule 19)  
**Active Sprint**: `2026-40.16`  
**Requirement Mapping**: `REQ-UI-258` (*Cockpit Sensor Field Visual Styling Architecture & Grid Spacing Prototyping Variants*)  
**Test Spec ID**: `TST-UI-217`  
**Branch**: `feature/ATT-2058`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Problem Statement & Motivation

During the live workout tracking cockpit usage (`SensorGridScreen.kt` and `SensorFieldView.kt`), telemetry values are presented in a multi-row, multi-column grid. In Cycle 1 (Sprint 2026-40.14), a structured visual evaluation architecture was created with 4 prototyping variants (V0 through V3) and comprehensive Compose previews.

During the Sprint Review ceremony with the Product Owner, the preview variants were evaluated and approved for product integration. The PO issued two concrete rework mandates:
1. **Configurable Selection in Expert Settings**:
   - The tile styling variants must be exposed as a configurable setting in the **Experten-Einstellungen** (under the Cockpit section in `AdvancedTuningDialog.kt`).
   - The athlete must be able to switch and test the tile layout variants live on their physical device (e.g. Google Pixel 10) during real-world training conditions.
   - The selected variant must be persisted in DataStore (`TuningPreferencesDataStore.kt`) and reactively update `SensorGridScreen.kt` without requiring an app restart.
2. **Appealing, Intuitive Naming Overhaul**:
   - The previous technical nomenclature (`Variant 0: Baseline (Status Quo)`) must be replaced across the UI and all 9 supported languages with appealing, descriptive names:
     - **V0**: *"Klassisch nahtlos"* / *"Classic Seamless Grid"*
     - **V1**: *"Moderne Sportkacheln"* / *"Modern Outlined Tiles"*
     - **V2**: *"Erhabene Sportkarten"* / *"Elevated Sports Cards"*
     - **V3**: *"Soft-Akzent Kapseln"* / *"Soft Accent Capsules"*

---

## 2. Forensic Investigation & Architectural Gap Analysis

### 2.1 Persistence Layer (`TuningPreferencesDataStore.kt`)
- `TuningPreferencesDataStore` currently manages cockpit font family, cockpit font weight, dimming factors, GPS thresholds, and slope parameters.
- **Gap**: Missing `KEY_SENSOR_FIELD_VARIANT` in `Preferences.Key<String>`, `TuningConfig.sensorFieldVariant`, and default `TuningPreferencesDefaults.SENSOR_FIELD_VARIANT = SensorFieldVariant.CLASSIC_SEAMLESS`.
- Atomic factory reset (`resetToDefaults()`) must also reset `KEY_SENSOR_FIELD_VARIANT` to `CLASSIC_SEAMLESS`.

### 2.2 UI Configuration Layer (`AdvancedTuningDialog.kt` & `CockpitTypographySection.kt`)
- Section 1 of the accordion in `AdvancedTuningDialog` is titled `tuning_cat_cockpit_typography` ("Cockpit & Typografie").
- Currently, `CockpitTypographySection` only houses font family and font weight selectors.
- **Gap**: Needs an additional dropdown or selector for `SensorFieldVariant`, displaying the localized display name for each variant, with live state update in `AdvancedTuningDialog`.
- `TuningSubtitleFormatter.formatCockpitSubtitle` must be updated to append the active tile layout style to the accordion header subtitle.

### 2.3 Reactive Live Cockpit Updating (`SensorGridScreen.kt`)
- `SensorGridScreen.kt` currently accepts default parameters `gridSpacing: Dp = 0.dp`, `fieldShape: Shape = RectangleShape`, `fieldElevation: CardElevation = CardDefaults.cardElevation(0.dp)`.
- It already observes `tuningConfig by tuningDataStore.tuningConfigFlow.collectAsState(initial = TuningConfig())`.
- **Gap**: In production (`TrackingTabGridContent.kt`), `SensorGridScreen` is called without explicit styling overrides. When caller arguments are at default values, `SensorGridScreen` must dynamically resolve its styling from `SensorFieldStyle.forVariant(tuningConfig.sensorFieldVariant)`. This guarantees instant, reactive visual updates upon changing settings in `AdvancedTuningDialog`.

### 2.4 Localization Parity Across All 9 Locales
- All variant labels and setting descriptions must be translated across all 9 supported locales:
  - English (`values/`)
  - German (`values-de/`)
  - Spanish (`values-es/`)
  - French (`values-fr/`)
  - Italian (`values-it/`)
  - Japanese (`values-ja/`)
  - Dutch (`values-nl/`)
  - Polish (`values-pl/`)
  - Portuguese (`values-pt/`)

---

## 3. Requirements Archaeology & Chesterton's Fence (`REQ-PRO-022`)

```markdown
### Requirement Archaeology & Chesterton's Fence Audit
1. **Original Requirement ID & Target**: `REQ-UI-258` (*Cockpit Sensor Field Visual Styling Architecture & Grid Spacing Prototyping Variants*), targeting `SensorFieldStyle.kt`, `SensorGridScreen.kt`, `TuningPreferencesDataStore.kt`, and `AdvancedTuningDialog.kt`.
2. **Historical Origin & Commit Trace**: Introduced in `ATT-2058` (commit `ecf08c35`) during Sprint 2026-40.14 as a prototyping preview suite.
3. **Root Reason for Existing Formulation**: Originally conceived strictly for static Compose previews during sprint ideation to avoid prematurely polluting user preferences before human review.
4. **Preservation of Core Invariants**: 
   - Runtime default remains strictly on `CLASSIC_SEAMLESS` (0dp spacing, RectangleShape, 0dp elevation), ensuring 100% backward compatibility for all athletes who prefer the legacy layout.
   - AMOLED pitch-black contrast (#000000) and pick-and-place reordering (`REQ-UI-200`) remain completely intact across all 4 variants.
   - LiveSegment display priority and SQLite database immutability are fully preserved.
```

---

## 4. Scope Bounding & Invariants

### In Scope
1. **Model & Domain Updates**:
   - Update `SensorFieldVariant` enum with descriptive identifiers: `CLASSIC_SEAMLESS`, `OUTLINED_TILES`, `ELEVATED_CARDS`, `SOFT_CAPSULES`.
   - Maintain backward compatibility mappings (`VARIANT_0_BASELINE`, etc.).
   - Associate each variant with a localized string resource ID (`@StringRes`).
2. **DataStore Persistence**:
   - Add `KEY_SENSOR_FIELD_VARIANT` to `TuningPreferencesDataStore`.
   - Expose `sensorFieldVariant` in `TuningConfig` and include in `ALL_KEYS` for atomic factory reset.
3. **Settings UI Integration**:
   - Add Tile Style selector into `CockpitTypographySection.kt` within `AdvancedTuningDialog.kt`.
   - Update `TuningSubtitleFormatter.formatCockpitSubtitle` to include active variant name.
4. **Reactive Screen Resolution**:
   - Wire `SensorGridScreen.kt` to resolve active style from `tuningConfig.sensorFieldVariant` when callers supply default styling parameters.
5. **Localization Parity**:
   - Add all 5 string keys (`tuning_sensor_field_variant_title`, `sensor_field_variant_v0`, `sensor_field_variant_v1`, `sensor_field_variant_v2`, `sensor_field_variant_v3`) across all 9 language directories.
6. **Automated Verification**:
   - Update and expand `SensorFieldStyleContractTest.kt` to verify DataStore persistence, reactive style resolution, and localization integrity.

### Out of Scope
1. Altering SQLite database tables (`TrackingViewsDatabaseManager`).
2. Modifying default layout from `CLASSIC_SEAMLESS`.
3. Touching `LiveSegmentDisplay` or `EditSensorFieldDialog`.

---

## 5. Verification & Gate 1 Criteria

| Gate Check | Description | Status |
| :--- | :--- | :--- |
| **Problem Domain Closeness** | Sprint Review mandate (Expert Settings integration & naming overhaul) fully analyzed | **PASS** |
| **Archaeology Compliance** | Mandatory 4 fields of Chesterton's Fence archaeology populated for REQ-UI-258 | **PASS** |
| **Invariance Protection** | Production baseline `CLASSIC_SEAMLESS` default and AMOLED contrast guaranteed | **PASS** |
| **Localization Parity** | 9-locale parity plan established for all new string keys | **PASS** |
