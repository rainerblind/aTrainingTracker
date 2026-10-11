# Stage 3: Implementation Plan - ATT-3163: Compose Stylus Handwriting NoClassDefFoundError Resilience

**Ticket**: [ATT-3163](https://atrainingtracker.atlassian.net/browse/ATT-3163)  
**Sub-task**: [ATT-3192](https://atrainingtracker.atlassian.net/browse/ATT-3192) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-235](https://atrainingtracker.atlassian.net/browse/ATT-235) (*No crashs*)  
**Target Release**: `V4.9.38.5` (Hotfix)  
**Active Sprint**: `2026-41.9`  
**Requirement Mapping**: `REQ-UI-342`  
**Test Mapping**: `TST-UI-302`  
**Branch**: `feature/ATT-3163`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-11  

---

## 1. Problem Description & Background

In production release `v4.9.38.4` (build 267), Firebase Crashlytics reported fatal crash [ATT-3163](https://atrainingtracker.atlassian.net/browse/ATT-3163):
```text
Fatal Exception: java.lang.NoClassDefFoundError: Failed resolution of: Landroid/view/inputmethod/SelectGesture;
       at androidx.compose.foundation.text.input.internal.EditorInfoApi34.setHandwritingGestures(EditorInfo.android.kt:230)
       at androidx.compose.foundation.text.input.internal.EditorInfo_androidKt.update-pLxbY9I(EditorInfo.android.kt:185)
       ...
       at androidx.compose.ui.platform.AndroidComposeView.onCreateInputConnection(AndroidComposeView.android.kt:3071)
```

Focusing any non-password text field in Compose prompts `InputMethodManager` to create an `InputConnection`. During this creation, Compose evaluates `shouldEnableStylusHandwriting(imeOptions)` (delegating to `StylusHandwriting_androidKt.isStylusHandwritingSupported`), and invokes `EditorInfoApi34.INSTANCE.setHandwritingGestures(editorInfo)`.
On fragmented Android 14+ OEM platforms where `android.view.inputmethod.SelectGesture` was omitted from `/system/framework/framework.jar`, the runtime linkage attempt causes `NoClassDefFoundError`, terminating the app.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-342` (*Jetpack Compose Text Input Stylus Handwriting NoClassDefFoundError Runtime Interception & Resilience*)
* **Test Mapping**: `TST-UI-302` (*Compose Text Input Stylus Handwriting Interception & Resilience Verification*)

---

## 3. System Invariants & Preserved Behavior

1. **No Platform Namespace Violations**: Do not inject or define classes in the reserved `android.*` namespace, preserving Android runtime integrity and DEX packaging rules.
2. **Caller Stability & Text Input Preservation**: Normal text fields (`OutlinedTextField`, `TextField`, `BasicTextField`) and IME virtual keyboards across all dialogs, search bars, and sheets retain full typing, cut/copy/paste, and auto-correct functionality.
3. **Graceful Degradation**: On fragmented devices missing `SelectGesture`, stylus handwriting gestures are cleanly disabled; on standard devices with full platform APIs, native capabilities remain intact.
4. **Clean-Room Test Pass Rate**: Full unit test suite (`./gradlew testDebugUnitTest`) must pass at 100%.
5. **Subtask Self-Sufficiency**: Subtask transitions directly to `Erledigt` upon passing Gate audit via `freigabe`.
6. **Parent Human Gate Invariance**: Terminal completion of parent ticket remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `ComposeHandwritingResilience` (`app/src/main/java/com/atrainingtracker/trainingtracker/ui/common/ComposeHandwritingResilience.kt`)
* Implements the runtime guard:
  - `install()`: Probes `android.view.inputmethod.SelectGesture` via `Class.forName()`. If missing or throwing, calls `disableComposeStylusHandwriting()`.
  - `disableComposeStylusHandwriting()`: Safely alters `StylusHandwriting_androidKt.isStylusHandwritingSupported` to `false` via reflection with `sun.misc.Unsafe` fallback.
  - When `isStylusHandwritingSupported` is `false`, Compose's `shouldEnableStylusHandwriting` evaluates to `false`, completely skipping `EditorInfoApi34.setHandwritingGestures`.

### Component 2: Application Lifecycle Integration (`TrainingApplication.java`)
* Invoke `ComposeHandwritingResilience.install()` inside `TrainingApplication.onCreate()` prior to UI inflation.

### Component 3: Build & Compose Dependency Alignment (`app/build.gradle`)
* Update `composeBom` to `platform('androidx.compose:compose-bom:2026.09.00')`.
* Remove hardcoded version pins (`:1.12.0`) on `ui`, `runtime-saveable`, and `foundation-layout` to ensure version harmony.

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Create `ComposeHandwritingResilience.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/common/ComposeHandwritingResilience.kt`
* **Action**: Author helper class with `install()` and `disableComposeStylusHandwriting()`, with robust exception handling and logging.

### Step 2: Integrate in `TrainingApplication.java`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/TrainingApplication.java`
* **Action**: Call `ComposeHandwritingResilience.install()` in `onCreate()`.

### Step 3: Align Compose BOM in `app/build.gradle`
* **File**: `app/build.gradle`
* **Action**: Update `composeBom` to `2026.09.00`, remove hardcoded `:1.12.0` pins.

### Step 4: Author Unit Tests (`ComposeHandwritingResilienceTest.kt`)
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/common/ComposeHandwritingResilienceTest.kt`
* **Action**: Verify probe safety, flag mutation via Unsafe/reflection, idempotency, and non-crashing behavior.

### Step 5: Execute Targeted Unit Tests
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.common.ComposeHandwritingResilienceTest"`

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted unit tests in Stage 4, followed by full clean-room suite (`./gradlew testDebugUnitTest`) in Stage 5, plus on-device smoke test.
* **Rollback**: Branch isolation (`feature/ATT-3163`) enables clean revert without affecting `sprint/2026-41.9` or `develop`.
