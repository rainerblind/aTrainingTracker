# Stage 1 Analysis: ATT-2767 - NoSuchFieldError in AccessibilityNodeInfoCompat$Api34Impl.getActionScrollInDirection

**Ticket**: [ATT-2767](https://atrainingtracker.atlassian.net/browse/ATT-2767)  
**Sub-task**: [ATT-2775](https://atrainingtracker.atlassian.net/browse/ATT-2775) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38.4`  
**Active Sprint**: `2026-41.4`  
**Branch**: `bugfix/ATT-2767`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-08  

---

## 1. Problem Statement & Motivation

A fatal production crash was captured via Firebase Crashlytics on Android 14 (API 34) devices:
```text
Fatal Exception: java.lang.NoSuchFieldError: No static field ACTION_SCROLL_IN_DIRECTION of type Landroid/view/accessibility/AccessibilityNodeInfo$AccessibilityAction; in class Landroid/view/accessibility/AccessibilityNodeInfo$AccessibilityAction;
    at androidx.core.view.accessibility.AccessibilityNodeInfoCompat$Api34Impl.getActionScrollInDirection(AccessibilityNodeInfoCompat.java)
    at androidx.core.view.accessibility.AccessibilityNodeInfoCompat$AccessibilityActionCompat.<clinit>(AccessibilityNodeInfoCompat.java:547)
```
*Firebase Crashlytics Issue*: [b293541c1c5c700d1d2fed8812655aea](https://console.firebase.google.com/project/atrainingtracker-bb57c/crashlytics/app/android:com.atrainingtracker/issues/b293541c1c5c700d1d2fed8812655aea)

### Observed Impact
The crash occurs during static class initialization of `AccessibilityNodeInfoCompat$AccessibilityActionCompat`. Because `AccessibilityActionCompat` defines static instances used by AndroidX View and Compose accessibility (e.g., `ACTION_CLICK`, `ACTION_SCROLL_FORWARD`, etc.), failure during `<clinit>` results in `ExceptionInInitializerError` followed by `NoClassDefFoundError` upon the very first touch of Compose or Android View accessibility on affected devices, instantly crashing the app upon launch or activity setup.

---

## 2. Root Cause Analysis (Forensic Investigation)

### A. Lineage of Defective Android 14 OEM ROMs
This defect follows the lineage of earlier production issues captured on non-compliant Android 14 OEM firmware:
1. **ATT-1347**: `NoSuchMethodError` on `AccessibilityEvent.setAccessibilityDataSensitive(Z)V`.
2. **ATT-2302**: `NoSuchMethodError` on `AccessibilityNodeInfo.setAccessibilityDataSensitive(Z)V`.
3. **ATT-2584**: `NoSuchMethodError` on `AccessibilityManager.isRequestFromAccessibilityTool()Z`.

Certain Android 14 OEM builds (e.g. Transsion, Infinix, Tecno, early Xiaomi ROMs, and non-certified emulator images) report `Build.VERSION.SDK_INT == 34`, but their system `framework.jar` was compiled from an incomplete or preview branch where standard Android 14 platform APIs were omitted.

### B. Static Class Initialization in `AccessibilityNodeInfoCompat`
In `androidx.core:core:1.15.0`:
```java
// Inside AccessibilityNodeInfoCompat.java:
public static class AccessibilityActionCompat {
    // ...
    public static final AccessibilityActionCompat ACTION_SCROLL_IN_DIRECTION =
            new AccessibilityActionCompat(
                    Build.VERSION.SDK_INT >= 34
                            ? AccessibilityNodeInfoCompat.Api34Impl.getActionScrollInDirection()
                            : null,
                    android.R.id.accessibilityActionScrollInDirection,
                    null, null, null);
    // ...
}
```
And inside `AccessibilityNodeInfoCompat$Api34Impl`:
```java
public static AccessibilityNodeInfo.AccessibilityAction getActionScrollInDirection() {
    return AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_IN_DIRECTION; // UNGUARDED!
}
```
When `Build.VERSION.SDK_INT >= 34`, `Api34Impl.getActionScrollInDirection()` is called unconditionally during class loading. When the underlying platform field `AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_IN_DIRECTION` is missing, ART throws `java.lang.NoSuchFieldError` (which extends `java.lang.IncompatibleClassChangeError` and `java.lang.LinkageError`). Because this is unhandled, it bubbles out of `Api34Impl` and aborts `<clinit>`, permanently disabling `AccessibilityActionCompat`.

### C. Forensic Bytecode Inspection of `core-1.15.0-patched.aar`
Disassembly of `classes.jar` reveals:
```text
public static android.view.accessibility.AccessibilityNodeInfo$AccessibilityAction getActionScrollInDirection();
  Code:
     0: getstatic     #76 // Field android/view/accessibility/AccessibilityNodeInfo$AccessibilityAction.ACTION_SCROLL_IN_DIRECTION:Landroid/view/accessibility/AccessibilityNodeInfo$AccessibilityAction;
     3: areturn
```
While `isAccessibilityDataSensitive` and `setAccessibilityDataSensitive` are guarded with `try ... catch (LinkageError)`, `getActionScrollInDirection()` and all remaining API 34 methods in `AccessibilityNodeInfoCompat$Api34Impl` are completely unguarded.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. **Direct Fix**: Wrap `AccessibilityNodeInfoCompat$Api34Impl.getActionScrollInDirection()` in `try ... catch (LinkageError e)` returning `null` safely and logging a diagnostic warning. Returning `null` precisely mirrors the pre-API 34 fallback (`Build.VERSION.SDK_INT < 34 ? null : ...`).
  2. **Proactive Blanket Shield**: Wrap **all remaining API 34 methods** in `AccessibilityNodeInfoCompat$Api34Impl` with defensive `try ... catch (LinkageError e)` blocks to guarantee complete immunity against any further missing platform methods:
     * `getContainerTitle(AccessibilityNodeInfo)`: returns `null`
     * `setContainerTitle(AccessibilityNodeInfo, CharSequence)`: safe no-op
     * `getBoundsInWindow(AccessibilityNodeInfo, Rect)`: safe no-op
     * `setBoundsInWindow(AccessibilityNodeInfo, Rect)`: safe no-op
     * `hasRequestInitialAccessibilityFocus(AccessibilityNodeInfo)`: returns `false`
     * `setRequestInitialAccessibilityFocus(AccessibilityNodeInfo, boolean)`: safe no-op
     * `getMinDurationBetweenContentChangeMillis(AccessibilityNodeInfo)`: returns `0L`
     * `setMinDurationBetweenContentChangeMillis(AccessibilityNodeInfo, long)`: safe no-op
     * `setQueryFromAppProcessEnabled(AccessibilityNodeInfo, View, boolean)`: safe no-op
  3. **Repackage Local AAR**: Recompile and repackage `local-repo/androidx/core/core/1.15.0-patched/core-1.15.0-patched.aar` and update `README.md`.
  4. **Targeted Unit Tests**: Extend `AccessibilityNodeInfoCompatResilienceTest.kt` to verify that `NoSuchFieldError` / `LinkageError` on `getActionScrollInDirection` and all blanket-shielded methods are handled cleanly without throwing exceptions or swallowing fatal JVM errors (`OutOfMemoryError`).
  5. **Dual Delivery**: Merge fix into `hotfix/V4.9.38.4__267` and the active sprint branch `sprint/2026-41.4`.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Do NOT modify Jetpack Compose view delegates directly.
  * Do NOT alter general UI components, navigation, or tracking code.
  * Do NOT bump AndroidX Core to an unreleased/incompatible major version.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

* **Original Requirement ID & Target**: `REQ-UI-164` (*Accessibility Event, NodeInfo & Manager Compatibility & Defective Platform Resilience*), targeting `local-repo/androidx/core/core/1.15.0-patched/core-1.15.0-patched.aar`.
* **Historical Origin & Commit Trace**:
  * Established under ATT-1347 (`b2a3a5f7`, Sprint 2026-40.4).
  * Amended under ATT-2302 (`96365709`, Hotfix V4.9.38.2).
  * Amended under ATT-2584 (`d2ab4240`, Hotfix V4.9.38.3).
* **Root Reason for Existing Formulation**:
  `REQ-UI-164` was formulated to eliminate crashes caused by incomplete Android 14 `framework.jar` builds via dependency substitution in `local-repo/`, maintaining 100% binary compatibility while protecting users on non-compliant OEM devices.
* **Preservation of Core Invariants**:
  Extending `REQ-UI-164` to `getActionScrollInDirection()` and establishing the Proactive Blanket Shield across all `AccessibilityNodeInfoCompat$Api34Impl` methods preserves 100% of the existing contract:
  - On intact Android 14+ devices, native platform fields and methods execute normally with zero overhead or behavioral drift.
  - On defective devices, `LinkageError` (`NoSuchFieldError`, `NoSuchMethodError`) is absorbed with graceful pre-API 34 fallback.
  - Fatal JVM non-linkage errors (`OutOfMemoryError`) remain uncaught and unswallowed.

---

## 5. Architectural Strategy & High-Level Solution

### A. Patched Bytecode Architecture
In `AccessibilityNodeInfoCompat.java` (targeting Java 8 bytecode):
```java
@RequiresApi(34)
static class Api34Impl {
    private static final String TAG = "AccessibilityNodeInfoCompat";

    public static AccessibilityNodeInfo.AccessibilityAction getActionScrollInDirection() {
        try {
            return AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_IN_DIRECTION;
        } catch (LinkageError e) {
            Log.w(TAG, "ACTION_SCROLL_IN_DIRECTION missing on platform framework; suppressing error", e);
            return null;
        }
    }

    public static CharSequence getContainerTitle(AccessibilityNodeInfo node) {
        try {
            return node.getContainerTitle();
        } catch (LinkageError e) {
            Log.w(TAG, "getContainerTitle missing on platform framework; suppressing error", e);
            return null;
        }
    }

    public static void setContainerTitle(AccessibilityNodeInfo node, CharSequence containerTitle) {
        try {
            node.setContainerTitle(containerTitle);
        } catch (LinkageError e) {
            Log.w(TAG, "setContainerTitle missing on platform framework; suppressing error", e);
        }
    }

    public static void getBoundsInWindow(AccessibilityNodeInfo node, Rect outBounds) {
        try {
            node.getBoundsInWindow(outBounds);
        } catch (LinkageError e) {
            Log.w(TAG, "getBoundsInWindow missing on platform framework; suppressing error", e);
        }
    }

    public static void setBoundsInWindow(AccessibilityNodeInfo node, Rect bounds) {
        try {
            node.setBoundsInWindow(bounds);
        } catch (LinkageError e) {
            Log.w(TAG, "setBoundsInWindow missing on platform framework; suppressing error", e);
        }
    }

    public static boolean hasRequestInitialAccessibilityFocus(AccessibilityNodeInfo node) {
        try {
            return node.hasRequestInitialAccessibilityFocus();
        } catch (LinkageError e) {
            Log.w(TAG, "hasRequestInitialAccessibilityFocus missing on platform framework; suppressing error", e);
            return false;
        }
    }

    public static void setRequestInitialAccessibilityFocus(AccessibilityNodeInfo node, boolean requestInitialAccessibilityFocus) {
        try {
            node.setRequestInitialAccessibilityFocus(requestInitialAccessibilityFocus);
        } catch (LinkageError e) {
            Log.w(TAG, "setRequestInitialAccessibilityFocus missing on platform framework; suppressing error", e);
        }
    }

    public static long getMinDurationBetweenContentChangeMillis(AccessibilityNodeInfo node) {
        try {
            return node.getMinDurationBetweenContentChanges().toMillis();
        } catch (LinkageError e) {
            Log.w(TAG, "getMinDurationBetweenContentChanges missing on platform framework; suppressing error", e);
            return 0L;
        }
    }

    public static void setMinDurationBetweenContentChangeMillis(AccessibilityNodeInfo node, long durationMillis) {
        try {
            node.setMinDurationBetweenContentChanges(java.time.Duration.ofMillis(durationMillis));
        } catch (LinkageError e) {
            Log.w(TAG, "setMinDurationBetweenContentChanges missing on platform framework; suppressing error", e);
        }
    }

    public static void setQueryFromAppProcessEnabled(AccessibilityNodeInfo node, View view, boolean enabled) {
        try {
            node.setQueryFromAppProcessEnabled(view, enabled);
        } catch (LinkageError e) {
            Log.w(TAG, "setQueryFromAppProcessEnabled missing on platform framework; suppressing error", e);
        }
    }
}
```

### B. Unit Test Verification
Extend `AccessibilityNodeInfoCompatResilienceTest.kt`:
1. `testSetAccessibilityDataSensitive_whenPlatformMethodThrowsNoSuchMethodError_isSuppressedCleanly`: verifies existing guard.
2. `testIsAccessibilityDataSensitive_whenPlatformMethodThrowsNoSuchMethodError_returnsFalseSafely`: verifies existing guard.
3. `testGetActionScrollInDirection_whenPlatformFieldThrowsNoSuchFieldError_returnsNullSafely`: verifies new guard.
4. `testBlanketShield_methodsCatchLinkageErrorSafely`: verifies all remaining API 34 methods safely catch `LinkageError`.
5. `testApi34Impl_doesNotSwallowFatalOutOfMemoryError`: verifies safety invariant that non-linkage errors are never swallowed.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero runtime overhead on compliant Android 14+ devices.
  2. Graceful fallback on broken OEM firmware without UI freeze or crash.
  3. Non-linkage errors (`OutOfMemoryError`) are never caught.
  4. Local dependency substitution preserves clean D8/R8 compilation without duplicate class collisions.
  5. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW**
  - The fix strictly catches `LinkageError` and returns standard pre-API 34 fallback values (`null`, `false`, `0L`, or no-op). Compliant devices continue executing native methods with 100% fidelity.
