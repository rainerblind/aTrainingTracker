# Stage 1: Forensic Analysis & Scope Bounding (ATT-2456)

**Ticket**: [ATT-2456](https://atrainingtracker.atlassian.net/browse/ATT-2456)  
**Summary**: [Verbesserung] More distinct cockpit tile variants and selectable tile border colour (white/black)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Active Sprint**: `2026-41.1`  
**Target Release**: `V4.9.39`  
**Author**: AI Agent 1 (Implementer)  
**Auditor**: AI Agent 2 (Auditor)  
**Date**: 2026-10-06  

---

## 1. Executive Summary

During the on-device review of ATT-2058 on a physical Pixel 10 (Sprint 2026-40.16 Joint Review), the Product Owner noted that the 4 cockpit tile styling variants (*Klassisch nahtlos*, *Moderne Sportkacheln*, *Erhabene Sportkarten*, *Soft-Akzent Kapseln*) appeared visually too subtle at arm's length while cycling or running.

This ticket improves cockpit customization by:
1. Making the 4 baseline presets noticeably distinct at glance distance:
   - **Classic Seamless**: Sharp rectangular (0 dp corner radius, 0 dp grid spacing, 0 dp elevation, subtle 1 dp border).
   - **Modern Outlined Sport Tiles**: 8 dp corner radius, 4 dp grid spacing, prominent 2 dp border with medium-high contrast framing.
   - **Elevated Sports Cards**: 10 dp corner radius, 6 dp grid spacing, 3 dp elevation shadow, borderless.
   - **Soft Accent Capsules**: 16 dp pill rounding, 8 dp grid spacing, 1 dp elevation, 1.5 dp high-contrast border outline.
2. Introducing 3 dedicated, granular tuning sliders in `CockpitTypographySection` (`TuningFormControls`):
   - **Corner Radius Slider**: 0 dp (sharp) to 20 dp (capsule) with 1 dp stepped increments.
   - **Border Thickness Slider**: 0.0 dp (borderless) to 4.0 dp (heavy stroke) with 0.5 dp increments.
   - **Border Tone / Contrast Slider**: 0% (subtle/theme-background aligned) to 100% (high-contrast white in Dark Mode, black in Light Mode) designed theme-aware to guarantee legibility across AMOLED Dark and Light modes.
3. Synchronizing preset selection with the granular sliders: selecting a preset snaps the sliders to that preset's baseline parameters, while athletes retain full agency to adjust any slider independently.
4. Persisting settings atomically via Jetpack DataStore (`TuningPreferencesDataStore`), resetting via factory defaults, updating the live tracking cockpit in `SensorGridScreen` without app restarts, and maintaining 9-language localization parity across all resource directories.

---

## 2. Forensic Analysis & Root Cause Identification

### 2.1 Current Implementation State (ATT-2058)
In `SensorFieldStyle.kt`:
- Preset corner radii were 0 dp, 6 dp, 8 dp, and 12 dp. At normal phone-to-eye distance (50–70 cm on handlebars), the difference between 6 dp and 8 dp was imperceptible.
- Border thickness was hardcoded to `1.dp outlineVariant` in `SensorFieldView.kt`.
- In `SensorGridScreen.kt`, when invoking `SensorFieldView`, the `border` argument was omitted entirely, forcing every tile to use the hardcoded `1.dp outlineVariant` border regardless of whether the athlete selected `CLASSIC_SEAMLESS`, `OUTLINED_TILES`, `ELEVATED_CARDS`, or `SOFT_CAPSULES`.
- Athletes had no mechanism to adjust border stroke thickness or contrast, leading to poor visibility against vibrant zone backgrounds or high-glare outdoor sunlight.

### 2.2 The Theme-Aware Contrast Model
Outdoor cockpits require high contrast:
- In **Dark / AMOLED Cockpit Mode**: Low contrast (`outlineVariant` ~ #383838) blends into the dark surface (#121212). High contrast requires crisp light-grey to white strokes (`Color.White` / #FFFFFF).
- In **Light Mode**: Low contrast (`outlineVariant` ~ #D6D6D6) blends into the light surface (#FAFAFA). High contrast requires dark-grey to pitch-black strokes (`Color.Black` / #000000).
- The solution computes the theme-aware border color via linear interpolation:
  `borderColor = lerp(subtleColor, maxContrastColor, borderContrast)`
  where `subtleColor` is theme-aligned (e.g. `outlineVariant` or neutral tone) and `maxContrastColor` is `Color.White` in dark mode and `Color.Black` in light mode.

---

## 3. Chesterton's Fence Requirement Archaeology

1. **REQ-UI-171 & REQ-UI-200**:
   - `REQ-UI-171` originally established high-contrast AMOLED typography and rectangular sensor fields to maximize data density.
   - `REQ-UI-200` added pick-and-place reordering in `ScreenMode.CONFIGURATION`. In `SensorFieldView`, selecting a tile renders a 2 dp `primary` border.
   - *Invariant*: When `isSelectedForMove` is true, the selection border (`BorderStroke(2.dp, MaterialTheme.colorScheme.primary)`) MUST take absolute precedence over custom tile borders.
2. **REQ-UI-258 (Cockpit Sensor Field Styling)**:
   - Established the 4 enum variants (`CLASSIC_SEAMLESS`, `OUTLINED_TILES`, `ELEVATED_CARDS`, `SOFT_CAPSULES`) and DataStore key `KEY_SENSOR_FIELD_VARIANT`.
   - *Preservation*: The 4 variants remain as the primary presets in `SensorFieldVariant`. Factory defaults must preserve `CLASSIC_SEAMLESS` (0 dp corner radius, 1 dp border, 0% contrast) so existing users experience zero unexpected visual shifts.
3. **REQ-UI-262 (Advanced Tuning Dialog Modularity)**:
   - Enforces that no single file in `ui.settings.tuning` exceeds 400 lines.
   - `AdvancedTuningDialog.kt` is currently at 382 lines; additions to `AdvancedTuningDialog.kt` must be minimal and concise to strictly honor the < 400 lines rule. `CockpitTypographySection.kt` (277 lines) has ample headroom.

---

## 4. Scope Bounding & Out-of-Scope Declarations

### In-Scope:
- Enhancing baseline parameters for the 4 presets in `SensorFieldStyle.kt`.
- Adding 3 DataStore keys in `TuningPreferencesDataStore.kt`:
  - `KEY_SENSOR_FIELD_CORNER_RADIUS`: Float (0.0f..20.0f, default 0.0f)
  - `KEY_SENSOR_FIELD_BORDER_THICKNESS`: Float (0.0f..4.0f, default 1.0f)
  - `KEY_SENSOR_FIELD_BORDER_CONTRAST`: Float (0.0f..1.0f, default 0.0f)
- Adding Corner Radius, Border Thickness, and Border Tone/Contrast sliders in `CockpitTypographySection.kt` using `TuningSliderItem`.
- Synchronizing preset selection in `CockpitTypographySection` so choosing a variant snaps the 3 sliders to that variant's baseline parameters.
- Updating `SensorGridScreen.kt` and `SensorFieldView.kt` to dynamically apply the custom corner radius and resolved theme-aware border stroke.
- Supporting atomic factory reset in `resetToDefaults()`.
- 9-language localization for all new preference labels, helper texts, and default value descriptors.
- Contract, persistence, and visual rendering unit tests.

### Out-of-Scope:
- Modifying live preview HUD card inside `CockpitTypographySection` (explicitly tracked in sibling ticket **ATT-2457**).
- Renaming the accordion section title to "Cockpit, Typografie & Kacheln" (explicitly tracked in sibling ticket **ATT-2457**).
- Modifying SQLite schemas or sensor data telemetry streams.

---

## 5. Architecture & Invariant Preservation Strategy

```
┌────────────────────────────────────────────────────────┐
│               CockpitTypographySection.kt              │
│  - SensorFieldVariant Dropdown (4 Presets)             │
│  - Corner Radius Slider (0 dp .. 20 dp)                │
│  - Border Thickness Slider (0 dp .. 4 dp)              │
│  - Border Contrast Slider (0% .. 100%)                 │
└───────────────────────────┬────────────────────────────┘
                            │ On change / Save
                            ▼
┌────────────────────────────────────────────────────────┐
│             TuningPreferencesDataStore.kt              │
│  - KEY_SENSOR_FIELD_VARIANT                            │
│  - KEY_SENSOR_FIELD_CORNER_RADIUS                      │
│  - KEY_SENSOR_FIELD_BORDER_THICKNESS                   │
│  - KEY_SENSOR_FIELD_BORDER_CONTRAST                    │
└───────────────────────────┬────────────────────────────┘
                            │ reactive tuningConfigFlow
                            ▼
┌────────────────────────────────────────────────────────┐
│                 SensorGridScreen.kt                    │
│  - effectiveShape = RoundedCornerShape(radius.dp)      │
│  - effectiveBorder = resolveBorder(thick, contrast)   │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│                 SensorFieldView.kt                     │
│  - Card(shape = shape, border = border, ...)           │
│  - isSelectedForMove -> 2.dp primary border            │
└────────────────────────────────────────────────────────┘
```

---

## 6. Risk Matrix & Quality Gate Review Plan

| Risk | Likelihood | Impact | Mitigation Strategy |
|---|---|---|---|
| File size violation in `AdvancedTuningDialog.kt` (> 400 lines) | High | High | Keep state pass-throughs ultra-compact in `AdvancedTuningDialog.kt` or pass `TuningConfig` bundle; ensure `AdvancedTuningModularityTest` passes. |
| Incompatible border styling on Light vs Dark/AMOLED modes | Medium | High | Use linear color interpolation from neutral grey to pure white (Dark) or pure black (Light); test both themes in contract tests. |
| Diamond-shaped voids at grid intersections with rounded corners | Medium | Medium | Maintain synchronized grid spacing; when corner radius > 0 dp, ensure `effectiveSpacing >= 4.dp`. |
| Loss of selection highlight during pick-and-place | Low | High | Enforce `if (isSelectedForMove) BorderStroke(2.dp, primary)` prior to evaluating custom borders in `SensorFieldView`. |
| Localization regression in 9 languages | Low | High | Full key audit across `values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`. |
