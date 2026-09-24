# Root Cause Analysis - ATT-1347: NoSuchMethodError in AccessibilityEventCompat$Api34Impl.setAccessibilityDataSensitive

## 1. Defect Overview & Crash Telemetry

* **Issue Key**: ATT-1347
* **Requirement Mapping**: `REQ-UI-164` (Accessibility Event Dispatch Compatibility & Defective Platform Resilience) in `docs/requirements.md`
* **Firebase Crashlytics Issue ID**: `ecdb3c221473dea4b125a2b3eba81cfa`
* **Session Event Key**: `6AB554C9001200011DDADC9806694924_DNE_0_v2`
* **Target Version**: `V4.9.38` (Sprint `2026-39.2`)
* **Affected Production Release**: `V4.9.37 (261)`
* **Historical Trace**: Previously tracked under `ATT-1038` and prematurely closed under the false assumption that downgrading to `androidx.core:1.15.0` in `ATT-1037` eliminated the crash.

### Crash Stacktrace
```text
Fatal Exception: java.lang.NoSuchMethodError: No virtual method setAccessibilityDataSensitive(Z)V in class Landroid/view/accessibility/AccessibilityEvent; or its super classes (declaration of 'android.view.accessibility.AccessibilityEvent' appears in /system/framework/framework.jar!classes3.dex)
       at androidx.core.view.accessibility.AccessibilityEventCompat$Api34Impl.setAccessibilityDataSensitive(AccessibilityEventCompat.java:590)
       at androidx.core.view.accessibility.AccessibilityEventCompat.setAccessibilityDataSensitive(AccessibilityEventCompat.java:574)
       at androidx.compose.ui.platform.AndroidComposeViewAccessibilityDelegateCompat.createEvent(AndroidComposeViewAccessibilityDelegateCompat.android.kt:1357)
       at androidx.compose.ui.platform.AndroidComposeViewAccessibilityDelegateCompat.sendEventForVirtualView(AndroidComposeViewAccessibilityDelegateCompat.android.kt:1296)
       at androidx.compose.ui.platform.AndroidComposeViewAccessibilityDelegateCompat.sendEventForVirtualView$default(AndroidComposeViewAccessibilityDelegateCompat.android.kt:1286)
       at androidx.compose.ui.platform.AndroidComposeViewAccessibilityDelegateCompat.sendSubtreeChangeAccessibilityEvents(AndroidComposeViewAccessibilityDelegateCompat.android.kt:2445)
       at androidx.compose.ui.platform.AndroidComposeViewAccessibilityDelegateCompat.boundsUpdatesEventLoop$ui(AndroidComposeViewAccessibilityDelegateCompat.android.kt:2321)
       at androidx.compose.ui.platform.AndroidComposeView.boundsUpdatesAccessibilityEventLoop(AndroidComposeView.android.kt:2331)
       at androidx.compose.ui.platform.WrappedComposition$setContent$1$2$1$1.invokeSuspend(Wrapper.android.kt:124)
       at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:34)
       at kotlinx.coroutines.DispatchedTask.run(DispatchedTask.kt:100)
       at androidx.compose.ui.platform.AndroidUiDispatcher.performTrampolineDispatch(AndroidUiDispatcher.android.kt:79)
       at androidx.compose.ui.platform.AndroidUiDispatcher.access$performTrampolineDispatch(AndroidUiDispatcher.android.kt:41)
       at androidx.compose.ui.platform.AndroidUiDispatcher$dispatchCallback$1.doFrame(AndroidUiDispatcher.android.kt:68)
       at android.view.Choreographer$CallbackRecord.run(Choreographer.java:1229)
       at android.view.Choreographer$CallbackRecord.run(Choreographer.java:1239)
       at android.view.Choreographer.doCallbacks(Choreographer.java:899)
       at android.view.Choreographer.doFrame(Choreographer.java:827)
       at android.view.Choreographer$FrameDisplayEventReceiver.run(Choreographer.java:1214)
       at android.os.Handler.handleCallback(Handler.java:942)
       at android.os.Handler.dispatchMessage(Handler.java:99)
       at android.os.Looper.loopOnce(Looper.java:201)
       at android.os.Looper.loop(Looper.java:288)
       at android.app.ActivityThread.main(ActivityThread.java:7872)
```

---

## 2. Forensic Root Cause Analysis (RCA)

