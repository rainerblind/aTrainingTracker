# Stage 3: Implementation Plan - ATT-4431: [Bug] Restore long-press continuous ring ('Brriiiing!') on Cockpit BikeBellButton without breaking single-tap onClick

**Ticket**: [ATT-4431](https://rainerblind.atlassian.net/browse/ATT-4431)  
**Sub-task**: [ATT-4441](https://rainerblind.atlassian.net/browse/ATT-4441) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.3`  
**Requirement Mapping**: `REQ-UI-421` (`REQ-UI-421.2`, `REQ-UI-421.4`, `REQ-UI-421.7`)  
**Test Mapping**: `TST-UI-384`, `TST-UI-390` (`TST-UI-390.1` .. `TST-UI-390.8`)  
**Branch**: `feature/ATT-4431`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`)  
**Date**: 2026-10-20  

---

## 1. Problem Description & Background

In Sprint 2026-43.1, `ATT-4281` introduced an electronic bicycle bell feature to the Cockpit overlay bar (`BikeBellButton`, `BikeBellAudioSynthesizer`). During the Sprint 2026-43.2 Joint Review, `ATT-4371` resolved several defects: routing audio to `AudioAttributes.USAGE_MEDIA`, enforcing static `AudioTrack` stop-before-reset cycles, and enabling availability across `READY`, `TRACKING`, and `PAUSED` modes (Governance Rule 53).

However, to resolve single-tap pointer event contention where clicks were being swallowed, `ATT-4371` deleted the outer `Modifier.pointerInput { detectTapGestures(...) }` on `ElevatedButton`. While removing the competing modifier restored single-tap reliability (`ElevatedButton(onClick)`), it inadvertently eliminated long-press detection, leaving `onContinuousRingStart()` and `onContinuousRingStop()` orphaned and never called. Consequently, holding down `BikeBellButton` no longer triggers the continuous striker roll (*"Brriiiing!"*).

Additionally, `BikeBellAudioSynthesizer.startRoll()` triggers `track.play()` without configuring static track looping (`AudioTrack.setLoopPoints`), causing playback to stop abruptly after 2.5 seconds (`ROLL_DURATION_SEC = 2.5`) instead of sustaining indefinitely for the duration of the athlete's press.

Under Governance Rules 53 and 58 (`docs/design_guidelines.md` §5.12), buttons supporting both single-tap and press-and-hold gestures must monitor `PressInteraction.Press`, `Release`, and `Cancel` on `MutableInteractionSource` rather than deleting long-press or nesting competing pointer modifiers. This plan formalizes the exact atomic steps to restore continuous long-press ringing with infinite audio looping and continuous shake animation while strictly preserving snappy, contention-free single taps.

---

## 2. Traceability & Requirements Mapping

* **Requirements**:
  * `REQ-UI-421.2`: Floating Cockpit Bell Button with contention-free single tap (*"Ding-Ding!"*) and long-press continuous striker roll (*"Brriiiing!"*) via `MutableInteractionSource` press monitoring, continuous shake animation, and guaranteed teardown on release/cancel/unmount.
  * `REQ-UI-421.4`: Pure Kotlin in-memory 16-bit PCM mono acoustic synthesis with `AudioAttributes.USAGE_MEDIA`, thread safety (`synchronized(lock)`), and infinite static track looping via `setLoopPoints(0, totalFrames, -1)` during roll playback.
  * `REQ-UI-421.7`: Preservation of system invariants, multi-mode availability across `READY`, `TRACKING`, and `PAUSED` modes, zero pointer contention (Rules 53 & 58), and 100% clean-room test suite pass rate.
* **Test Specifications**:
  * `TST-UI-390.1`: `BikeBellButton` interactionSource press-and-hold & continuous shake animation unit test.
  * `TST-UI-390.2`: `BikeBellButton` release, cancellation & unmount teardown unit test.
  * `TST-UI-390.3`: Single-tap disambiguation & spurious chime suppression unit test.
  * `TST-UI-390.4`: Cockpit zero gesture contention & multi-mode gating contract test (Rules 53 & 58).
  * `TST-UI-390.5`: `BikeBellAudioSynthesizer` looping roll `setLoopPoints` unit test.
  * `TST-UI-390.6`: `BikeBellAudioSynthesizer` concurrency safety & lifecycle teardown test.
  * `TST-UI-390.7`: 9-language localization & string specifier parity audit.
  * `TST-UI-390.8`: Clean-room full regression suite execution (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Zero UI Contention (Rules 53 & 58)**: No `Modifier.pointerInput` or `detectTapGestures` attached to `ElevatedButton`; single taps and press-and-hold gestures route cleanly through Compose `MutableInteractionSource` and `onClick`.
2. **Snappy Single Tap**: Brief taps ($< 400\text{ ms}$) trigger `onSingleRing()` immediately with a one-shot 240 ms wobble animation and polite chime (70% volume), without double clicks or delayed execution.
3. **Spurious Strike Suppression**: Releasing a long-press roll does not trigger an accidental single-tap chime.
4. **Guaranteed Audio Teardown**: Continuous roll audio is stopped unconditionally when released, cancelled (dragged off bounds), disabled (`!isAvailable`), or disposed (`DisposableEffect`).
5. **Multi-Mode Gating (Rule 53)**: `BikeBellButton` remains enabled across `READY`, `TRACKING`, and `PAUSED` modes, and disabled in `IDLE`.
6. **Thread Safety**: All native `AudioTrack` operations in `BikeBellAudioSynthesizer` remain synchronized under `lock`.
7. **Zero Regression on Callers**: `TrackingTabsScreen.kt` and `TrackingTabsViewModel.kt` call signatures and interfaces remain 100% unmodified.
8. **Subtask & Parent Decision Gate Invariance**: Subtasks close directly via `gate-verdict ... PASS`; parent ticket advances to `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `BikeBellAudioSynthesizer.kt` (`Audio` Layer)
* In `startRoll(volume: Float = 1.0f)`:
  - Keep synchronization under `synchronized(lock)`.
  - Calculate total frame count from `rollPcm`: `val totalFrames = rollPcm.size / 2` (16-bit signed mono PCM = 2 bytes per frame; 110,250 frames for 2.5s at 44.1 kHz).
  - Stop any existing playback and reset position: `track.stop()`, `track.playbackHeadPosition = 0`.
  - Configure infinite looping on the static buffer: `track.setLoopPoints(0, totalFrames, -1)`.
  - Set volume and call `track.play()`.
* In `stopRoll()`:
  - Stop playback and reset head position: `rollTrack?.stop()`, `rollTrack?.playbackHeadPosition = 0`.

### Component 2: `BikeBellButton.kt` (`Compose UI` Layer)
* Retain `ElevatedButton` and pass a remembered `MutableInteractionSource`:
  ```kotlin
  val interactionSource = remember { MutableInteractionSource() }
  ```
* Introduce press-state flags and configuration:
  ```kotlin
  val viewConfiguration = LocalViewConfiguration.current
  var isContinuousRingActive by remember { mutableStateOf(false) }
  var wasLongPressHandled by remember { mutableStateOf(false) }
  ```
* Monitor `interactionSource.interactions` in `LaunchedEffect(interactionSource, isAvailable)`:
  - On `PressInteraction.Press`: if `isAvailable`, reset `wasLongPressHandled = false` and launch a cancellable delay job for `viewConfiguration.longPressTimeoutMillis` (~400 ms).
  - On timer expiry: set `wasLongPressHandled = true`, `isContinuousRingActive = true`, perform long-press haptic feedback (`HapticFeedbackType.LongPress`), invoke `onContinuousRingStart()`, and execute continuous looping oscillation in `rotation.animateTo(...)` until cancelled.
  - On `PressInteraction.Release`: cancel the timer job. If `isContinuousRingActive == true`, set `isContinuousRingActive = false`, invoke `onContinuousRingStop()`, and reset rotation cleanly to $0^\circ$.
  - On `PressInteraction.Cancel`: cancel the timer job. If `isContinuousRingActive == true`, set `isContinuousRingActive = false`, invoke `onContinuousRingStop()`, and reset rotation to $0^\circ$.
* Disambiguate in `ElevatedButton(onClick)`:
  - If `wasLongPressHandled == true`: consume and reset flag (`wasLongPressHandled = false`) and do NOT invoke `onSingleRing()`.
  - If `wasLongPressHandled == false`: execute `triggerShakeAnimation()`, perform haptic feedback, and invoke `onSingleRing()`.
* Add `DisposableEffect(Unit)`:
  - In `onDispose`, if `isContinuousRingActive == true`, set `isContinuousRingActive = false` and invoke `onContinuousRingStop()`.

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
* **Reference screen / component**: `LapButton.kt` (`ElevatedButton` floating cockpit overlay button).
* **Reused components**: Material 3 `ElevatedButton`, `Icon` (`Icons.Default.Notifications`), `Row`, `RoundedCornerShape(12.dp)`.
* **Theme tokens**:
  - Dimensions: Height `72.dp`, icon size `40.dp`, padding `horizontal = 24.dp`, rounded corner `12.dp`.
  - Colors: Active container `MaterialTheme.colorScheme.primaryContainer`, content `MaterialTheme.colorScheme.onPrimaryContainer`; Disabled container `colorResource(R.color.lap_button_disabled_background)`, content `colorResource(R.color.lap_button_disabled_text)`.
  - Elevation: `defaultElevation = if (isAvailable) 6.dp else 0.dp`, `disabledElevation = 0.dp`.
* **New one-off styles & justification**: None. Reuses exact Material 3 tokens matching Cockpit design standards.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Infinite Looping in `BikeBellAudioSynthesizer.kt`
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/audio/BikeBellAudioSynthesizer.kt`
* **Changes**:
  1. In `startRoll()`, calculate `val totalFrames = rollPcm.size / 2`.
  2. Invoke `track.setLoopPoints(0, totalFrames, -1)` before `track.play()`.
  3. Ensure `stopRoll()` cleanly invokes `track.stop()` and `track.playbackHeadPosition = 0`.
* **Verification Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.audio.BikeBellAudioSynthesizerTest"
  ```

### Step 2: Update Synthesizer Unit Tests for Looping Verification (`TST-UI-390.5`, `TST-UI-390.6`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/audio/BikeBellAudioSynthesizerTest.kt`
* **Changes**:
  1. In `testBikeBellAudioSynthesizer_pcmSynthesisAndThreadSafeLifecycle`:
     - Add `verify(exactly = 1) { mockTrack2.setLoopPoints(0, 110250, -1) }`.
  2. Add dedicated test `testBikeBellAudioSynthesizer_startRoll_configuresSetLoopPoints` verifying loop points, position reset, volume, and clean teardown.
* **Verification Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.audio.BikeBellAudioSynthesizerTest"
  ```

### Step 3: Implement `MutableInteractionSource` Press Monitoring & Continuous Shake in `BikeBellButton.kt`
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/BikeBellButton.kt`
* **Changes**:
  1. Instantiate `val interactionSource = remember { MutableInteractionSource() }`.
  2. Pass `interactionSource = interactionSource` to `ElevatedButton`.
  3. Add `val viewConfiguration = LocalViewConfiguration.current`.
  4. Track `var isContinuousRingActive by remember { mutableStateOf(false) }` and `var wasLongPressHandled by remember { mutableStateOf(false) }`.
  5. In `LaunchedEffect(interactionSource, isAvailable)`:
     - On `PressInteraction.Press`: reset `wasLongPressHandled = false`, launch `delay(viewConfiguration.longPressTimeoutMillis)`. On timeout: set `wasLongPressHandled = true`, `isContinuousRingActive = true`, perform haptic feedback, invoke `onContinuousRingStart()`, and run continuous shake animation loop.
     - On `PressInteraction.Release`: cancel press job, invoke `onContinuousRingStop()` if active, reset rotation to 0.
     - On `PressInteraction.Cancel`: cancel press job, invoke `onContinuousRingStop()` if active, reset rotation to 0.
  6. In `onClick`:
     - Check `if (wasLongPressHandled) { wasLongPressHandled = false } else { triggerShakeAnimation(); haptic...; onSingleRing() }`.
  7. Add `DisposableEffect(Unit)` invoking `onContinuousRingStop()` on unmount if active.
* **Verification Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.BikeBellButtonContractTest"
  ```

### Step 4: Expand Contract Tests in `BikeBellButtonContractTest.kt` (`TST-UI-390.1` .. `TST-UI-390.4`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/BikeBellButtonContractTest.kt`
* **Changes**:
  1. Verify `BikeBellButton.kt` instantiates and passes `interactionSource` to `ElevatedButton`.
  2. Verify `LaunchedEffect(interactionSource, isAvailable)` collects `PressInteraction.Press`, `Release`, and `Cancel`.
  3. Verify `onContinuousRingStart()` and `onContinuousRingStop()` are wired and called.
  4. Verify `LocalViewConfiguration.current.longPressTimeoutMillis` is referenced.
  5. Verify `wasLongPressHandled` disambiguation suppresses `onSingleRing()` on release after long-press.
  6. Verify `DisposableEffect` teardown.
  7. Preserve existing assertions verifying zero `pointerInput` / `detectTapGestures` on `ElevatedButton` (Rules 53 & 58) and multi-mode availability.
* **Verification Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.BikeBellButtonContractTest"
  ```

### Step 5: Full Clean-Room Regression Verification (`TST-UI-390.8`)
* **Verification Command**:
  ```bash
  ./gradlew testDebugUnitTest
  ```
* **Success Criteria**: 100% test pass rate with zero failures.

---

## 6. Verification & Rollback Plan

### Verification:
1. Targeted unit tests executed at each atomic construction step.
2. Full clean-room test suite run (`./gradlew testDebugUnitTest`) during Stage 5.
3. Code governance check: `python3 tools/verify_requirement_governance.py`.
4. Visual review of Compose preview `@Preview fun PreviewBikeBellButton()`.

### Rollback:
* All work is isolated on git branch `feature/ATT-4431`.
* In the event of catastrophic failure or unresolvable test regressions, the branch can be cleanly reset to the base commit `e7b34e7e` via `git checkout -f feature/ATT-4431 && git reset --hard e7b34e7e`.
