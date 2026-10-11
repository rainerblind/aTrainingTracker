# Stage 1 Analysis: ATT-3163 - Prevent NoClassDefFoundError SelectGesture crash in EditorInfoApi34 setHandwritingGestures

**Ticket**: [ATT-3163](https://atrainingtracker.atlassian.net/browse/ATT-3163)  
**Sub-task**: [ATT-3190](https://atrainingtracker.atlassian.net/browse/ATT-3190) (`[Analysis]`)  
**Parent Epic**: [ATT-235](https://atrainingtracker.atlassian.net/browse/ATT-235) (*No crashs*)  
**Target Release**: `V4.9.38.5` (Hotfix)  
**Active Sprint**: `2026-41.9`  
**Branch**: `feature/ATT-3163` (integrating to `sprint/2026-41.9` and `hotfix/V4.9.38.5__269`)  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-11  

---

## 1. Problem Statement & Motivation

During production execution of release `v4.9.38.4` (build 267), Firebase Crashlytics captured fatal crash [ATT-3163](https://atrainingtracker.atlassian.net/browse/ATT-3163) (`Issue ecebf5473abc8cd7d33c06754e9349f1`, session `6ACADA5100F600011E16FE0202C224A4_DNE_0_v2`):

```text
Fatal Exception: java.lang.NoClassDefFoundError: Failed resolution of: Landroid/view/inputmethod/SelectGesture;
       at androidx.compose.foundation.text.input.internal.EditorInfoApi34.setHandwritingGestures(EditorInfo.android.kt:230)
       at androidx.compose.foundation.text.input.internal.EditorInfo_androidKt.update-pLxbY9I(EditorInfo.android.kt:185)
       at androidx.compose.foundation.text.input.internal.EditorInfo_androidKt.update-pLxbY9I$default(EditorInfo.android.kt:40)
       at androidx.compose.foundation.text.input.internal.LegacyTextInputMethodRequest.createInputConnection(LegacyPlatformTextInputServiceAdapter.android.kt:265)
       at androidx.compose.foundation.text.input.internal.LegacyTextInputMethodRequest.createInputConnection(LegacyPlatformTextInputServiceAdapter.android.kt:202)
       at androidx.compose.ui.platform.InputMethodSession.createInputConnection(AndroidPlatformTextInputSession.android.kt:142)
       at androidx.compose.ui.platform.AndroidPlatformTextInputSession.createInputConnection(AndroidPlatformTextInputSession.android.kt:105)
       at androidx.compose.ui.platform.AndroidComposeView.onCreateInputConnection(AndroidComposeView.android.kt:3071)
       at android.view.inputmethod.InputMethodManager.startInputInner(InputMethodManager.java:2308)
       ...
Caused by java.lang.ClassNotFoundException: android.view.inputmethod.SelectGesture
       at androidx.compose.foundation.text.input.internal.EditorInfoApi34.setHandwritingGestures(EditorInfo.android.kt:230)
       ...
```

Whenever an athlete touches or focuses any non-password text input field in Jetpack Compose (such as route searching, segment filtering, sensor renaming, workout notes, or cockpit layout configurations), Android's `InputMethodManager` requests the input connection from the hosting `AndroidComposeView`.

During input connection creation, Compose attempts to configure stylus handwriting gestures introduced in Android 14 (API 34). On devices where `Build.VERSION.SDK_INT >= 34` but the platform framework lacks the `SelectGesture` class, runtime linkage throws an unhandled `NoClassDefFoundError`, terminating the application immediately.

---

## 2. Forensic Root Cause Analysis (RCA)

### 2.1 The Compose Input Connection Flow
Disassembly and inspection of `androidx.compose.foundation:foundation` bytecode confirms the exact execution sequence:
1. Focus is assigned to a text input field in Compose.
2. `AndroidComposeView.onCreateInputConnection(outAttrs: EditorInfo)` is invoked by the platform `InputMethodManager`.
3. `AndroidComposeView` delegates to `EditorInfo_androidKt.update(editorInfo, ...)`.
4. `EditorInfo_androidKt` evaluates:
   ```kotlin
   if (shouldEnableStylusHandwriting(editorInfo, imeOptions)) {
       EditorInfoCompat.setStylusHandwritingEnabled(editorInfo, true)
       EditorInfoApi34.INSTANCE.setHandwritingGestures(editorInfo)
   }
   ```
5. `shouldEnableStylusHandwriting` delegates to `StylusHandwriting_androidKt.isStylusHandwritingSupported()`, which evaluates strictly to `Build.VERSION.SDK_INT >= 34`.
6. When `isStylusHandwritingSupported()` is true and the input field is not a password field, Compose calls `EditorInfoApi34.INSTANCE.setHandwritingGestures(editorInfo)`.

### 2.2 Direct Class Reference in `EditorInfoApi34` Bytecode
In `EditorInfoApi34.setHandwritingGestures`:
```java
public final void setHandwritingGestures(EditorInfo editorInfo) {
    editorInfo.setSupportedHandwritingGestures(
        CollectionsKt.listOf(new Class[] {
            SelectGesture.class,
            DeleteGesture.class,
            SelectRangeGesture.class,
            DeleteRangeGesture.class,
            JoinOrSplitGesture.class,
            InsertGesture.class,
            RemoveSpaceGesture.class
        })
    );
    editorInfo.setSupportedHandwritingGesturePreviews(
        SetsKt.setOf(new Class[] {
            SelectGesture.class,
            DeleteGesture.class,
            SelectRangeGesture.class,
            DeleteRangeGesture.class
        })
    );
}
```
At the bytecode level, referencing `SelectGesture.class` generates instruction `ldc class android/view/inputmethod/SelectGesture`.

### 2.3 Android OEM Fragmentation & ClassLoader Failure
`android.view.inputmethod.SelectGesture` was added to Android Open Source Project in Android 14 (API 34) for stylus handwriting. However, on certain devices:
- Budget OEM Android 14 builds stripped of stylus services or handwriting gestures.
- Custom ROMs (LineageOS, AOSP forks) built against non-standard or stripped framework jars.
- Android 14 Preview/Developer builds where API 34 SDK_INT was reported before final API stabilization.

The ART boot classloader fails to find `android.view.inputmethod.SelectGesture` in `/system/framework/framework.jar`. Because `androidx.compose.foundation` contains zero defensive `try/catch (t: Throwable)` guards around `EditorInfoApi34.setHandwritingGestures`, `ClassNotFoundException` escalates into `NoClassDefFoundError`, crashing the entire application process.

---

## 3. Chesterton's Fence & Impact Analysis

1. **Why was this code introduced?**
   Google introduced `EditorInfoApi34` in Compose Foundation 1.7+ to support native stylus handwriting gestures on tablets and foldable devices.
2. **Why does it fail on production devices?**
   Google assumed that every device reporting `Build.VERSION.SDK_INT >= 34` contains the full stylus handwriting framework. In the wild, Android device fragmentation breaks this assumption when OEMs omit `SelectGesture` from `framework.jar`.
3. **Auditor Review & Rejection of Package Stubbing (Gate 1 Audit Feedback)**:
   An initial consideration of bundling fallback stub classes in the `android.view.inputmethod` namespace was rightly rejected by Auditor Agent 2 during Gate 1 audit: defining classes within the protected platform `android.*` namespace in an application DEX violates Android runtime packaging rules, creates classloader collision risks on standard devices, and risks `SecurityException` or DEX verification rejection.
4. **What invariants must be preserved?**
   - No application classes placed inside reserved platform namespaces (`android.*`).
   - Text input across all 50+ dialogs, sheets, and search bars in the app must continue working seamlessly.
   - Text selection, typing, cut/copy/paste, and IME virtual keyboards must operate with 0 latency or regressions.
   - Normal devices with stylus support retain full functionality, while fragmented devices are safeguarded.
   - Full clean-room test suite must pass with 100% success.

---

## 4. Proposed Solution & Architecture

We implement a two-pillar defense-in-depth architectural solution:

### Pillar 1: Dynamic Runtime Guard (`ComposeHandwritingResilience`)
Instead of package hijacking, we intercept the condition that causes Compose to invoke `EditorInfoApi34.setHandwritingGestures`.

**Mechanism**:
1. In `EditorInfo.android.kt`, Compose evaluates:
   ```kotlin
   if (shouldEnableStylusHandwriting(imeOptions)) {
       EditorInfoCompat.setStylusHandwritingEnabled(this, true)
       EditorInfoApi34.setHandwritingGestures(this)
   } else {
       EditorInfoCompat.setStylusHandwritingEnabled(this, false)
   }
   ```
2. `shouldEnableStylusHandwriting` delegates to `StylusHandwriting_androidKt.isStylusHandwritingSupported()`.
3. At application startup (`TrainingApplication.onCreate()`), `ComposeHandwritingResilience.install()` executes:
   - On Android < 34 (`Build.VERSION.SDK_INT < 34`), Compose already sets `isStylusHandwritingSupported` to `false`.
   - On Android >= 34, it attempts a safe probe:
     ```kotlin
     val isAvailable = try {
         Class.forName("android.view.inputmethod.SelectGesture")
         true
     } catch (t: Throwable) {
         false
     }
     ```
   - If `SelectGesture` is missing or fails resolution, `ComposeHandwritingResilience` updates `isStylusHandwritingSupported` to `false` via reflection/Unsafe.
   - When any text field receives focus, `shouldEnableStylusHandwriting` returns `false`, gracefully disabling stylus gestures and completely avoiding the call to `EditorInfoApi34.setHandwritingGestures`.
   - Standard IME typing, copy/paste, and software keyboards operate normally without exception.

### Pillar 2: Compose BOM Alignment (`app/build.gradle`)
- Upgrade Compose BOM from `2026.08.00` to `2026.09.00` (latest stable).
- Remove hardcoded `:1.12.0` version pins on `ui`, `runtime-saveable`, and `foundation-layout`, allowing the BOM to orchestrate full version harmony.

---

## 5. Verification Plan

1. **Unit Test (`ComposeHandwritingResilienceTest.kt`)**:
   - Verify `ComposeHandwritingResilience.install()` safely probes class availability without crashing.
   - Verify that when `SelectGesture` is missing, `isStylusHandwritingSupported()` is safely disabled.
   - Verify idempotency and error tolerance (graceful fallback if reflection fails).
2. **Clean-Room Regression**:
   - Run `./gradlew testDebugUnitTest` asserting 100% pass rate.
3. **Physical / Emulator Device Verification**:
   - Assert zero crashes on text field focus.
