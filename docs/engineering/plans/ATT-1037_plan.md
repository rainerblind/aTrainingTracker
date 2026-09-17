# Implementation Plan - ATT-1037: AndroidX Core Compatibility & Android 14 Insets Crash Immunity

## 1. Problem & Context
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
On specific Android 14 (API 34) device builds / OEM implementations, `WindowInsets.Type.systemOverlays()` is absent from the platform framework `framework.jar`. Because `androidx.core` (versions 1.16.0–1.19.0) unconditionally invokes this method whenever `SDK_INT == 34`, any Compose view using insets padding encounters a fatal `NoSuchMethodError` upon attaching to the window.

---

## 2. Proposed Changes

### Build Configuration Layer
#### [MODIFY] [app/build.gradle](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/build.gradle)
1. Update `androidx.core:core-ktx` dependency declaration from `1.19.0` to `1.15.0`.
2. Add explicit explanatory documentation referencing upstream Google Issue Tracker (Issue 558456603 / 551348854) and tracking ticket `ATT-1091`.
3. Add a Gradle `resolutionStrategy` rule under `configurations.all` to force both `androidx.core:core:1.15.0` and `androidx.core:core-ktx:1.15.0`, preventing transitive libraries (e.g. `activity:1.13.0`, `material:1.14.0`) from resolving to defective `1.16.0–1.19.0` versions.

### Verification Layer
#### [NEW] [app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/CoreDependencyAlignmentTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/CoreDependencyAlignmentTest.kt)
1. Add automated test verifying `WindowInsetsCompat` and `ViewCompat` classes are loaded from `androidx.core:1.15.0`.
2. Verify insets type constants and compatibility methods resolve cleanly without throwing `NoSuchMethodError`.

---

## 3. Impact Analysis & Risk Assessment
* **Regression Risk**: Very Low. Version `1.15.0` is a mature, production-proven stable release of AndroidX Core.
* **Compose Compatibility**: Compose BOM `2026.08.00`, Material 3, and navigation components are fully backward compatible with `core:1.15.0`.
* **Sister Defect Immunity**: Aligning to `1.15.0` simultaneously eliminates sister production crash `ATT-1038` (`AccessibilityEventCompat$Api34Impl.setAccessibilityDataSensitive`).
* **Preserved Behavior**: Transparent status bars, edge-to-edge drawing, navigation bars padding, and existing touch handling remain 100% intact.

---

## 4. Verification Plan

### Automated Verification
1. Run dependency insight to verify runtime classpath:
   ```bash
   ./gradlew :app:dependencyInsight --configuration debugRuntimeClasspath --dependency androidx.core:core
   ./gradlew :app:dependencyInsight --configuration debugRuntimeClasspath --dependency androidx.core:core-ktx
   ```
   Both must resolve to `1.15.0`.
2. Run targeted test:
   ```bash
   ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.navigation.CoreDependencyAlignmentTest"
   ```
3. Run full clean-room unit regression test suite:
   ```bash
   ./gradlew testDebugUnitTest
   ```
   All 420+ unit tests must pass with 0 failures and 0 skips.

### Physical Device Verification
1. Install debug APK on Google Pixel 10 (Android 17 / API 37):
   ```bash
   ./gradlew installDebug
   ```
2. Verify app launch, navigation drawer, and bottom sheet popups render smoothly without crashes.
