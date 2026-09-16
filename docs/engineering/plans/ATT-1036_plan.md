# Implementation Plan - ATT-1036: Fix Resources$NotFoundException in DrawerHeader

## Problem Statement & Context
In production release `4.9.36 (260)`, a fatal `android.content.res.Resources$NotFoundException: Resource ID #0x7f080179` occurred at `AppNavigationDrawerKt.DrawerHeader(AppNavigationDrawer.kt:218)`.
Resource ID `0x7f080179` maps directly to `R.drawable.menu_header_background`.

### Root Cause
`menu_header_background.jpg` was provided exclusively in density buckets (`drawable-mdpi`, `drawable-hdpi`, `drawable-xhdpi`, `drawable-xxhdpi`, `drawable-xxxhdpi`), with no default fallback in `drawable/` or `drawable-nodpi/`.
When packaged as an Android App Bundle (.aab), Google Play's `bundletool` splits density resources into separate configuration APKs. When a device operates on an unprovided or non-standard density (e.g. `ldpi`, `tvdpi`, or custom user display zoom/scaling), the framework falls back to the base APK. Because no fallback exists in `drawable/` or `drawable-nodpi/`, Android throws `Resources$NotFoundException`, causing an instant fatal crash. Furthermore, `DrawerHeader()` had zero defensive exception handling.

---

## Proposed Changes

### 1. Resource Layer: Universal Base APK Fallback
#### [NEW] [menu_header_background.jpg](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/drawable-nodpi/menu_header_background.jpg)
* Copy `menu_header_background.jpg` into `app/src/main/res/drawable-nodpi/`.
* Being in `drawable-nodpi/`, this image is permanently packaged directly into the **base APK** and is never stripped by `bundletool` density splitting, providing an unconditional fallback across all devices and custom display configurations.

---

### 2. UI Layer: Defensive Resource Resolution & Graceful Fallback
#### [MODIFY] [AppNavigationDrawer.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawer.kt)
* In `DrawerHeader()`:
  * Encapsulate `painterResource(id = R.drawable.menu_header_background)` in a `try ... catch (e: Throwable)` block.
  * If painter resolution fails, log a warning with `Log.w(TAG, "Failed to load menu_header_background", e)` and fall back to `null`.
  * If painter is non-null, render the background `Image`.
  * If painter is null, render a fallback styled background `Box` with `MaterialTheme.colorScheme.surfaceVariant`.
  * This guarantees 100% crash immunity in `DrawerHeader()`.

---

### 3. Test Layer: Automated Verification
#### [NEW] [DrawerHeaderResourceTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/DrawerHeaderResourceTest.kt)
* Verify file presence of `app/src/main/res/drawable-nodpi/menu_header_background.jpg`.
* Verify `R.drawable.menu_header_background` resolves to a valid non-zero resource ID.
* Verify that defensive fallback logic in `DrawerHeader` handles missing/failing resources without throwing.

---

## Invariants Maintained
1. Navigation drawer item layout, ordering, IDs, and touch targets (`REQ-SET-050`, `REQ-UI-123`) remain untouched.
2. Branding text color (`colorResource(R.color.my_blue)`), font size, and `R.drawable.logo_512` branding remain intact.
3. Status bar inset handling in drawer header remains intact.

---

## Verification Plan
### Automated Tests
* Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.navigation.DrawerHeaderResourceTest"`
* Execute full clean-room unit test suite: `./gradlew testDebugUnitTest`
### Manual / Device Verification
* Deploy debug build to Google Pixel 10: `./gradlew installDebug`.
* Open navigation drawer and confirm header image displays crisp and clear.
* Change display size / font scale in system settings and confirm drawer opens without crash.
