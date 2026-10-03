# Test Specification - ATT-1347: NoSuchMethodError in AccessibilityEventCompat$Api34Impl.setAccessibilityDataSensitive

**Ticket**: [ATT-1347](https://rainerblind.atlassian.net/browse/ATT-1347)  
**Sub-task**: [ATT-1357](https://rainerblind.atlassian.net/browse/ATT-1357)  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*No crashs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-39.2`  
**Components**:
* `androidx.core:core:1.15.0-patched` (`local-repo/androidx/core/core/1.15.0-patched/`)
* `app/build.gradle` (Dependency Substitution)
* `settings.gradle` (Local Maven Repository)
* `app/src/test/java/com/atrainingtracker/trainingtracker/accessibility/AccessibilityEventCompatResilienceTest.kt`
**Requirement**: `REQ-UI-164` (Accessibility Event Dispatch Compatibility & Defective Platform Resilience)  
**Test Spec ID**: `TST-UI-116`  
**Branch**: `bugfix/ATT-1347`  

---

## 1. Overview & Verification Strategy

This test specification defines the test suite and verification criteria for verifying the complete elimination of `NoSuchMethodError` crashes in `AccessibilityEventCompat$Api34Impl.setAccessibilityDataSensitive` (`ATT-1347`).

The test suite validates:
1. **Defective Platform Simulation (`NoSuchMethodError` Immunity)**: Verifying that invoking `AccessibilityEventCompat.setAccessibilityDataSensitive(event, boolean)` on defective Android 14 runtimes lacking the virtual method safely intercepts `LinkageError`, logs a warning, and returns cleanly without propagating fatal exceptions.
2. **Defective Platform Query (`isAccessibilityDataSensitive` Immunity)**: Verifying that invoking `AccessibilityEventCompat.isAccessibilityDataSensitive(event)` on defective Android 14 runtimes safely intercepts `LinkageError`, logs a warning, and returns `false` without crashing.
3. **Platform Method Parity on Intact Devices**: Verifying that on non-defective Android 14/15/16 devices, `setAccessibilityDataSensitive(boolean)` and `isAccessibilityDataSensitive()` delegate normally to the underlying platform framework without behavioral deviation.
4. **Fatal Error Safety Invariant (Non-Swallowing of Fatal JVM Errors)**: Verifying that the defensive exception handler strictly catches `LinkageError` and does NOT swallow fatal JVM errors (such as `OutOfMemoryError` or `ThreadDeath`), upholding safety invariants.
5. **Dependency Resolution Integrity**: Verifying that Gradle's dependency graph resolves `androidx.core:core` uniformly to `1.15.0-patched` across all compile and runtime configurations.
6. **D8 Dex Merger & Release Build Cleanliness**: Verifying that `./gradlew assembleRelease` executes `mergeDexRelease` and `lintVitalRelease` to completion with exit code 0 and zero duplicate class merger collisions.

---

## 2. Test Cases & Verification Matrix

### Test Case 1: `testSetAccessibilityDataSensitive_whenPlatformMethodThrowsLinkageError_isSuppressedCleanly` (Unit Test)
* **Goal**: Verify that when `event.setAccessibilityDataSensitive(boolean)` throws `NoSuchMethodError` / `LinkageError`, `AccessibilityEventCompat.setAccessibilityDataSensitive` catches the error, logs a diagnostic warning, and returns cleanly.
* **Preconditions**:
  - Test instance of `AccessibilityEvent` configured or subclassed such that invoking `setAccessibilityDataSensitive(boolean)` throws `NoSuchMethodError("No virtual method setAccessibilityDataSensitive(Z)V in class Landroid/view/accessibility/AccessibilityEvent;")`.
* **Action**:
  - Invoke `AccessibilityEventCompat.setAccessibilityDataSensitive(event, true)`.
* **Expected Result**:
  - Zero unhandled exceptions thrown.
  - Operation completes cleanly.
  - Diagnostic warning logged via `android.util.Log.w`.

### Test Case 2: `testIsAccessibilityDataSensitive_whenPlatformMethodThrowsLinkageError_returnsFalseSafely` (Unit Test)
* **Goal**: Verify that when `event.isAccessibilityDataSensitive()` throws `NoSuchMethodError` / `LinkageError`, `AccessibilityEventCompat.isAccessibilityDataSensitive` catches the error, logs a warning, and returns `false`.
* **Preconditions**:
  - Test instance of `AccessibilityEvent` configured such that invoking `isAccessibilityDataSensitive()` throws `NoSuchMethodError`.
* **Action**:
  - Call `val result = AccessibilityEventCompat.isAccessibilityDataSensitive(event)`.
* **Expected Result**:
  - Zero unhandled exceptions thrown.
  - `result == false`.
  - Warning logged via `android.util.Log.w`.

### Test Case 3: `testAccessibilityDataSensitive_whenPlatformMethodIntact_invokesSuccessfully` (Unit Test)
* **Goal**: Verify normal operation on intact platforms where the underlying platform method exists and executes.
* **Preconditions**:
  - Normal `AccessibilityEvent` instance on an intact runtime.
* **Action**:
  - Call `AccessibilityEventCompat.setAccessibilityDataSensitive(event, true)`.
* **Expected Result**:
  - Underlying platform state is updated.
  - No errors logged or thrown.

### Test Case 4: `testApi34Impl_doesNotSwallowFatalOutOfMemoryError` (Unit Test)
* **Goal**: Verify that non-linkage fatal JVM errors (e.g. `OutOfMemoryError`) are NOT caught by `Api34Impl`, preserving JVM stability invariants.
* **Preconditions**:
  - Test harness simulating an `OutOfMemoryError` during event mutation.
* **Action**:
  - Call `AccessibilityEventCompat.setAccessibilityDataSensitive(event, true)`.
* **Expected Result**:
  - `OutOfMemoryError` is NOT caught by `catch (LinkageError e)` and propagates outwards as required by JVM invariants.

### Test Case 5: `testReleaseRuntimeClasspath_resolvesToPatchedCoreArtifact` (Build Verification)
* **Goal**: Verify that Gradle's dependency substitution rule correctly substitutes `androidx.core:core:1.15.0` with `androidx.core:core:1.15.0-patched` across all transitive paths.
* **Action**:
  - Run `./gradlew :app:dependencies --configuration releaseRuntimeClasspath`.
* **Expected Result**:
  - 100% of `androidx.core:core` occurrences resolve to `1.15.0-patched`.
  - Zero resolution conflicts.

### Test Case 6: `testAssembleRelease_mergesDexCleanlyWithoutDuplicateClassErrors` (Integration Build Test)
* **Goal**: Verify that `assembleRelease` with `minifyEnabled = false` succeeds without D8 `DexArchiveMergerException`.
* **Action**:
  - Run `./gradlew assembleRelease`.
* **Expected Result**:
  - Build exits with code 0 (`BUILD SUCCESSFUL`).
  - Tasks `mergeDexRelease`, `lintVitalRelease`, and `packageRelease` pass without errors.

---

## 3. Traceability Matrix

| Requirement | Test Spec ID | Test Case Name / Method | Target File | Type |
|:---|:---|:---|:---|:---|
| `REQ-UI-164` | `TST-UI-116.1` | `testSetAccessibilityDataSensitive_whenPlatformThrowsLinkageError_suppressed` | `AccessibilityEventCompatResilienceTest.kt` | Unit Test |
| `REQ-UI-164` | `TST-UI-116.2` | `testIsAccessibilityDataSensitive_whenPlatformThrowsLinkageError_returnsFalse` | `AccessibilityEventCompatResilienceTest.kt` | Unit Test |
| `REQ-UI-164` | `TST-UI-116.3` | `testAccessibilityDataSensitive_whenPlatformIntact_invokesSuccessfully` | `AccessibilityEventCompatResilienceTest.kt` | Unit Test |
| `REQ-UI-164` | `TST-UI-116.4` | `testApi34Impl_doesNotSwallowFatalOutOfMemoryError` | `AccessibilityEventCompatResilienceTest.kt` | Unit Test |
| `REQ-UI-164` | `TST-UI-116.5` | `testReleaseRuntimeClasspath_resolvesToPatchedCoreArtifact` | Gradle Dependency Audit | Build Test |
| `REQ-UI-164` | `TST-UI-116.6` | `testAssembleRelease_mergesDexCleanlyWithoutDuplicateClassErrors` | `app/build.gradle` | Integration Build |

---

## 4. Acceptance Criteria & Pass/Fail Thresholds

* **Gate 2 Entry Threshold**:
  - 100% of test cases in Section 2 defined with explicit preconditions, actions, and assertions.
  - Zero ambiguous test criteria.
  - Complete mapping to `REQ-UI-164` in `docs/requirements.md`.
* **Gate 5 Release Exit Threshold**:
  - 100% of unit tests passing in `./gradlew testDebugUnitTest`.
  - 100% clean release build via `./gradlew assembleRelease`.
  - Zero regressions across existing test suite (626+ tests).
