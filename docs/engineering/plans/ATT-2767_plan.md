# Stage 3: Implementation Plan - ATT-2767: NoSuchFieldError in AccessibilityNodeInfoCompat$Api34Impl.getActionScrollInDirection

**Ticket**: [ATT-2767](https://atrainingtracker.atlassian.net/browse/ATT-2767)  
**Sub-task**: [ATT-2777](https://atrainingtracker.atlassian.net/browse/ATT-2777) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38.4`  
**Active Sprint**: `2026-41.4`  
**Requirement Mapping**: `REQ-UI-164` (*Accessibility Event, NodeInfo & Manager Compatibility & Defective Platform Resilience*)  
**Test Mapping**: `TST-UI-116` (*Accessibility Event, NodeInfo & Manager Api34 Compatibility & LinkageError Resilience Verification*)  
**Branch**: `bugfix/ATT-2767`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-08  

---

## 1. Problem Description & Background

In production release `V4.9.38.2` / `V4.9.38.3`, a fatal crash was captured by Firebase Crashlytics on Android 14 (API 34) devices:
```text
Fatal Exception: java.lang.NoSuchFieldError: No static field ACTION_SCROLL_IN_DIRECTION of type Landroid/view/accessibility/AccessibilityNodeInfo$AccessibilityAction; in class Landroid/view/accessibility/AccessibilityNodeInfo$AccessibilityAction;
    at androidx.core.view.accessibility.AccessibilityNodeInfoCompat$Api34Impl.getActionScrollInDirection(AccessibilityNodeInfoCompat.java)
    at androidx.core.view.accessibility.AccessibilityNodeInfoCompat$AccessibilityActionCompat.<clinit>(AccessibilityNodeInfoCompat.java:547)
```
On non-certified or incomplete Android 14 OEM firmware (Transsion, Infinix, Tecno, etc.), `Build.VERSION.SDK_INT == 34` is reported, but standard API 34 platform fields like `ACTION_SCROLL_IN_DIRECTION` are absent from `framework.jar`. During static initialization of `AccessibilityNodeInfoCompat$AccessibilityActionCompat`, `Api34Impl.getActionScrollInDirection()` unguardedly accesses this field, throwing `NoSuchFieldError` (a `LinkageError`), causing `ExceptionInInitializerError` and terminating the app on startup.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-164` (*Accessibility Event, NodeInfo & Manager Compatibility & Defective Platform Resilience*)
* **Test Mapping**: `TST-UI-116` (*Accessibility Event, NodeInfo & Manager Api34 Compatibility & LinkageError Resilience Verification*)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing feature test suites continue to pass cleanly.
2. **Platform Parity on Intact Devices**: On standard Android 14/15/16 devices, native execution runs with zero overhead and full fidelity.
3. **Pre-API 34 Binary Compatibility**: When `ACTION_SCROLL_IN_DIRECTION` is absent, returning `null` safely mirrors the pre-API 34 fallback (`Build.VERSION.SDK_INT < 34 ? null : ...`).
4. **Fatal JVM Error Safety**: Non-linkage fatal errors such as `OutOfMemoryError` are never caught.
5. **Toolchain & Dex Integrity**: Dependency substitution via `local-repo/` guarantees zero D8 duplicate class collisions and zero classpath ambiguity in release/debug builds.
6. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.
7. **Dual Delivery & Hotfix Parity**: The fix must be integrated into both the active sprint integration branch (`sprint/2026-41.4`) and the release hotfix branch (`hotfix/V4.9.38.4__267`).

---

## 4. Proposed Architectural Changes

### Component 1: Patched Local AAR (`local-repo/androidx/core/core/1.15.0-patched/core-1.15.0-patched.aar`)
* **Layer**: Core Compatibility Layer (`androidx.core.view.accessibility`)
* **Class**: `AccessibilityNodeInfoCompat$Api34Impl.class`
* **Direct Guard**:
  - `getActionScrollInDirection()` wrapped in `try ... catch (LinkageError e)` returning safe `null` fallback and logging diagnostic warning.
* **Proactive Blanket Shield**:
  - `getContainerTitle(AccessibilityNodeInfo)`: returns `null` on `LinkageError`
  - `setContainerTitle(AccessibilityNodeInfo, CharSequence)`: safe no-op on `LinkageError`
  - `getBoundsInWindow(AccessibilityNodeInfo, Rect)`: safe no-op on `LinkageError`
  - `setBoundsInWindow(AccessibilityNodeInfo, Rect)`: safe no-op on `LinkageError`
  - `hasRequestInitialAccessibilityFocus(AccessibilityNodeInfo)`: returns `false` on `LinkageError`
  - `setRequestInitialAccessibilityFocus(AccessibilityNodeInfo, boolean)`: safe no-op on `LinkageError`
  - `getMinDurationBetweenContentChangeMillis(AccessibilityNodeInfo)`: returns `0L` on `LinkageError`
  - `setMinDurationBetweenContentChangeMillis(AccessibilityNodeInfo, long)`: safe no-op on `LinkageError`
  - `setQueryFromAppProcessEnabled(AccessibilityNodeInfo, View, boolean)`: safe no-op on `LinkageError`
* **Traceability Doc**: Update `local-repo/androidx/core/core/1.15.0-patched/README.md`.

### Component 2: Automated Unit Resilience Suite
* **Target**: `app/src/test/java/com/atrainingtracker/trainingtracker/accessibility/AccessibilityNodeInfoCompatResilienceTest.kt`
* Validates reflection and mock invocations:
  - `testGetActionScrollInDirection_whenPlatformFieldThrowsNoSuchFieldError_returnsNullSafely` (TST-UI-116.1)
  - `testGetActionScrollInDirection_whenPlatformFieldIntact_returnsActionSuccessfully` (TST-UI-116.2)
  - `testBlanketShield_methodsCatchLinkageErrorSafely` (TST-UI-116.3)
  - `testApi34Impl_doesNotSwallowFatalOutOfMemoryError` (TST-UI-116.4)

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
No UI changes. Pure low-level AndroidX Core library compatibility patch and reflection unit test suite.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Programmatic Gate Check
* Command:
  ```bash
  python3 tools/jira_util.py check-gate ATT-2777
  ```
* Verify exit code 0 (`GATE_PASSED`) before modifying any production or test code.

### Step 2: Compile Patched Bytecode
* Compile `AccessibilityNodeInfoCompat$Api34Impl` targeting Java 8 bytecode (`-source 1.8 -target 1.8`) against Android SDK 34 (`android.jar`).
* Verify bytecode exception tables via `javap -c -p` ensuring `LinkageError` is caught across all API 34 methods.

### Step 3: Update `core-1.15.0-patched.aar`
* Replace `AccessibilityNodeInfoCompat$Api34Impl.class` inside `classes.jar` of `core-1.15.0-patched.aar`.
* Re-pack `classes.jar` and `core-1.15.0-patched.aar`.
* Update `local-repo/androidx/core/core/1.15.0-patched/README.md`.

### Step 4: Extend Unit Test Suite
* Update `app/src/test/java/com/atrainingtracker/trainingtracker/accessibility/AccessibilityNodeInfoCompatResilienceTest.kt` to cover `getActionScrollInDirection` field resilience, blanket shield methods, and `OutOfMemoryError` non-swallowing.

### Step 5: Execute Targeted Unit Tests
* Command:
  ```bash
  ./gradlew testDebugUnitTest --tests "*.AccessibilityNodeInfoCompatResilienceTest"
  ```
* Expected outcome: All tests run, 0 failures, 0 errors.

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted unit tests in Stage 4, followed by full regression suite (`./gradlew testDebugUnitTest`) in Stage 5.
* **Rollback**: Branch isolation (`bugfix/ATT-2767`) allows instant rollback via git revert or branch recreation without impacting `master`, `develop`, or `sprint/2026-41.4`.
