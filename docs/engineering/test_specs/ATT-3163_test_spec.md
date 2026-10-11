# Stage 2: Requirement & Test Specification - ATT-3163: Compose Stylus Handwriting NoClassDefFoundError Resilience

**Ticket**: [ATT-3163](https://atrainingtracker.atlassian.net/browse/ATT-3163)  
**Sub-task**: [ATT-3191](https://atrainingtracker.atlassian.net/browse/ATT-3191) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-235](https://atrainingtracker.atlassian.net/browse/ATT-235) (*No crashs*)  
**Target Release**: `V4.9.38.5` (Hotfix)  
**Active Sprint**: `2026-41.9`  
**Requirement Mapping**: `REQ-UI-342` (*Jetpack Compose Text Input Stylus Handwriting NoClassDefFoundError Runtime Interception & Resilience*)  
**Test Spec ID**: `TST-UI-302`  
**Branch**: `feature/ATT-3163`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-11  

---

## 1. Requirement Specification (REQ-UI-342)

### 1.1 Problem Statement & Rationale
In production release `v4.9.38.4` (build 267), Firebase Crashlytics reported fatal crash [ATT-3163](https://atrainingtracker.atlassian.net/browse/ATT-3163) (`NoClassDefFoundError: Failed resolution of: Landroid/view/inputmethod/SelectGesture;` at `androidx.compose.foundation.text.input.internal.EditorInfoApi34.setHandwritingGestures(EditorInfo.android.kt:230)`).
Whenever a user touches or focuses any non-password text input field in Jetpack Compose on fragmented Android 14+ devices where the platform vendor omitted `SelectGesture` from `framework.jar`, Compose's internal `EditorInfo_androidKt.update` invokes `EditorInfoApi34.setHandwritingGestures`, triggering an unhandled `NoClassDefFoundError` that terminates the application process immediately.

### 1.2 Functional & Architectural Requirements
The system SHALL intercept and gracefully neutralize the platform class linkage failure (`NoClassDefFoundError` on `android.view.inputmethod.SelectGesture`) during Jetpack Compose text input focus on fragmented Android 14+ OEM platforms:
1. *Dynamic Runtime Interception (`ComposeHandwritingResilience.kt`)*:
   - At application startup (`TrainingApplication.onCreate()`), the system SHALL invoke `ComposeHandwritingResilience.install()`.
   - On devices reporting `Build.VERSION.SDK_INT >= 34`, `ComposeHandwritingResilience` SHALL verify availability of `android.view.inputmethod.SelectGesture` via `Class.forName()`.
   - If `SelectGesture` cannot be resolved (or throws `ClassNotFoundException` / `NoClassDefFoundError`), `ComposeHandwritingResilience` SHALL update `StylusHandwriting_androidKt.isStylusHandwritingSupported` to `false` using memory reflection/Unsafe.
   - When `isStylusHandwritingSupported` is false, Compose text input initialization (`EditorInfo_androidKt.update`) SHALL evaluate `shouldEnableStylusHandwriting` as `false`, safely skipping `EditorInfoApi34.setHandwritingGestures` and eliminating runtime linkage crashes.
2. *Architectural & Package Boundary Invariants*:
   - The application SHALL NOT inject or shadow stub classes inside the reserved platform `android.*` namespace, preserving Android runtime integrity and classloader isolation.
   - Text input, keyboard input methods (IME), selection, cut/copy/paste, and auto-correct across all Compose text fields in the application SHALL continue to function with zero latency or regression.
3. *Compose Bill of Materials (BOM) Alignment*:
   - The system SHALL align `androidx.compose:compose-bom` to `2026.09.00` in `app/build.gradle` and remove hardcoded version pins on Compose foundation and UI artifacts.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Missing SelectGesture Platform Resilience)**:
  * *Given* an Android 14+ device where `android.view.inputmethod.SelectGesture` is missing from the platform framework,
  * *When* `TrainingApplication.onCreate()` initializes and any non-password text field in Compose (e.g. search bar, workout notes, sensor rename) receives user focus,
  * *Then* `EditorInfoApi34.setHandwritingGestures` is safely bypassed, no `NoClassDefFoundError` is thrown, and the keyboard/IME input connection is successfully established.
* **Criterion 2 (Normal Devices with Full Platform Gestures)**:
  * *Given* an Android 14+ device with full native stylus handwriting support,
  * *When* `ComposeHandwritingResilience.install()` runs,
  * *Then* `SelectGesture` is resolved without error and stylus handwriting gestures remain enabled.
* **Criterion 3 (Safe Memory Reflection & Unit Test Integrity)**:
  * *Given* `ComposeHandwritingResilienceTest.kt`,
  * *When* running unit tests,
  * *Then* safe probe execution, fallback handling, and idempotent state mutation are validated with 100% pass rate.

### 1.4 System Invariants
- **No Platform Namespace Hijacking**: No application-level classes defined under `android.*`.
- **Caller Stability**: All Compose text fields (`OutlinedTextField`, `TextField`, `BasicTextField`) operate without visual or behavioral disruption.
- **Clean-Room Test Suite Pass Rate**: Full unit test suite (`./gradlew testDebugUnitTest`) must pass at 100%.

---

## 2. Test Specification (TST-UI-302)

### Test Case 1: Safe Probe & Flag Mutation (`TST-UI-302.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/common/ComposeHandwritingResilienceTest.kt`
* **Preconditions**: JVM test environment.
* **Action**:
  1. Call `ComposeHandwritingResilience.disableComposeStylusHandwriting()`.
  2. Verify that `StylusHandwriting_androidKt.isStylusHandwritingSupported()` returns `false`.
* **Expected Result**: Method completes successfully and returns `true`, and Compose reports `isStylusHandwritingSupported() == false`.

### Test Case 2: Idempotent & Fault-Tolerant Initialization (`TST-UI-302.2`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/common/ComposeHandwritingResilienceTest.kt`
* **Preconditions**: JVM test environment.
* **Action**: Invoke `ComposeHandwritingResilience.install()` multiple consecutive times.
* **Expected Result**: No exceptions are thrown, and application initialization remains completely robust and idempotent.

### Test Case 3: 9-Language Localization & Specifier Audit (`TST-UI-302.3`)
* **Scope**: Localization Parity Test
* **Goal**: Verify zero missing entries and zero format specifier mismatches across all 9 locales:
  * EN, DE, ES, FR, IT, JA, NL, PL, PT.
* **Expected Result**: 100% parity across all 9 languages (0 new UI strings introduced).

### Test Case 4: Clean-Room Regression Suite (`TST-UI-302.4`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-302.1` | Unit | `ComposeHandwritingResilience.disableComposeStylusHandwriting` | `REQ-UI-342` | Specified |
| `TST-UI-302.2` | Unit | `ComposeHandwritingResilience.install` | `REQ-UI-342` | Specified |
| `TST-UI-302.3` | Localization | `TranslationParityTest` | `REQ-UI-342`, `REQ-UI-106` | Specified |
| `TST-UI-302.4` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
