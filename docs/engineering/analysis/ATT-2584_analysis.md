# Stage 1 Analysis: ATT-2584 - NoSuchMethodError in AccessibilityManagerCompat$Api34Impl.isRequestFromAccessibilityTool

**Ticket**: [ATT-2584](https://rainerblind.atlassian.net/browse/ATT-2584)  
**Sub-task**: [ATT-2635](https://rainerblind.atlassian.net/browse/ATT-2635) (`[Analysis]`)  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*No crashs*)  
**Target Release**: `V4.9.38.3`  
**Active Sprint**: None (Hotfix Release V4.9.38.3)  
**Branch**: `hotfix/V4.9.38.3__266`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-07  

---

## 1. Problem Statement & Motivation

In production release `V4.9.38.2 (265)`, a fatal crash was captured by Firebase Crashlytics on Android 14 (API 34) devices:

```text
Fatal Exception: java.lang.NoSuchMethodError: No virtual method isRequestFromAccessibilityTool()Z in class Landroid/view/accessibility/AccessibilityManager; or its super classes (declaration of 'android.view.accessibility.AccessibilityManager' appears in /system/framework/framework.jar!classes3.dex)
       at androidx.core.view.accessibility.AccessibilityManagerCompat$Api34Impl.isRequestFromAccessibilityTool(AccessibilityManagerCompat.java:316)
       at androidx.core.view.accessibility.AccessibilityManagerCompat.isRequestFromAccessibilityTool(AccessibilityManagerCompat.java:301)
       at androidx.compose.ui.platform.AndroidComposeViewAccessibilityDelegateCompat.isRequestFromAccessibilityTool(AndroidComposeViewAccessibilityDelegateCompat.android.kt:612)
       at androidx.compose.ui.platform.AndroidComposeViewAccessibilityDelegateCompat.populateAccessibilityNodeInfoProperties(AndroidComposeViewAccessibilityDelegateCompat.android.kt:580)
       at androidx.compose.ui.platform.AndroidComposeViewAccessibilityDelegateCompat.createNodeInfo(AndroidComposeViewAccessibilityDelegateCompat.android.kt:544)
```

This defect immediately crashes the application during view composition whenever accessibility, autofill, or UI inspection services query the Compose accessibility hierarchy on affected Android 14 devices.

---

## 2. Root Cause Analysis (Forensic Investigation)

### A. Lineage of Defective Android 14 OEM ROMs
This defect is the direct companion crash to two earlier production defects:
1. **ATT-1347**: `NoSuchMethodError` on `AccessibilityEvent.setAccessibilityDataSensitive(Z)V`.
2. **ATT-2302**: `NoSuchMethodError` on `AccessibilityNodeInfo.setAccessibilityDataSensitive(Z)V`.

Android 14 (API 34) introduced accessibility data sensitivity and caller qualification APIs (`isRequestFromAccessibilityTool()`). On standard Android 14 devices, `AccessibilityManager.isRequestFromAccessibilityTool()` exists in `framework.jar`. However, certain OEM Android 14 builds and early emulator/crawling images report `Build.VERSION.SDK_INT == 34` while omitting the virtual method `isRequestFromAccessibilityTool()Z`.

### B. Invocation Mechanism
In `androidx.core:core:1.15.0`:
```java
public static boolean isRequestFromAccessibilityTool(@NonNull AccessibilityManager manager) {
    if (Build.VERSION.SDK_INT >= 34) {
        return Api34Impl.isRequestFromAccessibilityTool(manager);
    }
    return false;
}

@RequiresApi(34)
static class Api34Impl {
    @DoNotInline
    static boolean isRequestFromAccessibilityTool(AccessibilityManager manager) {
        return manager.isRequestFromAccessibilityTool();
    }
}
```
When `AndroidComposeViewAccessibilityDelegateCompat.createNodeInfo` executes, it invokes `AccessibilityManagerCompat.isRequestFromAccessibilityTool`. Because `Build.VERSION.SDK_INT >= 34`, ART attempts to resolve `manager.isRequestFromAccessibilityTool()`. On defective OEM firmware, this triggers `java.lang.NoSuchMethodError`.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Guard `AccessibilityManagerCompat$Api34Impl.isRequestFromAccessibilityTool()` with `try ... catch (LinkageError e)` returning safe `false` fallback and logging diagnostic warning.
  * Proactively guard unshielded API 34 methods in `AccessibilityWindowInfoCompat$Api34Impl` (`getTransitionTimeMillis` returning `0L`, `getLocales` returning `null`) to eliminate any future pre-launch or crawler crashes.
  * Update `core-1.15.0-patched.aar` in `local-repo/`.
  * Author unit test suite verifying `NoSuchMethodError` suppression, intact pass-through, and JVM `OutOfMemoryError` non-swallowing.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Do NOT modify Jetpack Compose view accessibility delegates directly.
  * Do NOT alter tracking, sensor listeners, database operations, or general UI layouts.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

* **Original Requirement ID & Target**: `REQ-UI-164` (Accessibility Event Dispatch Compatibility & Defective Platform Resilience) in `docs/requirements.md`.
* **Historical Origin & Commit Trace**: Established under ATT-1347 (`b2a3a5f7`) and amended under ATT-2302 (`96365709`).
* **Root Reason for Existing Formulation**: Built to shield Android 14 devices from fatal crashes caused by missing platform methods in `framework.jar` when `Build.VERSION.SDK_INT == 34`.
* **Preservation of Core Invariants**: Extending the scope of `REQ-UI-164` to explicitly encompass `AccessibilityManagerCompat.isRequestFromAccessibilityTool` and `AccessibilityWindowInfoCompat` preserves 100% of the existing contract: on intact devices, native behavior is unchanged; on defective devices, fatal crashes are safely suppressed with graceful degradation.

---

## 5. Architectural Strategy & High-Level Solution

1. **Patched Bytecode in `local-repo/androidx/core/core/1.15.0-patched/core-1.15.0-patched.aar`**:
   * Compile `AccessibilityManagerCompat$Api34Impl.class` with Java 8 bytecode catching `LinkageError` and returning `false`.
   * Compile `AccessibilityWindowInfoCompat$Api34Impl.class` with Java 8 bytecode catching `LinkageError` and returning safe defaults (`0L` / `null`).
   * Package into `classes.jar` of `core-1.15.0-patched.aar`.
2. **Automated Unit Testing**:
   * Implement `AccessibilityManagerCompatResilienceTest.kt` verifying reflection/mock invocations on `Api34Impl`.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero runtime overhead on compliant Android 14+ devices.
  2. Graceful fallback on broken OEM firmware without UI freeze or crash.
  3. Non-linkage errors (`OutOfMemoryError`) are never caught.
  4. 100% clean unit test suite.
* **Risk Rating**: **LOW**
  * The fix is strictly localized to exception handling within the compatibility layer. No business logic or state machines are touched.
