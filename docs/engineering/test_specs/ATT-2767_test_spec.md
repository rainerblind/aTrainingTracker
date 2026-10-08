# Stage 2: Requirement & Test Specification - ATT-2767: NoSuchFieldError in AccessibilityNodeInfoCompat$Api34Impl.getActionScrollInDirection

**Ticket**: [ATT-2767](https://atrainingtracker.atlassian.net/browse/ATT-2767)  
**Sub-task**: [ATT-2776](https://atrainingtracker.atlassian.net/browse/ATT-2776) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38.4`  
**Active Sprint**: `2026-41.4`  
**Requirement Mapping**: `REQ-UI-164` (*Accessibility Event, NodeInfo & Manager Compatibility & Defective Platform Resilience*)  
**Test Spec ID**: `TST-UI-116`  
**Branch**: `bugfix/ATT-2767`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-08  

---

## 1. Requirement Specification (REQ-UI-164)

### 1.1 Problem Statement & Rationale
In production on Android 14 (API 34) devices running non-compliant OEM firmware (Transsion, Infinix, Tecno, etc.), a fatal crash was captured via Firebase Crashlytics:
```text
Fatal Exception: java.lang.NoSuchFieldError: No static field ACTION_SCROLL_IN_DIRECTION of type Landroid/view/accessibility/AccessibilityNodeInfo$AccessibilityAction; in class Landroid/view/accessibility/AccessibilityNodeInfo$AccessibilityAction;
    at androidx.core.view.accessibility.AccessibilityNodeInfoCompat$Api34Impl.getActionScrollInDirection(AccessibilityNodeInfoCompat.java)
    at androidx.core.view.accessibility.AccessibilityNodeInfoCompat$AccessibilityActionCompat.<clinit>(AccessibilityNodeInfoCompat.java:547)
```
During static class initialization of `AccessibilityNodeInfoCompat$AccessibilityActionCompat`, `Api34Impl.getActionScrollInDirection()` unguardedly accesses `AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_IN_DIRECTION`. On affected devices, missing platform fields throw `java.lang.NoSuchFieldError` (a `LinkageError`), causing `ExceptionInInitializerError` and terminating the application upon the first touch of Compose or Android View accessibility.

Existing requirement `REQ-UI-164` is amended to encompass `AccessibilityNodeInfoCompat$Api34Impl.getActionScrollInDirection()` and establish a **Proactive Blanket Shield** across all remaining API 34 methods in `AccessibilityNodeInfoCompat$Api34Impl`.

### 1.2 Functional & Architectural Requirements
1. **Targeted Dependency Substitution & Patched Core Artifact**: The build system SHALL substitute `androidx.core:core:1.15.0` with local patched artifact `androidx.core:core:1.15.0-patched` (via `settings.gradle` local maven repository and `app/build.gradle` `resolutionStrategy.dependencySubstitution`), completely avoiding D8/R8 dex-merging duplicate class conflicts and classpath shadowing ambiguity in `app/src/main/java`.
2. **Defensive LinkageError Field & Method Guards**:
   - In `AccessibilityNodeInfoCompat$Api34Impl.getActionScrollInDirection()`, access to `AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_IN_DIRECTION` SHALL be wrapped in a defensive `try ... catch (LinkageError e)` block. On defective API 34 ROMs lacking the field, the guard SHALL log a diagnostic warning and return `null` safely (matching standard pre-API 34 fallback).
   - In `AccessibilityNodeInfoCompat$Api34Impl`, all remaining unguarded API 34 methods SHALL be wrapped with a **Proactive Blanket Shield** (`try ... catch (LinkageError e)`):
     * `getContainerTitle(AccessibilityNodeInfo)`: returns `null`
     * `setContainerTitle(AccessibilityNodeInfo, CharSequence)`: safe no-op
     * `getBoundsInWindow(AccessibilityNodeInfo, Rect)`: safe no-op
     * `setBoundsInWindow(AccessibilityNodeInfo, Rect)`: safe no-op
     * `hasRequestInitialAccessibilityFocus(AccessibilityNodeInfo)`: returns `false`
     * `setRequestInitialAccessibilityFocus(AccessibilityNodeInfo, boolean)`: safe no-op
     * `getMinDurationBetweenContentChangeMillis(AccessibilityNodeInfo)`: returns `0L`
     * `setMinDurationBetweenContentChangeMillis(AccessibilityNodeInfo, long)`: safe no-op
     * `setQueryFromAppProcessEnabled(AccessibilityNodeInfo, View, boolean)`: safe no-op
3. **Build Variant Neutrality**: The patched artifact SHALL operate uniformly and deterministically across all build types (Debug, Profile, Release), ensuring that developers, automated UI tests, and release APKs share identical crash immunity with zero dependency on R8 compiler optimization heuristics.
4. **Looper & Dispatch Loop Integrity**: The call-site interception SHALL prevent exceptions from escaping into static class initialization (`<clinit>`), View composition, or accessibility dispatch loops, completely eliminating app crashes, UI freezes, or state corruption.
5. **Dual Delivery & Hotfix Parity**: The patched artifact and verification suite SHALL be integrated into both the active sprint integration branch (`sprint/2026-41.4`) and the release hotfix branch (`hotfix/V4.9.38.4__267`).

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-164` (*Accessibility Event, NodeInfo & Manager Compatibility & Defective Platform Resilience*), targeting `local-repo/androidx/core/core/1.15.0-patched/core-1.15.0-patched.aar`.
* **Historical Origin & Commit Trace**:
  * Established under ATT-1347 (`b2a3a5f7`, Sprint 2026-40.4).
  * Amended under ATT-2302 (`96365709`, Hotfix V4.9.38.2).
  * Amended under ATT-2584 (`d2ab4240`, Hotfix V4.9.38.3).
  * Amended under ATT-2767 (Sprint 2026-41.4 & Hotfix V4.9.38.4).
* **Root Reason for Existing Formulation**: Built to absorb `LinkageError` crashes on defective Android 14 OEM firmware where `Build.VERSION.SDK_INT == 34` but accessibility methods or fields are omitted from `framework.jar`.
* **Preservation of Core Invariants**: Extending the defensive shield to `AccessibilityNodeInfoCompat$Api34Impl.getActionScrollInDirection` and all remaining API 34 methods preserves 100% of the existing contract: on intact devices, native behavior is unchanged; on defective devices, fatal crashes are safely suppressed with graceful pre-API 34 degradation; fatal JVM non-linkage errors (`OutOfMemoryError`) are never swallowed.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Defective OEM Field Crash Immunity)**:
  * *Given* an application build running on an Android 14 (API 34) device missing `AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_IN_DIRECTION`,
  * *When* `AccessibilityNodeInfoCompat$AccessibilityActionCompat` executes static class initialization (`<clinit>`),
  * *Then* `Api34Impl.getActionScrollInDirection()` SHALL intercept the missing field via `try ... catch (LinkageError e)`, log a warning to logcat, and return `null` cleanly without throwing `NoSuchFieldError`.
* **Criterion 2 (Proactive Blanket Shield Resilience)**:
  * *Given* a defective API 34 device missing any other platform API 34 method in `AccessibilityNodeInfoCompat`,
  * *When* the method is invoked,
  * *Then* `Api34Impl` SHALL catch `LinkageError`, log a warning, and fall back safely to default behavior (`null`, `false`, `0L`, or no-op) without crashing.
* **Criterion 3 (Intact Platform Parity)**:
  * *Given* an intact Android 14/15/16 device with standard platform fields and methods,
  * *When* `getActionScrollInDirection` or any API 34 method is invoked,
  * *Then* the platform field/method SHALL execute natively without interference or overhead.
* **Criterion 4 (JVM Safety)**:
  * *Given* a fatal non-linkage error such as `OutOfMemoryError`,
  * *When* invoked,
  * *Then* `Api34Impl` SHALL NOT swallow the error and SHALL allow it to propagate to the JVM uncaught exception handler.
* **Criterion 5 (Build & Toolchain Integrity)**:
  * *Given* any build configuration (Debug, Profile, Release),
  * *When* compiling via `./gradlew assembleRelease`,
  * *Then* compilation and dex merging SHALL complete successfully with zero duplicate class collisions.

### 1.5 System Invariants
1. Native behavior on compliant Android 14+ devices is 100% preserved.
2. Non-linkage JVM errors (`OutOfMemoryError`, `StackOverflowError`) are never caught.
3. Clean dex merging without package spoofing in `app/src/main/java`.
4. Pre-API 34 binary compatibility preserved (`action = null`).

---

## 2. Test Specification (TST-UI-116)

### Test Case 1: `testGetActionScrollInDirection_whenPlatformFieldThrowsNoSuchFieldError_returnsNullSafely` (`TST-UI-116.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/accessibility/AccessibilityNodeInfoCompatResilienceTest.kt`
* **Preconditions**: Java reflection targeting `AccessibilityNodeInfoCompat$Api34Impl.getActionScrollInDirection()`.
* **Action**: Simulate or invoke `getActionScrollInDirection()`. When underlying field lookup throws `NoSuchFieldError`, verify that `LinkageError` handler intercepts the exception.
* **Expected Result**: Method returns `null` safely; diagnostic warning logged to logcat.

### Test Case 2: `testGetActionScrollInDirection_whenPlatformFieldIntact_returnsActionSuccessfully` (`TST-UI-116.2`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/accessibility/AccessibilityNodeInfoCompatResilienceTest.kt`
* **Preconditions**: Standard test environment with mocked or platform `ACTION_SCROLL_IN_DIRECTION`.
* **Action**: Invoke `getActionScrollInDirection()`.
* **Expected Result**: Valid `AccessibilityAction` returned without exceptions.

### Test Case 3: `testBlanketShield_methodsCatchLinkageErrorSafely` (`TST-UI-116.3`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/accessibility/AccessibilityNodeInfoCompatResilienceTest.kt`
* **Preconditions**: Mocked `AccessibilityNodeInfo` throwing `NoSuchMethodError` on `getContainerTitle`, `setContainerTitle`, `getBoundsInWindow`, `setBoundsInWindow`, `hasRequestInitialAccessibilityFocus`, `setRequestInitialAccessibilityFocus`, `getMinDurationBetweenContentChanges`, `setMinDurationBetweenContentChanges`, and `setQueryFromAppProcessEnabled`.
* **Action**: Invoke corresponding `Api34Impl` methods via reflection.
* **Expected Result**: All methods catch `LinkageError` cleanly, return safe defaults (`null`, `false`, `0L`, or Unit), and log diagnostic warnings.

### Test Case 4: `testApi34Impl_doesNotSwallowFatalOutOfMemoryError` (`TST-UI-116.4`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/accessibility/AccessibilityNodeInfoCompatResilienceTest.kt`
* **Preconditions**: Mocked `AccessibilityNodeInfo` throwing `OutOfMemoryError`.
* **Action**: Invoke `Api34Impl` methods.
* **Expected Result**: `OutOfMemoryError` is thrown out and never caught.

### Test Case 5: Clean-Room Regression Suite (`TST-UI-116.5`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full 436-class test suite with zero regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-116.1` | Unit | `AccessibilityNodeInfoCompat$Api34Impl.getActionScrollInDirection` | `REQ-UI-164` | Specified |
| `TST-UI-116.2` | Unit | `AccessibilityNodeInfoCompat$Api34Impl.getActionScrollInDirection` | `REQ-UI-164` | Specified |
| `TST-UI-116.3` | Unit | `AccessibilityNodeInfoCompat$Api34Impl` (Blanket Shield) | `REQ-UI-164` | Specified |
| `TST-UI-116.4` | Unit | `AccessibilityNodeInfoCompat$Api34Impl` (JVM Safety) | `REQ-UI-164` | Specified |
| `TST-UI-116.5` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001`, `REQ-UI-164` | Specified |
