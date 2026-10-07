# Stage 2: Requirement & Test Specification - ATT-2584: AccessibilityManagerCompat$Api34Impl.isRequestFromAccessibilityTool

**Ticket**: [ATT-2584](https://rainerblind.atlassian.net/browse/ATT-2584)  
**Sub-task**: [ATT-2636](https://rainerblind.atlassian.net/browse/ATT-2636) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*No crashs*)  
**Target Release**: `V4.9.38.3`  
**Active Sprint**: None (Hotfix Release V4.9.38.3)  
**Requirement Mapping**: `REQ-UI-164` (*Accessibility Event, NodeInfo & Manager Compatibility & Defective Platform Resilience*)  
**Test Spec ID**: `TST-UI-116`  
**Branch**: `hotfix/V4.9.38.3__266`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-07  

---

## 1. Requirement Specification (REQ-UI-164)

### 1.1 Problem Statement & Rationale
In release `V4.9.38.2 (265)`, a fatal crash was recorded in Firebase Crashlytics on Android 14 (API 34) OEM devices missing `AccessibilityManager.isRequestFromAccessibilityTool()`. Jetpack Compose view inspection queries this method, causing unhandled `java.lang.NoSuchMethodError`. `REQ-UI-164` is expanded to provide runtime immunity across all unshielded API 34 accessibility compatibility methods in `androidx.core`.

### 1.2 Functional & Architectural Requirements
1. **Targeted Dependency Substitution & Patched Core Artifact**: The build system SHALL substitute `androidx.core:core:1.15.0` with local patched artifact `androidx.core:core:1.15.0-patched` (via `settings.gradle` local maven repository and `app/build.gradle` `resolutionStrategy.dependencySubstitution`), completely avoiding D8/R8 dex-merging duplicate class conflicts and classpath shadowing ambiguity in `app/src/main/java`.
2. **Defensive LinkageError Call-Site Guards**: In `AccessibilityEventCompat$Api34Impl`, `AccessibilityNodeInfoCompat$Api34Impl`, `AccessibilityManagerCompat$Api34Impl`, and `AccessibilityWindowInfoCompat$Api34Impl`, platform invocations SHALL be wrapped in defensive `try ... catch (LinkageError e)` blocks. On defective API 34 ROMs lacking the virtual methods, the guards SHALL log a warning and return cleanly (returning `false` for `isRequestFromAccessibilityTool`, `0L` for `getTransitionTimeMillis`, and `null` for `getLocales`) without throwing unhandled JVM linkage errors (`NoSuchMethodError`) or swallowing non-linkage fatal JVM errors (`OutOfMemoryError`).
3. **Build Variant Neutrality**: The patched artifact SHALL operate uniformly and deterministically across all build types (Debug, Profile, Release), ensuring that developers, automated UI tests, and release APKs share identical crash immunity with zero dependency on R8 compiler optimization heuristics.
4. **Looper & Dispatch Loop Integrity**: The call-site interception SHALL prevent exceptions from escaping into `AndroidComposeViewAccessibilityDelegateCompat.boundsUpdatesEventLoop$ui`, `createNodeInfo`, or `isRequestFromAccessibilityTool`, completely eliminating main-thread `Looper` infinite dispatch retry loops, UI freezes, or state corruption.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-164` (*Accessibility Event & NodeInfo Compatibility & Defective Platform Resilience*), targeting `local-repo/androidx/core/core/1.15.0-patched/core-1.15.0-patched.aar`.
* **Historical Origin & Commit Trace**: Established under ATT-1347 (`b2a3a5f7`) and amended under ATT-2302 (`96365709`).
* **Root Reason for Existing Formulation**: Built to absorb `NoSuchMethodError` crashes on defective Android 14 OEM firmware where `Build.VERSION.SDK_INT == 34` but accessibility methods are omitted from `framework.jar`.
* **Preservation of Core Invariants**: Extending the defensive shield to `AccessibilityManagerCompat$Api34Impl.isRequestFromAccessibilityTool` and `AccessibilityWindowInfoCompat$Api34Impl` preserves 100% of the existing contract: on intact devices, native behavior is unchanged; on defective devices, fatal crashes are safely suppressed with graceful degradation; fatal JVM errors are never swallowed.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Defective OEM Immunity)**:
  * *Given* an application build running on an Android 14 (API 34) device missing `AccessibilityManager.isRequestFromAccessibilityTool()`,
  * *When* Jetpack Compose UI accessibility loops or view delegates invoke `AccessibilityManagerCompat.isRequestFromAccessibilityTool(manager)`,
  * *Then* `Api34Impl` SHALL intercept the missing method via `try ... catch (LinkageError e)`, log a warning, and return `false` cleanly without throwing `NoSuchMethodError`.
* **Criterion 2 (Intact Platform Parity)**:
  * *Given* an intact Android 14/15/16 device with valid platform methods,
  * *When* `isRequestFromAccessibilityTool` is invoked,
  * *Then* the platform method SHALL execute natively and return the real boolean value without interference.
* **Criterion 3 (JVM Safety)**:
  * *Given* a fatal non-linkage error such as `OutOfMemoryError`,
  * *When* invoked,
  * *Then* `Api34Impl` SHALL NOT swallow the error and SHALL allow it to propagate to the JVM uncaught exception handler.
* **Criterion 4 (Build & Toolchain Integrity)**:
  * *Given* any build configuration (Debug, Profile, Release),
  * *When* compiling via `./gradlew assembleRelease`,
  * *Then* compilation and dex merging SHALL complete successfully with zero duplicate class collisions.

### 1.5 System Invariants
1. Native behavior on compliant Android 14+ devices is 100% preserved.
2. Non-linkage JVM errors (`OutOfMemoryError`, `StackOverflowError`) are never caught.
3. Clean dex merging without package spoofing in `app/src/main/java`.

---

## 2. Test Specification (TST-UI-116)

### Test Case 1: `testIsRequestFromAccessibilityTool_whenPlatformMethodThrowsNoSuchMethodError_returnsFalseSafely` (`TST-UI-116.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/accessibility/AccessibilityManagerCompatResilienceTest.kt`
* **Preconditions**: Mock `AccessibilityManager` configured to throw `NoSuchMethodError` on `isRequestFromAccessibilityTool`.
* **Action**: Invoke `AccessibilityManagerCompat$Api34Impl.isRequestFromAccessibilityTool(mockManager)`.
* **Expected Result**: Method returns `false` safely; warning logged with tag `"AccessibilityManagerCompat"`.

### Test Case 2: `testIsRequestFromAccessibilityTool_whenPlatformMethodIntact_invokesSuccessfully` (`TST-UI-116.2`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/accessibility/AccessibilityManagerCompatResilienceTest.kt`
* **Preconditions**: Mock `AccessibilityManager` returning `true`.
* **Action**: Invoke `AccessibilityManagerCompat$Api34Impl.isRequestFromAccessibilityTool(mockManager)`.
* **Expected Result**: Method returns `true`.

### Test Case 3: `testApi34Impl_doesNotSwallowFatalOutOfMemoryError` (`TST-UI-116.3`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/accessibility/AccessibilityManagerCompatResilienceTest.kt`
* **Preconditions**: Mock `AccessibilityManager` throwing `OutOfMemoryError`.
* **Action**: Invoke `AccessibilityManagerCompat$Api34Impl.isRequestFromAccessibilityTool(mockManager)`.
* **Expected Result**: `OutOfMemoryError` is thrown out of the method.

### Test Case 4: `testAccessibilityWindowInfoCompat_missingMethods_handledCleanly` (`TST-UI-116.4`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/accessibility/AccessibilityManagerCompatResilienceTest.kt`
* **Preconditions**: Mock `AccessibilityWindowInfo` throwing `NoSuchMethodError` on `getTransitionTimeMillis` and `getLocales`.
* **Action**: Invoke `AccessibilityWindowInfoCompat$Api34Impl` methods.
* **Expected Result**: `getTransitionTimeMillis` returns `0L`, `getLocales` returns `null`, warnings logged.

### Test Case 5: Clean-Room Regression Suite (`TST-UI-116.5`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite with zero regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-116.1` | Unit | `AccessibilityManagerCompat$Api34Impl.isRequestFromAccessibilityTool` | `REQ-UI-164` | Specified |
| `TST-UI-116.2` | Unit | `AccessibilityManagerCompat$Api34Impl.isRequestFromAccessibilityTool` | `REQ-UI-164` | Specified |
| `TST-UI-116.3` | Unit | `AccessibilityManagerCompat$Api34Impl.isRequestFromAccessibilityTool` | `REQ-UI-164` | Specified |
| `TST-UI-116.4` | Unit | `AccessibilityWindowInfoCompat$Api34Impl` | `REQ-UI-164` | Specified |
| `TST-UI-116.5` | Regression | Full Test Suite (`./gradlew testDebugUnitTest`) | `REQ-PRO-001`, `REQ-UI-164` | Specified |
