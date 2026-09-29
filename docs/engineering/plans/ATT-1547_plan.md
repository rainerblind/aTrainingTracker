# Stage 3: Implementation Plan - ATT-1547: [Bug] [Dark Mode] Navigation drawer icons lack contrast tinting and appear invisible on dark background

**Ticket**: [ATT-1547](https://atrainingtracker.atlassian.net/browse/ATT-1547)  
**Sub-task**: [ATT-1550](https://atrainingtracker.atlassian.net/browse/ATT-1550) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-1157](https://atrainingtracker.atlassian.net/browse/ATT-1157) (*Optimize dark mode*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.3`  
**Requirement Mapping**: `REQ-UI-123` (*Declarative Compose Navigation Drawer & High-Density Presentation*)  
**Test Mapping**: `TST-UI-135` (*Navigation Drawer Icon Theme-Aware Contrast Tinting Verification*)  
**Branch**: `feature/ATT-1547`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-29  

---

## 1. Problem Description & Root Cause

In the aTrainingTracker application, navigation between major features is managed via a declarative Jetpack Compose drawer (`AppNavigationDrawer.kt`). On devices running in Dark Mode (or AMOLED dark theme), all monochrome navigation icons are drawn with near-zero contrast against the dark navigation surface (`#121212` / `surfaceContainer`), rendering them almost invisible (captured in Jira attachment `nav_drawer_screenshot.png`).

### Root Cause Analysis
1. In `AppNavigationDrawer.kt` (`DrawerItemView`), the leading icon is rendered using:
   ```kotlin
   Image(
       painter = rememberDrawablePainter(drawable),
       contentDescription = null,
       modifier = Modifier.size(24.dp)
   )
   ```
   No `colorFilter` parameter is supplied to the `Image` composable.
2. Standard Android vector drawables located in `res/drawable/` contain hardcoded black path tints or fills (`android:tint="#000000"` or `android:fillColor="#000000"`).
3. While the adjacent destination label `Text` is dynamically tinted with `contentColor = colorResource(R.color.color_on_surface)` (resolving to pure white `#ffffff` in Dark Mode), the un-tinted vector drawable remains solid black, violating WCAG AA accessibility contrast standards.
4. However, multi-colored partner brand assets (`R.id.drawer_strava` with `logo_square_strava` and `R.id.drawer_dropbox` with `dropbox_logo_blue`) must NOT receive monochrome contrast tinting; they must retain their authentic brand colors.

---

## 2. Requirements & Traceability Mapping

* **`REQ-UI-123`**: *Declarative Compose Navigation Drawer & High-Density Presentation*.
  * Requires theme-aware icon contrast tinting: monochrome items adapt to `color_on_surface` (white in Dark Mode, dark navy `#000080` in Light Mode) when unselected, and `color_primary` when selected.
  * Partner brand assets (Strava, Dropbox) retain authentic brand colors without monochrome tinting.
  * Preserves 40dp row density, touch targets, and reactive tracking status.
* **`TST-UI-135`**: *Navigation Drawer Icon Theme-Aware Contrast Tinting Verification*.
  * `TST-UI-135.1`: Drawer item model and brand exemption unit tests (`tintIcon` flag).
  * `TST-UI-135.2`: `DrawerItemView` `ColorFilter` integration AST and source verification.
  * `TST-UI-135.3`: Theme token and contrast ratio calculations against `#121212` and `#ffffff` (WCAG AA > 4.5:1).
  * `TST-UI-135.4`: Clean-room full test suite regression (`./gradlew testDebugUnitTest`).

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-123` (*Declarative Compose Navigation Drawer & High-Density Presentation*), targeting `AppNavigationDrawer.kt` and `MainActivityWithNavigation.kt`.
* **Historical Origin & Commit Trace**: Introduced in `ATT-243` / `ATT-516` (commit `69cf7240`) to replace legacy Android View `NavigationView` with a pure Jetpack Compose drawer that eliminated reflection-based `InflateException` crashes and compressed vertical row spacing (40dp item height).
* **Root Reason for Existing Formulation**: The original architectural focus was eliminating View inflation crashes, ensuring touch target ergonomics, and managing dynamic reactive tracking state (`startTrackingTitleRes`). The row text color was assigned dynamically (`contentColor`), but the vector `Image` was rendered directly via `rememberDrawablePainter(drawable)` without a `colorFilter`.
* **Preservation of Core Invariants**: Compact layout density (~40dp height), item touch targets, selected background highlight (`color_primary` with 0.12 alpha), reactive tracking status state, back navigation hierarchy, and partner brand logos (`logo_square_strava`, `dropbox_logo_blue` retaining authentic brand colors) are strictly preserved.

---

## 3. System Invariants & Preserved Contracts

1. **Brand Identity Preservation**: Partner brand logos (`R.id.drawer_strava` and `R.id.drawer_dropbox`) MUST retain `tintIcon = false` so authentic brand colors are never overwritten with monochrome tint.
2. **Compact Row Density (ATT-243)**: All navigation drawer items MUST retain 40dp row height, standard horizontal padding (16dp), and 24dp icon bounds.
3. **Selection State Semantics**:
   * Selected item: text and icon tinted with `color_primary` over background highlight `color_primary.copy(alpha = 0.12f)`.
   * Unselected item: text and icon tinted with `color_on_surface` over transparent background.
4. **Navigation Routing & Lifecycle Invariance**: Zero modifications to `NavRoutes`, `MainActivityWithNavigation`, `NavigationDrawerController`, or activity fragment dispatching.
5. **Localization Integrity**: 100% string preservation across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
6. **ASPICE Governance**: Subtasks advance through automated gates; parent ticket `ATT-1547` remains reserved for human approval in Ceremony 2.

---

## 4. Proposed Architectural Changes (SWE.2)

### Component 1: `DrawerItemConfig` Model Extension
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawer.kt`
* **Changes**:
  * Add default property `val tintIcon: Boolean = true` to `data class DrawerItemConfig`.
  * Preserves complete backward compatibility for callers and previews.

### Component 2: Brand Partner Exemption in `createDrawerGroups`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawer.kt`
* **Changes**:
  * In `createDrawerGroups(startTrackingTitleRes: Int)`, configure:
    * `DrawerItemConfig(R.id.drawer_strava, R.drawable.logo_square_strava, R.string.Strava, tintIcon = false)`
    * `DrawerItemConfig(R.id.drawer_dropbox, R.drawable.dropbox_logo_blue, R.string.Dropbox, tintIcon = false)`
  * All remaining 19 items retain default `tintIcon = true`.

### Component 3: Theme-Aware `ColorFilter` in `DrawerItemView`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawer.kt`
* **Changes**:
  * Import `androidx.compose.ui.graphics.ColorFilter`.
  * Update `Image` composable in `DrawerItemView`:
    ```kotlin
    Image(
        painter = rememberDrawablePainter(drawable),
        contentDescription = null,
        modifier = Modifier.size(24.dp),
        colorFilter = if (item.tintIcon) ColorFilter.tint(contentColor) else null
    )
    ```

### Component 4: Dedicated Unit Test Suite (`AppNavigationDrawerTest.kt`)
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawerTest.kt`
* **Scope**: JVM Unit Tests
* **Test Cases**:
  1. `testDrawerGroups_monochromeItems_enableTinting`: Verifies all standard items have `tintIcon == true`.
  2. `testDrawerGroups_partnerBrandItems_exemptFromTinting`: Verifies Strava and Dropbox have `tintIcon == false`.
  3. `testDrawerItemView_appliesConditionalColorFilter`: AST/source inspection asserting `colorFilter = if (item.tintIcon) ColorFilter.tint(contentColor) else null`.
  4. `testThemeTokens_contrastRatioExceedsWcagAA`: Asserts `color_on_surface` in `values-night` is `#ffffff` and in `values` is `#000080`, verifying contrast ratios against background exceed 4.5:1.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update `DrawerItemConfig` and `createDrawerGroups`
* Modify `DrawerItemConfig` in `AppNavigationDrawer.kt` to include `val tintIcon: Boolean = true`.
* Update `drawer_strava` and `drawer_dropbox` entries in `createDrawerGroups` with `tintIcon = false`.

### Step 2: Apply `ColorFilter` in `DrawerItemView`
* In `AppNavigationDrawer.kt`, add `import androidx.compose.ui.graphics.ColorFilter`.
* Pass `colorFilter = if (item.tintIcon) ColorFilter.tint(contentColor) else null` to `Image`.

### Step 3: Author Unit Test Suite `AppNavigationDrawerTest.kt`
* Create `app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawerTest.kt`.
* Implement all four test cases defined in `TST-UI-135`.

### Step 4: Execute Targeted Unit Tests
* Run:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.navigation.AppNavigationDrawerTest"
  ```
* Assert 100% test pass rate.

### Step 5: Execute Clean-Room Full Test Suite Regression
* Run:
  ```bash
  ./gradlew testDebugUnitTest
  ```
* Verify 0 regressions across all modules.

---

## 6. Traceability Matrix

| Test ID | Method / Test File | Requirement | Verification Target |
| :--- | :--- | :--- | :--- |
| `TST-UI-135.1` | `AppNavigationDrawerTest.kt` | `REQ-UI-123` | Monochrome tint flag enabled; partner brand logos exempted (`tintIcon = false`) |
| `TST-UI-135.2` | `AppNavigationDrawerTest.kt` | `REQ-UI-123` | `DrawerItemView` applies `ColorFilter.tint(contentColor)` conditionally |
| `TST-UI-135.3` | `AppNavigationDrawerTest.kt` | `REQ-UI-123` | Night mode `#ffffff` and default `#000080` contrast ratios $\ge 10:1$ |
| `TST-UI-135.4` | Full Test Suite (`./gradlew testDebugUnitTest`) | `REQ-PRO-001` | Clean-room full regression with 0 failures |