### A. The Framework & AndroidX Discrepancy
1. **Android 14 (API 34) Feature Introduction**:
   Google introduced `setAccessibilityDataSensitive(boolean)` and `isAccessibilityDataSensitive()` in Android 14 to allow views and accessibility events to declare sensitive content, ensuring only trusted accessibility services (`isAccessibilityTool == true`) can inspect their payload.
2. **AndroidX Compatibility Wrapper**:
   In `androidx.core:core:1.15.0`, `AccessibilityEventCompat` was updated with API 34 compatibility methods:
   ```java
   public static void setAccessibilityDataSensitive(@NonNull AccessibilityEvent event,
           boolean accessibilityDataSensitive) {
       if (Build.VERSION.SDK_INT >= 34) {
           Api34Impl.setAccessibilityDataSensitive(event, accessibilityDataSensitive);
       }
   }

   @RequiresApi(34)
   static class Api34Impl {
       static void setAccessibilityDataSensitive(AccessibilityEvent event,
               boolean accessibilityDataSensitive) {
           event.setAccessibilityDataSensitive(accessibilityDataSensitive);
       }
   }
   ```
3. **The OEM / Platform Defect (Google Issue Tracker 555294634 / 560736851)**:
   On certain production Android 14 builds (specifically early Pixel Android 14 releases and particular OEM firmware), the system reports `Build.VERSION.SDK_INT == 34`, but the platform framework implementation (`framework.jar!classes3.dex`) **does not include** the virtual method `setAccessibilityDataSensitive(Z)V` on `android.view.accessibility.AccessibilityEvent`.
4. **Trigger Mechanism via Jetpack Compose**:
   When any Jetpack Compose screen is rendered, `AndroidComposeViewAccessibilityDelegateCompat` dispatches accessibility event updates in its bounds update loop (`boundsUpdatesEventLoop$ui`). In `createEvent()`, it calls:
   ```kotlin
   AccessibilityEventCompat.setAccessibilityDataSensitive(event, ...)
   ```
   Because `Build.VERSION.SDK_INT >= 34` evaluates to `true`, the code routes into `Api34Impl.setAccessibilityDataSensitive(event, ...)`. The Android Runtime fails to resolve the virtual method on the system `AccessibilityEvent` instance, throwing an immediate, fatal `java.lang.NoSuchMethodError`.
5. **Upstream Investigation (AOSP `androidx-main`)**:
   Verification against upstream AndroidX repository (`platform/frameworks/support/core/core/src/main/java/androidx/core/view/accessibility/AccessibilityEventCompat.java`) confirms that Google has not introduced defensive try-catch guards in `Api34Impl`. Upgrading `androidx.core` to current versions does not resolve the crash.

### B. Retrospective: Why ATT-1037 Did Not Prevent This Defect
* Under `ATT-1037`, `androidx.core` was pinned to `1.15.0` to resolve `WindowInsetsCompat$TypeImpl34.toPlatformType` calling `WindowInsets.Type.systemOverlays()`.
* Because `systemOverlays()` was introduced in `core:1.16.0`, pinning to `1.15.0` successfully eradicated the window insets crash.
* However, `ATT-1038` assumed that `core:1.15.0` had also excised `AccessibilityEventCompat$Api34Impl`. Inspection of `core-1.15.0.aar` proves that `AccessibilityEventCompat$Api34Impl` was present in `1.15.0`, meaning the accessibility vulnerability remained active in release `V4.9.37`.

---

## 3. Impact Analysis & Scope Boundary

* **Affected Component**: Jetpack Compose UI accessibility event pipeline (`AndroidComposeViewAccessibilityDelegateCompat`).
* **Trigger Conditions**: Active on devices running affected Android 14 builds whenever accessibility services, password managers, or screen readers are active.
* **Call Sites**:
  - `androidx.compose.ui.platform.AndroidComposeViewAccessibilityDelegateCompat.createEvent`
  - Any direct or indirect calls to `AccessibilityEventCompat.setAccessibilityDataSensitive` or `isAccessibilityDataSensitive`.
* **Collateral Systems**: Core workout tracking (`TrackerService`), BLE/ANT+ sensors, database storage, and GPS engines are completely untouched.

---

## 4. Remediation Strategy & Evaluation of Alternatives

### Evaluated Alternatives & Auditor Findings

1. **Alternative 1: Library Downgrade**:
   - Downgrading `androidx.core` below `1.15.0` (e.g. `1.13.1`):
   - **Verdict**: Infeasible. Downgrading breaks `androidx.activity:1.10.1`, `androidx.appcompat:1.8.0`, and Compose BOM compatibility.

