# Stage 2: Requirement & Test Specification - ATT-1592: AppNavigationDrawerKt.DrawerItemView Resources$NotFoundException Crash Resilience

**Ticket**: [ATT-1592](https://atrainingtracker.atlassian.net/browse/ATT-1592)  
**Sub-task**: [ATT-1597](https://atrainingtracker.atlassian.net/browse/ATT-1597) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-235](https://atrainingtracker.atlassian.net/browse/ATT-235) (*No crashs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.4`  
**Requirement Mapping**: `REQ-UI-123` (*Declarative Compose Navigation Drawer, High-Density Presentation, Theme-Aware Contrast Tinting & Defensive Resource Resolution*)  
**Test Spec ID**: `TST-UI-140`  
**Branch**: `bugfix/ATT-1592`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-29  

---

## 1. Requirement Specification (REQ-UI-123)

### 1.1 Problem Statement & Rationale
During production use of version `4.9.37` (build 261), Firebase Crashlytics registered a fatal crash (`4fe293aeb1bc00df48d9ada0ca0db779`) triggered by `Resources$NotFoundException: Resource ID #0x7f080126` in `DrawerItemView (AppNavigationDrawer.kt:319)`:
* The failing resource was `R.drawable.logo_square_strava`.
* While `DrawerItemView` anticipated that a drawable could be `null` by branching to `else { Spacer(modifier = Modifier.size(24.dp)) }`, it retrieved the drawable using `ContextCompat.getDrawable(context, item.iconRes)` without a `try-catch` wrapper.
* Because `ContextCompat.getDrawable` delegates to `ResourcesImpl.getValueForDensity` (which throws `Resources.NotFoundException` when an asset cannot be resolved for the device's display density), the unhandled exception crashed the activity during composition.
* Furthermore, `logo_square_strava` only existed in density-qualified folders (`drawable-mdpi`, `hdpi`, `xhdpi`, `xxhdpi`, `xxxhdpi`, `ldpi`) without a root fallback in `drawable/`, increasing vulnerability to density resolution misses.

### 1.2 Functional & Architectural Requirements
1. **Defensive Resource Exception Handling**:
   * The system SHALL wrap the invocation of `ContextCompat.getDrawable(context, item.iconRes)` in `DrawerItemView` within a defensive `try-catch` block.
   * If `Resources.NotFoundException` or any `Throwable` is thrown during drawable lookup, the system SHALL catch the exception, log a diagnostic warning with the failing resource hex ID, and return `null`.
2. **Graceful Fallback Layout**:
   * When drawable resolution returns `null`, `DrawerItemView` SHALL render a 24dp blank placeholder (`Spacer(modifier = Modifier.size(24.dp))`), preserving row vertical height (40dp), touch targets, horizontal spacing, and label alignment without throwing any exception.
3. **Density Fallback Assets**:
   * The project SHALL provide unqualified root fallback assets in `app/src/main/res/drawable/` for raster drawer icons (`logo_square_strava.png` and `dropbox_logo_blue.png`) to ensure that Android's density fallback hierarchy always resolves a valid bitmap regardless of device DPI configurations or split APKs.
4. **Drawer Group Resource Audit**:
   * All items produced by `createDrawerGroups()` MUST reference valid, non-zero drawable and string resource IDs.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Defensive Lookup Resilience)**:
  * *Given* a `DrawerItemConfig` configured with an invalid, non-existent, or density-unresolvable resource ID,
  * *When* `DrawerItemView` is composed,
  * *Then* the composable SHALL NOT throw `Resources.NotFoundException` or any runtime exception, and SHALL render a 24dp `Spacer` placeholder.
* **Criterion 2 (Valid Resource Rendering)**:
  * *Given* a `DrawerItemConfig` configured with a valid drawable resource (e.g. `R.drawable.logo_square_strava` or `R.drawable.ic_map`),
  * *When* `DrawerItemView` is composed,
  * *Then* the leading icon SHALL render normally with its designated tinting behavior.
* **Criterion 3 (Unqualified Asset Availability)**:
  * *Given* Android resource resolution querying `R.drawable.logo_square_strava` or `R.drawable.dropbox_logo_blue` on an arbitrary or non-standard screen density,
  * *When* density matching occurs,
  * *Then* Android resource manager SHALL find a valid base fallback in `res/drawable/`.

### 1.4 System Invariants
1. Drawer navigation items, IDs, text labels, and click dispatch MUST NOT be altered.
2. Authentic brand logo colors for Strava and Dropbox (`tintIcon = false`) MUST be preserved.
3. App cold start and main navigation drawer opening MUST NEVER crash on drawable resolution failure.
4. Item height standard (40dp) from ATT-243 and touch ergonomics MUST remain intact.

---

### Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-UI-123` (*Declarative Compose Navigation Drawer, High-Density Presentation & Theme-Aware Contrast Tinting*), targeting `AppNavigationDrawer.kt` and `MainActivityWithNavigation.kt`.
* **Historical Origin & Commit Trace**: Introduced in `ATT-243` / `ATT-516` (commit `69cf7240`) to replace legacy Android View `NavigationView` with a pure Jetpack Compose drawer that eliminated reflection-based `InflateException` crashes and compressed vertical row spacing (40dp item height). Enhanced in `ATT-1547` (commit `4d528cb8`) for theme-aware icon contrast tinting, and in `ATT-1592` for defensive resource resolution.
* **Root Reason for Existing Formulation**: The original architectural focus was eliminating View inflation crashes, ensuring touch target ergonomics, and managing dynamic reactive tracking state (`startTrackingTitleRes`). The row text color was assigned dynamically (`contentColor`), and the author provided an `if (drawable != null)` branch, expecting `ContextCompat.getDrawable` to return `null` on failure. The possibility of `Resources.NotFoundException` during density resolution was overlooked.
* **Preservation of Core Invariants**: Compact layout density (~40dp height), item touch targets, selected background highlight (`color_primary` with 0.12 alpha), reactive tracking status state, back navigation hierarchy, theme-aware contrast tinting, and partner brand logos (`logo_square_strava`, `dropbox_logo_blue` retaining authentic brand colors) are strictly preserved.

---

## 3. Test Specification (TST-UI-140)

### Test Case 1: Defensive try-catch Resolution Unit Test (`TST-UI-140.1`)
* **Scope**: Composable Unit Test (Robolectric / Compose Test)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawerTest.kt`
* **Preconditions**: Robolectric test environment with Android context.
* **Action**:
  1. Render `DrawerItemView` with a simulated invalid resource ID (e.g. `0x7f089999`).
  2. Verify that no exception is thrown during composition.
  3. Verify that the composable falls back to the spacer layout.
* **Expected Result**: Clean execution with zero unhandled exceptions.

### Test Case 2: Root Fallback Asset Verification (`TST-UI-140.2`)
* **Scope**: Static Resource Audit Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawerTest.kt`
* **Preconditions**: Project workspace.
* **Action**:
  1. Verify `app/src/main/res/drawable/logo_square_strava.png` exists and is a non-empty image file.
  2. Verify `app/src/main/res/drawable/dropbox_logo_blue.png` exists and is a non-empty image file.
* **Expected Result**: Both files exist in the base `res/drawable/` directory.

### Test Case 3: Drawer Groups Integrity Verification (`TST-UI-140.3`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawerTest.kt`
* **Preconditions**: Robolectric application context.
* **Action**:
  1. Call `createDrawerGroups(R.string.tab_start)`.
  2. For every `DrawerGroup` and every `DrawerItemConfig`:
     - Assert `item.id != 0`.
     - Assert `item.titleRes != 0` and resolves to a non-empty string.
     - Assert `item.iconRes != 0` and resolves to a valid drawable via `ContextCompat.getDrawable`.
* **Expected Result**: All 21 navigation items pass validation with 100% resolvable resources.

### Test Case 4: Clean-Room Full Suite Regression Execution (`TST-UI-140.4`)
* **Scope**: Automated Regression Suite
* **Command**: `./gradlew testDebugUnitTest`
* **Expected Result**: 100% pass rate across all project unit tests with zero regressions.

---

## 4. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-140.1` | Unit | `DrawerItemView` invalid resource resolution | `REQ-UI-123` | Specified |
| `TST-UI-140.2` | Asset Audit | Base `res/drawable/` fallback verification | `REQ-UI-123` | Specified |
| `TST-UI-140.3` | Unit | `createDrawerGroups` resource integrity | `REQ-UI-123` | Specified |
| `TST-UI-140.4` | Regression | Full test suite (`testDebugUnitTest`) | `REQ-PRO-001`, `REQ-UI-123` | Specified |
