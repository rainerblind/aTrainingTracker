# Test Specification & Requirement Synchronization - ATT-1413: [Cockpit] Comprehensive Cockpit Dark Mode: Apply dark theme to Top Bar, Tab Row, and Navigation Bar

## 1. Feature Overview & Test Scope

* **Issue Key**: `ATT-1413`
* **Sub-tasks**: `ATT-1419` (Analysis [Erledigt]), `ATT-1420` (Test-Spec [In Bearbeitung]), `[Impl-Plan]`, `[Implementation]`, `[Test]`
* **Parent Issue**: `ATT-1413` (*[Feature] [Cockpit] Comprehensive Cockpit Dark Mode: Apply dark theme to Top Bar, Tab Row, and Navigation Bar*)
* **Parent Epic**: `ATT-1157` (*[Epic] Optimize dark mode*)
* **Target Version**: `V4.9.38` (Sprint `2026-39.3`)
* **Associated Requirements**: `REQ-UI-170` (Comprehensive Cockpit Dark Theme), referencing `REQ-UI-168` (Cockpit Theme Selector) and `REQ-UI-169` (AMOLED Pure Black Theme)
* **Associated Verification**: `TST-UI-122` (Comprehensive Cockpit Dark Theme Verification)

### Objective
Extend the workout cockpit dark theme across the entire `TrackingTabsScreen` root, including the top header (`primaryContainer`), tab row (`surfaceContainerHighest`), and bottom navigation bar padding, eliminating the light-blue top bar and white bottom navigation padding on telemetry pages while cleanly preserving ambient system styling on Page 0 (Control Tracking) per user mandate.

