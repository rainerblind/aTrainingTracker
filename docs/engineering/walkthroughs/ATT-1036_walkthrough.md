# Walkthrough - ATT-1036: Navigation Drawer Header Background Crash Immunity

## Problem Overview
In production release `4.9.36 (260)`, a fatal crash was recorded in Firebase Crashlytics:
```
Fatal Exception: android.content.res.Resources$NotFoundException: Resource ID #0x7f080179
       at android.content.res.ResourcesImpl.getValue(ResourcesImpl.java:225)
       at androidx.compose.ui.res.ResourceIdCache.resolveResourcePath(Resources.android.kt:38)
       at androidx.compose.ui.res.PainterResources_androidKt.painterResource(PainterResources.android.kt:62)
       at com.atrainingtracker.trainingtracker.ui.navigation.AppNavigationDrawerKt.DrawerHeader(AppNavigationDrawer.kt:218)
```
Resource ID `0x7f080179` maps directly to `R.drawable.menu_header_background`.

### Root Cause
`menu_header_background.jpg` was provided exclusively in density-specific folders (`drawable-mdpi`, `drawable-hdpi`, `drawable-xhdpi`, `drawable-xxhdpi`, `drawable-xxxhdpi`), with no default fallback in `drawable/` or `drawable-nodpi/`.
When packaged as an Android App Bundle (.aab), Google Play's `bundletool` splits density resources into separate configuration APKs. When a device operates on an unprovided or non-standard density (e.g. `ldpi`, `tvdpi`, or custom user display zoom/scaling), the framework falls back to the base APK. Because no fallback existed in `drawable/` or `drawable-nodpi/`, Android threw `Resources$NotFoundException`, causing an instant fatal crash.

---

## Changes Implemented

### 1. Universal Fallback in Base APK
* Added `menu_header_background.jpg` into `app/src/main/res/drawable-nodpi/`.
* Being in `drawable-nodpi/`, this asset is packaged directly into the **base APK** and is never stripped by `bundletool` density splitting, providing an unconditional fallback across all devices and display scaling configurations.

### 2. Defensive Resource Resolution & Fallback Rendering
* In `AppNavigationDrawer.kt`:
  * Loaded the drawable safely using `ContextCompat.getDrawable(context, R.drawable.menu_header_background)` wrapped in a `try ... catch (e: Throwable)` block within `remember(context)`.
  * Rendered via `rememberDrawablePainter(drawable = headerDrawable)`.
  * If drawable loading fails for any reason, it logs a warning with `Log.w(TAG, ...)` and falls back to a styled background `Box(MaterialTheme.colorScheme.surfaceVariant)` rather than crashing the application process.

### 3. Automated Unit Testing
* Created `DrawerHeaderResourceTest.kt` verifying:
  * `R.drawable.menu_header_background` ID is valid (> 0).
  * `menu_header_background.jpg` exists in `drawable-nodpi/` with non-zero file size.
  * Density buckets all retain `menu_header_background.jpg`.
  * Both `logo_512.png` and `menu_header_background.jpg` exist in `drawable-nodpi/` to safeguard App Bundle packaging.

---

## Verification Results

### Automated Unit Tests
```bash
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.navigation.DrawerHeaderResourceTest"
```
**Result**: `BUILD SUCCESSFUL` (100% tests passed).

### Live Device Verification on Google Pixel 10 (Android 17 / API 37)
Debug APK deployed to physical device `66020DLCR002FL`. The navigation drawer opened cleanly and the landscape photo header rendered crisp and edge-to-edge behind the transparent status bar.
