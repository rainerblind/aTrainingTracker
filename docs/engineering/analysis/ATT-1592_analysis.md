# Stage 1 Analysis: ATT-1592 - AppNavigationDrawerKt.DrawerItemView Resources$NotFoundException Crash Resilience

**Ticket**: [ATT-1592](https://atrainingtracker.atlassian.net/browse/ATT-1592)  
**Sub-task**: [ATT-1596](https://atrainingtracker.atlassian.net/browse/ATT-1596) (`[Analysis]`)  
**Parent Epic**: [ATT-235](https://atrainingtracker.atlassian.net/browse/ATT-235) (*No crashs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.4`  
**Requirement Mapping**: `REQ-UI-123` (*Declarative Compose Navigation Drawer, High-Density Presentation & Defensive Resource Resolution*)  
**Test Mapping**: `TST-UI-136` (*Drawer Icon Defensive Resource Resolution Regression Verification*)  
**Branch**: `bugfix/ATT-1592`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-29  

---

## 1. Problem Statement & Motivation

During production execution of release `4.9.37` (build 261), Firebase Crashlytics captured a fatal application crash affecting user sessions (Crashlytics Issue `4fe293aeb1bc00df48d9ada0ca0db779`, Session `6ABBC01302F700010E70AE383949DDCF_DNE_0_v2`):

```
Fatal Exception: android.content.res.Resources$NotFoundException: Resource ID #0x7f080126
       at android.content.res.ResourcesImpl.getValueForDensity(ResourcesImpl.java:234)
       at android.content.res.Resources.getDrawableForDensity(Resources.java:982)
       at android.content.res.Resources.getDrawable(Resources.java:922)
       at android.content.Context.getDrawable(Context.java:753)
       at androidx.core.content.ContextCompat$Api21Impl.getDrawable(ContextCompat.java:1061)
       at androidx.core.content.ContextCompat.getDrawable(ContextCompat.java:496)
       at com.atrainingtracker.trainingtracker.ui.navigation.AppNavigationDrawerKt.DrawerItemView(AppNavigationDrawer.kt:319)
       at com.atrainingtracker.trainingtracker.ui.navigation.AppNavigationDrawerKt.DrawerGroupView(AppNavigationDrawer.kt:285)
       at com.atrainingtracker.trainingtracker.ui.navigation.AppNavigationDrawerKt.AppNavigationDrawer$lambda$0(AppNavigationDrawer.kt:188)
```

Because `AppNavigationDrawer` is bound to the main activity (`MainActivityWithNavigation`) and initialized during the primary UI composition lifecycle, an unhandled exception thrown during drawer row inflation causes a fatal application crash, preventing the user from using the application.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Symbolic Resource Mapping
Inspection of the production release symbols (`app/build/intermediates/runtime_symbol_list/release/processReleaseResources/R.txt`) confirms the exact resource ID referenced in the stacktrace:
```
int drawable logo_square_strava 0x7f080126
```
The failing resource ID `0x7f080126` corresponds to `R.drawable.logo_square_strava`, used as the leading icon for the Strava drawer item (`R.id.drawer_strava`).

### 2.2 Mechanism of Failure
1. In [AppNavigationDrawer.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawer.kt#L364-L377), `DrawerItemView` retrieves the icon drawable using:
   ```kotlin
   val context = LocalContext.current
   val drawable = remember(item.iconRes) {
       ContextCompat.getDrawable(context, item.iconRes)
   }
   if (drawable != null) {
       Image(
           painter = rememberDrawablePainter(drawable),
           contentDescription = null,
           modifier = Modifier.size(24.dp),
           colorFilter = if (item.tintIcon) ColorFilter.tint(contentColor) else null
       )
   } else {
       Spacer(modifier = Modifier.size(24.dp))
   }
   ```
2. **Missing Exception Handling**: Unlike `DrawerHeader` in the same file (which explicitly wraps `ContextCompat.getDrawable(context, R.drawable.menu_header_background)` in a `try-catch` block), `DrawerItemView` invokes `ContextCompat.getDrawable(context, item.iconRes)` directly.
3. **Behavior of `ContextCompat.getDrawable`**: `ContextCompat.getDrawable` does not return `null` when a resource fails density resolution or cannot be loaded—instead, the underlying Android framework `ResourcesImpl.getValueForDensity` throws a runtime `Resources.NotFoundException`.
4. **Dead Fallback Branch**: While the author implemented a fallback layout branch (`else { Spacer(modifier = Modifier.size(24.dp)) }`) assuming `drawable` could be `null`, that branch was unreachable in resource failure scenarios because the uncaught exception immediately crashed the process.
5. **Density Asset Packaging Fragility**: `logo_square_strava` (and `dropbox_logo_blue`) are raster PNG assets placed only inside density-qualified directories (`drawable-ldpi`, `drawable-mdpi`, `drawable-hdpi`, `drawable-xhdpi`, `drawable-xxhdpi`, `drawable-xxxhdpi`) without a root fallback inside `res/drawable/`. On devices running custom screen densities, split APK configurations (Google Play Dynamic Delivery), or runtime display size scaling, density resolution can trigger `Resources$NotFoundException`.

---

## 3. User Scope Grounding (ATT-1250)

### In-Scope Goals
* **Defensive Resource Resolution**: Wrap `ContextCompat.getDrawable(context, item.iconRes)` in `DrawerItemView` with defensive exception handling (`Resources.NotFoundException` and general `Throwable`), falling back to `null` so the existing 24dp `Spacer` placeholder renders gracefully.
* **Density Fallback Protection**: Provide a default base drawable in `res/drawable/` for `logo_square_strava` (and `dropbox_logo_blue`) so that Android resource resolution always finds a valid asset regardless of screen density splits or device DPI scaling.
* **Traceability & Specification**:
  * Update `REQ-UI-123` in `docs/requirements.md` with explicit defensive resource resolution requirements and the 4-field Chesterton's Fence archaeology audit.
  * Define test specification `TST-UI-136` in `docs/tests.md`.
* **Automated Unit & Regression Tests**: Add automated unit tests in `AppNavigationDrawerTest.kt` verifying that `DrawerItemView` handles non-existent or invalid resource IDs without throwing an exception.

### Out-of-Scope Non-Goals (Scope Bounding)
* Modifying drawer navigation routing logic or `NavRoutes` mappings.
* Altering drawer item heights (40dp density standard from ATT-243), touch targets, or typography.
* Modifying brand colors or monochrome icon tinting mechanics established in ATT-1547.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-UI-123` (*Declarative Compose Navigation Drawer, High-Density Presentation & Theme-Aware Contrast Tinting*), targeting `AppNavigationDrawer.kt` and `MainActivityWithNavigation.kt`.
* **Historical Origin & Commit Trace**: Introduced in `ATT-243` / `ATT-516` (commit `69cf7240`) to replace legacy Android View `NavigationView` with a pure Jetpack Compose drawer that eliminated reflection-based `InflateException` crashes and compressed vertical row spacing. Theme-aware tinting was added in `ATT-1547` (commit `4d528cb8`).
* **Root Reason for Existing Formulation**: The original architectural focus was eliminating View inflation crashes, ensuring touch target ergonomics, and managing dynamic reactive tracking state (`startTrackingTitleRes`). The row text color was assigned dynamically (`contentColor`), and the author provided an `if (drawable != null)` branch, expecting `ContextCompat.getDrawable` to return `null` on failure. The possibility of `Resources.NotFoundException` during density resolution was overlooked.
* **Preservation of Core Invariants**: Compact layout density (~40dp height), item touch targets, selected background highlight (`color_primary` with 0.12 alpha), reactive tracking status state, back navigation hierarchy, theme-aware contrast tinting, and partner brand logos (`logo_square_strava`, `dropbox_logo_blue` retaining authentic brand colors) remain 100% strictly intact.

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 Defensive Helper Resolution in `DrawerItemView`
Update the icon loading block in `DrawerItemView`:
```kotlin
val context = LocalContext.current
val drawable = remember(item.iconRes, context) {
    try {
        if (item.iconRes != 0) {
            ContextCompat.getDrawable(context, item.iconRes)
        } else {
            null
        }
    } catch (e: Resources.NotFoundException) {
        Log.w("AppNavigationDrawer", "Resource not found for drawer icon 0x${Integer.toHexString(item.iconRes)}", e)
        null
    } catch (e: Throwable) {
        Log.w("AppNavigationDrawer", "Failed to load drawer icon 0x${Integer.toHexString(item.iconRes)}", e)
        null
    }
}
```
When `drawable` is `null`, `DrawerItemView` smoothly executes its existing fallback:
```kotlin
if (drawable != null) {
    Image(
        painter = rememberDrawablePainter(drawable),
        contentDescription = null,
        modifier = Modifier.size(24.dp),
        colorFilter = if (item.tintIcon) ColorFilter.tint(contentColor) else null
    )
} else {
    Spacer(modifier = Modifier.size(24.dp))
}
```
This guarantees that drawer items remain clickable, accessible, properly aligned, and completely crash-free even if an asset is missing or corrupted.

### 5.2 Base Drawable Fallback Asset
Place a default version of `logo_square_strava.png` and `dropbox_logo_blue.png` in `app/src/main/res/drawable/` so that density lookups always have an unqualified fallback.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Drawer navigation items, IDs, text labels, and click dispatch MUST NOT be altered.
  2. Authentic brand logo colors for Strava and Dropbox (`tintIcon = false`) MUST be preserved.
  3. App cold start and main navigation drawer opening MUST NEVER crash on drawable resolution failure.
  4. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW**
  - Justification: The change introduces isolated defensive exception handling and asset fallback redundancy for icon loading in a Compose row. It does not alter database schemas, navigation routing, background services, or business logic.
