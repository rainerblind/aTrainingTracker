# Stage 3: Implementation Plan - ATT-1871: Expand Curated Font Selection with High-Performance Sports and HUD Fonts

**Ticket**: [ATT-1871](https://rainerblind.atlassian.net/browse/ATT-1871)  
**Sub-task**: [ATT-1929](https://rainerblind.atlassian.net/browse/ATT-1929) (`[Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.8`  
**Requirement Mapping**: `REQ-UI-227` (*Cockpit/Typography: Expanded Curated Athletic Sports and Aerospace HUD Font Selection*)  
**Test Mapping**: `TST-UI-181`  
**Author**: AI Agent 1 (Software Architect)  
**Date**: 2026-10-02  

---

## 1. Technical Architecture & Component Design

The objective is to expand the curated Cockpit HUD typography palette from 5 to 13 high-performance fonts, adding specialized athletic condensed and aerospace HUD typefaces to maximize glanceability and outdoor contrast.

### 1.1 Architecture Topology
```text
Cockpit HUD Typography Architecture
  ├── Downloadable Font Descriptors (res/font/)
  │     ├── [Existing] orbitron.xml, roboto_condensed.xml, roboto_mono.xml, comic_neue.xml
  │     └── [New (8)] bebas_neue.xml, teko.xml, barlow_condensed.xml, oswald.xml,
  │                   chakra_petch.xml, oxanium.xml, rajdhani.xml, montserrat.xml
  │
  ├── Preloaded Fonts (res/values/preloaded_fonts.xml)
  │     └── Registers @font/* descriptors for system prefetching
  │
  ├── Domain Model & Resolver (CockpitTypography.kt)
  │     ├── enum class CockpitFontFamily (13 constants: 5 existing + 8 new)
  │     │     └── fun getDisplayNameRes(): Int -> R.string.tuning_font_*
  │     ├── fontCache (ConcurrentHashMap<CockpitFontFamily, FontFamily>)
  │     ├── fun resolveFontFamily(family: CockpitFontFamily): FontFamily
  │     └── fun fallbackFontFamily(family: CockpitFontFamily): FontFamily (FontFamily.SansSerif)
  │
  ├── UI Presentation Layer (AdvancedTuningDialog.kt)
  │     └── ExposedDropdownMenu(modifier = Modifier.heightIn(max = 360.dp))
  │           └── Iterates CockpitFontFamily.values() displaying localized titles in resolved typefaces
  │
  └── 9-Language Resource Parity (res/values-*/strings.xml)
        └── 8 string keys defined across values, values-de, values-es, values-fr,
            values-it, values-ja, values-nl, values-pl, values-pt
```

---

## 2. Atomic Implementation Steps

### Step 1: Create 8 Google Fonts XML Descriptors in `app/src/main/res/font/`
- Create XML descriptors utilizing standard Google Play Services font provider:
  1. `bebas_neue.xml`: `name=Bebas Neue&amp;weight=700`
  2. `teko.xml`: `name=Teko&amp;weight=700`
  3. `barlow_condensed.xml`: `name=Barlow Condensed&amp;weight=700`
  4. `oswald.xml`: `name=Oswald&amp;weight=700`
  5. `chakra_petch.xml`: `name=Chakra Petch&amp;weight=700`
  6. `oxanium.xml`: `name=Oxanium&amp;weight=700`
  7. `rajdhani.xml`: `name=Rajdhani&amp;weight=700`
  8. `montserrat.xml`: `name=Montserrat&amp;weight=700`
- All files define:
  ```xml
  <font-family xmlns:app="http://schemas.android.com/apk/res-auto"
      app:fontProviderAuthority="com.google.android.gms.fonts"
      app:fontProviderPackage="com.google.android.gms"
      app:fontProviderQuery="..."
      app:fontProviderCerts="@array/com_google_android_gms_fonts_certs">
  </font-family>
  ```

### Step 2: Register New Fonts in `app/src/main/res/values/preloaded_fonts.xml`
- Append items to `<array name="preloaded_fonts" translatable="false">`:
  - `<item>@font/bebas_neue</item>`
  - `<item>@font/teko</item>`
  - `<item>@font/barlow_condensed</item>`
  - `<item>@font/oswald</item>`
  - `<item>@font/chakra_petch</item>`
  - `<item>@font/oxanium</item>`
  - `<item>@font/rajdhani</item>`
  - `<item>@font/montserrat</item>`

### Step 3: Define Localized Strings Across All 9 Languages
- Add the following 8 keys in:
  - `app/src/main/res/values/strings.xml` (Default English)
  - `app/src/main/res/values-de/strings.xml` (German)
  - `app/src/main/res/values-es/strings.xml` (Spanish)
  - `app/src/main/res/values-fr/strings.xml` (French)
  - `app/src/main/res/values-it/strings.xml` (Italian)
  - `app/src/main/res/values-ja/strings.xml` (Japanese)
  - `app/src/main/res/values-nl/strings.xml` (Dutch)
  - `app/src/main/res/values-pl/strings.xml` (Polish)
  - `app/src/main/res/values-pt/strings.xml` (Portuguese)
- Keys:
  - `tuning_font_bebas_neue` -> "Bebas Neue"
  - `tuning_font_teko` -> "Teko"
  - `tuning_font_barlow_condensed` -> "Barlow Condensed"
  - `tuning_font_oswald` -> "Oswald"
  - `tuning_font_chakra_petch` -> "Chakra Petch"
  - `tuning_font_oxanium` -> "Oxanium"
  - `tuning_font_rajdhani` -> "Rajdhani"
  - `tuning_font_montserrat` -> "Montserrat"

### Step 4: Expand Domain Model & Resolver in `CockpitTypography.kt`
- **Location**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/typography/CockpitTypography.kt`
- Expand `CockpitFontFamily` enum:
  - Add: `BEBAS_NEUE`, `TEKO`, `BARLOW_CONDENSED`, `OSWALD`, `CHAKRA_PETCH`, `OXANIUM`, `RAJDHANI`, `MONTSERRAT`.
  - Update `getDisplayNameRes()` to return corresponding `R.string.tuning_font_*`.
- Update `resolveFontFamily`:
  - Map new constants to `FontFamily(Font(R.font.<name>))`.
- Update `fallbackFontFamily`:
  - Map all 8 new constants to `FontFamily.SansSerif`.

### Step 5: Constrain Dropdown Height in `AdvancedTuningDialog.kt`
- **Location**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningDialog.kt`
- In `CockpitTypographySection`:
  - Add `modifier = Modifier.heightIn(max = 360.dp)` to `ExposedDropdownMenu`.

### Step 6: Update Existing Unit Tests
- `CockpitTypographyResolutionTest.kt`:
  - Assert that all 13 font families resolve non-null `FontFamily`.
  - Assert that fallback mappings match expected Compose families.
  - Assert that `getDisplayNameRes()` returns positive resource IDs for all 13.
- `TuningPreferencesCockpitTypographyTest.kt`:
  - Assert that all 13 font families serialize and deserialize cleanly.
  - Assert that invalid string defaults to `SYSTEM_DEFAULT`.

### Step 7: Author Contract & Expansion Tests (`CockpitFontExpansionTest.kt`)
- **Location**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/typography/CockpitFontExpansionTest.kt`
- Verify:
  - All 8 font XML files exist with valid provider authority and certs.
  - `preloaded_fonts.xml` includes all 8 new items.
  - All 8 string keys exist across all 9 localized resource files.
  - `AdvancedTuningDialog.kt` applies `heightIn(max = 360.dp)`.

### Step 8: Clean-Room Full Suite Regression Execution
- Run `./gradlew testDebugUnitTest` across all modules verifying 100% pass rate.

---

## 3. Preserved Invariants & Safeguards

1. **Safe Offline Fallback**:
   - `CockpitTypography.resolveFontFamily` catches `Throwable` and returns `fallbackFontFamily(family)`, preventing crashes on offline devices or JVM unit tests.
2. **DataStore Backward Compatibility**:
   - `valueOf(rawFontFamilyStr)` safely wrapped in `try/catch` in `TuningPreferencesDataStore.kt`; invalid values fall back to `SYSTEM_DEFAULT`.
3. **9-Language Parity**:
   - Zero missing string resources across any of the 9 supported locales.
4. **Weight Orthogonality**:
   - Weights `NORMAL`, `SEMI_BOLD`, `BOLD` remain orthogonal and apply cleanly across all 13 font families.
