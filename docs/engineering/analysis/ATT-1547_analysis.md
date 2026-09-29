# Stage 1: Problem Domain & Root Cause Analysis - ATT-1547: Dark Mode Navigation Drawer Contrast Tinting

**Ticket**: [ATT-1547](https://atrainingtracker.atlassian.net/browse/ATT-1547)  
**Sub-task**: [ATT-1548](https://atrainingtracker.atlassian.net/browse/ATT-1548) (`[Analysis]`)  
**Parent Epic**: [ATT-1157](https://atrainingtracker.atlassian.net/browse/ATT-1157) (*Optimize dark mode*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.3`  
**Requirement Mapping**: `REQ-UI-123` (*Declarative Compose Navigation Drawer & High-Density Presentation*)  
**Test Mapping**: `TST-UI-135` (*Navigation Drawer Icon Theme-Aware Contrast Tinting Verification*)  
**Branch**: `feature/ATT-1547`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-29  

---

## 1. Executive Summary & Problem Statement

When the application runs in Dark Mode (or AMOLED dark theme), all leading navigation icons in the main navigation drawer (`AppNavigationDrawer.kt`) are rendered as dark grey or black silhouettes against the dark `#121212` drawer sheet background (`surfaceContainer`), resulting in near-zero visual contrast and rendering the icons almost completely invisible (as documented in Jira attachment `nav_drawer_screenshot.png`).

While the adjacent destination labels (e.g. "Einheiten", "Zeiträume", "Karte", "Routen", "Sensoren") correctly resolve to high-contrast white text via `contentColor = colorResource(R.color.color_on_surface)`, the icons fail to adapt.

### Forensic Root Cause Analysis
1. In `AppNavigationDrawer.kt` (`DrawerItemView`), the leading icon is rendered using:
   ```kotlin
   val context = LocalContext.current
   val drawable = remember(item.iconRes) {
       ContextCompat.getDrawable(context, item.iconRes)
   }
   if (drawable != null) {
       Image(
           painter = rememberDrawablePainter(drawable),
           contentDescription = null,
           modifier = Modifier.size(24.dp)
       )
   }
   ```
2. The `Image` composable does not specify a `colorFilter`.
3. Standard monochrome navigation vector drawables in `app/src/main/res/drawable/` (e.g., `workout_list.xml`, `ic_calendar_month.xml`, `ic_map.xml`, `ic_segment.xml`, `ic_route.xml`, `ic_favorite_route.xml`, `my_locations.xml`, etc.) explicitly declare `android:tint="#000000"` or contain black vector path fills (`#000000`).
4. When `ContextCompat.getDrawable(...)` inflates these drawables, they inherit the hardcoded black tint.
5. In Light Mode, black icons against a pure white background `#FFFFFF` yield high contrast (~21:1). In Dark Mode, black icons against `#121212` yield a contrast ratio of ~1.1:1, failing Material Design and WCAG accessibility standards.

---

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**: `REQ-UI-123` (*Declarative Compose Navigation Drawer & High-Density Presentation*), targeting `AppNavigationDrawer.kt` and `MainActivityWithNavigation.kt`.
2. **Historical Origin & Commit Trace**: Introduced in `ATT-243` / `ATT-516` (commit `69cf7240`) to replace legacy Android View `NavigationView` with a pure Jetpack Compose drawer that eliminated reflection-based `InflateException` crashes and compressed vertical row spacing (40dp item height).
3. **Root Reason for Existing Formulation**: The original architectural focus was eliminating View inflation crashes, ensuring touch target ergonomics, and managing dynamic reactive tracking state (`startTrackingTitleRes`). The row text color was assigned dynamically (`contentColor`), but the vector `Image` was rendered directly via `rememberDrawablePainter(drawable)` without a `colorFilter`.
4. **Preservation of Core Invariants**: Compact layout density (~40dp height), item touch targets, selected background highlight (`color_primary` with 0.12 alpha), reactive tracking status state, back navigation hierarchy, and partner brand logos (`logo_square_strava`, `dropbox_logo_blue` retaining authentic brand colors).

---

## 3. Scope Bounding & Impact Analysis

### In-Scope
1. **`DrawerItemConfig` Model Enhancement**:
   - Add property `val tintIcon: Boolean = true` to `DrawerItemConfig`.
   - Set `tintIcon = false` exclusively for partner brand destinations (`R.id.drawer_strava`, `R.id.drawer_dropbox`).
2. **`DrawerItemView` Contrast Tinting**:
   - Apply `colorFilter = if (item.tintIcon) ColorFilter.tint(contentColor) else null` to `Image` in `DrawerItemView`.
   - Ensure selected items tint with `selectedColor` (`color_primary`) and unselected items tint with `unselectedColor` (`color_on_surface`).
3. **Living Documentation & Specification**:
   - Update `REQ-UI-123` in `docs/requirements.md` with explicit theme-aware icon contrast requirements and 4-field Chesterton's Fence archaeology.
   - Define test specification `TST-UI-135` in `docs/tests.md`.
4. **Automated Unit & Layout Tests**:
   - Extend `AppNavigationDrawerTest.kt` with tests verifying `tintIcon` configuration and `ColorFilter` application across Light and Dark theme configurations.

### Out-of-Scope
- Modifying drawer navigation routing logic or `NavRoutes` resolution.
- Changing `DrawerHeader` branding layout or `menu_header_background` painter logic.
- Altering XML vector assets in `res/drawable` (Compose-level tinting provides clean, universal theme adaptation without modifying legacy drawable resources).

---

## 4. Verification Strategy

1. **Unit Test Verification (`TST-UI-135.1`)**:
   - Verify `createDrawerGroups()` assigns `tintIcon = true` for all monochrome items and `tintIcon = false` for Strava and Dropbox.
2. **Layout & Source Code Inspection Test (`TST-UI-135.2`)**:
   - Verify `DrawerItemView` specifies `colorFilter = if (item.tintIcon) ColorFilter.tint(contentColor) else null`.
3. **Contrast & Theme Token Verification (`TST-UI-135.3`)**:
   - Validate that `contentColor` resolves to `color_on_surface` (#FFFFFF in `values-night/color.xml` and #000080 in `values/color.xml`).
4. **Full Test Suite Clean-Room Regression (`TST-UI-135.4`)**:
   - Run `./gradlew testDebugUnitTest` ensuring zero regressions across all modules.