2. **Alternative 2: Global Looper Interception (`Looper.loop()` `while(true)`)**:
   - Intercepting `NoSuchMethodError` globally on the main Looper:
   - **Auditor Verdict**: **REJECTED (Severe Hazard)**. Catching linkage errors globally in `Looper.loop()` does not recover Compose internal call-site state. Compose's coroutine `boundsUpdatesEventLoop$ui` will immediately retry the bounds update, re-triggering `createEvent()`, throwing `NoSuchMethodError` repeatedly, and locking the Main Looper into a 100% CPU infinite dispatch loop.

3. **Alternative 3: R8 Optimization Stripping (`-assumenosideeffects`)**:
   - Relying on R8 compiler optimization rules to excise calls to `setAccessibilityDataSensitive`:
   - **Auditor Verdict**: **REJECTED (Brittle & Incomplete)**. `-assumenosideeffects` is an optimization hint on void methods that does not guarantee stripping across all build variants. Crucially, non-release variants (Debug builds used by developers, local tests, Profile builds) run without minification, leaving developer devices 100% vulnerable to crashes on physical defective OEM hardware.

4. **Mandated Strategy: Direct Call-Site Classpath Shadowing with Defensive Try-Catch (SELECTED)**:
   - Provide `AccessibilityEventCompat.java` in package `androidx.core.view.accessibility` within `app/src/main/java`.
   - In `Api34Impl`, wrap platform invocations in targeted `try ... catch (Throwable t)` blocks:
     ```java
     @RequiresApi(34)
     static class Api34Impl {
         private Api34Impl() {
             // This class is not instantiable.
         }

         static boolean isAccessibilityDataSensitive(AccessibilityEvent event) {
             try {
                 return event.isAccessibilityDataSensitive();
             } catch (Throwable t) {
                 Log.w(TAG, "isAccessibilityDataSensitive missing on platform; suppressing error", t);
                 return false;
             }
         }

         static void setAccessibilityDataSensitive(AccessibilityEvent event,
                 boolean accessibilityDataSensitive) {
             try {
                 event.setAccessibilityDataSensitive(accessibilityDataSensitive);
             } catch (Throwable t) {
                 Log.w(TAG, "setAccessibilityDataSensitive missing on platform framework; suppressing error", t);
             }
         }
     }
     ```
   - **Technical Advantages & Compliance**:
     - **Direct Call-Site Guard**: Intercepts the missing virtual method directly at the invocation site before any exception can escape into Compose coroutines. `createEvent()` succeeds normally.
     - **Zero Looper Disruption & Zero CPU Lockup**: Because `createEvent()` completes cleanly, Compose's `boundsUpdatesEventLoop$ui` processes its frame and suspends normally without crashing or infinite retry looping.
     - **Build Variant Neutrality**: Provides identical 100% crash immunity across all build variants (Debug, Testing, Profile, Release) with zero reliance on compiler minification heuristics.
     - **Standard Classpath Precedence**: Application source classes in `app/src/main/java` take deterministic precedence over library AAR classes in D8/R8 compilation.
     - **Intact Platform Preservation**: On intact Android 14/15/16 devices, `event.setAccessibilityDataSensitive(...)` executes normally, preserving accessibility data privacy.

---

## 5. System Invariants & Preserved Behavior

1. **INV-ACC-01**: **Direct Call-Site Safety**: Exceptions from missing platform methods are caught immediately within `Api34Impl`, preventing any unhandled errors from escaping into Compose UI coroutines or the Main Looper.
2. **INV-ACC-02**: **Comprehensive Crash Immunity (`REQ-UI-164`)**: 100% crash immunity on defective Android 14 builds across all build configurations (Debug, Profile, Release).
3. **INV-ACC-03**: **Main Thread & Looper Stability**: Zero infinite dispatch loops, zero UI thread freezing, and zero CPU starvation.
4. **INV-ACC-04**: **API Contract & Intact Platform Parity**: On standard Android 14+ devices where the method exists, the platform API is invoked normally without behavioral divergence. Pre-API 34 compatibility is 100% preserved.

---

## 6. Risk Rating & Gate 1 Recommendation

* **Risk Level**: **LOW** (Targeted defensive try-catch at direct call site, 100% backward compatible, deterministic build-variant neutrality, prevents both crash and infinite-loop hazards).
* **Recommendation**: **RECOMMEND PASS**. Proceed to Stage 2: Test Specification & Requirements Synchronization.
