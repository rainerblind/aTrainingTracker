# Test Specification & Requirement Synchronization - ATT-1263: [Cockpit] Implement AMOLED Pure Black (#000000) Theme for Tracking Views

## 1. Feature Overview & Test Scope

* **Issue Key**: `ATT-1263`
* **Sub-tasks**: `ATT-1414` (Analysis [Erledigt]), `ATT-1415` (Test-Spec [In Bearbeitung]), `[Impl-Plan]`, `[Implementation]`, `[Test]`
* **Parent Issue**: `ATT-1263` (*[Feature] [Cockpit] Implement AMOLED Pure Black (#000000) Theme for Tracking Views*)
* **Parent Epic**: `ATT-1157` (*[Epic] Optimize dark mode*)
* **Target Version**: `V4.9.38` (Sprint `2026-39.3`)
* **Associated Requirements**: `REQ-UI-101` (Neutral Backgrounds), `REQ-UI-168` (Cockpit Theme Selector), `REQ-UI-169` (AMOLED Pure Black Cockpit Theme)
* **Associated Verification**: `TST-UI-121` (AMOLED Pure Black Theme Verification)

### Objective
Provide true pitch black (`#000000`) AMOLED rendering across the active tracking cockpit (`TrackingTabGridContent` on pages 1..N of `TrackingTabsScreen`) whenever dark mode is active for tracking, turning off OLED pixels completely for maximum outdoor sunlight contrast and minimal battery consumption.

---

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**: `REQ-UI-101` (*Neutral Backgrounds: The system SHALL use neutral white backgrounds (Light) or Material Surface (Dark) for all content areas...*) and `REQ-UI-168` (*Workout Cockpit Independent Theme Selector*).
2. **Historical Origin & Commit Trace**: `REQ-UI-101` established in early architecture for neutral surfaces; `REQ-UI-168` introduced in `ATT-1267` to isolate the cockpit theme from the ambient system mode.
3. **Root Reason for Existing Formulation**: Standard Material 3 dark themes use charcoal grey (`#1B1B1F`), which leaves all OLED pixels illuminated at low power, diminishing direct-sunlight readability and consuming battery during long workouts.
4. **Preservation of Core Invariants**: The global application shell (Navigation Drawer, History, Periods, Settings Dialogs) and `ControlTrackingScreen` (Page 0) continue to use the standard Material 3 theme. Athletic zone color indicators (Heart Rate & Power zones) and 6dp vertical status strips remain prominent.

---

## 2. Harmonized Requirement Specification (`REQ-UI-169`)

### REQ-UI-169: AMOLED Pure Black Cockpit Theme (#000000)
The system SHALL provide a dedicated AMOLED Pure Black (`#000000`) color scheme specifically for the workout tracking cockpit whenever dark mode is active (ATT-1263):

1. **Color Scheme Definition (`AmoledDarkColorScheme`)**:
   - In `Theme.kt`, the system SHALL define `AmoledDarkColorScheme` derived from `DarkColorScheme` with the following overrides:
     - `background = Color(0xFF000000)` (Pure pitch black, OLED pixels off)
     - `surface = Color(0xFF000000)` (Pure pitch black for uncolored sensor field cards)
     - `surfaceVariant = Color(0xFF000000)`
     - `surfaceDim = Color(0xFF000000)`
     - `surfaceBright = Color(0xFF1A1A1A)`
     - `surfaceContainerLowest = Color(0xFF000000)`
     - `surfaceContainerLow = Color(0xFF000000)`
     - `surfaceContainer = Color(0xFF000000)`
     - `surfaceContainerHigh = Color(0xFF121212)`
     - `surfaceContainerHighest = Color(0xFF1E1E1E)`
     - `outlineVariant = Color(0xFF2C2C2E)` (Crisp 1dp tile separation border between pure black tiles)
     - `outline = Color(0xFF38383A)`
     - `onSurface = Color(0xFFFFFFFF)` (Pure high-contrast white for metric digits and labels)
     - `onSurfaceVariant = Color(0xFFC4C6D0)` (Muted readable grey for units and metadata)
     - `onBackground = Color(0xFFFFFFFF)`

2. **Theme Composable Extension**:
   - `ATrainingTrackerTheme` SHALL accept an optional parameter `amoled: Boolean = false`.
   - When `darkTheme == true` and `amoled == true`, `ATrainingTrackerTheme` SHALL inject `AmoledDarkColorScheme` into `MaterialTheme`.

3. **Cockpit Scope Integration**:
   - In `TrackingTabsScreen.kt`, when rendering Pages 1..N (`TrackingTabGridContent`) and `LapButton`, the composables SHALL be wrapped with `ATrainingTrackerTheme(darkTheme = isCockpitDark, amoled = isCockpitDark)`.
   - `ControlTrackingScreen` (Page 0) and the surrounding application shell SHALL NOT receive the AMOLED theme override and SHALL retain the ambient system theme.

4. **Athletic Zone Color Preservation**:
   - When a sensor field in `SensorFieldView` has an active zone color, its background tint SHALL blend cleanly over the black surface (`fieldState.zoneColor.copy(alpha = 0.12f)`), preserving the 6dp vertical zone indicator strip.

---

## 3. Detailed Test Specification (`TST-UI-121`)

### TST-UI-121: AMOLED Pure Black Theme Verification

1. **Color Scheme Token Unit Tests (`AmoledThemeTest.kt`)**:
   - *Test 1.1*: Verify `AmoledDarkColorScheme.background` equals `Color(0xFF000000)`.
   - *Test 1.2*: Verify `AmoledDarkColorScheme.surface` equals `Color(0xFF000000)`.
   - *Test 1.3*: Verify `AmoledDarkColorScheme.onSurface` equals `Color(0xFFFFFFFF)`.
   - *Test 1.4*: Verify `AmoledDarkColorScheme.outlineVariant` equals `Color(0xFF2C2C2E)`.
   - *Test 1.5*: Verify `AmoledDarkColorScheme.surfaceContainer` equals `Color(0xFF000000)`.

2. **Standard vs AMOLED Theme Parity**:
   - *Test 2.1*: Verify standard `DarkColorScheme.background` retains `#1B1B1F` (ensuring the global app dark mode is unchanged).
   - *Test 2.2*: Verify standard `DarkColorScheme.surface` retains `#1B1B1F`.

3. **Clean-Room Regression Execution**:
   - Execute `./gradlew testDebugUnitTest` to ensure zero broken invariants across existing tests.