---

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**: `REQ-UI-168` (*Workout Cockpit Independent Theme Selector*) and `REQ-UI-169` (*AMOLED Pure Black Cockpit Theme*).
2. **Historical Origin & Commit Trace**: `ATT-1267` introduced independent cockpit theme selection; `ATT-1263` introduced `AmoledDarkColorScheme` (#000000). Both originally scoped dark theme only around `TrackingTabGridContent` and `LapButton` inside the pager.
3. **Root Reason for Existing Formulation**: Scoping dark theme strictly around `TrackingTabGridContent` was an initial cautious approach to isolate telemetry views without touching the surrounding navigation and headers. However, on physical devices, this left the top bar and tab row light blue and the navigation bar white, breaking OLED battery savings and blinding athletes with glare in night/sunlight riding conditions (`cockpit_dark_mode_partial_top_bottom_light.png`).
4. **Preservation of Core Invariants**: Per user instruction, Page 0 (`ControlTrackingScreen`) remains in the ambient system theme when device is in light mode. Outer application destinations (Drawer, History, Periods, Settings Dialogs) remain in standard ambient theme. When leaving `TrackingTabsScreen`, system bar insets cleanly revert to the ambient system mode.

---

## 2. Harmonized Requirement Specification (`REQ-UI-170`)

### REQ-UI-170: Comprehensive Cockpit Dark Theme (Top Bar, Tab Row, and Navigation Bar)
The system SHALL extend the workout cockpit dark theme across the entire `TrackingTabsScreen` layout, unifying top bar, tab bar, content, and navigation bar under AMOLED pure black on telemetry views while isolating Page 0 (ATT-1413):

1. **Dynamic Cockpit Theme State Resolution**:
   - The system SHALL provide a pure, deterministic resolution function `resolveEffectiveCockpitThemeState(screenMode, currentPage, cockpitThemeMode, isSystemDark): CockpitThemeState`:
     - *Telemetry Views (Pages 1..N in TRACKING mode, or all pages in CONFIGURATION / PREVIEW mode)*:
       - `darkTheme = isCockpitDark` (where `isCockpitDark = if (cockpitThemeMode == ALWAYS_DARK) true else isSystemDark`)
       - `amoled = isCockpitDark`
     - *Control Tracking (Page 0 in TRACKING mode)*:
       - `darkTheme = isSystemDark` (following host OS theme)
       - `amoled = false`
2. **Root Theme Enclosure**:
   - In `TrackingTabsScreen.kt`, the root `Surface` and all child containers SHALL be enclosed within `ATrainingTrackerTheme(darkTheme = themeState.darkTheme, amoled = themeState.amoled)`.
   - Redundant nested `ATrainingTrackerTheme` wrappers around `TrackingTabGridContent` and `LapButton` SHALL be removed.
3. **AMOLED Header & Tab Row Harmonization**:
   - In `AmoledDarkColorScheme`, `primaryContainer` SHALL be `Color(0xFF000000)` and `onPrimaryContainer` SHALL be `Color(0xFFFFFFFF)`.
   - In `AmoledDarkColorScheme`, `surfaceContainerHighest` SHALL be `Color(0xFF000000)`.
   - The top header `Surface(color = MaterialTheme.colorScheme.primaryContainer)` and `PrimaryScrollableTabRow(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)` SHALL render in true pitch black (`#000000`) without light-blue borders or strips.
4. **System Bars & Navigation Inset Integration**:
   - In `Theme.kt`, `WindowCompat.getInsetsController` SHALL update both `isAppearanceLightStatusBars = !darkTheme` and `isAppearanceLightNavigationBars = !darkTheme`, with `statusBarColor = colorScheme.surface.toArgb()` and `navigationBarColor = colorScheme.surface.toArgb()`.
   - Window interactions SHALL be guarded against detached, finishing, or destroyed activities.
   - In `TrackingTabsScreen.kt`, a `DisposableEffect` SHALL restore ambient system bar contrast (`!isSystemDark`) upon screen exit.
5. **Contrast Compliance**:
   - All text and indicators in `PrimaryScrollableTabRow` and `SensorStatus` SHALL meet WCAG 2.1 AA standards (minimum 4.5:1 for body/labels, 3.0:1 for disabled elements).

---

## 3. Detailed Test Specification (`TST-UI-122`)

### TST-UI-122: Comprehensive Cockpit Dark Theme Verification

1. **State Resolution Unit Tests (`TrackingThemeResolutionTest.kt`)**:
   - *Test 1.1*: Page 0 in TRACKING mode with `ALWAYS_DARK` and `isSystemDark = false`: Assert `darkTheme == false` and `amoled == false` (telemetry-only user policy).
   - *Test 1.2*: Page 1 in TRACKING mode with `ALWAYS_DARK` and `isSystemDark = false`: Assert `darkTheme == true` and `amoled == true`.
   - *Test 1.3*: Page 0 in TRACKING mode with `ALWAYS_DARK` and `isSystemDark = true`: Assert `darkTheme == true` and `amoled == false`.
   - *Test 1.4*: Page 1 in TRACKING mode with `SYSTEM` and `isSystemDark = false`: Assert `darkTheme == false` and `amoled == false`.
   - *Test 1.5*: Page 1 in TRACKING mode with `SYSTEM` and `isSystemDark = true`: Assert `darkTheme == true` and `amoled == true`.
   - *Test 1.6*: Page 0 in CONFIGURATION mode with `ALWAYS_DARK`: Assert `darkTheme == true` and `amoled == true` (all pages in config mode are telemetry).
   - *Test 1.7*: Page 0 in PREVIEW mode with `ALWAYS_DARK`: Assert `darkTheme == true` and `amoled == true`.
   - *Test 1.8*: Rapid page toggling (0 -> 1 -> 0 -> 2): Assert deterministic, stateless resolution without drift.

2. **Color Scheme Token Unit Tests (`AmoledThemeTest.kt`)**:
   - *Test 2.1*: Verify `AmoledDarkColorScheme.primaryContainer` equals `Color(0xFF000000)`.
   - *Test 2.2*: Verify `AmoledDarkColorScheme.onPrimaryContainer` equals `Color(0xFFFFFFFF)`.
   - *Test 2.3*: Verify `AmoledDarkColorScheme.surfaceContainerHighest` equals `Color(0xFF000000)`.
   - *Test 2.4*: Verify standard `DarkColorScheme.primaryContainer` remains `BabyBlueEyeInverse` (`Color(0xFF001A41)`), ensuring non-AMOLED dark theme is unaffected.

3. **Clean-Room Full Suite Regression Execution**:
   - Execute `./gradlew testDebugUnitTest` across all modules to verify 100% pass rate.
