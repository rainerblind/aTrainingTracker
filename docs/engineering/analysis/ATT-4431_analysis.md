# Stage 1 Analysis: ATT-4431 - [Bug] Restore long-press continuous ring ('Brriiiing!') on Cockpit BikeBellButton without breaking single-tap onClick

**Ticket**: [ATT-4431](https://rainerblind.atlassian.net/browse/ATT-4431)  
**Sub-task**: [ATT-4439](https://rainerblind.atlassian.net/browse/ATT-4439) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.3`  
**Branch**: `feature/ATT-4431`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`)  
**Date**: 2026-10-20  

---

## 1. Problem Statement & Motivation

During Sprint 2026-43.1, `ATT-4281` introduced an electronic bicycle bell feature to the Cockpit (`BikeBellButton`, `BikeBellAudioSynthesizer`, and `SensorGridScreen` double-tap gesture). During the Sprint 2026-43.2 Joint Review, `ATT-4371` resolved several critical defects:
1. Routing audio to `AudioAttributes.USAGE_MEDIA` so the bell remains audible when devices are set to Silent or Vibrate mode.
2. Managing static `AudioTrack` replay lifecycles (`track.stop()` prior to `track.playbackHeadPosition = 0`) to prevent `ERROR_INVALID_OPERATION` (-38).
3. Expanding button availability across `READY`, `TRACKING`, and `PAUSED` modes (Governance Rule 53).

However, to resolve single-tap pointer event contention where clicks were being swallowed, `ATT-4371` removed the nested `Modifier.pointerInput { detectTapGestures(...) }` on `ElevatedButton`. While this restored single-tap reliability, it inadvertently orphaned the `onContinuousRingStart()` and `onContinuousRingStop()` callbacks. The press-and-hold continuous striker roll (*"Brriiiing!"*) on `BikeBellButton` no longer functions.

Furthermore, `BikeBellAudioSynthesizer.startRoll()` does not configure static track looping (`setLoopPoints`), which means that even when continuous roll playback is initiated, audio output stops abruptly after 2.5 seconds (`ROLL_DURATION_SEC = 2.5`) rather than sustaining continuously while the cyclist holds the button down.

The goal of `ATT-4431` is to restore the press-and-hold continuous ring on `BikeBellButton` with continuous visual shake animation and guaranteed stop lifecycle handling, while strictly preserving contention-free single-tap operation (`onClick`) and enabling continuous looping in `BikeBellAudioSynthesizer`.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Pointer Event Contention and Subsequent Over-Correction (ATT-4371)
In the initial `ATT-4281` implementation, `BikeBellButton` attempted to handle gestures by attaching `.pointerInput(isTracking) { detectTapGestures(...) }` to the `ElevatedButton`'s modifier:
```kotlin
ElevatedButton(
    onClick = { ... },
    modifier = modifier.pointerInput(isTracking) {
        detectTapGestures(
            onPress = { ... },
            onTap = { ... },
            onLongPress = { ... }
        )
    }
)
```
Material 3's `ElevatedButton` internally applies its own `Modifier.clickable`, which also consumes pointer input during the Main pass. When `detectTapGestures` was nested at the outer level, both gesture detectors competed for down/up pointer events, causing single taps to be swallowed or inconsistently dispatched.

In `ATT-4371`, the developer resolved this contention by deleting `pointerInput` and `detectTapGestures` completely and routing single taps exclusively through `ElevatedButton(onClick)`. While this fixed single taps, it completely removed the invocation of `onContinuousRingStart()` and `onContinuousRingStop()`, leaving the parameters unused in `BikeBellButton.kt`.

### 2.2 Lack of Looping in `BikeBellAudioSynthesizer`
`BikeBellAudioSynthesizer` synthesizes a 2.5-second PCM mono buffer for rolls (`rollPcm`). The static `AudioTrack` (`rollTrack`) is created via `AudioTrack.MODE_STATIC`. In `startRoll()`, the track is simply started with `track.play()` without configuring loop points:
```kotlin
fun startRoll(volume: Float = 1.0f) {
    ...
    track.stop()
    track.playbackHeadPosition = 0
    track.play()
}
```
In Android's `AudioTrack.MODE_STATIC`, an unlooped track plays until the buffer end and transitions to an idle/stopped state. If a cyclist holds the bell button for 3 to 10 seconds (e.g. warning pedestrians or approaching an intersection), the sound cuts off after 2.5 seconds. For continuous playback, `AudioTrack.setLoopPoints(0, totalFrames, -1)` must be configured so playback loops seamlessly until `stopRoll()` is explicitly invoked.

### 2.3 Visual Animation Desynchronization
Currently, `BikeBellButton` only defines `triggerShakeAnimation()`, which performs a one-shot 240 ms wobble. When long-pressed, the button should display a continuous, rhythmic shake animation for the entire duration of the hold, and cleanly return to resting position ($0^\circ$) upon release.

---

## 3. User Scope Grounding (ATT-1250)

### In-Scope Goals:
1. **Contention-Free Press-and-Hold on `BikeBellButton`**:
   - Observe `PressInteraction.Press`, `PressInteraction.Release`, and `PressInteraction.Cancel` from `ElevatedButton`'s `MutableInteractionSource` inside a `LaunchedEffect`.
   - Start continuous ring (`onContinuousRingStart()`) and continuous shake animation when the press duration reaches `LocalViewConfiguration.current.longPressTimeoutMillis` (~400 ms).
   - Guarantee invocation of `onContinuousRingStop()` and animation reset when the press is released, cancelled (dragged off), or when the composable leaves the composition (`DisposableEffect`).
2. **Reliable Single-Tap (`onClick`) Disambiguation**:
   - Preserve `ElevatedButton(onClick)` handling for single taps (`onSingleRing()`), triggering one-shot shake animation and polite chime.
   - Suppress `onSingleRing()` if the press was recognized and handled as a continuous long-press roll, preventing spurious single strikes on finger release.
3. **Looping Audio Playback in `BikeBellAudioSynthesizer`**:
   - In `startRoll()`, configure `track.setLoopPoints(0, totalFrames, -1)` (with `totalFrames = rollPcm.size / 2`) so the striker roll loops indefinitely while held.
   - In `stopRoll()`, cleanly stop and reset playback position.
4. **Automated Test Coverage**:
   - Update `BikeBellButtonContractTest.kt` to verify `MutableInteractionSource` integration, `PressInteraction` observation, `DisposableEffect` teardown, and long-press disambiguation.
   - Verify `BikeBellAudioSynthesizerTest.kt` covers `setLoopPoints` and infinite roll playback contracts.

### Out-of-Scope Non-Goals (Scope Bounding):
1. **No Acoustic Waveform Alteration**: The physical 4-mode acoustic model (frequencies $2.4\text{ kHz}, 3.84\text{ kHz}, 5.28\text{ kHz}, 7.6\text{ kHz}$), strike intervals, and PCM generation math are proven and remain unmodified.
2. **No Call Site / ViewModel Refactoring**: Callers (`TrackingTabsScreen.kt`, `TrackingTabsViewModel.kt`) already define and pass `onContinuousRingStart` and `onContinuousRingStop`. No changes to `TrackingTabsViewModel` or database persistence are required.
3. **No Changes to Double-Tap Gesture**: Double-tap on `SensorGridScreen` is already verified and isolated; it is unaffected by button touch handling.
4. **No Custom Button Re-Implementation**: Maintain `ElevatedButton` rather than replacing it with a custom `Surface` to preserve existing Material 3 styling, theme colors, elevation tokens, and accessibility contracts.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

* **Original Requirement ID & Target**:
  - `REQ-UI-421.2` (*Floating Cockpit Bell Button*)
  - `REQ-UI-421.4` (*Acoustic Brass Synthesis, Concurrency Safety & Audio Focus*)
  - Targets: `BikeBellButton.kt`, `BikeBellAudioSynthesizer.kt`, `BikeBellButtonContractTest.kt`, `BikeBellAudioSynthesizerTest.kt`
* **Historical Origin & Commit Trace**:
  - `ATT-4281` (`40af18141387`): Introduced bike bell with `detectTapGestures` nested on `ElevatedButton`.
  - `ATT-4371` (`bac64ff9`): Removed `detectTapGestures` to fix single-tap pointer contention, which inadvertently excised long-press continuous ring.
* **Root Reason for Existing Formulation**:
  - The nested `Modifier.pointerInput { detectTapGestures(...) }` was removed because Compose does not support nesting two competing gesture detectors on the same component hierarchy without event consumption conflict.
  - The developer solved the immediate single-tap failure without adopting the recommended `MutableInteractionSource` press-monitoring pattern.
* **Preservation of Core Invariants**:
  - Rule 53 / Rule 58 compliance: `ElevatedButton` does NOT attach conflicting `Modifier.pointerInput(detectTapGestures)`.
  - Availability across `READY`, `TRACKING`, and `PAUSED` modes remains strictly intact (`isAvailable`).
  - Audio routing to `USAGE_MEDIA` and static replay reliability (`track.stop()`) remain strictly intact.
  - 100% clean-room test suite pass rate strictly preserved.

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 `BikeBellButton.kt` Interaction Monitoring Pattern
To achieve contention-free gesture handling without conflicting modifiers:
1. Pass a remembered `MutableInteractionSource` to `ElevatedButton`:
   ```kotlin
   val interactionSource = remember { MutableInteractionSource() }
   ```
2. Monitor interactions in `LaunchedEffect(interactionSource, isAvailable)`:
   ```kotlin
   var isContinuousRingActive by remember { mutableStateOf(false) }
   var wasLongPressHandled by remember { mutableStateOf(false) }
   val viewConfiguration = LocalViewConfiguration.current
   
   LaunchedEffect(interactionSource, isAvailable) {
       if (!isAvailable) return@LaunchedEffect
       interactionSource.interactions.collect { interaction ->
           when (interaction) {
               is PressInteraction.Press -> {
                   wasLongPressHandled = false
                   longPressJob = launch {
                       delay(viewConfiguration.longPressTimeoutMillis)
                       isContinuousRingActive = true
                       wasLongPressHandled = true
                       haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                       onContinuousRingStart()
                       // Launch continuous looping shake animation
                       while (isActive) {
                           rotation.animateTo(-15f, animationSpec = tween(40))
                           rotation.animateTo(15f, animationSpec = tween(60))
                           rotation.animateTo(-10f, animationSpec = tween(50))
                           rotation.animateTo(10f, animationSpec = tween(50))
                       }
                   }
               }
               is PressInteraction.Release -> {
                   longPressJob?.cancel()
                   if (isContinuousRingActive) {
                       isContinuousRingActive = false
                       onContinuousRingStop()
                       rotation.animateTo(0f, animationSpec = tween(40))
                   }
               }
               is PressInteraction.Cancel -> {
                   longPressJob?.cancel()
                   if (isContinuousRingActive) {
                       isContinuousRingActive = false
                       onContinuousRingStop()
                       rotation.animateTo(0f, animationSpec = tween(40))
                   }
               }
           }
       }
   }
   ```
3. Disambiguate Single-Tap in `ElevatedButton(onClick)`:
   ```kotlin
   onClick = {
       if (isAvailable) {
           if (wasLongPressHandled) {
               // Press was already handled as a continuous roll; reset flag and suppress single-ring
               wasLongPressHandled = false
           } else {
               triggerShakeAnimation()
               haptic.performHapticFeedback(HapticFeedbackType.LongPress)
               onSingleRing()
           }
       }
   }
   ```
4. Guaranteed Teardown with `DisposableEffect`:
   ```kotlin
   DisposableEffect(Unit) {
       onDispose {
           if (isContinuousRingActive) {
               isContinuousRingActive = false
               onContinuousRingStop()
           }
       }
   }
   ```

### 5.2 Looping in `BikeBellAudioSynthesizer.kt`
In `startRoll()`:
```kotlin
fun startRoll(volume: Float = 1.0f) {
    synchronized(lock) {
        if (isClosed) return
        try {
            val track = rollTrack ?: return
            val clamped = volume.coerceIn(0.0f, 1.0f)
            track.setVolume(clamped)
            track.stop()
            track.playbackHeadPosition = 0
            val totalFrames = rollPcm.size / 2 // 16-bit mono PCM = 2 bytes per frame
            track.setLoopPoints(0, totalFrames, -1) // -1 = loop indefinitely
            track.play()
        } catch (e: Exception) {
            Log.w(TAG, "Error starting bell roll", e)
        }
    }
}
```
In `stopRoll()`:
```kotlin
fun stopRoll() {
    synchronized(lock) {
        if (isClosed) return
        try {
            rollTrack?.stop()
            rollTrack?.playbackHeadPosition = 0
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping bell roll", e)
        }
    }
}
```

---

## 6. System Invariants & Risk Assessment

### Core Invariants:
1. **Zero Gesture Contention**: No `Modifier.pointerInput` or `detectTapGestures` attached to `ElevatedButton`, adhering strictly to Governance Rules 53 & 58.
2. **Reliable Single-Tap Operation**: Normal short taps trigger `onSingleRing()` without delay or miss, playing the polite double-strike tone.
3. **Guaranteed Teardown**: Continuous roll audio is stopped unconditionally when released, cancelled, disabled, or unmounted.
4. **Multi-Mode Availability**: `BikeBellButton` remains enabled across `READY`, `TRACKING`, and `PAUSED` modes, and disabled in `IDLE`.
5. **Thread Safety**: All native `AudioTrack` operations remain synchronized under `lock`.
6. **Parent Ticket Human Decision Gate**: Parent ticket advances to `Final Review (Human)` only; human user makes final acceptance decision.

### Risk Rating:
* **Risk Rating**: **LOW**
* **Technical Justification**:
  - The changes are localized strictly to `BikeBellButton.kt` (interaction monitoring) and `BikeBellAudioSynthesizer.kt` (loop points).
  - All upstream callers (`TrackingTabsScreen.kt`, `TrackingTabsViewModel.kt`) already implement `triggerBellRoll()` and `stopBellRoll()`.
  - No database migrations, background service bindings, or sensor pipelines are affected.
