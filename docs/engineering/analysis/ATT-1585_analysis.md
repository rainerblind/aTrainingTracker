# Stage 1: Problem Domain & Root Cause Analysis - ATT-1585: Harmonious Dark Mode Tonal Progression, Surface Elevation & Contrast Alignment

**Ticket**: [ATT-1585](https://atrainingtracker.atlassian.net/browse/ATT-1585)  
**Parent Epic**: [ATT-1157](https://atrainingtracker.atlassian.net/browse/ATT-1157) (*Optimize dark mode*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.4`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-29  

---

## 1. Problem Statement & User Impact

While the Light Theme of aTrainingTracker provides a cohesive, premium aesthetic with harmonious tonal color transitions (pastel sky blue header -> soft ice blue tabs -> elevated white cards with subtle hairline borders), the Dark Theme suffers from visual fragmentation and contrast imbalances:

1. **Header & Tab Row Tonal Clash**:
   - In `Theme.kt`, `primaryContainer` is defined as saturated midnight navy (`BabyBlueEyeInverse` = `#001A41`), while the scrollable tab bar directly underneath uses neutral dark charcoal (`surfaceContainerHighest` = `surfaceContainerDark` = `#201F20`).
   - This creates an uncoordinated horizontal stripe rather than a unified palette hierarchy across `WorkoutTabsScreen`, `PeriodsTabsScreen`, `SegmentsTabsScreen`, and `EquipmentTabsScreen`.

2. **Flattened Surface Elevation Tokens**:
   - In `Theme.kt`, `surfaceDim`, `surfaceBright`, `surfaceContainerLowest`, `surfaceContainerLow`, `surfaceContainer`, and `surfaceContainerHigh` are all flattened to the exact same color (`DarkSurface` = `#1B1B1F`), which also matches `DarkBackground` (`#1B1B1F`).
   - Consequently, `ElevatedCard` containers in `MappableListItem.kt` (used across Workout summaries, Period summaries, and Known Location cards) have zero container color delta against the background canvas, blending into a flat wall of text without physical depth.

3. **Glaring Disabled Lap Button**:
   - In `LapButton.kt`, the ghosted/disabled state uses `colorResource(R.color.lap_button_disabled_background)` (`#E0E0E0`) and `colorResource(R.color.lap_button_disabled_text)` (`#A0A0A0`).
   - Because no dark-mode override exists in `res/values-night/color.xml`, the button renders on the dark cockpit as a glaring, bright light-grey brick with washed-out text when tracking is stopped or paused.

4. **Primary Color & On-Primary Contrast in XML**:
   - In `res/values-night/color.xml`, `color_primary` is set to Light Theme blue `#1464F4` with `color_on_primary` `#000000`, causing low contrast (~1.8:1) wherever XML colors are referenced in dark mode, violating WCAG standards.

---

## 2. Chesterton's Fence & Requirement Archaeology

### 1. Requirement & Ticket Lineage
* **Historical Trace**:
  - Commit `2e6319401aae72cdc420471aab253c9127e8ee5e` (Ticket `SCRUM-134`): "ui: centralize color theming and normalize component backgrounds".
  - Commit `8e4b85ce` (Ticket `ATT-1264` / `REQ-UI-171`): "Pure black AMOLED dark mode theme".
* **Historical Reason for Existing Formulation**:
  - In `SCRUM-134`, all surface tokens were flattened to `#1B1B1F` and `surfaceTint` set to `Color.Transparent` to eliminate unwanted global Material 3 tonal elevation overlays that caused purple/blue tinting artifacts across sensor grid tiles and dialogs.
  - While this successfully neutralized unwanted tints, Dark Mode was left without any subtle tonal progression or surface container differentiation.
* **Root Reason for Gap**:
  - Flattening all surface tokens to `#1B1B1F` stripped `ElevatedCard` and `surfaceContainerLow` of any visual delta relative to `background` (`#1B1B1F`).
  - `values-night/color.xml` was never updated when Material 3 dark tokens were introduced, leaving legacy XML references with low-contrast combinations.

### 2. Core Invariant Preservation
* **Invariants Preserved**:
  - **AMOLED Pure Black Cockpit Invariant (`REQ-UI-171`, ATT-1264)**: `AmoledDarkColorScheme` must remain completely unchanged with `#000000` pitch black surfaces and `#262626` tile borders.
  - **Sensor Tile Clarity**: Sensor grid tiles must continue to display clean, uncorrupted metric readings and heart rate/power zone background colors without tint artifacts.
  - **WCAG AA/AAA Contrast Compliance**: All text and icon combinations against surfaces must meet or exceed WCAG AA standards (>= 4.5:1 for normal text, >= 3:1 for large text/icons).
  - **Single-Thread SQLite Concurrency**: Database layers remain 100% untouched.

---

## 3. Forensic Scope & Affected Components

1. **`Color.kt` (`ui.theme`)**:
   - Introduce refined slate-blue dark tokens:
     - `DarkPrimaryContainer = Color(0xFF162032)` (refined, deep slate-blue container).
     - `DarkOnPrimaryContainer = Color(0xFFD4E3FF)` (crisp, high-contrast text/icon tint).
     - `DarkBackground = Color(0xFF121214)` (deep, neutral dark canvas).
     - `DarkSurface = Color(0xFF16161A)`.
     - `surfaceContainerLowestDark = Color(0xFF0E0E10)`.
     - `surfaceContainerLowDark = Color(0xFF1C2028)` (elevated card surface with slate tone).
     - `surfaceContainerDark = Color(0xFF222734)`.
     - `surfaceContainerHighDark = Color(0xFF282F3D)`.
     - `surfaceContainerHighestDark = Color(0xFF1B2436)` (coordinated slate-blue tab background).
     - `DarkOutlineVariant = Color(0xFF2B3342)` (subtle slate border outline).

2. **`Theme.kt` (`ui.theme`)**:
   - Update `DarkColorScheme` to map:
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

3. **`MappableListItem.kt` (`ui.components`)**:
   - Add subtle border stroke to `ElevatedCard`: `border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))` ensuring cards have clean boundaries against canvas in both dark and light modes.

4. **`LapButton.kt` (`ui.tracking.trackingtabs`)**:
   - Use `MaterialTheme.colorScheme.primary` and `onPrimary` for active state.
   - Resolve disabled state via theme-aware resources.

5. **Resource Colors (`res/values-night/color.xml`)**:
   - Define dark overrides:
     - `<color name="color_primary">#A6C8FF</color>`
     - `<color name="color_on_primary">#003060</color>`
     - `<color name="lap_button_disabled_background">#22252C</color>`
     - `<color name="lap_button_disabled_text">#5A6270</color>`

---

## 4. Scope Bounding & Out-of-Scope Items

* **In-Scope**:
  - Slate-blue tonal progression between top headers and tab rows in dark mode.
  - Surface container elevation differentiation (`surfaceContainerLowest`, `surfaceContainerLow`, `surfaceContainer`, `surfaceContainerHigh`, `surfaceContainerHighest`).
  - Subtle card contour borders in `MappableListItem`.
  - Dark-mode disabled styling for `LapButton`.
  - Updating `res/values-night/color.xml` to match Material 3 Dark theme standards.
* **Out-of-Scope**:
  - Modifying `AmoledDarkColorScheme` (AMOLED theme remains strictly pitch black per REQ-UI-171).
  - Modifying Light theme colors.
  - Modifying sensor calculation, telemetry formatting, or workout export logic.
