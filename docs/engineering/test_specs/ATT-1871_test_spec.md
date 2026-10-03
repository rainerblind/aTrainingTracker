# Stage 2: Requirement & Test Specification - ATT-1871: Expand Curated Font Selection with High-Performance Sports and HUD Fonts

**Ticket**: [ATT-1871](https://rainerblind.atlassian.net/browse/ATT-1871)  
**Sub-task**: [ATT-1928](https://rainerblind.atlassian.net/browse/ATT-1928) (`[Specification]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.8`  
**Requirement Mapping**: `REQ-UI-227` (*Cockpit/Typography: Expanded Curated Athletic Sports and Aerospace HUD Font Selection*)  
**Test Mapping**: `TST-UI-181`  
**Author**: AI Agent 1 (Specification Engineer)  
**Date**: 2026-10-02  

---

## 1. Requirement Specification (`REQ-UI-227`)

The system SHALL expand the curated Cockpit HUD typography palette in Advanced Settings from 5 to 13 options, adding high-performance condensed sports typefaces and aerospace HUD fonts while ensuring defensive offline resolution, safe DataStore persistence, and 9-language localization parity (ATT-1871):

1. **Curated Athletic & HUD Font Additions**:
   - In `CockpitTypography.kt`, `CockpitFontFamily` enum SHALL be expanded with 8 new typeface constants:
     - Athletic Condensed / High Impact:
       - `BEBAS_NEUE` (`R.font.bebas_neue`): Tall, high-impact condensed sports display font.
       - `TEKO` (`R.font.teko`): Sharp condensed geometric athletic timer font.
       - `BARLOW_CONDENSED` (`R.font.barlow_condensed`): Clean grotesk condensed sports font.
       - `OSWALD` (`R.font.oswald`): Classic high-contrast Gothic display font.
     - Technical / Aerospace HUD:
       - `CHAKRA_PETCH` (`R.font.chakra_petch`): Square-cut technical cockpit HUD font.
       - `OXANIUM` (`R.font.oxanium`): Futuristic sci-fi telemetry font.
       - `RAJDHANI` (`R.font.rajdhani`): Squared modular aerospace cockpit font.
     - Modern Geometric:
       - `MONTSERRAT` (`R.font.montserrat`): Highly legible modern geometric sans-serif.

2. **Google Fonts Downloadable Font Descriptors**:
   - The system SHALL define XML font descriptors in `app/src/main/res/font/`:
     - `bebas_neue.xml`: `name=Bebas Neue&amp;weight=700`
     - `teko.xml`: `name=Teko&amp;weight=700`
     - `barlow_condensed.xml`: `name=Barlow Condensed&amp;weight=700`
     - `oswald.xml`: `name=Oswald&amp;weight=700`
     - `chakra_petch.xml`: `name=Chakra Petch&amp;weight=700`
     - `oxanium.xml`: `name=Oxanium&amp;weight=700`
     - `rajdhani.xml`: `name=Rajdhani&amp;weight=700`
     - `montserrat.xml`: `name=Montserrat&amp;weight=700`
   - All XML descriptors SHALL configure:
     - `app:fontProviderAuthority="com.google.android.gms.fonts"`
     - `app:fontProviderPackage="com.google.android.gms"`
     - `app:fontProviderCerts="@array/com_google_android_gms_fonts_certs"`
   - `app/src/main/res/values/preloaded_fonts.xml` SHALL declare all 8 new font resources in `@array/preloaded_fonts`.

3. **Defensive Resolution & Safe Offline Fallback**:
   - `CockpitTypography.resolveFontFamily` SHALL map each new enum constant to `FontFamily(Font(R.font.<name>))` through its concurrent resolution cache (`fontCache`).
   - If font loading throws an exception (offline device, missing Google Play Services, JVM unit test), the resolver SHALL catch `Throwable` and fall back to `fallbackFontFamily(family)`.
   - `CockpitTypography.fallbackFontFamily` SHALL map all 8 new font families to standard Compose `FontFamily.SansSerif`.

4. **Dropdown Container Scroll Resilience**:
   - In `AdvancedTuningDialog.kt`, `ExposedDropdownMenu` SHALL apply `Modifier.heightIn(max = 360.dp)` to ensure the 13-item list scrolls cleanly on compact and landscape displays without clipping or blocking dismiss gestures.

5. **9-Language Localization Parity**:
   - Display name strings SHALL be defined across all 9 supported language resource directories (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`):
     - `tuning_font_bebas_neue`
     - `tuning_font_teko`
     - `tuning_font_barlow_condensed`
     - `tuning_font_oswald`
     - `tuning_font_chakra_petch`
     - `tuning_font_oxanium`
     - `tuning_font_rajdhani`
     - `tuning_font_montserrat`

6. **Preservation of Core Invariants**:
   - Weight variations (`NORMAL`, `SEMI_BOLD`, `BOLD`) remain universally applicable across all 13 fonts.
   - `TuningPreferencesDataStore` serialization and deserialization remain 100% backwards-compatible with `SYSTEM_DEFAULT` fallback on invalid strings.
   - Live preview HUD card in `AdvancedTuningDialog.kt` renders selected font and weight reactively.

---

## 2. Test Cases (`TST-UI-181`)

### TST-UI-181.1: Pure Logic & Fallback Tests (`CockpitTypographyResolutionTest.kt`)
- **TST-UI-181.1.1 (Resolution of all 13 Font Families)**:
  - Iterate through all values of `CockpitFontFamily.values()`.
  - Assert that `CockpitTypography.resolveFontFamily(family)` returns non-null `FontFamily`.
- **TST-UI-181.1.2 (Safe Fallback Family Mappings)**:
  - Verify `CockpitTypography.fallbackFontFamily` maps:
    - `SYSTEM_DEFAULT` -> `FontFamily.Default`
    - `SEVEN_SEGMENT` -> `FontFamily.Default`
    - `MODERN_ATHLETIC` -> `FontFamily.SansSerif`
    - `MONOSPACE` -> `FontFamily.Monospace`
    - `PLAYFUL` -> `FontFamily.Cursive`
    - `BEBAS_NEUE` -> `FontFamily.SansSerif`
    - `TEKO` -> `FontFamily.SansSerif`
    - `BARLOW_CONDENSED` -> `FontFamily.SansSerif`
    - `OSWALD` -> `FontFamily.SansSerif`
    - `CHAKRA_PETCH` -> `FontFamily.SansSerif`
    - `OXANIUM` -> `FontFamily.SansSerif`
    - `RAJDHANI` -> `FontFamily.SansSerif`
    - `MONTSERRAT` -> `FontFamily.SansSerif`
- **TST-UI-181.1.3 (Display Name Resource ID Validity)**:
  - Assert `family.getDisplayNameRes() > 0` for all 13 enum members.
- **TST-UI-181.1.4 (Configuration Factory Complete Object Creation)**:
  - Verify `CockpitTypography.resolveConfig(...)` correctly populates `family`, `weight`, and `resolvedFontFamily` for expanded font selections.

### TST-UI-181.2: Preferences Serialization & Backward Compatibility Tests (`TuningPreferencesCockpitTypographyTest.kt`)
- **TST-UI-181.2.1 (Full Enum Serialization & Deserialization)**:
  - Verify every enum constant in `CockpitFontFamily` serializes via `.name` and deserializes identically via `CockpitFontFamily.valueOf(...)`.
- **TST-UI-181.2.2 (Defensive Deserialization on Unrecognized String)**:
  - Verify corrupted or legacy unknown strings fall back to `TuningPreferencesDefaults.COCKPIT_FONT_FAMILY` (`SYSTEM_DEFAULT`).

### TST-UI-181.3: Font Expansion & Localization Parity Contract Tests (`CockpitFontExpansionTest.kt`)
- **TST-UI-181.3.1 (XML Font Descriptors Audit)**:
  - Verify files `bebas_neue.xml`, `teko.xml`, `barlow_condensed.xml`, `oswald.xml`, `chakra_petch.xml`, `oxanium.xml`, `rajdhani.xml`, `montserrat.xml` exist in `res/font/`.
  - Verify each file defines `app:fontProviderAuthority="com.google.android.gms.fonts"`, `app:fontProviderPackage="com.google.android.gms"`, and `app:fontProviderCerts="@array/com_google_android_gms_fonts_certs"`.
- **TST-UI-181.3.2 (Preloaded Fonts Registration Audit)**:
  - Verify `res/values/preloaded_fonts.xml` contains `@font/<name>` entries for all 8 new fonts.
- **TST-UI-181.3.3 (9-Language String Parity Audit)**:
  - Verify all 8 string keys (`tuning_font_bebas_neue`, `tuning_font_teko`, `tuning_font_barlow_condensed`, `tuning_font_oswald`, `tuning_font_chakra_petch`, `tuning_font_oxanium`, `tuning_font_rajdhani`, `tuning_font_montserrat`) exist in:
    1. `res/values/strings.xml`
    2. `res/values-de/strings.xml`
    3. `res/values-es/strings.xml`
    4. `res/values-fr/strings.xml`
    5. `res/values-it/strings.xml`
    6. `res/values-ja/strings.xml`
    7. `res/values-nl/strings.xml`
    8. `res/values-pl/strings.xml`
    9. `res/values-pt/strings.xml`
- **TST-UI-181.3.4 (ExposedDropdownMenu Scroll Constraint Audit)**:
  - Verify `AdvancedTuningDialog.kt` applies `Modifier.heightIn(max = 360.dp)` to `ExposedDropdownMenu`.

### TST-UI-181.4: Clean-Room Full Suite Regression
- Execute `./gradlew testDebugUnitTest` across all modules verifying 100% pass rate with 0 regressions.

---

## 3. Traceability Matrix

| Requirement | Test Identifier | Target Artifact | Verification Level |
| :--- | :--- | :--- | :--- |
| `REQ-UI-227` (item 1, 3) | `[TST-UI-181.1]` | `CockpitTypographyResolutionTest.kt` | Unit / Logic |
| `REQ-UI-227` (item 6) | `[TST-UI-181.2]` | `TuningPreferencesCockpitTypographyTest.kt` | Unit / Serialization |
| `REQ-UI-227` (item 2, 4, 5) | `[TST-UI-181.3]` | `CockpitFontExpansionTest.kt` | Contract / Structural |
| `REQ-PRO-001` | `[TST-UI-181.4]` | `./gradlew testDebugUnitTest` | Clean-Room Full Suite |
