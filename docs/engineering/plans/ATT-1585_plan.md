# Stage 3: Implementation Plan - ATT-1585: Harmonious Dark Mode Tonal Progression, Surface Elevation & Contrast Alignment

**Ticket**: [ATT-1585](https://atrainingtracker.atlassian.net/browse/ATT-1585)  
**Parent Epic**: [ATT-1157](https://atrainingtracker.atlassian.net/browse/ATT-1157) (*Optimize dark mode*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.4`  
**Requirement Mapping**: `REQ-UI-190` (*UI/Theme: Harmonious Dark Mode Tonal Progression, Surface Container Elevation & Contrast Alignment*)  
**Test Mapping**: `TST-UI-144` (`TST-UI-144.1`, `TST-UI-144.2`, `TST-UI-144.3`, `TST-UI-144.4`)  
**Branch**: `feature/ATT-1585`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-29  

---

## 1. Problem Description & Background

While the Light Theme of aTrainingTracker provides a cohesive, premium aesthetic with harmonious tonal color transitions (pastel sky blue header -> soft ice blue tabs -> elevated white cards with subtle hairline borders), the Dark Theme currently suffers from visual fragmentation:
1. **Header & Tab Row Tonal Clash**: `primaryContainer` is midnight navy (`#001A41`), while tab backgrounds use dark charcoal (`#201F20`), creating an uncoordinated horizontal stripe.
2. **Flattened Surface Elevation Tokens**: All surface container tokens (`surfaceContainerLowest`, `surfaceContainerLow`, `surfaceContainer`, `surfaceContainerHigh`) were flattened to `#1B1B1F` in `SCRUM-134`. Consequently, cards have zero elevation against the `#1B1B1F` background, blending into a flat wall of text.
3. **Glaring Disabled Lap Button**: `LapButton.kt` uses hardcoded light-mode disabled colors (`#E0E0E0` background, `#A0A0A0` text) with no dark mode override in `res/values-night/color.xml`.
4. **Primary Color Contrast in XML**: `res/values-night/color.xml` still uses light blue `#1464F4` with black `#000000` text (~1.8:1 contrast).

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-190` (*UI/Theme: Harmonious Dark Mode Tonal Progression, Surface Container Elevation & Contrast Alignment*)
* **Test Mapping**: `TST-UI-144`
  - `TST-UI-144.1`: Unit test verifying `DarkColorScheme` constants, slate-blue header/tab progression, and graduated surface container hierarchy (`DarkThemeTonalHierarchyTest.kt`).
  - `TST-UI-144.2`: Visual contract tests verifying `MappableListItem` card border outline and AMOLED theme preservation (`DarkThemeVisualContractTest.kt`).
  - `TST-UI-144.3`: XML color resource and LapButton contrast verification (`LapButtonDarkThemeTest.kt`).
  - `TST-UI-144.4`: Clean-room full regression unit test suite (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **AMOLED Pure Black Cockpit Invariant (`REQ-UI-171`)**:
   - `AmoledDarkColorScheme` must remain 100% untouched (`#000000` pitch black surfaces, `#262626` tile dividers).
2. **Light Theme Invariant**:
   - Light theme colors and surface containers must remain completely unchanged.
3. **Sensor Grid Telemetry Invariant**:
   - Sensor metric tiles and heart rate/power zone background colors must remain completely uncorrupted.
4. **WCAG Contrast Invariant**:
   - Primary action buttons and text against dark surfaces must meet or exceed WCAG AA standards (>= 4.5:1).

---

## 4. Proposed Architectural Changes (SWE.2)

### Component 1: Theme Color Tokens (`Color.kt`)
* Define slate-blue dark tokens in `com.atrainingtracker.trainingtracker.ui.theme`:
  - `DarkPrimaryContainer = Color(0xFF162032)`
  - `DarkOnPrimaryContainer = Color(0xFFD4E3FF)`
  - `DarkBackground = Color(0xFF121214)`
  - `DarkSurface = Color(0xFF16161A)`
  - `surfaceContainerLowestDark = Color(0xFF0E0E10)`
  - `surfaceContainerLowDark = Color(0xFF1C2028)`
  - `surfaceContainerDark = Color(0xFF20242E)`
  - `surfaceContainerHighDark = Color(0xFF282F3D)`
  - `surfaceContainerHighestDark = Color(0xFF1B2436)`
  - `DarkOutlineVariant = Color(0xFF2B3342)`

### Component 2: Dark Color Scheme Mapping (`Theme.kt`)
* Update `DarkColorScheme`:
  - `primaryContainer = DarkPrimaryContainer`
  - `onPrimaryContainer = DarkOnPrimaryContainer`
  - `background = DarkBackground`
  - `surface = DarkSurface`
  - `surfaceContainerLowest = surfaceContainerLowestDark`
  - `surfaceContainerLow = surfaceContainerLowDark`
  - `surfaceContainer = surfaceContainerDark`
  - `surfaceContainerHigh = surfaceContainerHighDark`
  - `surfaceContainerHighest = surfaceContainerHighestDark`
  - `outlineVariant = DarkOutlineVariant`

### Component 3: Card Boundary Definition (`MappableListItem.kt`)
* Add `border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))` to `ElevatedCard`.

### Component 4: Theme-Aware Lap Button & XML Colors (`LapButton.kt`, `res/values-night/color.xml`)
* In `LapButton.kt`:
  - Bind `containerColor = MaterialTheme.colorScheme.primary` and `contentColor = MaterialTheme.colorScheme.onPrimary`.
  - Bind `disabledContainerColor = colorResource(R.color.lap_button_disabled_background)`.
  - Bind `disabledContentColor = colorResource(R.color.lap_button_disabled_text)`.
* In `res/values-night/color.xml`:
  - Update `color_primary` to `#A6C8FF`.
  - Update `color_on_primary` to `#003060`.
  - Define `lap_button_disabled_background` as `#22252C`.
  - Define `lap_button_disabled_text` as `#5A6270`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update `Color.kt`
* Add slate-blue dark tokens and graduated surface container color constants.

### Step 2: Update `Theme.kt`
* Map the new color tokens in `DarkColorScheme`.

### Step 3: Enhance `MappableListItem.kt`
* Add subtle `outlineVariant` border stroke to `ElevatedCard`.

### Step 4: Update `LapButton.kt` and `res/values-night/color.xml`
* Add dark mode overrides in `values-night/color.xml`.
* Update `LapButton.kt` button colors to consume theme primary/onPrimary and resource disabled colors.

### Step 5: Author Unit Tests & Full Regression
* Author `DarkThemeTonalHierarchyTest.kt`, `DarkThemeVisualContractTest.kt`, and `LapButtonDarkThemeTest.kt`.
* Run targeted tests: `./gradlew testDebugUnitTest --tests "*DarkTheme*" --tests "*LapButton*"`.
* Run full suite: `./gradlew testDebugUnitTest`.

---

## 6. Verification & Rollback Plan

* **Verification**:
  - Unit tests verifying color hex values, tonal progression, card border stroke, and WCAG contrast.
  - Full clean-room test suite execution.
* **Rollback Strategy**:
  - Work is isolated on `feature/ATT-1585`. Reverting leaves `sprint/2026-40.4` undisturbed.
