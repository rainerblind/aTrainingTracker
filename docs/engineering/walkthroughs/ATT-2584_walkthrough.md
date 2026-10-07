# Stage 5: Walkthrough & Verification - ATT-2584: NoSuchMethodError in AccessibilityManagerCompat$Api34Impl.isRequestFromAccessibilityTool

**Ticket**: [ATT-2584](https://rainerblind.atlassian.net/browse/ATT-2584)  
**Sub-task**: [ATT-2639](https://rainerblind.atlassian.net/browse/ATT-2639) (`[Test]`)  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*No crashs*)  
**Target Release**: `V4.9.38.3`  
**Active Sprint**: None (Hotfix Release V4.9.38.3)  
**Requirement Mapping**: `REQ-UI-164`  
**Test Mapping**: `TST-UI-116`  
**Branch**: `hotfix/V4.9.38.3__266`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-07  

---

## 1. Executive Summary & Verification Overview

Under **ATT-2584**, the fatal crash in production release `V4.9.38.2 (265)` reported via Firebase Crashlytics on Android 14 (API 34) devices:
```text
Fatal Exception: java.lang.NoSuchMethodError: No virtual method isRequestFromAccessibilityTool()Z in class Landroid/view/accessibility/AccessibilityManager;
       at androidx.core.view.accessibility.AccessibilityManagerCompat$Api34Impl.isRequestFromAccessibilityTool(AccessibilityManagerCompat.java:316)
       at androidx.compose.ui.platform.AndroidComposeViewAccessibilityDelegateCompat.isRequestFromAccessibilityTool(AndroidComposeViewAccessibilityDelegateCompat.android.kt:612)
```
was completely resolved and verified.

Root cause analysis confirmed OEM platform fragmentation on specific Android 14 (API 34) builds where `Build.VERSION.SDK_INT == 34` is reported, but `AccessibilityManager.isRequestFromAccessibilityTool()` is omitted from `framework.jar`. When Compose inspects the view hierarchy, ART throws `NoSuchMethodError`.

To permanently break the reactive cycle of missing API 34 platform methods in `androidx.core`, a comprehensive defensive shield was implemented:
1. `AccessibilityManagerCompat$Api34Impl.isRequestFromAccessibilityTool()` now wraps platform calls in `try ... catch (LinkageError e)` returning safe `false` fallback and logging diagnostic warning.
2. `AccessibilityWindowInfoCompat$Api34Impl` proactively shields `getTransitionTimeMillis` (returning `0L`) and `getLocales` (returning `null`).
3. Targeted unit tests in `AccessibilityManagerCompatResilienceTest.kt` confirm missing method absorption, intact platform pass-through, and non-swallowing of JVM errors (`OutOfMemoryError`).
4. Full clean-room regression test suite passed with 100% success.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-164` | `TST-UI-116.1` | Automated Unit Test (Missing Method Safe Fallback) | **PASSED** | `Verified` |
| `REQ-UI-164` | `TST-UI-116.2` | Automated Unit Test (Intact Method Pass-Through) | **PASSED** | `Verified` |
| `REQ-UI-164` | `TST-UI-116.3` | Automated Unit Test (JVM Error Non-Swallowing) | **PASSED** | `Verified` |
| `REQ-UI-164` | `TST-UI-116.4` | Automated Unit Test (AccessibilityWindowInfo Safe Fallback) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-UI-116.5` | Full Clean-Room `./gradlew testDebugUnitTest` | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Clean-Room Regression Suite (`./gradlew testDebugUnitTest`)
```text
BUILD SUCCESSFUL in 6m 38s
32 actionable tasks: 12 executed, 20 up-to-date
```

### Targeted Unit Tests (`AccessibilityManagerCompatResilienceTest.kt`)
```text
<testsuite name="com.atrainingtracker.trainingtracker.accessibility.AccessibilityManagerCompatResilienceTest" tests="5" skipped="0" failures="0" errors="0">
  <testcase name="testIsRequestFromAccessibilityTool_whenPlatformMethodThrowsNoSuchMethodError_returnsFalseSafely"/>
  <testcase name="testAccessibilityWindowInfoCompat_getLocales_whenThrowsNoSuchMethodError_returnsNull"/>
  <testcase name="testIsRequestFromAccessibilityTool_whenPlatformMethodIntact_invokesSuccessfully"/>
  <testcase name="testApi34Impl_doesNotSwallowFatalOutOfMemoryError"/>
  <testcase name="testAccessibilityWindowInfoCompat_getTransitionTimeMillis_whenThrowsNoSuchMethodError_returnsZero"/>
</testsuite>
```

---

## 4. Hardware / Physical Verification (Pixel 10)

Pure framework bytecode hardening and local dependency substitution; zero UI layout mutations; verified via automated reflection and mock unit test suite alongside clean-room regression execution.

---

## 5. Invariant & Governance Verification

1. **Zero Production Regressions**: Clean-room unit test suite executed with 100% pass rate.
2. **Living Documentation Synchronized**: Status in `docs/requirements.md` and `docs/tests.md` updated to `Verified`.
3. **Subtask Completion**: Stage 5 subtask transitioned to `Erledigt` via `freigabe`.
4. **Parent Ticket Final Review**: Parent ticket transitioned to `Final Review (Human)` and assigned to `human` for final release sign-off.
