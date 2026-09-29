# Stage 2: Requirement & Test Specification - ATT-1585: Harmonious Dark Mode Tonal Progression, Surface Elevation & Contrast Alignment

**Ticket**: [ATT-1585](https://atrainingtracker.atlassian.net/browse/ATT-1585)  
**Parent Epic**: [ATT-1157](https://atrainingtracker.atlassian.net/browse/ATT-1157) (*Optimize dark mode*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.4`  
**Requirement Mapping**: `REQ-UI-190` (*UI/Theme: Harmonious Dark Mode Tonal Progression, Surface Container Elevation & Contrast Alignment*)  
**Test Mapping**: `TST-UI-144` (`TST-UI-144.1`, `TST-UI-144.2`, `TST-UI-144.3`, `TST-UI-144.4`)  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-29  

---

## 1. Formal Requirement Specification

### REQ-UI-190: UI/Theme: Harmonious Dark Mode Tonal Progression, Surface Container Elevation & Contrast Alignment
The system SHALL establish a cohesive dark theme palette hierarchy, surface container elevation progression, and contrast compliance (ATT-1585):

1. **Slate-Blue Tonal Progression (`Color.kt`, `Theme.kt`)**:
   - The dark theme SHALL provide a coordinated slate-blue palette progression connecting status bars, screen headers, and tab rows:
     - Top status bar and screen header container: `primaryContainer = DarkPrimaryContainer` (`#162032`),
     - Header title and actions: `onPrimaryContainer = DarkOnPrimaryContainer` (`#D4E3FF`),
     - Tab row container: `surfaceContainerHighest = surfaceContainerHighestDark` (`#1B2436`),
     - Tab active indicator: `primary = DarkPrimary` (`#A6C8FF`).

2. **Surface Container Elevation Hierarchy (`Color.kt`, `Theme.kt`)**:
   - `DarkColorScheme` SHALL define distinct, graduated surface container elevation tokens:
     - Canvas background: `background = DarkBackground` (`#121214`),
     - Base surface: `surface = DarkSurface` (`#16161A`),
     - Lowest container: `surfaceContainerLowest = surfaceContainerLowestDark` (`#0E0E10`),
     - Low container (elevated cards): `surfaceContainerLow = surfaceContainerLowDark` (`#1C2028`),
     - Standard container: `surfaceContainer = surfaceContainerDark` (`#20242E`),
     - High container: `surfaceContainerHigh = surfaceContainerHighDark` (`#282F3D`),
     - Outline border: `outlineVariant = DarkOutlineVariant` (`#2B3342`).

3. **Card Boundary Definition (`MappableListItem.kt`)**:
   - `MappableListItem` SHALL apply a subtle hairline border stroke `BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))` to `ElevatedCard`, ensuring clean visual demarcation of workout cards, period summaries, and location cards against the canvas in both dark and light modes.

4. **Theme-Aware Lap Button & Resource Contrast (`LapButton.kt`, `res/values-night/color.xml`)**:
   - `LapButton` active state SHALL use `MaterialTheme.colorScheme.primary` and `onPrimary`.
   - In `res/values-night/color.xml`, disabled colors SHALL be defined as:
     - `lap_button_disabled_background` = `#22252C`,
     - `lap_button_disabled_text` = `#5A6270`,
     - `color_primary` = `#A6C8FF`,
     - `color_on_primary` = `#003060`.
   - Primary and text contrast against surfaces SHALL meet WCAG AA (>= 4.5:1).

5. **Invariants**:
   - AMOLED pure black cockpit theme (`AmoledDarkColorScheme`, `REQ-UI-171`) MUST NOT be altered.
   - Light theme colors MUST NOT be altered.
   - Sensor grid metric tiles and zone colors MUST NOT be altered.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**: Net-new requirement (`REQ-UI-190`), complementing `REQ-UI-171` (*Pure Black AMOLED Dark Mode Theme*) and building upon Epic `ATT-1157` (*Optimize dark mode*).
2. **Historical Origin & Commit Trace**: Commit `2e6319401aae72cdc420471aab253c9127e8ee5e` (Ticket `SCRUM-134`): "ui: centralize color theming and normalize component backgrounds".
3. **Root Reason for Existing Formulation**: In `SCRUM-134`, all surface tokens were flattened to `#1B1B1F` and `surfaceTint` set to `Color.Transparent` to eliminate unwanted global Material 3 tonal elevation overlays that caused purple/blue tinting artifacts across sensor grid tiles and dialogs. However, this left Dark Mode without subtle tonal progression or card elevation.
4. **Preservation of Core Invariants**:
   - AMOLED pure black cockpit theme (`AmoledDarkColorScheme`, `REQ-UI-171`) remains 100% untouched (`#000000` pitch black).
   - Light theme colors remain unchanged.
   - Sensor grid metric tiles and zone colors unaffected.
   - WCAG AA/AAA contrast standards preserved.

---

## 3. Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Header & Tab Harmony)**:
  * *Given* the app is running in Dark Mode,
  * *When* viewing screens with a header and tab bar (`TrackingTabsScreen`, `WorkoutTabsScreen`, `PeriodsTabsScreen`),
  * *Then* the header and tab row SHALL display a coordinated slate-blue tonal progression (`#162032` -> `#1B2436`), eliminating the navy/charcoal horizontal mismatch.

* **Criterion 2 (Card Elevation & Separation)**:
  * *Given* the Workouts list (`Einheiten`), Periods, or Locations list in Dark Mode,
  * *When* viewing cards,
  * *Then* each card SHALL render with elevated surface color `#1C2028` and subtle outline `#2B3342`, distinct from background `#121214`.

* **Criterion 3 (Ghosted Lap Button Appearance)**:
  * *Given* the Runden cockpit tab when tracking is stopped or paused in Dark Mode,
  * *When* the "+ Runde" button is displayed,
  * *Then* the button background SHALL render as a muted dark container (`#22252C` with text `#5A6270`) without glaring light-grey background.

* **Criterion 4 (WCAG Contrast Compliance)**:
  * *Given* primary actions and selected items in Dark Mode,
  * *When* evaluating text and icon contrast against dark surfaces,
  * *Then* the contrast ratio SHALL meet or exceed WCAG AA standards (>= 4.5:1 for normal text, >= 3:1 for large text/icons).

---

## 4. Verification & Test Specification (TST-UI-144)

### TST-UI-144.1: Dark Color Scheme Tonal Hierarchy Unit Tests (`DarkThemeTonalHierarchyTest.kt`)
* **Objective**: Verify `DarkColorScheme` constants, slate-blue header/tab progression, and graduated surface container hierarchy.
* **Assertions**:
  - `DarkColorScheme.primaryContainer == Color(0xFF162032)` (`DarkPrimaryContainer`).
  - `DarkColorScheme.onPrimaryContainer == Color(0xFFD4E3FF)` (`DarkOnPrimaryContainer`).
  - `DarkColorScheme.surfaceContainerHighest == Color(0xFF1B2436)` (`surfaceContainerHighestDark`).
  - `DarkColorScheme.background == Color(0xFF121214)`.
  - `DarkColorScheme.surfaceContainerLow == Color(0xFF1C2028)`.
  - `DarkColorScheme.surfaceContainer == Color(0xFF20242E)`.
  - `DarkColorScheme.surfaceContainerHigh == Color(0xFF282F3D)`.
  - `DarkColorScheme.outlineVariant == Color(0xFF2B3342)`.
  - Progression invariant: `background != surfaceContainerLow`.

### TST-UI-144.2: Card Border & Visual Separation Tests (`DarkThemeVisualContractTest.kt`)
* **Objective**: Verify `MappableListItem` applies `outlineVariant` border stroke to `ElevatedCard`.
* **Assertions**:
  - `MappableListItem.kt` applies `border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))` to `ElevatedCard`.
  - AMOLED theme invariants: `AmoledDarkColorScheme.background == Color(0xFF000000)` and `AmoledDarkColorScheme.surface == Color(0xFF000000)`.

### TST-UI-144.3: Theme-Aware Lap Button & XML Color Verification (`LapButtonDarkThemeTest.kt`)
* **Objective**: Verify `res/values-night/color.xml` defines dark-mode disabled colors and high-contrast primary tokens.
* **Assertions**:
  - `res/values-night/color.xml` contains `lap_button_disabled_background` as `#22252C`.
  - `res/values-night/color.xml` contains `lap_button_disabled_text` as `#5A6270`.
  - `res/values-night/color.xml` contains `color_primary` as `#A6C8FF` and `color_on_primary` as `#003060`.
  - Computed contrast between `#A6C8FF` and `#003060` meets WCAG AAA (>= 7:1).

### TST-UI-144.4: Clean-Room Full Suite Regression Execution
* **Objective**: Verify zero regressions across the complete unit test suite.
* **Command**: `./gradlew testDebugUnitTest`
* **Success Criteria**: 100% pass rate with 0 failures across all test suites.

---

## 5. Traceability Matrix

| Requirement | Test ID | Verification Target | Status |
| :--- | :--- | :--- | :--- |
| `REQ-UI-190.1` | `TST-UI-144.1` | Slate-blue tonal progression (`primaryContainer`, `surfaceContainerHighest`) | Proposed |
| `REQ-UI-190.2` | `TST-UI-144.1` | Surface container elevation hierarchy (`background`, `surfaceContainerLow/High`) | Proposed |
| `REQ-UI-190.3` | `TST-UI-144.2` | Card border and visual separation in `MappableListItem` | Proposed |
| `REQ-UI-190.4` | `TST-UI-144.3` | Theme-aware LapButton and `values-night/color.xml` contrast | Proposed |
| `REQ-UI-190.5` | `TST-UI-144.2` | AMOLED pure black theme invariant preservation (`REQ-UI-171`) | Proposed |
| `REQ-UI-190` | `TST-UI-144.4` | Full clean-room unit test regression suite (`./gradlew testDebugUnitTest`) | Proposed |
