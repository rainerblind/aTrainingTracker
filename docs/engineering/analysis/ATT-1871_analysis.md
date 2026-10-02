# Stage 1: Problem Domain & Root Cause Analysis - ATT-1871: Expand Curated Font Selection with High-Performance Sports and HUD Fonts

**Ticket**: [ATT-1871](https://rainerblind.atlassian.net/browse/ATT-1871)  
**Sub-task**: [ATT-1927](https://rainerblind.atlassian.net/browse/ATT-1927) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.8`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Problem Statement & Background

### 1.1 Context & Evolution (Chesterton's Fence)
With ticket **ATT-1751** (`[Feature] [Cockpit/HUD] Configurable Typography & Font Weight for Sensor Fields in Cockpit`), the application introduced a clean, decoupled architecture for customizable HUD typography. Athletes could configure font family (`CockpitFontFamily`) and boldness (`CockpitFontWeight`) in Advanced Settings, persisted via `TuningPreferencesDataStore` and propagated cleanly across Compose hierarchies via `LocalCockpitTypography`.

To minimize architectural risk and establish the underlying baseline, ATT-1751 introduced an initial minimal set of 5 font options:
1. `SYSTEM_DEFAULT` (System Default Sans)
2. `SEVEN_SEGMENT` (`Orbitron`, futuristic digital clock)
3. `MODERN_ATHLETIC` (`Roboto Condensed`, clean athletic)
4. `MONOSPACE` (`Roboto Mono`, fixed-width alignment)
5. `PLAYFUL` (`Comic Neue`, casual display)

While this MVP validated the architecture, real-world outdoor athletic training presents severe visual challenges:
- **Handlebar & Arm Mounts**: Athletes view the cockpit at arm's length (60–100 cm) under intense vibrations and jarring terrain.
- **Sunlight & Glare**: High-contrast, tall glyphs with narrow horizontal footprint maximize readable digit size without truncating multi-digit metrics (e.g. 4-digit wattage, 5-digit distance).
- **Aesthetic Personalization**: Competitive cyclists and runners strongly desire iconic sports timer fonts (like `Bebas Neue` and `Teko`) and technical aerospace HUD typography (like `Chakra Petch`, `Oxanium`, and `Rajdhani`).

### 1.2 Identified Gap
The current typography palette lacks high-impact athletic condensed and specialized HUD fonts. Furthermore, expanding the palette from 5 to 13 options requires ensuring that:
1. The dropdown menu (`ExposedDropdownMenuBox` in `AdvancedTuningDialog.kt`) scrolls gracefully on smaller display viewports without obstructing dialog actions.
2. All new font families maintain 100% 9-language localization parity across all resource directories.
3. Offline devices and JVM unit test environments resolve safe fallbacks without throwing `FontLoadingException` or `Resources.NotFoundException`.
4. Existing user preferences and DataStore schemas remain 100% backwards and forwards compatible.

---

## 2. Forensic Investigation of Affected Subsystems

### 2.1 Downloadable Font Provider Subsystem (`app/src/main/res/font/`)
- Existing fonts use Google Play Services Downloadable Fonts (`com.google.android.gms.fonts`), verified via certificates in `res/values/font_certs.xml` (`@array/com_google_android_gms_fonts_certs`).
- Existing XML descriptors:
  - `orbitron.xml`: `name=Orbitron&amp;weight=700`
  - `roboto_condensed.xml`: `name=Roboto Condensed&amp;weight=700`
  - `roboto_mono.xml`: `name=Roboto Mono&amp;weight=700`
  - `comic_neue.xml`: `name=Comic Neue&amp;weight=700`
- To expand the selection, the following 8 font descriptors must be introduced:
  - Athletic Condensed / High Impact:
    1. `bebas_neue.xml`: `name=Bebas Neue&amp;weight=700`
    2. `teko.xml`: `name=Teko&amp;weight=700`
    3. `barlow_condensed.xml`: `name=Barlow Condensed&amp;weight=700`
    4. `oswald.xml`: `name=Oswald&amp;weight=700`
  - Technical / Aerospace HUD:
    5. `chakra_petch.xml`: `name=Chakra Petch&amp;weight=700`
    6. `oxanium.xml`: `name=Oxanium&amp;weight=700`
    7. `rajdhani.xml`: `name=Rajdhani&amp;weight=700`
  - Modern Geometric:
    8. `montserrat.xml`: `name=Montserrat&amp;weight=700`
- All descriptors must be registered in `app/src/main/res/values/preloaded_fonts.xml` to allow system font pre-fetching.

### 2.2 Typography Resolution & Fallback Logic (`CockpitTypography.kt`)
- `CockpitFontFamily` enum:
  - Currently contains 5 enum members.
  - Must be expanded with: `BEBAS_NEUE`, `TEKO`, `BARLOW_CONDENSED`, `OSWALD`, `CHAKRA_PETCH`, `OXANIUM`, `RAJDHANI`, `MONTSERRAT`.
- Resolution & Caching:
  - `CockpitTypography.resolveFontFamily` uses `ConcurrentHashMap<CockpitFontFamily, FontFamily>()`.
  - When loading downloadable font resources in Compose, an offline device or unit test JVM throws an exception upon font creation or lookup.
  - The `catch (t: Throwable)` block safely delegates to `fallbackFontFamily(family)`.
  - Fallback mapping for the 8 new families will map reliably to standard `FontFamily.SansSerif`.
- Weight mapping:
  - Weights (`NORMAL` -> `FontWeight.Normal`, `SEMI_BOLD` -> `FontWeight.SemiBold`, `BOLD` -> `FontWeight.Bold`) apply orthogonally across all fonts.

### 2.3 Settings Persistence & DataStore Serialization (`TuningPreferencesDataStore.kt`)
- Serialization stores `cockpitFontFamily.name` as a string preference in DataStore (`KEY_COCKPIT_FONT_FAMILY`).
- Deserialization executes:
  ```kotlin
  val cockpitFontFamily = try {
      if (rawFontFamilyStr != null) CockpitFontFamily.valueOf(rawFontFamilyStr) else TuningPreferencesDefaults.COCKPIT_FONT_FAMILY
  } catch (e: IllegalArgumentException) {
      TuningPreferencesDefaults.COCKPIT_FONT_FAMILY
  }
  ```
- Because enum deserialization uses `valueOf(rawFontFamilyStr)` wrapped in a `try/catch`, adding new enum values is inherently backwards and forwards compatible. Any unknown or legacy string safely falls back to `SYSTEM_DEFAULT`.

### 2.4 UI Container & Dialog Usability (`AdvancedTuningDialog.kt`)
- In `CockpitTypographySection`, an `ExposedDropdownMenuBox` renders the font selection dropdown.
- Expanding from 5 to 13 items increases the vertical height of the popup menu.
- Without an explicit maximum height constraint, the menu on small or landscape screens can extend past the viewport boundary.
- Applying `Modifier.heightIn(max = 360.dp)` ensures smooth internal scrolling of the dropdown menu list while keeping dialog headers and dismiss areas accessible.

### 2.5 Localization Parity (9 Languages)
- String resources must be provided for all 8 new fonts in 9 languages:
  1. Default (English): `values/strings.xml`
  2. German: `values-de/strings.xml`
  3. Spanish: `values-es/strings.xml`
  4. French: `values-fr/strings.xml`
  5. Italian: `values-it/strings.xml`
  6. Japanese: `values-ja/strings.xml`
  7. Dutch: `values-nl/strings.xml`
  8. Polish: `values-pl/strings.xml`
  9. Portuguese: `values-pt/strings.xml`

---

## 3. Scope Definition

### 3.1 In-Scope
1. **8 Google Fonts XML Descriptors**:
   - `res/font/bebas_neue.xml`, `teko.xml`, `barlow_condensed.xml`, `oswald.xml`, `chakra_petch.xml`, `oxanium.xml`, `rajdhani.xml`, `montserrat.xml`.
2. **Preloaded Fonts Registration**:
   - Update `app/src/main/res/values/preloaded_fonts.xml` to include all 8 new font resource references.
3. **Domain Model & Typography Resolution**:
   - Expand `CockpitFontFamily` enum with 8 new constants and display name resource bindings.
   - Update `CockpitTypography.resolveFontFamily` and `CockpitTypography.fallbackFontFamily` with robust mapping.
4. **Localization Parity**:
   - 8 new localized strings added across all 9 language resource directories (72 string additions total).
5. **UI Dropdown Resilience**:
   - Constrain `ExposedDropdownMenu` with `Modifier.heightIn(max = 360.dp)` in `AdvancedTuningDialog.kt`.
6. **Comprehensive Automated Verification**:
   - Unit tests covering resolution, fallbacks, serialization, resource IDs, and 9-language string parity.

### 3.2 Out-of-Scope
- Custom user font uploads or arbitrary local TTF file imports.
- Changes to database schemas, workout recording engines, or sensor data pipelines.
- Alterations to existing font options (`SYSTEM_DEFAULT`, `SEVEN_SEGMENT`, `MODERN_ATHLETIC`, `MONOSPACE`, `PLAYFUL`).

---

## 4. Invariants & Safety Measures

1. **Defensive Font Fallback Invariant**:
   - No missing network connection or missing Play Services APK shall cause a crash or crash loop; font resolution must gracefully yield a standard Compose `FontFamily`.
2. **DataStore Schema Invariant**:
   - Corrupted or unrecognized stored strings must default to `SYSTEM_DEFAULT` without crashing or resetting other tuning preferences.
3. **9-Language Parity Invariant**:
   - Every selectable font family must have non-null, valid localized display titles in all 9 supported languages.
4. **Performance & Memory Invariant**:
   - Font family resolution must be cached in `fontCache` (`ConcurrentHashMap`) to avoid redundant font family object allocations on re-composition.

---

## 5. Conclusion & Stage 1 Sign-Off

The problem domain and architecture are fully investigated and bounded. We are ready to proceed to **Stage 2 (Requirement & Test Specification)** to formalize `REQ-UI-227` and `TST-UI-181`.
