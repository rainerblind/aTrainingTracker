# Stage 1 Analysis: ATT-1751 - [Cockpit/Typography] Configurable Cockpit Font Family & Boldness (Normal, Semi-Bold, Bold) in Advanced Settings

**Ticket**: [[ATT-1751]](https://rainerblind.atlassian.net/browse/ATT-1751)  
**Sub-task**: [[ATT-1829]](https://rainerblind.atlassian.net/browse/ATT-1829) (`[Analysis]`)  
**Parent Epic**: [[ATT-355]](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.7`  
**Branch**: `feature/ATT-1751`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Problem Statement & Motivation

During active outdoor and indoor workouts (e.g., high-speed road cycling, trail running, mountain biking), the Cockpit HUD is the athlete's primary telemetry interface. Athletes glance at live metrics (heart rate, power, cadence, speed, slope) under challenging environmental conditions including bright sunlight, handlebar vibrations, and variable viewing distances.

Currently, `SensorFieldView.kt` hardcodes `FontWeight.SemiBold` (`getSensorValueTextStyle`) and relies exclusively on the default system typeface (`FontFamily.Default` / Roboto). Athletes cannot adjust font thickness or font family to match their eyesight, bike computer aesthetic preferences, or sunlight readability conditions.

As a professional sport tracking application, offering tailored typography options elevates the cockpit user experience:
1. **Classic 7-Segment Digital Display**: Gives the cockpit an authentic retro bike computer / chronograph feel (`Orbitron` / 7-segment display).
2. **Modern Athletic & Monospace**: Clean, high-density athletic sans-serif and tabular monospace fonts that eliminate horizontal digit jitter when values change rapidly.
3. **Playful / Casual Font**: A fun, lighthearted alternative (`Comic Neue`).
4. **Configurable Boldness**: Adjustable weight (`Normal`, `Semi-Bold`, `Bold`) accommodating varying eyesight, sunglasses tinting, and mount distances.

---

## 2. Root Cause & Architectural Gap Analysis

1. **Hardcoded Font Weight in `SensorFieldView.kt`**:
   - `getSensorValueTextStyle(viewSize, typography)` enforces `baseStyle.copy(fontWeight = FontWeight.SemiBold)` without parameterization or external configuration hooks.
2. **Absence of Font Family Modeling**:
   - There is no domain abstraction for selectable cockpit fonts. All text styles implicitly resolve to the platform Roboto typeface.
3. **Tuning DataStore Scope Boundary**:
   - `TuningPreferencesDataStore.kt` and `TuningConfig` currently persist battery saver parameters, GPS accuracy thresholds, and Aftermath X-axis domain, but lack keys for Cockpit typography preferences (`cockpit_font_family`, `cockpit_font_weight`).
4. **Settings Dialog Missing Typography Section**:
   - `AdvancedTuningDialog.kt` contains battery saver, altitude filter, and aftermath sections, but lacks a dedicated "Cockpit-Typografie" section and an interactive live preview.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * **Domain & Preference Modeling**:
    - Introduce `CockpitFontWeight`: `NORMAL` (`FontWeight.Normal`), `SEMI_BOLD` (`FontWeight.SemiBold`), `BOLD` (`FontWeight.Bold`). Default: `SEMI_BOLD`.
    - Introduce `CockpitFontFamily`: `SYSTEM_DEFAULT`, `SEVEN_SEGMENT`, `MODERN_ATHLETIC`, `MONOSPACE`, `PLAYFUL`. Default: `SYSTEM_DEFAULT`.
    - Extend `TuningConfig` and `TuningPreferencesDataStore.kt` with reactive DataStore persistence, validation, and atomic factory reset.
  * **Font Family Resources & Preloading**:
    - Define Google Fonts / Android XML font family resources in `app/src/main/res/font/` (`orbitron.xml`, `roboto_condensed.xml`, `comic_neue.xml`, `roboto_mono.xml`).
    - Register downloadable font resources in `app/src/main/res/values/preloaded_fonts.xml` with graceful fallback to standard Compose `FontFamily` (Default, Monospace, SansSerif, Cursive).
  * **Advanced Settings UI (`AdvancedTuningDialog.kt`)**:
    - Add a dedicated "Cockpit-Typografie" category.
    - Provide a font family selector with localized display names.
    - Provide a 3-way toggle / segmented button for font weight (`Normal`, `Semi-Bold`, `Bold`).
    - Provide an interactive live preview card displaying sample sensor metrics ("148 bpm", "28.5 km/h", "1:24:35") reflecting active selections instantly.
  * **Cockpit UI Integration (`SensorFieldView.kt`)**:
    - Parameterize `getSensorValueTextStyle` and `getSensorUnitTextStyle` with `fontFamily` and `fontWeight`.
    - Provide `LocalCockpitTypography` CompositionLocal in `SensorGridScreen.kt` and cockpit previews.
    - Ensure zero layout clipping across all 9 `ViewSize` steps (`XSMALL` through `XXXHUGE`).
  * **Localization Parity**:
    - Add all category titles, font names, and weight labels across all 9 supported locales (en, de, es, fr, it, ja, nl, pl, pt).

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Modifying non-cockpit typography (Aftermath, summary cards, dialogs, lists remain strictly untouched).
  * Modifying sensor calculations, filters, database schemas, or Bluetooth/ANT+ telemetry pipelines.
  * Changing `SensorFieldState` data classes or SQLite persistence schemas.

---

### Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-UI-171` (*High-Contrast Cockpit Typography and Subtle Tile Grid*) and `REQ-UI-181` (*Ultra-Large Cockpit Typography Extensions*).
* **Historical Origin & Commit Trace**: Ticket `ATT-1264` and `ATT-1431`.
* **Root Reason for Existing Formulation**: `REQ-UI-171` hardcoded `FontWeight.SemiBold` and default system font to guarantee high contrast and sunlight legibility on AMOLED displays, preventing thin illegible fonts on bike mounts.
* **Preservation of Core Invariants**:
  - The default configuration remains strictly `SEMI_BOLD` and `SYSTEM_DEFAULT`, preserving 100% visual and behavioral parity for existing users upon app update.
  - The 9-step `ViewSize` scale (`XSMALL` through `XXXHUGE`) and proportional unit scaling remain immutable.
  - High-contrast color tokens (`onSurface` pure white, `onSurfaceVariant` #9E9E9E, `outlineVariant` #262626) are preserved.

---

## 4. Architectural Strategy & High-Level Solution

```
┌────────────────────────────────────────────────────────────────┐
│  TuningPreferencesDataStore (cockpit_font_family, font_weight) │
└──────────────────────────────┬─────────────────────────────────┘
                               │ Flow<TuningConfig>
                               ▼
┌────────────────────────────────────────────────────────────────┐
│  SensorGridScreen / TrackingTabsScreen                         │
│  CompositionLocalProvider(LocalCockpitTypography provides ...) │
└──────────────────────────────┬─────────────────────────────────┘
                               │
                               ▼
┌────────────────────────────────────────────────────────────────┐
│  SensorFieldView                                               │
│  - valueStyle: getSensorValueTextStyle(..., family, weight)   │
│  - unitStyle:  getSensorUnitTextStyle(..., family)            │
│  - Fits seamlessly across XSMALL .. XXXHUGE                    │
└────────────────────────────────────────────────────────────────┘
```

1. **`CockpitTypography.kt` (`settings/` or `ui/tracking/`)**:
   - `CockpitFontWeight`: `NORMAL`, `SEMI_BOLD`, `BOLD`.
   - `CockpitFontFamily`: `SYSTEM_DEFAULT`, `SEVEN_SEGMENT`, `MODERN_ATHLETIC`, `MONOSPACE`, `PLAYFUL`.
   - `resolveFontFamily()`: Returns Compose `FontFamily` with bulletproof fallback for tests and offline usage.
2. **`TuningPreferencesDataStore.kt`**:
   - Add keys `KEY_COCKPIT_FONT_FAMILY` and `KEY_COCKPIT_FONT_WEIGHT`.
   - Include defaults in `TuningPreferencesDefaults`.
   - Update `TuningConfig`, `saveTuningConfig`, and `resetToDefaults`.
3. **`AdvancedTuningDialog.kt`**:
   - Dedicated "Cockpit-Typografie" section with live preview tile.
4. **`SensorFieldView.kt`**:
   - Consume `LocalCockpitTypography.current`.
   - Optional parameters on `getSensorValueTextStyle` and `getSensorUnitTextStyle`.

---

## 5. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Default typography remains identical to current production behavior (`SYSTEM_DEFAULT`, `SEMI_BOLD`).
  2. All 9 view sizes render without text truncation, layout overflow, or baseline misalignment.
  3. Factory reset restores `SYSTEM_DEFAULT` and `SEMI_BOLD` atomically.
  4. 100% 9-language localization parity across all resource files.
* **Risk Rating**: **LOW**
  - Justification: UI styling and preference storage enhancement. No database migrations, no background service alterations.
