# Stage 3: Implementation Plan - ATT-2584: AccessibilityManagerCompat$Api34Impl.isRequestFromAccessibilityTool

**Ticket**: [ATT-2584](https://rainerblind.atlassian.net/browse/ATT-2584)  
**Sub-task**: [ATT-2637](https://rainerblind.atlassian.net/browse/ATT-2637) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*No crashs*)  
**Target Release**: `V4.9.38.3`  
**Active Sprint**: None (Hotfix Release V4.9.38.3)  
**Requirement Mapping**: `REQ-UI-164` (*Accessibility Event, NodeInfo & Manager Compatibility & Defective Platform Resilience*)  
**Test Mapping**: `TST-UI-116` (*Accessibility Event, NodeInfo & Manager Api34 Compatibility & LinkageError Resilience Verification*)  
**Branch**: `hotfix/V4.9.38.3__266`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-07  

---

## 1. Problem Description & Background

In production release `V4.9.38.2 (265)`, a fatal crash was captured by Firebase Crashlytics on Android 14 (API 34) devices:
```text
Fatal Exception: java.lang.NoSuchMethodError: No virtual method isRequestFromAccessibilityTool()Z in class Landroid/view/accessibility/AccessibilityManager;
       at androidx.core.view.accessibility.AccessibilityManagerCompat$Api34Impl.isRequestFromAccessibilityTool(AccessibilityManagerCompat.java:316)
       at androidx.compose.ui.platform.AndroidComposeViewAccessibilityDelegateCompat.isRequestFromAccessibilityTool(AndroidComposeViewAccessibilityDelegateCompat.android.kt:612)
```
On specific OEM Android 14 builds, `Build.VERSION.SDK_INT == 34` is reported, but `AccessibilityManager.isRequestFromAccessibilityTool()` is omitted from `framework.jar`. When Compose inspects the view hierarchy, ART throws `NoSuchMethodError`.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-164` (*Accessibility Event, NodeInfo & Manager Compatibility & Defective Platform Resilience*)
* **Test Mapping**: `TST-UI-116` (*Accessibility Event, NodeInfo & Manager Api34 Compatibility & LinkageError Resilience Verification*)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing feature suites and unit tests continue to pass cleanly.
2. **Platform Parity on Intact Devices**: On compliant Android 14+ devices, native execution runs with zero overhead and full fidelity.
3. **Fatal JVM Error Safety**: Non-linkage errors such as `OutOfMemoryError` are never caught.
4. **Toolchain & Dex Integrity**: Dependency substitution via `local-repo/` guarantees zero D8 duplicate class collisions and zero classpath ambiguity.
5. **Human Decision Gate**: Parent ticket completion remains strictly reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: Patched Local AAR (`local-repo/androidx/core/core/1.15.0-patched/core-1.15.0-patched.aar`)
* **Layer**: Core Compatibility Layer (`androidx.core.view.accessibility`)
* **Class 1**: `AccessibilityManagerCompat$Api34Impl.class`
  * Guard `isRequestFromAccessibilityTool` with `try ... catch (LinkageError e)` returning safe `false` fallback and logging diagnostic warning.
* **Class 2**: `AccessibilityWindowInfoCompat$Api34Impl.class`
  * Guard `getTransitionTimeMillis` (returning `0L`) and `getLocales` (returning `null`) against missing platform methods.
* **Traceability Doc**: Update `local-repo/androidx/core/core/1.15.0-patched/README.md`.

### Component 2: Automated Unit Resilience Suite
* **Target**: `app/src/test/java/com/atrainingtracker/trainingtracker/accessibility/AccessibilityManagerCompatResilienceTest.kt`
* Validates reflection/mock invocations:
  * Safe `false` fallback when `NoSuchMethodError` occurs.
  * Native `true` pass-through when method exists.
  * Uncaught propagation of `OutOfMemoryError`.
  * Safe `0L` / `null` fallbacks on `AccessibilityWindowInfoCompat$Api34Impl`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update `core-1.15.0-patched.aar`
* Extract `classes.jar` from `local-repo/androidx/core/core/1.15.0-patched/core-1.15.0-patched.aar`.
* Replace `AccessibilityManagerCompat$Api34Impl.class` and `AccessibilityWindowInfoCompat$Api34Impl.class` with Java 8 bytecode compiled with `LinkageError` exception handlers.
* Re-pack `classes.jar` and `core-1.15.0-patched.aar`.

### Step 2: Update Local Repo Documentation
* File: `local-repo/androidx/core/core/1.15.0-patched/README.md`.
* Document ATT-2584 issue traceability, modified methods, and compilation flags.

### Step 3: Construct Unit Test Suite
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/accessibility/AccessibilityManagerCompatResilienceTest.kt`.
* Implement the 5 test cases specified under `TST-UI-116`.

### Step 4: Execute Targeted Unit Tests
* Command:
  ```bash
  ./gradlew testDebugUnitTest --tests "*.AccessibilityManagerCompatResilienceTest"
  ```
* Expected outcome: 5 tests run, 0 failures, 0 errors.

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted unit tests during Stage 4 construction, followed by full test suite regression (`./gradlew testDebugUnitTest`) in Stage 5.
* **Rollback**: Isolated git branch `hotfix/V4.9.38.3__266` allows instant rollback via git revert or branch recreation without impacting `master` or `develop`.
