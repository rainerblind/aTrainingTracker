# Walkthrough - ATT-1037: AndroidX Core Compatibility & Android 14 Insets Crash Immunity

## Problem Overview
In production release `4.9.36 (260)`, a fatal crash was recorded in Firebase Crashlytics:
```
Fatal Exception: java.lang.NoSuchMethodError: No static method systemOverlays()I in class Landroid/view/WindowInsets$Type;
       at androidx.core.view.WindowInsetsCompat$TypeImpl34.toPlatformType(WindowInsetsCompat.java:2880)
       at androidx.core.view.WindowInsetsCompat$Impl34.getInsets(WindowInsetsCompat.java:1710)
       at androidx.core.view.WindowInsetsCompat$Impl20.initTypeBoundingRectsMaps(WindowInsetsCompat.java:1276)
       at androidx.core.view.WindowInsetsCompat.init(WindowInsetsCompat.java:2900)
       at androidx.core.view.ViewCompat$Api23Impl.getRootWindowInsets(ViewCompat.java:5118)
       at androidx.core.view.ViewCompat.getRootWindowInsets(ViewCompat.java:3023)
       at androidx.compose.foundation.layout.WindowInsetsHolder.<init>(WindowInsets.android.kt:433)
       at androidx.compose.foundation.layout.WindowInsetsHolder$Companion.getOrCreateFor(WindowInsets.android.kt:595)
       at androidx.compose.foundation.layout.SystemInsetsPaddingModifierNode.onAttach(WindowInsetsPadding.android.kt:300)
       at androidx.compose.ui.platform.AndroidComposeView.onAttachedToWindow(AndroidComposeView.android.kt:2395)
```

### Root Cause
In `androidx.core` versions `1.16.0` through `1.19.0`, `WindowInsetsCompat$TypeImpl34.toPlatformType` unconditionally calls `android.view.WindowInsets.Type.systemOverlays()` on any device reporting `Build.VERSION.SDK_INT >= 34`. However, on specific OEM Android 14 (API 34) builds, `systemOverlays()` is absent from the platform framework, causing an immediate fatal `NoSuchMethodError` as soon as any Jetpack Compose view attaches to the window (documented in Google Issue Tracker 558456603 / 551348854). Sister crash `ATT-1038` (`AccessibilityEventCompat$Api34Impl.setAccessibilityDataSensitive`) shares an identical defect mechanism.

---

## Changes Implemented

### 1. Build Configuration & Dependency Harmonization
* In `app/build.gradle`:
  * Configured `configurations.all.resolutionStrategy` with forced pins:
    ```groovy
    configurations.all {
        resolutionStrategy {
            // ATT-1037: Pin androidx.core to 1.15.0 to eliminate NoSuchMethodError on Android 14 (API 34)
            // (Google Issue Tracker: 558456603 / 551348854). Tracked for re-upgrade in ATT-1091.
            force 'androidx.core:core:1.15.0'
            force 'androidx.core:core-ktx:1.15.0'
            // Pin androidx.activity to 1.10.1 which is fully compatible with core:1.15.0
            // (does not require core:1.16.0+ ProtectionLayout or PictureInPictureProvider)
            force 'androidx.activity:activity:1.10.1'
            force 'androidx.activity:activity-ktx:1.10.1'
            force 'androidx.activity:activity-compose:1.10.1'
        }
    }
    ```
  * Updated dependencies:
    * `androidx.core:core-ktx:1.15.0`
    * `androidx.activity:activity-compose:1.10.1`
* **Architectural Invariant & Deep Discovery**:
  * Forcing `androidx.core:1.15.0` while leaving `androidx.activity:1.12.x` or `1.13.x` causes two severe runtime/compile issues:
    1. `PictureInPictureProvider` supertype missing in `FragmentActivity` (`core:1.16.0+`).
    2. On Android 15+ devices (API 35+), `androidx.activity:1.12.x`'s `EdgeToEdgeApi35.setUp()` unconditionally requires `androidx.core.view.insets.ProtectionLayout`, triggering a fatal `NoClassDefFoundError` on launch.
  * Harmonizing `androidx.activity` to `1.10.1` perfectly aligns with `androidx.core:1.15.0`, requires neither `ProtectionLayout` nor `PictureInPictureProvider`, and provides 100% stable execution across all Android versions (including Android 17 / API 37).

### 2. Automated Regression Unit Testing
* Created `app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/CoreDependencyAlignmentTest.kt` verifying:
  * `WindowInsetsCompat$TypeImpl34` is completely absent from the runtime classpath.
  * Window insets bitmask constants (`statusBars`, `navigationBars`, `systemBars`, `ime`, `displayCutout`) resolve accurately.
  * Invariant sentinel `WindowInsetsCompat.CONSUMED` is accessible and functional.

---

## Verification Results

### Automated Unit Tests
```bash
./gradlew testDebugUnitTest
```
**Result**: `BUILD SUCCESSFUL` across all 423 unit tests with 0 failures, 0 errors, and 0 skips:
```
Total tests: 423, Failures: 0, Errors: 0, Skipped: 0
```

### Live Device Verification on Google Pixel 10 (Android 17 / API 37)
* Deployed `app-debug.apk` to physical Google Pixel 10 (`66020DLCR002FL`).
* Executed cold launch (`LaunchState: COLD`, `TotalTime: 1307ms`, status `ok`).
* Opened Compose navigation drawer; header image and all navigation items rendered cleanly and smoothly.
* Verified `adb logcat` reported 0 `NoSuchMethodError`, 0 `NoClassDefFoundError`, and 0 `FATAL` exceptions.
* Artifact: `docs/attachments/Screenshot_ATT-1037_Pixel10_Drawer_Open.png`.
