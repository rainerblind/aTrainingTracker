# Stage 2: Requirement & Test Specification - ATT-1547: [Bug] [Dark Mode] Navigation drawer icons lack contrast tinting and appear invisible on dark background

**Ticket**: [ATT-1547](https://atrainingtracker.atlassian.net/browse/ATT-1547)  
**Sub-task**: [ATT-1549](https://atrainingtracker.atlassian.net/browse/ATT-1549) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-1157](https://atrainingtracker.atlassian.net/browse/ATT-1157) (*Optimize dark mode*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.3`  
**Requirement Mapping**: `REQ-UI-123` (*Declarative Compose Navigation Drawer & High-Density Presentation*)  
**Test Spec ID**: `TST-UI-135`  
**Branch**: `feature/ATT-1547`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-29  

---

## 1. Requirement Specification (REQ-UI-123)

### 1.1 Problem Statement & Rationale
On devices running in Dark Mode (or AMOLED dark theme), all navigation icons in the main navigation drawer (`AppNavigationDrawer.kt`) are rendered as dark silhouettes with near-zero contrast against the dark background (`#121212` / `surfaceContainer`), rendering them almost invisible (Jira attachment `nav_drawer_screenshot.png`).
* In `AppNavigationDrawer.kt` (`DrawerItemView`), the leading icon is rendered via `Image(painter = rememberDrawablePainter(drawable), ...)` without specifying a `colorFilter`.
* Standard monochrome navigation vector drawables in `res/drawable/` contain `android:tint="#000000"` or black path fills.
* While adjacent destination text labels correctly resolve to high-contrast white via `contentColor = colorResource(R.color.color_on_surface)`, the un-tinted vector images remain black, violating WCAG AA accessibility contrast ratios.

### 1.2 Functional & Architectural Requirements
1. **Model Property Enhancement (`DrawerItemConfig`)**:
   * `DrawerItemConfig` SHALL include `val tintIcon: Boolean = true`.
   * Standard monochrome navigation items across all groups SHALL retain `tintIcon = true`.
   * Multi-colored partner brand assets (`R.id.drawer_strava` with `R.drawable.logo_square_strava` and `R.id.drawer_dropbox` with `R.drawable.dropbox_logo_blue`) SHALL specify `tintIcon = false` to preserve authentic brand colors.
2. **Theme-Aware Icon Contrast Tinting (`DrawerItemView`)**:
   * In `DrawerItemView`, the `Image` composable SHALL apply:
     `colorFilter = if (item.tintIcon) ColorFilter.tint(contentColor) else null`
   * When an item is selected, `contentColor` is `colorResource(R.color.color_primary)`, tinting both text and icon with the primary brand color over a subtle container highlight (`color_primary.copy(alpha = 0.12f)`).
   * When an item is unselected, `contentColor` is `colorResource(R.color.color_on_surface)`, tinting both text and icon with high-contrast `#ffffff` in Dark Mode and `#000080` in Light Mode.
3. **Accessibility & Touch Geometry**:
   * All navigation item rows SHALL maintain compact 40dp row height with standard touch padding and 24dp icon bounds.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-123` (*Declarative Compose Navigation Drawer & High-Density Presentation*), targeting `AppNavigationDrawer.kt` and `MainActivityWithNavigation.kt`.
* **Historical Origin & Commit Trace**: Introduced in `ATT-243` / `ATT-516` (commit `69cf7240`) to replace legacy Android View `NavigationView` with a pure Jetpack Compose drawer that eliminated reflection-based `InflateException` crashes and compressed vertical row spacing (40dp item height).
* **Root Reason for Existing Formulation**: The original architectural focus was eliminating View inflation crashes, ensuring touch target ergonomics, and managing dynamic reactive tracking state (`startTrackingTitleRes`). The row text color was assigned dynamically (`contentColor`), but the vector `Image` was rendered directly via `rememberDrawablePainter(drawable)` without a `colorFilter`.
* **Preservation of Core Invariants**: Compact layout density (~40dp height), item touch targets, selected background highlight (`color_primary` with 0.12 alpha), reactive tracking status state, back navigation hierarchy, and partner brand logos (`logo_square_strava`, `dropbox_logo_blue` retaining authentic brand colors) are strictly preserved.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Dark Mode Contrast Parity)**:
  * *Given* the device configured in Dark Mode (or AMOLED dark theme),
  * *When* the navigation drawer (`AppNavigationDrawer`) is opened,
  * *Then* all monochrome destination icons (Workouts, Periods, Map, Segments, Routes, Lieblingsstrecken, Lieblingsorte, Sensors, Bikes, Shoes, Sport Types, Training Zones, Export, Settings, Units, Displays, Search, Backup, Privacy) SHALL render with high-contrast white tinting matching their adjacent text labels.
* **Criterion 2 (Selection State Tinting)**:
  * *Given* any selected destination in the navigation drawer,
  * *When* the drawer row is rendered,
  * *Then* both the leading icon and text label SHALL render tinted with `color_primary`.
* **Criterion 3 (Brand Partner Logo Integrity)**:
  * *Given* the Online Communities drawer group,
  * *When* Strava and Dropbox rows are rendered,
  * *Then* their icons SHALL render with their authentic brand colors without monochrome tinting.
* **Criterion 4 (Light Mode Visual Parity)**:
  * *Given* the device configured in Light Mode,
  * *When* the navigation drawer is opened,
  * *Then* monochrome destination icons SHALL render with `color_on_surface` (dark navy `#000080`), maintaining high contrast against the white surface.

### 1.5 System Invariants
* Zero disruption to navigation drawer routing, `NavRoutes` resolution, or drawer state controller.
* 40dp compact item density (`LayoutConstants` / ATT-243) and 24dp icon footprint preserved.
* 100% localization parity across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

---

## 2. Test Specification (TST-UI-135)

### Test Case 1: Drawer Item Model & Brand Exemption (`TST-UI-135.1`)
* **Scope**: JVM Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawerTest.kt`
* **Actions**:
  1. Instantiate `createDrawerGroups(R.string.tab_start)`.
  2. Verify that all monochrome item configs have `tintIcon == true`.
  3. Verify that `R.id.drawer_strava` and `R.id.drawer_dropbox` have `tintIcon == false`.
* **Expected Result**: 100% compliance across all 21 navigation items.

### Test Case 2: DrawerItemView ColorFilter Integration (`TST-UI-135.2`)
* **Scope**: JVM Unit / Layout Inspection Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawerTest.kt`
* **Actions**:
  1. Inspect `DrawerItemView` source in `AppNavigationDrawer.kt`.
  2. Verify that `Image` defines `colorFilter = if (item.tintIcon) ColorFilter.tint(contentColor) else null`.
* **Expected Result**: ColorFilter is applied conditionally based on `item.tintIcon`.

### Test Case 3: Theme Token & Contrast Ratio Audit (`TST-UI-135.3`)
* **Scope**: Resource & Contrast Verification Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawerTest.kt`
* **Actions**:
  1. Verify `color_on_surface` in `values-night/color.xml` is `#ffffff`.
  2. Verify `color_on_surface` in `values/color.xml` is `#000080`.
  3. Calculate contrast ratio against `#121212` (dark) and `#ffffff` (light), asserting WCAG AA compliance (> 4.5:1).
* **Expected Result**: Contrast ratios exceed 10:1 in both modes.

### Test Case 4: Clean-Room Full Regression Suite (`TST-UI-135.4`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Validate that all existing unit, repository, and ViewModel tests continue to pass with 100% success rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-135.1` | Unit Test | `createDrawerGroups` / `DrawerItemConfig` | `REQ-UI-123` | Specified |
| `TST-UI-135.2` | Layout Inspection | `DrawerItemView` (`AppNavigationDrawer.kt`) | `REQ-UI-123` | Specified |
| `TST-UI-135.3` | Theme Contrast Audit | `color.xml` (default & night) | `REQ-UI-123` | Specified |
| `TST-UI-135.4` | Clean-Room Regression | Full Test Suite (`./gradlew testDebugUnitTest`) | `REQ-PRO-001` | Specified |
