# Stage 2: Requirement & Test Specification - ATT-4431: [Bug] Restore long-press continuous ring ('Brriiiing!') on Cockpit BikeBellButton without breaking single-tap onClick

**Ticket**: [ATT-4431](https://rainerblind.atlassian.net/browse/ATT-4431)  
**Sub-task**: `[Req & Test Spec]` (To be created via `submit-stage`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.3`  
**Requirement Mapping**: `REQ-UI-421` (*Electronic bicycle bell in Cockpit with button, zero-look double-tap gesture, and progressive loudness escalation*), `REQ-UI-421.2`, `REQ-UI-421.4`  
**Test Spec ID**: `TST-UI-384`, `TST-UI-390`  
**Branch**: `feature/ATT-4431`  
**Author**: AI Agent 1 (Implementer - `subagent_flash`)  
**Date**: 2026-10-20  

---

## 1. Requirement Specification (REQ-UI-421, REQ-UI-421.2, REQ-UI-421.4)

### 1.1 Problem Statement & Rationale
During Sprint 2026-43.1, `ATT-4281` introduced an electronic bicycle bell into the Cockpit overlay bar (`BikeBellButton`, `BikeBellAudioSynthesizer`). During Sprint 2026-43.2 (`ATT-4371`), `BikeBellButton` was updated to resolve pointer event contention by removing a nested `Modifier.pointerInput { detectTapGestures(...) }` on `ElevatedButton`. While removing the competing modifier restored reliable single-tap operation (`ElevatedButton(onClick)`), it inadvertently eliminated long-press detection, leaving `onContinuousRingStart()` and `onContinuousRingStop()` orphaned and never called. Consequently, holding the button down no longer triggers the continuous striker roll (*"Brriiiing!"*).

Additionally, `BikeBellAudioSynthesizer.startRoll()` currently triggers `track.play()` without configuring static track loop points (`AudioTrack.setLoopPoints`), which causes playback to stop abruptly after 2.5 seconds (`ROLL_DURATION_SEC = 2.5`) instead of sustaining indefinitely for the duration of the athlete's hold.

Under Governance Rules 53 and 58 (`docs/design_guidelines.md` §5.12), auxiliary cockpit controls with dual single-tap and press-and-hold gestures must monitor press interactions on `MutableInteractionSource` (or use dedicated non-competing clickables) rather than deleting gestures or nesting competing pointer detectors. This specification formalizes the requirements and test verification procedures to restore continuous long-press ringing with looping audio and continuous animation while strictly preserving snappy, contention-free single taps.

---

### 1.2 Functional & Architectural Requirements

#### 1.2.1 `BikeBellButton` MutableInteractionSource Press Monitoring & Disambiguation (`REQ-UI-421.2`, Rules 53 & 58)
1. `BikeBellButton` SHALL instantiate and remember a `MutableInteractionSource` and pass it to `ElevatedButton(interactionSource = interactionSource)`.
2. `BikeBellButton` SHALL NOT attach `Modifier.pointerInput` or `detectTapGestures` to `ElevatedButton` or its enclosing containers (Rules 53 & 58).
3. In a `LaunchedEffect(interactionSource, isAvailable)`, `BikeBellButton` SHALL collect `interactionSource.interactions`:
   - On `PressInteraction.Press`: reset disambiguation state (`wasLongPressHandled = false`) and launch a cancellable delay job awaiting `LocalViewConfiguration.current.longPressTimeoutMillis` (~400 ms).
   - Upon timer expiry (long-press detected): set `wasLongPressHandled = true`, set `isContinuousRingActive = true`, trigger long-press haptic feedback (`HapticFeedbackType.LongPress`), invoke `onContinuousRingStart()`, and launch a continuous looping bell shake animation while held.
   - On `PressInteraction.Release`: cancel the pending press job. If `isContinuousRingActive == true`, set `isContinuousRingActive = false`, invoke `onContinuousRingStop()`, and animate rotation back to $0^\circ$.
   - On `PressInteraction.Cancel` (e.g. finger dragged off button bounds): cancel the pending press job. If `isContinuousRingActive == true`, set `isContinuousRingActive = false`, invoke `onContinuousRingStop()`, and animate rotation back to $0^\circ$.
4. In `ElevatedButton(onClick)`:
   - When `wasLongPressHandled == true`: the click handler SHALL reset `wasLongPressHandled = false` and suppress invocation of `onSingleRing()`, preventing spurious single-strike chimes upon finger release after a long press.
   - When `wasLongPressHandled == false` (short tap $\le 400\text{ ms}$): the click handler SHALL trigger the one-shot shake animation, dispatch haptic feedback, and invoke `onSingleRing()`.
5. Availability Invariant (Rule 53): `BikeBellButton` SHALL evaluate `isAvailable = trackingMode in setOf(TrackingMode.READY, TrackingMode.TRACKING, TrackingMode.PAUSED)`, remaining enabled across these modes and disabled in `TrackingMode.IDLE`. If `isAvailable` transitions to `false` while a continuous ring is active, `onContinuousRingStop()` SHALL be invoked immediately and animation reset.
6. Guaranteed Teardown: `BikeBellButton` SHALL attach a `DisposableEffect(Unit)` ensuring that if the button is removed from composition (e.g. tab switch or configuration mode) while `isContinuousRingActive == true`, `onContinuousRingStop()` is invoked and state is cleaned up.

#### 1.2.2 Looping Striker Roll Audio in `BikeBellAudioSynthesizer` (`REQ-UI-421.4`)
1. In `BikeBellAudioSynthesizer.kt`, `startRoll(volume)` SHALL configure looping on the static `rollTrack`:
   - Stop any existing playback (`track.stop()`) and reset position (`track.playbackHeadPosition = 0`).
   - Calculate total frame count: `totalFrames = rollPcm.size / 2` (16-bit signed mono PCM = 2 bytes per frame).
   - Configure indefinite looping via `track.setLoopPoints(0, totalFrames, -1)`.
   - Set clamped volume and invoke `track.play()`.
2. `stopRoll()` SHALL stop playback (`rollTrack?.stop()`) and reset playback head position to 0 (`rollTrack?.playbackHeadPosition = 0`).
3. Audio stream routing to `AudioAttributes.USAGE_MEDIA` with `AudioAttributes.CONTENT_TYPE_SONIFICATION` SHALL remain strictly active so roll playback remains audible on Silent/Vibrate devices without audio focus ducking.
4. Concurrency Safety: All public operations (`ring()`, `startRoll()`, `stopRoll()`, `close()`) SHALL remain thread-safe (`synchronized(lock)`).

#### 1.2.3 Visual Shake Animation Lifecycle (`REQ-UI-421.2`)
1. Single Tap: Triggers a single 240 ms wobble cycle ($0^\circ \to -15^\circ \to +15^\circ \to -10^\circ \to +10^\circ \to 0^\circ$).
2. Continuous Hold: While `isContinuousRingActive == true`, `Animatable(rotation)` loops continuously through the oscillation cycle until released or cancelled.
3. Release / Cancel: Animates cleanly back to resting angle $0^\circ$.

---

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-421.2` (*Floating Cockpit Bell Button*) and `REQ-UI-421.4` (*Acoustic Brass Synthesis, Concurrency Safety & Audio Focus*), targeting `BikeBellButton.kt`, `BikeBellAudioSynthesizer.kt`, `BikeBellButtonContractTest.kt`, and `BikeBellAudioSynthesizerTest.kt`.
* **Historical Origin & Commit Trace**: `ATT-4281` (`40af18141387`) introduced bike bell with `detectTapGestures` nested on `ElevatedButton`. `ATT-4371` (`bac64ff9`) removed `detectTapGestures` to eliminate single-tap click contention, which inadvertently orphaned `onContinuousRingStart()` and `onContinuousRingStop()`. Sprint 2026-43.2 Joint Review filed `ATT-4431` to restore press-and-hold continuous ring without breaking single tap.
* **Root Reason for Existing Formulation**: In `ATT-4371`, `detectTapGestures` was excised because Compose clickable and pointerInput competed for down/up events, swallowing clicks. The developer did not implement `MutableInteractionSource` press monitoring, leaving long-press unhandled and `rollTrack` unlooped in `BikeBellAudioSynthesizer`.
* **Preservation of Core Invariants**: Rules 53 and 58 compliance (zero pointer contention on `ElevatedButton`); reliable single tap on short press; guaranteed teardown on release/cancel/unmount; multi-mode availability across `READY`, `TRACKING`, and `PAUSED`; thread safety in `BikeBellAudioSynthesizer`; 100% clean-room test suite pass rate strictly maintained.

---

### 1.4 Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Contention-Free Single Tap)**:
  * *Given* `BikeBellButton` in `READY`, `TRACKING`, or `PAUSED` mode,
  * *When* the athlete taps the button briefly ($< 400\text{ ms}$),
  * *Then* `onSingleRing()` is invoked, a one-shot shake animation plays, and polite chime sounds without missed clicks.

* **Criterion 2 (Press-and-Hold Continuous Striker Roll)**:
  * *Given* `BikeBellButton` in `READY`, `TRACKING`, or `PAUSED` mode,
  * *When* the athlete presses and holds the button for $\ge 400\text{ ms}$,
  * *Then* `onContinuousRingStart()` is invoked, continuous shake animation begins, and `BikeBellAudioSynthesizer.startRoll()` loops the *"Brriiiing!"* roll indefinitely.

* **Criterion 3 (Clean Release & Single-Ring Suppression)**:
  * *Given* an active continuous striker roll on `BikeBellButton`,
  * *When* the athlete releases their finger,
  * *Then* `onContinuousRingStop()` is invoked, audio playback stops immediately, the shake animation resets to $0^\circ$, and `onSingleRing()` is NOT invoked.

* **Criterion 4 (Touch Cancellation & Drag-Off Handling)**:
  * *Given* the athlete holds `BikeBellButton` and drags their finger outside the button bounds,
  * *When* `PressInteraction.Cancel` is dispatched,
  * *Then* `onContinuousRingStop()` is invoked, audio roll stops, and rotation resets to $0^\circ$.

* **Criterion 5 (Mode Change or Unmount During Active Hold)**:
  * *Given* an active continuous striker roll on `BikeBellButton`,
  * *When* the tracking mode transitions to `IDLE` or the composable leaves the composition,
  * *Then* `onContinuousRingStop()` is invoked via `DisposableEffect` and synthesizer resources are safely released.

* **Criterion 6 (Indefinite Audio Looping in Synthesizer)**:
  * *Given* `BikeBellAudioSynthesizer.startRoll(1.0f)`,
  * *When* invoked,
  * *Then* `track.setLoopPoints(0, totalFrames, -1)` is executed and the track plays continuously beyond 2.5 seconds until `stopRoll()` is called.

* **Criterion 7 (Rule 53 & Rule 58 Zero Gesture Contention)**:
  * *Given* `BikeBellButton.kt`,
  * *When* static code and contract tests evaluate its structure,
  * *Then* zero instances of `pointerInput` or `detectTapGestures` exist on `ElevatedButton`.

---

### 1.5 System Invariants
1. **Zero UI Contention**: Pointer events route cleanly through Compose interaction pipelines without swallowing clicks.
2. **Guaranteed Teardown**: Continuous audio never continues playing as an orphaned background loop.
3. **Multi-Mode Gating**: Availability across `READY`, `TRACKING`, and `PAUSED` modes is strictly preserved.
4. **Thread Safety**: All native `AudioTrack` operations remain synchronized under `lock`.
5. **Clean Architecture**: Call sites (`TrackingTabsScreen.kt`, `TrackingTabsViewModel.kt`) require zero interface alterations.

---

## 2. Test Specification (TST-UI-384, TST-UI-390)

### Test Case 1: `testBikeBellButton_interactionSourcePressMonitoring_longPressContinuousRing` (`[TST-UI-390.1]`)
* **Scope**: Contract & Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/BikeBellButtonContractTest.kt`
* **Preconditions**: `BikeBellButton.kt` source file exists.
* **Action**: Audit source code and contract assertions for:
  - `remember { MutableInteractionSource() }` passed to `ElevatedButton`.
  - `LaunchedEffect(interactionSource, isAvailable)` collecting `PressInteraction.Press`, `Release`, and `Cancel`.
  - Timer threshold using `LocalViewConfiguration.current.longPressTimeoutMillis`.
  - `onContinuousRingStart()` invocation and looping shake animation on long press.
* **Expected Result**: Assertions pass confirming press-and-hold implementation.

### Test Case 2: `testBikeBellButton_pressReleaseAndCancelTeardown_stopsContinuousRing` (`[TST-UI-390.2]`)
* **Scope**: Contract & Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/BikeBellButtonContractTest.kt`
* **Preconditions**: `BikeBellButton.kt` source file exists.
* **Action**: Audit source code for:
  - `PressInteraction.Release` invoking `onContinuousRingStop()`.
  - `PressInteraction.Cancel` invoking `onContinuousRingStop()`.
  - `DisposableEffect` invoking `onContinuousRingStop()`.
* **Expected Result**: Clean teardown verified across all release, cancellation, and disposal pathways.

### Test Case 3: `testBikeBellButton_singleTapDisambiguation_suppressesSingleRingAfterLongPress` (`[TST-UI-390.3]`)
* **Scope**: Contract & Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/BikeBellButtonContractTest.kt`
* **Preconditions**: `BikeBellButton.kt` source file exists.
* **Action**: Verify `wasLongPressHandled` disambiguation logic:
  - Short tap executes `onSingleRing()`.
  - Release after long-press resets flag without calling `onSingleRing()`.
* **Expected Result**: Disambiguation logic cleanly separates tap from long press.

### Test Case 4: `testBikeBellButton_zeroGestureContention_rule53AndRule58Compliance` (`[TST-UI-390.4]`)
* **Scope**: Architectural Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/BikeBellButtonContractTest.kt`
* **Preconditions**: `BikeBellButton.kt` exists.
* **Action**: Scan file content for forbidden modifier combinations:
  - Assert `content.contains("pointerInput")` is `false`.
  - Assert `content.contains("detectTapGestures")` is `false`.
  - Assert `isAvailable` evaluates `READY`, `TRACKING`, and `PAUSED`.
* **Expected Result**: 100% compliance with Rules 53 and 58.

### Test Case 5: `testBikeBellAudioSynthesizer_startRoll_configuresSetLoopPoints` (`[TST-UI-390.5]`)
* **Scope**: Unit Test (Robolectric)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/audio/BikeBellAudioSynthesizerTest.kt`
* **Preconditions**: Mock `AudioTrack` instantiated via factory.
* **Action**: Invoke `synthesizer.startRoll(1.0f)` and `synthesizer.stopRoll()`.
* **Expected Result**:
  - `verify(exactly = 1) { mockTrack2.setLoopPoints(0, 110250, -1) }`.
  - `verify(atLeast = 1) { mockTrack2.play() }`.
  - `verify(atLeast = 1) { mockTrack2.stop() }`.

### Test Case 6: `testBikeBellAudioSynthesizer_threadSafetyAndResourceTeardown` (`[TST-UI-390.6]`)
* **Scope**: Concurrency & Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/audio/BikeBellAudioSynthesizerTest.kt`
* **Preconditions**: 10 concurrent coroutine threads.
* **Action**: Dispatch 50 interleaved calls across `ring()`, `startRoll()`, `stopRoll()`, and `close()`.
* **Expected Result**: Zero race conditions, deadlocks, or unhandled exceptions; clean idempotent closure.

### Test Case 7: 9-Language Localization & Format Specifier Audit (`[TST-UI-390.7]`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/TranslationParityTest.kt`
* **Goal**: Verify string resources `config_tracking__showBikeBell` and `config_tracking__showBikeBell_subtitle` exist with zero missing keys across all 9 canonical locales: EN, DE, ES, FR, IT, JA, NL, PL, PT.
* **Expected Result**: 100% parity across all locales.

### Test Case 8: Clean-Room Full Regression Suite Execution (`[TST-UI-390.8]`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full application test suite without regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-390.1]` | Contract/Unit | `BikeBellButton` (interactionSource collection) | `REQ-UI-421.2` | Specified |
| `[TST-UI-390.2]` | Contract/Unit | `BikeBellButton` (release & teardown) | `REQ-UI-421.2` | Specified |
| `[TST-UI-390.3]` | Contract/Unit | `BikeBellButton` (single-tap disambiguation) | `REQ-UI-421.2` | Specified |
| `[TST-UI-390.4]` | Contract | `BikeBellButtonContractTest` (Rules 53 & 58) | `REQ-UI-421.2` | Specified |
| `[TST-UI-390.5]` | Unit | `BikeBellAudioSynthesizer.startRoll` (setLoopPoints) | `REQ-UI-421.4` | Specified |
| `[TST-UI-390.6]` | Concurrency | `BikeBellAudioSynthesizer` (thread safety) | `REQ-UI-421.4` | Specified |
| `[TST-UI-390.7]` | Localization | `TranslationParityTest` | `REQ-UI-421.6`, `REQ-UI-106` | Specified |
| `[TST-UI-390.8]` | Regression | `./gradlew testDebugUnitTest` | `REQ-UI-421.7`, `REQ-PRO-001` | Specified |
